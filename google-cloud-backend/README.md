# Dua AI engine (Google Cloud Run)

Flask service that answers chats with Gemini on Vertex AI. The Android app sends its chat
messages here by default (`DUA_CHAT_BASE_URL`); everything else (sign-in, cloud history, files,
Imagine, voice, setup/config) still uses the Vercel `web/` backend.

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
| `DUA_APP_TOKEN` | optional password. When set, requests must send header `X-Dua-App-Token` with the same value. Build the app with `-PduaAppToken=...` (or env `DUA_APP_TOKEN`) so it sends it. Never commit it. Note: anyone who unpacks the APK could still find it, so it only stops casual strangers. |

## Redeploy from Cloud Shell
```bash
cd ~/Dua-ai && git fetch origin && git pull origin main   # after the PR is merged
cd google-cloud-backend
gcloud run deploy dua-ai-engine --source . --region us-central1 \
  --set-env-vars "GOOGLE_CLOUD_PROJECT=global-healer-m6shk,VERTEX_LOCATION=us-central1,VERTEX_MODEL=gemini-2.5-flash" \
  --allow-unauthenticated --project global-healer-m6shk
```
First-time setup (APIs + permissions + deploy): `PROJECT_ID=<your-project> ./deploy.sh`.

## Tests
```bash
pip install -r requirements.txt pytest && python -m pytest -q tests
```

For mocked unit tests only, Vertex credentials/SDK are unnecessary: install `Flask==3.0.3 pytest==8.3.5` and run `python -m pytest -q tests`. GitHub's Google Cloud engine check uses this isolated setup.

Separate-engine Android builds treat Dua/Gemini chat as configured independently of the base backend's provider keys. Chat file selection uses the existing bounded inline path (up to 3 files / 2 MB total; oversized photos are resized). Audio can also be sent inline. Use CSV/UTF-8 text for spreadsheets; XLSX/ODS and documents over the inline limit are rejected rather than uploaded as inaccessible storage references. Imagine retains the base-backend storage path.

Inline media bytes stay with their original turns for follow-ups/retry during the live process and are not persisted, exported or synced. Existing `attachmentText` is persisted with history (up to 32,000 characters per user turn). After restoring a chat, reselect media files if their bytes are needed again. Aggregate request/history size limits still apply; oversized inline history fails explicitly instead of silently dropping its media.
