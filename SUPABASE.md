# Supabase account and cloud sync setup

Supabase provides email accounts, profile/device logout, reviewed chat/memory snapshot transfers and opt-in foreground automatic sync for the Android app. Production uses the Vercel backend at `https://dua-cyan.vercel.app`; deployed settings must be verified in the hosting dashboard. Credentials remain in server configuration. Optional recovery, native Google sign-in, deletion and account protection are described below.

## 1. Create and configure the project

1. Reuse the existing Supabase project for this app. Create a project only when setting up a separate installation, retaining its database password securely.
2. In Authentication → Providers, enable Email. Keep email confirmation enabled and require passwords of at least eight characters. Configure production SMTP before relying on sign-up email delivery; Supabase's default mail service has restrictions.
3. In Authentication → URL Configuration, review Site URL and the allowed redirect URLs. The removed web client used `https://YOUR-SITE/profile.html` (and the web Google OAuth flow used `/api/account/oauth/callback`); neither page exists any more, so remove those redirect entries. The Android app does not consume confirmation or redirect links: users confirm by email and then sign in manually in the app, and password recovery uses the emailed numeric code. Set Site URL to an HTTPS page you control (for example your backend origin); the confirmation itself succeeds regardless of where the browser lands afterwards.
4. Run `supabase/migrations/202610040001_dua_cloud.sql` in the SQL editor. It creates the snapshot table, owner-only read policy and authenticated revision-checked write function. Do not disable RLS or grant direct snapshot writes to app users.

## 1b. Stored files bucket (Phase 2: uploads, PDFs, spreadsheets, audio)

Run `supabase/migrations/202610080001_dua_files_storage.sql` once (SQL editor → New query → paste → Run, or `supabase db push` from a linked CLI project). It:

- creates a **private** bucket `dua-files` with a 50 MB per-file limit (52,428,800 bytes) and an allowed MIME list (images, PDF, XLSX/XLS/ODS/CSV, TXT/MD/JSON, common audio types);
- adds RLS policies on `storage.objects` so a signed-in user can only **select, insert and delete** objects whose first folder is their own `auth.uid()`. There is no update policy: files are immutable and uploads use `x-upsert: false`.

**Plan limits:** 50 MB is the Supabase Free plan's maximum file size. The bucket limit cannot exceed the project's global limit (Storage → Settings). On Pro and above you can raise both, then set `DUA_FILES_MAX_MB` on Vercel to match. Free projects also have a total storage quota; Settings → Data & privacy → Data controls in the app shows how much you use.

The backend talks to Storage with **your own access token** plus the public anon key, so RLS decides every access. No service-role key is used for files. The app uploads straight to Storage with a signed upload URL (valid 2 hours), which avoids Vercel's 4.5 MB request body limit.

Because only your account should use this personal app, turn off new sign-ups after creating your account (Authentication → Providers → Email → "Allow new users to sign up" off, and the same for Google). The backend already refuses file routes for anyone except `DUA_ALLOWED_EMAIL`, but someone else who signed up could still upload to their own folder directly through Storage with the public key.

Stored-file variables (Vercel):

| Variable | Default | Purpose |
| --- | --- | --- |
| `DUA_ALLOWED_EMAIL` | — (required) | Stored files are owner-only. Must be your sign-in email. |
| `DUA_FILES` | on | Set `0` to turn stored files off (the app falls back to small inline files). |
| `DUA_FILES_BUCKET` | `dua-files` | Bucket name, if you renamed it in the migration. |
| `DUA_FILES_MAX_MB` | `50` | Per-file limit the backend enforces. Keep it ≤ the bucket limit. |
| `DUA_HISTORY_FILES` | `4` | How many earlier files are re-attached in full on follow-ups. Older files become a short note. Audio transcripts are always included. |
| `DUA_FILE_TEXT_MAX_CHARS` | `150000` | Cap for text extracted from PDFs/text files. |
| `DUA_SHEET_MAX_ROWS` / `DUA_SHEET_MAX_COLS` / `DUA_SHEET_MAX_CHARS` | `300` / `40` / `120000` | Spreadsheet → CSV caps (per sheet rows/columns, total characters). Truncation is noted in the text sent to the model. |
| `DUA_TRANSCRIBE_PROVIDER` | `auto` | `auto` (Grok → Gemini → OpenAI, whichever keys exist), or `grok`, `gemini`, `openai`. |
| `GROK_TRANSCRIBE_MODEL` | `grok-voice-transcribe-2.0` | xAI speech-to-text model. |
| `OPENAI_TRANSCRIBE_MODEL` | `gpt-transcribe` | OpenAI transcription model (≤ 25 MB; mp3, mp4, mpeg, mpga, m4a, wav, webm). |
| `GROK_FILE_INPUTS` | on | Set `0` to send Grok extracted PDF text instead of an xAI file input. |

API routes: `POST /api/files/sign`, `POST /api/files/url`, `POST /api/files/delete`, `POST /api/files/delete-all`, `GET /api/files/usage`. All require the owner's Supabase sign-in. `/api/config.files` reports `{enabled, maxBytes, types, requiresSignIn}`; older apps ignore it.

