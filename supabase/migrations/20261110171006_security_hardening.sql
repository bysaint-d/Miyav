-- Security review fixes.
-- 1. Owners could bring an archived listing back by updating its status directly; only administrators move a
--    listing out of 'deleted' now.
-- 2. A walker could mark a walk done and withdraw its money at once. Walks the walker completed themselves
--    are held for 48 hours unless the owner rated them; walks the owner completed count straight away.
-- 3. The requesting administrator no longer gets the "new withdrawal" notice about their own request.
-- Additive only.

create or replace function private.adoption_status_guard()
returns trigger
language plpgsql
set search_path to ''
as $$
begin
  if old.status = 'deleted' and new.status is distinct from old.status and not private.is_admin() then
    raise exception 'FORBIDDEN';
  end if;
  return new;
end;
$$;
revoke all on function private.adoption_status_guard() from public, anon, authenticated;
create trigger adoption_status_guard before update of status on public.adoption_listings
  for each row execute function private.adoption_status_guard();

alter table public.walk_listings
  add column if not exists completed_by uuid references public.profiles(id) on delete set null,
  add column if not exists completed_at timestamptz;

create or replace function private.finish_walk_listing(p_listing uuid, p_status text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare v_listing public.walk_listings;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found then raise exception 'FORBIDDEN'; end if;
  if p_status = 'completed' then
    if v_listing.status <> 'assigned' or auth.uid() not in (v_listing.owner_id, v_listing.walker_id)
       or v_listing.starts_at > now() then
      raise exception 'WALK_LISTING_CLOSED';
    end if;
  elsif p_status = 'cancelled' then
    if v_listing.owner_id <> auth.uid() or v_listing.status not in ('open', 'assigned') then
      raise exception 'WALK_LISTING_CLOSED';
    end if;
    update public.walk_offers set status = 'declined', updated_at = now()
     where listing_id = p_listing and status = 'pending';
    if v_listing.walker_id is not null then
      perform private.notify(v_listing.walker_id, 'walk_cancelled',
        jsonb_build_object('pet', v_listing.pet_name, 'listing', v_listing.id));
    end if;
  else
    raise exception 'FORBIDDEN';
  end if;
  if p_status = 'completed' then
    update public.walk_listings set status = p_status, completed_by = auth.uid(), completed_at = now() where id = p_listing;
    perform private.post_walk_event(
      case when auth.uid() = v_listing.owner_id then v_listing.walker_id else v_listing.owner_id end,
      v_listing.id, 'walk_completed', '🏁 Gəzinti tamamlandı');
  else
    update public.walk_listings set status = p_status where id = p_listing;
    if v_listing.walker_id is not null then
      perform private.post_walk_event(v_listing.walker_id, v_listing.id, 'walk_cancelled', '✖ Gəzinti ləğv edildi');
    end if;
  end if;
end;
$$;

drop function if exists public.my_wallet();
drop function if exists private.my_wallet();
drop function if exists private.wallet_of(uuid);

-- "held": walks the walker marked done themselves in the last 48 hours that the owner has not rated yet.
create function private.wallet_of(p_user uuid)
returns table (earned numeric, held numeric, pending numeric, withdrawn numeric, available numeric, completed integer)
language sql
stable
security definer
set search_path to ''
as $$
  with walks as (
    select round(coalesce(w.agreed_price_per_hour, w.price_per_hour) * w.duration_minutes / 60, 2) as total,
           (w.completed_by = w.walker_id and w.walker_rating is null and w.completed_at > now() - interval '48 hours') as on_hold
      from public.walk_listings w
     where w.walker_id = p_user and w.status = 'completed'),
  e as (
    select coalesce(sum(total), 0) as earned, coalesce(sum(total) filter (where on_hold), 0) as held,
           count(*)::integer as completed
      from walks),
  o as (
    select coalesce(sum(x.amount) filter (where x.status = 'pending'), 0) as pending,
           coalesce(sum(x.amount) filter (where x.status = 'paid'), 0) as withdrawn
      from public.withdrawals x
     where x.walker_id = p_user)
  select e.earned, e.held, o.pending, o.withdrawn, greatest(e.earned - e.held - o.pending - o.withdrawn, 0), e.completed
    from e, o;
$$;
revoke all on function private.wallet_of(uuid) from public, anon, authenticated;

create function private.my_wallet()
returns table (earned numeric, held numeric, pending numeric, withdrawn numeric, available numeric, completed integer,
               fee_percent numeric, minimum numeric)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  return query
  select w.earned, w.held, w.pending, w.withdrawn, w.available, w.completed,
         private.withdrawal_fee_percent(), private.withdrawal_minimum()
    from private.wallet_of(auth.uid()) w;
end;
$$;

create function public.my_wallet()
returns table (earned numeric, held numeric, pending numeric, withdrawn numeric, available numeric, completed integer,
               fee_percent numeric, minimum numeric)
language sql stable set search_path to '' as $$ select * from private.my_wallet(); $$;

revoke all on function private.my_wallet() from public, anon;
revoke all on function public.my_wallet() from public, anon;
grant execute on function private.my_wallet() to authenticated;
grant execute on function public.my_wallet() to authenticated;

create or replace function private.request_withdrawal(p_amount numeric)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_amount numeric := round(p_amount, 2);
  v_payout public.payout_details;
  v_wallet record;
  v_percent numeric := private.withdrawal_fee_percent();
  v_fee numeric;
  v_id uuid;
  v_name text;
  v_admin uuid;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  perform pg_advisory_xact_lock(hashtextextended('withdrawal:' || auth.uid()::text, 0));
  select * into v_payout from public.payout_details where user_id = auth.uid();
  if not found then raise exception 'WITHDRAWAL_PAYOUT'; end if;
  if v_payout.agreement_version <> private.walker_agreement_version() then raise exception 'WITHDRAWAL_AGREEMENT'; end if;
  if exists (select 1 from public.withdrawals where walker_id = auth.uid() and status = 'pending') then
    raise exception 'WITHDRAWAL_PENDING';
  end if;
  if v_amount is null or v_amount < private.withdrawal_minimum() then raise exception 'WITHDRAWAL_MINIMUM'; end if;
  select * into v_wallet from private.wallet_of(auth.uid());
  if v_amount > v_wallet.available then raise exception 'WITHDRAWAL_BALANCE'; end if;
  if (select count(*) from public.withdrawals where walker_id = auth.uid() and created_at > now() - interval '1 day') >= 5 then
    raise exception 'WITHDRAWAL_LIMIT';
  end if;
  v_fee := round(v_amount * v_percent / 100, 2);
  insert into public.withdrawals (walker_id, amount, fee_percent, fee, net, holder_name, bank_name, iban)
  values (auth.uid(), v_amount, v_percent, v_fee, v_amount - v_fee, v_payout.holder_name, v_payout.bank_name, v_payout.iban)
  returning id into v_id;
  select full_name into v_name from public.profiles where id = auth.uid();
  for v_admin in select id from public.profiles where role = 'admin' and not suspended and id <> auth.uid() loop
    perform private.notify(v_admin, 'withdrawal_requested',
      jsonb_build_object('who', coalesce(v_name, ''), 'amount', v_amount::text, 'request', v_id));
  end loop;
  return v_id;
exception
  when unique_violation then
    raise exception 'WITHDRAWAL_PENDING';
end;
$$;
