-- Admin push broadcasts: an administrator sends a notification to every member, to owners or to walkers
-- (optionally in one city). Each recipient gets a row in public.notifications, which the apps show as a
-- system notification. Sends are logged and limited to 10 per hour. Additive only.

create table private.admin_broadcasts (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null references public.profiles(id),
  title text not null check (length(btrim(title)) between 1 and 80),
  body text not null check (length(btrim(body)) between 1 and 500),
  audience text not null check (audience in ('all', 'owners', 'sitters')),
  city text check (city is null or city in ('Bakı', 'Sumqayıt', 'Abşeron')),
  recipients integer not null,
  created_at timestamptz not null default now()
);

alter table private.admin_broadcasts enable row level security;
revoke all on private.admin_broadcasts from public, anon, authenticated;

create or replace function private.broadcast_targets(p_audience text, p_city text)
returns setof uuid
language sql
stable
security definer
set search_path to ''
as $$
  select p.id from public.profiles p
   where not p.suspended
     and (p_audience = 'all'
          or (p_audience = 'owners' and p.role = 'owner')
          or (p_audience = 'sitters' and p.role = 'sitter'))
     and (p_city is null or p.city = p_city);
$$;
revoke all on function private.broadcast_targets(text, text) from public, anon, authenticated;

create or replace function private.admin_broadcast_audience(p_audience text, p_city text)
returns integer
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  if p_audience not in ('all', 'owners', 'sitters') then raise exception 'BROADCAST_AUDIENCE'; end if;
  return (select count(*) from private.broadcast_targets(p_audience, nullif(btrim(p_city), '')));
end;
$$;

create or replace function private.admin_broadcast(p_title text, p_body text, p_audience text, p_city text)
returns integer
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_title text := btrim(coalesce(p_title, ''));
  v_body text := btrim(coalesce(p_body, ''));
  v_city text := nullif(btrim(coalesce(p_city, '')), '');
  v_count integer;
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  if length(v_title) not between 1 and 80 or length(v_body) not between 1 and 500 then
    raise exception 'BROADCAST_TEXT';
  end if;
  if p_audience not in ('all', 'owners', 'sitters') then raise exception 'BROADCAST_AUDIENCE'; end if;
  if v_city is not null and v_city not in ('Bakı', 'Sumqayıt', 'Abşeron') then raise exception 'BROADCAST_AUDIENCE'; end if;
  if (select count(*) from private.admin_broadcasts where created_at > now() - interval '1 hour') >= 10 then
    raise exception 'BROADCAST_RATE';
  end if;
  insert into public.notifications (user_id, title, body)
  select t, v_title, v_body from private.broadcast_targets(p_audience, v_city) t;
  get diagnostics v_count = row_count;
  insert into private.admin_broadcasts (actor_id, title, body, audience, city, recipients)
  values (auth.uid(), v_title, v_body, p_audience, v_city, v_count);
  insert into private.admin_audit (actor_id, action, value, note)
  values (auth.uid(), 'broadcast', p_audience || coalesce(':' || v_city, ''), v_title);
  return v_count;
end;
$$;

create or replace function private.admin_broadcasts_recent()
returns table (id uuid, title text, body text, audience text, city text, recipients integer,
               created_at timestamptz, actor_name text)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select b.id, b.title, b.body, b.audience, b.city, b.recipients, b.created_at, coalesce(p.full_name, '')
    from private.admin_broadcasts b left join public.profiles p on p.id = b.actor_id
   order by b.created_at desc
   limit 30;
end;
$$;

create or replace function public.admin_broadcast(p_title text, p_body text, p_audience text, p_city text default null)
returns integer language sql set search_path to ''
as $$ select private.admin_broadcast(p_title, p_body, p_audience, p_city); $$;

create or replace function public.admin_broadcast_audience(p_audience text, p_city text default null)
returns integer language sql stable set search_path to ''
as $$ select private.admin_broadcast_audience(p_audience, p_city); $$;

create or replace function public.admin_broadcasts_recent()
returns table (id uuid, title text, body text, audience text, city text, recipients integer,
               created_at timestamptz, actor_name text)
language sql stable set search_path to ''
as $$ select * from private.admin_broadcasts_recent(); $$;

revoke all on function private.admin_broadcast(text, text, text, text) from public, anon;
revoke all on function private.admin_broadcast_audience(text, text) from public, anon;
revoke all on function private.admin_broadcasts_recent() from public, anon;
revoke all on function public.admin_broadcast(text, text, text, text) from public, anon;
revoke all on function public.admin_broadcast_audience(text, text) from public, anon;
revoke all on function public.admin_broadcasts_recent() from public, anon;
grant execute on function private.admin_broadcast(text, text, text, text) to authenticated;
grant execute on function private.admin_broadcast_audience(text, text) to authenticated;
grant execute on function private.admin_broadcasts_recent() to authenticated;
grant execute on function public.admin_broadcast(text, text, text, text) to authenticated;
grant execute on function public.admin_broadcast_audience(text, text) to authenticated;
grant execute on function public.admin_broadcasts_recent() to authenticated;
