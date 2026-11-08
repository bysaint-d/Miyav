-- Walks carry the pets being walked: the walker's own pets ("Mənim heyvanlarım") and/or the pet of a walk
-- job they were chosen for. Walkers who see each other on the map can open each other's pet cards
-- (and pet photos) while both are walking within 3 km. Additive only.

-- A pet profile is quick to create; the emergency contact is optional.
alter table public.pets alter column emergency_contact set default '';
alter table public.pets drop constraint if exists pets_emergency_contact_check;
alter table public.pets add constraint pets_emergency_contact_check
  check (emergency_contact = '' or length(emergency_contact) between 5 and 160);

alter table public.walk_sessions
  add column walk_listing_id uuid references public.walk_listings(id) on delete set null;

create table public.walk_session_pets (
  session_id uuid not null references public.walk_sessions(id) on delete cascade,
  slot smallint not null check (slot between 0 and 5),
  pet_id uuid references public.pets(id) on delete set null,
  name text not null check (length(btrim(name)) between 1 and 60),
  species text not null check (species in ('dog', 'cat', 'bird', 'other')),
  breed text not null default '' check (length(breed) <= 80),
  age numeric check (age is null or (age >= 0 and age <= 80)),
  photo_path text,
  from_job boolean not null default false,
  primary key (session_id, slot)
);

create index walk_session_pets_photo_idx on public.walk_session_pets (photo_path) where photo_path is not null;

alter table public.walk_session_pets enable row level security;
revoke all on public.walk_session_pets from anon, authenticated;

-- The walker themself, or someone else walking right now within 3 km of an active walk.
create or replace function private.can_see_walk(p_session uuid)
returns boolean
language sql
stable
security definer
set search_path to ''
as $$
  select private.is_active() and exists (
    select 1 from public.walk_sessions s
     where s.id = p_session
       and (s.user_id = auth.uid()
            or (s.status = 'active' and s.expires_at > now()
                and exists (
                  select 1
                    from private.walk_locations l,
                         public.walk_sessions me
                    join private.walk_locations ml on ml.session_id = me.id
                   where l.session_id = s.id
                     and me.user_id = auth.uid() and me.status = 'active' and me.expires_at > now()
                     and private.distance_m(ml.latitude::float8, ml.longitude::float8,
                                            l.latitude::float8, l.longitude::float8) <= 3000))));
$$;
revoke all on function private.can_see_walk(uuid) from public, anon;
grant execute on function private.can_see_walk(uuid) to authenticated;

-- Starts a walk with up to five of the caller's pets and/or the pet of a walk job assigned to them.
create or replace function private.start_walk_with(p_pets uuid[], p_listing uuid, p_message text,
                                                    p_lat double precision, p_lng double precision)
returns public.walk_sessions
language plpgsql
security definer
set search_path to ''
as $$
declare
  s public.walk_sessions;
  v_listing public.walk_listings;
  v_pets uuid[] := coalesce(p_pets, '{}');
  v_species text;
  v_names text;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if cardinality(v_pets) > 5 then raise exception 'WALK_PET_LIMIT'; end if;
  if p_listing is not null then
    select * into v_listing from public.walk_listings
     where id = p_listing and walker_id = auth.uid() and status = 'assigned';
    if not found then raise exception 'WALK_LISTING_CLOSED'; end if;
  end if;
  if cardinality(v_pets) = 0 and p_listing is null then raise exception 'WALK_PET_REQUIRED'; end if;
  if exists (select 1 from unnest(v_pets) x
              where not exists (select 1 from public.pets p where p.id = x and p.owner_id = auth.uid())) then
    raise exception 'FORBIDDEN';
  end if;

  select coalesce(v_listing.pet_species, (select animal_type from public.pets where id = v_pets[1])) into v_species;
  select left(string_agg(name, ', ' order by ord), 60) into v_names
    from (select v_listing.pet_name as name, 0::bigint as ord where p_listing is not null
          union all
          select p.name, t.ord from unnest(v_pets) with ordinality t(id, ord) join public.pets p on p.id = t.id) named;

  s := private.start_walk(v_species, v_names, p_message, p_lat, p_lng);
  update public.walk_sessions set walk_listing_id = p_listing where id = s.id returning * into s;

  if p_listing is not null then
    insert into public.walk_session_pets (session_id, slot, name, species, from_job)
    values (s.id, 0, v_listing.pet_name, v_listing.pet_species, true);
  end if;
  insert into public.walk_session_pets (session_id, slot, pet_id, name, species, breed, age, photo_path)
  select s.id, (t.ord - 1 + case when p_listing is null then 0 else 1 end)::smallint,
         p.id, p.name, p.animal_type, p.breed, p.age, p.photo_path
    from unnest(v_pets) with ordinality t(id, ord) join public.pets p on p.id = t.id;
  return s;
end;
$$;

-- The pets on a walk, for the walker and for people walking nearby.
create or replace function private.walk_pets(p_session uuid)
returns table (slot smallint, name text, species text, breed text, age numeric, photo_path text, from_job boolean)
language sql
stable
security definer
set search_path to ''
as $$
  select sp.slot, sp.name, sp.species, sp.breed, sp.age, sp.photo_path, sp.from_job
    from public.walk_session_pets sp
   where sp.session_id = p_session and private.can_see_walk(p_session)
   order by sp.slot;
$$;

-- Pet photos are private to the owner, except while the pet is out on a walk others can see.
create or replace function private.walk_pet_photo_visible(p_path text)
returns boolean
language sql
stable
security definer
set search_path to ''
as $$
  select exists (select 1 from public.walk_session_pets sp
                  where sp.photo_path = p_path and private.can_see_walk(sp.session_id));
$$;
revoke all on function private.walk_pet_photo_visible(text) from public, anon;
grant execute on function private.walk_pet_photo_visible(text) to authenticated;

create policy walk_pet_photo_read on storage.objects for select to authenticated
  using (bucket_id = 'pets' and private.walk_pet_photo_visible(name));

create or replace function public.start_walk_with(p_pets uuid[], p_listing uuid, p_message text,
                                                   p_lat double precision, p_lng double precision)
returns public.walk_sessions language sql set search_path to ''
as $$ select private.start_walk_with(p_pets, p_listing, p_message, p_lat, p_lng); $$;

create or replace function public.walk_pets(p_session uuid)
returns table (slot smallint, name text, species text, breed text, age numeric, photo_path text, from_job boolean)
language sql stable set search_path to ''
as $$ select * from private.walk_pets(p_session); $$;

revoke all on function private.start_walk_with(uuid[], uuid, text, double precision, double precision) from public, anon;
revoke all on function private.walk_pets(uuid) from public, anon;
revoke all on function public.start_walk_with(uuid[], uuid, text, double precision, double precision) from public, anon;
revoke all on function public.walk_pets(uuid) from public, anon;
grant execute on function private.start_walk_with(uuid[], uuid, text, double precision, double precision) to authenticated;
grant execute on function private.walk_pets(uuid) to authenticated;
grant execute on function public.start_walk_with(uuid[], uuid, text, double precision, double precision) to authenticated;
grant execute on function public.walk_pets(uuid) to authenticated;
