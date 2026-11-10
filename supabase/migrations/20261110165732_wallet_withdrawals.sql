-- MIYAV wallet for walkers. Every walk a member completed as the walker adds its agreed total to their balance.
-- With payout details and the current walker agreement saved, they ask for a withdrawal; an administrator
-- transfers the amount minus the 10% service fee and records the bank reference, which becomes the receipt.
-- Amounts, the fee and the agreement version are decided here, never by the client. Additive only.

create or replace function private.walker_agreement_version()
returns text language sql immutable set search_path to '' as $$ select '2026-11-10'::text; $$;

create or replace function private.withdrawal_fee_percent()
returns numeric language sql immutable set search_path to '' as $$ select 10::numeric; $$;

create or replace function private.withdrawal_minimum()
returns numeric language sql immutable set search_path to '' as $$ select 5::numeric; $$;

revoke all on function private.walker_agreement_version() from public, anon;
revoke all on function private.withdrawal_fee_percent() from public, anon;
revoke all on function private.withdrawal_minimum() from public, anon;

create table public.withdrawals (
  id uuid primary key default gen_random_uuid(),
  receipt_no bigint generated always as identity unique,
  walker_id uuid references public.profiles(id) on delete set null,
  amount numeric(9, 2) not null check (amount > 0),
  fee_percent numeric(5, 2) not null check (fee_percent between 0 and 100),
  fee numeric(9, 2) not null check (fee >= 0),
  net numeric(9, 2) not null check (net > 0),
  holder_name text not null check (length(holder_name) <= 100),
  bank_name text not null check (length(bank_name) <= 80),
  iban text not null check (iban ~ '^AZ[0-9]{2}[A-Z0-9]{4}[A-Z0-9]{20}$'),
  status text not null default 'pending' check (status in ('pending', 'paid', 'rejected', 'cancelled')),
  transfer_reference text not null default '' check (length(transfer_reference) <= 100),
  note text not null default '' check (length(note) <= 300),
  processed_by uuid references public.profiles(id) on delete set null,
  processed_at timestamptz,
  created_at timestamptz not null default now(),
  constraint withdrawals_split check (amount = fee + net),
  constraint withdrawals_paid_reference check (status <> 'paid' or length(btrim(transfer_reference)) >= 3)
);

create unique index withdrawals_one_pending on public.withdrawals (walker_id) where status = 'pending';
create index withdrawals_walker_idx on public.withdrawals (walker_id, created_at desc);
create index withdrawals_queue_idx on public.withdrawals (created_at) where status = 'pending';
create index walk_listings_walker_done_idx on public.walk_listings (walker_id) where status = 'completed';

alter table public.withdrawals enable row level security;
revoke all on public.withdrawals from anon, authenticated;
grant select on public.withdrawals to authenticated;
create policy withdrawals_read on public.withdrawals for select to authenticated
  using (walker_id = (select auth.uid()) or (select private.is_admin()));

-- Earned from completed walks, money waiting in a request, already paid out, and what can still be withdrawn.
create or replace function private.wallet_of(p_user uuid)
returns table (earned numeric, pending numeric, withdrawn numeric, available numeric, completed integer)
language sql
stable
security definer
set search_path to ''
as $$
  with e as (
    select coalesce(sum(round(coalesce(w.agreed_price_per_hour, w.price_per_hour) * w.duration_minutes / 60, 2)), 0) as earned,
           count(*)::integer as completed
      from public.walk_listings w
     where w.walker_id = p_user and w.status = 'completed'),
  o as (
    select coalesce(sum(x.amount) filter (where x.status = 'pending'), 0) as pending,
           coalesce(sum(x.amount) filter (where x.status = 'paid'), 0) as withdrawn
      from public.withdrawals x
     where x.walker_id = p_user)
  select e.earned, o.pending, o.withdrawn, e.earned - o.pending - o.withdrawn, e.completed from e, o;
$$;
revoke all on function private.wallet_of(uuid) from public, anon, authenticated;

create or replace function private.my_wallet()
returns table (earned numeric, pending numeric, withdrawn numeric, available numeric, completed integer,
               fee_percent numeric, minimum numeric)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  return query
  select w.earned, w.pending, w.withdrawn, w.available, w.completed, private.withdrawal_fee_percent(), private.withdrawal_minimum()
    from private.wallet_of(auth.uid()) w;
