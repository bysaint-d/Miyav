# Known limitations

## Release blockers (verify before a public launch)

1. Supabase Auth must list `az.miyav.native://auth` under Redirect URLs, or confirmation and recovery links will not open the Android app. Dashboard access is required; it was not changed here.
2. The built-in Supabase email sender is rate limited (a live recovery request returned `auth.rate_limited`). Configure custom SMTP before launch. Email delivery itself was not verified.
3. In-app account deletion is implemented (Android Settings → Delete account, desktop account menu) through the `delete-account` Edge Function and `private.delete_account_data`. Google Play still needs a public web page or link where users can request deletion without the app; admin accounts are refused by design.
4. A privacy policy URL is required for the Play listing.
5. No upload keystore exists, so the release APK and AAB are unsigned. See `ANDROID_PRODUCTION_GUIDE.md` section 16.
6. Outbox retries adoption apply only. Creating or photographing a listing while offline is not queued.
7. Desktop admin was not re-verified against live data in the Android pass; no `jpackage` installer.

## Guest mode

- Google sign-in in the Java clients uses PKCE. Supabase Auth must list `az.miyav.native://auth` (Android) and `http://127.0.0.1:47821/callback` (desktop) under Redirect URLs, and the Google provider must be enabled; otherwise the sheet reports that Google is unavailable.
- "Telegram ilə təsdiqlə" is hidden in the Android and desktop sign-in screens until `TelegramVerification.available()` is true.
- Members can report and block each other from the publisher card (`report_user`, `user_blocks`). Blocks hide listings and chats on the client and the message guard refuses messages between blocked members.
- The Expo app translates the guest-mode surfaces (sign-in sheet, guest panel, legal pages, favourites sync, expiry notice) into az/en/ru; the rest of the Expo UI is still Azerbaijani only.
- The legal texts are product summaries written for the app; have them reviewed before publishing a store listing.

## Push, realtime chat and notification language

- Phone push: every insert into `public.notifications` calls the `dispatch-push` Edge Function through
  `pg_net` (migration `20261109212710_push_dispatch`). It sends to FCM tokens (native Android) and Expo
  tokens (Expo app) from `push_devices`. FCM needs two things that were not set up here: the four
  `firebase.*` values in `local.properties` (see `local.properties.example`) and the Supabase Edge Function
  secret `FCM_SERVICE_ACCOUNT` (the Firebase service account JSON). Until then Android keeps the
  15-minute background check, and the in-app list is unaffected.
- `dispatch-push` runs with `verify_jwt = false`: calling it only flushes notifications already queued for
  their own recipients, so it needs no caller identity.
- Chats use Supabase Realtime (`messages` is in the `supabase_realtime` publication) with a slower poll as a
  fallback. Realtime was verified by contract tests only, not against the live socket from this machine.
- Notification titles and bodies are written by `private.notify()` in the recipient's
  `profiles.preferred_language` (az, en, ru). Older rows keep their original Azerbaijani text. Admin
  broadcasts are sent exactly as typed.
- Desktop "nearby" placement: the desktop has no GPS, so new desktop listings are placed at the chosen
  city's centre.

## Vets tab ("Yaxınlıqda")

- The OpenStreetMap seed was cleaned (migration `20261109215624`): vet pharmacies, state vet offices,
  shelters and unnamed shop duplicates are hidden, not deleted. 5 vets and 35 shops remain visible, most
  without phone or hours. Admins add and verify real clinics in the console (Baytar və mağazalar).
- "Open now" understands the common OSM opening_hours forms; anything else shows no open/closed badge.
- Sponsored partner: Khazar University Hasvet animal hospital (migration `20261109222430`). Phone
  (+994 10 230 66 77) and hours (Mon–Sat 09:00–17:00) come from public listings that disagree slightly
  (2GIS shows 09:00–19:00 and another number); confirm with the clinic and correct them in the console.

## Walk history

- Routes are recorded from migration `20261109222332` on; earlier walks show without a route.
- The route is private to the walker (RLS) and can be deleted from history; live sharing with others is
  unchanged (latest rounded point only, removed on pause/end).
- Duration counts from start to end, including pauses.

## Deferred by product scope

- Telegram linking and OTP (`UnavailableTelegramVerification` still throws `telegram.not_deployed`)
- Care marketplace (sitters, bookings) — Expo already owns that surface
- Google Maps (nearby uses on-device city snapping; no Maps key needed)
- Supported cities are still Bakı, Sumqayıt and Abşeron (database checks, filters and validators)

## Platform notes

- Desktop GPS is unavailable; city is a stored profile/listing field.
- Admin listing restore to `active` requires existing photos (database check).
- Adoption images are private; the client downloads with the user access token.
- Graphify still does not index XML/FXML/SQL well; Gradle remains the module DAG authority.

## Security advisor

`private.admin_audit` has RLS enabled and no policies. That deny-all is intentional. The security advisor reports it as INFO.
