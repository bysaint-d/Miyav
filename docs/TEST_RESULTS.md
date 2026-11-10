# Test results

Date: 8 November 2026 (Android production pass).

## Regression command

```bat
gradlew.bat test :desktop-app:compileJava :android-app:assembleDebug :android-app:assembleRelease :android-app:bundleRelease :android-app:lintDebug
```

Final status: **BUILD SUCCESSFUL**. Lint: 0 errors.

## JVM tests

| Suite | Tests | Result |
| --- | ---: | --- |
| `AuthServiceTest` | 8 | pass |
| `AdminServiceTest` | 2 | pass |
| `AdoptionServiceTest` | 3 | pass |
| `LocationServiceTest` | 3 | pass |
| `SupabaseContractTest` | 6 | pass |
| `EncryptedFileSessionStoreTest` | 1 | pass |
| `RoleGuardTest` | 1 | pass |
| `ResourceMessageCatalogTest` | 1 | pass |
| `PetPolymorphismTest` | 1 | pass |
| `CredentialValidatorTest` | 2 | pass |
| `AuthLinkTest` (Android) | 3 | pass |
| `OutboxPolicyTest` (Android) | 3 | pass |

Shared: 28 tests. Android unit: 6 tests (run for both debug and release variants). 0 failures.

New `AuthService` coverage: offline restore keeps the session, rejected refresh clears it, auth link for another address is refused, password change requires the current password.

## Live Supabase tests (executed with dedicated QA accounts)

`android-app/src/test/java/az/miyav/android/LiveSupabaseFlowTest.java`, run with `MIYAV_IT_*` variables against project `<project-ref>`:

| Test | Result |
| --- | --- |
| `sessionLifecycleWorksAgainstHostedAuth` | pass — login, refresh on restore, refresh token revoked after logout, wrong password rejected |
| `listingAdoptionAndRlsFlowWorksAgainstHostedDatabase` | pass — profile update, draft, photo upload + download, publish, discovery, RLS denies another user's write, application, duplicate blocked, own-listing insert blocked by RLS, non-owner accept refused, accept → listing adopted, archive |
| `passwordChangeAndRecoveryRequestWorkAgainstHostedAuth` | pass — wrong current password refused, change and restore. Recovery request reached Auth and returned `auth.rate_limited` (built-in email sender limit); email delivery not verified |

Without the variables these tests are reported as skipped, not passed.

## Emulator smoke test (manual, scripted with adb)

Pixel 9 Pro image, Android 16, minified release APK signed locally with the debug key only for installation. Verified: first launch in Azerbaijani, empty-form validation, wrong-password message, login, session restore after process kill, all four tabs, notifications (real trigger row), editor inline validation, draft creation, owner detail, archive confirmation, location permission dialog and denial, out-of-area handling, switch to English, logout, no session after restart. No `AndroidRuntime` crash logged. Screenshots: `docs/screenshots/`.

Not verified on the emulator: picking an image from the system photo picker, opening an email link (needs the Supabase Redirect URL), physical device.

## Builds

| Task | Result | Output |
| --- | --- | --- |
| `:android-app:assembleDebug` | SUCCESS (debug-signed) | `android-app/build/outputs/apk/debug/android-app-debug.apk` |
| `:android-app:assembleRelease` | SUCCESS, R8, unsigned (no upload key) | `android-app/build/outputs/apk/release/android-app-release-unsigned.apk` |
| `:android-app:bundleRelease` | SUCCESS, unsigned | `android-app/build/outputs/bundle/release/android-app-release.aab` |
| `:android-app:lintDebug` | 0 errors | `android-app/build/reports/lint-results-debug.html` |
| `:desktop-app:compileJava` | SUCCESS | — |

AAB split config checked with bundletool: `LANGUAGE` split disabled so in-app language switching works from Play installs.
