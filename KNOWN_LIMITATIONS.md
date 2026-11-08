# Known limitations

## Release blockers (verify before a public launch)

1. Supabase Auth must list `az.miyav.native://auth` under Redirect URLs, or confirmation and recovery links will not open the Android app. Dashboard access is required; it was not changed here.
2. The built-in Supabase email sender is rate limited (a live recovery request returned `auth.rate_limited`). Configure custom SMTP before launch. Email delivery itself was not verified.
3. Google Play requires in-app account deletion plus a web deletion link for apps with account creation. Not implemented: it needs a data-retention decision for adoption history and a `service_role` Edge Function.
4. A privacy policy URL is required for the Play listing.
5. No upload keystore exists, so the release APK and AAB are unsigned. See `ANDROID_PRODUCTION_GUIDE.md` section 16.
6. Outbox retries adoption apply only. Creating or photographing a listing while offline is not queued.
7. Desktop admin was not re-verified against live data in the Android pass; no `jpackage` installer.

## Deferred by product scope

- Telegram linking and OTP (`UnavailableTelegramVerification` still throws `telegram.not_deployed`)
- Push notifications
- Google OAuth PKCE completion in Java (URL builder only)
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
