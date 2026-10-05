# Dua AI

Dua includes a native Android Compose client in `app/` and a mobile web app with server-side API functions in `web/`. Production uses **https://dua-cyan.vercel.app**. Both clients use the same backend; Android renders its own interface without a WebView.
Simple tasks use the coordinator; independent Android and web tasks can use bounded specialists.

## Current features

- Local conversations, drafts, model selection, search, export/import, sharing and data controls.
- Gemini plus configurable ChatGPT, Claude, Grok and Kimi adapters. TXT attachments work on all providers; images/PDF depend on the selected provider and configured model. See [PROVIDERS.md](PROVIDERS.md).
- Chat Web: Off/Search/Read link modes and clickable source footers. Search requires Brave configuration; public page reading uses the existing chat provider. Web mode starts Off.
- Copy, retry and speaker reply icons; device speech recognition/TTS and explicit foreground hands-free turns. Optional generated reply audio uses an authenticated, owner-restricted server integration.
- Appearance, permissions, local notifications, reviewed memory notes/proposals and local privacy controls.
- Supabase email accounts, reviewed cloud snapshots and opt-in foreground automatic sync on web and Android. Optional recovery, web Google login, account deletion, owner/login restrictions and durable daily request limits require their server settings.
- A configured read-only GitHub repository connector and browser local camera/screen previews.

API keys and optional credentials remain server-side. Preparing a feature does not activate a provider or paid service. OpenAI key setup remains deferred. Background/realtime sync, model-based memory learning, native Google handoff, full-duplex/background voice, remote computer control and general plugin tools remain unfinished. See [EIGHT-FEATURE-SETUP.md](EIGHT-FEATURE-SETUP.md) for activation and remaining scope.

## Web and hosting

Use Node 24+ and run from `web/`:

```sh
npm start
npm test
npm run build
```

Local settings use an ignored `.env`; available names are in [web/.env.example](web/.env.example). Vercel uses Root Directory `web`, Framework Other and the checked-in [web/vercel.json](web/vercel.json). See [VERCEL.md](VERCEL.md), [web/README.md](web/README.md), [SUPABASE.md](SUPABASE.md) and [ACCOUNT-SETUP.md](ACCOUNT-SETUP.md).

Legacy Netlify files remain for the earlier backend contract. Current provider/setup/advanced integrations use Vercel or the local Node handler.

## Android and routine checks

Open the repository in Android Studio. The launcher is `com.dua.ai.MainActivity`; checked-in Gradle configuration uses JDK 17 and Android SDK 35. The default API URL is `https://dua-cyan.vercel.app`. Override it with the `duaApiBaseUrl` Gradle property or `DUA_API_BASE_URL` environment variable when testing another HTTPS backend. Provider keys never belong in Android resources.

Run source compilation and JVM tests without packaging an APK:

```sh
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

[Project checks](.github/workflows/checks.yml) runs web tests and Android compilation/JVM tests on PRs, pushes to main and manual runs. It does not assemble an APK. Tests use mocked services/local HTTPS and require no live provider credentials. Android test reports are preserved on failures; branch-protection rules are configured separately.

Android local data remains on the device. Website deployment updates the backend/web app; installed native code changes require a later Android release. APK packaging is deferred at the user's request. Physical-device and live-service checks remain separate from automated tests. [FEATURES.md](FEATURES.md) records implementation history and validation.
