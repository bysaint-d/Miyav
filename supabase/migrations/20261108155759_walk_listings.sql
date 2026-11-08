-- Walking listings: a pet owner posts "I need someone to walk my pet" with time, duration and an hourly
-- price; walkers send offers; the owner accepts one. Separate from adoption listings. Additive only.

create table public.walk_listings (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  pet_name text not null check (length(btrim(pet_name)) between 1 and 60),
  pet_species text not null check (pet_species in ('dog', 'cat', 'bird', 'other')),
  starts_at timestamptz not null,
  duration_minutes integer not null check (duration_minutes between 15 and 480),
  price_per_hour numeric(7, 2) not null check (price_per_hour >= 1 and price_per_hour <= 500),
  city text not null check (city in ('Bakı', 'Sumqayıt', 'Abşeron')),
  -- Two decimals (about 1 km): the meeting point is agreed in private, never published.
  latitude numeric(5, 2),
  longitude numeric(5, 2),
  location_name text not null default '' check (length(location_name) <= 120),
  notes text not null default '' check (length(notes) <= 500),
  status text not null default 'open' check (status in ('open', 'assigned', 'completed', 'cancelled')),
  walker_id uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint walk_listings_location_pair check ((latitude is null) = (longitude is null)),
  constraint walk_listings_location_area check (latitude is null or (latitude between 38 and 42 and longitude between 44 and 51)),
  constraint walk_listings_open_has_no_walker check (status <> 'open' or walker_id is null)
);

create index walk_listings_open_idx on public.walk_listings (starts_at) where status = 'open';
create index walk_listings_owner_idx on public.walk_listings (owner_id, created_at desc);
create index walk_listings_walker_idx on public.walk_listings (walker_id) where walker_id is not null;

create table public.walk_offers (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.walk_listings(id) on delete cascade,
  walker_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  message text not null default '' check (length(message) <= 500),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'withdrawn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (listing_id, walker_id)
);

create index walk_offers_walker_idx on public.walk_offers (walker_id, created_at desc);

-- New listings must start in the future (within 60 days); rounding keeps the area approximate.
create or replace function private.walk_listing_guard()
returns trigger
language plpgsql
set search_path to ''
as $$
begin
  if tg_op = 'INSERT' or new.starts_at is distinct from old.starts_at then
    if new.starts_at < now() - interval '5 minutes' or new.starts_at > now() + interval '60 days' then
      raise exception 'WALK_TIME';
    end if;
  end if;
  new.latitude := round(new.latitude, 2);
  new.longitude := round(new.longitude, 2);
  new.updated_at := now();
  return new;
end;
$$;

create trigger walk_listings_guard before insert or update on public.walk_listings
  for each row execute function private.walk_listing_guard();

alter table public.walk_listings enable row level security;
alter table public.walk_offers enable row level security;
revoke all on public.walk_listings from anon, authenticated;
revoke all on public.walk_offers from anon, authenticated;

grant select on public.walk_listings to anon, authenticated;
grant insert (pet_name, pet_species, starts_at, duration_minutes, price_per_hour, city, latitude, longitude,
              location_name, notes) on public.walk_listings to authenticated;
grant update (pet_name, pet_species, starts_at, duration_minutes, price_per_hour, city, latitude, longitude,
              location_name, notes, status) on public.walk_listings to authenticated;
grant select on public.walk_offers to authenticated;
grant insert (listing_id, message) on public.walk_offers to authenticated;
grant update (status) on public.walk_offers to authenticated;

-- Open, upcoming listings of active owners are public; people involved and admins see the rest.
create policy walk_listings_read on public.walk_listings for select to anon, authenticated
  using ((status = 'open' and starts_at > now() - interval '30 minutes' and private.adoption_owner_active(owner_id))
         or owner_id = (select auth.uid())
         or walker_id = (select auth.uid())
         or (select private.is_admin()));

create policy walk_listings_insert on public.walk_listings for insert to authenticated
  with check (owner_id = (select auth.uid()) and (select private.is_active()) and status = 'open' and walker_id is null);

-- Owners edit or cancel while the listing is open. Assignment and completion go through the functions below.
create policy walk_listings_update on public.walk_listings for update to authenticated
  using (owner_id = (select auth.uid()) and (select private.is_active()) and status = 'open')
  with check (owner_id = (select auth.uid()) and status in ('open', 'cancelled') and walker_id is null);

create policy walk_offers_read on public.walk_offers for select to authenticated
  using ((select private.is_active()) and (walker_id = (select auth.uid())
         or exists (select 1 from public.walk_listings l where l.id = walk_offers.listing_id and l.owner_id = (select auth.uid()))
         or (select private.is_admin())));

