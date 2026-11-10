-- Faster reads. The admin read policy on adoption listings called is_admin() for every row and ran next to the
-- public policy; one policy with the check evaluated once per query replaces both. Storage admin checks are
-- wrapped the same way, and foreign keys used by triggers and joins get indexes. Same access as before.

drop policy if exists adoption_admin_read on public.adoption_listings;
drop policy if exists adoption_read on public.adoption_listings;
create policy adoption_read on public.adoption_listings for select to anon, authenticated
  using ((status = 'active' and private.adoption_owner_active(owner_id))
         or (owner_id = (select auth.uid()) and (select private.is_active()))
         or (select private.is_admin()));

drop policy if exists adoption_image_admin_read on storage.objects;
create policy adoption_image_admin_read on storage.objects for select to authenticated
  using (bucket_id = 'adoptions' and (select private.is_admin()));

drop policy if exists message_image_admin_read on storage.objects;
create policy message_image_admin_read on storage.objects for select to authenticated
  using (bucket_id = 'messages' and (select private.is_admin()));

drop policy if exists receipt_read on storage.objects;
create policy receipt_read on storage.objects for select to authenticated
  using (bucket_id = 'receipts'
         and (split_part(name, '/', 1) = (select auth.uid())::text or (select private.is_admin())));

create index if not exists walk_listings_pet_idx on public.walk_listings (pet_id) where pet_id is not null;
create index if not exists walk_sessions_listing_idx on public.walk_sessions (walk_listing_id) where walk_listing_id is not null;
create index if not exists walk_session_pets_pet_idx on public.walk_session_pets (pet_id);
