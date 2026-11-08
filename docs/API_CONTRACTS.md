# API contracts

Java clients call the hosted Supabase Auth and REST endpoints with the publishable key and, after login, the user access token. They do not call a service-role API.

Error codes returned to the UI are stable strings such as `auth.failed` and `config.missing`. Server response bodies are not shown.

## Auth

| Action | Contract | Client behavior |
| --- | --- | --- |
| Password login | `POST /auth/v1/token?grant_type=password` with JSON `email`, `password` | 400, 401, and 422 become `auth.failed`. A 2xx body without an access token becomes `auth.confirmation_required` |
| Registration | `POST /auth/v1/signup` with `email`, `password`, and `data.full_name` | Same session rules. Hosted email confirmation may mean no session is returned |
| Logout | `POST /auth/v1/logout` with the user bearer token | 2xx and 401 clear the local encrypted session |
| Password reset | `POST /auth/v1/recover` with `email` | Non-2xx becomes `auth.unavailable`. Completing the new password is the hosted email link |
| Refresh | `POST /auth/v1/token?grant_type=refresh_token` | Used by `restore` / `requireSession` when the access token is within 60 seconds of expiry. A rejected refresh clears the session (`auth.session_expired`); a network failure keeps it |
| Redirects | `?redirect_to=az.miyav.native://auth` on signup and recover | Must be listed in Supabase Auth Redirect URLs. The app parses `#access_token…&type=recovery` |
| Link completion | `GET /auth/v1/user` with the link's access token | Accepted only when the email matches the address requested on this device |
| Password change | `PUT /auth/v1/user` with `password` | Current password is re-checked with a password grant first. `same_password` and `weak_password` map to validation codes |
| Auth error codes | GoTrue `error_code` | `email_not_confirmed`, `user_already_exists`, `weak_password`, `over_email_send_rate_limit` map to `auth.confirmation_required`, `auth.user_exists`, `validation.password`, `auth.rate_limited` |
| Google | `GET /auth/v1/authorize?provider=google&redirect_to=` | `googleAuthorizeUri` builds this URL. The Java clients do not finish PKCE. The Expo client does, and hosted `external.google` is true |
| Current user profile | `GET /rest/v1/profiles?id=eq.{userId}&select=id,full_name,city,district,phone,role,suspended,preferred_language` | Missing row is `auth.profile_unavailable`. `suspended=true` signs the session out |
| Profile update | `PATCH /rest/v1/profiles?id=eq.{userId}` | Body is `full_name`, `city`, `district`, `phone`, and `preferred_language`. `role` and `suspended` are not sent |

Headers on every call: `apikey` is the publishable key, `Authorization` is `Bearer` plus either that key or the user access token, `Accept` and `Content-Type` are JSON. Profile update also sends `Prefer: return=representation`.

Password policy checked before the request: length 10 through 72, at least one letter and one digit, no whitespace. That matches the Expo Auth config minimum of 10.

## Listings

`GET /rest/v1/adoption_listings` selects `id,owner_id,name,species,breed,age_months,gender,city,description,status,photos`.

Published discovery adds `status=eq.active`, `order=created_at.desc`, `limit`, and `offset`. Optional filters are `city`, `species`, and an `or` filter on `name.ilike` and `breed.ilike`. Search text is reduced to letters, digits, spaces, and hyphen before encoding.

Status mapping is `draft`, `active`, `adopted`, `deleted` to `DRAFT`, `PUBLISHED`, `ADOPTED`, `ARCHIVED`. Unknown status fails with `schema.not_ready` rather than being shown as published.

Writes: `POST /rest/v1/adoption_listings` without `owner_id` (default `auth.uid()`), then `POST /storage/v1/object/adoptions/{owner}/{listing}/{uuid}.jpg`, then `PATCH` photos/status. Publish requires at least one photo. Archive sets `status=deleted`. Admin hide/restore uses `POST /rest/v1/rpc/admin_action` with `p_action=listing` and `p_value` `deleted` or `active`.

## Conversations and notifications

`GET /rest/v1/conversations?select=id,owner_id,sitter_id,last_message_at&order=last_message_at.desc`

The `sitter_id` column is the other participant. RLS already limits rows to a participant. Message history and send are not implemented; those methods throw `schema.not_ready` even though `messages` exists, because the send path needs the same validation as the Expo client and is scheduled for milestone 9.

`GET /rest/v1/notifications?select=id,user_id,title,body,created_at,read_at&order=created_at.desc`

`PATCH` sets `read_at` only. The live table has no notification type, so rows are mapped as `SYSTEM`.

## Adoption applications

`POST /rest/v1/adoption_applications` with `Prefer: return=representation`. The JSON body contains `listing_id` and `message`. `applicant_id` defaults to `auth.uid()` and `status` defaults to `pending`.

Status changes are `POST /rest/v1/rpc/accept_adoption_application`, `reject_adoption_application`, and `cancel_adoption_application` with `{ "p_application": "<uuid>" }`. The public wrappers are security invokers. The locked work is in `private`.

Error mapping: HTTP 401 is `auth.failed`. A function message of `FORBIDDEN` is `access.owner`. Unique violation `one_pending` is `adoption.duplicate`. `CONFLICT` or another `23505` is `adoption.already_accepted`. `NOT_FOUND` is `adoption.not_pending`. Other failures are `network.unavailable`. The server body is not shown.

Reads use `GET /rest/v1/adoption_applications` filtered by `listing_id` and, for the accepted row, `status=eq.accepted`.

## Not available

| Contract | Result today |
| --- | --- |
| Telegram link and OTP | `telegram.not_deployed`. No function URL is called |
| Account deletion | No endpoint is called |
| Admin audit read | Not exposed to the publishable key. The admin button reports `schema.not_ready` after the role gate |
| `dispatch-push` | Source exists in the Expo repo. It is not deployed |

## Telegram function to add later

The function name should be explicit, for example `telegram-link`. It must run with the bot token in Edge Function secrets, verify the Supabase user JWT, and never return the token. Request and response shapes belong in that milestone's review. This document does not invent a deployed URL.

## Phone OTP

The Expo UI can call `signInWithOtp` for a phone number. Hosted Auth currently reports `external.phone=false`, so that path is not a working contract for the Java clients.