**Deploy order:** apply the migration, set the variables, deploy the backend, then install the new APK. Old APKs keep working unchanged: they never send file references.

## 2. Configure the shared backend

Add these environment variables to Vercel Production and redeploy:

- `SUPABASE_URL`: project URL, such as `https://PROJECT.supabase.co`.
- `SUPABASE_ANON_KEY`: the project's public publishable key or legacy anon key.

`SUPABASE_ANON_KEY` accepts only the project's public publishable/anon credential; service-role/secret credentials are rejected in that field. Optional deletion and durable request quotas use a separate server-only `SUPABASE_SERVICE_ROLE_KEY`. Keep existing model configuration. Supabase credentials are read by the backend and are not embedded in the Android app. Local development uses an ignored `web/.env`; run `npm start` from `web` with Node 24 or newer.

Android defaults to the same HTTPS Vercel backend through `DUA_API_BASE_URL`. Native source changes reach installed apps through a later release; routine compilation/JVM tests do not package an APK. The backend serves `/api/account/*` and `/api/cloud`. No separate Supabase key belongs in Android.

### Optional native Google sign-in

Android uses native Google Sign-In (Credential Manager) and sends the ID token to `POST /api/account/google-token`, which exchanges it with Supabase. To enable it:

- In Supabase Authentication → Providers, enable Google with the OAuth web client ID.
- Build the app with that web client ID via the `duaGoogleWebClientId` Gradle property or the `GOOGLE_CLIENT_ID` environment variable.
- The **Sign in with Google** button is shown when the app was built with a web client ID and is enabled once the backend reports Supabase as configured.

`SUPABASE_AUTH_REDIRECT_URL` was only used by the removed web OAuth redirect flow and is no longer read; it can be deleted from Vercel.

## 3. Verify after deployment

- Open Settings → Profile. Sign up, confirm the email, sign in and edit the display name.
- Open Cloud sync from Profile. Review an upload before confirming it. Automatic sync starts only after device/account consent; sign-in alone does not enable it.
- Sign into the same account on another device, review Download, then confirm the merge. Check drafts and explicitly saved memory notes.
- Test two accounts: account B must not read account A's snapshot.
- Review an upload on both devices, upload from one, then confirm the older review on the other. The latter must report a conflict instead of overwriting silently.
- Stored files: sign in as the owner, attach a PDF over 2 MB, check the upload progress, send, then ask a follow-up about it. Delete the chat and check Storage → `dua-files` → your folder no longer has the file. Try `GET /api/files/usage` as a different account: it must return 403.
- Log out and confirm the session clears while local chats and memories remain. Clearing local data does not clear cloud data; use the separate reviewed Clear cloud snapshot action.

## Data and security behavior

Cloud storage uses whole snapshots with revision checks. Manual uploads replace the snapshot after review; downloads merge into local data, skipping identical records and retaining conflicting chats as separate copies. Optional automatic sync runs only while the app is in the foreground and pauses for account changes, deletions, conflicts or failed storage. Consent is specific to the device/account. Local deletions do not propagate automatically. Clearing cloud writes an empty revision so stale uploads still conflict. Background/realtime sync remains pending.

Limits: 100 chats per snapshot, 500 messages per chat, 8,000 draft characters, 1,048,576 message characters, 10 memory notes of 200 characters each, and 4 MiB of normalized app JSON. PostgreSQL's JSON representation includes spaces, so snapshots near the limit may need to be reduced. File/audio bytes, stored-file references, audio transcripts, local settings and provider keys are not synced. Saved memory notes are included even if memory use is currently switched off; download preserves that opt-in switch. The review shows drafts and notes before upload.

Local data belongs to the device, not a separate per-account local namespace. Logout retains it. On shared devices, inspect the review before uploading local data to a different account and clear local data when appropriate.

For non-native callers the backend still issues Secure, HttpOnly, SameSite=Strict cookies scoped to `/api`. Android encrypts tokens with Android Keystore and excludes session preferences from cloud backup and device transfer. Passwords are not persisted. Logout attempts server revocation and always permits local native session removal; if revocation cannot be confirmed, an already issued JWT may remain usable until expiry.

Cloud storage is protected by user-token RLS and revision checks; it is not end-to-end encrypted. Recovery/password updates, optional account deletion and native Google sign-in are implemented with separate setup. Model-based automatic memory extraction, server push and background/realtime synchronization remain pending. Reviewed local memory proposals and foreground sync are available.

## Validation status

Mocked tests cover authentication, refresh, ownership, redacted errors, limits, conflicts and foreground synchronization. The SQL migration was exercised with owner isolation, write permissions and revision tests. Routine Android checks compile sources and run JVM tests without assembling an APK. Live account/deployment and physical Android Keystore/lifecycle checks remain separate.

## Hosting migration

The current Vercel deployment retains the existing Supabase project, schema and SMTP. Environment values do not transfer automatically between hosts.
