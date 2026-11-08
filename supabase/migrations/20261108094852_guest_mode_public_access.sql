-- Guest mode. Anonymous visitors may read the public card of a listing publisher, and a signed-in user
-- can merge favourites they saved on the device before signing in.
-- Additive only: no RLS policy is relaxed, no table becomes readable by anon.

-- 1. Publisher card for guests. Anonymous callers only see publishers that have an active listing, and
--    never receive a phone number: contacting an owner requires an account. Signed-in behaviour is
--    unchanged (own profile, publishers, and walkers visible to a fellow active walker).
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
          or (viewer is not null
              and exists (select 1 from public.walk_sessions w
                           where w.user_id = p.id and w.status = 'active' and w.expires_at > now())
              and exists (select 1 from public.walk_sessions mine
                           where mine.user_id = viewer and mine.status = 'active' and mine.expires_at > now())));
end;
$$;

grant execute on function private.public_profile(uuid) to anon;
grant execute on function public.public_profile(uuid) to anon;

-- 2. Merge device favourites into the account in one call. Security invoker: the listing lookup goes
--    through adoption_listings RLS and the insert through adoption_favorites_add, so only active listings
--    of active owners can be saved, and only for the caller.
create or replace function public.merge_adoption_favorites(p_listing_ids uuid[])
returns table (added integer, already_saved integer, unavailable integer)
language plpgsql
security invoker
set search_path to ''
as $$
declare
  wanted uuid[];
  v_total integer;
  v_already integer;
  v_added integer;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_listing_ids is null or cardinality(p_listing_ids) = 0 then
    return query select 0, 0, 0;
    return;
  end if;
  if cardinality(p_listing_ids) > 200 then raise exception 'TOO_MANY'; end if;

  select coalesce(array_agg(distinct x), '{}') into wanted from unnest(p_listing_ids) as x where x is not null;
  v_total := cardinality(wanted);

  select count(*) into v_already
    from public.adoption_favorites f
   where f.user_id = (select auth.uid()) and f.listing_id = any (wanted);

  with inserted as (
    insert into public.adoption_favorites (listing_id)
    select l.id
      from public.adoption_listings l
     where l.id = any (wanted)
       and l.status = 'active'
       and not exists (select 1 from public.adoption_favorites f
                        where f.user_id = (select auth.uid()) and f.listing_id = l.id)
    on conflict do nothing
    returning 1)
  select count(*) into v_added from inserted;

  return query select v_added, v_already, greatest(v_total - v_added - v_already, 0);
end;
$$;

revoke all on function public.merge_adoption_favorites(uuid[]) from public, anon;
grant execute on function public.merge_adoption_favorites(uuid[]) to authenticated;
