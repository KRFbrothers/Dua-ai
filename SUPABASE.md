# Supabase account and cloud sync setup

Supabase provides email accounts, profile/device logout, reviewed chat/memory snapshot transfers and opt-in foreground automatic sync on web and Android. Production uses the existing project with the Vercel backend at `https://dua-cyan.vercel.app`. Credentials remain in server configuration. Optional recovery, web Google login, deletion and account protection are documented in [ACCOUNT-SETUP.md](ACCOUNT-SETUP.md).

## 1. Create and configure the project

1. Reuse the existing Supabase project for this app. Create a project only when setting up a separate installation, retaining its database password securely.
2. In Authentication → Providers, enable Email. Keep email confirmation enabled and require passwords of at least eight characters. Configure production SMTP before relying on sign-up email delivery; Supabase's default mail service has restrictions.
3. In Authentication → URL Configuration, set Site URL to your deployed app's `https://YOUR-SITE/profile.html`. Add that URL to the allowed redirect URLs. The current flow asks users to confirm by email and then sign in manually; it does not ingest tokens from confirmation links.
4. Run `supabase/migrations/202610040001_dua_cloud.sql` in the SQL editor. It creates the snapshot table, owner-only read policy and authenticated revision-checked write function. Do not disable RLS or grant direct snapshot writes to app users.

## 2. Configure the shared backend

Add these environment variables to Vercel Production and redeploy:

- `SUPABASE_URL`: project URL, such as `https://PROJECT.supabase.co`.
- `SUPABASE_ANON_KEY`: the project's public publishable key or legacy anon key.

`SUPABASE_ANON_KEY` accepts only the project's public publishable/anon credential; service-role/secret credentials are rejected in that field. Optional deletion and durable request quotas use a separate server-only `SUPABASE_SERVICE_ROLE_KEY`, described in [ACCOUNT-SETUP.md](ACCOUNT-SETUP.md). Keep existing model configuration. Supabase credentials are read by the backend and are not embedded in HTML or Android. Local development uses an ignored `web/.env`; run `npm start` from `web` with Node 24 or newer.

Android defaults to the same HTTPS Vercel backend through `DUA_API_BASE_URL`. Native source changes reach installed apps through a later release; routine compilation/JVM tests do not package an APK. The backend serves `/api/account/*` and `/api/cloud`. No separate Supabase key belongs in Android.

## 3. Verify after deployment

- Open Settings → Profile. Sign up, confirm the email, sign in and edit the display name.
- Open Cloud sync from Profile. Review an upload before confirming it. Automatic sync starts only after device/account consent; sign-in alone does not enable it.
- Sign into the same account on another device, review Download, then confirm the merge. Check drafts and explicitly saved memory notes.
- Test two accounts: account B must not read account A's snapshot.
- Review an upload on both devices, upload from one, then confirm the older review on the other. The latter must report a conflict instead of overwriting silently.
- Log out and confirm the session clears while local chats and memories remain. Clearing local data does not clear cloud data; use the separate reviewed Clear cloud snapshot action.

## Data and security behavior

Cloud storage uses whole snapshots with revision checks. Manual uploads replace the snapshot after review; downloads merge into local data, skipping identical records and retaining conflicting chats as separate copies. Optional automatic sync runs only while the page/app is in the foreground and pauses for account changes, deletions, conflicts or failed storage. Consent is specific to the device/account. Local deletions do not propagate automatically. Clearing cloud writes an empty revision so stale uploads still conflict. Background/realtime sync remains pending. See [AUTOMATION-SETUP.md](AUTOMATION-SETUP.md).

Limits: 100 chats per snapshot, 500 messages per chat, 8,000 draft characters, 1,048,576 message characters, 10 memory notes of 200 characters each, and 4 MiB of normalized app JSON. PostgreSQL's JSON representation includes spaces, so snapshots near the limit may need to be reduced. File/audio bytes, local settings and provider keys are not synced. Saved memory notes are included even if memory use is currently switched off; download preserves that opt-in switch. The review shows drafts and notes before upload.

Local data belongs to the device/browser, not a separate per-account local namespace. Logout retains it. On shared devices, inspect the review before uploading local data to a different account and clear local data when appropriate.

Browser sessions use Secure, HttpOnly, SameSite=Strict cookies scoped to `/api`; tokens are not put in localStorage. Android encrypts tokens with Android Keystore and excludes session preferences from cloud backup and device transfer. Passwords are not persisted. Logout attempts server revocation and always permits local native session removal; if revocation cannot be confirmed, an already issued JWT may remain usable until expiry.

Cloud storage is protected by user-token RLS and revision checks; it is not end-to-end encrypted. Recovery/password updates, optional account deletion and web Google OAuth are implemented with separate setup. Native Google handoff, model-based automatic memory extraction, server push and background/realtime synchronization remain pending. Reviewed local memory proposals and foreground sync are available.

## Validation status

Mocked tests cover authentication, refresh, ownership, redacted errors, limits, conflicts and foreground synchronization. The SQL migration was exercised with owner isolation, write permissions and revision tests. Routine Android checks compile sources and run JVM tests without assembling an APK. Live account/deployment and physical Android Keystore/lifecycle checks remain separate.

## Hosting migration

The current Vercel deployment retains the existing Supabase project, schema and SMTP. Follow [VERCEL.md](VERCEL.md) when setting up another deployment or transferring browser data from the earlier Netlify origin. Environment values do not transfer automatically between hosts.
