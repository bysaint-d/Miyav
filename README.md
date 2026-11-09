# MIYAV

MIYAV is a pet adoption and community platform. This repository is the native Java foundation: an Android application, a JavaFX desktop application, and a shared Java core. Both Java clients are built to use the existing Supabase project. The Expo application that already talks to that project stays at `<expo-app-path>` and is not part of this Gradle build.

The default language is Azerbaijani, with English and Russian catalogs. Android is the user client. Desktop is the admin client. Both share one Supabase Auth project.

## Requirements

- JDK 21 installed is sufficient. The build emits Java 17 bytecode.
- Android SDK with platform 35. `local.properties` must contain `sdk.dir`. That file is gitignored.
- No service-role key.

## Configuration

Environment variables:

```text
MIYAV_SUPABASE_URL
MIYAV_SUPABASE_PUBLISHABLE_KEY
```

or a gitignored `config/supabase.properties` copied from `config/supabase.properties.example`.

Android reads `supabase.url` and `supabase.publishableKey` from `local.properties`. Leave them empty to open the shell; sign-in then reports that configuration is missing. Do not put a secret or service-role key in either file.

## Build

```bat
gradlew.bat test :desktop-app:compileJava :android-app:assembleDebug
```

That command completed successfully on this machine: 24 JVM tests passed, the desktop sources compiled, and the Android debug APK was assembled. See `TEST_RESULTS.md`.

Desktop:

```bat
gradlew.bat :desktop-app:run
```

Android: open the repository root in Android Studio or install the debug APK from `android-app/build/outputs/apk/debug/`. The application id is `az.miyav.native`, so it does not replace the Expo package `az.miyav.app`. Configuration, signing, test accounts, and Google Play publishing are described in Azerbaijani in [`ANDROID_PRODUCTION_GUIDE.md`](ANDROID_PRODUCTION_GUIDE.md). Templates: `local.properties.example`, `keystore.properties.example`, `.env.example`.

## Guest mode

Both clients open on Discover without an account. Guests browse, search, filter, open listing details and photos, view publisher cards (never with a phone number), keep favourites on the device, switch language and read the help, terms and privacy pages. Account-only actions (posting, applying, contacting owners, messages, reports, notifications, walks, admin) go through `AuthStateManager.request(...)`, which opens the MIYAV sign-in sheet and returns the user to the same action afterwards. States are `GUEST`, `AUTHENTICATING`, `AUTHENTICATED` and `SESSION_EXPIRED`; an expired session falls back to guest browsing.

- Public reads: `DiscoveryService` + `PublicCatalogRepository` (publishable key only, `anon` role).
- Authenticated operations: the existing services, which require a session.
- Policy: `AccessPolicy` decides what the UI offers; RLS enforces it (migration `20261108094852_guest_mode_public_access`).
- Favourites: `FavoritesService`; device favourites move to the account only after the user agrees, through `merge_adoption_favorites` (deduplicated, inactive listings skipped).

## What works

- Register, login, logout, password-reset request, session restore, and access-token refresh.
- Encrypted on-disk sessions (AES-256-GCM).
- Profile edit and locale sync.
- Listing create, JPEG photos, publish, archive, discover, filter, detail.
- Adoption apply / accept / reject / cancel through SQL RPCs.
- Android coarse location mapped to Bakı, Sumqayıt, or Abşeron.
- Desktop admin dashboard (users, listings, applications, reports) gated by `private.is_admin()`.
- Desktop member area next to Discover: My listings (create, photos, publish, archive), Applications
  (incoming and sent), Messages (live chat with photos), Notifications, Profile (details, photo, password).
- Detailed listing filter on Android and desktop: gender, age band, vaccinated only, sort order.
- In-app chat with photos and "Message the owner" on adoption listings; Realtime updates with poll fallback.
- Phone push through FCM (`dispatch-push` Edge Function) once Firebase is configured.
- Server notifications in the recipient's language (az, en, ru).
- Adoption outbox retry when apply fails with a network error.

Telegram is deferred. See `KNOWN_LIMITATIONS.md` and `PRODUCTION_READINESS_REPORT.md`. Preview the desktop
member pages offline with `gradlew.bat :desktop-app:memberPreview` (screenshots in `desktop-app/build/member-preview`).

## Layout

```text
android-app/          native Android shell
desktop-app/          JavaFX shell
shared/common         errors, locale, clock
shared/domain         model, validators, repository ports
shared/application    services
shared/security       role gate and encrypted session store
shared/data           Supabase adapters
existing-expo-app/    pointer to the preserved Expo project
supabase/             note on the live migration history
docs/                 architecture, schema, security, OOP, contracts
```

## Security note

Clients use the publishable key only. Telegram is not deployed. Adoption acceptance is locked in SQL by `20261107212305`. Admin listing visibility is `20261107214742`. The Java apps compile and the core flows are implemented; live Auth/Storage was not walked in the last implementation session.
