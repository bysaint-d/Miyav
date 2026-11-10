-- Premium adoption listings. The owner pays 5 ₼ by M10 or card transfer, uploads the receipt and sends a
-- request; an administrator checks the receipt and approves or rejects it. An approved listing shows first
-- in Discover for 7 days (renewals add 7 more days). Price and length are fixed here, never by the client.
-- Additive only.

alter table public.adoption_listings add column if not exists premium_until timestamptz;
grant select (premium_until) on public.adoption_listings to anon, authenticated;
create index if not exists adoption_listings_premium_idx
  on public.adoption_listings (premium_until desc) where status = 'active' and premium_until is not null;

-- PostgREST computed field: order=is_premium.desc puts current premium listings first.
create or replace function public.is_premium(l public.adoption_listings)
returns boolean
language sql
stable
set search_path to ''
as $$ select coalesce(l.premium_until > now(), false); $$;
grant execute on function public.is_premium(public.adoption_listings) to anon, authenticated;

create table public.premium_requests (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.adoption_listings(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  method text not null check (method in ('m10', 'card')),
  amount numeric(7, 2) not null check (amount > 0),
  days integer not null check (days between 1 and 90),
  receipt_path text not null check (length(receipt_path) <= 200),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  note text not null default '' check (length(note) <= 300),
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);

create unique index premium_requests_one_pending on public.premium_requests (listing_id) where status = 'pending';
create index premium_requests_owner_idx on public.premium_requests (owner_id, created_at desc);
create index premium_requests_queue_idx on public.premium_requests (created_at) where status = 'pending';

alter table public.premium_requests enable row level security;
revoke all on public.premium_requests from anon, authenticated;
grant select on public.premium_requests to authenticated;
create policy premium_requests_read on public.premium_requests for select to authenticated
  using (owner_id = (select auth.uid()) or (select private.is_admin()));

-- Receipts: <owner id>/<random id>.jpg in a private bucket; the owner and administrators can read them.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipts', 'receipts', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy receipt_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'receipts'
              and split_part(name, '/', 1) = (select auth.uid())::text
              and (select private.is_active())
              and name ~ '^[a-f0-9-]{36}/[a-f0-9-]{36}[.]jpg$');

create policy receipt_read on storage.objects for select to authenticated
  using (bucket_id = 'receipts'
         and (split_part(name, '/', 1) = (select auth.uid())::text or private.is_admin()));

create or replace function private.notification_text(p_kind text, p_lang text, p_params jsonb)
 returns table (title text, body text)
 language plpgsql
 immutable
 set search_path to ''
as $function$
declare
  v_lang text := case when p_lang in ('en', 'ru') then p_lang else 'az' end;
  v_title text;
  v_body text;
  v_key text;
  v_value text;
