-- Additive adoption applications and profile language.
-- Does not drop, rename, or rewrite existing tables.
-- profiles.role and profiles.suspended stay selectable by authenticated:
-- the Expo client reads them, and RLS already limits rows to the owner or an admin.
-- They remain non-updatable by clients.

alter table public.profiles
  add column preferred_language text not null default 'az'
  check (preferred_language in ('az', 'en', 'ru'));

revoke all (preferred_language) on public.profiles from public, anon;
grant select (preferred_language), update (preferred_language) on public.profiles to authenticated;

create table public.adoption_applications (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.adoption_listings (id) on delete cascade,
  applicant_id uuid not null default auth.uid() references public.profiles (id),
  message text not null check (length(trim(message)) between 10 and 2000),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'rejected', 'cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index adoption_applications_listing_idx
  on public.adoption_applications (listing_id, created_at desc);
create index adoption_applications_applicant_idx
  on public.adoption_applications (applicant_id, created_at desc);
create unique index adoption_applications_one_accepted_idx
  on public.adoption_applications (listing_id)
  where status = 'accepted';
create unique index adoption_applications_one_pending_idx
  on public.adoption_applications (listing_id, applicant_id)
  where status = 'pending';

alter table public.adoption_applications enable row level security;
revoke all on public.adoption_applications from anon, authenticated;
grant select on public.adoption_applications to authenticated;
grant insert (listing_id, message) on public.adoption_applications to authenticated;

create policy adoption_applications_read
  on public.adoption_applications
  for select
  to authenticated
  using (
    (select private.is_active())
    and (
      applicant_id = (select auth.uid())
      or (select private.is_admin())
      or exists (
        select 1
        from public.adoption_listings listing
        where listing.id = listing_id
          and listing.owner_id = (select auth.uid())
      )
    )
  );

create policy adoption_applications_insert
  on public.adoption_applications
  for insert
  to authenticated
  with check (
    applicant_id = (select auth.uid())
    and status = 'pending'
    and (select private.is_active())
    and exists (
      select 1
      from public.adoption_listings listing
      where listing.id = listing_id
        and listing.status = 'active'
        and listing.owner_id <> (select auth.uid())
        and private.adoption_owner_active(listing.owner_id)
    )
  );

create function private.touch_adoption_application()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger adoption_applications_touch
  before update on public.adoption_applications
  for each row execute function private.touch_adoption_application();

create function private.guard_adoption_application()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  listing public.adoption_listings;
begin
  if not private.is_active() then
    raise exception 'FORBIDDEN';
  end if;
  if new.applicant_id is distinct from auth.uid() or new.status is distinct from 'pending' then
    raise exception 'FORBIDDEN';
  end if;
  select * into listing
  from public.adoption_listings
  where id = new.listing_id
  for update;
  if not found
     or listing.status <> 'active'
     or listing.owner_id = new.applicant_id
     or not private.adoption_owner_active(listing.owner_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.notifications (user_id, title, body)
  values (listing.owner_id, 'Yeni övladlığa müraciət', 'Elanınıza yeni müraciət gəldi.');
  return new;
end;
$$;

create trigger adoption_applications_guard
  before insert on public.adoption_applications
  for each row execute function private.guard_adoption_application();

create function private.accept_adoption_application(p_application uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = ''
as $$
declare
  application public.adoption_applications;
  listing public.adoption_listings;
begin
  if not private.is_active() then
    raise exception 'FORBIDDEN';
  end if;
  select * into application
  from public.adoption_applications
  where id = p_application;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into listing
  from public.adoption_listings
  where id = application.listing_id
  for update;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into application
  from public.adoption_applications
  where id = p_application
  for update;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  if listing.owner_id is distinct from auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if application.status <> 'pending' or listing.status <> 'active' then
    raise exception 'CONFLICT';
  end if;
  with replaced as (
    update public.adoption_applications
    set status = 'rejected'
    where listing_id = listing.id
      and id <> application.id
      and status = 'pending'
    returning applicant_id
  )
  insert into public.notifications (user_id, title, body)
  select applicant_id, 'Müraciət qəbul edilmədi', 'Bu elan üçün başqa müraciət qəbul edildi.'
  from replaced;
  update public.adoption_applications
  set status = 'accepted'
  where id = application.id
  returning * into application;
  update public.adoption_listings
  set status = 'adopted'
  where id = listing.id;
  insert into public.notifications (user_id, title, body)
  values (application.applicant_id, 'Müraciət qəbul edildi', 'Övladlığa götürmə müraciətiniz qəbul edildi.');
  return application;
exception
  when unique_violation then
    raise exception 'CONFLICT';
end;
$$;

create function public.accept_adoption_application(p_application uuid)
returns public.adoption_applications
language sql
security invoker
set search_path = ''
as $$
  select * from private.accept_adoption_application(p_application);
$$;

create function private.reject_adoption_application(p_application uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = ''
as $$
declare
  application public.adoption_applications;
  listing public.adoption_listings;
begin
  if not private.is_active() then
    raise exception 'FORBIDDEN';
  end if;
  select * into application from public.adoption_applications where id = p_application;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into listing from public.adoption_listings where id = application.listing_id for update;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into application from public.adoption_applications where id = p_application for update;
  if listing.owner_id is distinct from auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if application.status <> 'pending' then
    raise exception 'CONFLICT';
  end if;
  update public.adoption_applications
  set status = 'rejected'
  where id = application.id
  returning * into application;
  insert into public.notifications (user_id, title, body)
  values (application.applicant_id, 'Müraciət qəbul edilmədi', 'Övladlığa götürmə müraciətiniz qəbul edilmədi.');
  return application;
end;
$$;

create function public.reject_adoption_application(p_application uuid)
returns public.adoption_applications
language sql
security invoker
set search_path = ''
as $$
  select * from private.reject_adoption_application(p_application);
$$;

create function private.cancel_adoption_application(p_application uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = ''
as $$
declare
  application public.adoption_applications;
  listing public.adoption_listings;
begin
  if not private.is_active() then
    raise exception 'FORBIDDEN';
  end if;
  select * into application from public.adoption_applications where id = p_application;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into listing from public.adoption_listings where id = application.listing_id for update;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  select * into application from public.adoption_applications where id = p_application for update;
  if application.applicant_id is distinct from auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if application.status <> 'pending' then
    raise exception 'CONFLICT';
  end if;
  update public.adoption_applications
  set status = 'cancelled'
  where id = application.id
  returning * into application;
  insert into public.notifications (user_id, title, body)
  values (listing.owner_id, 'Müraciət ləğv edildi', 'Gözləyən övladlığa müraciət ləğv edildi.');
  return application;
end;
$$;

create function public.cancel_adoption_application(p_application uuid)
returns public.adoption_applications
language sql
security invoker
set search_path = ''
as $$
  select * from private.cancel_adoption_application(p_application);
$$;

revoke all on function private.touch_adoption_application() from public, anon, authenticated;
revoke all on function private.guard_adoption_application() from public, anon, authenticated;
grant execute on function private.guard_adoption_application() to authenticated;
revoke all on function private.accept_adoption_application(uuid) from public, anon, authenticated;
revoke all on function private.reject_adoption_application(uuid) from public, anon, authenticated;
revoke all on function private.cancel_adoption_application(uuid) from public, anon, authenticated;
revoke all on function public.accept_adoption_application(uuid) from public, anon, authenticated;
revoke all on function public.reject_adoption_application(uuid) from public, anon, authenticated;
revoke all on function public.cancel_adoption_application(uuid) from public, anon, authenticated;

grant execute on function private.accept_adoption_application(uuid), public.accept_adoption_application(uuid) to authenticated;
grant execute on function private.reject_adoption_application(uuid), public.reject_adoption_application(uuid) to authenticated;
grant execute on function private.cancel_adoption_application(uuid), public.cancel_adoption_application(uuid) to authenticated;
