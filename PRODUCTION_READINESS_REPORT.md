# MIYAV v1 production readiness

Date: 8 November 2026.

This is an implementation report, not a launch certificate. Shared Java services, Android user flows, and the desktop admin console were completed and compiled. Live email/password journeys were not executed against the hosted project in this session.

## Verdict

**Core v1 is implemented and buildable. It is not proven production-ready.**

What is true:

- Android debug APK assembled (`:android-app:assembleDebug` BUILD SUCCESSFUL).
- Desktop sources compiled (`:desktop-app:compileJava` BUILD SUCCESSFUL).
- 24 JVM unit tests ran, 0 failures, 0 skipped.
- Hosted additive migration `20261107214742_admin_listing_moderation` is applied.
- RLS remains enabled. Clients still cannot UPDATE `profiles.role` or `profiles.suspended`.
- Telegram, push, Google PKCE completion, and messaging send stay deferred.

What is not proven:

- End-to-end login, listing create, photo upload, or adoption apply against live Auth/Storage.
- Instrumented Android UI tests.
- Password-reset email delivery and the hosted email confirmation link.
- Outbox replay on a real device after process death.

Do not ship a signed Play/jpackage release until those live paths are exercised with a real publishable key.

## What was implemented

### Authentication

- Register, login, logout, password-reset request on Android and desktop.
- Session restore on splash / desktop start.
- Token refresh when the access token is within 60 seconds of expiry.
- Suspended profiles are signed out and not stored.
- Persistent AES-256-GCM session files (`EncryptedFileSessionStore`). Android uses app-private storage. Desktop uses `%USERPROFILE%\.miyav`.

### Profiles

- `UserService` is constructed in both composition roots.
- Profile load/edit (name, city, district, phone) and locale sync.

### Pets and discovery

- Create draft listings, attach JPEG photos (owner/listing/uuid.jpg), publish, archive.
- Discovery with search, city, species, pagination query, empty/error states.
- Detail view and image download through authenticated storage.

### Adoption

- `AdoptionService` is wired into Android apply / incoming accept-reject / cancel.
- Duplicate and ownership rules remain in the service and in SQL RPCs.

### Location

- Android requests `ACCESS_COARSE_LOCATION` only, maps a rounded point to Bakı / Sumqayıt / Abşeron, and never sends exact coordinates to the backend.
- Desktop has no GPS; admins filter by stored city values.

### Desktop administration

- Non-admin sessions see `admin.denied` and can only log out.
- Admins get dashboard counts, user list, listing list, applications, reports.
- Suspend, listing hide/restore, and report resolve call `admin_action`. Backend `private.is_admin()` is the authority.

### Outbox

- Room schema v2 with `attempts`.
- `OutboxSyncWorker` returns success when empty, retries on network/auth failure, and processes `APPLY_ADOPTION` only.
- Listing image saves are online-only.

## Security audit (this session)

Verified:

- No service_role or `sb_secret_` in clients (`SupabaseConfig` still rejects them).
- Profile PATCH still omits `role` and `suspended`.
- Adoption status changes still go through RPCs.
- Listing insert omits `owner_id` (database default `auth.uid()`).
- Storage JPEG path matches `private.valid_adoption_photos`.
- New admin SELECT policy is `private.is_admin()` only.
- `private.admin_audit` still has RLS and no policies (deny-all). Advisor INFO is unchanged and accepted.
- `AuthSession.toString()` still omits tokens. Encrypted session files do not contain the raw token as plaintext.

Not done:

- Penetration test.
- Android Keystore wrapping (file AES in private storage instead of EncryptedSharedPreferences).
- Live RLS policy tests with two real users.

## Database

Applied hosted versions:

`20261027175631`, `20261027181207`, `20261027193000`, `20261028083445`, `20261028085008`, `20261107212305`, `20261107214742`.

`20261107214742` adds:

- `adoption_admin_read` on `adoption_listings`
- `adoption_image_admin_read` on `storage.objects`
- `listing` branch on `private.admin_action` (`deleted` / `active`, never `adopted`)

No tables were dropped. No production rows were deleted.

## Remaining external blockers

- Hosted email templates and SMTP for confirmation / reset (Auth dashboard, not this repo).
- A real publishable key in gitignored `local.properties` / `config/supabase.properties` on each machine.
- Play signing and jpackage installers.
- Telegram bot token and Edge Function (explicitly deferred).

## Production-readiness checklist

| Requirement | Status |
| --- | --- |
| Android compiles | Yes |
| Desktop compiles | Yes |
| Auth implemented on both platforms | Yes (unit-tested; live Auth not exercised here) |
| Shared accounts | Same Supabase Auth project |
| Profiles | Yes |
| Pet management | Yes |
| Discovery | Yes |
| Adoption applications | Yes |
| Admin workflows | Yes (RPC-backed) |
| Database access / RLS | Additive policy applied; live two-user test not run |
| Outbox | Adoption queue implemented; listing photos online-only |
| Critical placeholders removed from core journeys | Yes for login/register/reset, listings, adopt, admin |
| Telegram | Deferred, fail-closed |
