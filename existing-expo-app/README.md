# Existing Expo application

The working Expo / React Native MIYAV application is preserved at:

`<expo-app-path>`

It was not moved into this repository. This folder records that decision. Copying it here would duplicate `node_modules`, local environment files, and a second Supabase migration history. Do not delete or rename that project while the native Java clients are being built.

The hosted Supabase project used by that app is the same backend these Java clients are designed to share. Migration files that are already applied live in the Expo project's `supabase/migrations` directory. Do not copy them into this repo and push them again.
