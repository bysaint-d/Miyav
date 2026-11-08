-- Listing location, public profile fields, nearby listings, and Gəzinti (walking) sessions.
-- Additive only: no table is dropped, no RLS is disabled, existing rows keep working.

-- 1. Listing location. numeric(6,3) rounds to ~100 m in the database, so an exact address is never stored.
alter table public.adoption_listings
  add column latitude numeric(6,3),
  add column longitude numeric(6,3),
  add column location_name text not null default '';

alter table public.adoption_listings
  add constraint adoption_listings_location_pair check ((latitude is null) = (longitude is null)),
  add constraint adoption_listings_location_area check (
    latitude is null or (latitude between 38 and 42 and longitude between 44 and 51)),
  add constraint adoption_listings_location_name_len check (length(location_name) <= 120);

grant select (latitude, longitude, location_name) on public.adoption_listings to anon, authenticated;
grant insert (latitude, longitude, location_name) on public.adoption_listings to authenticated;
grant update (latitude, longitude, location_name) on public.adoption_listings to authenticated;

create index adoption_listings_active_location_idx
  on public.adoption_listings (latitude, longitude)
  where status = 'active' and latitude is not null;

-- 2. Public profile fields. The phone stays private unless the owner opts in.
alter table public.profiles
  add column bio text not null default '',
  add column phone_public boolean not null default false;

alter table public.profiles
  add constraint profiles_bio_len check (length(bio) <= 200);

grant select (bio, phone_public) on public.profiles to authenticated;
grant update (bio, phone_public) on public.profiles to authenticated;

-- 3. Distance helper (metres, haversine).
create or replace function private.distance_m(lat1 float8, lng1 float8, lat2 float8, lng2 float8)
returns float8
language sql
immutable
set search_path to ''
as $$
  select 6371000 * 2 * asin(sqrt(
    power(sin(radians(lat2 - lat1) / 2), 2)
    + cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lng2 - lng1) / 2), 2)));
$$;

revoke all on function private.distance_m(float8, float8, float8, float8) from public;
grant execute on function private.distance_m(float8, float8, float8, float8) to anon, authenticated;

-- 4. Nearby listings. Security invoker: adoption_listings RLS still decides what is visible.
create or replace function public.listings_near(
  p_lat float8,
  p_lng float8,
  p_radius_km integer default 25,
  p_species text default null,
  p_search text default null,
  p_limit integer default 20,
  p_offset integer default 0)
returns setof public.adoption_listings
language sql
stable
set search_path to ''
as $$
  select l.*
  from public.adoption_listings l
  where l.status = 'active'
    and l.latitude is not null
    and p_lat between 38 and 42
    and p_lng between 44 and 51
    and (p_species is null or l.species = p_species)
    and (coalesce(p_search, '') = '' or l.name ilike '%' || p_search || '%' or l.breed ilike '%' || p_search || '%')
    and private.distance_m(p_lat, p_lng, l.latitude::float8, l.longitude::float8)
        <= least(greatest(p_radius_km, 1), 100) * 1000
  order by private.distance_m(p_lat, p_lng, l.latitude::float8, l.longitude::float8), l.created_at desc
  limit least(greatest(p_limit, 1), 50)
  offset greatest(p_offset, 0);
$$;

revoke all on function public.listings_near(float8, float8, integer, text, text, integer, integer) from public;
grant execute on function public.listings_near(float8, float8, integer, text, text, integer, integer) to anon, authenticated;

-- 5. Walking sessions. Session metadata is readable by its owner only; live coordinates live in the
--    unexposed private schema and are only reachable through the functions below.
create table public.walk_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  status text not null default 'active' check (status in ('active', 'paused', 'ended')),
  pet_species text not null check (pet_species in ('dog', 'cat', 'bird', 'other')),
  pet_name text not null default '' check (length(pet_name) <= 60),
  status_message text not null default '' check (length(status_message) <= 120),
  started_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '10 minutes',
  ended_at timestamptz,
  check ((status = 'ended') = (ended_at is not null))
);

create unique index walk_sessions_one_open_idx on public.walk_sessions (user_id) where status <> 'ended';
create index walk_sessions_active_idx on public.walk_sessions (expires_at) where status = 'active';

alter table public.walk_sessions enable row level security;
revoke all on public.walk_sessions from anon, authenticated;
grant select on public.walk_sessions to authenticated;

create policy walk_sessions_own_read
  on public.walk_sessions
  for select
  to authenticated
  using (user_id = (select auth.uid()));

create table private.walk_locations (
  session_id uuid primary key references public.walk_sessions(id) on delete cascade,
  latitude numeric(8,5) not null check (latitude between 38 and 42),
  longitude numeric(8,5) not null check (longitude between 44 and 51),
  updated_at timestamptz not null default now()
);

