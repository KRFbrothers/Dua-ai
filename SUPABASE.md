# Supabase account and cloud sync setup

Supabase provides Google-backed accounts, device logout, the authoritative server snapshot for chats and memory, reviewed transfers and optional foreground cache refresh for the Android app. The app signs in only with native Google sign-in (see below); it has no email/password UI. Production uses the Vercel backend at `https://dua-cyan.vercel.app`; deployed settings must be verified in the hosting dashboard. Credentials remain in server configuration. Native Google sign-in, deletion and account protection are described below.

## 1. Create and configure the project

1. Reuse the existing Supabase project for this app. Create a project only when setting up a separate installation, retaining its database password securely.
2. In Authentication → Providers, enable Google (see [Native Google sign-in](#native-google-sign-in)). Dua signs in with Google only: the app has no email/password UI and the backend no longer serves email/password routes (sign-in, sign-up, password reset or recovery). **Turn the Email provider off** so nobody can create an email account on the project directly.
3. In Authentication → URL Configuration, review Site URL and the allowed redirect URLs. The removed web client used `https://YOUR-SITE/profile.html` (and the web Google OAuth flow used `/api/account/oauth/callback`); neither page exists any more, so remove those redirect entries. The Android app does not consume confirmation or redirect links (native Google sign-in needs none). Set Site URL to an HTTPS page you control (for example your backend origin); the confirmation itself succeeds regardless of where the browser lands afterwards.
4. Run `supabase/migrations/202610040001_dua_cloud.sql` in the SQL editor. It creates the snapshot table, owner-only read policy and authenticated revision-checked write function. Do not disable RLS or grant direct snapshot writes to app users.

## 1b. Stored files bucket (Phase 2: uploads, PDFs, spreadsheets, audio)

Run `supabase/migrations/202610080001_dua_files_storage.sql` once (SQL editor → New query → paste → Run, or `supabase db push` from a linked CLI project). It:

- creates a **private** bucket `dua-files` with a 50 MB per-file limit (52,428,800 bytes) and an allowed MIME list (images, PDF, XLSX/XLS/ODS/CSV, TXT/MD/JSON, common audio types);
- adds RLS policies on `storage.objects` so a signed-in user can only **select, insert and delete** objects whose first folder is their own `auth.uid()`. There is no update policy: files are immutable and uploads use `x-upsert: false`.

**Plan limits:** 50 MB is the Supabase Free plan's maximum file size. The bucket limit cannot exceed the project's global limit (Storage → Settings). On Pro and above you can raise both, then set `DUA_FILES_MAX_MB` on Vercel to match. Free projects also have a total storage quota; Settings → Data & privacy → Data controls in the app shows how much you use.

The backend talks to Storage with **your own access token** plus the public anon key, so RLS decides every access. No service-role key is used for files. The app uploads straight to Storage with a signed upload URL (valid 2 hours), which avoids Vercel's 4.5 MB request body limit.

Because only your account should use this personal app, turn off new sign-ups after creating your account (Authentication → Providers → Google, and Email if enabled → "Allow new users to sign up" off). The backend refuses Google sign-in, session refresh, cloud snapshots, account deletion, chat and file routes for anyone except `DUA_ALLOWED_EMAIL`, but someone else who signed up could still upload to their own folder directly through Storage with the public key.

Stored-file variables (Vercel):

| Variable | Default | Purpose |
| --- | --- | --- |
| `DUA_ALLOWED_EMAIL` | — (required) | Owner lock: only this Google account can sign in, refresh, use cloud snapshots, stored files and online AI, or delete the account. Must be your sign-in email. |
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

Per-file cleanup checks the owner's current server snapshot with the same owner token and retains referenced uploads and generated media. It returns `retained` when paths are kept, so old queued cache-deletion requests finish without deleting those files. If the snapshot cannot be read or validated, no Storage deletion is attempted. Apply the server snapshot migration as well as the Storage migration before using cleanup. Snapshot reads and Storage deletion are separate requests; this guard is not atomic with concurrent snapshot writes. The explicitly reviewed Delete all uploaded files action intentionally deletes files even when chats reference them.

**Deploy order:** apply the migration, set the variables, deploy the backend, then install the new APK. Old APKs keep working unchanged: they never send file references.

## 1c. Imagine (Phase 3: generated images and videos)

Run `supabase/migrations/202610090001_dua_imagine.sql` once, after the §1b migration (SQL editor → New query → paste → Run, or `supabase db push`). It is safe to run again. It:

- adds `video/mp4` to the `dua-files` bucket's allowed MIME list (nothing else about the bucket changes: still private, 50 MB per file, owner-folder policies);
- creates `public.dua_imagine_usage` (per account and UTC day: images and video seconds; RLS on, no client access);
- creates `dua_consume_imagine(feature, amount, daily_limit)` and `dua_read_imagine_usage()`, `security definer` functions that only act on `auth.uid()`. The backend calls them with your own token, so no service-role key is needed. They can only add usage, never reset it.

Generated files live next to your uploads under `<your user id>/`. Deleting or clearing cached chats keeps those files available for server restore. **Delete all uploaded files** permanently removes them; account deletion also attempts file cleanup. Discarded unsent uploads are cleaned up separately. Cloud snapshots keep references to generated images and videos (paths only, never bytes).

Without this migration the backend refuses Imagine requests with a setup message instead of generating without caps; video storage fails with a "run the Phase 3 migration" message.

**Deploy order:** run the migration, set `GROK_API_KEY` (and optionally `GEMINI_API_KEY`) plus any `DUA_IMAGINE_*` variables (PROVIDERS.md), deploy the backend, then install the new APK.

## 1d. Live voice minutes (Phase 4: realtime voice)

Run `supabase/migrations/202610100001_dua_voice.sql` once (SQL editor → New query → paste → Run, or `supabase db push`). It is safe to run again and does not change earlier tables. It:

- creates `public.dua_voice_usage` (seconds charged per account and UTC day) and `public.dua_voice_sessions` (one row per live session: reserved seconds, start, end, charged seconds); RLS on, no client access;
- creates `dua_start_voice_session(session_id, reserve_seconds, daily_limit_seconds, min_seconds)`, `dua_end_voice_session(session_id)` and `dua_read_voice_usage()`, `security definer` functions that only act on `auth.uid()`. The backend calls them with your own token (no service-role key). Starting a session reserves minutes under a row lock, so the daily cap holds even with parallel requests; ending charges the time used (never more than reserved); sessions left open are charged their full reservation once overdue.

Without this migration `/api/voice/session` refuses with a setup message and live voice never starts (hands-free mode still works).

**Deploy order:** run the migration, set the `DUA_VOICE_*` variables if you want non-defaults (PROVIDERS.md › Live voice), deploy the backend, then install the new APK.

## 2. Configure the shared backend

Add these environment variables to Vercel Production and redeploy:

- `SUPABASE_URL`: project URL, such as `https://PROJECT.supabase.co`.
- `SUPABASE_ANON_KEY`: the project's public publishable key or legacy anon key.

`SUPABASE_ANON_KEY` accepts only the project's public publishable/anon credential; service-role/secret credentials are rejected in that field. Optional deletion and durable request quotas use a separate server-only `SUPABASE_SERVICE_ROLE_KEY`. Keep existing model configuration. Supabase credentials are read by the backend and are not embedded in the Android app. Local development uses an ignored `web/.env`; run `npm start` from `web` with Node 24 or newer.

Android defaults to the same HTTPS Vercel backend through `DUA_API_BASE_URL`. Native source changes reach installed apps through a later release; routine compilation/JVM tests do not package an APK. The backend serves `/api/account/*` and `/api/cloud`. No separate Supabase key belongs in Android.

### Native Google sign-in

Google is the app's only sign-in method. Android uses native Google Sign-In (Credential Manager) and sends the ID token plus the raw nonce to `POST /api/account/google-token`, which exchanges them with Supabase. To enable it:

- In Supabase Authentication → Providers, enable Google with the OAuth web client ID.
- Build the app with that web client ID via the `duaGoogleWebClientId` Gradle property or the `GOOGLE_CLIENT_ID` environment variable.
- In the existing Google Cloud Android OAuth client, register package `com.dua.ai` and the permanent signing certificate's SHA-1 from `app/signing-certificate.properties`. Use the saved key for every update; see [SIGNING.md](SIGNING.md). A different build machine's debug certificate will not match this client.
- The **Sign in with Google** button appears on the first-launch welcome screen and in Settings → Profile. Without a web client ID the button reports that sign-in is not configured for the build. **Skip for now** on the welcome screen goes straight to local chat and is remembered on the device.

`SUPABASE_AUTH_REDIRECT_URL` was only used by the removed web OAuth redirect flow and is no longer read; it can be deleted from Vercel.

## 3. Verify after deployment

- On a fresh install, the welcome screen shows **Sign in with Google**. Sign in and check the app opens chat. Settings → Profile shows the account card (Google name and email) and a single **Log out** button. Chat greetings use the first word of the Google account name (`full_name`, then `name`; an older saved `display_name` is the fallback).
- Open Settings → Cloud sync. Review an upload before confirming it. Automatic sync starts only after device/account consent; sign-in alone does not enable it.
- Sign into the same account on another device, review Download, then confirm the merge. Check drafts and explicitly saved memory notes.
- Test two accounts: account B must not read account A's snapshot.
- Review an upload on both devices, upload from one, then confirm the older review on the other. The latter must report a conflict instead of overwriting silently.
- Stored files: sign in as the owner, attach a PDF over 2 MB, check the upload progress, send, then ask a follow-up about it. Delete the chat and check Storage → `dua-files` → your folder no longer has the file. Try `GET /api/files/usage` as a different account: it must return 403.
- Log out from Profile, confirm the dialog, and check the session clears while the server snapshot remains authoritative. The phone cache may remain until cleared; use the separate reviewed Clear server snapshot action to delete server data.

## Data and security behavior

Cloud storage uses whole snapshots with revision checks. Manual uploads replace the snapshot after review. Reviewed downloads restore each server chat at its original ID, including the open chat's messages, draft, model, summary and Build repository. Different phone versions are retained as separate `unsynced-` copies; phone-only chats remain cached. Repeating the same download does not create extra copies. Server memory notes replace cached notes, including when the server has no notes. The combined snapshot must fit the limits before history or memory is changed; finish an active reply or PR request before downloading.

Optional automatic sync runs only while the app is in the foreground and pauses for account changes, deletions, conflicts or failed storage. Consent is specific to the device/account. Unlike reviewed downloads, automatic sync uses its last-sync baseline to preserve offline edits and propagate memory changes: notes deleted on the phone since the last sync are intentionally removed from the server snapshot; new local notes are retained. Clearing cloud writes an empty revision so stale uploads still conflict. Background/realtime sync remains pending.

Limits: 100 chats per snapshot, 500 messages per chat, 8,000 draft characters, 1,048,576 message characters, 10 memory notes of 200 characters each, and 4 MiB of normalized app JSON. PostgreSQL's JSON representation includes spaces, so snapshots near the limit may need to be reduced. Raw file/audio bytes, local settings and provider keys are not synced; file references, bounded text attachment context, audio transcripts and other server-backed chat metadata are synced as metadata. Saved memory notes are included even if memory use is currently switched off; download preserves that opt-in switch. The review shows drafts and notes before upload.

The backend preserves and validates stable message IDs, bounded attachment context, file references/audio transcripts, summaries, Imagine state, Run code results and Build proposals/PR links on both snapshot write and read. Older version 1 snapshots without these optional fields remain readable. Raw file/audio bytes and unknown credential fields are not copied into the normalized snapshot.

The phone cache is disposable and is separate from the signed-in server snapshot. Imported chats first enter the phone cache and become server-backed only after an authenticated sync. Logout does not delete server data; clear the cache when appropriate on a shared device. Never treat a restored cache as authoritative before signing in and syncing the owner account.

For non-native callers the backend still issues Secure, HttpOnly, SameSite=Strict cookies scoped to `/api`. Android encrypts tokens with Android Keystore and excludes session preferences from cloud backup and device transfer. Logout attempts server revocation and always permits local native session removal; if revocation cannot be confirmed, an already issued JWT may remain usable until expiry.

Cloud storage is protected by user-token RLS and revision checks; it is not end-to-end encrypted. Account deletion and native Google sign-in are implemented with separate setup. Model-based automatic memory extraction, server push and background/realtime synchronization remain pending. Reviewed memory proposals and foreground cache refresh are available.

## Validation status

Mocked tests cover authentication, refresh, ownership, redacted errors, limits, conflicts and foreground synchronization. The SQL migration was exercised with owner isolation, write permissions and revision tests. Routine Android checks compile sources and run JVM tests without assembling an APK. Live account/deployment and physical Android Keystore/lifecycle checks remain separate.

## Hosting migration

The current Vercel deployment retains the existing Supabase project, schema and SMTP. Environment values do not transfer automatically between hosts.
