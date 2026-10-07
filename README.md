# Dua AI

Dua is a native Android Compose app in `app/`, backed by server-side API functions in `web/` (deployed on Vercel). There is no web client; `web/` contains only the backend API. Production uses https://dua-cyan.vercel.app.

## Current features

- Local conversations, drafts, model selection, search, export/import, sharing and data controls.
- Gemini plus configurable ChatGPT, Claude, Grok and Kimi adapters. TXT attachments work on all providers; images/PDF depend on the selected provider and configured model. See [PROVIDERS.md](PROVIDERS.md).
- Chat Web: Off/Search/Read link modes and clickable source footers. Search requires Brave configuration; public page reading uses the existing chat provider. Web mode starts Off.
- Copy, retry and speaker reply icons; device speech recognition/TTS and explicit foreground hands-free turns. Optional generated reply audio uses an authenticated, owner-restricted server integration.
- Appearance, permissions, local notifications, reviewed memory notes/proposals and local privacy controls.
- Supabase email accounts, reviewed cloud snapshots and opt-in foreground automatic sync. Optional recovery, native Google sign-in, account deletion, owner/login restrictions and durable server-side quotas are prepared.
- A configured read-only GitHub repository connector.

API keys and optional credentials remain server-side. Preparing a feature does not activate a provider or paid service. OpenAI key setup remains deferred. Background/realtime sync, model-based memory and other advanced integrations remain pending.

## Project roadmap

See [ROADMAP.md](ROADMAP.md) for the product and engineering roadmap: the Grok-style product direction, feature status, phased plan (UI foundation and reasoning, files and search, Imagine, voice, coding/Build mode), privacy and cost controls, and open decisions. The underlying gap analysis is in [docs/grok-style-plan.md](docs/grok-style-plan.md).
Use [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md) to verify production access controls, provider spending limits, device/staging checks, and Android release preparation. The production configuration cannot be verified from this source checkout alone.

## Backend API and hosting

The backend lives in `web/`: Vercel functions in `web/api/` route to `web/src/server/vercel-handler.mjs`, with shared logic in `web/src/core/`. To run the API locally, use Node 24+ from `web/`:

```sh
npm start
```

This serves only `/api/*` on `http://localhost:3000`. Local settings go in `web/.env`, which `.gitignore` excludes (`.env` and `.env.*`); never commit it. The backend is deployed on Vercel with Root Directory `web`.

## Android and routine checks

Open the repository in Android Studio. The launcher is `com.dua.ai.MainActivity`; checked-in Gradle configuration uses JDK 17 and Android SDK 35. The default API URL is `https://dua-cyan.vercel.app`.

Run source compilation and JVM tests without packaging an APK:

```sh
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

[Project checks](.github/workflows/checks.yml) runs Android compilation and JVM unit tests on PRs, pushes to main and manual runs. It does not assemble or sign an APK. The backend currently has no automated test suite in this repository.

Android local data is stored in app-private preferences; Android backup/device transfer can include chat history and preferences according to device settings. A Vercel deployment updates the backend only; installed native code changes require a later Android release.
