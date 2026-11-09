-- Vets tab cleanup. The OpenStreetMap seed listed vet pharmacies, state vet offices, shelters and a shop as
-- "vets", and many unnamed "Zoo Mağaza" duplicates without address or phone. Those rows are hidden
-- (active = false, kept for admins). New fields: 24/7, WhatsApp number and an admin "verified" mark.

alter table public.places
  add column if not exists is_24h boolean not null default false,
  add column if not exists whatsapp text check (whatsapp is null or whatsapp ~ '^[0-9]{9,15}$'),
  add column if not exists verified boolean not null default false,
  add column if not exists updated_at timestamptz not null default now();

update public.places set is_24h = true where btrim(coalesce(opening_hours, '')) = '24/7';

update public.places set active = false, updated_at = now()
 where active and kind = 'vet'
   and name ilike any (array['%aptek%', '%darəsi%', '%sığınacaq%', '%mağaza%', '%xilasetmə%', '%qayğı mərkəzi%']);

update public.places set active = false, updated_at = now()
 where active and osm_id = 'way/1154369416';

update public.places set active = false, updated_at = now()
 where active and kind = 'pet_shop' and address is null and phone is null
   and lower(name) ~ '^(zoo ?ma[gğ]aza|zoo ?market( 1)?|zoomarket|yem d[uü]kan[iı]|zoo shop)$';

create or replace function private.touch_place()
 returns trigger
 language plpgsql
 set search_path to ''
as $function$
begin
  new.updated_at := now();
  if btrim(coalesce(new.opening_hours, '')) = '24/7' then
    new.is_24h := true;
  end if;
  return new;
end;
$function$;

drop trigger if exists places_touch on public.places;
create trigger places_touch before insert or update on public.places
  for each row execute function private.touch_place();
