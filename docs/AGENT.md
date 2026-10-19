# Dua Agent (dua-ai-engine)

The Agent is the tool-using mode of the Cloud Run engine (`google-cloud-backend/`). This page summarises how it runs and which settings control it. Implementation details and setup steps live in the [engine README](../google-cloud-backend/README.md#dua-agent-tools-general-tool-loop). Change settings only with `--update-env-vars` ([OPERATIONS.md](OPERATIONS.md#changing-settings-safely)).

The approved ADK target is recorded in [ADK_SCOPE.md](ADK_SCOPE.md): Fareed's
unchanged six-part checklist, current integration gaps and official GitHub sources.
Keep its pending requirements distinct from implemented behavior below.

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

ADK runs through the official production `run_async` API. Each planning/helper
run owns an ephemeral in-memory session, closes its event stream, and awaits
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

Code proposals require matching, non-empty path/content lists of strings. A
mismatch returns a repairable tool error before creating any approval; no file
is silently dropped. Complete proposals still use the existing owner approval
and repository validation rules.

**Rollback to the loop:** if the request does not split, or ADK fails before any tool ran, the normal tool loop answers. Coding-mode requests always use the loop. To turn ADK off, remove the flag:

```bash
gcloud run services update dua-ai-engine --region us-central1 --project global-healer-m6shk --remove-env-vars AGENT_ENGINE
```

After either path, optional add-ons run:

- **Self-check** (`AGENT_VERIFY=1`, [`agent_verify.py`](../google-cloud-backend/agent_verify.py)): a cheap critic call compares the draft with the request and sources; only if it finds issues, one revision. Failures keep the draft; a revision that drops a PR link is rejected. Casual replies and coding mode skip it.
- **Tested PRs** (`AGENT_TEST_CODE=1`, [`pr_flow.py`](../google-cloud-backend/pr_flow.py)): proposed Python files are byte-compiled and JSON parsed before a PR opens; after the PR opens, the engine polls GitHub CI and allows **one** fix commit on the same `dua-agent/` branch (never main, never force, never merge). The `check_pr_ci` tool reads the agent's own PR status.

## Settings (verified from code)

| Variable | Default | Purpose |
| --- | --- | --- |
| `AGENT_MODEL` | same as `VERTEX_MODEL` (`gemini-3.8-flash`) | Model for the Agent loop / ADK merger only. Falls back once to `VERTEX_MODEL` on 404/403. |
| `AGENT_THINKING_LEVEL` | `low` when `AGENT_MODEL` differs from `VERTEX_MODEL`, else `VERTEX_THINKING_LEVEL` | `low`, `medium` or `high` for the Agent model. |
| `AGENT_ENGINE` | unset (tool loop) | `adk` turns on ADK parallel mode. |
| `AGENT_SUB_STEPS` | `5` | Model calls per ADK helper. |
| `AGENT_ADK_SECONDS` | `300` | Total time limit for the ADK path. |
| `AGENT_VERIFY` | `0` | `1` turns on the self-check. |
| `AGENT_VERIFY_SECONDS` | `45` | Critic time limit. |
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
