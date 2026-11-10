-- Moderation: administrators can read members' conversations and chat photos. This is disclosed in the Terms
-- of Service members accept (terms version 2026-11-10). Opening a conversation is written to the audit log
-- with the administrator's id, so every read stays accountable. Additive only.

create or replace function private.admin_conversations(p_search text default null)
returns table (
  id uuid, owner_id uuid, owner_name text, sitter_id uuid, sitter_name text, last_message_at timestamptz,
  last_body text, last_has_image boolean, message_count bigint, created_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
declare
  v_search text := nullif(btrim(coalesce(p_search, '')), '');
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  v_search := case when v_search is null then null
                   else '%' || replace(replace(replace(v_search, '\', '\\'), '%', '\%'), '_', '\_') || '%' end;
  return query
  select c.id, c.owner_id, coalesce(po.full_name, c.owner_name, ''), c.sitter_id, coalesce(ps.full_name, c.sitter_name, ''),
         c.last_message_at, coalesce(m.body, ''), m.image_path is not null,
         (select count(*) from public.messages x where x.conversation_id = c.id), c.created_at
    from public.conversations c
    left join public.profiles po on po.id = c.owner_id
    left join public.profiles ps on ps.id = c.sitter_id
    left join lateral (select x.body, x.image_path from public.messages x
                        where x.conversation_id = c.id order by x.created_at desc limit 1) m on true
   where exists (select 1 from public.messages x where x.conversation_id = c.id)
     and (v_search is null
          or coalesce(po.full_name, c.owner_name, '') ilike v_search
          or coalesce(ps.full_name, c.sitter_name, '') ilike v_search
          or exists (select 1 from public.messages x where x.conversation_id = c.id and x.body ilike v_search))
   order by c.last_message_at desc nulls last
   limit 300;
end;
$$;

create or replace function private.admin_conversation_messages(p_conversation uuid)
returns table (
  id uuid, conversation_id uuid, sender_id uuid, body text, image_path text, walk_listing_id uuid,
  price_per_hour numeric, kind text, created_at timestamptz)
language plpgsql
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  if not exists (select 1 from public.conversations c where c.id = p_conversation) then raise exception 'NOT_FOUND'; end if;
  insert into private.admin_audit (actor_id, action, target_id, value, note)
  values (auth.uid(), 'read_conversation', p_conversation, '', '');
  return query
  select m.id, m.conversation_id, m.sender_id, m.body, m.image_path, m.walk_listing_id, m.price_per_hour, m.kind, m.created_at
    from public.messages m
   where m.conversation_id = p_conversation
   order by m.created_at asc
   limit 1000;
end;
$$;

create or replace function public.admin_conversations(p_search text default null)
returns table (
  id uuid, owner_id uuid, owner_name text, sitter_id uuid, sitter_name text, last_message_at timestamptz,
  last_body text, last_has_image boolean, message_count bigint, created_at timestamptz)
language sql stable set search_path to '' as $$ select * from private.admin_conversations(p_search); $$;

create or replace function public.admin_conversation_messages(p_conversation uuid)
returns table (
  id uuid, conversation_id uuid, sender_id uuid, body text, image_path text, walk_listing_id uuid,
  price_per_hour numeric, kind text, created_at timestamptz)
language sql set search_path to '' as $$ select * from private.admin_conversation_messages(p_conversation); $$;

revoke all on function private.admin_conversations(text) from public, anon;
revoke all on function private.admin_conversation_messages(uuid) from public, anon;
revoke all on function public.admin_conversations(text) from public, anon;
revoke all on function public.admin_conversation_messages(uuid) from public, anon;
grant execute on function private.admin_conversations(text) to authenticated;
grant execute on function private.admin_conversation_messages(uuid) to authenticated;
grant execute on function public.admin_conversations(text) to authenticated;
grant execute on function public.admin_conversation_messages(uuid) to authenticated;

create policy message_image_admin_read on storage.objects for select to authenticated
  using (bucket_id = 'messages' and private.is_admin());
