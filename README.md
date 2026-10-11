# Dua AI

Dua is a native Android Compose app in `app/`, backed by server-side API functions in `web/` (deployed on Vercel). There is no web client; `web/` contains only the backend API. Production uses https://dua-cyan.vercel.app.

Dua is a personal, single-user app: it is built for its owner only and installed on his own device(s) as a debug or sideloaded APK. There are no other users, no Google Play release and no monetisation. The backend is meant to stay locked to the owner's account (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL`) with provider spending limits.

## Current features

- Server-backed conversations, drafts and memory with an app-private cache for offline viewing, full-message/draft search, export/import, sharing and data controls. Search is project-scoped with match snippets; see [HISTORY-SEARCH.md](HISTORY-SEARCH.md).
- Gemini plus configurable ChatGPT, Claude, Grok and Kimi adapters with streaming replies and a Reasoning mode (collapsible Thinking block on Gemini, Grok and reasoning ChatGPT models). Text and code files (TXT, CSV, MD, JSON, source code) work on all providers; images/PDF depend on the selected provider and configured model. See [PROVIDERS.md](PROVIDERS.md).
- Chat Web: Off/Search/Read link modes and clickable source footers. Search requires Brave configuration; public page reading uses the existing chat provider. Web mode starts Off.
- Copy, retry and speaker reply icons; device speech recognition/TTS and explicit foreground hands-free turns. Optional generated reply audio uses an authenticated, owner-restricted server integration.
- Independent appearance/accent preferences (Preferences DataStore), approved Sidebar/Aura/Profile navigation, permissions, local notifications, reviewed server-backed memory notes/proposals and privacy controls for the cache and account.
- Native Google sign-in (Supabase accounts): a single welcome screen at first launch with **Sign in with Google** or **Skip for now**; the sidebar avatar opens **Profile & Settings**; Google sign-in is under **Account**, and confirmed Logout stays in the profile hub. Signed-in chats and memory use the server as the source of truth; the phone keeps a disposable cache and foreground sync refreshes it. Without an account, the phone-only cache is temporary and cannot become authoritative. The Android app has no email/password sign-in.
- A configured read-only GitHub repository connector.

API keys and optional credentials remain server-side. Preparing a feature does not activate a provider or paid service. OpenAI key setup remains deferred. Background/realtime sync, model-based memory and other advanced integrations remain pending.

## Project roadmap

See [ROADMAP.md](ROADMAP.md) for the product and engineering roadmap: the Grok-style product direction, feature status, phased plan (UI foundation and reasoning, files and search, Imagine, voice, coding/Build mode), privacy and cost controls, and open decisions.
Use [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md) to verify the owner-only lock, provider spending limits, device/staging checks, and installing the APK on your own phone. The production configuration cannot be verified from this source checkout alone.
Use [SIGNING.md](SIGNING.md) to store the permanent personal signing key and run the manual [Build personal APK](.github/workflows/personal-apk.yml) workflow. Release builds require the pinned certificate; they cannot fall back to another machine's debug key.

## Backend API and hosting

The backend lives in `web/`: Vercel functions in `web/api/` route to `web/src/server/vercel-handler.mjs`, with shared logic in `web/src/core/`. To run the API locally, use Node 24+ from `web/`:

```sh
npm ci
npm start
```

This serves only `/api/*` on `http://localhost:3000`. Run `npm test` for the backend tests. Local settings go in `web/.env`, which `.gitignore` excludes (`.env` and `.env.*`); never commit it. The backend is deployed on Vercel with Root Directory `web`; `web/vercel.json` only sets function duration (300 s) and response headers. Deploy the backend before installing an APK built from this version: new APKs request streaming, which older backends reject.

## Android and routine checks

Open the repository in Android Studio. The launcher is `com.dua.ai.MainActivity`; checked-in Gradle configuration uses JDK 17 and Android SDK 35. The default API URL is `https://dua-cyan.vercel.app`.

Run source compilation and JVM tests without packaging an APK:

```sh
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

[Project checks](.github/workflows/checks.yml) runs Android compilation/JVM tests and the backend's full `node:test` suite in separate jobs on PRs, pushes to main and manual runs. It does not assemble or sign an APK. The backend job uses Node 24, installs locked dependencies and saves a JUnit report even when tests fail. It requires no provider keys or signing credentials.

Run backend tests locally with `cd web && npm ci && npm test`; `npm run test:ci` runs the same suite with console output and a `backend-test-results.xml` report. Install the locked dependencies first (`unpdf` and SheetJS). SheetJS is fetched from `cdn.sheetjs.com`, which must be accessible from the build environment.

The Android cache is stored in app-private preferences; Android backup/device transfer exclude cached chat history, account sessions and automatic-sync consent. Other preferences, including cached memory and project instructions, can be backed up according to device settings. Signed-in server data remains authoritative and is not replaced by a restored cache. A Vercel deployment updates the backend only; Android code changes require building and installing a new APK.

### Projects and custom instructions

Home → **Projects** offers Personal, Coding and Business, plus custom projects. Each chat keeps its project, and the history drawer searches that project only. Save instructions and project memory, then sync them before chatting; the backend reads the owner’s server snapshot. See [PROJECTS.md](PROJECTS.md) for limits, conflict handling and the first version’s scope, and [MEMORY.md](MEMORY.md) for separate project notes. **Projects → Tasks** adds separate to-do lists with edit, complete/reopen and confirmed delete; sync and JSON backup include tasks. See [TASKS.md](TASKS.md). Optional due dates and opt-in device reminders are implemented; see [REMINDERS.md](REMINDERS.md). **Projects → Daily planner** groups deadlines and undated tasks with date navigation, project filtering and completion; see [PLANNER.md](PLANNER.md). These source changes need an updated APK; physical-device/live sync verification remains pending.
