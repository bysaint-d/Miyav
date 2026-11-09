-- Vets and pet shops shown on the Vets tab, nearest first. Seeded from OpenStreetMap (ODbL, (c) OSM
-- contributors) for Bakı, Sumqayıt and Abşeron; admins maintain the list afterwards.
create table if not exists public.places (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('vet', 'pet_shop')),
  name text not null check (length(btrim(name)) between 1 and 120),
  address text check (length(address) <= 200),
  phone text check (length(phone) <= 40),
  opening_hours text check (length(opening_hours) <= 120),
  latitude numeric(9, 6) not null check (latitude between 38 and 42),
  longitude numeric(9, 6) not null check (longitude between 44 and 51),
  osm_id text unique,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists places_kind_active_idx on public.places (kind) where active;

alter table public.places enable row level security;

create policy places_public_read on public.places
  for select to anon, authenticated using (active or (select private.is_admin()));
create policy places_admin_insert on public.places
  for insert to authenticated with check ((select private.is_admin()));
create policy places_admin_update on public.places
  for update to authenticated using ((select private.is_admin())) with check ((select private.is_admin()));
create policy places_admin_delete on public.places
  for delete to authenticated using ((select private.is_admin()));

revoke all on public.places from anon, authenticated;
grant select on public.places to anon, authenticated;
grant insert, update, delete on public.places to authenticated;

