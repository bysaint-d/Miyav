-- Self-service account deletion (App Store guideline 5.1.1(v), Google Play account deletion policy).
-- Only the delete-account Edge Function (service_role) may call these. Storage files are removed there
-- through the Storage API, because SQL deletes on storage.objects are blocked by protect_objects_delete.

create or replace function private.account_files(p_user uuid)
returns table (bucket_id text, name text)
language sql
stable
security definer
set search_path = ''
as $$
  select o.bucket_id, o.name
    from storage.objects o
   where o.bucket_id in ('avatars', 'pets', 'adoptions', 'messages')
     and (o.owner_id = p_user::text or (storage.foldername(o.name))[1] = p_user::text);
$$;

-- Removes every row that belongs to the user, then the auth user, in one transaction.
-- Admin accounts are refused: their moderation history must stay attributable.
create or replace function private.delete_account_data(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_conversations uuid[];
  v_bookings uuid[];
begin
  if p_user is null or not exists (select 1 from auth.users where id = p_user) then
    raise exception 'ACCOUNT_NOT_FOUND';
  end if;
  if exists (select 1 from public.profiles where id = p_user and role = 'admin') then
    raise exception 'ACCOUNT_ADMIN';
  end if;

  select coalesce(array_agg(id), '{}') into v_conversations
    from public.conversations where owner_id = p_user or sitter_id = p_user;
  select coalesce(array_agg(id), '{}') into v_bookings
    from public.bookings
   where owner_id = p_user or sitter_id = p_user
      or pet_id in (select id from public.pets where owner_id = p_user);

  -- Walks this user was hired for go back on the board for their owners.
  update public.walk_listings
     set status = 'open', walker_id = null, agreed_price_per_hour = null
   where walker_id = p_user and owner_id <> p_user and status = 'assigned';

  delete from public.notifications
   where user_id = p_user or conversation_id = any (v_conversations) or booking_id = any (v_bookings);
  delete from public.messages
   where sender_id = p_user or conversation_id = any (v_conversations) or booking_id = any (v_bookings);
  delete from public.conversations where id = any (v_conversations);
  delete from public.reviews
   where owner_id = p_user or sitter_id = p_user or booking_id = any (v_bookings);
  delete from public.bookings where id = any (v_bookings);

  delete from public.verification_requests where sitter_id = p_user;
  update public.verification_requests set reviewed_by = null where reviewed_by = p_user;
  delete from public.services where sitter_id = p_user;
  delete from public.availability where sitter_id = p_user;
  delete from public.sitter_profiles where id = p_user;

  delete from public.reports where reporter_id = p_user or reported_id = p_user;
  delete from public.push_devices where user_id = p_user;
  delete from public.adoption_favorites where user_id = p_user;
  delete from public.adoption_applications where applicant_id = p_user;
  delete from public.adoption_listings where owner_id = p_user;
  delete from public.pets where owner_id = p_user;

  -- Walk listings, offers and sessions cascade from the profile.
  delete from public.profiles where id = p_user;
  delete from auth.users where id = p_user;
end;
$$;

create or replace function public.account_files(p_user uuid)
returns table (bucket_id text, name text)
language sql
stable
set search_path = ''
as $$ select * from private.account_files(p_user); $$;

create or replace function public.delete_account_data(p_user uuid)
returns void
language sql
set search_path = ''
as $$ select private.delete_account_data(p_user); $$;

revoke all on function private.account_files(uuid) from public, anon, authenticated;
revoke all on function private.delete_account_data(uuid) from public, anon, authenticated;
revoke all on function public.account_files(uuid) from public, anon, authenticated;
revoke all on function public.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function private.account_files(uuid) to service_role;
grant execute on function private.delete_account_data(uuid) to service_role;
grant execute on function public.account_files(uuid) to service_role;
grant execute on function public.delete_account_data(uuid) to service_role;
