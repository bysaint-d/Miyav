-- Walk history. While a walk is active, every live position (private.walk_locations) is also appended to
-- public.walk_route_points, which only the walker can read. Standing still (< 12 m) and GPS jumps
-- (> 50 m/s) are skipped, and a walk keeps at most 5000 points. my_walks() lists the member's walks with
-- distance and start/end points, my_walk_route() returns one route, delete_my_walk() removes a finished
-- walk and its route.

create table if not exists public.walk_route_points (
  session_id uuid not null references public.walk_sessions (id) on delete cascade,
  recorded_at timestamptz not null default clock_timestamp(),
  latitude numeric(8, 5) not null check (latitude between 38 and 42),
  longitude numeric(8, 5) not null check (longitude between 44 and 51),
  primary key (session_id, recorded_at)
);

alter table public.walk_route_points enable row level security;
create policy walk_route_points_own_read on public.walk_route_points
  for select to authenticated
  using (exists (select 1 from public.walk_sessions s where s.id = session_id and s.user_id = (select auth.uid())));
revoke all on public.walk_route_points from anon, authenticated;
grant select on public.walk_route_points to authenticated;

create or replace function private.record_walk_point()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare
  v_last public.walk_route_points;
  v_meters double precision;
begin
  select * into v_last from public.walk_route_points
   where session_id = new.session_id order by recorded_at desc limit 1;
  if found then
    v_meters := private.distance_m(v_last.latitude::float8, v_last.longitude::float8, new.latitude::float8, new.longitude::float8);
    if v_meters < 12 then
      return null;
    end if;
    if v_meters / greatest(extract(epoch from clock_timestamp() - v_last.recorded_at), 1) > 50 then
      return null;
    end if;
    if (select count(*) from public.walk_route_points where session_id = new.session_id) >= 5000 then
      return null;
    end if;
  end if;
  insert into public.walk_route_points (session_id, latitude, longitude)
  values (new.session_id, new.latitude, new.longitude)
  on conflict do nothing;
  return null;
end;
$function$;

revoke all on function private.record_walk_point() from public, anon, authenticated;

drop trigger if exists walk_locations_route on private.walk_locations;
create trigger walk_locations_route
  after insert or update of latitude, longitude on private.walk_locations
  for each row execute function private.record_walk_point();

create or replace function public.my_walks(p_limit integer default 50, p_offset integer default 0)
 returns table (
   id uuid, pet_name text, pet_species text, started_at timestamptz, ended_at timestamptz, open boolean,
   distance_m double precision, points integer,
   start_lat double precision, start_lng double precision, end_lat double precision, end_lng double precision
 )
 language sql
 stable
 security definer
 set search_path to ''
as $function$
  with mine as (
    select s.* from public.walk_sessions s
     where s.user_id = auth.uid()
     order by s.started_at desc
     limit least(greatest(p_limit, 1), 200) offset greatest(p_offset, 0)
  ), legs as (
    select p.session_id, p.recorded_at, p.latitude::float8 lat, p.longitude::float8 lng,
           lag(p.latitude::float8) over w plat, lag(p.longitude::float8) over w plng
      from public.walk_route_points p join mine m on m.id = p.session_id
    window w as (partition by p.session_id order by p.recorded_at)
  ), sums as (
    select session_id,
           coalesce(sum(case when plat is null then 0 else private.distance_m(plat, plng, lat, lng) end), 0) dist,
           count(*)::int pts,
           (array_agg(lat order by recorded_at))[1] slat, (array_agg(lng order by recorded_at))[1] slng,
           (array_agg(lat order by recorded_at desc))[1] elat, (array_agg(lng order by recorded_at desc))[1] elng
      from legs group by session_id
  )
  select m.id, m.pet_name, m.pet_species, m.started_at,
         case when m.status = 'ended' then coalesce(m.ended_at, m.last_seen_at)
              when m.expires_at < now() then m.last_seen_at end,
         m.status <> 'ended' and m.expires_at >= now(),
         coalesce(s.dist, 0), coalesce(s.pts, 0), s.slat, s.slng, s.elat, s.elng
    from mine m left join sums s on s.session_id = m.id
   order by m.started_at desc;
$function$;

create or replace function public.my_walk_route(p_session uuid)
 returns table (latitude double precision, longitude double precision, recorded_at timestamptz)
 language sql
 stable
 security definer
 set search_path to ''
as $function$
  select p.latitude::float8, p.longitude::float8, p.recorded_at
    from public.walk_route_points p
   where p.session_id = p_session
     and exists (select 1 from public.walk_sessions s where s.id = p_session and s.user_id = auth.uid())
   order by p.recorded_at;
$function$;

create or replace function public.delete_my_walk(p_session uuid)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
begin
  if not exists (select 1 from public.walk_sessions
                  where id = p_session and user_id = auth.uid()
                    and (status = 'ended' or expires_at < now())) then
    raise exception 'FORBIDDEN';
  end if;
  delete from private.walk_locations where session_id = p_session;
  delete from public.walk_session_pets where session_id = p_session;
  delete from public.walk_sessions where id = p_session;
end;
$function$;

revoke all on function public.my_walks(integer, integer) from public, anon;
revoke all on function public.my_walk_route(uuid) from public, anon;
revoke all on function public.delete_my_walk(uuid) from public, anon;
grant execute on function public.my_walks(integer, integer) to authenticated;
grant execute on function public.my_walk_route(uuid) to authenticated;
grant execute on function public.delete_my_walk(uuid) to authenticated;
