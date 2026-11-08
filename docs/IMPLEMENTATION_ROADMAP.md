# Implementation roadmap

The product was planned in twelve milestones. This repository now implements the v1 **core** on Android and desktop. Telegram and several secondary surfaces remain deferred.

| Milestone | Scope | State |
| --- | --- | --- |
| 1 | Inspection and architecture docs | Done |
| 2 | Gradle multi-module build | Done |
| 3 | Shared domain/services/ports | Done |
| 4 | Language + adoption applications SQL | Applied `20261107212305` |
| 5 | Auth completion | Email register/login/logout/reset/refresh/persistent session done. Telegram and Google PKCE still deferred |
| 6 | Android product screens | Discover, listing CRUD, photos, apply, applications, profile, location |
| 7–8 | Desktop admin + listing/adoption workflow | Desktop is an admin console. AdoptionService is wired on Android |
| 9 | Messaging and notifications | Read paths exist. Send still `schema.not_ready` |
| 10 | Admin panel | Desktop dashboard, users, listings, applications, reports via `admin_action` |
| 11 | Localization and outbox | AZ/EN/RU catalogs. Outbox retries adoption apply only |
| 12 | Packaging | Debug APK only. No jpackage / Play signing |

## Hosted SQL added after milestone 4

`20261107214742_admin_listing_moderation`: admin SELECT on listings and adoption images, plus `listing` on `private.admin_action`.

## Still open

- Telegram Edge Function and tables
- Google PKCE in Java
- Message send
- EncryptedSharedPreferences / Android Keystore
- Live two-user RLS tests
- Signed Android and jpackage
