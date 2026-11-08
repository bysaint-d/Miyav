-- Additive admin listing visibility and listing archive through the existing admin_action RPC.
-- Does not drop tables, disable RLS, or grant clients UPDATE on role/suspended.
-- Hosted version: 20261107214742.

create policy adoption_admin_read
  on public.adoption_listings
  for select
  to authenticated
  using (private.is_admin());

create policy adoption_image_admin_read
  on storage.objects
  for select
  to authenticated
  using ((bucket_id = 'adoptions'::text) and private.is_admin());

create or replace function private.admin_action(p_action text, p_id uuid, p_value text, p_note text)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare v_sitter uuid;
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
  else raise exception 'FORBIDDEN'; end if;
  insert into private.admin_audit(actor_id,action,target_id,value,note) values(auth.uid(),p_action,p_id,p_value,p_note);
end;
$function$;
