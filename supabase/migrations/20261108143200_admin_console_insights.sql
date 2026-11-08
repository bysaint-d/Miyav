-- Admin console read models for the desktop app. Additive only: no table, column or policy changes.
-- Every function is security definer and starts with private.is_admin(). They never return message
-- bodies, emails, phone numbers, emergency contacts, care notes or exact walk coordinates; live walk
-- positions are rounded to two decimals (about 1 km).

create or replace function private.admin_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path to ''
as $$
declare
  today date := (now() at time zone 'Asia/Baku')::date;
  result jsonb;
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  select jsonb_build_object(
    'users_total', (select count(*) from public.profiles),
    'users_suspended', (select count(*) from public.profiles where suspended),
    'users_new_7d', (select count(*) from public.profiles where created_at > now() - interval '7 days'),
    'users_active_30d', (select count(distinct a.user_id) from (
        select user_id from public.walk_sessions where started_at > now() - interval '30 days'
        union select owner_id from public.adoption_listings where updated_at > now() - interval '30 days'
        union select applicant_id from public.adoption_applications where created_at > now() - interval '30 days'
        union select owner_id from public.bookings where created_at > now() - interval '30 days'
        union select sitter_id from public.bookings where created_at > now() - interval '30 days'
        union select sender_id from public.messages where created_at > now() - interval '30 days') a),
    'pets_total', (select count(*) from public.pets),
    'helpers_active', (select count(*) from public.sitter_profiles where active),
    'helpers_verified', (select count(*) from public.sitter_profiles where active and verified),
    'requests_by_status', (select coalesce(jsonb_object_agg(s.status, s.n), '{}'::jsonb)
                             from (select status, count(*) n from public.bookings group by status) s),
    'requests_upcoming', (select count(*) from public.bookings
                           where status in ('pending', 'accepted') and ends_at > now()),
    'walks_total', (select count(*) from public.walk_sessions),
    'walks_live', (select count(*) from public.walk_sessions where status = 'active' and expires_at > now()),
    'listings_by_status', (select coalesce(jsonb_object_agg(s.status, s.n), '{}'::jsonb)
                             from (select status, count(*) n from public.adoption_listings group by status) s),
    'applications_pending', (select count(*) from public.adoption_applications where status = 'pending'),
    'reports_open', (select count(*) from public.reports where not resolved),
    'conversations_total', (select count(*) from public.conversations),
    'messages_7d', (select count(*) from public.messages where created_at > now() - interval '7 days'),
    'notifications_total', (select count(*) from public.notifications),
    'notifications_unread', (select count(*) from public.notifications where read_at is null),
    'registrations_14d', (select jsonb_agg(jsonb_build_object('day', d::date, 'count',
        (select count(*) from public.profiles p where (p.created_at at time zone 'Asia/Baku')::date = d::date)) order by d)
        from generate_series(today - 13, today, interval '1 day') d),
    'walks_14d', (select jsonb_agg(jsonb_build_object('day', d::date, 'count',
        (select count(*) from public.walk_sessions w where (w.started_at at time zone 'Asia/Baku')::date = d::date)) order by d)
        from generate_series(today - 13, today, interval '1 day') d),
    'cities', (select coalesce(jsonb_agg(jsonb_build_object('city', x.city, 'users', x.users,
        'listings', x.listings, 'helpers', x.helpers) order by x.users desc, x.city), '[]'::jsonb)
        from (select c.city,
                     (select count(*) from public.profiles p where p.city = c.city) users,
                     (select count(*) from public.adoption_listings l where l.city = c.city and l.status = 'active') listings,
                     (select count(*) from public.sitter_profiles s where s.city = c.city and s.active) helpers
                from (select city from public.profiles
                      union select city from public.adoption_listings
                      union select city from public.sitter_profiles) c
               where coalesce(c.city, '') <> '') x),
    'recent_users', (select coalesce(jsonb_agg(r), '[]'::jsonb) from (
        select id, full_name, city, role, created_at from public.profiles order by created_at desc limit 6) r),
    'recent_walks', (select coalesce(jsonb_agg(r), '[]'::jsonb) from (
        select w.id, p.full_name, w.pet_name, w.pet_species,
               case when w.status <> 'ended' and w.expires_at < now() then 'ended' else w.status end as status,
               w.started_at
          from public.walk_sessions w join public.profiles p on p.id = w.user_id
         order by w.started_at desc limit 6) r),
    'recent_listings', (select coalesce(jsonb_agg(r), '[]'::jsonb) from (
        select id, name, species, city, status, updated_at from public.adoption_listings
         order by updated_at desc limit 6) r)
  ) into result;
  return result;
