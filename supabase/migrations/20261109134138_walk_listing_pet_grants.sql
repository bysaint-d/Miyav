-- Column grants: members choose the pet; only the walk_listings_pet trigger writes the photo path.
grant insert (pet_id), update (pet_id) on public.walk_listings to authenticated;
