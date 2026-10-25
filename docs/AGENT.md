# Dua Agent (dua-ai-engine)

The Agent is the tool-using mode of the Cloud Run engine (`apps/ai-engine/`). This page summarises how it runs and which settings control it. Implementation details and setup steps live in the [engine README](../apps/ai-engine/README.md#dua-agent-tools-general-tool-loop). Change settings only with `--update-env-vars` ([OPERATIONS.md](OPERATIONS.md#changing-settings-safely)).

The approved ADK target is recorded in [ADK_SCOPE.md](ADK_SCOPE.md): Fareed's
unchanged six-part checklist, current integration gaps and official GitHub sources.
The integration and live-configuration boundaries are documented in
[ADK_IMPLEMENTATION.md](ADK_IMPLEMENTATION.md).

## Two ways to run: tool loop vs ADK

| Mode | When | How |
| --- | --- | --- |
| Tool loop (default) | `AGENT_ENGINE` unset | [`agent_tools.py`](../apps/ai-engine/agent_tools.py): one model (`AGENT_MODEL`) calls tools step by step (search, code, notes, image, GitHub proposals) within step/time/tool caps. |
| ADK parallel mode | `AGENT_ENGINE=adk` | [`agent_adk.py`](../apps/ai-engine/agent_adk.py) (Google ADK, imported only when on): a controller plans 2–3 independent parts, helpers on `VERTEX_MODEL` run in parallel through the same tools, a merger on `AGENT_MODEL` writes the answer. |

The Python SDK is already included in the engine requirements (`google-adk==2.12.0`),
from the official [Google ADK source](https://github.com/google/adk-python/tree/v2.12.0).
[CI verifies the real SDK offline](TESTING.md#agent--adk-sdk-checks), including
parallel helpers, multipart answers, approvals and disconnect handling. This is
integration verification; it does not establish which path a deployed revision
currently enables or how well its live model answers.

ADK runs through the official production `run_async` API. Planning uses an ephemeral session. The helper/merger pipeline uses the official
SQL session service when `ADK_SESSION_DB_URL` is configured, and supports owned
resume through `resumeRun`; otherwise it uses an in-memory session. Each run closes its event stream, and awaits
`Runner.close()` in the same asyncio task/event loop, including failure paths. Thought events
reach the existing progress stream as they arrive. Cancellation is checked while
waiting for an event (about once per second), and a deadline also signals the
worker to stop before further model/tool calls. An HTTP call already executing
inside a synchronous tool thread still depends on its existing bounded timeout;
this does not promise that a remote provider stopped processing or billing it.

The controller starts helpers only for a real JSON `true` with a list of valid,
available helper kinds and non-empty string tasks. Invalid plans use the normal
loop. Each helper's `AGENT_SUB_STEPS` limit is enforced before model calls, in
addition to the run-wide SDK limit. A capped helper returns an explicit incomplete
result and retains that status in progress, so the merger can finish honestly
with the available results. Other helpers keep their own call allowance.

Code proposals require matching path/content lists of strings. Empty lists submit
all staged edits from an active workspace; without staged edits they are rejected
by the server before approval. A
mismatch returns a repairable tool error before creating any approval; no file
is silently dropped. Complete proposals still use the existing owner approval
and repository validation rules.

**Fallback to the loop:** ordinary unsplit requests can use the existing loop. Configured provider routes, custom tools and indexed documents can use a single ADK helper. After streaming/actions, on resume, or with explicit provider routes, failures stop without replay or silent provider changes. Coding-mode requests use the existing coding loop. To turn ADK off, remove the flag:

```bash
gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --remove-env-vars AGENT_ENGINE
```

After either path, optional add-ons run:

- **Self-check** (`AGENT_VERIFY=1`, [`agent_verify.py`](../apps/ai-engine/agent_verify.py)): a cheap critic call compares the draft with the request and sources; only if it finds issues, one revision. Failures keep the draft; a revision that drops a PR link is rejected. Casual replies and coding mode skip it.
- **ADK reviewer** (`DUA_AGENT_REVIEW=1`, ADK path only, [`agent_adk.py`](../apps/ai-engine/agent_adk.py)): after the merger, a `reviewer` LlmAgent (cheap Flash model) returns a JSON verdict (`ok`, `needs_fix`, `needs_approval`). Only `needs_fix` runs the `reviser` agent, at most once. Live streaming waits for the review; any bad verdict, or a revision that drops a PR link, keeps the draft. Pattern adapted from google/adk-samples llm-auditor.
- **Tested PRs** (`AGENT_TEST_CODE=1`, [`pr_flow.py`](../apps/ai-engine/pr_flow.py)): proposed Python files are byte-compiled and JSON parsed before a PR opens; after the PR opens, the engine polls GitHub CI and allows **one** fix commit on the same `dua-agent/` branch (never main, never force, never merge). The `check_pr_ci` tool reads the agent's own PR status.

See the [new ADK configuration and operations guide](ADK_IMPLEMENTATION.md) for
SQL persistence, embeddings, provider routes, tools, telemetry and CLI settings.

## Coding workspace: first implementation phase

Coding tools now support a request-local text workspace on a fixed `main` commit.
Both the normal tool loop and the ADK Code helper use the same implementation in
[`coding_workspace.py`](../apps/ai-engine/coding_workspace.py), the existing
allowlisted GitHub REST client, and Python's standard-library `difflib`. Google
ADK remains the runtime; no additional agent framework or copied vendor source
is introduced. The text workspace alone requires no new secret or deployment
setting; optional terminal execution has separate configuration below.

1. `workspace_status` pins the current commit and returns root `AGENTS.md`, if
   present. With `path="app/.../File.kt"`, it returns applicable root-to-leaf
   `AGENTS.md` guidance. Repository guidance supplies project conventions only;
   it cannot change permissions, request secrets or authorize external actions.
2. `list_files` and `read_file` then use that immutable tree, including staged
   text. Read responses also carry scoped guidance. UTF-8 file contents are
   bounded; a clipped read never replaces the full original stored server-side.
   `search_code` remains a live GitHub index hint: candidates must be read at the
   pinned commit, and newly staged content is not indexed.
3. `edit_file(path, old_text, new_text)` requires one exact match in the current
   draft. For a new file only, empty `old_text` uses `new_text` as full content.
   Scoped guidance must have been returned before editing. File/byte caps and
   protected paths apply; symlinks, submodules and directory replacement are
   rejected. Existing executable file modes are preserved when creating the PR.
4. `workspace_diff` shows a bounded unified diff. `propose_changes` with
   `files=[]` (ADK: empty `paths`/`contents`) materializes **all** staged files
   into the existing approval record, with server-selected repository/base SHA.
   The model cannot choose the stored base SHA. A proposal using full file
   contents remains supported when no staged edits exist.
5. Approval executes those stored contents even after the original request has
   ended. If `main` or the configured repository changed, the action is refused
   before GitHub writes and the owner must request a fresh proposal. Otherwise,
   it creates the usual new `dua-agent/` branch and PR from the pinned commit.
   If `main` advances after the preflight check, the PR still uses the original
   commit; GitHub review/merge checks remain necessary.

Active drafts/caches are held in bounded per-request memory, serialized with a
lock. Saved staged text can be restored through the third phase below; terminal
processes and caches do not survive cancellation/restart. Approval records use the existing
approval storage and expiration rules. Existing callers that do not start a
workspace retain the previous read/full-content proposal flow.

**Second phase:** [`run_command` and the separate container runner](CODING_RUNNER.md)
execute a pinned source snapshot plus staged edits when explicitly configured.
The runner has bounded resources, no network/engine credentials and cancellation
wiring. Selected text edits can be explicitly imported through the third phase.
The existing provider `run_python` tool remains separate from this repo terminal.

**Third phase:** [private SQL draft checkpoints/resume and selected command-file
import](CODING_CHECKPOINTS.md), with opt-in owner/revision checks and atomic,
single-use imports that reject a changed draft.

**Fourth phase:** [Android coding activity, approval diff and saved-draft
controls](CODING_ACTIVITY.md).

**Fifth phase:** [read-only PR CI evidence](CODING_CI.md), including observed-head
checks, bounded failure annotations/steps and an Android snapshot card.

**Sixth phase:** [reviewed repairs to an existing agent PR](CODING_PR_REPAIR.md),
with a pinned PR workspace, separate fix approval and non-forced branch update.

**Seventh phase:** [configured Python/JVM/Android runner profiles](CODING_PROFILES.md),
with fixed limits, image-contract discovery and explicit command profile binding.

**Eighth phase:** [live command output](CODING_LIVE_OUTPUT.md) during existing
runner polling, with redacted cumulative snapshots and final-state protection.

**Ninth phase:** [request-local coding plans](CODING_PLANS.md), with bounded
steps, one active step, ordered updates and an Android checklist.

**Tenth phase:** [pinned/staged text search](CODING_SEARCH.md), with literal
matches, file paths, line numbers and explicit coverage/output limits.

**Remaining work:** end-to-end coding evaluation, image/runner provisioning and
interactive sessions. No runner has been enabled or engine deployed by these implementation
phases. Local tests were deferred at the owner's request; both workspace and
runner behavior still require verification before activation.

## Settings (verified from code)

| Variable | Default | Purpose |
| --- | --- | --- |
| `AGENT_MODEL` | same as `VERTEX_MODEL` (`gemini-3.8-flash`) | Model for the Agent loop / ADK merger only. Falls back once to `VERTEX_MODEL` on 404/403. |
| `AGENT_THINKING_LEVEL` | `low` when `AGENT_MODEL` differs from `VERTEX_MODEL`, else `VERTEX_THINKING_LEVEL` | `low`, `medium` or `high` for the Agent model. |
| `AGENT_ENGINE` | unset (tool loop) | `adk` turns on ADK parallel mode. |
| `GEMINI_AUTH` | `adc` | `apikey` sends chat, helpers, search grounding and the Agent (tool loop and every ADK Gemini model) to Vertex AI express mode with `GOOGLE_API_KEY` (no project/location; the key's own project is billed and must have the model, e.g. `gemini-4-argon`). Imagine and document/notes embeddings stay on ADC. `apikey` without a key logs one warning and keeps ADC. Rollback: `GEMINI_AUTH=adc`. |
| `GOOGLE_API_KEY` | unset | Google Cloud API key (restricted to the Gemini API) for `GEMINI_AUTH=apikey`. Secret Manager only, never a plain env var: `--update-secrets GOOGLE_API_KEY=dua-gemini-key:latest` (service account needs `roles/secretmanager.secretAccessor`). Remove with `--remove-secrets GOOGLE_API_KEY`. |
| `AGENT_SUB_STEPS` | `5` | Model calls per ADK helper. |
| `AGENT_ADK_SECONDS` | `300` | Total time limit for the ADK path. `AGENT_CI_SECONDS` is added on top only when the request actually uses the PR flow (`AGENT_TEST_CODE=1` and a PR/CI tool ran); the limit grows at that moment, mid-run. The deadline log line prints the real limit. Logs also show `adk plan Xs`, `adk helpers Xs`, `adk merge Xs`. |
| `AGENT_SEARCH_SECONDS` | `90` | Deadline for one `google_search` call (clamped 10–300; invalid value = 90). |
| `ADK_GEMINI_RETRY` | `1` | On by default: the default Gemini model retries 429 `RESOURCE_EXHAUSTED`/5xx with exponential backoff + jitter (1 call + 3 retries, ~2 s, 4 s, 8 s; worst case ~17 s, still inside `AGENT_ADK_SECONDS`) before ADK gives up and falls back. `ADK_MODELS_JSON` Gemini routes keep their short 2-attempt retry. Rollback: `0` (plain model name, no retry). |
| `ADK_HELPERS_SEQUENTIAL` | `1` | `1` runs ADK helpers one after another so they do not burst the shared per-minute Vertex quota (fewer 429s; slower but same answer). Rollback: `0` = old parallel helpers. |
| `AGENT_VERIFY` | `0` | `1` turns on the self-check. |
| `AGENT_VERIFY_SECONDS` | `45` | Critic time limit. |
| `DUA_AGENT_REVIEW` | `0` | `1` adds the ADK reviewer step (one review, at most one revision). |
| `DUA_AGENT_REVIEW_MODEL` | same as `VERTEX_MODEL` | Model for the reviewer and reviser. |
| `AGENT_TEST_CODE` | `0` | `1` turns on pre-checks + CI wait + one fix. |
| `AGENT_CI_SECONDS` | `420` | Total CI wait per request. |
| `AGENT_NOTES` | `1` | `0` turns Agent notes off. |
| `AGENT_MAX_STEPS` | `12` | Model calls per Agent request. |
| `AGENT_DAILY_TOKEN_LIMIT` | `0` (off) | Engine-side daily token cap per owner (IST calendar day). When >0, an owner who already used this many model tokens today gets a friendly Hinglish/English "limit reached" reply instead of an Agent run. Counts every observed model call in the request (router, tools loop, ADK helpers, notes learning). In-memory per Cloud Run instance: resets on restart/scale-out, so it is a soft guard, not billing. Invalid or negative = off. |
| `MAX_RUN_TOTAL_TOKENS` | `0` (off) | Cumulative token cap (input + output from model usage metadata) for ONE Agent run: every step of the tool loop, or the ADK controller + helpers + merger/reviewer together. When reached, the run stops cleanly and answers with what was done so far plus a short "token limit reached" note (no error). Missing usage metadata counts as 0. Invalid or negative = off. |
| `AGENT_MAX_SECONDS` | `600` | Total Agent time limit. |
| `AGENT_STEP_SECONDS` | `150` | Deadline per model step. |
| `AGENT_TOOL_SECONDS` | `45` | Default deadline per tool (search/code 90 s, image 120 s). |
| `AGENT_STEP_TOKENS` | `16384` (`32768` for `gemini-4*` models) | Output cap per step, thinking included. |
| `AGENT_SUB_TOKENS`, `AGENT_ANSWER_TOKENS`, `AGENT_ROUTER_TOKENS` | `3072`, `4096`, `256` | Output caps for sub-calls, the final answer and the router. |
| `GITHUB_TOKEN`, `DUA_REPO` | unset, `KRFbrothers/Dua-ai` | Optional coding agent token and its single target repository. |
| `DUA_CODING_RUNNER_URL`, `DUA_CODING_RUNNER_TOKEN` | unset | Optional separate HTTPS coding runner origin and dedicated secret. No engine host-shell fallback. See [setup and limits](CODING_RUNNER.md). |
| `DUA_CODING_CHECKPOINTS` | `0` | `1` enables private SQL draft autosave/list/restore after the explicit init-db step. No command replay. See [checkpoints](CODING_CHECKPOINTS.md). |

## Agent notes vs phone Memory

| | Agent notes | Phone Personal Memory |
| --- | --- | --- |
| Where | Supabase table `agent_notes` ([migration](../infra/database/migrations/202610160001_dua_agent_notes.sql)), owner-only RLS | Phone cache + owner cloud snapshot (`dua_cloud_snapshots`) |
| Who writes | The Agent: after a reply it may save 0–3 short lessons; tools `save_note`, `search_notes`, `delete_note` | You, in Settings → Memory (user-approved) |
| Limits | 200 notes per owner (oldest pruned), up to 20 relevant notes loaded per task; anything that looks like a secret, token, key or email is dropped | 10 notes of 200 characters ([SETTINGS_DEVICE_CHECK.md](SETTINGS_DEVICE_CHECK.md)) |
| Turn off | `AGENT_NOTES=0` | Memory switch in Settings |

Notes use the signed-in owner's token plus the public anon key; no service-role key. `GET /api/agent/notes/status` (owner-only) reports `{"enabled","ready","reason"}` without note data. The daily `dua-backup` job also exports `agent_notes` ([OPERATIONS.md](OPERATIONS.md#daily-backup-job-dua-backup)).

## GitHub token permissions

The engine's `GITHUB_TOKEN` (Secret Manager; fine-grained, only the selected repository) needs:

- **Contents: Read and write** and **Pull requests: Read and write** — to create `dua-agent/` branches, commits and PRs.
- **Actions: Read-only** (and **Checks: Read-only** if offered) — for `AGENT_TEST_CODE` CI polling and `check_pr_ci`. Without it the Agent says it cannot read CI results.
- Nothing else: the agent never merges, pushes to main, force-pushes or edits `.github/` workflows.

This token is separate from dua-api's `GITHUB_CONNECTOR_TOKEN` (read-only) and `GITHUB_BUILD_WRITE_TOKEN`; see [GITHUB_VALIDATION.md](GITHUB_VALIDATION.md).

## Agent Files (Android)

The AI Agent page has a **Files** card. It opens a page that lists, adds and deletes the owner's documents through the engine's `/api/agent/documents` (same engine URL, app token and sign-in as chat). Only UTF-8 text files work (TXT, MD, CSV, JSON and similar), up to 100,000 characters (`MAX_DOCUMENT_CHARS`). PDFs are not supported yet because the app has no PDF text extractor. When the engine has no `ADK_EMBEDDING_MODEL` (or no Agent database), the page shows a friendly "Files aren't switched on" message instead of an error. Agent then finds these files with its `search_documents` tool.

## Tool availability (audit fixes)
- Optional [paired desktop/browser/Android control and interactive terminal sessions](DEVICE_AUTOMATION.md) use a separate companion and existing owner approvals. Tools stay hidden without companion configuration; no host was paired or deployed by the implementation.
- Runner tools (`list_runner_profiles`, `run_command`, `import_command_changes`) are only declared, in the tool loop and the ADK code helper, when `coding_terminal.status()` reports `configured`. Checkpoint tools (`checkpoint_workspace`, `list_workspaces`, `restore_workspace`) only when `coding_checkpoints.enabled()`.
- ADK has a small `actions` helper (`generate_image`, `remember`, `recall`, `current_time`) so a split request does not lose image/Memory parts. It only joins an already-split plan; it never turns the ADK path on by itself.
- The tool loop gets `search_documents` when `agent_documents.enabled()` (ADK research helper already had it).

