-- Notifications in the recipient's language. Every server-side notification now goes through
-- private.notify(user, kind, params), which renders the title and body in profiles.preferred_language
-- (az, en, ru) and keeps kind + params so clients can re-render or route by type. Admin broadcasts stay
-- exactly as the admin typed them.

alter table public.notifications
  add column if not exists kind text,
  add column if not exists params jsonb not null default '{}'::jsonb;

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
      ('booking_completed', 'ru', 'Заказ обновлён', 'Услуга завершена. Вы можете оставить отзыв.')
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
  title := v_title;
  body := v_body;
  return next;
end;
$function$;

create or replace function private.notify(
  p_user uuid,
  p_kind text,
  p_params jsonb default '{}'::jsonb,
  p_conversation uuid default null,
  p_booking uuid default null
)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare
  v_lang text;
  v_text record;
begin
  if p_user is null then
    return;
  end if;
  select preferred_language into v_lang from public.profiles where id = p_user;
  select * into v_text from private.notification_text(p_kind, v_lang, p_params);
  if v_text.title is null then
    raise exception 'NOTIFICATION_KIND';
  end if;
  insert into public.notifications (user_id, title, body, conversation_id, booking_id, kind, params)
  values (p_user, v_text.title, v_text.body, p_conversation, p_booking, p_kind, coalesce(p_params, '{}'::jsonb));
end;
$function$;

revoke all on function private.notification_text(text, text, jsonb) from public, anon, authenticated;
revoke all on function private.notify(uuid, text, jsonb, uuid, uuid) from public, anon, authenticated;

create or replace function private.guard_adoption_application()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $function$
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
  perform private.notify(listing.owner_id, 'adoption_new', jsonb_build_object('pet', listing.name, 'listing', listing.id));
  return new;
end;
$function$;

create or replace function private.accept_adoption_application(p_application uuid)
 returns public.adoption_applications
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare
  application public.adoption_applications;
  listing public.adoption_listings;
  v_other uuid;
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
  for v_other in
    update public.adoption_applications
    set status = 'rejected'
    where listing_id = listing.id
      and id <> application.id
      and status = 'pending'
    returning applicant_id
  loop
    perform private.notify(v_other, 'adoption_other_accepted', jsonb_build_object('pet', listing.name, 'listing', listing.id));
  end loop;
  update public.adoption_applications
  set status = 'accepted'
  where id = application.id
  returning * into application;
  update public.adoption_listings
  set status = 'adopted'
  where id = listing.id;
  perform private.notify(application.applicant_id, 'adoption_accepted', jsonb_build_object('pet', listing.name, 'listing', listing.id));
  return application;
exception
  when unique_violation then
    raise exception 'CONFLICT';
end;
$function$;

create or replace function private.reject_adoption_application(p_application uuid)
 returns public.adoption_applications
 language plpgsql
 security definer
 set search_path to ''
as $function$
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
  perform private.notify(application.applicant_id, 'adoption_rejected', jsonb_build_object('pet', listing.name, 'listing', listing.id));
  return application;
end;
$function$;

create or replace function private.cancel_adoption_application(p_application uuid)
 returns public.adoption_applications
 language plpgsql
 security definer
 set search_path to ''
as $function$
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
  perform private.notify(listing.owner_id, 'adoption_cancelled', jsonb_build_object('pet', listing.name, 'listing', listing.id));
  return application;
end;
$function$;

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
    perform private.notify(
      case when new.sender_id = c.owner_id then c.sitter_id else c.owner_id end,
      'message_new',
      jsonb_build_object('who', case when new.sender_id = c.owner_id then c.owner_name else c.sitter_name end),
      c.id);
  end if;
  return new;
end;
$function$;

create or replace function private.walk_offer_notify()
 returns trigger
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare v_owner uuid; v_pet text; v_walker text; v_price numeric;
begin
  select owner_id, pet_name, price_per_hour into v_owner, v_pet, v_price from public.walk_listings where id = new.listing_id;
  select full_name into v_walker from public.profiles where id = new.walker_id;
  perform private.notify(v_owner, 'walk_offer_new',
    jsonb_build_object('who', v_walker, 'pet', v_pet, 'price', trim_scale(coalesce(new.price_per_hour, v_price))::text));
  return new;
end;
$function$;