alter table private.walk_locations enable row level security;
revoke all on private.walk_locations from public, anon, authenticated;

-- Ends sessions without a recent heartbeat and removes every location that is no longer actively shared.
create or replace function private.expire_walks()
returns void
language plpgsql
security definer
set search_path to ''
as $$
begin
  update public.walk_sessions
     set status = 'ended', ended_at = now()
   where status <> 'ended' and expires_at < now();
  delete from private.walk_locations wl
   using public.walk_sessions ws
   where ws.id = wl.session_id and ws.status <> 'active';
end;
$$;

revoke all on function private.expire_walks() from public, anon, authenticated;

create or replace function private.start_walk(
  p_species text, p_pet_name text, p_message text, p_lat float8, p_lng float8)
returns public.walk_sessions
language plpgsql
security definer
set search_path to ''
as $$
declare s public.walk_sessions;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_lat is null or p_lng is null or p_lat not between 38 and 42 or p_lng not between 44 and 51 then
    raise exception 'OUT_OF_AREA';
  end if;
  perform private.expire_walks();
  update public.walk_sessions set status = 'ended', ended_at = now()
   where user_id = auth.uid() and status <> 'ended';
  delete from private.walk_locations wl
   using public.walk_sessions ws
   where ws.id = wl.session_id and ws.user_id = auth.uid();
  insert into public.walk_sessions (user_id, pet_species, pet_name, status_message)
  values (auth.uid(), p_species, coalesce(btrim(p_pet_name), ''), coalesce(btrim(p_message), ''))
  returning * into s;
  insert into private.walk_locations (session_id, latitude, longitude)
  values (s.id, round(p_lat::numeric, 5), round(p_lng::numeric, 5));
  return s;
end;
$$;

create or replace function private.update_walk_location(p_lat float8, p_lng float8)
returns public.walk_sessions
language plpgsql
security definer
set search_path to ''
as $$
declare s public.walk_sessions;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_lat is null or p_lng is null or p_lat not between 38 and 42 or p_lng not between 44 and 51 then
    raise exception 'OUT_OF_AREA';
  end if;
  perform private.expire_walks();
  update public.walk_sessions
     set last_seen_at = now(), expires_at = now() + interval '10 minutes'
   where user_id = auth.uid() and status = 'active'
  returning * into s;
  if not found then raise exception 'NO_SESSION'; end if;
  insert into private.walk_locations (session_id, latitude, longitude, updated_at)
  values (s.id, round(p_lat::numeric, 5), round(p_lng::numeric, 5), now())
  on conflict (session_id) do update
    set latitude = excluded.latitude, longitude = excluded.longitude, updated_at = now();
  return s;
end;
$$;

create or replace function private.set_walk_status(p_status text)
returns public.walk_sessions
language plpgsql
security definer
set search_path to ''
as $$
declare s public.walk_sessions;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_status not in ('active', 'paused', 'ended') then raise exception 'FORBIDDEN'; end if;
  perform private.expire_walks();
  update public.walk_sessions
     set status = p_status,
         ended_at = case when p_status = 'ended' then now() else null end,
         last_seen_at = now(),
         expires_at = now() + case when p_status = 'paused' then interval '30 minutes' else interval '10 minutes' end
   where user_id = auth.uid() and status <> 'ended'
  returning * into s;
  if not found then raise exception 'NO_SESSION'; end if;
  if p_status <> 'active' then
    delete from private.walk_locations where session_id = s.id;
  end if;
  return s;
end;
$$;

-- Walkers within 3 km of the caller. Only callers who are walking themselves get results, coordinates
-- are rounded to ~100 m and distances to 100 m. Paused, ended, expired and suspended users never appear.
create or replace function private.nearby_walkers()
returns table (
  session_id uuid,
  user_id uuid,
  full_name text,
  avatar_path text,
  bio text,
  pet_species text,
  pet_name text,
  status_message text,
  latitude float8,
  longitude float8,
  distance_m integer,
  started_at timestamptz,
  contact_phone text)
