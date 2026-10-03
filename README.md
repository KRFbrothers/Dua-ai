# Dua AI

Dua contains an Android client in `app/` and the mobile browser app with Gemini backend in `web/`.

## Browser app

The browser app includes home, chat, settings, attachments, model selection and voice-input screens. Gemini text replies use the server-side key. Follow [web/README.md](web/README.md) for Netlify deployment and local use.

The root `netlify.toml` publishes `web/dist` and deploys the Gemini function. Store `GEMINI_API_KEY` and `GEMINI_MODEL` in private Netlify configuration. Never commit API keys.

Voice AI replies, account/cloud sync and most settings remain unfinished; see [FEATURES.md](FEATURES.md) for the ordered implementation list.

## Android client

Open the repository in Android Studio. The launcher is `com.dua.ai.MainActivity`. Build instructions are in [GRADLE.md](GRADLE.md).

Android text replies use the same Gemini backend as web. Local chat history saves separate conversations, drafts and selected models; Home can search, reopen and delete them. Account/cloud synchronization remains planned.

## Validation

With Node 24+, run `npm test` from `web/` to run the local and Netlify endpoint tests. With JDK 17 and Android SDK 35 installed, run `bash ./gradlew :app:assembleDebug :app:testDebugUnitTest` from the repository root.

[Project checks](.github/workflows/checks.yml) runs both commands on pull requests and pushes to `main`, and supports manual runs from the Actions tab. Tests use mocked providers/local HTTPS and do not require Gemini credentials. Android unit-test reports are uploaded even when tests fail. These checks do not deploy the application, verify a physical device, or call live Gemini. Required branch-protection checks must be configured separately.

## Native interface

The Android Compose launcher now follows the current web design: I am Dua home, centered-logo chat, Material 3 composer, model sheet, Add to chat, Settings and Voice screens. Navigation preserves the current in-memory conversation and draft; New chat resets it. Native Dua and Gemini selections use the same HTTPS backend as the web app. Images, PDF and TXT attachments are sent with the current message. Other providers and AI voice remain planned features. No WebView or live website is required to render the Android interface.

## Android AI backend

Android sends the same `model`, `messages` and optional `attachments` JSON to `/api/chat` as web. The default backend is `https://celadon-halva-adafe4.netlify.app`. Override it with `-PduaApiBaseUrl=https://your-project.netlify.app` when building. Set GEMINI_API_KEY and GEMINI_MODEL on the server only; no API key belongs in the APK. The hosted endpoint must permit native clients; if Netlify requires browser/team login, adjust access policy before testing. Native AI voice remains a future feature. Attachment limits and retention are documented in web/README.md. Android/backend integration has not been live-tested here.
