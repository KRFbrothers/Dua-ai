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
cd apps/ai-engine
python adk_cli.py init-db
python adk_cli.py status
```

The official SDK owns its session/event tables; Dua adds approvals, leases,
coding workspaces, documents and vector chunks in the same private schema.
`init-db` changes database objects and permissions; it is a separate setup step,
not a read-only readiness probe. No user migration or cloud command is executed
by routine tests.

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

### PostgreSQL readiness checklist before promote

Audit finding #5 is a deployment check. This checklist documents how to collect
evidence; it does not certify the owner's live database. Keep the candidate at
0% traffic until these checks and the intended workflow have been verified.

1. **Check the candidate's settings.** `ADK_SESSION_DB_URL` must be bound from
   Secret Manager to the intended shared PostgreSQL database and dedicated role,
   using a direct/session connection with verified TLS. All participating
   revisions/instances must use that same database/schema. The staged rollout
   sets `AGENT_ENGINE=adk` and `ADK_PRODUCTION=1`; it preserves other bindings
   but does not create the DB binding or initialize tables. Retain the existing
   owner Auth settings listed in the [engine README](../apps/ai-engine/README.md#settings-environment-variables-only-no-secrets-in-code).
   A successful startup without the DB URL still permits instance-memory
   approvals; startup/health alone is insufficient.
2. **Confirm preparation.** The private `dua_adk` schema must contain
   `dua_agent_approvals`, `dua_agent_leases`, `dua_agent_documents`,
   `dua_agent_chunks` and the pinned SDK's session tables (including `sessions`
   and `events`). Initialization also creates `dua_coding_workspaces`; check it
   if workspace checkpoints are used. pgvector's `extensions.vector` type and
   schema permissions are required by `init-db` even when embeddings are off.
   Have the administrator confirm runtime table privileges and the approval
   columns/primary key/unique `request_key` against
   [the owner module](../apps/ai-engine/dua_engine/agent/agent_store.py).
   Keep the schema private and unrelated application tables inaccessible.
3. **Run the read-only commands in Cloud Shell.** Use the current checkout and
   its installed engine dependencies. Cloud Run bindings are not automatically
   exported into Cloud Shell: securely supply the same `ADK_SESSION_DB_URL` for
   local diagnostics, and `DUA_OWNER_ACCESS_TOKEN` plus `DUA_APP_TOKEN` if the
   service requires it for remote diagnostics. Do not print credentials or pass
   them as command arguments. Set `ADK_CANDIDATE_ORIGIN` to the HTTPS origin of
   the staged `adk-candidate` tag, not the serving service URL.

   ```bash
   # From the repository root; plan prints revision/traffic, not env values.
   bash apps/ai-engine/deploy-adk.sh plan
   cd apps/ai-engine
   python adk_cli.py status
   python adk_cli.py status --endpoint "$ADK_CANDIDATE_ORIGIN"
   ```

   Both storage results must show `configured: true`, `ready: true`,
   `reason: "ok"` and `durableAcrossInstances: true`. On the candidate also check
   `adkEnabled: true` and `build` against the intended source SHA; record the
   exact tagged revision for `ADK_VERIFIED_REVISION`. Any missing/false field,
   `not_configured`, `migration_required`, `unavailable` or nonzero command exit
   blocks promotion. `durableAcrossInstances: true` alone only identifies a
   connected PostgreSQL backend; `ready` only checks six visible table names.
4. **Check private-schema metadata through the application's connection.**
   From that same `apps/ai-engine/` shell, this reads metadata only and prints no
   DB URL, owner IDs or approval payloads:

   ```bash
   python - <<'PY'
   import json
   import agent_store
   from sqlalchemy import inspect, text
   db = None
   try:
       db = agent_store.engine()
       with db.connect() as connection:
           connection.execute(text("SET TRANSACTION READ ONLY"))
           row = connection.execute(text("SELECT current_schema(), current_setting('search_path'), to_regtype('extensions.vector') IS NOT NULL")).one()
           print(json.dumps({"schema": row[0], "searchPath": row[1],
                             "vectorTypePresent": row[2],
                             "tables": inspect(connection).get_table_names(schema="dua_adk")}))
   except Exception:
       raise SystemExit("Metadata check failed; check server configuration securely.") from None
   finally:
       if db is not None:
           db.dispose()
   PY
   ```

   Require `schema: "dua_adk"`, search path `dua_adk, extensions`, the expected
   tables above and `vectorTypePresent: true`. This checks the synchronous
   approval connection; it does not exercise the SDK's async session/resume
   path. Neither metadata probe proves column compatibility, write privileges,
   atomic claims across instances or live provider availability.
5. **Record the remaining evidence before promote.** Verify session/resume and
   cross-instance single-use approvals with synthetic data in isolated staging,
   using the same image/configuration. Those are separate write-capable checks,
   not commands to run against real pending approvals. Approve/reject consumes
   an action, and a claim does not prove the external write succeeded. Do not
   use `init-db`, indexing or approval actions as read-only probes. Keep the
   checked revision, build, secret versions (not values), diagnostic results and
   staging workflow result with the rollout record. CI's disposable PostgreSQL
   tests do not certify the live database; `liveProvidersVerified: false` in
   capabilities is deliberate.

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
Streaming usage snapshots count once; reported totals reconcile providers that
include reasoning in output tokens versus report it separately, preventing double
charging in estimates. Inconsistent totals leave cost unknown. Nested search/code calls, router and optional
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

The engine README is the entrypoint and deployment map:
[ADK entrypoints and ownership](../apps/ai-engine/README.md#adk-entrypoints-and-ownership)
and [supported staged ADK rollout](../apps/ai-engine/README.md#supported-adk-rollout).
The single server chain is Docker CMD → `adk_server.py` → Gunicorn `wsgi:app` →
the existing Flask app. Packaged `dua_engine.agent.agent_adk` runs inside that
server; `adk_cli.py` and `adk_examples/` do not replace it. Use current-checkout
`deploy-adk.sh` for ADK rollouts; `dua-adk-cloudshell.sh` is the retained historical
bootstrap pinned to an older checkout, with its own setup side effects.

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
bash apps/ai-engine/deploy-adk.sh plan
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
