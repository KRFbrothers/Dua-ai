# AGENTS.md — Universal AI Coding Agent Rules for Dua AI (`KRFbrothers/Dua-ai`)

> **Cross-Tool Standard:** This file follows the open [AGENTS.md](https://agents.md/) specification. All AI coding tools working in this repository must follow these rules, including Codex, Claude Code, Gemini, Cursor, Copilot, and Dua AI's internal coding agent. Tools without native discovery must load this file through their configured instruction mechanism.

These rules apply throughout the repository. Preserve unrelated work. Paths are repository-relative; verify symbols against current source rather than relying on fixed line numbers.

## 1. Project Overview & Architecture Map

**Dua AI (`com.dua.ai`)** is a private, owner-locked multimodal GenAI assistant across Android (`app/`), Web (`apps/web/` + `apps/api/`), and Google Cloud Run (`apps/ai-engine/`), backed by Supabase Auth, PostgreSQL RLS, and Storage (`infra/database/`).

| Layer / Directory | Tech Stack | Key Entry Points & Files | Responsibility |
| :--- | :--- | :--- | :--- |
| Android App (`app/`) | Kotlin + Jetpack Compose + Material 3 | `MainActivity.kt`, `ui/navigation/DuaNavHost.kt`, `ui/chat/ChatScreen.kt`, `ui/chat/ChatTopBar.kt`, `ui/chat/ChatComposer.kt`, `viewmodel/ChatViewModel.kt`, `network/DuaApiClient.kt` | Native Android client, 4-state composer, Live Voice, Artifacts Canvas, Projects, Settings, Home AppWidget (`DuaToolbarWidgetReceiver`) |
| Android Theme (`app/.../ui/theme/`) | Compose Design Tokens (`@Immutable`) | `Color.kt`, `DuaTheme.kt`, `Type.kt`, `Motion.kt`, `Spacing.kt`, `HomeTokens.kt`, `ChatTokens.kt`, `DrawerTokens.kt` | Single source of truth for colors, typography, shapes, spacing, and motion springs |
| Cloud Run AI Engine (`apps/ai-engine/`) | Python 3.12 container + Flask + Vertex AI (`google-genai`) + Google ADK (`google-adk==2.12.0`) | `app.py`, `owner_auth.py`, `agent_tools.py`, `agent_adk.py`, `coding_agent.py`, `coding_workspace.py`, `coding_runner_server.py`, `kill_switches.py`, `backup.py` | Owner-verified Gemini streaming chat, single-loop and ADK multi-agent tools, grounded search, coding PR agent, Imagine, backups |
| Node API & Proxy (`apps/api/`) | Node.js 24+; native HTTP server | `src/server/server.mjs`, `src/server/service-handler.mjs`, `src/core/access-policy.mjs`, `src/core/account.mjs`, `src/core/providers.mjs`, `src/core/kill-switches.mjs` | Same-origin web host, HttpOnly cookie sessions, daily request quota, cloud snapshot sync, `/api/web/chat` SSE proxy |
| Web Frontend (`apps/web/`) | React 19 + TypeScript + Vite 6 | `src/main.tsx`, `src/App.tsx`, `src/ChatShell.tsx`, `src/Login.tsx`, `src/api.ts`, `src/styles.css` | Browser chat and Agent UI compiled into `apps/api/site` |
| Database & Storage (`infra/database/`) | PostgreSQL + RLS + `SECURITY DEFINER` RPCs | `migrations/*.sql` | Owner-isolated cloud snapshots (`dua_cloud_snapshots`), usage budgets, private `dua-files` bucket, Agent notes |
| Documentation (`docs/`) | Markdown | `README.md`, `ARCHITECTURE.md`, `AGENT.md`, `OPERATIONS.md`, `TESTING.md` | Runtime, deployment, testing, and feature specifications |

Use the runtime and dependency versions declared in the repository. The Node API declares Node 24+ in `apps/api/package.json`; the engine Dockerfile uses Python 3.12.

## 2. Hard Stop Rules (Zero-Tolerance Guardrails for All AI Tools)

### 2.1 Protected Paths

Do not edit, delete, or move these paths unless explicitly instructed by the owner:

- `.github/workflows/` and `.gitmodules`
- `app/src/main/assets/vendor/mermaid/`
- Deployment and container configs anywhere in the repository: `Dockerfile`, `*.Dockerfile`, `cloudbuild*.yaml`, `deploy*.sh`, and `Procfile`, including `apps/ai-engine/coding-runner/*.Dockerfile`
- Database migrations (`infra/database/migrations/*.sql`)
- The `+` attachment sheet layout (`app/src/main/java/com/dua/ai/ui/chat/AttachmentSheet.kt`)

### 2.2 Zero Secrets in Code or Docs

- Never hardcode API keys, tokens, service-role keys, passwords, or personal emails in source files, tests, or documentation.
- Read backend secrets exclusively from environment variables (`process.env` / `os.environ`) at runtime.
- Never expose server secrets in Android or web client builds, responses, logs, or tool output.

### 2.3 Strict Brand Ownership (`KRFbrothers / Dua AI`)

- Never introduce third-party competitor logos, brand names, or proprietary font files as Dua AI branding. All UI branding, components, and user-facing copy must maintain the KRFbrothers / Dua AI identity.
- Preserve the existing Home Screen Dua Calligraphy Logo (`DuaCalligraphyLogo` in `ChatContent.kt`) and existing `DuaTheme` color scheme unless explicitly asked to change them.

### 2.4 Evidence-Only Reporting (Zero Guessing / Zero Hallucinated Fixes)

- Read the complete target file, from line 1 to the final line, before proposing an edit or reporting an issue.
- Never claim tests ran or passed unless you executed the command and observed a zero-exit-code pass.
- Distinguish source findings from runtime verification. Report blocked checks and their blockers explicitly.

## 3. Android (`app/`) — Jetpack Compose, Theme Tokens & 120fps Smoothness Rules

### 3.1 Three-Layer Design Token Architecture (`ui/theme/Color.kt` & `Type.kt`)

**Never write raw `Color(0x...)` or `.sp` literals outside `app/src/main/java/com/dua/ai/ui/theme/`.**

1. **Layer 1:** Raw hex palette values belong only in `internal object DuaPalette` in `Color.kt`.
2. **Layer 2:** Map palette colors to semantic roles in `@Immutable data class DuaColors` and `DuaLightColorScheme` / `DuaDarkColorScheme`, supplying both Light and Dark values.
3. **Layer 3:** Screens read colors exclusively through `DuaTheme.colors`, `MaterialTheme.colorScheme`, or feature token objects such as `HomeTheme.colors`, `ChatTheme.colors`, and `DrawerTheme.colors`.

Always use `MaterialTheme.typography`, `CodeTextStyle`, or named `TextStyle` tokens from `ui/theme/Type.kt` for typography.

### 3.2 60fps / 120fps Smoothness & Recomposition Rules

- **State isolation during streaming:** Pass only primitive or needed arguments, such as draft text and typing state, into the composer and top bar. Avoid passing whole chat state so streaming message updates do not unnecessarily recompose them.
- **Memoize heavy parsing:** Wrap Markdown escaping, source splitting, and artifact extraction in `remember(key)`, for example `remember(message.text) { ReplySources.split(message.text) }`.
- **LazyColumn keys and content types:** Supply stable item keys and `contentType` for list items, including message lists and history drawers, so Compose can reuse nodes.
- **GPU-accelerated motion:** Prefer `Modifier.graphicsLayer` for alpha, scale, and translation animations when layout changes are unnecessary.
- **Motion accessibility:** Use `DuaMotion` springs and respect `rememberMotionAllowed()`. Snap immediately when system animations are disabled.
- **Shared networking:** Share `DuaApiClient.sharedClient`; use `.newBuilder()` for per-call timeout overrides. Never instantiate unshared `OkHttpClient()` instances in UI or ViewModel code.
- **Sandboxed previews:** Keep `CodePreview.kt` WebViews sandboxed with `allowFileAccess = false`, `allowContentAccess = false`, and `blockNetworkLoads = true`. Enable JavaScript only for bundled Mermaid and destroy previews using `onRelease = ::releaseCodePreview`.
- **Resource cleanup:** Clean up `LifecycleEventObserver`, `ContentObserver`, `TextToSpeech`, and `SpeechRecognizer` resources in `DisposableEffect.onDispose` when owned by composables, or in the corresponding owner lifecycle elsewhere.

### 3.3 Accessibility & UI Test Compatibility

- Every interactive button, chip, or row must have at least a `48.dp` touch target (`Sizes.touchTarget`) and a meaningful TalkBack label through `contentDescription` or semantics.
- Never rename or remove existing `Modifier.testTag` identifiers relied upon by tests, including `home-empty-content`, `open-canvas`, `drawer-search-field`, and `code-preview`.

## 4. GenAI Backends (`apps/ai-engine/` & `apps/api/`) — Security, Streaming & Cost Rules

### 4.1 Authentication, Owner Lock & Kill Switches

**Cloud Run Engine (`apps/ai-engine/`):**

- Every protected route must call `owner_access_error()`, validating optional `X-Dua-App-Token` in constant time with `hmac.compare_digest` and verifying the Supabase Bearer token through `verify_owner()` against `/auth/v1/user` and `DUA_ALLOWED_EMAIL`.
- Owner login must remain mandatory regardless of `DUA_REQUIRE_LOGIN`.
- Never follow HTTP redirects on authentication calls; preserve `NoAuthRedirects` in `owner_auth.py`.
- Check `kill_switches.maintenance_message()` and applicable feature switches, including `agent_disabled()` and `imagine_disabled()`, before invoking models or performing side effects.

**Node API (`apps/api/`):**

- Enforce same-origin browser checks (`Origin` and `Sec-Fetch-Site`) on cookie-authenticated routes while preserving native-client authentication.
- Preserve owner authentication, `DUA_ALLOWED_EMAIL`, applicable `DUA_REQUIRE_LOGIN` policy, and account-scoped access to stored data.
- Enforce `switchResponse(path, env)` and `guardChatAccess(request, { env, fetcher })` on applicable chat routes, including `/api/web/chat`.
- Honor `DUA_MAINTENANCE`, `DUA_DISABLE_AGENT`, `DUA_DISABLE_IMAGINE`, and `DUA_DISABLE_VOICE` where applicable. Preserve maintenance messages and paused responses.
- Ensure a session refreshed by `guardChatAccess` propagates both its `Set-Cookie` headers and its fresh access token to downstream calls.
- Preserve explicit approval requirements for sensitive Agent actions.

### 4.2 SSE Streaming, Deadlines & Client Disconnect Propagation

Always return SSE streams with:

```text
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-store
X-Accel-Buffering: no
X-Content-Type-Options: nosniff
```

## 5. Validation Commands

Run checks appropriate to the changed code using declared dependencies and supported runtimes:

- Node.js API: `cd apps/api && npm test`
- Python engine: `cd apps/ai-engine && python -m pytest -q tests`
- Android, from the repository root: `./gradlew :app:testDebugUnitTest`

Report only commands actually executed and their observed results. Explain any checks that could not run; never present blocked or unexecuted checks as passing.
