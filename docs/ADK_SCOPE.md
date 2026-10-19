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
SDK already installed by [engine requirements](../google-cloud-backend/requirements.txt).
The inspected release is **v2.12.0**, tree
`38381f65f2bee0f70a89f938ee64e51b34916fa7`, under its
[Apache-2.0 license](https://github.com/google/adk-python/blob/v2.12.0/LICENSE).
The source paths below were checked against that release's GitHub tree. Runtime,
resumability, retrieval, model-adapter and deployment source was also inspected
where it affects these decisions. Source availability is not integration proof.

Use the SDK APIs and focused official examples, retaining required notices for
any copied material. Add only dependencies with actual callers. Do not vendor
another copy of the entire ADK, introduce a competing runtime, or install every
optional backend to make this checklist appear complete. The adapters listed
below remain candidates until exercised through Dua with appropriate tests.
Open-source code does not make hosted model calls, embeddings or storage free.

## Implementation status and acceptance criteria

Snapshot: Dua PR [#257](https://github.com/KRFbrothers/Dua-ai/pull/257), code head
`9ea489cd996da315a3f11e7c75c0ea2016d89c20`. The hardening is on that PR;
production deployment and the deployed ADK flag have not been inspected.
**Partial** means there is relevant Dua code but the complete approved requirement
still needs integration. **Pending** means the required Dua integration is absent.
**Implemented/tested** refers only to the stated code and offline behavior.

### 1. Core Architecture

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| Agent Core Runtime | Implemented/tested: planner, bounded parallel specialists and merger in [agent_adk.py](../google-cloud-backend/agent_adk.py). | [runners.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/runners.py) | Keep request cancellation, deadlines, cleanup and tool-loop fallback covered. Live deployment remains separate. |
| Prompt Manager / Templates | Partial: planner/helper/merger instructions exist in the ADK module. | [LlmAgent instructions](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/llm_agent.py) | A consistent prompt-template interface exercised by the runtime, including context isolation and missing-value checks. |
| Memory Management | Partial: ephemeral ADK sessions and owner-only Supabase notes; no integrated vector memory or durable multi-turn ADK history. | [sessions](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/sessions), [memory services](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/memory) | Durable owner-scoped chat context plus the approved vector-backed long-term memory, tested for retrieval, deletion and cross-owner isolation. Notes alone do not satisfy the vector requirement. |
| RAG (Retrieval) | Partial: web and note retrieval; no complete owned-document ingestion/retrieval pipeline. | [retrieval tools](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools/retrieval) | Ingest and index owned documents, retrieve relevant chunks and cite them; deletion must remove searchable content. Choose the actual storage/embedding backend during this integration. |
| State & Session Management | Partial: current sessions are temporary; no durable interrupted-task resume. | [database_session_service.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/sessions/database_session_service.py), [ResumabilityConfig](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/apps/_configs.py) | Restart and resume an owned task from persisted state; retain approvals and prevent duplicate writes. Upstream resumability is experimental and best-effort/at-least-once, requiring idempotent tools. |

### 2. Tooling & Integrations

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| Built-in Tools | Partial: ADK exposes web, calculator, time, GitHub file reads and approval-gated file proposals. Python execution exists in the ordinary loop, not the ADK tool list. | [FunctionTool](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) | Exercise read/write/execute/calculate through ADK with existing authorization, sandbox and timeout boundaries. File writes must remain approved proposals. |
| API Connectors | Partial: Google Search, GitHub and Supabase adapters; no dedicated weather connector. | [tools](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools) | Stable connector contracts and tests for auth failures, malformed responses, timeouts and the external services required by actual tasks. |
| Custom Tool SDK | Partial: Python functions are wrapped as ADK FunctionTools in make_tools. | [BaseTool](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/base_tool.py), [FunctionTool](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) | A documented, exercised way to register a custom tool with schema validation, cancellation, ownership and per-tool limits. |
| Error Handling & Retry | Partial: bounded calls, repairable tool errors and fallback before side effects; no unified transient-error retry policy. | [reflect_retry_tool_plugin.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/plugins/reflect_retry_tool_plugin.py) | Bounded transient retries/backoff, cancellation and an honest terminal error. Model repair and transport retries are different. Never blindly retry non-idempotent writes. |

### 3. Model Connectors

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| LLM Providers Support | Partial: ADK currently uses Gemini/Vertex. Other app chat providers do not establish ADK adapter support. | [LiteLlm adapter](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/lite_llm.py), [model registry](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/registry.py) | Exercise the approved Gemini/OpenAI/Claude/local-model routes through a common ADK contract using offline fixtures first. Install optional adapters only with callers; do not route private data to an unintended endpoint. |
| Embedding Models | Pending: no Dua ADK embedding/indexing pipeline. | [retrieval interfaces](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/tools/retrieval) | An actual embedding provider and vector store integrated with ingestion/query/deletion, consistent dimensions/versioning and owner isolation. ADK's retrieval API is not itself an embedding model. |
| Streaming | Partial: runtime consumes async SDK events and forwards progress; final visible author text is accumulated, not emitted as token deltas. | [RunConfig](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/run_config.py), [runners.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/runners.py) | Incremental user-visible answer text through the existing API, with ordered deltas, no repeated text and Stop/disconnect cleanup. Progress events alone are not answer streaming. |
| Model Fallback | Partial: the ordinary engine has fallback behavior; the ADK path has no configured multi-provider fallback chain. | [official fallback sample](https://github.com/google/adk-python/tree/v2.12.0/contributing/samples/models/litellm_with_fallback_models) | Configured bounded fallback with error classification, remaining request budget and no replay of completed actions. Upstream FallbackModel is experimental in this release. |

### 4. Guardrails & Orchestration

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| Multi-Agent Orchestrator | Implemented/tested for independent research/code/notes work with a merger and per-helper limits. | [agent implementations](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/agents) | Retain task isolation, independent budgets, cancellation and explicit incomplete-helper results. Installed orchestration APIs have deprecation warnings; do not migrate without testing. |
| Guardrails & Safety Filters | Partial: agent_safety.py, owner checks, fenced tool output and bounded operations. | [plugin interfaces](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/plugins), [LlmAgent callbacks](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/llm_agent.py) | Input/tool/output policy checks with adversarial fixtures and clear refusal/error states. Filters do not guarantee every answer is correct or safe. |
| Input/Output Validators | Partial: strict planner and complete proposal-pair checks are tested on the PR. | [LlmAgent schemas](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/agents/llm_agent.py), [FunctionTool schema handling](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/tools/function_tool.py) | Validate every externally actionable tool payload and structured model result before use, with repairable errors and malformed-input coverage. |
| Human-in-the-Loop | Partial: owner-scoped, expiring, single-use approvals for explicit file/note proposals; pending approvals are in memory. | [tool confirmation sample](https://github.com/google/adk-python/tree/v2.12.0/contributing/samples/hitl/tool_confirmation) | Persist approval state where resume needs it and preserve confirmation for consequential actions. Automatic note learning is separate behavior; do not describe every existing write as approval-gated. |
| Security & Secrets | Partial: owner verification, secret references and single-repository boundaries exist; live IAM/secrets not audited here. | [ADK auth](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/auth) | Server-side credentials, least-privilege connectors, safe errors/logs, owner isolation and reviewable deployment permissions. Keep existing controls while adding tools. |

### 5. Monitoring & Evaluation

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| Logging & Tracing | Partial: logs, tool steps and progress exist; no Dua request-correlated trace contract verified. | [telemetry](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/telemetry) | Correlate request/agent/tool spans, duration, outcome and cancellation with safe structured fields. Record actions and safe summaries, not private chain-of-thought or secrets. |
| Token & Cost Trackers | Pending at the per-request ADK reporting boundary. Output caps are not usage accounting. | [LlmResponse usage_metadata](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/models/llm_response.py), [token usage telemetry](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/telemetry/_token_usage.py) | Aggregate actual reported input/output/cached/thinking usage without double-counting streamed totals. Mark missing usage unknown and pricing-based costs estimated; do not invent billed amounts. |
| Evaluation Metrics | Pending: regression tests do not measure live answer accuracy. | [evaluation](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/evaluation), [CLI evaluation](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/cli/cli_eval.py) | Versioned cases and separate quality reports for answers, retrieval and tool decisions. Keep paid/live evaluation opt-in, separate from deterministic offline CI. |
| Automated Testing | Implemented/tested for current ADK behavior: minimal mocks and real SDK with offline scripted models. | [upstream unit tests](https://github.com/google/adk-python/tree/v2.12.0/tests/unittests) | Continue [test_agent_adk.py](../google-cloud-backend/tests/test_agent_adk.py) and [CI](../.github/workflows/checks.yml); add meaningful behavioral coverage for each integration. The inspected PR code has 428 passing SDK-suite tests, not 428 answer-quality benchmarks. |

### 6. CLI & Deployment

| Requirement | Dua state | Official GitHub starting point | Evidence needed to complete |
| --- | --- | --- | --- |
| Command Line Interface (CLI) | Partial: official adk command is installed; Dua's Flask app is not a configured discoverable ADK CLI agent package. | [cli_create.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/cli/cli_create.py), [cli.py](https://github.com/google/adk-python/blob/v2.12.0/src/google/adk/cli/cli.py) | Document and exercise scaffold/run/test commands against Dua's actual entry point without making local debugging publicly accessible. |
| Deployment Configuration | Partial: existing Flask/Gunicorn Cloud Run entry point and runbook; no newly integrated ADK Docker/AWS template. | [official deployers](https://github.com/google/adk-python/tree/v2.12.0/src/google/adk/cli/deployers) | Reproducible image/startup/health/secrets configuration, with validated examples for the selected deployment target. The pinned official deployer documents GCP and Docker; it does not establish an AWS implementation. Supabase serves data/auth; deployment targets are not interchangeable. |
| Versioning & Rollback | Partial: SDK pinned, Git branches/PRs and operations guidance exist; live healthy revision/rollback unverified. | [pinned ADK release](https://github.com/google/adk-python/tree/v2.12.0) | Record code/config/schema versions and a tested rollback to a known healthy revision, preserving owner data and session compatibility. Fareed merges; deploying or changing cloud settings requires the relevant authorization. |

## Delivery boundary

Implement the pending work in focused increments under these same requirements;
do not delete a pending requirement to claim completion. Verify each increment
with the real pinned SDK and offline providers before live testing. No model,
storage, deployment or billing change is implied by saving this scope. Update
status only from observed code/tests and label live checks separately.