end;
$$;

-- The walker asks for part or all of the available balance. Payout details are copied so the receipt keeps
-- the account that was actually paid, even if the member edits them later.
create or replace function private.request_withdrawal(p_amount numeric)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_amount numeric := round(p_amount, 2);
  v_payout public.payout_details;
  v_wallet record;
  v_percent numeric := private.withdrawal_fee_percent();
  v_fee numeric;
  v_id uuid;
  v_name text;
  v_admin uuid;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  perform pg_advisory_xact_lock(hashtextextended('withdrawal:' || auth.uid()::text, 0));
  select * into v_payout from public.payout_details where user_id = auth.uid();
  if not found then raise exception 'WITHDRAWAL_PAYOUT'; end if;
  if v_payout.agreement_version <> private.walker_agreement_version() then raise exception 'WITHDRAWAL_AGREEMENT'; end if;
  if exists (select 1 from public.withdrawals where walker_id = auth.uid() and status = 'pending') then
    raise exception 'WITHDRAWAL_PENDING';
  end if;
  if v_amount is null or v_amount < private.withdrawal_minimum() then raise exception 'WITHDRAWAL_MINIMUM'; end if;
  select * into v_wallet from private.wallet_of(auth.uid());
  if v_amount > v_wallet.available then raise exception 'WITHDRAWAL_BALANCE'; end if;
  if (select count(*) from public.withdrawals where walker_id = auth.uid() and created_at > now() - interval '1 day') >= 5 then
    raise exception 'WITHDRAWAL_LIMIT';
  end if;
  v_fee := round(v_amount * v_percent / 100, 2);
  insert into public.withdrawals (walker_id, amount, fee_percent, fee, net, holder_name, bank_name, iban)
  values (auth.uid(), v_amount, v_percent, v_fee, v_amount - v_fee, v_payout.holder_name, v_payout.bank_name, v_payout.iban)
  returning id into v_id;
  select full_name into v_name from public.profiles where id = auth.uid();
  for v_admin in select id from public.profiles where role = 'admin' and not suspended loop
    perform private.notify(v_admin, 'withdrawal_requested',
      jsonb_build_object('who', coalesce(v_name, ''), 'amount', v_amount::text, 'request', v_id));
  end loop;
  return v_id;
exception
  when unique_violation then
    raise exception 'WITHDRAWAL_PENDING';
end;
$$;

create or replace function private.cancel_withdrawal(p_request uuid)
returns void
language plpgsql
security definer
set search_path to ''
as $$
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  update public.withdrawals set status = 'cancelled', processed_at = now()
   where id = p_request and walker_id = auth.uid() and status = 'pending';
  if not found then raise exception 'WITHDRAWAL_CLOSED'; end if;
end;
$$;

