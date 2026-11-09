-- Sponsored places: shown first in the list with a large card and a large map marker. First sponsor:
-- Khazar University Hasvet School of Veterinary Medicine animal hospital (address, phone and hours from its
-- public listings; admins can correct them in the console).

alter table public.places
  add column if not exists sponsored boolean not null default false,
  add column if not exists website text check (website is null or website ~ '^https://'),
  add column if not exists description text check (description is null or length(description) <= 300);

insert into public.places (kind, name, address, phone, opening_hours, latitude, longitude, osm_id,
                           verified, sponsored, website, description)
values ('vet', 'Xəzər Universiteti Hasvet Heyvan Xəstəxanası', 'Cəmşid Naxçıvanski küç. 72, Nizami r-nu',
        '+994102306677', 'Mo-Sa 09:00-17:00; Su off', 40.406145, 49.955867, 'miyav/khazar-hasvet',
        true, true, 'https://www.khazarhasvetvetmed.com/az/animalhospital',
        'Xəzər Universiteti Hasvet Baytarlıq Təbabəti fakültəsinin müasir klinikası: müayinə, peyvənd, cərrahiyyə, laboratoriya və görüntüləmə.')
on conflict (osm_id) do update
   set name = excluded.name, address = excluded.address, phone = excluded.phone,
       opening_hours = excluded.opening_hours, latitude = excluded.latitude, longitude = excluded.longitude,
       verified = true, sponsored = true, active = true, website = excluded.website, description = excluded.description;
