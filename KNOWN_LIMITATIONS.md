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

## Deferred by product scope

- Telegram linking and OTP (`UnavailableTelegramVerification` still throws `telegram.not_deployed`)
- Push notifications
- In-app messaging send (`schema.not_ready`)
- Care marketplace (sitters, bookings) — Expo already owns that surface
- Google Maps (nearby uses on-device city snapping; no Maps key needed)
- System push notifications (in-app notification list only, so no `POST_NOTIFICATIONS` permission)

## Platform notes

- Desktop GPS is unavailable; city is a stored profile/listing field.
- Admin listing restore to `active` requires existing photos (database check).
- Adoption images are private; the client downloads with the user access token.
- Graphify still does not index XML/FXML/SQL well; Gradle remains the module DAG authority.

## Security advisor

`private.admin_audit` has RLS enabled and no policies. That deny-all is intentional. The security advisor reports it as INFO.
