# Implementation log

Date: 8 November 2026.

## Goal

Finish MIYAV v1 core (auth, profiles, pets, discovery, adoption, location, desktop admin, outbox) without Telegram and without destroying live schema.

## Sequence

1. Read Graphify, `ARCHITECTURE_REVIEW.md`, roadmap, live tables, grants, RLS, and `private.admin_action`.
2. Extended domain ports: auth refresh, pet save/owned/admin, adoption applicant/admin lists, media, admin RPC.
3. Added AES-GCM `EncryptedFileSessionStore` and `AuthService.restore` / refresh.
4. Implemented listing writes, JPEG storage, `AdminService`.
5. Wired Android shell (register/reset, tabs, listing editor, apply, profile, coarse location, outbox).
6. Wired desktop admin console.
7. Applied hosted migration `admin_listing_moderation`.
8. Compiled Android + desktop, ran 24 JVM tests, refreshed Graphify.

## Modules changed

- `shared/common` — locale catalogs
- `shared/domain` — `AuthSession`, `Pet` copies, new repository ports, `AdminStats`
- `shared/security` — encrypted file session store
- `shared/application` — Auth, Pet, Adoption, Admin, Location services
- `shared/data` — adapters, binary HTTP, `MiyavBackend`
- `android-app` — screens, Room v2, outbox worker
- `desktop-app` — login/register/reset and admin home
- `supabase/migrations/20261107214742_admin_listing_moderation.sql`

## Graphify after the change

- Nodes 1069, edges 3317, communities 48
- Top hubs: `AuthSession` (189), `Pet` (79), `AuthService` (33), `MiyavApplication` (32), `HomeController` (29), `AdminService` (27)

Layering is unchanged: `common` ← `domain` ← (`application`, `data`, `security`) ← apps.

## Decisions

- Desktop is an admin console. Non-admins can sign in (shared accounts) and are denied the panel. RLS/RPC still block their admin calls.
- Photo uploads are JPEG under 2 MB to match the `adoptions` bucket and `.jpg` path check.
- Location uses coarse permission and city snapping; coordinates are not written to `adoption_listings`.
- Outbox covers adoption apply only. Photo listing writes stay online because storage objects must exist before `photos[]` is valid.
- Android Keystore was not added (offline Gradle cache had no `security-crypto`). AES-GCM in app-private storage is the v1 store.