begin
  select x.t, x.b into v_title, v_body
    from (values
      ('adoption_new', 'az', 'Yeni övladlığa müraciət', '«{pet}» elanınıza yeni müraciət gəldi.'),
      ('adoption_new', 'en', 'New adoption application', 'Someone applied to adopt {pet}.'),
      ('adoption_new', 'ru', 'Новая заявка на усыновление', 'На объявление «{pet}» пришла новая заявка.'),
      ('adoption_accepted', 'az', 'Müraciət qəbul edildi', '«{pet}» üçün övladlığa götürmə müraciətiniz qəbul edildi.'),
      ('adoption_accepted', 'en', 'Application accepted', 'Your application to adopt {pet} was accepted.'),
      ('adoption_accepted', 'ru', 'Заявка принята', 'Ваша заявка на усыновление «{pet}» принята.'),
      ('adoption_rejected', 'az', 'Müraciət qəbul edilmədi', '«{pet}» üçün övladlığa götürmə müraciətiniz qəbul edilmədi.'),
      ('adoption_rejected', 'en', 'Application not accepted', 'Your application to adopt {pet} was not accepted.'),
      ('adoption_rejected', 'ru', 'Заявка отклонена', 'Ваша заявка на усыновление «{pet}» отклонена.'),
      ('adoption_other_accepted', 'az', 'Müraciət qəbul edilmədi', '«{pet}» üçün başqa müraciət qəbul edildi.'),
      ('adoption_other_accepted', 'en', 'Application not accepted', 'Another application for {pet} was accepted.'),
      ('adoption_other_accepted', 'ru', 'Заявка отклонена', 'По объявлению «{pet}» принята другая заявка.'),
      ('adoption_cancelled', 'az', 'Müraciət ləğv edildi', '«{pet}» üçün gözləyən müraciət geri götürüldü.'),
      ('adoption_cancelled', 'en', 'Application withdrawn', 'A pending application for {pet} was withdrawn.'),
      ('adoption_cancelled', 'ru', 'Заявка отозвана', 'Ожидающая заявка на «{pet}» отозвана.'),
      ('message_new', 'az', 'Yeni mesaj', '{who} sizə mesaj yazdı.'),
      ('message_new', 'en', 'New message', '{who} sent you a message.'),
      ('message_new', 'ru', 'Новое сообщение', '{who} написал(а) вам сообщение.'),
      ('walk_offer_new', 'az', 'Yeni gəzinti təklifi', '{who} {pet} üçün {price} ₼/saat təklif etdi.'),
      ('walk_offer_new', 'en', 'New walk offer', '{who} offered to walk {pet} for {price} ₼/h.'),
      ('walk_offer_new', 'ru', 'Новое предложение прогулки', '{who} предлагает выгулять {pet} за {price} ₼/час.'),
      ('walk_offer_accepted', 'az', 'Təklifiniz qəbul edildi', '{pet} üçün gəzinti təklifiniz {price} ₼/saat qiymətlə qəbul edildi.'),
      ('walk_offer_accepted', 'en', 'Offer accepted', 'Your offer to walk {pet} at {price} ₼/h was accepted.'),
      ('walk_offer_accepted', 'ru', 'Предложение принято', 'Ваше предложение выгулять {pet} за {price} ₼/час принято.'),
      ('walk_offer_declined', 'az', 'Təklifiniz qəbul edilmədi', '{pet} üçün təklifiniz rədd edildi.'),
      ('walk_offer_declined', 'en', 'Offer declined', 'Your offer for {pet} was declined.'),
      ('walk_offer_declined', 'ru', 'Предложение отклонено', 'Ваше предложение для {pet} отклонено.'),
      ('walk_cancelled', 'az', 'Gəzinti ləğv edildi', '{pet} üçün gəzinti sahibi tərəfindən ləğv edildi.'),
      ('walk_cancelled', 'en', 'Walk cancelled', 'The owner cancelled the walk with {pet}.'),
      ('walk_cancelled', 'ru', 'Прогулка отменена', 'Владелец отменил прогулку с {pet}.'),
      ('verification_approved', 'az', 'Müraciətiniz yoxlanıldı', 'Baxıcı profiliniz təsdiqləndi.'),
      ('verification_approved', 'en', 'Verification reviewed', 'Your sitter profile was approved.'),
      ('verification_approved', 'ru', 'Заявка проверена', 'Ваш профиль ситтера подтверждён.'),
      ('verification_declined', 'az', 'Müraciətiniz yoxlanıldı', 'Müraciətiniz qəbul edilmədi. Profilinizdə qeydə baxın.'),
      ('verification_declined', 'en', 'Verification reviewed', 'Your request was not approved. See the note in your profile.'),
      ('verification_declined', 'ru', 'Заявка проверена', 'Заявка не одобрена. Посмотрите примечание в профиле.'),
      ('booking_moderated', 'az', 'Gəzinti sorğusu ləğv edildi', 'Sorğu moderator tərəfindən ləğv edildi.'),
      ('booking_moderated', 'en', 'Request cancelled', 'A moderator cancelled the request.'),
      ('booking_moderated', 'ru', 'Запрос отменён', 'Запрос отменён модератором.'),
      ('booking_new', 'az', 'Yeni sifariş sorğusu', 'Yeni qayğı sorğunuz var. Sifarişlər bölməsində cavab verin.'),
      ('booking_new', 'en', 'New booking request', 'You have a new care request. Reply in Bookings.'),
      ('booking_new', 'ru', 'Новый запрос', 'У вас новый запрос на уход. Ответьте в разделе «Заказы».'),
      ('booking_sent', 'az', 'Sorğunuz göndərildi', 'Baxıcının cavabını Sifarişlər bölməsində izləyin.'),
      ('booking_sent', 'en', 'Request sent', 'Follow the sitter''s reply in Bookings.'),
      ('booking_sent', 'ru', 'Запрос отправлен', 'Следите за ответом ситтера в разделе «Заказы».'),
      ('booking_accepted', 'az', 'Sifariş yeniləndi', 'Baxıcı sorğunuzu təsdiqlədi.'),
      ('booking_accepted', 'en', 'Booking updated', 'The sitter confirmed the request.'),
      ('booking_accepted', 'ru', 'Заказ обновлён', 'Ситтер подтвердил запрос.'),
      ('booking_declined', 'az', 'Sifariş yeniləndi', 'Sorğu qəbul edilmədi.'),
      ('booking_declined', 'en', 'Booking updated', 'The request was not accepted.'),
      ('booking_declined', 'ru', 'Заказ обновлён', 'Запрос не принят.'),
      ('booking_cancelled', 'az', 'Sifariş yeniləndi', 'Sifariş ləğv edildi.'),
      ('booking_cancelled', 'en', 'Booking updated', 'The booking was cancelled.'),
      ('booking_cancelled', 'ru', 'Заказ обновлён', 'Заказ отменён.'),
      ('booking_completed', 'az', 'Sifariş yeniləndi', 'Xidmət tamamlandı. Rəyinizi bölüşə bilərsiniz.'),
      ('booking_completed', 'en', 'Booking updated', 'The service is complete. You can leave a review.'),
      ('booking_completed', 'ru', 'Заказ обновлён', 'Услуга завершена. Вы можете оставить отзыв.'),
      ('premium_request_new', 'az', 'Yeni premium sorğusu', '«{pet}» elanı üçün ödəniş çeki yoxlanılmağı gözləyir.'),
      ('premium_request_new', 'en', 'New premium request', 'A payment receipt for «{pet}» is waiting for review.'),
      ('premium_request_new', 'ru', 'Новая заявка на премиум', 'Чек об оплате для «{pet}» ждёт проверки.'),
      ('premium_approved', 'az', 'Elanınız premiumdur', '«{pet}» elanı {until} tarixinədək ilk sırada göstəriləcək.'),
      ('premium_approved', 'en', 'Your listing is premium', '«{pet}» will be shown first until {until}.'),
      ('premium_approved', 'ru', 'Объявление в премиуме', '«{pet}» будет показываться первым до {until}.'),
      ('premium_rejected', 'az', 'Premium sorğusu təsdiqlənmədi', '«{pet}» üçün ödəniş təsdiqlənmədi. {note}'),
      ('premium_rejected', 'en', 'Premium request not approved', 'The payment for «{pet}» was not confirmed. {note}'),
      ('premium_rejected', 'ru', 'Заявка на премиум отклонена', 'Оплата за «{pet}» не подтверждена. {note}')
    ) as x(kind, lang, t, b)
   where x.kind = p_kind and x.lang = v_lang;
  if v_title is null then
    return;
  end if;
  for v_key, v_value in select key, value from jsonb_each_text(coalesce(p_params, '{}'::jsonb)) loop
    if coalesce(btrim(v_value), '') <> '' then
      v_title := replace(v_title, '{' || v_key || '}', v_value);
      v_body := replace(v_body, '{' || v_key || '}', v_value);
    end if;
  end loop;
  v_body := replace(v_body, '{who}', case v_lang when 'en' then 'Someone' when 'ru' then 'Пользователь' else 'Bir istifadəçi' end);
  v_body := replace(v_body, '{pet}', case v_lang when 'en' then 'your pet' when 'ru' then 'питомец' else 'heyvan' end);
  v_body := btrim(replace(v_body, '{note}', ''));
  title := v_title;
  body := v_body;
  return next;
