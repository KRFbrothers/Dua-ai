# Supabase account and manual cloud sync setup

This integration adds email sign-up/sign-in, display-name editing, device logout, and reviewed chat/memory snapshot uploads and downloads on web and Android. No Supabase project or credentials are included. OpenAI setup and the other integration groups remain separate work.

## 1. Create and configure the project

1. Create a Supabase project and retain its database password securely.
2. In Authentication → Providers, enable Email. Keep email confirmation enabled and require passwords of at least eight characters. Configure production SMTP before relying on sign-up email delivery; Supabase's default mail service has restrictions.
3. In Authentication → URL Configuration, set Site URL to your deployed app's `https://YOUR-SITE/profile.html`. Add that URL to the allowed redirect URLs. The current flow asks users to confirm by email and then sign in manually; it does not ingest tokens from confirmation links.
4. Run `supabase/migrations/202610040001_dua_cloud.sql` in the SQL editor. It creates the snapshot table, owner-only read policy and authenticated revision-checked write function. Do not disable RLS or grant direct snapshot writes to app users.

## 2. Configure the shared backend

Add these environment variables in Netlify and redeploy:

- `SUPABASE_URL`: project URL, such as `https://PROJECT.supabase.co`.
- `SUPABASE_ANON_KEY`: the project's public publishable key or legacy anon key.

Never use a service-role key or `sb_secret_` key. The backend deliberately rejects them. Keep existing Gemini configuration. Keys are read only by the backend; they are not embedded in HTML or the APK. Local development uses the same variables in an ignored `web/.env`; run `npm start` from `web` with Node 24 or newer.

Android uses the existing HTTPS `DUA_API_BASE_URL` pointing to this same backend. Pull the merged branch, sync Gradle and rebuild. The production endpoint must serve `/api/account/*` and `/api/cloud`, not just static HTML. No separate Supabase key is needed in Android.

## 3. Verify after deployment

- Open Settings → Profile. Sign up, confirm the email, sign in and edit the display name.
- Open Cloud sync from Profile. Review an upload before confirming it; sign-in never uploads automatically.
- Sign into the same account on another device, review Download, then confirm the merge. Check drafts and explicitly saved memory notes.
- Test two accounts: account B must not read account A's snapshot.
- Review an upload on both devices, upload from one, then confirm the older review on the other. The latter must report a conflict instead of overwriting silently.
- Log out and confirm the session clears while local chats and memories remain. Clearing local data does not clear cloud data; use the separate reviewed Clear cloud snapshot action.

## Data and security behavior

Sync is manual, whole-snapshot storage, not automatic or real-time sync. An upload replaces the cloud snapshot after review. Download merges into current local data: matching content/timestamps are skipped; conflicting chat IDs become separate copies, preserving local drafts. Local deletions do not propagate automatically. Clearing cloud writes an empty revision so stale uploads still conflict.

Limits: 100 chats per snapshot, 500 messages per chat, 8,000 draft characters, 1,048,576 message characters, 10 memory notes of 200 characters each, and 4 MiB of normalized app JSON. PostgreSQL's JSON representation includes spaces, so snapshots near the limit may need to be reduced. File/audio bytes, local settings and provider keys are not synced. Saved memory notes are included even if memory use is currently switched off; download preserves that opt-in switch. The review shows drafts and notes before upload.

Local data belongs to the device/browser, not a separate per-account local namespace. Logout retains it. On shared devices, inspect the review before uploading local data to a different account and clear local data when appropriate.

Browser sessions use Secure, HttpOnly, SameSite=Strict cookies scoped to `/api`; tokens are not put in localStorage. Android encrypts tokens with Android Keystore and excludes session preferences from cloud backup and device transfer. Passwords are not persisted. Logout attempts server revocation and always permits local native session removal; if revocation cannot be confirmed, an already issued JWT may remain usable until expiry.

Cloud storage is protected by user-token RLS and revision checks; it is not end-to-end encrypted. Account deletion, password reset, OAuth, automatic memory extraction, push notifications, and automatic synchronization are not implemented by this change.

## Validation status

Mocked API tests cover authentication, refresh, ownership, redacted errors, limits and conflicts. The SQL migration was exercised in a temporary PostgreSQL-compatible PGlite instance with owner isolation, write permissions and revision tests. Android unit tests and debug build pass. Actual Supabase email delivery, deployed Netlify cookies and Android Keystore on a physical device require the project and deployment above.

## Hosting migration

The same Supabase project, schema and SMTP can be retained on Vercel. Follow [VERCEL.md](VERCEL.md) to configure the new backend, change confirmation URLs and rebuild Android. Values saved in Netlify do not transfer automatically.