end;
$$;

-- Walking requests (bookings): owner, walker, pet and time. Location is the walker's public area.
create or replace function private.admin_walk_requests(p_status text default null)
returns table (
  id uuid, owner_id uuid, owner_name text, helper_id uuid, helper_name text,
  pet_name text, pet_type text, service_kind text, starts_at timestamptz, ends_at timestamptz,
  status text, total_price numeric, price_unit text, city text, district text, notes text,
  created_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select b.id, b.owner_id, coalesce(o.full_name, ''), b.sitter_id, coalesce(s.full_name, h.full_name, ''),
         coalesce(pt.name, ''), coalesce(pt.animal_type, ''), b.service_kind, b.starts_at, b.ends_at,
         b.status, b.total_price, b.price_unit, coalesce(s.city, h.city, ''), coalesce(s.district, h.district, ''),
         coalesce(b.notes, ''), b.created_at
    from public.bookings b
    left join public.profiles o on o.id = b.owner_id
    left join public.profiles h on h.id = b.sitter_id
    left join public.sitter_profiles s on s.id = b.sitter_id
    left join public.pets pt on pt.id = b.pet_id
   where p_status is null or b.status = p_status
   order by b.starts_at desc
   limit 300;
end;
$$;

-- NearMe walk sessions. A position is only returned while the walk is live, rounded to ~1 km.
create or replace function private.admin_walk_sessions()
returns table (
  id uuid, user_id uuid, user_name text, pet_name text, pet_species text, status text,
  status_message text, started_at timestamptz, ended_at timestamptz,
  latitude float8, longitude float8)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select w.id, w.user_id, coalesce(p.full_name, ''), w.pet_name, w.pet_species,
         case when w.status <> 'ended' and w.expires_at < now() then 'ended' else w.status end,
         w.status_message, w.started_at, w.ended_at,
         case when w.status = 'active' and w.expires_at > now() then round(l.latitude, 2)::float8 end,
         case when w.status = 'active' and w.expires_at > now() then round(l.longitude, 2)::float8 end
    from public.walk_sessions w
    left join public.profiles p on p.id = w.user_id
    left join private.walk_locations l on l.session_id = w.id
   order by w.started_at desc
   limit 300;
end;
$$;

-- Map layer: only people and listings that chose to share a location.
create or replace function private.admin_map_points()
returns table (kind text, id uuid, title text, subtitle text, latitude float8, longitude float8, verified boolean)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select 'walk', w.id, coalesce(p.full_name, ''), trim(w.pet_name || ' · ' || w.pet_species),
         round(l.latitude, 2)::float8, round(l.longitude, 2)::float8, false
    from public.walk_sessions w
    join private.walk_locations l on l.session_id = w.id
    left join public.profiles p on p.id = w.user_id
   where w.status = 'active' and w.expires_at > now()
  union all
  select 'helper', s.id, s.full_name, trim(both ' ·' from coalesce(s.district, '') || ' · ' || coalesce(s.city, '')),
         s.latitude::float8, s.longitude::float8, s.verified
    from public.sitter_profiles s
   where s.active and s.latitude is not null and s.longitude is not null
  union all
  select 'adoption', a.id, a.name, a.species || ' · ' || a.city,
         a.latitude::float8, a.longitude::float8, false
    from public.adoption_listings a
   where a.status = 'active' and a.latitude is not null and a.longitude is not null;
end;
$$;

-- User directory with activity counts. No email or phone.
create or replace function private.admin_users()
returns table (
  id uuid, full_name text, city text, district text, role text, suspended boolean, avatar_path text,
  created_at timestamptz, pets_count bigint, listings_count bigint, walks_count bigint,
  requests_count bigint, last_active_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select p.id, p.full_name, coalesce(p.city, ''), coalesce(p.district, ''), p.role, p.suspended, p.avatar_path,
         p.created_at,
         (select count(*) from public.pets x where x.owner_id = p.id),
         (select count(*) from public.adoption_listings x where x.owner_id = p.id and x.status <> 'deleted'),
         (select count(*) from public.walk_sessions x where x.user_id = p.id),
         (select count(*) from public.bookings x where x.owner_id = p.id or x.sitter_id = p.id),
         greatest(p.created_at,
                  (select max(x.started_at) from public.walk_sessions x where x.user_id = p.id),
                  (select max(x.updated_at) from public.adoption_listings x where x.owner_id = p.id),
                  (select max(x.created_at) from public.bookings x where x.owner_id = p.id or x.sitter_id = p.id))
    from public.profiles p
   order by p.created_at desc
   limit 500;
end;
$$;

-- Pet profiles without care notes or emergency contacts.
create or replace function private.admin_pets()
returns table (
  id uuid, owner_id uuid, owner_name text, name text, animal_type text, breed text, age numeric,
  weight numeric, photo_path text, created_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select pt.id, pt.owner_id, coalesce(p.full_name, ''), pt.name, pt.animal_type, coalesce(pt.breed, ''),
         pt.age, pt.weight, pt.photo_path, pt.created_at
    from public.pets pt
    left join public.profiles p on p.id = pt.owner_id
   order by pt.created_at desc
   limit 500;
end;
$$;

-- Moderation: an administrator may cancel an open walking request. Everything else is unchanged.
create or replace function private.admin_action(p_action text, p_id uuid, p_value text, p_note text)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare v_sitter uuid; v_owner uuid;
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  if p_action='verification' and p_value in ('approved','declined') then
    update public.verification_requests set status=p_value,note=p_note,reviewed_by=auth.uid(),reviewed_at=now() where id=p_id returning sitter_id into v_sitter;
    if not found then raise exception 'FORBIDDEN'; end if;
    update public.sitter_profiles set verified=(p_value='approved') where id=v_sitter;
    update public.profiles set role=case when p_value='approved' then 'sitter' else 'owner' end where id=v_sitter and role<>'admin';
    insert into public.notifications(user_id,title,body) values(v_sitter,'Müraciətiniz yoxlanıldı',case when p_value='approved' then 'Baxıcı profiliniz təsdiqləndi.' else 'Müraciətiniz qəbul edilmədi. Profilinizdə qeydə baxın.' end);
  elsif p_action='suspend' and p_value in ('true','false') and p_id<>auth.uid() then
    update public.profiles set suspended=p_value::boolean where id=p_id and role<>'admin';
    if not found then raise exception 'FORBIDDEN'; end if;
  elsif p_action='review' and p_value in ('true','false') then
    update public.reviews set hidden=p_value::boolean where id=p_id;
    if not found then raise exception 'FORBIDDEN'; end if;
  elsif p_action='report' then
    update public.reports set resolved=true where id=p_id;
    if not found then raise exception 'FORBIDDEN'; end if;
  elsif p_action='listing' and p_value in ('deleted','active') then
    update public.adoption_listings
       set status = p_value, updated_at = now()
     where id = p_id
       and status <> 'adopted'
       and (p_value <> 'active' or cardinality(photos) > 0);
    if not found then raise exception 'FORBIDDEN'; end if;
  elsif p_action='booking' and p_value='cancelled' then
    update public.bookings set status='cancelled'
     where id=p_id and status in ('pending','accepted')
    returning owner_id, sitter_id into v_owner, v_sitter;
    if not found then raise exception 'FORBIDDEN'; end if;
    insert into public.notifications(user_id,title,body,booking_id) values
      (v_owner,'Gəzinti sorğusu ləğv edildi','Sorğu moderator tərəfindən ləğv edildi.',p_id),
      (v_sitter,'Gəzinti sorğusu ləğv edildi','Sorğu moderator tərəfindən ləğv edildi.',p_id);
  else raise exception 'FORBIDDEN'; end if;
  insert into private.admin_audit(actor_id,action,target_id,value,note) values(auth.uid(),p_action,p_id,p_value,p_note);
end;
$function$;

-- Public wrappers (security invoker), the same pattern as the other RPCs.
create or replace function public.admin_overview() returns jsonb
language sql stable set search_path to '' as $$ select private.admin_overview(); $$;

create or replace function public.admin_walk_requests(p_status text default null)
returns table (
  id uuid, owner_id uuid, owner_name text, helper_id uuid, helper_name text,
  pet_name text, pet_type text, service_kind text, starts_at timestamptz, ends_at timestamptz,
  status text, total_price numeric, price_unit text, city text, district text, notes text,
  created_at timestamptz)
language sql stable set search_path to '' as $$ select * from private.admin_walk_requests(p_status); $$;

create or replace function public.admin_walk_sessions()
returns table (
  id uuid, user_id uuid, user_name text, pet_name text, pet_species text, status text,
  status_message text, started_at timestamptz, ended_at timestamptz, latitude float8, longitude float8)
language sql stable set search_path to '' as $$ select * from private.admin_walk_sessions(); $$;

create or replace function public.admin_map_points()
returns table (kind text, id uuid, title text, subtitle text, latitude float8, longitude float8, verified boolean)
language sql stable set search_path to '' as $$ select * from private.admin_map_points(); $$;

create or replace function public.admin_users()
returns table (
  id uuid, full_name text, city text, district text, role text, suspended boolean, avatar_path text,
  created_at timestamptz, pets_count bigint, listings_count bigint, walks_count bigint,
  requests_count bigint, last_active_at timestamptz)
language sql stable set search_path to '' as $$ select * from private.admin_users(); $$;

create or replace function public.admin_pets()
returns table (
  id uuid, owner_id uuid, owner_name text, name text, animal_type text, breed text, age numeric,
  weight numeric, photo_path text, created_at timestamptz)
language sql stable set search_path to '' as $$ select * from private.admin_pets(); $$;

revoke all on function private.admin_overview() from public, anon;
revoke all on function private.admin_walk_requests(text) from public, anon;
revoke all on function private.admin_walk_sessions() from public, anon;
revoke all on function private.admin_map_points() from public, anon;
revoke all on function private.admin_users() from public, anon;
revoke all on function private.admin_pets() from public, anon;
revoke all on function public.admin_overview() from public, anon;
revoke all on function public.admin_walk_requests(text) from public, anon;
revoke all on function public.admin_walk_sessions() from public, anon;
revoke all on function public.admin_map_points() from public, anon;
revoke all on function public.admin_users() from public, anon;
revoke all on function public.admin_pets() from public, anon;
grant execute on function private.admin_overview() to authenticated;
grant execute on function private.admin_walk_requests(text) to authenticated;
grant execute on function private.admin_walk_sessions() to authenticated;
grant execute on function private.admin_map_points() to authenticated;
grant execute on function private.admin_users() to authenticated;
grant execute on function private.admin_pets() to authenticated;
grant execute on function public.admin_overview() to authenticated;
grant execute on function public.admin_walk_requests(text) to authenticated;
grant execute on function public.admin_walk_sessions() to authenticated;
grant execute on function public.admin_map_points() to authenticated;
grant execute on function public.admin_users() to authenticated;
grant execute on function public.admin_pets() to authenticated;