end;
$function$;

-- The owner sends a request for one of their active listings with an uploaded receipt.
create or replace function private.request_premium(p_listing uuid, p_method text, p_receipt text)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_listing public.adoption_listings;
  v_id uuid;
  v_admin uuid;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.adoption_listings where id = p_listing for update;
  if not found or v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'active' then raise exception 'PREMIUM_LISTING'; end if;
  if p_method is null or p_method not in ('m10', 'card') then raise exception 'PREMIUM_METHOD'; end if;
  if p_receipt is null or split_part(p_receipt, '/', 1) <> auth.uid()::text
     or not exists (select 1 from storage.objects where bucket_id = 'receipts' and name = p_receipt) then
    raise exception 'PREMIUM_RECEIPT';
  end if;
  if exists (select 1 from public.premium_requests where listing_id = p_listing and status = 'pending') then
    raise exception 'PREMIUM_PENDING';
  end if;
  if (select count(*) from public.premium_requests
       where owner_id = auth.uid() and created_at > now() - interval '1 day') >= 5 then
    raise exception 'PREMIUM_LIMIT';
  end if;
  insert into public.premium_requests (listing_id, owner_id, method, amount, days, receipt_path)
  values (p_listing, auth.uid(), p_method, 5, 7, p_receipt)
  returning id into v_id;
  for v_admin in select id from public.profiles where role = 'admin' and not suspended loop
    perform private.notify(v_admin, 'premium_request_new', jsonb_build_object('pet', v_listing.name, 'request', v_id));
  end loop;
  return v_id;
