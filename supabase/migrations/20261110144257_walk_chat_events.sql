-- The walk agreement shows up in the chat: accepting or declining an offer, cancelling and finishing a walk
-- each post an event card ("kind") into the conversation between the owner and the walker. Clients render the
-- card in the reader's language; "body" keeps an Azerbaijani fallback for older clients. Events never send
-- their own "new message" notification (the walk notification already covers them) and never block the
-- action itself, for example when one side has blocked the other. Additive only.

alter table public.messages
  add column if not exists kind text
    check (kind is null or kind in ('walk_accepted', 'walk_declined', 'walk_cancelled', 'walk_completed'));

grant select (kind) on public.messages to authenticated;

alter table public.messages drop constraint messages_check;
alter table public.messages add constraint messages_check
  check (length(btrim(body)) > 0 or image_path is not null or price_per_hour is not null or kind is not null);

create or replace function private.message_guard()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
declare c public.conversations;
begin
  if not private.is_active() or new.sender_id <> auth.uid() or not private.is_participant(new.conversation_id) then raise exception 'FORBIDDEN'; end if;
  select * into c from public.conversations where id = new.conversation_id for update;
  if exists (select 1 from public.profiles where id in (c.owner_id, c.sitter_id) and suspended) then raise exception 'ACCOUNT_SUSPENDED'; end if;
  if exists (select 1 from public.user_blocks
              where (blocker_id = c.owner_id and blocked_id = c.sitter_id)
                 or (blocker_id = c.sitter_id and blocked_id = c.owner_id)) then
    raise exception 'USER_BLOCKED';
  end if;
  if new.booking_id is not null and not exists (select 1 from public.bookings where id = new.booking_id and owner_id = c.owner_id and sitter_id = c.sitter_id) then raise exception 'FORBIDDEN'; end if;
  if new.walk_listing_id is not null and not exists (select 1 from public.walk_listings where id = new.walk_listing_id and owner_id in (c.owner_id, c.sitter_id)) then raise exception 'FORBIDDEN'; end if;
  if new.image_path is not null and (split_part(new.image_path, '/', 1) <> c.id::text or split_part(new.image_path, '/', 2) <> auth.uid()::text or not exists (select 1 from storage.objects where bucket_id = 'messages' and name = new.image_path)) then raise exception 'FORBIDDEN'; end if;
  if (select count(*) from public.messages where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then raise exception 'too many messages'; end if;
  new.created_at := clock_timestamp();
  update public.conversations set last_message_at = new.created_at where id = c.id;
  if new.price_per_hour is null and new.kind is null then
    perform private.notify(
      case when new.sender_id = c.owner_id then c.sitter_id else c.owner_id end,
      'message_new',
      jsonb_build_object('who', case when new.sender_id = c.owner_id then c.owner_name else c.sitter_name end),
      c.id);
  end if;
  return new;
end;
$$;

-- Posts one event card from the caller to {@code p_other} about {@code p_listing}; best effort.
create or replace function private.post_walk_event(p_other uuid, p_listing uuid, p_kind text, p_body text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare v_room uuid;
begin
  if p_other is null or p_other = auth.uid() then
    return;
  end if;
  begin
    v_room := private.conversation_between(p_other);
    insert into public.messages (conversation_id, sender_id, body, walk_listing_id, kind)
    values (v_room, auth.uid(), p_body, p_listing, p_kind);
  exception when others then
    null;
  end;
end;
$$;
revoke all on function private.post_walk_event(uuid, uuid, text, text) from public, anon, authenticated;

create or replace function private.respond_walk_offer(p_offer uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare v_listing public.walk_listings; v_offer public.walk_offers; v_price numeric(7, 2);
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_offer from public.walk_offers where id = p_offer for update;
  if not found or v_offer.status <> 'pending' then raise exception 'WALK_OFFER_CLOSED'; end if;
  select * into v_listing from public.walk_listings where id = v_offer.listing_id for update;
  if v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'open' then raise exception 'WALK_LISTING_CLOSED'; end if;
  v_price := coalesce(v_offer.price_per_hour, v_listing.price_per_hour);
  if p_accept then
    update public.walk_offers set status = 'accepted', updated_at = now() where id = p_offer;
    update public.walk_offers set status = 'declined', updated_at = now()
     where listing_id = v_listing.id and id <> p_offer and status = 'pending';
    update public.walk_listings set status = 'assigned', walker_id = v_offer.walker_id, agreed_price_per_hour = v_price
     where id = v_listing.id;
    perform private.notify(v_offer.walker_id, 'walk_offer_accepted',
      jsonb_build_object('pet', v_listing.pet_name, 'price', trim_scale(v_price)::text, 'listing', v_listing.id));
    perform private.post_walk_event(v_offer.walker_id, v_listing.id, 'walk_accepted',
      '✅ Razılaşma: ' || trim_scale(v_price)::text || ' ₼/saat');
  else
    update public.walk_offers set status = 'declined', updated_at = now() where id = p_offer;
    perform private.notify(v_offer.walker_id, 'walk_offer_declined',
      jsonb_build_object('pet', v_listing.pet_name, 'listing', v_listing.id));
    perform private.post_walk_event(v_offer.walker_id, v_listing.id, 'walk_declined', 'Təklif qəbul edilmədi');
  end if;
end;
$$;

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
  update public.walk_listings set status = p_status where id = p_listing;
  if p_status = 'completed' then
    perform private.post_walk_event(
      case when auth.uid() = v_listing.owner_id then v_listing.walker_id else v_listing.owner_id end,
      v_listing.id, 'walk_completed', '🏁 Gəzinti tamamlandı');
  elsif v_listing.walker_id is not null then
    perform private.post_walk_event(v_listing.walker_id, v_listing.id, 'walk_cancelled', '✖ Gəzinti ləğv edildi');
  end if;
end;
$$;
