-- The admin audit log needs a target: a broadcast is audited with its own id.
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
  v_id uuid;
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
  values (auth.uid(), v_title, v_body, p_audience, v_city, v_count)
  returning id into v_id;
  insert into private.admin_audit (actor_id, action, target_id, value, note)
  values (auth.uid(), 'broadcast', v_id, p_audience || coalesce(':' || v_city, ''), v_title);
  return v_count;
end;
$$;
