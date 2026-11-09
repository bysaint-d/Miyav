-- walk_listings_read calls private.offered_on for every row; without EXECUTE the guest board failed with
-- 42501. The function only answers for auth.uid(), so for guests it is always false.
grant execute on function private.offered_on(uuid) to anon;