create or replace function private.make_walk_offer(p_listing uuid, p_price numeric, p_message text)
 returns uuid
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare
  v_listing public.walk_listings;
  v_offer public.walk_offers;
  v_price numeric(7, 2);
  v_text text := btrim(coalesce(p_message, ''));
  v_room uuid;
  v_walker text;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found or v_listing.status <> 'open' or v_listing.starts_at <= now()
     or not private.adoption_owner_active(v_listing.owner_id) then
    raise exception 'WALK_LISTING_CLOSED';
  end if;
  if v_listing.owner_id = auth.uid() then raise exception 'WALK_OWN_LISTING'; end if;
  v_price := coalesce(p_price, v_listing.price_per_hour);
  if v_price < 1 or v_price > 500 then raise exception 'WALK_PRICE'; end if;
  if length(v_text) > 500 then raise exception 'WALK_NOTES'; end if;
  select * into v_offer from public.walk_offers where listing_id = p_listing and walker_id = auth.uid() for update;
  if found and v_offer.status = 'accepted' then raise exception 'WALK_OFFER_CLOSED'; end if;
  insert into public.walk_offers (listing_id, walker_id, message, price_per_hour, status)
  values (p_listing, auth.uid(), v_text, v_price, 'pending')
  on conflict (listing_id, walker_id) do update
     set message = excluded.message, price_per_hour = excluded.price_per_hour, status = 'pending', updated_at = now()
  returning * into v_offer;

  v_room := private.open_walk_conversation(p_listing, null);
  insert into public.messages (conversation_id, sender_id, body, walk_listing_id, price_per_hour)
  values (v_room, auth.uid(), v_text, p_listing, v_price);

  select full_name into v_walker from public.profiles where id = auth.uid();
  perform private.notify(v_listing.owner_id, 'walk_offer_new',
    jsonb_build_object('who', v_walker, 'pet', v_listing.pet_name, 'price', trim_scale(v_price)::text, 'listing', v_listing.id),
    v_room);
  return v_offer.id;
end;
$function$;

create or replace function private.respond_walk_offer(p_offer uuid, p_accept boolean)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare v_listing public.walk_listings; v_offer public.walk_offers; v_price numeric(7, 2);
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_offer from public.walk_offers where id = p_offer for update;
  if not found or v_offer.status <> 'pending' then raise exception 'WALK_OFFER_CLOSED'; end if;
  select * into v_listing from public.walk_listings where id = v_offer.listing_id for update;
  if v_listing.owner_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_listing.status <> 'open' then raise exception 'WALK_LISTING_CLOSED'; end if;
  v_price := coalesce(v_offer.price_per_hour, v_listing.price_per_hour);
  if p_accept then
    update public.walk_offers set status = 'accepted', updated_at = now() where id = p_offer;
    update public.walk_offers set status = 'declined', updated_at = now()
     where listing_id = v_listing.id and id <> p_offer and status = 'pending';
    update public.walk_listings set status = 'assigned', walker_id = v_offer.walker_id, agreed_price_per_hour = v_price
     where id = v_listing.id;
    perform private.notify(v_offer.walker_id, 'walk_offer_accepted',
      jsonb_build_object('pet', v_listing.pet_name, 'price', trim_scale(v_price)::text, 'listing', v_listing.id));
  else
    update public.walk_offers set status = 'declined', updated_at = now() where id = p_offer;
    perform private.notify(v_offer.walker_id, 'walk_offer_declined',
      jsonb_build_object('pet', v_listing.pet_name, 'listing', v_listing.id));
  end if;
end;
$function$;

create or replace function private.finish_walk_listing(p_listing uuid, p_status text)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare v_listing public.walk_listings;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into v_listing from public.walk_listings where id = p_listing for update;
  if not found then raise exception 'FORBIDDEN'; end if;
  if p_status = 'completed' then
    if v_listing.status <> 'assigned' or auth.uid() not in (v_listing.owner_id, v_listing.walker_id)
       or v_listing.starts_at > now() then
      raise exception 'WALK_LISTING_CLOSED';
    end if;
  elsif p_status = 'cancelled' then
    if v_listing.owner_id <> auth.uid() or v_listing.status not in ('open', 'assigned') then
      raise exception 'WALK_LISTING_CLOSED';
    end if;
    update public.walk_offers set status = 'declined', updated_at = now()
     where listing_id = p_listing and status = 'pending';
    if v_listing.walker_id is not null then
      perform private.notify(v_listing.walker_id, 'walk_cancelled',
        jsonb_build_object('pet', v_listing.pet_name, 'listing', v_listing.id));
    end if;
  else
    raise exception 'FORBIDDEN';
  end if;
  update public.walk_listings set status = p_status where id = p_listing;
end;
$function$;

