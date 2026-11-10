-- Walker ratings: after a walk is completed the owner gives the walker 1–5 stars and an optional short comment,
-- once. A walker's average is readable by signed-in members so owners can compare offers.
-- Payout details: a walker's account holder, bank and IBAN together with the accepted walker agreement.
-- Only the member and administrators can read them. Additive only.

alter table public.walk_listings
  add column if not exists walker_rating smallint check (walker_rating between 1 and 5),
  add column if not exists walker_review text not null default '' check (length(walker_review) <= 300),
  add column if not exists rated_at timestamptz;
grant select (walker_rating, walker_review, rated_at) on public.walk_listings to anon, authenticated;
create index if not exists walk_listings_rated_idx
  on public.walk_listings (walker_id) where walker_rating is not null;

create or replace function private.rate_walker(p_listing uuid, p_rating integer, p_review text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_listing public.walk_listings;
  v_review text := btrim(coalesce(p_review, ''));
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_rating is null or p_rating not between 1 and 5 then raise exception 'WALK_RATING'; end if;
  if length(v_review) > 300 then raise exception 'WALK_REVIEW'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found or v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'completed' or v_listing.walker_id is null then raise exception 'WALK_LISTING_CLOSED'; end if;
  if v_listing.walker_rating is not null then raise exception 'WALK_RATED'; end if;
  update public.walk_listings
     set walker_rating = p_rating, walker_review = v_review, rated_at = now()
   where id = p_listing;
end;
$$;

-- Average stars and number of ratings for up to 100 walkers at once; walkers without ratings are left out.
create or replace function private.walker_ratings(p_users uuid[])
returns table (user_id uuid, average numeric, ratings integer)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_users is null or cardinality(p_users) = 0 then return; end if;
  if cardinality(p_users) > 100 then raise exception 'FORBIDDEN'; end if;
  return query
  select w.walker_id, round(avg(w.walker_rating)::numeric, 1), count(*)::integer
    from public.walk_listings w
   where w.walker_id = any(p_users) and w.walker_rating is not null
   group by w.walker_id;
end;
$$;

create or replace function public.rate_walker(p_listing uuid, p_rating integer, p_review text)
returns void language sql set search_path to '' as $$ select private.rate_walker(p_listing, p_rating, p_review); $$;

create or replace function public.walker_ratings(p_users uuid[])
returns table (user_id uuid, average numeric, ratings integer)
language sql stable set search_path to '' as $$ select * from private.walker_ratings(p_users); $$;

revoke all on function private.rate_walker(uuid, integer, text) from public, anon;
revoke all on function private.walker_ratings(uuid[]) from public, anon;
revoke all on function public.rate_walker(uuid, integer, text) from public, anon;
revoke all on function public.walker_ratings(uuid[]) from public, anon;
grant execute on function private.rate_walker(uuid, integer, text) to authenticated;
grant execute on function private.walker_ratings(uuid[]) to authenticated;
grant execute on function public.rate_walker(uuid, integer, text) to authenticated;
grant execute on function public.walker_ratings(uuid[]) to authenticated;

create table public.payout_details (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  holder_name text not null check (length(btrim(holder_name)) between 3 and 100),
  bank_name text not null check (length(btrim(bank_name)) between 2 and 80),
  iban text not null check (iban ~ '^AZ[0-9]{2}[A-Z0-9]{4}[A-Z0-9]{20}$'),
  agreement_version text not null check (length(agreement_version) between 1 and 20),
  agreement_accepted_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.payout_details enable row level security;
revoke all on public.payout_details from anon, authenticated;
grant select on public.payout_details to authenticated;
create policy payout_details_read on public.payout_details for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));

-- Saving always re-accepts the agreement version shown on screen; the server sets both timestamps.
create or replace function private.save_payout_details(p_holder text, p_bank text, p_iban text, p_agreement text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_holder text := btrim(coalesce(p_holder, ''));
  v_bank text := btrim(coalesce(p_bank, ''));
  v_iban text := upper(regexp_replace(coalesce(p_iban, ''), '\s', '', 'g'));
  v_agreement text := btrim(coalesce(p_agreement, ''));
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if length(v_holder) not between 3 and 100 then raise exception 'PAYOUT_HOLDER'; end if;
  if length(v_bank) not between 2 and 80 then raise exception 'PAYOUT_BANK'; end if;
  if v_iban !~ '^AZ[0-9]{2}[A-Z0-9]{4}[A-Z0-9]{20}$' then raise exception 'PAYOUT_IBAN'; end if;
  if length(v_agreement) not between 1 and 20 then raise exception 'PAYOUT_AGREEMENT'; end if;
  insert into public.payout_details (user_id, holder_name, bank_name, iban, agreement_version)
  values (auth.uid(), v_holder, v_bank, v_iban, v_agreement)
  on conflict (user_id) do update
     set holder_name = excluded.holder_name, bank_name = excluded.bank_name, iban = excluded.iban,
         agreement_version = excluded.agreement_version, agreement_accepted_at = now(), updated_at = now();
end;
$$;

create or replace function public.save_payout_details(p_holder text, p_bank text, p_iban text, p_agreement text)
returns void language sql set search_path to ''
as $$ select private.save_payout_details(p_holder, p_bank, p_iban, p_agreement); $$;

revoke all on function private.save_payout_details(text, text, text, text) from public, anon;
revoke all on function public.save_payout_details(text, text, text, text) from public, anon;
grant execute on function private.save_payout_details(text, text, text, text) to authenticated;
grant execute on function public.save_payout_details(text, text, text, text) to authenticated;
