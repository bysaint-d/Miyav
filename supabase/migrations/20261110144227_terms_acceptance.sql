-- Members accept the Terms of Service and the Privacy Policy when they register. The accepted version and
-- time are kept on the profile; accounts without the current version (older and Google sign-ups) are asked
-- once on their next sign-in through accept_terms. Additive only.

alter table public.profiles
  add column if not exists terms_version text check (terms_version is null or length(terms_version) <= 40),
  add column if not exists terms_accepted_at timestamptz;

grant select (terms_version, terms_accepted_at) on public.profiles to authenticated;

-- Sign-up sends the accepted version in the user metadata.
create or replace function private.on_new_user()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_terms text := left(nullif(btrim(coalesce(new.raw_user_meta_data->>'terms_version', '')), ''), 40);
begin
  insert into public.profiles (id, full_name, terms_version, terms_accepted_at)
  values (new.id,
          left(coalesce(nullif(trim(new.raw_user_meta_data->>'full_name'), ''), 'MIYAV istifadəçisi'), 100),
          v_terms,
          case when v_terms is null then null else now() end);
  return new;
end;
$$;

create or replace function private.accept_terms(p_version text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
begin
  if auth.uid() is null then raise exception 'FORBIDDEN'; end if;
  if p_version is null or length(btrim(p_version)) not between 1 and 40 then raise exception 'TERMS_VERSION'; end if;
  update public.profiles set terms_version = btrim(p_version), terms_accepted_at = now() where id = auth.uid();
end;
$$;

create or replace function public.accept_terms(p_version text)
returns void language sql set search_path to '' as $$ select private.accept_terms(p_version); $$;

revoke all on function private.accept_terms(text) from public, anon;
revoke all on function public.accept_terms(text) from public, anon;
grant execute on function private.accept_terms(text) to authenticated;
grant execute on function public.accept_terms(text) to authenticated;
