# Dua AI - complete project mind map

Poora project ek jagah: Android app, website, Node API, Cloud Run engine/ADK, data, integrations, tests aur deployment.

**Source snapshot:** [`main@59feb08`](https://github.com/KRFbrothers/Dua-ai/tree/59feb089a6aede3c45eb5be8592c72ce0df9e28d), checked 21 October 2026. Yeh checked-in code ka map hai. **Configured/optional** nodes ko live service availability mat samjho; **pending** nodes source mein placeholder hain.

GitHub par is file ka **Preview** view kholo: neeche Mermaid blocks diagrams ke roop mein dikhte hain. Pehla map overview hai; detailed map ke liye in links se jump karo.

- [1. Poore project ka overview](#overview)
- [2. Android app - screens aur device features](#android)
- [3. ChatViewModel - kaam kis module ka hai](#viewmodel)
- [4. Website aur Node API](#web-api)
- [5. Cloud Run engine aur Agent execution](#engine)
- [6. ADK - tumhare 6 categories aur 25 requirements](#adk-25)
- [7. Data, memory, files aur backup](#data)
- [8. Models, integrations aur tools](#integrations)
- [9. Tests, security aur deployment](#delivery)
- [10. Pending aur configuration-dependent parts](#pending)
- [11. Har top-level folder ka index](#folders)
- [12. ADK requirement se source-file tak](#adk-sources)

<a id="overview"></a>

## 1. Poore project ka overview

```mermaid
mindmap
  m0root((Dua AI))
    m0n1["Android app"]
      m0n2["Compose screens"]
      m0n3["ChatViewModel aur feature delegates"]
      m0n4["Voice, files, projects aur local AI"]
    m0n5["Website"]
      m0n6["React aur Vite"]
      m0n7["Owner login"]
      m0n8["Chat shell - backend binding pending"]
    m0n9["Node API - dua-api"]
      m0n10["Account, sync aur files"]
      m0n11["Provider chat, Imagine aur voice"]
      m0n12["GitHub connector aur Build PR"]
    m0n13["Cloud Run engine"]
      m0n14["Vertex chat aur SSE streaming"]
      m0n15["Google ADK ya existing tool loop"]
      m0n16["RAG, sessions, approvals aur evaluation"]
    m0n17["Data aur backup"]
      m0n18["Phone cache aur encrypted session"]
      m0n19["Supabase Auth, SQL aur Storage"]
      m0n20["Google Drive aur GCS backup"]
    m0n21["Providers aur tools"]
      m0n22["Models, search, code aur images"]
      m0n23["GitHub aur custom tool registry"]
    m0n24["Quality aur delivery"]
      m0n25["Android, Node aur Python tests"]
      m0n26["CI, APK signing aur Cloud Run rollout"]
      m0n27["Benchmarks aur device checks"]
    m0n28["Project knowledge"]
      m0n29["Docs aur roadmap"]
      m0n30["Licensed assets aur third-party notices"]
      m0n31["Cleanup audit log"]
```

**Code aur guide:** [Runtime connections](ARCHITECTURE.md#runtime-map) · [Project documentation](README.md)

<a id="android"></a>

## 2. Android app - screens aur device features

```mermaid
mindmap
  m1root((Android app))
    m1n1["Entry aur app root"]
      m1n2["MainActivity - launch aur intents"]
      m1n3["DuaApp - preferences, theme aur lifecycle"]
      m1n4["DuaNavHost - destinations"]
      m1n5["Share targets, shortcuts aur home widget"]
    m1n6["Account aur setup"]
      m1n7["Onboarding aur skip"]
      m1n8["Google aur email sign-in"]
      m1n9["Profile, logout aur cloud sync"]
      m1n10["Provider aur feature setup"]
    m1n11["Chat"]
      m1n12["Approved SuppliedChatUi aur composer"]
      m1n13["ChatRoute state aur Android pickers"]
      m1n14["MessageList, Markdown aur streaming"]
      m1n15["Code blocks, Run code aur artifact canvas"]
      m1n16["Message actions, search aur history drawer"]
    m1n17["Feature screens"]
      m1n18["Projects, instructions aur sources"]
      m1n19["Project memory, tasks aur daily planner"]
      m1n20["AI Agent, Images aur Library"]
      m1n21["Plugins aur capability status"]
    m1n22["Voice aur media"]
      m1n23["Dictation aur hands-free turns"]
      m1n24["Reply read-aloud aur generated audio"]
      m1n25["Live voice, barge-in aur foreground service"]
      m1n26["Vosk offline voice models"]
      m1n27["Camera, photos, files aur Imagine viewer"]
    m1n28["Device services aur appearance"]
      m1n29["On-device text and writing tools"]
      m1n30["Background removal aur cutouts"]
      m1n31["Themes, fonts, motion aur accessibility"]
      m1n32["Storage, notifications aur reminder receiver"]
      m1n33["Backup, import, export aur data controls"]
```

**Code aur guide:** [App entry](../app/src/main/java/com/dua/ai/MainActivity.kt) · [UI packages](../app/src/main/java/com/dua/ai/ui) · [Device AI](../app/src/main/java/com/dua/ai/ondevice) · [Voice implementation](../app/src/main/java/com/dua/ai/voice) · [Entry points](ANDROID_ENTRY_POINTS.md)

<a id="viewmodel"></a>

## 3. ChatViewModel - kaam kis module ka hai

```mermaid
mindmap
  m2root((ChatViewModel))
    m2n1["Coordinator"]
      m2n2["Send preparation aur frozen request plan"]
      m2n3["Stream collection aur message updates"]
      m2n4["Shared StateFlows aur host interfaces"]
      m2n5["Generation, cancellation aur per-reply mode maps"]
    m2n6["Conversation"]
      m2n7["ChatHistory - save, open, sync aur deletion"]
      m2n8["ChatLifecycle - retry response"]
      m2n9["ChatReplyHelpers - reply helpers"]
    m2n10["Modes aur tools"]
      m2n11["ChatModes - Auto, Reasoning aur web mode"]
      m2n12["ChatAgentControls - Agent, code, Build aur approvals"]
      m2n13["ChatImagine - image aur video requests"]
    m2n14["Files"]
      m2n15["ChatAttachments - draft attachment glue"]
      m2n16["ChatAttachmentFiles - source file context"]
      m2n17["ChatUploads - selection aur stored uploads"]
    m2n18["Personal state"]
      m2n19["ChatAccount - account aur cloud operations"]
      m2n20["ChatProjects - project context aur tasks"]
      m2n21["ChatMemory - proposed personal notes"]
      m2n22["ChatSettings - preferences aur writing tools"]
      m2n23["ChatVoice - speech aur live voice"]
      m2n24["ChatDataControls - import, export aur clearing"]
```

**Code aur guide:** [All ViewModel modules](../app/src/main/java/com/dua/ai/viewmodel) · [ChatRoute binding](../app/src/main/java/com/dua/ai/ui/chat/ChatRoute.kt) · [Cleanup and split history](../_to_delete/README.md)

<a id="web-api"></a>

## 4. Website aur Node API

```mermaid
mindmap
  m3root((Website and dua-api))
    m3n1["Website - website folder"]
      m3n2["React, TypeScript aur Vite"]
      m3n3["App - session state"]
      m3n4["Login - owner sign-in"]
      m3n5["api.ts - account requests"]
      m3n6["ChatShell - local UI and placeholder answer"]
      m3n7["Build output - web/site"]
    m3n8["HTTP server - web/src/server"]
      m3n9["server.mjs - Node server"]
      m3n10["service-handler.mjs - shared API routing"]
      m3n11["static-site.mjs - serve built website"]
    m3n12["Account aur owned data"]
      m3n13["access-policy aur account"]
      m3n14["cloud-snapshot aur reply-persistence"]
      m3n15["projects aur file-context"]
      m3n16["files, storage aur attachment validation"]
    m3n17["AI services"]
      m3n18["providers, adapters aur Gemini"]
      m3n19["context summaries aur SSE"]
      m3n20["Search, native-search aur page-reader"]
      m3n21["Run code, spreadsheets aur transcription"]
      m3n22["Imagine image and video jobs"]
      m3n23["Generated voice, TTS aur live session setup"]
    m3n24["GitHub aur operations"]
      m3n25["Read connector aur repository validation"]
      m3n26["Build chat aur proposed diffs"]
      m3n27["Owner-confirmed Build PR writes"]
      m3n28["Request logging aur kill switches"]
```

**Code aur guide:** [Website components](../website/src) · [Website guide](WEBSITE.md) · [Node server](../web/src/server) · [Node service modules](../web/src/core) · [Node tests](../web/test)

<a id="engine"></a>

## 5. Cloud Run engine aur Agent execution

```mermaid
mindmap
  m4root((dua-ai-engine))
    m4n1["HTTP entry"]
      m4n2["Gunicorn - wsgi.py"]
      m4n3["web_cors.py - browser origin policy"]
      m4n4["app.py - chat, title, Agent and document endpoints"]
      m4n5["owner_auth.py - verify owner session"]
      m4n6["SSE text, progress, session and terminal events"]
    m4n7["Plain chat"]
      m4n8["Vertex Gemini"]
      m4n9["Optional OpenRouter models"]
      m4n10["Attachments, project context aur memory"]
      m4n11["Bounded 429 retry before first text"]
    m4n12["Agent selection"]
      m4n13["Existing Gemini tool loop - agent_tools.py"]
      m4n14["Google ADK path - AGENT_ENGINE=adk"]
    m4n15["ADK stages"]
      m4n16["Controller aur bounded plan"]
      m4n17["Parallel research, code aur notes helpers"]
      m4n18["Merger aur streamed final answer"]
      m4n19["Optional reviewer and one revision"]
      m4n20["Optional answer verifier"]
    m4n21["Boundaries aur execution"]
      m4n22["Owner context aur output filtering"]
      m4n23["Read retries, deadlines and cancellation"]
      m4n24["Coding agent repository boundaries"]
      m4n25["Human approval for write tools"]
      m4n26["PR flow and bounded CI check when used"]
      m4n27["Stage timing and optional Gemini retry"]
    m4n28["Other engine jobs"]
      m4n29["Imagine generation aur owned storage"]
      m4n30["backup.py - separate scheduled backup job"]
```

**Code aur guide:** [Engine entry](../google-cloud-backend/app.py) · [ADK orchestration](../google-cloud-backend/agent_adk.py) · [Tools loop](../google-cloud-backend/agent_tools.py) · [Coding and PR flow](../google-cloud-backend/coding_agent.py) · [Agent operating guide](AGENT.md)

<a id="adk-25"></a>

## 6. ADK - tumhare 6 categories aur 25 requirements

```mermaid
mindmap
  m5root((ADK - 25 requirements))
    m5n1["1 Core - 5"]
      m5n2["Agent runtime"]
      m5n3["Prompt and context management"]
      m5n4["Short and long-term memory"]
      m5n5["Document RAG retrieval"]
      m5n6["State, sessions and resume"]
    m5n7["2 Tools - 4"]
      m5n8["Built-in tools"]
      m5n9["API connectors"]
      m5n10["Custom Tool SDK"]
      m5n11["Errors and bounded read retry"]
    m5n12["3 Models - 4"]
      m5n13["LLM provider adapters"]
      m5n14["Embedding models"]
      m5n15["Streaming"]
      m5n16["Configured model fallback"]
    m5n17["4 Control - 5"]
      m5n18["Multi-agent orchestration"]
      m5n19["Guardrails and safety filters"]
      m5n20["Input and output validators"]
      m5n21["Human approval for writes"]
      m5n22["Secrets and least privilege"]
    m5n23["5 Monitoring - 4"]
      m5n24["Content-free logs and traces"]
      m5n25["Provider token and cost tracking"]
      m5n26["Deterministic evaluations"]
      m5n27["Automated offline tests"]
    m5n28["6 Shipping - 3"]
      m5n29["ADK CLI"]
      m5n30["Docker and Cloud Run configuration"]
      m5n31["Versioned rollout and rollback"]
```

**Code aur guide:** [Exact scope](ADK_SCOPE.md) · [Implementation and configuration](ADK_IMPLEMENTATION.md)

<a id="data"></a>

## 7. Data, memory, files aur backup

```mermaid
mindmap
  m6root((Data and ownership))
    m6n1["Phone"]
      m6n2["Keystore-backed account session"]
      m6n3["Chat cache, drafts and mode recovery"]
      m6n4["DataStore settings and appearance"]
      m6n5["Transient attachments and media cache"]
      m6n6["Local personal and project memory"]
    m6n7["Supabase application data"]
      m6n8["Auth - owner identity and sessions"]
      m6n9["Cloud snapshots and revision conflicts"]
      m6n10["Usage and daily caps"]
      m6n11["Private files and Imagine media"]
      m6n12["Voice usage and agent notes"]
      m6n13["RLS and owner-scoped access"]
    m6n14["ADK private SQL state - configured"]
      m6n15["Official SDK sessions and events"]
      m6n16["Task leases and resumable runs"]
      m6n17["Durable single-use approvals"]
      m6n18["Owned documents and chunks"]
      m6n19["pgvector embeddings and semantic retrieval"]
    m6n20["Backup and recovery"]
      m6n21["Phone archive and encryption"]
      m6n22["Google Drive authorization and WorkManager"]
      m6n23["Cloud backup job to GCS"]
      m6n24["Reviewed import, export and cloud restore"]
```

**Code aur guide:** [Local data and account code](../app/src/main/java/com/dua/ai/account) · [History cache](../app/src/main/java/com/dua/ai/history) · [SQL and Storage migrations](../supabase/migrations) · [ADK session store](../google-cloud-backend/agent_store.py) · [Documents and vector memory](../google-cloud-backend/agent_documents.py) · [Phone backups](STORAGE_BACKUP.md) · [Cloud backup](../google-cloud-backend/backup.py)

<a id="integrations"></a>

## 8. Models, integrations aur tools

```mermaid
mindmap
  m7root((Providers and tools))
    m7n1["Node chat adapters - configured"]
      m7n2["Gemini and Dua"]
      m7n3["OpenAI ChatGPT"]
      m7n4["Anthropic Claude"]
      m7n5["xAI Grok"]
      m7n6["Kimi"]
    m7n7["ADK model gateway - configured"]
      m7n8["Google Gemini adapter"]
      m7n9["LiteLLM provider routes"]
      m7n10["OpenAI, Anthropic and OpenRouter routes"]
      m7n11["Local Ollama and vLLM routes - explicit opt-in"]
      m7n12["FallbackModel and provider usage observation"]
    m7n13["Built-in Agent tools"]
      m7n14["Google search and public page fetch"]
      m7n15["Calculator and IST time"]
      m7n16["Provider-sandbox Python"]
      m7n17["Image generation"]
      m7n18["Remember, recall and owned document search"]
    m7n19["GitHub"]
      m7n20["Repository tree, files and search"]
      m7n21["Issues, issue search and comments"]
      m7n22["Validated file proposals and PR creation"]
      m7n23["Separate read and write capabilities"]
    m7n24["Other services"]
      m7n25["Brave search fallback and Grok X search"]
      m7n26["Provider transcription and generated speech"]
      m7n27["Live voice session provider"]
      m7n28["Google sign-in and Drive backup"]
    m7n29["Custom tools - configured"]
      m7n30["Server-installed modules only"]
      m7n31["Typed arguments and bounded JSON results"]
      m7n32["Per-tool budgets and stable revision identity"]
      m7n33["Write tools share owner approval boundary"]
```

**Code aur guide:** [Node provider adapters](../web/src/core/providers.mjs) · [ADK model routing](../google-cloud-backend/agent_models.py) · [Custom tool registry](../google-cloud-backend/agent_toolkit.py) · [Agent tool declarations](../google-cloud-backend/agent_tools.py) · [GitHub boundaries](GITHUB_VALIDATION.md) · [Provider setup](PROVIDERS.md)

<a id="delivery"></a>

## 9. Tests, security aur deployment

```mermaid
mindmap
  m8root((Quality and shipping))
    m8n1["Android checks"]
      m8n2["JVM and Robolectric tests"]
      m8n3["Compose UI and screenshot fixtures"]
      m8n4["Mock HTTP and local TLS transport tests"]
      m8n5["Debug build and separate lint task"]
      m8n6["Benchmark module - connected device"]
    m8n7["Backend checks"]
      m8n8["Node test runner with mocked services"]
      m8n9["Python mocked engine tests"]
      m8n10["Official ADK SDK offline-model tests"]
      m8n11["CI pgvector PostgreSQL service"]
      m8n12["Deterministic ADK eval fixture"]
    m8n13["GitHub Actions"]
      m8n14["Project checks - four jobs"]
      m8n15["Android, Node, engine and website build"]
      m8n16["Manual main-branch personal APK workflow"]
      m8n17["Permanent signing key and certificate verification"]
      m8n18["APK checksums and Release assets"]
    m8n19["Cloud Run deployment"]
      m8n20["Node Dockerfile and deploy-cloudrun.sh"]
      m8n21["Engine Dockerfile and Gunicorn entry"]
      m8n22["ADK Cloud Build source revision"]
      m8n23["Candidate revision at zero traffic"]
      m8n24["Explicit promotion and rollback snapshot"]
      m8n25["Cloud Shell stage and smoke commands"]
    m8n26["Security and operational controls"]
      m8n27["Owner auth, RLS and private storage"]
      m8n28["Secret Manager and server-side keys"]
      m8n29["GitHub path policy and separate write token"]
      m8n30["Tool-output fencing and secret filtering"]
      m8n31["Kill switches, quotas and time budgets"]
      m8n32["Phone and live integration verification"]
```

**Code aur guide:** [CI workflow](../.github/workflows/checks.yml) · [Signed APK workflow](../.github/workflows/personal-apk.yml) · [APK verifier](../scripts/verify-apk-signature.py) · [Performance module](../benchmark) · [ADK deploy and rollback](../google-cloud-backend/deploy-adk.sh) · [Cloud Shell script](../google-cloud-backend/dua-adk-cloudshell.sh) · [Security policy](../SECURITY.md) · [Operations](OPERATIONS.md) · [Test guide](TESTING.md)

<a id="pending"></a>

## 10. Pending aur configuration-dependent parts

```mermaid
mindmap
  m9root((Read the status correctly))
    m9n1["Implemented in source"]
      m9n2["Android chat and service bindings"]
      m9n3["Mode and retry guards"]
      m9n4["Backend and ADK modules"]
      m9n5["Owner-scoped persistence and approval code"]
    m9n6["Configuration-dependent"]
      m9n7["Provider keys and model routes"]
      m9n8["ADK SQL schema and pgvector"]
      m9n9["Embedding indexing and custom modules"]
      m9n10["Search, Imagine, voice and GitHub tools"]
      m9n11["Signing and deployed OAuth configuration"]
    m9n12["Website pending"]
      m9n13["Real chat backend and streaming binding"]
      m9n14["Synced history"]
      m9n15["Projects, Imagine, Agent and Search"]
      m9n16["Attachments"]
    m9n17["Android placeholders"]
      m9n18["Computer remote control"]
      m9n19["Screenshare and video"]
      m9n20["Search settings and edit profile photo"]
      m9n21["Dedicated Privacy, Sharing and Permissions pages"]
    m9n22["Verification separate from code"]
      m9n23["Live cloud configuration and connectivity"]
      m9n24["Physical phone permissions and audio"]
      m9n25["Cross-device sync and restart recovery"]
```

**Code aur guide:** [Website send handler](../website/src/ChatShell.tsx) · [Android placeholder destinations](../app/src/main/java/com/dua/ai/ui/navigation/DuaDestination.kt) · [Computer screen](../app/src/main/java/com/dua/ai/ui/features/ComputerScreen.kt) · [Release/device checklist](RELEASE_CHECKLIST.md)

<a id="folders"></a>

## 11. Har top-level folder ka index

Is snapshot ke saare 12 tracked top-level directories, plus root files, neeche covered hain. Build outputs aur secrets is inventory ka hissa nahi hain.

| Folder / root files | Kya hai |
|---|---|
| [`app/`](../app) | Android app, production code, resources, offline Mermaid bundle, JVM/Compose tests, debug previews, baseline profile. |
| [`website/`](../website) | React/Vite website, styles, logo-copy script aur build configuration. Output `web/site` mein jaata hai. |
| [`web/`](../web) | Node API/server, core services, Docker/deploy script, test fixtures, RLS verification aur smoke utilities. |
| [`google-cloud-backend/`](../google-cloud-backend) | Flask/Vertex engine, Google ADK, tool loop, SQL/RAG, CLI, evaluations, container/deployment aur Python tests. |
| [`supabase/`](../supabase) | Application migrations: snapshots, usage, files/Storage, Imagine, voice aur agent notes. ADK private SDK tables `init-db` se initialize hote hain. |
| [`benchmark/`](../benchmark) | Startup macrobenchmark aur Baseline Profile generation; connected device chahiye. |
| [`.github/`](../.github) | Four-job CI aur manual signed personal APK workflow. |
| [`gradle/`](../gradle) | Version catalog aur Gradle wrapper configuration. |
| [`scripts/`](../scripts) | APK signature, certificate aur checksum verification. |
| [`docs/`](.) | Architecture, ADK scope, operations, providers, feature/UI guides, release/testing checklists aur HTML design previews. |
| [`third_party/`](../third_party) | Androidify Apache license text. Root notices aur inline attribution bhi license trail ka part hain. |
| [`_to_delete/`](../_to_delete) | Audit/split history README; folder ka naam automatic deletion permission nahi hai. |
| Root configuration and guides | [`README.md`](../README.md), [`build.gradle.kts`](../build.gradle.kts), [`settings.gradle.kts`](../settings.gradle.kts), [`gradle.properties`](../gradle.properties), wrappers aur Node version files. [`SIGNING.md`](../SIGNING.md), [`SUPABASE.md`](../SUPABASE.md), [`SECURITY.md`](../SECURITY.md), [`LICENSE`](../LICENSE), [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md). |

`dua_gen_ai.py` ek [standalone UI design-generation sample](../dua_gen_ai.py) hai; Android/Node/Flask runtime entry point nahi. Assets, fonts, Telegram/Androidify adaptations aur vendored Mermaid ke notices [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) aur [offline Mermaid README](../app/src/main/assets/vendor/mermaid/README.md) mein milte hain.

<a id="adk-sources"></a>

## 12. ADK requirement se source-file tak

25 requirements ka map section 6 mein hai; yeh table unke implementation tak seedha raasta deta hai.

| Category | Main source locations |
|---|---|
| Core runtime, prompts/context, memory, RAG, sessions | [`agent_adk.py`](../google-cloud-backend/agent_adk.py), [`agent_context.py`](../google-cloud-backend/agent_context.py), [`agent_notes.py`](../google-cloud-backend/agent_notes.py), [`agent_documents.py`](../google-cloud-backend/agent_documents.py), [`agent_store.py`](../google-cloud-backend/agent_store.py) |
| Tools, connectors, custom SDK, retry | [`agent_tools.py`](../google-cloud-backend/agent_tools.py), [`coding_agent.py`](../google-cloud-backend/coding_agent.py), [`agent_toolkit.py`](../google-cloud-backend/agent_toolkit.py), [`agent_resilience.py`](../google-cloud-backend/agent_resilience.py), [`adk_examples/text_tools.py`](../google-cloud-backend/adk_examples/text_tools.py) |
| Provider/embedding models, streaming, fallback | [`agent_models.py`](../google-cloud-backend/agent_models.py), [`agent_documents.py`](../google-cloud-backend/agent_documents.py), [`app.py`](../google-cloud-backend/app.py), [`openrouter.py`](../google-cloud-backend/openrouter.py) |
| Orchestration, guardrails, validation, approvals, secrets | [`agent_adk.py`](../google-cloud-backend/agent_adk.py), [`agent_safety.py`](../google-cloud-backend/agent_safety.py), [`agent_policy.py`](../google-cloud-backend/agent_policy.py), [`owner_auth.py`](../google-cloud-backend/owner_auth.py), [`agent_store.py`](../google-cloud-backend/agent_store.py), [`pr_flow.py`](../google-cloud-backend/pr_flow.py) |
| Traces, token/cost tracking, evaluation, tests | [`agent_observability.py`](../google-cloud-backend/agent_observability.py), [`agent_evaluation.py`](../google-cloud-backend/agent_evaluation.py), [`evals/adk-v1.json`](../google-cloud-backend/evals/adk-v1.json), [`tests/`](../google-cloud-backend/tests) |
| CLI, containers/cloud, versioned rollout/rollback | [`adk_cli.py`](../google-cloud-backend/adk_cli.py), [`adk_server.py`](../google-cloud-backend/adk_server.py), [`Dockerfile`](../google-cloud-backend/Dockerfile), [`cloudbuild-adk.yaml`](../google-cloud-backend/cloudbuild-adk.yaml), [`deploy-adk.sh`](../google-cloud-backend/deploy-adk.sh), [`dua-adk-cloudshell.sh`](../google-cloud-backend/dua-adk-cloudshell.sh) |

Connections/data flow ke liye [Architecture runtime map](ARCHITECTURE.md#runtime-map), configuration ke liye [ADK implementation](ADK_IMPLEMENTATION.md), aur future work ke liye [ROADMAP.md](ROADMAP.md) kholo.
