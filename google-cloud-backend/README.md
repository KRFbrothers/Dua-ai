# Dua AI engine (Google Cloud Run)

Flask service that answers chats with Gemini on Vertex AI. The Android app sends its chat
messages here by default (`DUA_CHAT_BASE_URL`); everything else (sign-in, cloud history, files,
Imagine, voice, setup/config) still uses the Vercel `web/` backend.

Both chat endpoints require the owner's existing Supabase access token in `Authorization: Bearer <token>`.
The engine verifies it with the configured Supabase project's `GET /auth/v1/user`, then matches the
verified, non-anonymous account's confirmed email to `DUA_ALLOWED_EMAIL`. It does not trust decoded
JWT payloads, client-provided emails or `user_metadata` for authorization. Missing/invalid sessions
return 401, non-owner/unconfirmed accounts return 403, and missing setup or unavailable verification
returns 503 **before calling Vertex**. The health check remains public. Owner login cannot be disabled
with `DUA_REQUIRE_LOGIN=0`; `DUA_APP_TOKEN`, if retained, is an additional gate only.

## Endpoints
- `GET /` – health check.
- `POST /api/chat` – same format the app uses with Vercel:
  `{"model","messages":[{"role":"user|model","text","attachmentText?"}],"attachments":[{"name","mimeType","data(base64)"}],"memory","stream":true,"mode","context?"}`.
  With `"stream": true` it replies with Server-Sent Events (`event: text` / `data: {"type":"text","delta":"..."}`,
  then `done`, or `error`), exactly like `web/src/core/sse.mjs`. Without it: `{"text": "..."}`.
  The whole conversation is sent to Gemini, so it remembers earlier messages.
  Earlier user turns can also carry inline `attachments`; `attachmentText` and supplied `files` metadata text/transcripts are included as context. Storage paths are never fetched by Cloud Run.
  Streaming flushes an initial SSE heartbeat and sends another every 15 seconds while the Vertex worker is waiting. Cancelling the stream stops queueing results.
  Not supported here (a short notice is shown instead): other models (ChatGPT/Claude/Grok/Kimi – Gemini answers),
  web search, Run code, Build mode, server-side reply saving.
- `POST /chat` – old single message endpoint `{"message"}` -> `{"reply"}` (still works).

## Settings (environment variables only, no secrets in code)
| Name | Meaning |
| --- | --- |
| `GOOGLE_CLOUD_PROJECT` | your project id (required) |
| `VERTEX_LOCATION` | default `us-central1` (`global` is needed for preview models such as `gemini-3-flash-preview`) |
| `VERTEX_MODEL` | default `gemini-2.5-flash` |
| `SUPABASE_URL` | required; the same Supabase Auth project URL used by Vercel, HTTPS only |
| `SUPABASE_PUBLISHABLE_KEY` / `SUPABASE_ANON_KEY` | required; publishable key preferred, existing anon key supported. Used only by the engine to verify the account; no service-role key is needed |
| `DUA_ALLOWED_EMAIL` | required; the same owner email used by Vercel, compared case-insensitively against the Auth-verified account |
| `DUA_APP_TOKEN` | optional **additional** password. When set, requests must also send `X-Dua-App-Token`. Build the app with `-PduaAppToken=...` (or env `DUA_APP_TOKEN`). It never replaces the owner session; APK-embedded values are extractable |

## Redeploy from Cloud Shell

Configure `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` (or `SUPABASE_ANON_KEY`) and `DUA_ALLOWED_EMAIL`
on the **Cloud Run service**, using the same project/owner values as Vercel. Do not paste access tokens
or keys into chat, source files or screenshots. These settings are never compiled into Android.

For a redeploy with the values already available in your Cloud Shell environment:
```bash
cd ~/Dua-ai && git fetch origin && git pull origin main   # after the PR is merged
cd google-cloud-backend
: "${SUPABASE_URL:?Set the same Supabase URL used by Vercel}"
: "${DUA_ALLOWED_EMAIL:?Set the owner email}"
AUTH_KEY="${SUPABASE_PUBLISHABLE_KEY:-${SUPABASE_ANON_KEY:-}}"
: "${AUTH_KEY:?Set the Supabase publishable or anon key}"
gcloud run deploy dua-ai-engine --source . --region us-central1 \
  --update-env-vars "GOOGLE_CLOUD_PROJECT=global-healer-m6shk,VERTEX_LOCATION=us-central1,VERTEX_MODEL=gemini-2.5-flash,SUPABASE_URL=${SUPABASE_URL},SUPABASE_PUBLISHABLE_KEY=${AUTH_KEY},DUA_ALLOWED_EMAIL=${DUA_ALLOWED_EMAIL}" \
  --allow-unauthenticated --project global-healer-m6shk
```
`--allow-unauthenticated` allows requests to reach the service; application owner verification still
guards both chat routes. `--update-env-vars` preserves existing settings, including an optional
`DUA_APP_TOKEN`; if that gate is enabled, the APK must still be built with its matching value.

First-time setup (APIs + permissions + deploy): after setting the three required Auth values above,
run `PROJECT_ID=<your-project> ./deploy.sh`. Install an APK built from this change so the existing
Supabase session reaches Cloud Run. Older APKs omit that session and will correctly receive 401.
Deploying the engine alone does not update Android. There is no change to chat persistence, project
memory, capabilities, UI or the Vercel APIs in this authentication fix.

After deployment, confirm both `/api/chat` and `/chat` reject a request without `Authorization`,
reject a non-owner/expired session, and allow a fresh owner session. No rejected request should
invoke Vertex. Tests mock Auth/Vertex; successful tests do not certify the live service settings.

## Tests
```bash
pip install -r requirements.txt pytest && python -m pytest -q tests
```

For mocked unit tests only, Vertex credentials/SDK are unnecessary: install `Flask==3.0.3 pytest==8.3.5` and run `python -m pytest -q tests`. GitHub's Google Cloud engine check uses this isolated setup.

Separate-engine Android builds treat Dua/Gemini chat as configured independently of the base backend's provider keys. Chat file selection uses the existing bounded inline path (up to 3 files / 2 MB total; oversized photos are resized). Audio can also be sent inline. Use CSV/UTF-8 text for spreadsheets; XLSX/ODS and documents over the inline limit are rejected rather than uploaded as inaccessible storage references. Imagine retains the base-backend storage path.

Inline media bytes stay with their original turns for follow-ups/retry during the live process and are not persisted, exported or synced. Existing `attachmentText` is persisted with history (up to 32,000 characters per user turn). After restoring a chat, reselect media files if their bytes are needed again. Aggregate request/history size limits still apply; oversized inline history fails explicitly instead of silently dropping its media.