-- An administrator marks a request paid (with the bank transfer reference) or rejects it with a reason.
-- Nobody processes their own request.
create or replace function private.review_withdrawal(p_request uuid, p_paid boolean, p_reference text, p_note text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_request public.withdrawals;
  v_reference text := left(btrim(coalesce(p_reference, '')), 100);
  v_note text := left(btrim(coalesce(p_note, '')), 300);
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  select * into v_request from public.withdrawals where id = p_request for update;
  if not found or v_request.status <> 'pending' then raise exception 'WITHDRAWAL_CLOSED'; end if;
  if v_request.walker_id = auth.uid() then raise exception 'FORBIDDEN'; end if;
  if p_paid then
    if length(v_reference) < 3 then raise exception 'WITHDRAWAL_REFERENCE'; end if;
    update public.withdrawals
       set status = 'paid', transfer_reference = v_reference, note = v_note, processed_by = auth.uid(), processed_at = now()
     where id = p_request;
    perform private.notify(v_request.walker_id, 'withdrawal_paid',
      jsonb_build_object('net', v_request.net::text, 'amount', v_request.amount::text, 'request', p_request));
  else
    update public.withdrawals
       set status = 'rejected', note = v_note, processed_by = auth.uid(), processed_at = now()
     where id = p_request;
    perform private.notify(v_request.walker_id, 'withdrawal_rejected',
      jsonb_build_object('amount', v_request.amount::text, 'note', v_note, 'request', p_request));
  end if;
  insert into private.admin_audit (actor_id, action, target_id, value, note)
  values (auth.uid(), 'withdrawal', p_request, case when p_paid then 'paid' else 'rejected' end,
          case when p_paid then v_reference else v_note end);
end;
$$;

-- The review queue with the walker's name and current balance, pending first.
create or replace function private.admin_withdrawals(p_status text default null)
returns table (
  id uuid, receipt_no bigint, walker_id uuid, walker_name text, amount numeric, fee_percent numeric, fee numeric,
  net numeric, holder_name text, bank_name text, iban text, status text, transfer_reference text, note text,
  processed_at timestamptz, created_at timestamptz, available numeric, completed integer)
language plpgsql
stable
security definer
set search_path to ''
as $$
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  return query
  select x.id, x.receipt_no, x.walker_id, coalesce(p.full_name, ''), x.amount, x.fee_percent, x.fee, x.net,
         x.holder_name, x.bank_name, x.iban, x.status, x.transfer_reference, x.note, x.processed_at, x.created_at,
         w.available, w.completed
    from public.withdrawals x
    left join public.profiles p on p.id = x.walker_id
    left join lateral private.wallet_of(x.walker_id) w on true
   where p_status is null or x.status = p_status
   order by (x.status = 'pending') desc, x.created_at desc
   limit 300;
end;
$$;

create or replace function public.my_wallet()
returns table (earned numeric, pending numeric, withdrawn numeric, available numeric, completed integer,
               fee_percent numeric, minimum numeric)
language sql stable set search_path to '' as $$ select * from private.my_wallet(); $$;

create or replace function public.request_withdrawal(p_amount numeric)
returns uuid language sql set search_path to '' as $$ select private.request_withdrawal(p_amount); $$;

create or replace function public.cancel_withdrawal(p_request uuid)
returns void language sql set search_path to '' as $$ select private.cancel_withdrawal(p_request); $$;

create or replace function public.review_withdrawal(p_request uuid, p_paid boolean, p_reference text, p_note text)
returns void language sql set search_path to ''
as $$ select private.review_withdrawal(p_request, p_paid, p_reference, p_note); $$;

create or replace function public.admin_withdrawals(p_status text default null)
returns table (
  id uuid, receipt_no bigint, walker_id uuid, walker_name text, amount numeric, fee_percent numeric, fee numeric,
  net numeric, holder_name text, bank_name text, iban text, status text, transfer_reference text, note text,
  processed_at timestamptz, created_at timestamptz, available numeric, completed integer)
language sql stable set search_path to '' as $$ select * from private.admin_withdrawals(p_status); $$;

revoke all on function private.my_wallet() from public, anon;
revoke all on function private.request_withdrawal(numeric) from public, anon;
revoke all on function private.cancel_withdrawal(uuid) from public, anon;
revoke all on function private.review_withdrawal(uuid, boolean, text, text) from public, anon;
revoke all on function private.admin_withdrawals(text) from public, anon;
revoke all on function public.my_wallet() from public, anon;
revoke all on function public.request_withdrawal(numeric) from public, anon;
revoke all on function public.cancel_withdrawal(uuid) from public, anon;
revoke all on function public.review_withdrawal(uuid, boolean, text, text) from public, anon;
revoke all on function public.admin_withdrawals(text) from public, anon;
grant execute on function private.my_wallet() to authenticated;
grant execute on function private.request_withdrawal(numeric) to authenticated;
grant execute on function private.cancel_withdrawal(uuid) to authenticated;
grant execute on function private.review_withdrawal(uuid, boolean, text, text) to authenticated;
grant execute on function private.admin_withdrawals(text) to authenticated;
grant execute on function public.my_wallet() to authenticated;
grant execute on function public.request_withdrawal(numeric) to authenticated;
grant execute on function public.cancel_withdrawal(uuid) to authenticated;
grant execute on function public.review_withdrawal(uuid, boolean, text, text) to authenticated;
grant execute on function public.admin_withdrawals(text) to authenticated;

-- Only the agreement version the server considers current can be accepted.
create or replace function private.save_payout_details(p_holder text, p_bank text, p_iban text, p_agreement text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_holder text := btrim(coalesce(p_holder, ''));
  v_bank text := btrim(coalesce(p_bank, ''));
  v_iban text := upper(regexp_replace(coalesce(p_iban, ''), '\s', '', 'g'));
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if length(v_holder) not between 3 and 100 then raise exception 'PAYOUT_HOLDER'; end if;
  if length(v_bank) not between 2 and 80 then raise exception 'PAYOUT_BANK'; end if;
  if v_iban !~ '^AZ[0-9]{2}[A-Z0-9]{4}[A-Z0-9]{20}$' then raise exception 'PAYOUT_IBAN'; end if;
  if coalesce(p_agreement, '') <> private.walker_agreement_version() then raise exception 'PAYOUT_AGREEMENT'; end if;
  insert into public.payout_details (user_id, holder_name, bank_name, iban, agreement_version)
  values (auth.uid(), v_holder, v_bank, v_iban, private.walker_agreement_version())
  on conflict (user_id) do update
     set holder_name = excluded.holder_name, bank_name = excluded.bank_name, iban = excluded.iban,
         agreement_version = excluded.agreement_version, agreement_accepted_at = now(), updated_at = now();
end;
$$;

-- The walker hears about a new rating.
create or replace function private.rate_walker(p_listing uuid, p_rating integer, p_review text)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_listing public.walk_listings;
  v_review text := btrim(coalesce(p_review, ''));
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_rating is null or p_rating not between 1 and 5 then raise exception 'WALK_RATING'; end if;
  if length(v_review) > 300 then raise exception 'WALK_REVIEW'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found or v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'completed' or v_listing.walker_id is null then raise exception 'WALK_LISTING_CLOSED'; end if;
  if v_listing.walker_rating is not null then raise exception 'WALK_RATED'; end if;
  update public.walk_listings
     set walker_rating = p_rating, walker_review = v_review, rated_at = now()
   where id = p_listing;
  perform private.notify(v_listing.walker_id, 'walk_rated',
    jsonb_build_object('pet', v_listing.pet_name, 'stars', p_rating::text, 'listing', v_listing.id));
end;
$$;

-- New notification kinds: walk_rated, withdrawal_requested, withdrawal_paid, withdrawal_rejected.
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
      ('premium_rejected', 'ru', 'Заявка на премиум отклонена', 'Оплата за «{pet}» не подтверждена. {note}'),
      ('walk_rated', 'az', 'Yeni qiymət', '{pet} gəzintisi üçün {stars} ulduz aldınız.'),
      ('walk_rated', 'en', 'New rating', 'You got {stars} stars for the walk with {pet}.'),
      ('walk_rated', 'ru', 'Новая оценка', 'За прогулку с {pet} вы получили {stars} звёзд.'),
      ('withdrawal_requested', 'az', 'Yeni çıxarış sorğusu', '{who} {amount} ₼ çıxarış istəyir.'),
      ('withdrawal_requested', 'en', 'New withdrawal request', '{who} asked to withdraw {amount} ₼.'),
      ('withdrawal_requested', 'ru', 'Новый запрос на вывод', '{who} запросил(а) вывод {amount} ₼.'),
      ('withdrawal_paid', 'az', 'Ödəniş göndərildi', '{net} ₼ bank hesabınıza köçürüldü. Qəbz MIYAV hesabınızdadır.'),
      ('withdrawal_paid', 'en', 'Payment sent', '{net} ₼ was transferred to your bank account. The receipt is in your MIYAV wallet.'),
      ('withdrawal_paid', 'ru', 'Выплата отправлена', '{net} ₼ переведено на ваш счёт. Квитанция в кошельке MIYAV.'),
      ('withdrawal_rejected', 'az', 'Çıxarış təsdiqlənmədi', '{amount} ₼ balansınıza qaytarıldı. {note}'),
      ('withdrawal_rejected', 'en', 'Withdrawal not approved', '{amount} ₼ was returned to your balance. {note}'),
      ('withdrawal_rejected', 'ru', 'Вывод не одобрен', '{amount} ₼ возвращено на баланс. {note}')
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
