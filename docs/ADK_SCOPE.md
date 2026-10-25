# Dua ADK: final scope and open-source implementation map

Fareed approved this scope on 2026-10-19. Keep all six categories and all 25
requirements below. Do not add, remove or replace requirements without his
instruction. This page records the target; it does not claim the entire target
is implemented. Work remains limited to ADK, not unrelated app/UI changes.

## Approved checklist (verbatim)

🧩 1. Core Architecture (Main Engine)
• Agent Core Runtime: agent ka dimaag, jo decide karta hai ki kab kya karna hai.
• Prompt Manager / Templates: prompts ko structure aur manage karne ke liye pehle se bane templates.
• Memory Management: short-term memory (chat history) aur long-term memory (vector database).
• RAG (Retrieval): apne documents ya data se sahi jaankari dhoondh kar jawab dena.
• State & Session Management: lamba kaam beech mein ruk jaaye toh wahi se dobara shuru karna.

⚙️ 2. Tooling & Integrations (Actions)
• Built-in Tools: files padhna aur likhna (File I/O), code chalana, calculations.
• API Connectors: Google Search, Weather API, database jaisi bahar ki services se baat karna.
• Custom Tool SDK: developer apna naya tool bana kar agent se jod sake.
• Error Handling & Retry: tool ya API fail ho toh dobara try karna ya koi aur raasta lena.

🧠 3. Model Connectors (LLM Gateway)
• LLM Providers Support: Gemini, OpenAI, Claude, ya local models (Llama, Mistral).
• Embedding Models: text ko vectors (numbers) mein badalna, semantic search ke liye.
• Streaming: jawab word by word aata dikhe.
• Model Fallback: ek model fail ho toh doosra model use karna.

🛡️ 4. Guardrails & Orchestration (Safety & Control)
• Multi-Agent Orchestrator: ek se zyada agents ke beech kaam aur baatcheet ko control karna.
• Guardrails & Safety Filters: galat, unsafe ya out-of-scope jawab rokna.
• Input/Output Validators: AI se aane wala data (JSON, schema) double-check karna.
• Human-in-the-Loop: bada kaam, jaise bhejna, delete karna ya paise kharchna, karne se pehle user ki manzoori lena.
• Security & Secrets: API keys safe rakhna, aur har tool ko sirf zaroori permission dena.

📊 5. Monitoring & Evaluation (Tracking)
• Logging & Tracing: agent ke steps, kaunsa tool call hua aur kitna time laga, sab ka log.
• Token & Cost Trackers: har request mein kitne tokens lage aur kitna kharcha hua.
• Evaluation Metrics: jawab kitne sahi hain, ye benchmark se test karna.
• Automated Testing: fake (mock) tools ke saath tests, taaki har badlav ke baad kuch tootna na ho.

🚀 6. CLI & Deployment (Shipping)
• Command Line Interface (CLI): terminal se agent banana, chalana aur test karna.
• Deployment Configuration: Docker aur cloud (GCP, AWS, Supabase) ke templates.
• Versioning & Rollback: naya version kharab nikle toh purane version par wapas jaana.

## Source and integration rules

