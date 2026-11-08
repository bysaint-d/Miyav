# Deployment guide

## Configuration (never commit secrets)

Desktop / JVM:

```text
MIYAV_SUPABASE_URL=https://<project-ref>.supabase.co
MIYAV_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

or `config/supabase.properties` copied from `config/supabase.properties.example`.

Android (`local.properties`, gitignored):

```text
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

Use the publishable key only. `SupabaseConfig` rejects `sb_secret_` and JWTs with `role=service_role`.

## Build

```bat
gradlew.bat test :desktop-app:compileJava :android-app:assembleDebug
gradlew.bat :desktop-app:run
```

Android application id: `az.miyav.native` (does not replace Expo `az.miyav.app`).

Debug APK: `android-app/build/outputs/apk/debug/android-app-debug.apk`.

## Database

Hosted project `<project-ref>` already has migrations through `20261107214742_admin_listing_moderation`. Do not reapply older Expo SQL. Do not run destructive SQL against production.

## Auth dashboard checks before launch

- Email confirmation templates
- Recovery (password reset) redirect URL
- JWT expiry (hosted Expo config uses 3600s; Java refreshes at 60s remaining)
- Disable phone OTP until `external.phone` is actually enabled

## Android release (not done here)

1. Create a upload keystore offline. Do not commit it.
2. Enable `minifyEnabled` only after a ProGuard keep pass for Gson models.
3. Build `bundleRelease` and test the adoption photo path on a physical device (storage + coarse location).

## Desktop release (not done here)

```bat
gradlew.bat :desktop-app:jpackage
```

is not configured yet. Until it is, ship `:desktop-app:run` from a JDK 21 machine with JavaFX resolved by the Gradle plugin.

## Operations

- Admins are `profiles.role = 'admin'` and `suspended = false`. Promote admins in SQL as `service_role`, never from the client.
- Listing moderation: desktop `Hide listing` → `admin_action('listing', id, 'deleted')`.
- User suspension: desktop `Suspend` → `admin_action('suspend', id, 'true')`.
