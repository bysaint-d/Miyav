-- Members can clear their own notifications.
create policy notifications_delete_own on public.notifications
  for delete to authenticated
  using (user_id = (select auth.uid()) and (select private.is_active()));

grant delete on public.notifications to authenticated;