Use the official [google/adk-python](https://github.com/google/adk-python/tree/v2.12.0)
SDK already installed by [engine requirements](../apps/ai-engine/requirements.txt).
The inspected release is **v2.12.0**, tree
`38381f65f2bee0f70a89f938ee64e51b34916fa7`, under its
[Apache-2.0 license](https://github.com/google/adk-python/blob/v2.12.0/LICENSE).
The source paths below were checked against that release's GitHub tree. Runtime,
resumability, retrieval, model-adapter and deployment source was also inspected
where it affects these decisions. Source availability is not integration proof.

Use the SDK APIs and focused official examples, retaining required notices for
any copied material. Add only dependencies with actual callers. Do not vendor
another copy of the entire ADK, introduce a competing runtime, or install every
optional backend to make this checklist appear complete. The integrations below use those upstream APIs through Dua adapters and are
exercised with offline models. Hosted-provider acceptance remains separate.
Open-source code does not make hosted model calls, embeddings or storage free.

## Implementation status and acceptance criteria

This map describes the implementation in PR [#257](https://github.com/KRFbrothers/Dua-ai/pull/257).
All 25 requirements remain unchanged. **Implemented; offline verified** means
exercised code with the pinned SDK/scripted providers, not a production deployment.
**Configuration required / live acceptance pending** means code exists but operator
credentials, database readiness or real-provider checks remain outstanding. See
[ADK_IMPLEMENTATION.md](ADK_IMPLEMENTATION.md) for exact configuration, commands,
limits and verification boundaries, and [TESTING.md](TESTING.md) for checks.

### 1. Core Architecture

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| Agent Core Runtime | Implemented; offline verified | Official ADK Runner, bounded planner, helpers and merger; cancellation/deadlines/cleanup remain covered. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/runners.py) |
| Prompt Manager / Templates | Implemented; offline verified | Existing role templates plus agent_context preserve bounded owner instructions, multimodal history and isolated helper tool history. Strict planner schema rejects incomplete values. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/llm_agent.py) |
| Memory Management | Implemented; configuration required | Durable SQL sessions plus pgvector semantic notes. Existing Supabase notes remain authoritative; explicit bounded reindex migrates historical notes. No hosted embeddings were called. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/sessions/database_session_service.py) |
| RAG (Retrieval) | Implemented; configuration required | Owner-only text ingestion/chunking, native pgvector search, private document citations, replacement and deletion. SQLite exact search is a local test fallback. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools/retrieval) |
| State & Session Management | Implemented; offline verified | Official SQL session persistence/resume, owner leases, stored input restoration, completed-task rejection and durable single-use approvals. Upstream resumability is experimental; arbitrary external writes are not exactly-once. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/apps/_configs.py) |

### 2. Tooling & Integrations

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| Built-in Tools | Implemented; offline verified | ADK exposes search, calculator, time, sandboxed Python, GitHub file reads, notes and approval-gated write proposals. No unrestricted host filesystem or shell tool. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) |
| API Connectors | Implemented for configured services | Existing Google Search, GitHub and Supabase connectors share bounded dispatch; custom SDK extends reviewed APIs. Weather is an example of an optional external service, not a newly configured paid endpoint. Live API credentials/permissions require operator verification. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools) |
| Custom Tool SDK | Implemented; offline verified | agent_toolkit provides typed registration, per-tool quotas, bounded JSON responses and source/revision-bound write approval. The installed word_count example runs through the real SDK. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) |
| Error Handling & Retry | Implemented; offline verified | Read-only classified transient retries/backoff, cancellation and bounded native Gemini retries. Unknown, timeout and write outcomes are never blindly replayed. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/google_llm.py) |

### 3. Model Connectors

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| LLM Providers Support | Implemented; live acceptance pending | Gemini plus official LiteLlm OpenAI, Anthropic, OpenRouter and opt-in local routes. Explicit server configuration; offline adapter contracts tested. No claim of live provider/model availability or quality. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/lite_llm.py) |
| Embedding Models | Implemented; live acceptance pending | Explicit Vertex embedding model/dimensions with vector validation, model-space identity and owner isolation. Native pgvector integration tested on disposable PostgreSQL. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools/retrieval) |
| Streaming | Implemented; offline verified | Merger token deltas reach existing SSE text events without duplicate final aggregates; Stop/disconnect cancel work. Critic-enabled runs intentionally buffer until verification. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/run_config.py) |
| Model Fallback | Implemented; offline verified | Bounded server-configured official FallbackModel chain; no switch after emitted response. Failed attempts retain unknown usage; no action replay or silent provider-route override. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/_fallback_model.py) |

