# Database design

The live schema is the source of truth. Milestone 4 applied one additive migration. It did not drop or rename existing tables.

## Live inventory

Applied versions: `20261027175631`, `20261027181207`, `20261027193000`, `20261028083445`, `20261028085008`, `20261107212305`, `20261107214742`.

| Live table | Role today | Requested name | Decision |
| --- | --- | --- | --- |
| `profiles` | Account profile. `role` is `owner`, `sitter`, or `admin`. `suspended` is separate. `preferred_language` is `az`, `en`, or `ru` | `profiles`, `user_roles` | Keep the role column. A second role table would duplicate authorization |
| `pets` | Private care pets used by bookings | not the public adoption pet | Keep. Do not overload it with adoption listings |
| `adoption_listings` | Public adoption listings | `pets` in the new domain | Java `Pet` reads this table. Do not rename it in place |
| `adoption_favorites` | Saved listings | `favorites` | Keep the existing table |
| `sitter_profiles`, `services`, `availability`, `bookings`, `reviews`, `verification_requests` | Care marketplace already shipped in Expo | outside the new adoption brief | Preserve |
| `conversations`, `messages` | Two-member conversations. `sitter_id` now references `profiles` | `conversations`, `conversation_members`, `messages` | Keep the pair model. Do not replace it with a members table until a reviewed migration copies rows |
| `notifications` | Per-user notices plus push outbox columns | `notifications` | Keep. There is no type column yet; the client maps these to `SYSTEM` |
| `reports` | User reports a user | `reports` | Keep |
| `push_devices` | Expo push tokens | `device_tokens` | Keep the existing table |
| `private.admin_audit` | Privileged audit, identity primary key | `audit_logs` | Keep it in `private`. Do not expose a public copy |
| storage buckets `avatars`, `pets`, `messages`, `adoptions` | Image paths and MIME limits | `pet_images` | Keep path checks. A normalized image table can wait |

Every listed public table has RLS enabled. `private` is not an exposed Data API schema. Privileged functions are `SECURITY DEFINER` in `private` with an empty `search_path`, and public wrappers are `SECURITY INVOKER`.

Profiles grant `authenticated` select on `id`, `full_name`, `city`, `district`, `phone`, `avatar_path`, `created_at`, `role`, `suspended`, and `preferred_language`. Update is limited to `full_name`, `city`, `district`, `phone`, `avatar_path`, and `preferred_language`. `role` and `suspended` stay select-only for `authenticated`. That select stays because the Expo client reads `select('*')` and an admin session has to learn its role from the profile row, not from editable JWT metadata. RLS still limits profile reads to self or admin. `anon` has no grant on `preferred_language`.

Cities stored today are `Bakı`, `Sumqayıt`, and `Abşeron`. Adoption listing status values are `draft`, `active`, `adopted`, and `deleted`. Species values are `dog`, `cat`, `bird`, and `other`. Gender values are `female`, `male`, and `unknown`. An active listing requires at least one photo. Photo paths must match `{owner}/{listing}/{uuid}.jpg`.

Booking overlap is already transaction-safe through GiST exclusion constraints. Adoption acceptance is now locked in SQL. `adoption_applications` has a partial unique index for one accepted row per listing and another for one pending row per listing and applicant. Clients may insert only `listing_id` and `message`. Status changes go through `accept_adoption_application`, `reject_adoption_application`, and `cancel_adoption_application`.

Installed extensions include `btree_gist`, `pgcrypto`, `uuid-ossp`, `pg_stat_statements`, and `supabase_vault`. PostGIS is not installed. Sitter coordinates are `numeric(6,2)` inside latitude 38–42 and longitude 44–51, which is already an approximate location. Do not enable PostGIS until a query needs it.

## Domain mapping

| Domain value | Live database value |
| --- | --- |
| `PetStatus.DRAFT` | `draft` |
| `PetStatus.PUBLISHED` | `active` |
| `PetStatus.ADOPTED` | `adopted` |
| `PetStatus.ARCHIVED` | `deleted` |
| `PetStatus.PENDING`, `REJECTED` | no live value |

`ApplicationStatus` maps directly to `pending`, `accepted`, `rejected`, and `cancelled` on `adoption_applications`.

`public.pets` and `adoption_listings` stay separate. One is a care record with weight and an emergency contact. The other is a public listing.

## Applied in `20261107212305`

`profiles.preferred_language` is `text not null default 'az'` with a check for `az`, `en`, and `ru`.

`adoption_applications` stores `listing_id`, `applicant_id` (default `auth.uid()`), `message` (10–2000 characters), `status` (default `pending`), and timestamps. RLS allows select for the applicant, an admin, or the listing owner, and insert only when the caller is the applicant, the status is pending, the listing is active, and the caller is not the owner. There is no client update or delete policy. A before-insert trigger locks the listing and writes an owner notification. Accept locks the listing, then the application, rejects the other pending rows, marks the listing `adopted`, and maps a unique violation to `CONFLICT`.

`private.admin_audit` still has RLS and no policies. That deny-all is intentional. The security advisor INFO lint is accepted and does not get a permissive policy.

## Applied in `20261107214742`

Authenticated admins (`private.is_admin()`) may SELECT all `adoption_listings` rows and adoption storage objects. They still cannot PATCH listings as themselves. Hide/restore goes through `private.admin_action` with `p_action='listing'` and `p_value` `deleted` or `active`. Adopted listings are left unchanged. Active restore still requires at least one photo.

## Still proposed

These are not migrations and must not be applied by copying this section into SQL without a review.

1. `telegram_accounts` (`user_id` unique, `telegram_user_id` unique, `username`, `linked_at`). Linking proves control of a Telegram account, not ownership of a phone number.
2. `otp_challenges` in `private`: hashed code, expiry of five minutes, attempt counter, resend time, and consumed-at. No plaintext OTP.
3. Keep audit rows in `private.admin_audit`. Add columns only if the current `action`, `target_id`, `value`, and `note` shape cannot represent a new admin action.

Do not drop `pets`, rename `adoption_listings`, or rewrite conversation foreign keys. The tables are empty today, and that is not permission to destroy the schema the Expo client already uses.

## Client write rules already enforced

- Publishable key only. `SupabaseConfig` rejects `sb_secret_` and a JWT whose `role` is `service_role`.
- Profile updates send `full_name`, `city`, `district`, `phone`, and `preferred_language`. They never send `role` or `suspended`.
- A pending application insert sends `listing_id` and `message` only. Accept, reject, and cancel call the matching RPC.
- Listing search strips characters that would change a PostgREST `or` filter.
