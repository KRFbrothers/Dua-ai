# Dua Agent (dua-ai-engine)

The Agent is the tool-using mode of the Cloud Run engine (`google-cloud-backend/`). This page summarises how it runs and which settings control it. Implementation details and setup steps live in the [engine README](../google-cloud-backend/README.md#dua-agent-tools-general-tool-loop). Change settings only with `--update-env-vars` ([OPERATIONS.md](OPERATIONS.md#changing-settings-safely)).

The approved ADK target is recorded in [ADK_SCOPE.md](ADK_SCOPE.md): Fareed's
unchanged six-part checklist, current integration gaps and official GitHub sources.
The integration and live-configuration boundaries are documented in
[ADK_IMPLEMENTATION.md](ADK_IMPLEMENTATION.md).

## Two ways to run: tool loop vs ADK

| Mode | When | How |
| --- | --- | --- |
| Tool loop (default) | `AGENT_ENGINE` unset | [`agent_tools.py`](../google-cloud-backend/agent_tools.py): one model (`AGENT_MODEL`) calls tools step by step (search, code, notes, image, GitHub proposals) within step/time/tool caps. |
| ADK parallel mode | `AGENT_ENGINE=adk` | [`agent_adk.py`](../google-cloud-backend/agent_adk.py) (Google ADK, imported only when on): a controller plans 2–3 independent parts, helpers on `VERTEX_MODEL` run in parallel through the same tools, a merger on `AGENT_MODEL` writes the answer. |

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

- **Self-check** (`AGENT_VERIFY=1`, [`agent_verify.py`](../google-cloud-backend/agent_verify.py)): a cheap critic call compares the draft with the request and sources; only if it finds issues, one revision. Failures keep the draft; a revision that drops a PR link is rejected. Casual replies and coding mode skip it.
- **ADK reviewer** (`DUA_AGENT_REVIEW=1`, ADK path only, [`agent_adk.py`](../google-cloud-backend/agent_adk.py)): after the merger, a `reviewer` LlmAgent (cheap Flash model) returns a JSON verdict (`ok`, `needs_fix`, `needs_approval`). Only `needs_fix` runs the `reviser` agent, at most once. Live streaming waits for the review; any bad verdict, or a revision that drops a PR link, keeps the draft. Pattern adapted from google/adk-samples llm-auditor.
- **Tested PRs** (`AGENT_TEST_CODE=1`, [`pr_flow.py`](../google-cloud-backend/pr_flow.py)): proposed Python files are byte-compiled and JSON parsed before a PR opens; after the PR opens, the engine polls GitHub CI and allows **one** fix commit on the same `dua-agent/` branch (never main, never force, never merge). The `check_pr_ci` tool reads the agent's own PR status.

See the [new ADK configuration and operations guide](ADK_IMPLEMENTATION.md) for
SQL persistence, embeddings, provider routes, tools, telemetry and CLI settings.

## Coding workspace: first implementation phase

Coding tools now support a request-local text workspace on a fixed `main` commit.
Both the normal tool loop and the ADK Code helper use the same implementation in
[`coding_workspace.py`](../google-cloud-backend/coding_workspace.py), the existing
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

**Remaining phases:** app terminal/diff/checkpoint review, richer CI failure
evidence and end-to-end coding evaluation. No runner has been enabled or engine deployed by these implementation
phases. Local tests were deferred at the owner's request; both workspace and
runner behavior still require verification before activation.

## Settings (verified from code)

| Variable | Default | Purpose |
| --- | --- | --- |
| `AGENT_MODEL` | same as `VERTEX_MODEL` (`gemini-3.8-flash`) | Model for the Agent loop / ADK merger only. Falls back once to `VERTEX_MODEL` on 404/403. |
| `AGENT_THINKING_LEVEL` | `low` when `AGENT_MODEL` differs from `VERTEX_MODEL`, else `VERTEX_THINKING_LEVEL` | `low`, `medium` or `high` for the Agent model. |
| `AGENT_ENGINE` | unset (tool loop) | `adk` turns on ADK parallel mode. |
| `AGENT_SUB_STEPS` | `5` | Model calls per ADK helper. |
| `AGENT_ADK_SECONDS` | `300` | Total time limit for the ADK path. `AGENT_CI_SECONDS` is added on top only when the request actually uses the PR flow (`AGENT_TEST_CODE=1` and a PR/CI tool ran); the limit grows at that moment, mid-run. The deadline log line prints the real limit. Logs also show `adk plan Xs`, `adk helpers Xs`, `adk merge Xs`. |
| `AGENT_SEARCH_SECONDS` | `90` | Deadline for one `google_search` call (clamped 10–300; invalid value = 90). |
| `ADK_GEMINI_RETRY` | `0` | `1` gives the default Gemini model a short retry (2 attempts, 0.5–2 s backoff, on 429/5xx), same as `ADK_MODELS_JSON` Gemini routes. Off = plain model name (rollback: unset). |
| `AGENT_VERIFY` | `0` | `1` turns on the self-check. |
| `AGENT_VERIFY_SECONDS` | `45` | Critic time limit. |
| `DUA_AGENT_REVIEW` | `0` | `1` adds the ADK reviewer step (one review, at most one revision). |
| `DUA_AGENT_REVIEW_MODEL` | same as `VERTEX_MODEL` | Model for the reviewer and reviser. |
| `AGENT_TEST_CODE` | `0` | `1` turns on pre-checks + CI wait + one fix. |
| `AGENT_CI_SECONDS` | `420` | Total CI wait per request. |
| `AGENT_NOTES` | `1` | `0` turns Agent notes off. |
| `AGENT_MAX_STEPS` | `12` | Model calls per Agent request. |
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
| Where | Supabase table `agent_notes` ([migration](../supabase/migrations/202610160001_dua_agent_notes.sql)), owner-only RLS | Phone cache + owner cloud snapshot (`dua_cloud_snapshots`) |
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
