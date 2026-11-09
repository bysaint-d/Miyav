# Supabase

The live project is `<project-ref>` in region `ap-northeast-1` (Postgres 17). Its migration history already contains:

- `20261027175631_miyav_core`
- `20261027181207_push_outbox`
- `20261027193000_restrict_avatar_listing`
- `20261028083445_adoption_marketplace`
- `20261028085008_adoption_explicit_privileges`
- `20261107212305_adoption_applications_and_language`
- `20261107214742_admin_listing_moderation`

The earlier SQL files remain in the Expo project at `<expo-app-path>\supabase`. They are already applied. Do not recreate or reapply them. The milestone 4 file is stored in both `supabase/migrations` directories and is already applied on the hosted project. A later `supabase db push` must use version `20261107212305`, not an older local filename.

Deployed Edge Functions: `delete-account` and `dispatch-push` (source in `supabase/functions`). `dispatch-push`
replaces the Expo project's undeployed source of the same name and also serves Expo tokens; FCM delivery
needs the `FCM_SERVICE_ACCOUNT` secret. A configured `TELEGRAM_BOT_TOKEN` secret does not mean a Telegram function is deployed; none is.

Telegram tables stay out of this migration. They belong with the Edge Function review.
