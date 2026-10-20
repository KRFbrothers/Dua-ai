# Dua ADK implementation and operating guide

This implements the unchanged [six-category, 25-requirement scope](ADK_SCOPE.md).
The Google ADK runtime remains the foundation; no second agent framework or UI
redesign is introduced. Code, offline verification and live deployment are
separate states. This change does not activate hosted models or modify cloud
settings by itself.

## Open-source components actually used

- [Google ADK Python v2.12.0](https://github.com/google/adk-python/tree/v2.12.0):
  `Runner.run_async`, `App`/resumability, `DatabaseSessionService`, typed function
  tools, Gemini/LiteLlm adapters, streaming and `FallbackModel`.
- [SQLAlchemy](https://github.com/sqlalchemy/sqlalchemy), asyncpg and Psycopg:
  the official SDK's durable sessions plus atomic approvals and task leases.
- [pgvector](https://github.com/pgvector/pgvector) and its
  [SQLAlchemy integration](https://github.com/pgvector/pgvector-python): native
  PostgreSQL vector storage/cosine retrieval. SQLite has a deterministic exact
  fallback for local/offline tests; Cloud Run temporary disk is not durable.
- [LiteLLM](https://github.com/BerriAI/litellm): used through the official ADK
  adapter for explicitly configured OpenAI/Claude/OpenRouter/local routes.

Dependencies are package-managed, with no duplicated vendor source tree.
`requirements-lock.txt` is the tested Gemini/PostgreSQL dependency closure;
`requirements-adapters-lock.txt` adds the actual provider adapter. Both target
Python 3.12/Linux. The container defaults to the adapter lock; a Gemini-only image
can select `--build-arg REQUIREMENTS=requirements-lock.txt`. New lock versions
must be compatibility-tested, not refreshed just for their date.

## Runtime and context

The planner, helpers and merger all receive validated owner system instructions,
conversation and multimodal parts. Context is bounded to the newest 24 turns and
96,000 text characters, with an omission marker; originals are not mutated.
Helpers retain their own tool histories rather than reading another helper's
concurrent intermediate messages. Notes are fenced as untrusted data.

Research now includes the existing provider-sandbox Python execution and Memory
recall. Indexed documents and configured custom tools are available to research;
a single-part extension request can use the ADK directly instead of being sent
to a loop without those tools. With only one ordinary helper kind available,
the needless controller call is skipped. Ordinary coding-mode behavior is
preserved.

ADK merger answer deltas use the existing SSE `text` events. Final aggregate
responses do not repeat previously emitted text. Secret filtering holds only
possible key prefixes across chunk boundaries; ordinary words stream immediately.
When `AGENT_VERIFY=1`, the draft remains buffered until the critic finishes, so
users never receive a draft followed by a contradictory replacement. Errors after
streaming append a terminal notice instead of falling back and replaying work.

## Durable sessions and approvals

Set `ADK_SESSION_DB_URL` through a server secret binding. It is not an HTTP request
parameter. Production uses a dedicated PostgreSQL role, with TLS verification,
that owns only private ADK objects. Local SQLite is supported for tests; the
container refuses SQLite persistence in Cloud Run/production.

An administrator must prepare a private schema and pgvector before `init-db`:

```sql
-- Use the intended dedicated role name, not an application login/JWT role.
-- Enable pgvector through the database administrator or Supabase extensions UI.
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA extensions;
CREATE SCHEMA IF NOT EXISTS dua_adk AUTHORIZATION dua_adk_runtime;
REVOKE ALL ON SCHEMA dua_adk FROM PUBLIC;
GRANT USAGE ON SCHEMA extensions TO dua_adk_runtime;
```

Do not grant the service role access to unrelated application tables or expose
`dua_adk` through PostgREST. `dua_adk_runtime` must already be created with its
password entered securely by the administrator; this guide contains no password.
If pgvector already exists in another schema, arrange its location/permissions
with the administrator rather than running a destructive extension reinstall.
Use a direct/session PostgreSQL connection supported by asyncpg and Psycopg;
transaction-pooler settings are not certified by these tests.

With the server secret available in the command's environment, run:

```bash
cd google-cloud-backend
python adk_cli.py init-db
python adk_cli.py status
```

The official SDK owns its session/event tables; Dua adds approvals, leases,
documents and vector chunks in the same private schema. Initialization is
additive. No user migration or cloud command is executed by routine tests.

Each durable pipeline emits an `agent_session` SSE event containing `runId`.
Resume with the owner-authenticated existing chat endpoint:

```json
{"resumeRun":"<run UUID>"}
```

The server restores its saved input, plan and SDK invocation. Caller replacements
for messages, owner IDs, tools or model configuration are ignored. A completed
task returns 409 without rerunning it. A task lease blocks simultaneous resume;
owner IDs scope every load. SDK resumability is experimental/best-effort in this
pinned release, not a guarantee of exactly-once arbitrary external effects.

Approvals persist across process restarts. A stable task/tool/argument key
prevents duplicate queued writes during resume. An atomic claim makes an
approval single-use; other owners cannot consume it. Claimed/expired actions
are not retried automatically. A provider-side failure after a claim can be
uncertain; inspect the external action before requesting a new write.

## Vector memory and owned-document RAG

`ADK_EMBEDDING_MODEL` is deliberately unset by default: indexing/querying hosted
embeddings can cost money. Choose an available embedding model explicitly and
set `ADK_EMBEDDING_DIMENSIONS` (default 768). Availability and pricing require a
live check; no future-free claim is made. Model and dimensions form the vector
space identity, so incompatible indexes are not mixed.

Owner-only endpoints:

| Endpoint | Request/result |
| --- | --- |
| `GET /api/agent/documents` | List this owner's document/note index metadata |
| `POST /api/agent/documents` | `{name,text,id?}` indexes extracted text; optional owned ID replaces its chunks atomically |
| `POST /api/agent/documents` | `{query}` returns the closest owned document chunks |
| `DELETE /api/agent/documents` | `{id}` removes owned metadata and vector/text chunks |
| `GET /api/agent/capabilities` | Read-only effective configuration/storage readiness/build; no keys or private content |

Documents are limited to 100,000 characters each, 50 per owner. Text is chunked
at 1,000 characters with 150 overlap; embedding batches are bounded. Retrieval
uses native pgvector cosine distance, owner and vector-space filters. Each result
has `[D:<document UUID>:<chunk>]` for citations. These are private document
references, not public download URLs. Approximate indexing and massive-corpus
scale are not claimed.

Notes saved while semantic memory is configured are indexed too. Note search
can rank semantic matches but intersects them with the authoritative live
Supabase notes, preventing deleted/stale index entries from reappearing. Existing
notes use explicit `reindex-notes --endpoint <origin> --offset 0 --limit 20` batches; enabling the feature does not silently
embed all historical data. A failed index update/delete is reported separately,
not presented as fully successful. Lexical notes remain available if embeddings
fail. The notes migration/RLS and owner authentication remain in force.

## Model routes, retries and custom tools

`ADK_MODELS_JSON` is a server-only JSON object with `controller`, `helper` and
`merger` keys. Each role accepts 1–3 explicit names: `gemini-*`, `openai/*`,
`anthropic/*`, `openrouter/*`, `ollama/*`, `ollama_chat/*` or `vllm/*`.
Credentials/endpoints come from the provider's normal server SDK environment,
never request data or source code. Local routes additionally require
`ADK_ALLOW_LOCAL_MODELS=1`. Test the selected local model's actual tool/multimodal
support; an adapter does not make every local model vision-capable.

Configured provider routes bypass the Gemini routing call. Their ADK failures
cannot silently leak the request into the ordinary Gemini loop. Explicit search/
Python tools still use the existing Vertex-backed tool implementation; provider
selection for the agent does not change which external service a tool contacts.

Official `FallbackModel` tries backups only for its classified transient statuses,
not after the first streamed response. Gemini routes have bounded SDK retries;
LiteLLM's extra retry layer is disabled. Read tools retry classified transient
failures at most once, respecting timeouts, cancellation and usage limits.
Write tools, ambiguous timeouts and unknown failures are not blindly replayed.

Custom tools use `agent_toolkit.Tool`/`Registry` and official ADK typed function
tools. Enable only installed, reviewed modules with `ADK_TOOL_MODULES`; the actual
example is `adk_examples.text_tools`. Modules cannot replace reserved built-ins.
Arguments are strict typed JSON, outputs bounded, and tool limits/timeout/owner
approvals use the same server boundaries. Custom read output is fenced.
Custom writes carry a source/revision fingerprint and execute only after approval;
changed code rejects an old approval. Bump the explicit revision when imported
dependencies or configuration change write semantics. Custom tools should not
spawn independent background work that bypasses the supplied lifetime controls.

Guardrails comprise owner authentication, allowlisted tools/repositories/paths,
strict action validation, untrusted-data fencing, quotas, write approval, safe
errors and output secret filtering. They do not prove arbitrary model advice
correct or guarantee protection from every prompt injection. Provider safety
behavior and adversarial examples still need ongoing quality evaluation.

## Tracing, actual usage and quality evaluation

Each request gets a correlation ID. JSON traces record agent/provider/tool action,
duration, outcome and retries; prompts, tool arguments, credentials and thought
text are not logged by this tracing layer. SDK message-content capture is disabled
per run and in the container defaults. Existing user-facing thought/progress
behavior is separate from persistent content-free traces.

Every actual SDK delegate attempt is metered, including failed fallback models.
Streaming usage snapshots count once; nested search/code calls, router and optional
critic/learning calls are wrapped too. The SSE `usage` event reports observed
usage and completeness. Missing metadata and failed attempts remain unknown.
`ADK_MODEL_PRICES_JSON` can supply operator-verified USD/million-token rates with
`input`, `output` and `cached_input` per exact reported model ID. Costs are
explicit estimates, never billed amounts. No rates are invented or fetched
silently. Embedding endpoints that report only characters leave token cost
unknown. Normal SDK retries may hide attempt-level billing detail; consult
provider billing for charged totals.

The versioned `evals/adk-v1.json` dataset covers math, owned-document citations,
unsupported facts, approval, network boundaries and untrusted instructions.
Collect model answers/tool names separately as JSONL rows, then score them:

```bash
python adk_cli.py evaluate --responses responses.jsonl --output quality-report.json
```

Each response has `id`, `text`, `tools` and, for approval cases,
`approval_required`. Missing/duplicate/unknown cases cannot inflate the score.
The report includes case and rubric results. No live model/judge call happens
inside the scorer. Its deterministic reference checks are a starting benchmark,
not a comprehensive semantic safety grader or a claim of live accuracy.

## CLI, image and rollout

`python adk_cli.py --help` exposes `scaffold`, `run`, `test`, `init-db`, `status`,
`index`, `delete-document`, `reindex-notes` and `evaluate`. Scaffold creates a real discoverable
ADK package without overwriting an existing directory; `adk run <package>` then
uses the official SDK. Dua's `run` command calls the actual owner-only service,
including `--resume`. Set `DUA_OWNER_ACCESS_TOKEN` securely in the CLI environment;
redirects are blocked and secrets are never accepted as command arguments.
`run` and `index` are explicit live operations and can incur provider charges.

The Python 3.12 container uses pinned base/dependency versions, a non-root user,
validated startup and a local health check. Optional CA mounts support managed
build proxies without copying their certificates into image layers. No secret
files are copied into the build context/image.

```bash
bash google-cloud-backend/deploy-adk.sh plan
```

`plan` is read-only and prints serving revision/traffic, not environment values.
After merge, use a clean, fast-forwarded `main` and explicit `ADK_IMAGE`:

- `deploy` records prior traffic, builds the exact source revision and stages a
  tagged candidate at 0% traffic, preserving existing secret/env/IAM bindings.
- Verify candidate owner-only capabilities, initialized PostgreSQL/pgvector,
  secret versions and the intended live workflow. The diagnostic's
  `liveProvidersVerified:false` is deliberate; configured does not mean tested.
- `promote` requires the exact checked `ADK_VERIFIED_REVISION` and moves traffic.
- `rollback` validates snapshot target/revision percentages and restores recorded
  traffic, retaining data. Candidate tags are not an automatic data/schema rollback.

The scripts are prepared tooling, not authorization to deploy/merge or change
billing/IAM. Schema changes here are additive; pin secret versions and record
compatibility before rollout. Turning off `AGENT_ENGINE` switches execution mode
but is not a substitute for revision rollback.

## Verification boundary

The suite includes actual SDK async execution and persistent-session resume,
SQLite and disposable PostgreSQL/pgvector integration, concurrent approval claims,
owner isolation, real custom-tool dispatch, model adapters/fallback streaming
rules, secret-boundary handling, usage accounting, evaluation CLI and rollback
command tests. CI also builds/checks Android, Node and Website.

Live hosted model behavior, database setup in the owner's project, GCP IAM,
serving revision, provider prices, phone interaction and a real traffic rollback
remain unverified until their authorized live checks. No paid model call or cloud
configuration change is part of routine verification.
