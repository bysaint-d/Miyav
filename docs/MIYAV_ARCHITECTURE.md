# MIYAV architecture

MIYAV is a pet adoption and community product with one Supabase backend and three clients:

- The existing Expo / React Native application, preserved at `<expo-app-path>`.
- A native Android application in `android-app` (`az.miyav.native`).
- A JavaFX desktop application in `desktop-app`.

The Java clients share domain rules, validators, and repository contracts. They do not share UI code. The Expo application is not part of the Gradle build and was not moved.

## What was inspected

The Cursor workspace `Heyvan` had no application source, no `package.json`, and no commits. It contained an unrelated `node_modules` fragment (`react-native-reanimated`, `react-native-worklets`) and an empty Git repository.

The working product is the Expo SDK 58 application on the Desktop. It uses `@supabase/supabase-js` 2.118.0, Expo Router, SecureStore session chunks, PKCE, and a publishable key. Google sign-in is implemented in `src/components/AlternativeAuth.tsx` with `signInWithOAuth`. Phone OTP UI exists in that client, but the hosted Auth setting `external.phone` is false. There is no Telegram code anywhere in that project.

Hosted project `<project-ref>` (ap-northeast-1, Postgres 17.11) is `ACTIVE_HEALTHY`. The five migrations from the Expo repo are applied. Public tables currently have row level security enabled and zero rows. No Edge Functions are deployed. `dispatch-push` exists only as source. PostGIS is not installed. `btree_gist` is installed and is already used by booking overlap constraints.

Client configuration names, without values:

- Expo: `EXPO_PUBLIC_SUPABASE_URL`, `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY`, `EXPO_PUBLIC_EAS_PROJECT_ID`.
- Edge worker example: `PUSH_WORKER_SECRET`, `EXPO_ACCESS_TOKEN`.
- Java clients: `MIYAV_SUPABASE_URL`, `MIYAV_SUPABASE_PUBLISHABLE_KEY`.

The hosted URL is the project host. The key prefix in the Expo environment is a publishable key, not a secret key. Google external auth is enabled. Email signup is enabled. `disable_signup` is false.

## Module boundaries

```text
android-app  ─┐
desktop-app  ─┼─► application ─► domain ─► common
              │         │
              │         └─► security ─► domain
              └─► data ─► domain, common
```

| Module | Responsibility |
| --- | --- |
| `shared/common` | Errors, locale catalog, clock |
| `shared/domain` | Entities, enums, validators, repository interfaces |
| `shared/security` | Role gate and in-memory session store |
| `shared/application` | Use cases. No Android or JavaFX types |
| `shared/data` | Supabase HTTP adapters |
| `android-app` | XML UI, navigation, Room outbox, WorkManager shell |
| `desktop-app` | JavaFX UI and the same use cases |

Activities, fragments, and JavaFX controllers call services. They do not encode adoption rules.

## Runtime decisions

- Java bytecode target is 17. The machine used to compile currently has Temurin 21, which is new enough to emit `--release 17`.
- Android `applicationId` is `az.miyav.native`. The Expo app already owns `az.miyav.app`, so a debug install of the native shell does not replace it.
- Sessions for the Java shells stay in memory. Tokens are not written to a file in this milestone.
- Admin visibility is gated by `RoleGuard`, and the role value comes from `profiles.role` after login. That check is not authorization. Postgres RLS remains the authority.
- Listing reads map the live status `active` to the domain status `PUBLISHED`. Adoption applications insert `listing_id` and `message`, then change status through locked RPCs. Message send still fails with `schema.not_ready`.
- Telegram verification fails closed with `telegram.not_deployed` until an Edge Function exists. The bot token is never read by a client.

## Build

`./gradlew test :desktop-app:compileJava :android-app:assembleDebug`

Versions are pinned in `gradle/libs.versions.toml`. Upgrade them deliberately: Android Gradle Plugin 8.7.3, Gradle 8.11.1, JavaFX 21.0.6, JUnit 5.11.4, Mockito 5.14.2, OkHttp 4.12.0, Gson 2.11.0, compileSdk 35.

This foundation is not a production release. Security tests, backup drills, and the remaining product screens are still open. See `docs/IMPLEMENTATION_ROADMAP.md`.
