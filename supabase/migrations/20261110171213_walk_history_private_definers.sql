-- The walk history functions ran as SECURITY DEFINER in the exposed schema. Their bodies move to private
-- unchanged and the public names become invoker wrappers with the same signature, so clients keep calling
-- /rpc/my_walks, /rpc/my_walk_route and /rpc/delete_my_walk.
do $migration$
declare
  f record;
begin
  for f in
    select p.oid, p.proname, pg_get_function_arguments(p.oid) as args, pg_get_function_identity_arguments(p.oid) as ident,
           pg_get_function_result(p.oid) as res, p.provolatile,
           array_to_string(p.proargnames[1:p.pronargs], ', ') as names
      from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prosecdef and p.proname in ('delete_my_walk', 'my_walk_route', 'my_walks')
  loop
    execute replace(pg_get_functiondef(f.oid), 'FUNCTION public.' || f.proname || '(', 'FUNCTION private.' || f.proname || '(');
    execute format('revoke all on function private.%I(%s) from public, anon', f.proname, f.ident);
    execute format('grant execute on function private.%I(%s) to authenticated', f.proname, f.ident);
    execute format(
      'create or replace function public.%I(%s) returns %s language sql %s security invoker set search_path to '''' as $body$ select %s private.%I(%s); $body$',
      f.proname, f.args, f.res, case f.provolatile when 's' then 'stable' else 'volatile' end,
      case when f.res like 'TABLE%' then '* from' else '' end, f.proname, f.names);
    execute format('revoke all on function public.%I(%s) from public, anon', f.proname, f.ident);
    execute format('grant execute on function public.%I(%s) to authenticated', f.proname, f.ident);
  end loop;
end;
$migration$;