exception
  when unique_violation then
    raise exception 'PREMIUM_PENDING';
end;
$$;

-- An administrator approves (the listing becomes premium for the request's days) or rejects a request.
create or replace function private.review_premium(p_request uuid, p_approve boolean, p_note text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_request public.premium_requests;
  v_pet text;
  v_until timestamptz;
  v_note text := left(btrim(coalesce(p_note, '')), 300);
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  select * into v_request from public.premium_requests where id = p_request for update;
  if not found or v_request.status <> 'pending' then raise exception 'PREMIUM_CLOSED'; end if;
  if p_approve then
    update public.adoption_listings
       set premium_until = greatest(coalesce(premium_until, now()), now()) + make_interval(days => v_request.days)
     where id = v_request.listing_id
    returning name, premium_until into v_pet, v_until;
    update public.premium_requests set status = 'approved', note = v_note, reviewed_by = auth.uid(), reviewed_at = now()
     where id = p_request;
    perform private.notify(v_request.owner_id, 'premium_approved', jsonb_build_object(
      'pet', v_pet, 'until', to_char(v_until at time zone 'Asia/Baku', 'DD.MM.YYYY'), 'listing', v_request.listing_id));
  else
    select name into v_pet from public.adoption_listings where id = v_request.listing_id;
    update public.premium_requests set status = 'rejected', note = v_note, reviewed_by = auth.uid(), reviewed_at = now()
     where id = p_request;
    perform private.notify(v_request.owner_id, 'premium_rejected', jsonb_build_object(
      'pet', v_pet, 'note', v_note, 'listing', v_request.listing_id));
  end if;
  insert into private.admin_audit (actor_id, action, target_id, value, note)
  values (auth.uid(), 'premium', p_request, case when p_approve then 'approved' else 'rejected' end, v_note);
end;
$$;

-- The review queue with names, newest pending first.
create or replace function private.admin_premium_requests(p_status text default null)
returns table (
  id uuid, listing_id uuid, listing_name text, listing_status text, premium_until timestamptz, owner_id uuid,
  owner_name text, method text, amount numeric, days integer, receipt_path text, status text, note text,
  reviewed_at timestamptz, created_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select r.id, r.listing_id, coalesce(l.name, ''), coalesce(l.status, ''), l.premium_until, r.owner_id,
         coalesce(p.full_name, ''), r.method, r.amount, r.days, r.receipt_path, r.status, r.note, r.reviewed_at, r.created_at
    from public.premium_requests r
    left join public.adoption_listings l on l.id = r.listing_id
    left join public.profiles p on p.id = r.owner_id
   where p_status is null or r.status = p_status
   order by (r.status = 'pending') desc, r.created_at desc
   limit 300;
end;
$$;

create or replace function public.request_premium(p_listing uuid, p_method text, p_receipt text)
returns uuid language sql set search_path to '' as $$ select private.request_premium(p_listing, p_method, p_receipt); $$;

create or replace function public.review_premium(p_request uuid, p_approve boolean, p_note text)
returns void language sql set search_path to '' as $$ select private.review_premium(p_request, p_approve, p_note); $$;

create or replace function public.admin_premium_requests(p_status text default null)
returns table (
  id uuid, listing_id uuid, listing_name text, listing_status text, premium_until timestamptz, owner_id uuid,
  owner_name text, method text, amount numeric, days integer, receipt_path text, status text, note text,
  reviewed_at timestamptz, created_at timestamptz)
language sql stable set search_path to '' as $$ select * from private.admin_premium_requests(p_status); $$;

revoke all on function private.request_premium(uuid, text, text) from public, anon;
revoke all on function private.review_premium(uuid, boolean, text) from public, anon;
revoke all on function private.admin_premium_requests(text) from public, anon;
revoke all on function public.request_premium(uuid, text, text) from public, anon;
revoke all on function public.review_premium(uuid, boolean, text) from public, anon;
revoke all on function public.admin_premium_requests(text) from public, anon;
grant execute on function private.request_premium(uuid, text, text) to authenticated;
grant execute on function private.review_premium(uuid, boolean, text) to authenticated;
grant execute on function private.admin_premium_requests(text) to authenticated;
grant execute on function public.request_premium(uuid, text, text) to authenticated;
grant execute on function public.review_premium(uuid, boolean, text) to authenticated;
grant execute on function public.admin_premium_requests(text) to authenticated;

-- Nearby listings: current premium listings first, then by distance.
create or replace function public.listings_near(p_lat double precision, p_lng double precision, p_radius_km integer DEFAULT 25,
  p_species text DEFAULT NULL::text, p_search text DEFAULT NULL::text, p_limit integer DEFAULT 20, p_offset integer DEFAULT 0,
  p_gender text DEFAULT NULL::text, p_min_age integer DEFAULT NULL::integer, p_max_age integer DEFAULT NULL::integer,
  p_vaccinated boolean DEFAULT false)
 returns setof public.adoption_listings
 language sql
 stable
 set search_path to ''
as $function$
  select l.*
  from public.adoption_listings l
  where l.status = 'active'
    and l.latitude is not null
    and p_lat between 38 and 42
    and p_lng between 44 and 51
    and (p_species is null or l.species = p_species)
    and (p_gender is null or l.gender = p_gender)
    and (p_min_age is null or l.age_months >= p_min_age)
    and (p_max_age is null or l.age_months < p_max_age)
    and (not coalesce(p_vaccinated, false) or l.vaccination = 'yes')
    and (coalesce(p_search, '') = '' or l.name ilike '%' || p_search || '%' or l.breed ilike '%' || p_search || '%')
    and private.distance_m(p_lat, p_lng, l.latitude::float8, l.longitude::float8)
        <= least(greatest(p_radius_km, 1), 100) * 1000
  order by coalesce(l.premium_until > now(), false) desc,
           private.distance_m(p_lat, p_lng, l.latitude::float8, l.longitude::float8), l.created_at desc
  limit least(greatest(p_limit, 1), 50)
  offset greatest(p_offset, 0);
$function$;
