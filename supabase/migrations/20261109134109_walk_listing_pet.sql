-- A walk request can name one of the owner's pets ("Mənim heyvanlarım"); its photo is then shown on the
-- listing. The server copies the photo path from the pet, so a client can never point at another file.
alter table public.walk_listings
  add column if not exists pet_id uuid references public.pets (id) on delete set null,
  add column if not exists pet_photo_path text;
create or replace function private.walk_listing_pet()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $$
declare p public.pets;
begin
  if new.pet_id is null then
    new.pet_photo_path := null;
    return new;
  end if;
  select * into p from public.pets where id = new.pet_id;
  if p.id is null or p.owner_id <> new.owner_id then
    raise exception 'WALK_PET_NOT_OWN';
  end if;
  new.pet_photo_path := p.photo_path;
  return new;
end;
$$;

drop trigger if exists walk_listings_pet on public.walk_listings;
create trigger walk_listings_pet before insert or update on public.walk_listings
  for each row execute function private.walk_listing_pet();

-- A new pet photo replaces the one shown on that pet's walk requests.
create or replace function private.pet_photo_to_walks()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $$
begin
  update public.walk_listings set pet_photo_path = new.photo_path where pet_id = new.id;
  return new;
end;
$$;

drop trigger if exists pets_photo_to_walks on public.pets;
create trigger pets_photo_to_walks after update of photo_path on public.pets
  for each row when (old.photo_path is distinct from new.photo_path)
  execute function private.pet_photo_to_walks();

-- Anyone who can browse walk requests may see the photo of a pet on an open or assigned request.
create or replace function private.walk_listing_photo_visible(p_name text)
 returns boolean
 language sql
 stable
 security definer
 set search_path to ''
as $$
  select exists (select 1 from public.walk_listings w
                  where w.pet_photo_path = p_name and w.status in ('open', 'assigned'));
$$;

grant execute on function private.walk_listing_photo_visible(text) to anon, authenticated;

drop policy if exists walk_listing_pet_photo_read on storage.objects;
create policy walk_listing_pet_photo_read on storage.objects
  for select to anon, authenticated
  using (bucket_id = 'pets' and private.walk_listing_photo_visible(name));