### 4. Guardrails & Orchestration

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| Multi-Agent Orchestrator | Implemented; offline verified | Independent helper histories, shared run budget, per-helper limits and explicit incomplete results. Single extension requests can also use ADK directly. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/agents) |
| Guardrails & Safety Filters | Implemented; offline verified | Existing policy/authorization plus fenced custom retrieval and buffered/incremental secret filtering. Adversarial fixtures exercise boundaries; no filter guarantees all advice safe or accurate. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/llm_agent.py) |
| Input/Output Validators | Implemented; offline verified | Strict planner schemas, actionable proposal pairs, custom typed JSON arguments/results, vector dimensions and owner-only API payload validation. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) |
| Human-in-the-Loop | Implemented; offline verified | SQL approvals survive restart, expire, bind owner/tool revision and are atomically claimed once before execution. Automatic note learning is separate existing behavior, not universally approval-gated. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/tool_context.py) |
| Security & Secrets | Implemented in code; live IAM pending | Server-only keys/routes, private SQL schema/role, verified TLS for hosted DB, owner checks, safe errors and content-free traces. Production IAM, secret bindings and data retention were not inspected or changed. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/auth) |

### 5. Monitoring & Evaluation

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| Logging & Tracing | Implemented; offline verified | Request-correlated model/provider/tool spans, duration/status/retries and terminal usage events. SDK content capture disabled. No prompt bodies, tool arguments, credentials or private chain-of-thought in these traces. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/telemetry) |
| Token & Cost Trackers | Implemented; offline verified | Actual reported per-attempt usage, streamed-total deduplication, cached/thinking fields and explicit operator-verified pricing. Missing usage/prices remain unknown; estimates are not bills. SDK internal HTTP retry billing remains provider-authoritative. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/llm_response.py) |
| Evaluation Metrics | Implemented harness; live quality unmeasured | Versioned six-case reference dataset and JSONL scorer for answers, retrieval citations, tools, approval and boundaries; missing/duplicate cases fail. This starter deterministic rubric is not a comprehensive semantic quality judge. No live answer-accuracy score is claimed. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/evaluation) |
| Automated Testing | Implemented; offline verified | Minimal dependency mocks plus real pinned SDK, SQLite and disposable PostgreSQL/pgvector tests. New coverage exercises context, streaming, adapters, recovery, approval races, custom tools, telemetry, RAG and rollback. See TESTING.md; regression counts are not quality benchmarks. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/tests/unittests) |

### 6. CLI & Deployment

| Requirement | Current state | Dua evidence / remaining acceptance | Official GitHub source |
| --- | --- | --- | --- |
| Command Line Interface (CLI) | Implemented; offline verified | Dua CLI status/run/resume/index/delete/reindex/scaffold/test/evaluate commands; scaffold creates an official ADK package without overwriting. Live commands are explicit authenticated operations. | [Upstream](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/cli/cli.py) |
| Deployment Configuration | Implemented for Docker + GCP; undeployed | Pinned nonroot Docker image, dependency locks, startup/health checks, Cloud Build candidate revision and existing Supabase data/auth boundary. AWS was not selected or provisioned; hosted targets are not interchangeable. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/cli/deployers) |
| Versioning & Rollback | Implemented; cloud acceptance pending | Build SHA and schema version, clean merged-main deployment, no-traffic candidate, explicit verified promotion and target-checked prior traffic restoration. Mocked rollback tests pass; no production revision or data migration was rolled back. | [Upstream](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/cli/deployers) |

## Delivery boundary

No live cloud, model, embedding, storage, IAM or billing settings were changed by
this implementation. Production acceptance requires an initialized private
PostgreSQL/pgvector store, reviewed secret bindings, selected model/provider smoke
checks and a no-traffic candidate verification before promotion. Keep unknown
usage/cost and unmeasured live quality explicitly unknown. Fareed reviews and
merges the PR; saving this code does not deploy it.
