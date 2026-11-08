-- Price negotiation for walk listings and in-app chat between an owner and interested walkers.
-- Walkers offer their own hourly price, can update or re-send an offer while the listing is open, and
-- every offer appears as a price card in the chat. Reuses the existing conversations/messages tables.
-- Additive only.

alter table public.walk_offers
  add column price_per_hour numeric(7, 2) check (price_per_hour is null or (price_per_hour >= 1 and price_per_hour <= 500));

alter table public.walk_listings
  add column agreed_price_per_hour numeric(7, 2)
    check (agreed_price_per_hour is null or (agreed_price_per_hour >= 1 and agreed_price_per_hour <= 500));

alter table public.messages
  add column walk_listing_id uuid references public.walk_listings(id) on delete set null,
  add column price_per_hour numeric(7, 2) check (price_per_hour is null or (price_per_hour >= 1 and price_per_hour <= 500));

-- A price card may come without text.
alter table public.messages drop constraint messages_check;
alter table public.messages add constraint messages_check
  check (length(btrim(body)) > 0 or image_path is not null or price_per_hour is not null);

create index messages_walk_listing_idx on public.messages (walk_listing_id) where walk_listing_id is not null;

-- Normal messages may name the listing they are about; price cards only come from make_walk_offer.
grant insert (walk_listing_id) on public.messages to authenticated;

-- RLS-free check, so the listing policy can look at offers without recursing into the offers policy.
create or replace function private.offered_on(p_listing uuid)
returns boolean
language sql
stable
security definer
set search_path to ''
as $$
  select exists (select 1 from public.walk_offers where listing_id = p_listing and walker_id = auth.uid());
$$;
revoke all on function private.offered_on(uuid) from public, anon;
grant execute on function private.offered_on(uuid) to authenticated;

-- Walkers keep seeing the listings they offered on (in "My walks"), even after they close.
drop policy walk_listings_read on public.walk_listings;
create policy walk_listings_read on public.walk_listings for select to anon, authenticated
  using ((status = 'open' and starts_at > now() - interval '30 minutes' and private.adoption_owner_active(owner_id))
         or owner_id = (select auth.uid())
         or walker_id = (select auth.uid())
         or ((select auth.uid()) is not null and private.offered_on(id))
         or (select private.is_admin()));

-- Offers are created and updated only through make_walk_offer (withdrawing stays a direct update).
drop policy walk_offers_insert on public.walk_offers;
revoke insert on public.walk_offers from authenticated;
drop trigger walk_offers_notify on public.walk_offers;

-- The conversation between two people, in either direction; created on first use.
create or replace function private.conversation_between(p_other uuid)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare v_id uuid;
begin
  select id into v_id from public.conversations
   where (owner_id = auth.uid() and sitter_id = p_other) or (owner_id = p_other and sitter_id = auth.uid())
   order by created_at limit 1;
  if v_id is null then
    insert into public.conversations (owner_id, sitter_id, owner_name, sitter_name)
    select auth.uid(), them.id, me.full_name, them.full_name
      from public.profiles me, public.profiles them
     where me.id = auth.uid() and them.id = p_other
    on conflict (owner_id, sitter_id) do update set owner_id = excluded.owner_id
    returning id into v_id;
  end if;
  if v_id is null then raise exception 'FORBIDDEN'; end if;
  return v_id;
end;
$$;
revoke all on function private.conversation_between(uuid) from public, anon, authenticated;

-- Opens the chat about a walk listing. A walker writes to the owner while the listing is open (or after
-- offering / being chosen); the owner writes only to walkers who offered or were chosen.
create or replace function private.open_walk_conversation(p_listing uuid, p_with uuid)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare v_listing public.walk_listings; v_other uuid;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.walk_listings where id = p_listing;
  if not found then raise exception 'FORBIDDEN'; end if;
  if v_listing.owner_id = auth.uid() then
    v_other := p_with;
    if v_other is null or v_other = auth.uid()
       or not (v_listing.walker_id = v_other
               or exists (select 1 from public.walk_offers where listing_id = p_listing and walker_id = v_other)) then
      raise exception 'FORBIDDEN';
    end if;
  else
    v_other := v_listing.owner_id;
    if not ((v_listing.status = 'open' and v_listing.starts_at > now() and private.adoption_owner_active(v_listing.owner_id))
            or v_listing.walker_id = auth.uid()
            or exists (select 1 from public.walk_offers where listing_id = p_listing and walker_id = auth.uid())) then
      raise exception 'WALK_LISTING_CLOSED';
    end if;
  end if;
  if exists (select 1 from public.profiles where id in (auth.uid(), v_other) and suspended) then
    raise exception 'ACCOUNT_SUSPENDED';
  end if;
  return private.conversation_between(v_other);
