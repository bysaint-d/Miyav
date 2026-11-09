-- Blocking abusive users (App Store guideline 1.2). A block is private to the blocker; clients hide the
-- blocked member's listings and chats, and the message guard stops messages in either direction.

create table if not exists public.user_blocks (
  blocker_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint user_blocks_not_self check (blocker_id <> blocked_id)
);

create index if not exists user_blocks_blocked_idx on public.user_blocks (blocked_id);

alter table public.user_blocks enable row level security;

create policy user_blocks_select_own on public.user_blocks
  for select to authenticated using (blocker_id = (select auth.uid()));
create policy user_blocks_insert_own on public.user_blocks
  for insert to authenticated with check (blocker_id = (select auth.uid()) and private.is_active());
create policy user_blocks_delete_own on public.user_blocks
  for delete to authenticated using (blocker_id = (select auth.uid()));

revoke all on public.user_blocks from anon, authenticated;
grant select, insert, delete on public.user_blocks to authenticated;

create or replace function private.message_guard()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare c public.conversations;
begin
  if not private.is_active() or new.sender_id <> auth.uid() or not private.is_participant(new.conversation_id) then raise exception 'FORBIDDEN'; end if;
  select * into c from public.conversations where id = new.conversation_id for update;
  if exists (select 1 from public.profiles where id in (c.owner_id, c.sitter_id) and suspended) then raise exception 'ACCOUNT_SUSPENDED'; end if;
  if exists (select 1 from public.user_blocks
              where (blocker_id = c.owner_id and blocked_id = c.sitter_id)
                 or (blocker_id = c.sitter_id and blocked_id = c.owner_id)) then
    raise exception 'USER_BLOCKED';
  end if;
  if new.booking_id is not null and not exists (select 1 from public.bookings where id = new.booking_id and owner_id = c.owner_id and sitter_id = c.sitter_id) then raise exception 'FORBIDDEN'; end if;
  if new.walk_listing_id is not null and not exists (select 1 from public.walk_listings where id = new.walk_listing_id and owner_id in (c.owner_id, c.sitter_id)) then raise exception 'FORBIDDEN'; end if;
  if new.image_path is not null and (split_part(new.image_path, '/', 1) <> c.id::text or split_part(new.image_path, '/', 2) <> auth.uid()::text or not exists (select 1 from storage.objects where bucket_id = 'messages' and name = new.image_path)) then raise exception 'FORBIDDEN'; end if;
  if (select count(*) from public.messages where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then raise exception 'too many messages'; end if;
  new.created_at := clock_timestamp();
  update public.conversations set last_message_at = new.created_at where id = c.id;
  if new.price_per_hour is null then
    insert into public.notifications (user_id, title, body, conversation_id)
    values (case when new.sender_id = c.owner_id then c.sitter_id else c.owner_id end, 'Yeni mesaj', 'Yeni şəxsi mesajınız var.', c.id);
  end if;
  return new;
end;
$function$;