insert into public.places (kind, name, address, phone, opening_hours, latitude, longitude, osm_id) values
  ('pet_shop', 'Zoo Market', 'Üzeyir Hacıbəyov 2', null, null, 40.37098, 49.840728, 'node/1854436817'),
  ('vet', 'AARC Heyvanların Xilasetmə Mərkəzi \ \ Aşağı Güzdək', null, null, null, 40.458305, 49.662132, 'node/2914379701'),
  ('pet_shop', 'Zoo Mağaza', null, null, null, 40.531235, 49.786019, 'node/3750537772'),
  ('pet_shop', 'Yem Dukani', null, null, null, 40.538003, 49.711643, 'node/4231853706'),
  ('pet_shop', 'Animal planet', null, null, null, 40.372121, 49.840865, 'node/4303024310'),
  ('pet_shop', 'Zoo Panda', '14 İyul', null, 'Mo-Su 10:00-19:00', 40.431052, 50.034187, 'node/4330922891'),
  ('vet', 'White Bear', 'Qurban Abbasov küç. 128', '0502000750', null, 40.346692, 49.837101, 'node/4428002491'),
  ('vet', 'Əlifba', null, null, null, 40.390992, 49.814856, 'node/5068701221'),
  ('pet_shop', 'Zoo Shop', null, null, null, 40.365943, 49.821105, 'node/5268555848'),
  ('pet_shop', 'Zoomagaza', 'Sarayevo', null, 'Mo-Su 09:00-22:00', 40.386403, 49.964833, 'node/5329041221'),
  ('vet', 'Baytarlıq Stansiyası \ Mərdəkan', 'Rəfail Cəfərov 2', null, null, 40.48526, 50.14403, 'node/5418832526'),
  ('vet', 'Evsiz heyvanlar üçün sığınacaq', 'Rəfail Cəfərov 2', null, null, 40.485198, 50.143689, 'node/5427430923'),
  ('vet', 'Baytar mağazası', null, null, null, 40.321795, 49.733614, 'node/5455766822'),
  ('pet_shop', 'Zoomarket', null, null, null, 40.466723, 50.071962, 'node/5520218021'),
  ('pet_shop', 'Zoo Lider', 'Anar Şükürov küçəsi 35', null, 'Mo-Su 08:30-19:00', 40.418285, 49.838762, 'node/7389716085'),
  ('pet_shop', 'Frends Zoo Mağaza', null, null, null, 40.370456, 49.844798, 'node/9812838472'),
  ('vet', 'Baytarlıq İdarəsi', null, null, null, 40.448321, 50.089114, 'node/9943495187'),
  ('pet_shop', 'Yem Dükanı', null, null, null, 40.483715, 49.984841, 'node/10144206663'),
  ('pet_shop', 'Zoo Mağaza', null, null, null, 40.480346, 49.986502, 'node/10144206693'),
  ('pet_shop', 'Zoo Mağaza', null, null, null, 40.460396, 49.921458, 'node/10229016506'),
  ('vet', 'Baytarlıq İdarəsi', null, null, null, 40.470754, 49.843176, 'node/10268224396'),
  ('pet_shop', 'Zoo Mağaza', null, null, null, 40.466349, 49.827377, 'node/10268386289'),
  ('pet_shop', 'Zoo Market', null, null, null, 40.447386, 49.843906, 'node/10276526798'),
  ('pet_shop', 'Zoo Market 1', null, null, null, 40.459356, 49.842268, 'node/10276534711'),
  ('pet_shop', 'Royal Canin', null, null, null, 40.434739, 49.84428, 'node/10278860887'),
  ('pet_shop', 'Zoo Kakadu', null, null, null, 40.431708, 49.842543, 'node/10278922349'),
  ('pet_shop', 'Zoo Market', null, null, null, 40.441397, 49.84454, 'node/10295517557'),
  ('pet_shop', 'Zoo Mağaza', 'Xudadat Məlikaslanov küç.', null, null, 40.444099, 49.809489, 'node/10295645666'),
  ('vet', 'Baytarlıq Apteki', 'Əliağa Vahid küç.', null, null, 40.459525, 49.756184, 'node/10540122621'),
  ('vet', 'Baytarlıq Apteki', 'Əliağa Vahid küç.', null, null, 40.46849, 49.754543, 'node/10540422160'),
  ('pet_shop', 'Zoo Market', null, null, null, 40.491597, 49.749036, 'node/10552813253'),
  ('vet', 'Baytarlıq Apteki', 'Azadlıq pr.', null, null, 40.434136, 49.843982, 'node/10648385902'),
  ('pet_shop', 'Zoo Mağaza', null, null, null, 40.51858, 50.10441, 'node/10656114244'),
  ('pet_shop', 'Zoo Mağaza', 'Buzovna-Şüvələn yolu', null, null, 40.508389, 50.114777, 'node/10659276238'),
  ('vet', 'Baytarlıq Apteki', 'Almas İldırım \ Yesenin küç.', null, null, 40.485077, 50.184506, 'node/10726518467'),
  ('pet_shop', 'Zoomağaza', 'Almas İldırım \ Yesenin küç.', null, null, 40.484556, 50.180624, 'node/10728210413'),
  ('pet_shop', 'Zoomağaza', 'Almas İldırım \ Yesenin küç.', null, null, 40.488375, 50.139371, 'node/10728288710'),
  ('pet_shop', 'Zoo Market', 'Almas İldırım \ Yesenin küç.', null, null, 40.49057, 50.145418, 'node/10735864112'),
  ('vet', 'Baytarlıq Apteki', 'Almas İldırım \ Yesenin küç.', null, null, 40.489309, 50.142004, 'node/10735864149'),
  ('pet_shop', 'Zoo Sfera', 'Hövsan Şossesi', null, null, 40.35845, 50.065299, 'node/10753812437'),
  ('pet_shop', 'Zoo Market', '32-ci Hövsan küç.', null, null, 40.363732, 50.080761, 'node/10754326013'),
  ('pet_shop', 'Pet Shop Baku', 'Mərdəkan şossesi', null, null, 40.474243, 50.1094, 'node/10778671252'),
  ('pet_shop', 'Alfa Zoo Mağaza', null, null, null, 40.433135, 49.983033, 'node/10832410194'),
  ('pet_shop', 'Zoo Mağaza', 'Heydər Əliyev küç.', null, null, 40.529956, 49.786963, 'node/10876530035'),
  ('pet_shop', 'House Az Zoo', null, null, null, 40.55032, 49.780901, 'node/10879419780'),
  ('vet', 'Baytarlıq Apteki', 'Rəsulzadə küç.', null, null, 40.529678, 49.711254, 'node/10896876613'),
  ('pet_shop', 'Zoo Mağaza', 'Heydər Əliyev küç.', null, null, 40.543171, 49.716467, 'node/10896876753'),
  ('pet_shop', 'Zoo Market', null, null, null, 40.456942, 49.722804, 'node/10951959979'),
  ('pet_shop', 'Zoo Mağaza', 'Heydər Əliev pr.', null, null, 40.457844, 49.733324, 'node/10969711398'),
  ('pet_shop', 'Zoo Kiki', 'Qalubiyyə küç.', null, null, 40.454709, 49.7435, 'node/10974521499'),
  ('pet_shop', 'Zoo Butik', '28-ci Məhəllə Xırdalan', null, null, 40.456326, 49.738157, 'node/10977336041'),
  ('pet_shop', 'Zoo Mağaza', 'Heydər Əliev pr.', null, null, 40.445024, 49.75419, 'node/10984699069'),
  ('vet', 'Baytarlıq Apteki', null, null, null, 40.470852, 49.622679, 'node/11091674840'),
  ('pet_shop', 'Zoo Market', 'Qurtuluş küç.', null, null, 40.55945, 49.889208, 'node/11105810025'),
  ('vet', 'İdea Heyvanlara Qayğı Mərkəzi \ Qatadağ', null, null, null, 40.28839, 49.72455, 'node/11200742164'),
  ('pet_shop', 'Miki Zoo Mağazası', null, null, null, 40.324891, 49.729439, 'node/11284595483'),
  ('vet', 'Doktor Zoo Baytar', null, null, null, 40.572997, 49.693258, 'node/11500957693'),
  ('pet_shop', 'Anar Zoo', null, null, null, 40.380948, 49.830224, 'node/11879472249'),
  ('pet_shop', 'Zoo 28', null, null, null, 40.377106, 49.841301, 'node/11883518310'),
  ('pet_shop', 'Mr. Curry', null, null, null, 40.378699, 49.836485, 'node/11883518357'),
  ('pet_shop', 'Alfapet', null, null, null, 40.37696, 49.808761, 'node/11883782693'),
  ('pet_shop', 'Flamingo', null, null, null, 40.380018, 49.8199, 'node/11883964513'),
  ('pet_shop', 'ZOO MARKET MILO ZOO', 'Elşən Süleymanov küçəsi 166B', '+994557865319', '10:00-22:00', 40.407548, 49.958622, 'node/12118527565'),
  ('pet_shop', 'Beethoven Pet Store', 'Ənvər Qasimzadə küç. 50', '+994556822121', 'Mo-Su 09:00-00:00', 40.391384, 49.843347, 'node/12153346256'),
  ('vet', 'Zoo HappyVet', 'Koroğlu Rəhimov küç. 9', '+994519529618', '24/7', 40.395808, 49.860549, 'node/13078794401'),
  ('pet_shop', 'ZooMarket', 'Əbdüləzəl Dəmirçizadə küçəsi 114-120', null, 'Mo-Su 10:00-22:00', 40.392441, 49.860783, 'node/14252567748'),
  ('pet_shop', 'ZooPrime.Az', 'Akademik Lətif İmanov küçəsi 1', '+994518721407', '11:00-22:00', 40.376036, 49.813884, 'way/211266569'),
  ('vet', 'Baytarlıq Stansiyası', null, null, null, 40.485174, 50.144078, 'way/1154369416')
on conflict (osm_id) do nothing;

update public.places
   set name = btrim(regexp_replace(name, '\s*\\(\s*\\)?\s*', ' · ', 'g')),
       address = btrim(regexp_replace(address, '\s*\\(\s*\\)?\s*', ' · ', 'g'));