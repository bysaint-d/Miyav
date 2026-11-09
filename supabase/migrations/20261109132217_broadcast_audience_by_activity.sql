-- Every member signs up with role 'owner', so audiences follow activity instead: "owners" are members with
-- a listing or pet profile, "sitters" are members who offered or did a walk.
create or replace function private.broadcast_targets(p_audience text, p_city text)
 returns setof uuid
 language sql
 stable security definer
 set search_path to ''
as $function$
  select p.id from public.profiles p
   where not p.suspended
     and (p_audience = 'all'
          or (p_audience = 'owners' and (
                exists (select 1 from public.adoption_listings a where a.owner_id = p.id)
             or exists (select 1 from public.walk_listings w where w.owner_id = p.id)
             or exists (select 1 from public.pets x where x.owner_id = p.id)))
          or (p_audience = 'sitters' and (
                p.role = 'sitter'
             or exists (select 1 from public.walk_offers o where o.walker_id = p.id)
             or exists (select 1 from public.walk_sessions s where s.user_id = p.id))))
     and (p_city is null or p.city = p_city);
$function$;
