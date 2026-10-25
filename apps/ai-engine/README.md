# Dua AI engine (Google Cloud Run)

Flask service that answers chats with Gemini on Vertex AI. The Android app sends its chat
messages here by default (`DUA_CHAT_BASE_URL`); everything else (sign-in, cloud history, files,
Imagine, voice, setup/config) uses the Node API `apps/api/` (Cloud Run `dua-api`).

Both chat endpoints require the owner's existing Supabase access token in `Authorization: Bearer <token>`.
The engine verifies it with the configured Supabase project's `GET /auth/v1/user`, then matches the
verified, non-anonymous account's confirmed email to `DUA_ALLOWED_EMAIL`. It does not trust decoded
JWT payloads, client-provided emails or `user_metadata` for authorization. Missing/invalid sessions
return 401, non-owner/unconfirmed accounts return 403, and missing setup or unavailable verification
returns 503 **before calling Vertex**. The health check remains public. Owner login cannot be disabled
with `DUA_REQUIRE_LOGIN=0`; `DUA_APP_TOKEN`, if retained, is an additional gate only.

## Endpoints
- `GET /` – health check.
- `POST /api/chat` – same format as the Node API's chat:
  `{"model","messages":[{"role":"user|model","text","attachmentText?"}],"attachments":[{"name","mimeType","data(base64)"}],"memory","stream":true,"mode","context?"}`.
  With `"stream": true` it replies with Server-Sent Events (`event: text` / `data: {"type":"text","delta":"..."}`,
  then `done`, or `error`), exactly like `apps/api/src/core/sse.mjs`. Without it: `{"text": "..."}`.
  The whole conversation is sent to Gemini, so it remembers earlier messages.
  Earlier user turns can also carry inline `attachments`; `attachmentText` and supplied `files` metadata text/transcripts are included as context. Storage paths are never fetched by Cloud Run.
  Streaming flushes an initial SSE heartbeat and sends another every 15 seconds while the Vertex worker is waiting. Cancelling the stream stops queueing results.
  Not supported here (a short notice is shown instead): other models (ChatGPT/Claude/Grok/Kimi – Gemini answers),
  web search, Run code, Build mode, server-side reply saving.
