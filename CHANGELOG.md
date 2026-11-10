# Changelog

## 1.8.0 — 2026-11-10

### Added
- **MIYAV wallet** for walkers: balance from completed walks, withdrawal requests, 10% service fee, receipts
  with the bank reference. Android screen, desktop page and an admin *Withdrawals* queue.
- **Walker ratings**: owners rate a completed walk once (1–5 stars, optional review); averages show next to
  offers and on the profile. Walkers are notified.
- **Contract and bank details**: account holder, bank and IBAN (mod-97 check) with acceptance of the walker
  agreement.
- **İşlərim / My jobs** tab on Android (replaces *Applications*) and a matching desktop page: post walk
  requests, answer offers, complete, cancel and rate.
- Profile shows earnings, completed jobs and rating, plus WhatsApp support.
- The *new listing* button moved to a floating button at the bottom right of Discover.

### Security
- Owners can no longer restore a listing an administrator removed.
- Earnings from walks the walker marked done themselves are held for 48 hours unless the owner rates them.
- Only the current walker agreement version can be accepted; administrators cannot pay their own withdrawal.
- Walk history functions moved behind private `security definer` wrappers.
- Account screens with bank and wallet data close on sign-out.

### Performance
- The HTTP connection is opened at start-up (first request ~185 ms → ~31 ms on a warm connection).
- Profile, walk detail, applications and wallet load their data in parallel.
- Merged per-row admin RLS checks and added missing foreign key indexes.

### Build
- The Android APK is now a minified release build (9 MB → 2.7 MB).

## Earlier versions

1.1.0 – 1.7.0 built the Android and desktop clients, adoption marketplace, chat, walk board, live walks, places,
premium listings and the admin console. See the git history.
