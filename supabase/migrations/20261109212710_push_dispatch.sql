-- Phone push for every notification. After each insert statement on public.notifications, pg_net calls the
-- dispatch-push Edge Function, which claims the queue with claim_push_batch() and sends through FCM
-- (native Android, platform 'fcm') or Expo (the Expo app, platform 'expo'). The call is best effort and
-- never blocks the insert. Notifications that existed before this migration are marked as already sent so
-- nobody receives a burst of old pushes.

create extension if not exists pg_net with schema extensions;

alter table public.push_devices
  add column if not exists platform text not null default 'expo';
alter table public.push_devices drop constraint if exists push_devices_platform_check;
alter table public.push_devices
  add constraint push_devices_platform_check check (platform in ('expo', 'fcm'));
grant insert (platform), update (platform) on public.push_devices to authenticated;

update public.notifications set push_sent_at = now() where push_sent_at is null;

create or replace function private.kick_push_dispatch()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $function$
begin
  perform net.http_post(
    url := 'https://scrnrhtorjhuzfznbjhg.supabase.co/functions/v1/dispatch-push',
    body := '{}'::jsonb,
    headers := '{"Content-Type": "application/json"}'::jsonb,
    timeout_milliseconds := 10000
  );
  return null;
exception when others then
  return null;
end;
$function$;

revoke all on function private.kick_push_dispatch() from public, anon, authenticated;

drop trigger if exists notifications_push_kick on public.notifications;
create trigger notifications_push_kick
  after insert on public.notifications
  for each statement execute function private.kick_push_dispatch();
