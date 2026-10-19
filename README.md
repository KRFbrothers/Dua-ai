# Dua AI

Dua AI is my personal assistant.

Dua is a native Android Compose app in `app/`, with a Node API in `web/` (deployed on Cloud Run as `dua-api`) and a separate Vertex AI chat engine in `google-cloud-backend/`. `web/` is the backend API; `dua-api` also serves the owner-only website built from `website/` at `/` (see [docs/WEBSITE.md](docs/WEBSITE.md)). The Android build defaults to `https://dua-api-228305912525.us-central1.run.app` for account/data services and `https://dua-ai-engine-228305912525.us-central1.run.app` for chat; these are build defaults, not a verification of live service configuration.

Dua is a personal, single-user app: it is built for its owner only and installed on his own device(s) as a debug or sideloaded APK. There are no other users, no Google Play release and no monetisation. The backend is meant to stay locked to the owner's account (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL`) with provider spending limits.

## Current features

- Server-backed conversations, drafts and memory with an app-private cache for offline viewing, full-message/draft search, in-conversation search with previous/next message navigation, pinned conversations, export/import, sharing and data controls. Node API chat supports saving completed replies before acknowledging completion; the separate Cloud Run engine relies on the existing cloud-sync flow instead. Search is project-scoped with match snippets; see [HISTORY-SEARCH.md](docs/features/HISTORY_SEARCH.md).
- Gemini plus configurable ChatGPT, Claude, Grok and Kimi adapters with streaming replies and a Reasoning mode (collapsible Thinking block on Gemini, Grok and reasoning ChatGPT models). Text and code files (TXT, CSV, MD, JSON, source code) work on all providers; images/PDF depend on the selected provider and configured model. See [PROVIDERS.md](docs/PROVIDERS.md).
- Chat Web: Off/Search/Read link modes and clickable source footers. Search requires Brave configuration; public page reading uses the existing chat provider. Web mode starts Off.
- Copy, retry and speaker reply icons; device speech recognition/TTS and explicit foreground hands-free turns. Optional generated reply audio uses an authenticated, owner-restricted server integration.
- Independent appearance/accent preferences (Preferences DataStore), approved Sidebar/Aura/Profile navigation, permissions, local notifications, reviewed server-backed memory notes/proposals and privacy controls for the cache and account.
- Native Google sign-in (Supabase accounts): a welcome screen at first launch with **Continue with Google**, **Log in another way**, or **Skip for now**; the sidebar avatar opens **Profile & Settings**; **Account** opens the dedicated account screen with email, editable contact phone number, password setup, Sync configuration and confirmed Log out. Signed-out Account opens the welcome layout. Signed-in chats and memory use the server as the source of truth; the phone keeps a disposable cache and foreground sync refreshes it. Without an account, the phone-only cache is temporary and cannot become authoritative. Owner-only email login/signup and existing-account password setup are implemented; see [SUPABASE.md](SUPABASE.md#owner-email-login-and-signup) for mandatory provider/confirmation setup. Google sign-in and Skip for now remain available.
- An optional read-only GitHub repository connector, with separately configured write-capable Build PR and Cloud Run coding-agent flows (see below).

API keys and optional credentials remain server-side. Preparing a feature does not activate a provider or paid service. OpenAI key setup remains deferred. Background/realtime sync, model-based memory and other advanced integrations remain pending.

For Node API server reply saving, deploy the Node API (dua-api) first, route chat to that backend, install the updated APK and refresh Feature setup. Existing cached conversations must match their server message history before a follow-up or regeneration; sync/review older or stopped offline turns first. Save conflicts and uncertain acknowledgements keep received text on the phone and require cloud review. The existing snapshot RPC/schema is reused; see [SUPABASE.md](SUPABASE.md#completed-reply-saving) for the contract and limits. Cloud Run does not implement this completed-reply saving contract. Imagine and live realtime voice retain their existing storage flows.

## How the pieces fit together

For a directory map, request traces, persistence contracts and GitHub permission boundaries, read the [contributor architecture guide](docs/ARCHITECTURE.md).

| Layer | Entry points | Responsibility |
| --- | --- | --- |
| Android | [MainActivity.kt](app/src/main/java/com/dua/ai/MainActivity.kt), [ChatRoute.kt](app/src/main/java/com/dua/ai/ui/chat/ChatRoute.kt), [DuaApiClient.kt](app/src/main/java/com/dua/ai/network/DuaApiClient.kt) | Native Compose/Material 3 UI, ViewModels, account session, composer/attachments, disposable offline cache and foreground sync. |
| Node API (`web/`, Cloud Run `dua-api`) | [server.mjs](web/src/server/server.mjs), [service-handler.mjs](web/src/server/service-handler.mjs) | Account/auth, configuration, cloud snapshots, stored files, Imagine, voice, optional provider chat and GitHub Build orchestration. Cloud Run and the local Node server both use the service handler. |
| Cloud Run engine | [app.py](google-cloud-backend/app.py), [owner_auth.py](google-cloud-backend/owner_auth.py), [coding_agent.py](google-cloud-backend/coding_agent.py) | Default `/api/chat` destination: Vertex Gemini, conversation/inline-file context, SSE, Agent research and optional repository coding. Verifies the signed-in owner with Supabase before invoking Vertex. |
| Supabase | [Migrations](supabase/migrations), [setup guide](SUPABASE.md) | Auth, authoritative owner snapshots and private uploaded/generated files, protected by RLS. Both backends must use the same Auth project and owner email. |

**Routing:** `DUA_API_BASE_URL` handles non-chat services; `DUA_CHAT_BASE_URL` selects the chat engine independently. Changing one does not change the other. Cloud Run uses its own Google credentials, so it does not need a Gemini API key on the Node API. Its responses always use Vertex Gemini, even if a different provider label is selected in the app. The Node API's multi-provider chat, Build mode, Run code and completed-reply saving require routing chat to the Node API (dua-api). See [engine capabilities and limitations](google-cloud-backend/README.md).

**Data:** signed-in server snapshots are authoritative; the Android cache is for offline use and recovery. Cloud Run receives conversation context and bounded inline attachments rather than reading dua-api stored-file references. For project context, Android loads the authenticated server definition and hydrates supported sources into that inline protocol; see [Projects flow](docs/PROJECTS_REFERENCE_FLOW.md#sources-and-backend-setup). Inline media bytes do not survive process restart/export/sync; reselect media when needed. This differs from files retained in Supabase Storage.

### GitHub read and write boundaries

- **Read-only connector / Build chat on the Node API:** [github.mjs](web/src/core/github.mjs) uses `GITHUB_CONNECTOR_TOKEN` for allowlisted GET requests. [build-chat.mjs](web/src/core/build-chat.mjs) can read repository context and propose diffs; generating a proposal does not create a branch or PR.
- **Owner-confirmed Build PR on the Node API:** [build-pr.mjs](web/src/core/build-pr.mjs) uses the separate [github-write.mjs](web/src/core/github-write.mjs) client and `GITHUB_BUILD_WRITE_TOKEN` only after the app's Create PR confirmation. Writes require explicit enablement, validated paths and current file SHAs; they create a `dua/*` branch and PR, without merging, force-pushing or deleting branches. [Build setup and token permissions](docs/PROVIDERS.md#build-mode-read-only-repository-chat-phase-5b) covers both steps.
- **Cloud Run coding agent:** [coding_agent.py](google-cloud-backend/coding_agent.py) uses its own optional `GITHUB_TOKEN`, limited to `DUA_REPO`, to create `dua-agent/*` branches and PRs during coding tasks. This is a separate write-capable agent flow, not the read-only connector or the Node API's confirmation endpoint. See [Cloud Run setup](google-cloud-backend/README.md) before enabling it; without the token, coding is unavailable.

### Short feature tour

Start at Home for chat, open History for search/rename/delete, and use Projects for real project chats, sources and instructions. Profile & Settings contains account, appearance, Voice, and History and data controls. Launcher-widget and Android share entry points are described in [Android entry points](docs/ANDROID_ENTRY_POINTS.md); the current Projects screens and their limitations are described in [Projects flow](docs/PROJECTS_REFERENCE_FLOW.md). [Historical light](docs/ui/compact-preview-light.png) and [dark](docs/ui/compact-preview-dark.png) previews use synthetic fixtures and are not a complete screenshot inventory of the current app.

## Docs

All guides are indexed in [docs/README.md](docs/README.md). Main entry points:

- How it fits and how to run it: [ARCHITECTURE](docs/ARCHITECTURE.md), [OPERATIONS](docs/OPERATIONS.md), [TESTING](docs/TESTING.md), [RELEASE_CHECKLIST](docs/RELEASE_CHECKLIST.md)
- Backends: [PROVIDERS](docs/PROVIDERS.md), [AGENT](docs/AGENT.md), [WEBSITE](docs/WEBSITE.md), [engine README](google-cloud-backend/README.md), [web/README](web/README.md)
- Android: [Android UI guide](docs/ui/ANDROID_UI.md) and [feature guides](docs/features/FEATURES.md)
- Root guides: [SUPABASE.md](SUPABASE.md), [SIGNING.md](SIGNING.md), [SECURITY.md](SECURITY.md); privacy: [PRIVACY](docs/PRIVACY.md)

## Project roadmap

See [ROADMAP.md](docs/ROADMAP.md) for the Dua AI Improvement Roadmap. Phase 1's architecture/local setup, environment examples, contributor project map and actionable GitHub/Build errors are implemented. Phase 2 now includes shared GitHub validation, protected-path regression coverage and [Node request/stage logging](docs/GITHUB_LOGGING.md); the remaining roadmap sections are not implied complete.
Use [RELEASE-CHECKLIST.md](docs/RELEASE_CHECKLIST.md) to verify the owner-only lock, provider spending limits, device/staging checks, and installing the APK on your own phone. The production configuration cannot be verified from this source checkout alone.
Use [SIGNING.md](SIGNING.md) to store the permanent personal signing key and run the manual [Build personal APK](.github/workflows/personal-apk.yml) workflow. Release builds require the pinned certificate; they cannot fall back to another machine's debug key.

## Local setup

Clone the repository and enter its root:

```sh
git clone https://github.com/KRFbrothers/Dua-ai.git
cd Dua-ai
```

Use Node 24+ for `web/`, Python 3 for the engine (the examples below were checked with Python 3.12), and JDK 17 / Android SDK 35 for Android. The mocked backend/engine suites require no provider credentials, Supabase project or signing key. Real sign-in, sync and generation require the configuration below; installing dependencies alone does not enable them.

### Configuration by runtime

This is the minimum configuration and integration index; linked setup guides contain optional limits and provider-specific variables. Put Node API settings in Secret Manager (same name as the variable) and re-run `web/deploy-cloudrun.sh`, or locally in ignored `web/.env`; `npm start` loads that file. Cloud Run settings belong in its service environment; for local Python, export them in the shell (`app.py` does not load `.env` automatically). Never commit provider keys, GitHub tokens, service-role keys or signing material.

Start from [web/.env.example](web/.env.example) or [google-cloud-backend/.env.example](google-cloud-backend/.env.example). Required account/project values and all credentials are empty; optional integrations are labeled. The examples are safe setup templates, not working credentials. Copy only when a local `.env` does not already exist, fill your own values, and keep login required. Do not replace existing deployment settings with the blank examples.

| Runtime / purpose | Required settings | Notes / full setup |
| --- | --- | --- |
| Node API (dua-api) owner sign-in and data | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `DUA_ALLOWED_EMAIL`; keep `DUA_REQUIRE_LOGIN=1` | Root project URL such as `https://PROJECT.supabase.co`, without `/rest/v1/`. Use a public publishable/anon key in `SUPABASE_ANON_KEY`, never a service-role key. Auth providers, redirects and snapshot/Storage migrations are in [SUPABASE.md](SUPABASE.md). |
| Node API chat (when selected) | At least one provider key **and** model ID: e.g. `GEMINI_API_KEY`, `GEMINI_MODEL` | Not required for separate Cloud Run chat. Other providers, web search, Imagine, voice and Run code have their own prerequisites in [PROVIDERS.md](docs/PROVIDERS.md). |
| Cloud Run owner chat | `GOOGLE_CLOUD_PROJECT`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` **or** `SUPABASE_ANON_KEY`, `DUA_ALLOWED_EMAIL` | Google runtime credentials/ADC with Vertex access are also required for generation. Use the same Supabase project/owner as dua-api. `VERTEX_LOCATION` defaults to `global`, `VERTEX_MODEL` to `gemini-3.8-flash`; see [engine setup](google-cloud-backend/README.md). |
| Android backend routing | No override required for checked-in endpoints; override with `DUA_API_BASE_URL` / `DUA_CHAT_BASE_URL` or Gradle `duaApiBaseUrl` / `duaChatBaseUrl` | Both URLs must be HTTPS. Native Google login also needs the matching OAuth web client ID (`GOOGLE_CLIENT_ID` / `duaGoogleWebClientId`), Android package/signing certificate and Supabase Google setup; see [SUPABASE.md](SUPABASE.md). Release signing is separate: [SIGNING.md](SIGNING.md). |
| Optional engine gate | Matching `DUA_APP_TOKEN` on Cloud Run and the Android build | Additional gate only; does not replace verified owner authentication. A token embedded in an APK is extractable. |
| Node API GitHub read / Build | `ENABLE_GITHUB_CONNECTOR=1`, `GITHUB_CONNECTOR_TOKEN`; Build also needs Node API Gemini chat and owner auth | Read-only token. Writes separately require `DUA_BUILD_PR=1`, `GITHUB_BUILD_WRITE_TOKEN` and owner confirmation; optional `DUA_BUILD_PR_REPOS` narrows repositories. See [Build configuration](docs/PROVIDERS.md#build-mode-read-only-repository-chat-phase-5b). |
| Cloud Run coding agent | `GITHUB_TOKEN` (optional); `DUA_REPO` defaults to `KRFbrothers/Dua-ai` | Write-capable token for the selected repository. Separate from both Node API tokens; see [engine setup](google-cloud-backend/README.md). |

### Node API (dua-api on Cloud Run, local)

The backend lives in `web/`. On Cloud Run (`dua-api`, see [web/README.md](web/README.md)) and locally it runs `web/src/server/service-handler.mjs`, with shared logic in `web/src/core/`. To run the API locally, start from the repository root:

```sh
cd web
npm ci --ignore-scripts --no-audit --no-fund
# Preserve an existing .env; otherwise create it from the safe template.
[ -e .env ] || cp .env.example .env
# Edit .env for real integrations; the unfilled template reports missing setup.
npm start
```

This binds to `127.0.0.1:3000` (`PORT` overrides the port) and serves only `/api/*`; `GET /api/config` reports configuration, while `/` returns 404 because there is no web UI. Without runtime configuration, dependent features report setup errors. Run `npm test` from `web/` for the backend tests. Local settings go in `web/.env`, which `.gitignore` excludes (`.env` and `.env.*`); never commit it. The backend is deployed to Cloud Run as `dua-api` with `bash web/deploy-cloudrun.sh`. Deploy the backend before installing an APK built from this version: new APKs request streaming, which older backends reject.

### Cloud Run / local Python engine

For the engine's mocked tests, run from the repository root:

```sh
python3 -m venv /tmp/dua-gcb-tests
/tmp/dua-gcb-tests/bin/python -m pip install Flask==3.1.3 pytest==8.3.5
/tmp/dua-gcb-tests/bin/python -m pytest -q google-cloud-backend/tests
```

To run the actual service locally, install [requirements.txt](google-cloud-backend/requirements.txt) in a separate virtual environment, export its runtime settings, then run `python app.py` from `google-cloud-backend/`. It serves HTTP on port 8080 by default (`PORT` overrides it); `GET /` is a public health/capability response, not an authenticated generation test. Deployment uses the [Procfile](google-cloud-backend/Procfile); follow the [engine README](google-cloud-backend/README.md) for Google credentials, permissions and deployment.

For a local engine `.env`, follow the [copy/export instructions](google-cloud-backend/README.md#local-environment-file). The Python service does not load that file itself. To redeploy, use the standard command in [Redeploy from Cloud Shell](google-cloud-backend/README.md#redeploy-from-cloud-shell).

### Android app

Open the repository in Android Studio, select JDK 17 and install Android SDK 35 / Build Tools 35.0.0. Set `sdk.dir` in ignored `local.properties` if Android Studio has not done so. For a debug APK, run from the root:

```sh
bash ./gradlew :app:assembleDebug --no-daemon --max-workers=2
```

The output is `app/build/outputs/apk/debug/app-debug.apk`; permanent personal builds use [SIGNING.md](SIGNING.md). Native Google login must match the certificate signing the installed APK.

The Android build requires HTTPS endpoints, so the loopback HTTP servers above are for API development/tests. To use a reachable HTTPS development backend for **both** chat and data, pass both overrides when building:

```sh
bash ./gradlew :app:assembleDebug \
  -PduaApiBaseUrl=https://YOUR-DEV-BACKEND.example \
  -PduaChatBaseUrl=https://YOUR-DEV-BACKEND.example \
  --no-daemon --max-workers=2
```

Replace the example with your actual endpoint. Build-time URL changes require rebuilding/installing the APK; deploying a backend alone does not change the app's routing.

## Android and routine checks

See [ANDROID_UI_GUIDE.md](docs/ui/ANDROID_UI.md) for the compact mobile UI, screen/MVVM map, debug-only sample-conversation preview, setup and implemented/pending features.

The launcher is `com.dua.ai.MainActivity`; [app/build.gradle.kts](app/build.gradle.kts) defines the SDK, endpoint overrides and signing inputs described above.

From the repository root, run the same Android command as [Project checks](.github/workflows/checks.yml), including native UI render recording:

```sh
bash ./gradlew :app:testDebugUnitTest -Pdua.ui-record --no-daemon --max-workers=2
```

The default Android suite includes JVM and Compose/Robolectric interaction tests. `-Pdua.ui-record` also records the current UI fixtures to `app/build/approved-ui/`; omitting that flag still runs those tests, but does not record their PNGs. HTML results are in `app/build/reports/tests/testDebugUnitTest/` and JUnit XML in `app/build/test-results/testDebugUnitTest/`.

The separate historical screenshot classes under `app/src/test/java/com/dua/ai/screenshots/` are **excluded by default**, including in CI. To include them and record their renders:

```sh
bash ./gradlew :app:recordRoborazziDebug -Pdua.screenshots --no-daemon --max-workers=2
```

Those renders go to `app/build/outputs/roborazzi/`. Recording images is not a pixel-baseline comparison or a physical-device check. The two opt-in flags have different purposes: `dua.ui-record` records current UI fixtures; `dua.screenshots` includes the historical screenshot classes. This behavior is configured in [app/build.gradle.kts](app/build.gradle.kts); the fixture capture paths are in [ApprovedNavigationUiTest.kt](app/src/test/java/com/dua/ai/ui/ApprovedNavigationUiTest.kt), [CompactChatUiTest.kt](app/src/test/java/com/dua/ai/ui/chat/CompactChatUiTest.kt) and [HomeScreenshotTest.kt](app/src/test/java/com/dua/ai/screenshots/HomeScreenshotTest.kt).

First-run Android checks need dependency access and writable Gradle/Maven caches: Robolectric may download an instrumented Android runtime. A dependency-download failure before test assertions is not evidence of an app assertion failure; inspect the test report. The older local blockers in [RELEASE-CHECKLIST.md](docs/RELEASE_CHECKLIST.md) remain historical validation records.

[Project checks](.github/workflows/checks.yml) runs three separate jobs on PRs, pushes to main and manual runs: Android JVM/Compose tests with recorded UI fixtures, the backend's full `node:test` suite, and the Cloud Run engine's mocked pytest suite. It uploads `android-unit-test-reports` (HTML/JUnit plus `approved-ui` renders), `backend-unit-test-report` and `google-cloud-engine-test-report` (`google-cloud-backend/pytest-results.xml`). It does not assemble or sign an APK. The backend job uses Node 24 and locked dependencies; the engine job installs only Flask/pytest in an isolated environment. These checks require no provider keys, live Supabase/Vertex credentials or signing secrets.

For the backend, use Node 24+ and run from the repository root:

```sh
cd web
npm ci --ignore-scripts --no-audit --no-fund
npm run test:ci
```

These are the install/test commands from CI. `npm test` runs the same test files without writing the CI JUnit report; `npm run test:ci` writes `web/backend-test-results.xml` (repository-relative path). Root `.nvmrc` and `.node-version` select Node 24; `web/.npmrc` enforces the package's Node >=24.0.0 requirement at install time. CI already pins Node 24. All locked packages come from the npm registry with integrity hashes: `unpdf` handles PDFs, and `cfb` reads the container for legacy BIFF8 XLS files. XLSX/ODS ZIP/XML and CSV worksheet extraction run locally without the `xlsx` package or a CDN download, formula evaluation or external XML entities. ZIP parts/inflation and output rows/columns/characters are bounded; encrypted, malformed or oversized worksheet XML returns a clear 422 error. The scripts and Node requirement are declared in [web/package.json](web/package.json).

For a non-mutating anonymous database access smoke check, see [Supabase verification](SUPABASE.md#anonymous-access-smoke-check). `cd web && npm run verify:rls` uses exported public Supabase credentials and skips cleanly when they are absent. Live verification is optional and is not run by the credential-free CI suite.

After installation, the separate Imagine smoke check is `node scripts/imagine-smoke.mjs` **from `web/`**. [That script](web/scripts/imagine-smoke.mjs) starts an ephemeral loopback HTTP server and uses fake providers/storage; it needs no real provider keys and is not run by `Project checks`.

The Android cache is stored in app-private preferences; Android backup/device transfer exclude cached chat history, account sessions and automatic-sync consent. Other preferences, including cached memory and project instructions, can be backed up according to device settings. Signed-in server data remains authoritative and is not replaced by a restored cache. A dua-api deployment updates the backend only; Android code changes require building and installing a new APK.

**Settings → Storage** provides real cache/cloud usage, manual password-protected full backups through Android's file picker, and optional daily Google Drive backups on unmetered networks. Automatic mode requests separate private app-folder permission, protects the saved archive password with Android Keystore, shows last-success/progress/retry and keeps the latest three verified backups. Archives include Personal notes and available raw Dua AI files; restore verifies files, preserves existing data and asks for confirmation. See [contents, limits, Google Cloud setup and recovery steps](docs/STORAGE_BACKUP.md). Legacy JSON export/import remains available in Data controls.

### Projects and custom instructions

The dedicated **Projects** screen shows real user-created projects and chats, without seeding demo projects. Personal remains the internal Home workspace; untouched legacy Coding/Business starters are hidden. See [Projects flow](docs/PROJECTS_REFERENCE_FLOW.md) for current screens, sources, and legacy-data handling. Each chat keeps its project, and history search is project-scoped. Save instructions and project memory, then sync them before chatting; replies use the owner’s server definition. [PROJECTS.md](docs/features/PROJECTS.md) describes the original persistence/conflict contract, and [MEMORY.md](docs/features/PROJECT_MEMORY.md) covers project notes. Tasks, memory, planner and archive settings are available from **Edit project → Tasks, memory & planner**. To-do edit/complete/reopen and confirmed delete are described in [TASKS.md](docs/features/TASKS.md); due dates and opt-in device reminders in [REMINDERS.md](docs/features/REMINDERS.md); deadline/undated task planning in [PLANNER.md](docs/features/PLANNER.md). Physical-device/live sync verification remains separate from source-level tests.