language plpgsql
security definer
set search_path to ''
as $$
declare me_lat float8; me_lng float8;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  perform private.expire_walks();
  select wl.latitude::float8, wl.longitude::float8 into me_lat, me_lng
    from public.walk_sessions ws
    join private.walk_locations wl on wl.session_id = ws.id
   where ws.user_id = auth.uid() and ws.status = 'active' and ws.expires_at > now();
  if not found then return; end if;
  return query
  select ws.id, p.id, p.full_name, p.avatar_path, p.bio, ws.pet_species, ws.pet_name, ws.status_message,
         round(wl.latitude, 3)::float8,
         round(wl.longitude, 3)::float8,
         (round(private.distance_m(me_lat, me_lng, wl.latitude::float8, wl.longitude::float8) / 100) * 100)::integer,
         ws.started_at,
         case when p.phone_public and length(btrim(p.phone)) > 0 then p.phone else null end
    from public.walk_sessions ws
    join private.walk_locations wl on wl.session_id = ws.id
    join public.profiles p on p.id = ws.user_id
   where ws.status = 'active'
     and ws.expires_at > now()
     and ws.user_id <> auth.uid()
     and not p.suspended
     and private.distance_m(me_lat, me_lng, wl.latitude::float8, wl.longitude::float8) <= 3000
   order by 11
   limit 50;
end;
$$;

-- Public profile card for a listing publisher or a nearby walker. The phone is returned only when the
-- owner opted in. Private profiles of users with no active listing or walk stay hidden.
create or replace function private.public_profile(p_user uuid)
returns table (
  id uuid,
  full_name text,
  avatar_path text,
  city text,
  bio text,
  verified boolean,
  contact_phone text,
  member_since timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  return query
  select p.id, p.full_name, p.avatar_path, p.city, p.bio,
         exists (select 1 from public.sitter_profiles sp where sp.id = p.id and sp.verified),
         case when p.phone_public and length(btrim(p.phone)) > 0 then p.phone else null end,
         p.created_at
    from public.profiles p
   where p.id = p_user
     and not p.suspended
     and (p.id = auth.uid()
          or exists (select 1 from public.adoption_listings l where l.owner_id = p.id and l.status = 'active')
          or (exists (select 1 from public.walk_sessions w
                       where w.user_id = p.id and w.status = 'active' and w.expires_at > now())
              and exists (select 1 from public.walk_sessions mine
                           where mine.user_id = auth.uid() and mine.status = 'active' and mine.expires_at > now())));
end;
$$;

revoke all on function private.start_walk(text, text, text, float8, float8) from public, anon;
revoke all on function private.update_walk_location(float8, float8) from public, anon;
revoke all on function private.set_walk_status(text) from public, anon;
revoke all on function private.nearby_walkers() from public, anon;
revoke all on function private.public_profile(uuid) from public, anon;
grant execute on function private.start_walk(text, text, text, float8, float8) to authenticated;
grant execute on function private.update_walk_location(float8, float8) to authenticated;
grant execute on function private.set_walk_status(text) to authenticated;
grant execute on function private.nearby_walkers() to authenticated;
grant execute on function private.public_profile(uuid) to authenticated;

-- Public wrappers (security invoker), same pattern as the adoption RPCs.
create or replace function public.start_walk(
  p_species text, p_pet_name text, p_message text, p_lat float8, p_lng float8)
returns public.walk_sessions
language sql
set search_path to ''
as $$ select * from private.start_walk(p_species, p_pet_name, p_message, p_lat, p_lng); $$;

create or replace function public.update_walk_location(p_lat float8, p_lng float8)
returns public.walk_sessions
language sql
set search_path to ''
as $$ select * from private.update_walk_location(p_lat, p_lng); $$;

create or replace function public.set_walk_status(p_status text)
returns public.walk_sessions
language sql
set search_path to ''
as $$ select * from private.set_walk_status(p_status); $$;

create or replace function public.nearby_walkers()
returns table (
  session_id uuid, user_id uuid, full_name text, avatar_path text, bio text, pet_species text,
  pet_name text, status_message text, latitude float8, longitude float8, distance_m integer,
  started_at timestamptz, contact_phone text)
language sql
set search_path to ''
as $$ select * from private.nearby_walkers(); $$;

create or replace function public.public_profile(p_user uuid)
returns table (
  id uuid, full_name text, avatar_path text, city text, bio text, verified boolean,
  contact_phone text, member_since timestamptz)
language sql
stable
set search_path to ''
as $$ select * from private.public_profile(p_user); $$;

revoke all on function public.start_walk(text, text, text, float8, float8) from public, anon;
revoke all on function public.update_walk_location(float8, float8) from public, anon;
revoke all on function public.set_walk_status(text) from public, anon;
revoke all on function public.nearby_walkers() from public, anon;
revoke all on function public.public_profile(uuid) from public, anon;
grant execute on function public.start_walk(text, text, text, float8, float8) to authenticated;
grant execute on function public.update_walk_location(float8, float8) to authenticated;
grant execute on function public.set_walk_status(text) to authenticated;
grant execute on function public.nearby_walkers() to authenticated;
grant execute on function public.public_profile(uuid) to authenticated;
