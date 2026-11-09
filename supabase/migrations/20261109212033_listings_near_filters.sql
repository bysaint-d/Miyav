-- Detailed listing filter for "nearby": gender, age band (upper bound exclusive) and vaccinated only.
-- New parameters have defaults, so callers that send the old seven arguments keep working.

drop function if exists public.listings_near(double precision, double precision, integer, text, text, integer, integer);

create function public.listings_near(
  p_lat double precision,
  p_lng double precision,
  p_radius_km integer default 25,
  p_species text default null,
  p_search text default null,
  p_limit integer default 20,
  p_offset integer default 0,
  p_gender text default null,
  p_min_age integer default null,
  p_max_age integer default null,
  p_vaccinated boolean default false
)
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
  order by private.distance_m(p_lat, p_lng, l.latitude::float8, l.longitude::float8), l.created_at desc
  limit least(greatest(p_limit, 1), 50)
  offset greatest(p_offset, 0);
$function$;

revoke all on function public.listings_near(double precision, double precision, integer, text, text, integer, integer, text, integer, integer, boolean) from public;
grant execute on function public.listings_near(double precision, double precision, integer, text, text, integer, integer, text, integer, integer, boolean) to anon, authenticated, service_role;

create index if not exists adoption_listings_active_filters_idx
  on public.adoption_listings (species, gender, age_months)
  where status = 'active';