end;
$$;

-- A walker sends, updates or re-sends an offer with their hourly price. The owner is notified and the
-- offer is posted into the chat as a price card.
create or replace function private.make_walk_offer(p_listing uuid, p_price numeric, p_message text)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_listing public.walk_listings;
  v_offer public.walk_offers;
  v_price numeric(7, 2);
  v_text text := btrim(coalesce(p_message, ''));
  v_room uuid;
  v_walker text;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found or v_listing.status <> 'open' or v_listing.starts_at <= now()
     or not private.adoption_owner_active(v_listing.owner_id) then
    raise exception 'WALK_LISTING_CLOSED';
  end if;
  if v_listing.owner_id = auth.uid() then raise exception 'WALK_OWN_LISTING'; end if;
  v_price := coalesce(p_price, v_listing.price_per_hour);
  if v_price < 1 or v_price > 500 then raise exception 'WALK_PRICE'; end if;
  if length(v_text) > 500 then raise exception 'WALK_NOTES'; end if;
  select * into v_offer from public.walk_offers where listing_id = p_listing and walker_id = auth.uid() for update;
  if found and v_offer.status = 'accepted' then raise exception 'WALK_OFFER_CLOSED'; end if;
  insert into public.walk_offers (listing_id, walker_id, message, price_per_hour, status)
  values (p_listing, auth.uid(), v_text, v_price, 'pending')
  on conflict (listing_id, walker_id) do update
     set message = excluded.message, price_per_hour = excluded.price_per_hour, status = 'pending', updated_at = now()
  returning * into v_offer;

  v_room := private.open_walk_conversation(p_listing, null);
  insert into public.messages (conversation_id, sender_id, body, walk_listing_id, price_per_hour)
  values (v_room, auth.uid(), v_text, p_listing, v_price);

  select full_name into v_walker from public.profiles where id = auth.uid();
  insert into public.notifications (user_id, title, body, conversation_id)
  values (v_listing.owner_id, 'Yeni gəzinti təklifi',
          coalesce(v_walker, 'Bir istifadəçi') || ' ' || v_listing.pet_name || ' üçün ' || v_price || ' ₼/saat təklif etdi.',
          v_room);
  return v_offer.id;
end;
$$;