create policy walk_offers_insert on public.walk_offers for insert to authenticated
  with check (walker_id = (select auth.uid()) and status = 'pending' and (select private.is_active())
              and exists (select 1 from public.walk_listings l
                           where l.id = walk_offers.listing_id and l.status = 'open'
                             and l.owner_id <> (select auth.uid()) and l.starts_at > now()));

-- Walkers can only withdraw their own pending offer.
create policy walk_offers_withdraw on public.walk_offers for update to authenticated
  using (walker_id = (select auth.uid()) and status = 'pending')
  with check (walker_id = (select auth.uid()) and status = 'withdrawn');

-- The owner hears about every new offer.
create or replace function private.walk_offer_notify()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
declare v_owner uuid; v_pet text; v_walker text;
begin
  select owner_id, pet_name into v_owner, v_pet from public.walk_listings where id = new.listing_id;
  select full_name into v_walker from public.profiles where id = new.walker_id;
  insert into public.notifications (user_id, title, body)
  values (v_owner, 'Yeni gəzinti təklifi', coalesce(v_walker, 'Bir istifadəçi') || ' ' || v_pet || ' üçün gəzinti təklif etdi.');
  return new;
end;
$$;

revoke all on function private.walk_offer_notify() from public, anon, authenticated;
create trigger walk_offers_notify after insert on public.walk_offers
  for each row execute function private.walk_offer_notify();

-- Owner accepts or declines an offer. Accepting assigns the walker and declines the other offers.
create or replace function private.respond_walk_offer(p_offer uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare v_listing public.walk_listings; v_offer public.walk_offers;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_offer from public.walk_offers where id = p_offer for update;
  if not found or v_offer.status <> 'pending' then raise exception 'WALK_OFFER_CLOSED'; end if;
  select * into v_listing from public.walk_listings where id = v_offer.listing_id for update;
  if v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'open' then raise exception 'WALK_LISTING_CLOSED'; end if;
  if p_accept then
    update public.walk_offers set status = 'accepted', updated_at = now() where id = p_offer;
    update public.walk_offers set status = 'declined', updated_at = now()
     where listing_id = v_listing.id and id <> p_offer and status = 'pending';
    update public.walk_listings set status = 'assigned', walker_id = v_offer.walker_id where id = v_listing.id;
    insert into public.notifications (user_id, title, body)
    values (v_offer.walker_id, 'Təklifiniz qəbul edildi', v_listing.pet_name || ' üçün gəzinti təklifiniz qəbul edildi.');
  else
    update public.walk_offers set status = 'declined', updated_at = now() where id = p_offer;
    insert into public.notifications (user_id, title, body)
    values (v_offer.walker_id, 'Təklifiniz qəbul edilmədi', v_listing.pet_name || ' üçün başqa gəzdirən seçildi və ya təklif rədd edildi.');
  end if;
end;
$$;

-- Owner or assigned walker marks a started walk as done; the owner can cancel before it is done.
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
      insert into public.notifications (user_id, title, body)
      values (v_listing.walker_id, 'Gəzinti ləğv edildi', v_listing.pet_name || ' üçün gəzinti sahibi tərəfindən ləğv edildi.');
    end if;
  else
    raise exception 'FORBIDDEN';
  end if;
  update public.walk_listings set status = p_status where id = p_listing;
end;
$$;

create or replace function public.respond_walk_offer(p_offer uuid, p_accept boolean)
returns void language sql set search_path to '' as $$ select private.respond_walk_offer(p_offer, p_accept); $$;

create or replace function public.finish_walk_listing(p_listing uuid, p_status text)
returns void language sql set search_path to '' as $$ select private.finish_walk_listing(p_listing, p_status); $$;

revoke all on function private.respond_walk_offer(uuid, boolean) from public, anon;
revoke all on function private.finish_walk_listing(uuid, text) from public, anon;
revoke all on function public.respond_walk_offer(uuid, boolean) from public, anon;
revoke all on function public.finish_walk_listing(uuid, text) from public, anon;
grant execute on function private.respond_walk_offer(uuid, boolean) to authenticated;
grant execute on function private.finish_walk_listing(uuid, text) to authenticated;
grant execute on function public.respond_walk_offer(uuid, boolean) to authenticated;
grant execute on function public.finish_walk_listing(uuid, text) to authenticated;

-- Publisher cards: owners of open walk listings are public like adoption publishers; walkers who offered
-- on my listing and the owner/walker of an assigned walk can see each other (phone only if opted in).
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
          or (viewer is not null
              and exists (select 1 from public.walk_sessions w
                           where w.user_id = p.id and w.status = 'active' and w.expires_at > now())
              and exists (select 1 from public.walk_sessions mine
                           where mine.user_id = viewer and mine.status = 'active' and mine.expires_at > now())));
end;
$$;