create or replace function private.admin_action(p_action text, p_id uuid, p_value text, p_note text)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare v_sitter uuid; v_owner uuid;
begin
  if not private.is_admin() then raise exception 'FORBIDDEN'; end if;
  if p_action='verification' and p_value in ('approved','declined') then
    update public.verification_requests set status=p_value,note=p_note,reviewed_by=auth.uid(),reviewed_at=now() where id=p_id returning sitter_id into v_sitter;
    if not found then raise exception 'FORBIDDEN'; end if;
    update public.sitter_profiles set verified=(p_value='approved') where id=v_sitter;
    update public.profiles set role=case when p_value='approved' then 'sitter' else 'owner' end where id=v_sitter and role<>'admin';
    perform private.notify(v_sitter, 'verification_' || p_value);
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
  elsif p_action='booking' and p_value='cancelled' then
    update public.bookings set status='cancelled'
     where id=p_id and status in ('pending','accepted')
    returning owner_id, sitter_id into v_owner, v_sitter;
    if not found then raise exception 'FORBIDDEN'; end if;
    perform private.notify(v_owner, 'booking_moderated', '{}'::jsonb, null, p_id);
    perform private.notify(v_sitter, 'booking_moderated', '{}'::jsonb, null, p_id);
  else raise exception 'FORBIDDEN'; end if;
  insert into private.admin_audit(actor_id,action,target_id,value,note) values(auth.uid(),p_action,p_id,p_value,p_note);
end;
$function$;

create or replace function private.create_booking(p_service_id uuid, p_pet_id uuid, p_starts_at timestamp with time zone, p_ends_at timestamp with time zone, p_expected_total numeric, p_notes text)
 returns uuid
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare v_service public.services; v_id uuid; v_conversation uuid; v_total numeric;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  if p_starts_at is null or p_ends_at is null or p_starts_at <= now() or p_ends_at < p_starts_at + interval '30 minutes' or p_ends_at > p_starts_at + interval '30 days' then raise exception 'INVALID_DATES'; end if;
  select * into v_service from public.services where id=p_service_id and active for share;
  if not found or v_service.sitter_id=auth.uid() then raise exception 'FORBIDDEN'; end if;
  if not private.is_public_sitter(v_service.sitter_id) then raise exception 'NOT_APPROVED'; end if;
  if not exists(select 1 from public.pets p join public.sitter_profiles s on s.id=v_service.sitter_id where p.id=p_pet_id and p.owner_id=auth.uid() and p.animal_type=any(s.animal_types)) then raise exception 'FORBIDDEN'; end if;
  -- Lock the provider while checking availability to serialize changes and requests.
  perform 1 from public.sitter_profiles where id=v_service.sitter_id for update;
  if not exists(select 1 from public.availability where sitter_id=v_service.sitter_id and starts_at<=p_starts_at and ends_at>=p_ends_at) then raise exception 'NOT_AVAILABLE'; end if;
  v_total := v_service.price * ceil(extract(epoch from p_ends_at-p_starts_at) / case when v_service.unit='day' then 86400 else 3600 end);
  if p_expected_total is null or p_expected_total<>v_total then raise exception 'PRICE_CHANGED'; end if;
  insert into public.bookings(owner_id,sitter_id,pet_id,service_id,service_kind,starts_at,ends_at,price_per_unit,price_unit,total_price,notes)
  values(auth.uid(),v_service.sitter_id,p_pet_id,p_service_id,v_service.kind,p_starts_at,p_ends_at,v_service.price,v_service.unit,v_total,coalesce(p_notes,'')) returning id into v_id;
  v_conversation := private.open_conversation(v_service.sitter_id);
  perform private.notify(v_service.sitter_id, 'booking_new', '{}'::jsonb, v_conversation, v_id);
  perform private.notify(auth.uid(), 'booking_sent', '{}'::jsonb, v_conversation, v_id);
  return v_id;
exception when exclusion_violation then raise exception 'BOOKING_CONFLICT';
end; $function$;

create or replace function private.transition_booking(p_booking_id uuid, p_status text)
 returns void
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare b public.bookings;
begin
  if not private.is_active() then raise exception 'FORBIDDEN'; end if;
  select * into b from public.bookings where id=p_booking_id for update;
  if not found or auth.uid() not in (b.owner_id,b.sitter_id) then raise exception 'FORBIDDEN'; end if;
  if not (
    (p_status in ('accepted','declined') and b.status='pending' and auth.uid()=b.sitter_id and b.starts_at>now()) or
    (p_status='cancelled' and b.status in ('pending','accepted') and b.starts_at>now()) or
    (p_status='completed' and b.status='accepted' and auth.uid()=b.sitter_id and b.ends_at<=now()) or
    (p_status='cancelled' and b.status='pending' and b.starts_at<=now())
  ) then raise exception 'INVALID_TRANSITION'; end if;
  update public.bookings set status=p_status where id=b.id;
  perform private.notify(b.owner_id, 'booking_' || p_status, '{}'::jsonb, null, b.id);
  perform private.notify(b.sitter_id, 'booking_' || p_status, '{}'::jsonb, null, b.id);
end; $function$;