-- Same checks as before, plus the listing reference; price cards carry their own notification.
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
  if new.booking_id is not null and not exists (select 1 from public.bookings where id = new.booking_id and owner_id = c.owner_id and sitter_id = c.sitter_id) then raise exception 'FORBIDDEN'; end if;
  if new.walk_listing_id is not null and not exists (select 1 from public.walk_listings where id = new.walk_listing_id and owner_id in (c.owner_id, c.sitter_id)) then raise exception 'FORBIDDEN'; end if;
  if new.image_path is not null and (split_part(new.image_path, '/', 1) <> c.id::text or split_part(new.image_path, '/', 2) <> auth.uid()::text or not exists (select 1 from storage.objects where bucket_id = 'messages' and name = new.image_path)) then raise exception 'FORBIDDEN'; end if;
  if (select count(*) from public.messages where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then raise exception 'too many messages'; end if;
  new.created_at := clock_timestamp();
  update public.conversations set last_message_at = new.created_at where id = c.id;
  if new.price_per_hour is null then
    insert into public.notifications (user_id, title, body, conversation_id)
    values (case when new.sender_id = c.owner_id then c.sitter_id else c.owner_id end, 'Yeni mesaj', 'Yeni şəxsi mesajınız var.', c.id);
  end if;
  return new;
end;
$$;

-- Accepting an offer fixes the agreed hourly price.
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
    insert into public.notifications (user_id, title, body)
    values (v_offer.walker_id, 'Təklifiniz qəbul edildi',
            v_listing.pet_name || ' üçün gəzinti təklifiniz ' || v_price || ' ₼/saat qiymətlə qəbul edildi.');
  else
    update public.walk_offers set status = 'declined', updated_at = now() where id = p_offer;
    insert into public.notifications (user_id, title, body)
    values (v_offer.walker_id, 'Təklifiniz qəbul edilmədi', v_listing.pet_name || ' üçün təklifiniz rədd edildi.');
  end if;
end;
$$;

-- The caller's chats with the other person's name, the last message and whether it is unread.
create or replace function public.my_conversations()
returns table (
  id uuid, other_id uuid, other_name text, last_message_at timestamptz, last_body text,
  last_sender_id uuid, last_price numeric, unread boolean)
language sql
stable
set search_path to ''
as $$
  select c.id,
         case when c.owner_id = auth.uid() then c.sitter_id else c.owner_id end,
         case when c.owner_id = auth.uid() then c.sitter_name else c.owner_name end,
         c.last_message_at,
         m.body, m.sender_id, m.price_per_hour,
         m.sender_id is not null and m.sender_id <> auth.uid()
           and m.created_at > case when c.owner_id = auth.uid() then c.owner_read_at else c.sitter_read_at end
    from public.conversations c
    left join lateral (select body, sender_id, price_per_hour, created_at from public.messages
                        where conversation_id = c.id order by created_at desc limit 1) m on true
   where auth.uid() in (c.owner_id, c.sitter_id) and m.sender_id is not null
   order by c.last_message_at desc
   limit 100;
$$;

create or replace function public.open_walk_conversation(p_listing uuid, p_with uuid default null)
returns uuid language sql set search_path to '' as $$ select private.open_walk_conversation(p_listing, p_with); $$;

create or replace function public.make_walk_offer(p_listing uuid, p_price numeric, p_message text)
returns uuid language sql set search_path to '' as $$ select private.make_walk_offer(p_listing, p_price, p_message); $$;

revoke all on function private.open_walk_conversation(uuid, uuid) from public, anon;
revoke all on function private.make_walk_offer(uuid, numeric, text) from public, anon;
revoke all on function public.open_walk_conversation(uuid, uuid) from public, anon;
revoke all on function public.make_walk_offer(uuid, numeric, text) from public, anon;
revoke all on function public.my_conversations() from public, anon;
grant execute on function private.open_walk_conversation(uuid, uuid) to authenticated;
grant execute on function private.make_walk_offer(uuid, numeric, text) to authenticated;
grant execute on function public.open_walk_conversation(uuid, uuid) to authenticated;
grant execute on function public.make_walk_offer(uuid, numeric, text) to authenticated;
grant execute on function public.my_conversations() to authenticated;

-- Chat partners can see each other's card.
create or replace function private.public_profile(p_user uuid)
returns table (
  id uuid, full_name text, avatar_path text, city text, bio text, verified boolean,
  contact_phone text, member_since timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
declare
  viewer uuid := auth.uid();
begin
  if viewer is not null and not private.is_active() then raise exception 'FORBIDDEN'; end if;
  return query
  select p.id, p.full_name, p.avatar_path, p.city, p.bio,
         exists (select 1 from public.sitter_profiles sp where sp.id = p.id and sp.verified),
         case when viewer is not null and p.phone_public and length(btrim(p.phone)) > 0 then p.phone else null end,
         p.created_at
    from public.profiles p
   where p.id = p_user
     and not p.suspended
     and ((viewer is not null and p.id = viewer)
          or exists (select 1 from public.adoption_listings l where l.owner_id = p.id and l.status = 'active')
          or exists (select 1 from public.walk_listings w where w.owner_id = p.id and w.status = 'open' and w.starts_at > now())
          or (viewer is not null and exists (
                select 1 from public.walk_offers o join public.walk_listings w on w.id = o.listing_id
                 where o.walker_id = p.id and w.owner_id = viewer))
          or (viewer is not null and exists (
                select 1 from public.walk_listings w
                 where w.status in ('assigned', 'completed')
                   and ((w.owner_id = viewer and w.walker_id = p.id) or (w.walker_id = viewer and w.owner_id = p.id))))
          or (viewer is not null and exists (
                select 1 from public.conversations c
                 where (c.owner_id = viewer and c.sitter_id = p.id) or (c.sitter_id = viewer and c.owner_id = p.id)))
          or (viewer is not null
              and exists (select 1 from public.walk_sessions w
                           where w.user_id = p.id and w.status = 'active' and w.expires_at > now())
              and exists (select 1 from public.walk_sessions mine
                           where mine.user_id = viewer and mine.status = 'active' and mine.expires_at > now())));
end;
$$;