- **Dua Agent**: `POST /api/chat` with `"mode": "agent"` and `"stream": true`. Real tasks run one tool-calling
  loop (web search, page reading, code, calculator/time, images, memory suggestions, GitHub); see
  [Dua Agent tools](#dua-agent-tools-general-tool-loop) for tools, caps and progress chips. Web results end with a
  numbered Sources footer (`sources` event, [1], [2]). Grounding cost (Vertex): Gemini 3.x models share 5,000 free
  search queries per month, then $14 per 1,000 search queries (each `google_search` call counts).
  **Router:** casual messages (hi, thanks, small talk, simple questions) get one direct reply with full history
  and no Search. Clear task words (latest, news, ideas, compare, tools…) run the tool loop. Unclear messages use one
  cheap router call (low thinking, max `AGENT_ROUTER_TOKENS`=256, JSON); if it fails, long messages run the agent
  and short ones get a direct reply.
- `POST /api/agent/action` – owner only: `{"id", "decision": "approve"|"reject"}` runs (or drops) ONE write action
  the Agent asked for; see [Agent safety](#agent-safety-approvals-and-untrusted-content).
- `POST /chat` – old single message endpoint `{"message"}` -> `{"reply"}` (still works).

## Settings (environment variables only, no secrets in code)
| Name | Meaning |
| --- | --- |
| `GOOGLE_CLOUD_PROJECT` | your project id (required) |
| `VERTEX_LOCATION` | Vertex AI location, default `global` (needed for Gemini 3.x; endpoint `aiplatform.googleapis.com`). Not the Cloud Run region. |
| `VERTEX_MODEL` | default `gemini-3.8-flash` |
| `VERTEX_THINKING_LEVEL` | Gemini 3 only: `low` (default), `medium` or `high` (`minimal` is not supported by 3.8 Flash) |
| `SUPABASE_URL` | required; the same Supabase Auth project URL used by dua-api, HTTPS only |
| `SUPABASE_PUBLISHABLE_KEY` / `SUPABASE_ANON_KEY` | required; publishable key preferred, existing anon key supported. Used only by the engine to verify the account; no service-role key is needed |
| `DUA_ALLOWED_EMAIL` | required; the same owner email used by dua-api, compared case-insensitively against the Auth-verified account |
| `DUA_APP_TOKEN` | optional **additional** password. When set, requests must also send `X-Dua-App-Token`. Build the app with `-PduaAppToken=...` (or env `DUA_APP_TOKEN`). It never replaces the owner session; APK-embedded values are extractable |

### Local environment file

Use [.env.example](.env.example) as the local template. It leaves project/account values and credentials empty, documents the separate coding-agent token and keeps an empty repository target until you choose one. From `apps/ai-engine/`, with your Python virtual environment active and runtime dependencies installed:

```bash
# Preserve an existing local file.
[ -e .env ] || cp .env.example .env
# Edit .env with your own settings before sourcing it.
# Only source your trusted local file; quote values with shell metacharacters.
set -a
. ./.env
set +a
python app.py
```

Python does not automatically read `.env`; these commands export it for this shell. A copied, unfilled template may serve the public health check, but authenticated generation remains unavailable until owner Auth settings and Google runtime credentials are ready. Tests use mocks and need neither real credentials nor this file.

On Cloud Run, configure runtime settings on the service and keep the optional GitHub token in Secret Manager; do not upload a populated `.env`. Let Cloud Run supply `PORT`.

## Redeploy from Cloud Shell

For an ADK rollout, use the [supported staged ADK path](#supported-adk-rollout)
below. The direct source command in this section retains existing service
settings and can change serving traffic immediately; it is not the staged ADK
rollout procedure.

Runtime settings (`GOOGLE_CLOUD_PROJECT`, `VERTEX_LOCATION`, `VERTEX_MODEL`, `SUPABASE_URL`,
`SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY`, `DUA_ALLOWED_EMAIL`, optional secrets) already live on the
**Cloud Run service**. A redeploy keeps them, so the standard command passes no settings:

```bash
cd ~/Dua-ai && git fetch origin && git checkout main && git reset --hard origin/main && cd apps/ai-engine && gcloud run deploy dua-ai-engine --source . --region us-central1 --project global-healer-m6shk --allow-unauthenticated --timeout 900
```

To change one setting, use `gcloud run services update dua-ai-engine --region us-central1 --project
global-healer-m6shk --update-env-vars NAME=value` (or `--update-secrets` for Secret Manager values). Never paste
tokens or keys into chat, source files or screenshots; they are never compiled into Android.
`--allow-unauthenticated` only lets requests reach the service; owner verification still guards the chat routes.

After deployment, confirm both `/api/chat` and `/chat` reject a request without `Authorization`,
reject a non-owner/expired session, and allow a fresh owner session. No rejected request should
invoke Vertex. Tests mock Auth/Vertex; successful tests do not certify the live service settings.

## ADK entrypoints and ownership

There is one supported Cloud Run HTTP server chain for normal chat and ADK:

```text
Dockerfile CMD -> adk_server.py -> exec Gunicorn wsgi:app -> app.py Flask app
```

`wsgi.py` applies the website CORS middleware to the existing Flask app. ADK runs
inside the same owner-authenticated `/api/chat` request path when selected by the
existing configuration/router. It does not start a separate ADK HTTP server or
change `dua-ai-engine` into an SDK example server. This map describes the current
checkout's roles; it does not assert which revision is serving in Cloud Run.

| File or directory | Role | Used for current ADK rollout? |
| --- | --- | --- |
| [adk_server.py](adk_server.py) | Container startup validator and Gunicorn launcher. Checks adapters/custom tools, `PORT` and configured production persistence; does not initialize database tables. | Yes: Docker CMD calls it for the same `wsgi:app` server. Keep the wrapper. |
| [wsgi.py](wsgi.py), [app.py](app.py) | Main HTTP entry and Flask chat/Agent endpoints. | Yes: one server for ordinary chat and ADK. |
| [dua_engine/agent/agent_adk.py](dua_engine/agent/agent_adk.py) | Real ADK orchestration: planner, helpers, merger, optional review, cancellation and SDK runner lifecycle. | Yes, in process when the existing ADK route is selected. Root [agent_adk.py](agent_adk.py) is a compatibility alias for imports/monkeypatches, not another server. |
| [adk_cli.py](adk_cli.py) | Operations CLI: status, explicit database initialization, live requests/indexing, scaffold, offline tests/evaluation. | Optional operator tool, never the HTTP server or deploy command. `run`/indexing can make provider calls. |
| [adk_examples/](adk_examples/) | Opt-in custom-tool examples, including `text_tools.word_count`; exercised by extension tests. | Only if explicitly configured through `ADK_TOOL_MODULES`. No server and no default activation. CLI-generated scaffold packages and official `adk run` are separate development examples, not the chat deployment. |
| [deploy-adk.sh](deploy-adk.sh) | Current-checkout rollout controller: `plan`, `deploy` (stage), `promote`, `rollback`. | Yes: the supported ADK deployment path below. |
| [cloudbuild-adk.yaml](cloudbuild-adk.yaml) | Image build/push recipe invoked by `deploy-adk.sh`; passes the source SHA into the existing Dockerfile. | Yes, as a build input; it does not serve HTTP or promote traffic. |
| [dua-adk-cloudshell.sh](dua-adk-cloudshell.sh) | Historical executable bootstrap pinned to commit `cea9b6df188b8bd926bbf549419ef62e8e08643b`. It clones that old checkout and uses its `google-cloud-backend/` paths. | No for current-source rollouts. Retained for historical setup/recovery; it can alter database/secret/candidate state when run, so it is not a harmless example. Do not substitute it for `deploy-adk.sh` or rewrite its pinned paths in isolation. |

The launcher, operations CLI and import alias have different responsibilities;
there is no duplicate HTTP server wrapper to fold. Their filenames, behavior,
tests, deployment scripts and historical pin are retained.

### Supported ADK rollout

Use `deploy-adk.sh` from a clean, fetched and fast-forwarded `main`; it checks that
the checkout matches `origin/main`. Start from the repository root:

```bash
git fetch origin
git switch main
git pull --ff-only
bash apps/ai-engine/deploy-adk.sh plan
```

Then use this single flow for the existing `dua-ai-engine` service:

1. Prepare the existing server settings/secret bindings and required PostgreSQL
   schema/tables as described in [ADK operations](../../docs/ADK_IMPLEMENTATION.md#durable-sessions-and-approvals).
   Startup validates configured storage; it does not run `init-db` automatically.
2. Set `ADK_IMAGE` to the intended unique image URL in your existing Artifact
   Registry repository, then run `bash apps/ai-engine/deploy-adk.sh deploy`.
   The script records prior traffic in `ADK_SNAPSHOT` (default
   `/tmp/dua-adk-rollout.json`), builds this checkout through
   `cloudbuild-adk.yaml` and stages tag `adk-candidate` with `--no-traffic`.
   It updates the existing ADK mode/production/build markers and retains other
   service environment, secret and IAM bindings. Keep the snapshot for rollback.
3. Verify the candidate revision/tag URL, owner-only capabilities, storage
   readiness and the intended authenticated workflow. Public health or offline
   CI alone does not verify provider readiness. `adk_cli.py status --endpoint`
   can query candidate capabilities with the existing owner credentials in the
   environment; `run` is a separate explicit live request.
4. Set `ADK_VERIFIED_REVISION` to the exact revision you verified, then run
   `bash apps/ai-engine/deploy-adk.sh promote`. This is the explicit traffic
   change; staging does not promote automatically.
5. If rollback is needed, select the saved `ADK_SNAPSHOT` and run
   `bash apps/ai-engine/deploy-adk.sh rollback`. It validates the target and
   restores recorded traffic; it does not undo database/schema changes.

Defaults target `dua-ai-engine` in `global-healer-m6shk`, `us-central1`; keep the
intended target consistent across plan, stage, promote and rollback. These are
operator instructions, not a deployment performed by this documentation change.

## Kill-switch ownership

[`kill_switches.py`](kill_switches.py) is the engine's single live kill-switch
implementation. It owns parsing, pause messages and the engine checks for
`DUA_DISABLE_AGENT`, `DUA_DISABLE_IMAGINE`, `DUA_MAINTENANCE` and optional
`DUA_MAINTENANCE_MESSAGE`. It is not a leftover root module: the Flask routes
import it directly. There is no second implementation under `dua_engine/` and
no stale engine copy to remove or compatibility shim to add.

| Path | Responsibility |
| --- | --- |
| [kill_switches.py](kill_switches.py) | Live Python owner of emergency pause flags/messages; all switches default off. |
| [app.py](app.py) | Calls that owner for chat/Agent admission, Agent actions, capabilities, selected document operations, note reindex, Imagine, titles and legacy chat. These route checks are callers, not duplicate flag definitions. |
| [dua_engine/agent/agent_tools.py](dua_engine/agent/agent_tools.py), [dua_engine/agent/agent_adk.py](dua_engine/agent/agent_adk.py) | Tool-loop and ADK implementations run behind the same Flask chat/Agent guards. Their root aliases preserve imports; neither owns another kill-switch parser. |
| [imagine.py](imagine.py) | `DUA_IMAGINE` is feature configuration checked separately from the route's emergency pause guard. Its `enabled()` helper is not a second definition of `DUA_DISABLE_IMAGINE`. |
| [Shared contract](../../packages/contracts/KILL_SWITCHES.md) | Authoritative names/defaults/messages and per-service ownership. This document is consumed by contract tests, not loaded to resolve runtime switches. |

`AGENT_ENGINE=adk` selects the Agent implementation; removing it can select the
ordinary tool loop, so it is not a replacement for pausing Agent. Likewise,
`DUA_IMAGINE` and `DUA_DISABLE_IMAGINE` keep their different existing meanings
and parsing/default rules. Do not merge or alias those settings.

The separate `dua-api` implementation enforces its own routes using the shared
contract; it is not an engine duplicate or an obsolete import path. Voice's
`DUA_DISABLE_VOICE` belongs only to the API. Each service reads its own environment,
so shared names do not synchronize deployed values. See the
[operations guide](../../docs/KILL_SWITCHES.md) for existing route exemptions and
operator commands. This ownership map changes no flags, live defaults or routes.

## Coding agent setup (phase 2, optional)

In Agent mode, coding requests ("fix the login bug", "add X to the app", "code likho …") make the Dua Agent
read the repository, prepare a change, push it to a **new branch `dua-agent/…`** and open a **pull request
into main**. It never merges, never pushes to main, never force-pushes and never deletes branches; the server
blocks every other GitHub request (allowlist in `coding_agent.py`). Repository: `DUA_REPO` (default
`KRFbrothers/Dua-ai`). Caps: 15 tool steps, 40 files read, 10 files changed, 200 KB per file, 10 minutes.
Without `GITHUB_TOKEN` it just says coding is not set up yet.

Repository/path checks run at both the tool/proposal and HTTP client boundaries. Reads and writes
reject traversal, empty/dot path segments, controls and case-insensitive `.git` segments. Writes also
block root `.gitmodules` and all root `.github/` files; tree entries must be regular files, not symlinks
or submodules. Code search accepts plain terms, replaces caller-supplied repository/org/user scopes
with the configured repository, rejects quotes/parentheses/backslashes and OR/NOT operators, and
verifies each result belongs to that repository. See [GitHub validation policy](../../docs/GITHUB_VALIDATION.md)
for platform-specific limits and mocked regression tests.

Before GitHub writes, the PR client now checks the complete `dua-agent/` branch name (1–80 lowercase letters/digits/dashes after the prefix, with an alphanumeric start/end), rejects empty submissions and file/folder clashes within the proposed changes, and enforces the same changed-file count cap at the tree request boundary. Existing generated branch names and file order are preserved. See [Agent PR preflight](../../docs/GITHUB_VALIDATION.md#cloud-run-agent-pr-preflight) for the exact policy and limits; this is roadmap section 2.3 hardening, not a new write capability.

**1. Create the GitHub token** (on github.com, signed in as KRFbrothers)
1. Profile picture → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.
2. Name: `dua-agent`. Expiration: 90 days (make a new one when it expires).
3. Repository access: **Only select repositories** → `KRFbrothers/Dua-ai`.
4. Permissions → Repository permissions: **Contents: Read and write**, **Pull requests: Read and write**, **Issues: Read and write** (Agent issue tools)
   (Metadata: Read-only is added automatically). Nothing else.
5. **Generate token** and copy it (it starts with `github_pat_`). Do not paste it in chat, code or screenshots.

**2. Store it in Secret Manager and give it to Cloud Run** (Cloud Shell)
```bash
gcloud services enable secretmanager.googleapis.com --project global-healer-m6shk
read -s -p "Paste GitHub token: " GH && printf %s "$GH" | gcloud secrets create dua-github-token --data-file=- --project global-healer-m6shk; unset GH; echo
gcloud secrets add-iam-policy-binding dua-github-token --project global-healer-m6shk \
  --member="serviceAccount:228305912525-compute@developer.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk \
  --update-secrets GITHUB_TOKEN=dua-github-token:latest --timeout 900
```
New token later: `read -s -p "Paste GitHub token: " GH && printf %s "$GH" | gcloud secrets versions add dua-github-token --data-file=- --project global-healer-m6shk; unset GH`
then run the `gcloud run services update … --update-secrets GITHUB_TOKEN=dua-github-token:latest` line again.
To switch coding off: `gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --remove-secrets GITHUB_TOKEN`.

## Free models (OpenRouter, optional)

With `OPENROUTER_API_KEY` set, `GET /` lists a few curated **free** OpenRouter models (`freeModels`, ids ending
`:free`, re-checked against OpenRouter hourly) and the app shows them in the model picker under **Free models**.
They answer plain chat (and Reasoning) with the full multi-turn history, streamed in the app's SSE format. Agent,
web, code and Build always stay on Gemini, and Gemini stays the default. Only text is sent; images and audio are not.
Owner sign-in still applies. The free tier allows about **20 requests a minute and 50 a day** (1,000 a day once you
have bought at least 10 credits in total); past that the app shows a friendly "limit reached" message.

**1. Create the key** (free): sign up at https://openrouter.ai → **Settings → Keys** → **Create key** (name `dua`,
leave the credit limit empty or set it to 0). Copy the key (`sk-or-…`). Do not paste it in chat, code or screenshots.

**2. Store it in Secret Manager and give it to Cloud Run** (Cloud Shell)
```bash
read -s -p "Paste OpenRouter key: " OR && printf %s "$OR" | gcloud secrets create openrouter-key --data-file=- --project global-healer-m6shk; unset OR; echo
gcloud secrets add-iam-policy-binding openrouter-key --project global-healer-m6shk \
  --member="serviceAccount:228305912525-compute@developer.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk \
  --update-secrets OPENROUTER_API_KEY=openrouter-key:latest
```
New key later: `read -s -p "Paste OpenRouter key: " OR && printf %s "$OR" | gcloud secrets versions add openrouter-key --data-file=- --project global-healer-m6shk; unset OR`
then run the `--update-secrets` line again. To switch free models off: `gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --remove-secrets OPENROUTER_API_KEY`.

## Tests
```bash
pip install -r requirements.txt pytest && python -m pytest -q tests
```

For mocked unit tests only, Vertex credentials/SDK are unnecessary: install `Flask==3.0.3 pytest==8.3.5` and run `python -m pytest -q tests`. GitHub's Google Cloud engine check runs this minimal setup first, then reruns with the production SDK dependencies and scripted offline models (see [ADK SDK checks](../../docs/TESTING.md#agent--adk-sdk-checks)).

Separate-engine Android builds treat Dua/Gemini chat as configured independently of the base backend's provider keys. Chat file selection uses the existing bounded inline path (up to 3 files / 2 MB total; oversized photos are resized). Audio can also be sent inline. Use CSV/UTF-8 text for spreadsheets; XLSX/ODS and documents over the inline limit are rejected rather than uploaded as inaccessible storage references. Imagine retains the base-backend storage path.

Inline media bytes stay with their original turns for follow-ups/retry during the live process and are not persisted, exported or synced. Existing `attachmentText` is persisted with history (up to 32,000 characters per user turn). After restoring a chat, reselect media files if their bytes are needed again. Aggregate request/history size limits still apply; oversized inline history fails explicitly instead of silently dropping its media.

## Imagine on Google Cloud (images)

`POST /api/imagine/image` makes **one image per request** with the Gemini image model on Vertex AI
(`IMAGINE_MODEL`, default `gemini-3.1-flash-lite-image`, "Nano Banana 2 Lite", global endpoint; list price
$30 per 1M image-output tokens = about **$0.034 per 1K image**, plus a tiny text-input cost). It takes the same request and returns the same
response as the Node API, and stores the image in your private Supabase `dua-files` bucket using your own
sign-in (no service-role key). The daily cap uses the same Supabase counter as the Node API (`IMAGINE_DAILY_IMAGES`,
default 20). Owner sign-in is required. The app uses Google Cloud for new images when `GET /` shows
`"imagine": {"image": true}`; photo edits and video stay on the Node API (dua-api). Turn it off with `DUA_IMAGINE=0`.
Needs the existing `SUPABASE_URL` / `SUPABASE_ANON_KEY` settings and the Supabase Imagine migration. No new secret is needed.

## Dua Agent tools (general tool loop)

Agent mode first runs the cheap router: greetings and simple questions get one direct reply. Real tasks run one
tool-calling loop on `VERTEX_MODEL` (default `gemini-3.8-flash`) in `agent_tools.py`. The model picks the tools;
each one runs on this server, behind the same owner sign-in as `/api/chat`.

| Tool | What it does | Cap per request |
| --- | --- | --- |
| `google_search` | One grounded Gemini call with Google Search; notes + numbered sources (Sources footer) | 3 |
| `fetch_url` | Reads one public page as text. http(s), ports 80/443 only; private, loopback, link-local and metadata IPs blocked; connection pinned to the checked IP; redirects re-checked; 2 MB / 20k chars | 5 |
| `run_python` | One Gemini call with the built-in code execution sandbox (Google runs the code, not this server) | 2 |
| `calculator` | Safe math (no Python eval) | 20 |
| `current_time` | Date and time in IST | 5 |
| `generate_image` | Same path as `/api/imagine/image` (owner storage, `IMAGINE_DAILY_IMAGES` cap); needs `DUA_IMAGINE` on | 1 |
| `remember` / `recall` | Suggests a note to the app's Memory (you confirm it in Settings > Integrations); reads the Memory the app sends | 3 / 3 |
| `list_files`, `read_file`, `search_code`, `propose_changes` | GitHub tools on `DUA_REPO`, only when `GITHUB_TOKEN` is set; same allowlist (new `dua-agent/` branch + PR, never merges) | coding caps |
| `list_issues`, `read_issue`, `search_issues`, `create_issue`, `comment_on_issue` | Issues on `KRFbrothers/Dua-ai` only (needs `GITHUB_TOKEN` with Issues: Read and write). Pull requests are skipped; bodies/comments capped at 4,000 characters. New issues end with "Created by Dua Agent", comments with "Comment by Dua Agent"; only existing labels. No close, edit, assign, lock or delete | 5 reads (shared), 1 create, 1 comment |

Loop caps: `AGENT_MAX_STEPS` model calls (default 12), `AGENT_STEP_TOKENS` output tokens per step (16384),
`AGENT_SUB_TOKENS` per search/code call (3072) and `AGENT_MAX_SECONDS` total (600). Each tool shows a step chip
in the app ("Searching the web…", "Opening page…", "Running code…", "Creating image…").

## Agent model (optional)

`AGENT_MODEL` sets the model for the Dua Agent tool loop only (its search/code sub-calls included); the router, casual replies, titles and normal chat stay on `VERTEX_MODEL`. Default: same as `VERTEX_MODEL`. Example: `AGENT_MODEL=gemini-4-argon` (Vertex locations `global`, `us`, `eu`). `AGENT_THINKING_LEVEL` is `low`, `medium` or `high` (default `medium` when `AGENT_MODEL` differs). With a `gemini-4*` agent model the per-step output cap (thinking included) defaults to 32768; `AGENT_STEP_TOKENS` overrides it. Step, time and tool caps are unchanged. If the agent model returns 404/403 (not found or no permission), the request falls back once to `VERTEX_MODEL` and logs `[dua-agent] ... falling back`.

## Agent notes (self-learning, optional)

The Dua Agent keeps its own private notes in Supabase table `agent_notes` (migration `infra/database/migrations/202610160001_dua_agent_notes.sql`). They are separate from the app's user-approved Memory. Every request uses the signed-in owner's token plus the anon key, so RLS keeps rows owner-only; no service-role key is used.
- At the start of an Agent task, up to 20 relevant notes (about 2k tokens) are added to the system prompt.
- After the reply, one cheap `VERTEX_MODEL` call (low thinking, 1024-token cap) extracts 0–3 durable lessons, drops duplicates and anything that looks like a secret, token, key or email, and saves them (`source = auto`).
- Tools: `save_note`, `search_notes`, `delete_note` (3 each per request; delete works only on the owner's rows).
- At most 200 notes per owner; a table trigger prunes the oldest.
- `AGENT_NOTES=0` turns it all off. Notes, memory, "yaad", "pasand" and preference requests always use the tool loop; casual replies also get notes and learn afterwards.
- If the table is missing (migration not applied) or unreachable, the log shows `[dua-agent-notes] could not load notes: missing_table` and the Agent says notes are unavailable. `GET /api/agent/notes/status` (owner-only) returns `{"enabled", "ready", "reason"}` with no note data.

## Agent timeouts

Each Agent model step has a hard deadline, `AGENT_STEP_SECONDS` (default 150; also sent as the HTTP timeout). Tools get `AGENT_TOOL_SECONDS` (default 45); `google_search` and `run_python` get 90 and `generate_image` 120. A slow tool returns an error to the model, and a slow step stops the reply with a clear message. Supabase notes calls time out after 5 s. Slow steps show a "Still thinking (step N, 30s)…" chip every 15 s, and Cloud Run logs show `[dua-agent] step N: model 12.3s` / `tool name: 0.2s`. Since this change, `AGENT_THINKING_LEVEL` defaults to `low` when `AGENT_MODEL` differs from `VERTEX_MODEL`.

**Phone vs server limits.** The agent loop stops itself at `AGENT_MAX_SECONDS` (600 s, checked before every step),
Cloud Run ends every request at `--timeout 900`, and the app waits up to 960 s for an Agent reply (normal chat keeps
330 s). So the server always finishes first and the phone shows the server's own message instead of a silent timeout.
We kept the server limit and raised the phone limit (not the other way round) because tested PRs (`AGENT_CI_SECONDS`
420) and long research need more than 330 s.

**Disconnect = stop.** When the phone goes away (app closed, network lost, Stop tapped), the stream's generator is
closed; that sets a cancel flag. The old loop checks it before every model step and tool and stops waiting on a
running step within about 1 s; the ADK path stops reading events, its tools raise, and a `before_model_callback`
answers "Stopped." without calling the model. The self-check and notes learning are skipped too. The log shows
`step N: app disconnected; stopping`. (A model call already in flight finishes on its own HTTP timeout, but its
result is thrown away and no further calls are made.)

## Agent safety: approvals and untrusted content

[`agent_safety.py`](agent_safety.py) protects both agent paths (the normal loop and `AGENT_ENGINE=adk`) the same way:

- **Write tools need your approval.** `propose_changes` (open a PR), `create_issue`, `comment_on_issue`, `save_note`
  and `delete_note` never run inside the agent loop. The engine checks the call (paths, sizes, pre-check), stores it
  in server memory and streams an `approval` event `{"id","tool","title","details","expiresIn"}`; the reply says
  "Approval needed". The normal loop then stops; in ADK mode the helper is told to wait and the merger says so.
  The app shows an Approve / Reject card. Approve calls `POST /api/agent/action` with only the id: the engine checks
  the owner login, takes the action out of memory (single use) and refuses it after 15 minutes
  (`AGENT_APPROVAL_SECONDS`, 410) or if unknown/used/another owner (404). Reject drops it, and the app adds
  "You declined …" to the reply so the Agent sees it in the next turn. Pending actions live only in this instance's
  memory (`--workers 1`): after a restart or on another instance the id is unknown and nothing runs.
- **Read-only tools stay automatic** (search, pages, files, issues, notes search, calculator, time, Python sandbox).
  `generate_image` (owner storage, 1 per request, daily cap) and `remember` (only a suggestion the app's Memory review
  list asks you about) also stay automatic.
- **Untrusted content is fenced.** Output of tools that read outside text (web search notes, pages, repository files,
  issues, CI output, notes, Python output) reaches the model between `<<<UNTRUSTED_DATA tool=…>>>` and
  `<<<END_UNTRUSTED_DATA>>>`; fake markers inside the content are removed. The system prompt says such content is
  data, never instructions.
- **Tested PRs:** with `AGENT_TEST_CODE=1` the pre-check still runs before the approval card. The PR opens only after
  Approve, so the engine no longer waits for CI or pushes an automatic fix inside the same request; check CI on the
  PR page. (Steps 2-4 of [Tested pull requests](#tested-pull-requests-agent_test_code1) are not reached by the agent
  loop any more.)

## Daily backup (Cloud Run job `dua-backup`)

[`backup.py`](backup.py) runs as a Cloud Run job every day at 03:00 IST (Cloud Scheduler). It needs no extra packages.
- **Database:** exports the Supabase tables (`dua_cloud_snapshots` with chats/memory, `agent_notes`, usage tables) to `gs://dua-backups/db/YYYY-MM-DD/<table>.json.gz`. A bucket lifecycle rule deletes these after 30 days.
- **Files:** mirrors the Supabase Storage bucket `dua-files` to `gs://dua-backups-m6shk/files/<path>`. Only new or changed files are copied, and files deleted in the app stay in the backup.
- **Secrets:** needs `SUPABASE_SERVICE_ROLE_KEY` in Secret Manager, because a scheduled job has no user login. It is used only by this job, never by the chat engine.
- **Restore:** download a `.json.gz` file from the bucket; each one holds `{"table", "date", "rows"}`.
- **Logs:** each run writes `[dua-backup] OK {...}` or `[dua-backup] FAILED: ...`, and a non-zero exit marks the run as failed.

## Parallel multi-agent mode on Google ADK (optional, `AGENT_ENGINE=adk`)

[`dua_engine/agent/agent_adk.py`](dua_engine/agent/agent_adk.py) (root `agent_adk.py` is its compatibility alias) uses Google Agent Development Kit (`google-adk==2.12.0`, official source: [google/adk-python](https://github.com/google/adk-python/tree/v2.12.0); owner fork: [KRFbrothers/adk-python](https://github.com/KRFbrothers/adk-python)), imported only when the flag is on.
1. **Plan:** an ADK controller agent on `AGENT_MODEL` decides whether the request has 2–3 independent parts (research / code / notes). Uses robust JSON plan parsing that handles both raw JSON and markdown codeblock responses.
2. **Helpers:** an ADK `SequentialAgent` runs the helper stage, then the merger on `AGENT_MODEL`. Helpers on `VERTEX_MODEL` run sequentially by default (`ADK_HELPERS_SEQUENTIAL=1`); `0` selects the existing `ParallelAgent` stage.
   - Helpers call the existing tools through one shared `AgentRun`, so the same per-tool caps, #214 timeouts and global source numbers apply.
   - At most one pull request is opened per request.
   - `AGENT_SUB_STEPS` (default 5 per helper) caps model calls, and `AGENT_ADK_SECONDS` (default 300) caps total time.
3. **Progress & Chain-of-Thought (CoT):** goes out as the existing chips (`sub_a`/`sub_b`/`sub_c`, `merge`) and live thinking events (`part.thought` extraction) streamed directly to Dua AI's Android `ThinkingBlock` UI.
4. **Artifacts Canvas formatting:** The merger formats diagrams, SVG, React, and code snippets with explicit language identifiers for interactive preview in Dua AI's Claude-style Artifacts Canvas.

**Plans, helper limits and proposals:** the planner must return an actual JSON
`true` and valid available helper tasks. Malformed plans fall back to the normal
loop. A per-helper before-model callback enforces `AGENT_SUB_STEPS`, while
`RunConfig.max_llm_calls` separately caps the whole parallel run. Hitting a
helper's cap produces an incomplete result and a matching progress label; it
preserves the other helpers' budgets and lets the merger use partial results.
`propose_changes` rejects mismatched path/content lists before approval, returning
a tool error so the model can repair its arguments instead of silently omitting
files. Existing file validation and owner approvals still apply.

**Runner lifecycle:** the request thread uses ADK's production `run_async`
interface with async session creation. Thought updates are forwarded during the
run. The stream and runner close on the same event loop on success, failure or
cancellation; the total deadline also signals background ADK work to stop.
Already-started synchronous tool calls retain their existing HTTP timeouts.
See [ADK SDK checks](../../docs/TESTING.md#agent--adk-sdk-checks) for offline regression coverage.

**Fallback:** ordinary unsplit requests can use the existing loop. Explicit provider routes and extension tools use ADK even for one part. Failures after streaming/actions, during resume, or with explicit routes stop without replay or silent provider changes. Coding-mode requests use the existing coding loop. Optional notes learning is included in request usage reporting.

## Self-check (`AGENT_VERIFY=1`)

[`agent_verify.py`](agent_verify.py) runs after both the old loop and the ADK path. Casual replies and coding mode skip it.
- **Critic:** one Flash call (low thinking, 1024 tokens, 45 s limit) compares the draft with the request, the sources and short tool results.
- **Revision:** only if the critic finds issues, one revision by the Agent model (90 s limit).
- **Safety:** any failure keeps the draft, and a revision that drops a PR link is rejected.
- **Progress and logs:** the chip shows `verify` ("Checking answer…"), and the log shows `[dua-agent] verify: ok` or `N issue(s)`.

## Tested pull requests (`AGENT_TEST_CODE=1`)

Since the approval guard, only step 1 runs during the agent request (before the approval card); the PR is opened
by `POST /api/agent/action` after you approve, without the CI wait / auto-fix below.
[`pr_flow.py`](pr_flow.py) handles `propose_changes` in both paths:
1. **Pre-check:** Python files are byte-compiled in a temp dir (20 s timeout) and JSON files are parsed. A failure goes back to the model; after 2 failures, no PR is opened.
2. **CI wait:** after the PR opens, the engine polls GitHub CI (check runs, falling back to Actions runs). The total wait per request is `AGENT_CI_SECONDS` (default 420).
3. **One fix:** if CI fails, the model gets one fix attempt. The fix is a fast-forward commit on the same `dua-agent/` branch (never force, never main, never merge), and CI is polled again.
4. **`check_pr_ci` tool:** reads the status of the agent's own PR.

**Token:** reading CI needs 'Actions: Read-only' (or 'Checks') on the fine-grained token; without it the answer says so.

## Complete ADK integrations

See [ADK implementation and operations](../../docs/ADK_IMPLEMENTATION.md) for the
unchanged 25-requirement scope, durable SQL sessions/resume and approvals, native
pgvector document/note retrieval, provider routing/fallback, custom typed tools,
streaming, safe tracing/usage, evaluations, CLI and staged deployment/rollback.
Use `python adk_cli.py --help` for actual commands. Configuration/live provider
readiness is separate from code and offline tests.
