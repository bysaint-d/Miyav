# Security

This milestone designs the security boundary and enforces the parts a client can enforce. It does not claim a production review.

## Keys

Java and Expo clients may hold a publishable key. `SupabaseConfig` rejects an `sb_secret_` key and a JWT whose payload role is `service_role` before any request is sent. `toString` on the config and on `AuthSession` does not include secrets. Repositories do not log response bodies.

`local.properties`, `config/supabase.properties`, and `.env` files are gitignored. `config/supabase.properties.example` has placeholders only.

## Authorization

`RoleGuard` hides admin actions unless the profile role loaded after login is `admin`. A user who changes the client can still call the button path; the guard throws `access.admin`. The database remains the real control:

- `profiles.role` and `profiles.suspended` are not in the authenticated update grant.
- `private.is_admin()` reads `profiles`, not `user_metadata`.
- Profile select RLS is self or admin.
- Adoption writes require `private.is_active()`.
- Chat reads require participation.

`authenticated` keeps `SELECT` on `role` and `suspended`. The Expo client uses `select('*')`, and an admin client needs the profile role after login. RLS is self-or-admin. `UPDATE` of those two columns stays `service_role` only. `preferred_language` is `SELECT` and `UPDATE` for `authenticated` only.

A suspended user can still receive a GoTrue session. `AuthService` loads the profile, signs that session out, and does not store it.

## Sessions

Android persists the session with `AndroidSessionStore`: AES-256-GCM with a key held in Android Keystore, ciphertext in private SharedPreferences, cloud backup and device transfer excluded. Older file-based Android sessions are migrated once and deleted. Desktop uses `EncryptedFileSessionStore` (AES-256-GCM) under `~/.miyav`. `MemorySessionStore` remains for tests. Logout calls `/auth/v1/logout` and clears the local copy in a `finally` block. A failed logout still drops the local token. It does not prove the server session is gone. `AuthService.restore` refreshes when the access token is within 60 seconds of expiry. Hosted JWT expiry in the Expo config is 3600 seconds with refresh-token rotation. Deleting a user later will not, by itself, revoke already issued tokens. This is not Android Keystore wrapping.

Android backup is disabled. Cleartext HTTP is disabled on the native app. `http` is accepted only for `localhost`, `127.0.0.1`, and `10.0.2.2`.

## Telegram

`@MiyavVerificationBot` is the intended bot. `UnavailableTelegramVerification` throws `telegram.not_deployed`. No client calls the Bot API. The token was not read. Zero Edge Functions are deployed, so a dashboard secret would not be in use.

When milestone 5 builds the function it must:

- generate a one-time linking token server-side
- store a hashed six-digit OTP with a five-minute expiry
- limit attempts and resend
- consume the OTP once
- rate-limit by user and by Telegram account
- treat success as proof of Telegram account control, not phone ownership

## Data handling

Listing search removes characters outside letters, digits, spaces, and hyphen so a filter cannot add another PostgREST expression. Profile updates cannot change role. Adoption status changes are not granted on the table. `accept_adoption_application` locks the listing before the application, and the partial unique index rejects a second accepted row. A direct client update of `status` has no privilege and no policy.

Location values are rounded to two decimal places and rejected outside the existing latitude/longitude box. That matches the coarse `numeric(6,2)` sitter coordinates. It is not a privacy proof for a future precise map.

Java listing uploads send JPEG bytes to `adoptions/{owner}/{listing}/{uuid}.jpg` and refuse files over 2 MiB. Rate limits, account deletion, and backup restoration are not implemented in the Java clients. Do not bypass the storage path policies.

## What is not done

- No penetration test and no RLS test suite against Postgres.
- Desktop session key is a local file, not an OS keychain.
- Email confirmation and recovery links are accepted only for the address the device asked for (`AuthService.completeLink`). Password change re-verifies the current password.
- No admin RPC for audit reads. The audit table is in `private` and is correctly unreachable with the publishable key.
- No claim that the product is safe to launch.
