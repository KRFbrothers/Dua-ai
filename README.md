# Dua AI

Dua contains an Android client in `app/` and the mobile browser app with Gemini backend in `web/`.

## Browser app

The browser app includes home, chat, settings, attachments, model selection and voice-input screens. Gemini text replies use the server-side key. Follow [web/README.md](web/README.md) for Netlify deployment and local use.

The root `netlify.toml` publishes `web/dist` and deploys the Gemini function. Store `GEMINI_API_KEY` and `GEMINI_MODEL` in private Netlify configuration. Never commit API keys.

Voice AI replies, attachment processing, history persistence and most settings remain unfinished; see web documentation for current limits.

## Android client

Open the repository in Android Studio. The launcher is `com.dua.ai.MainActivity`. Build instructions are in [GRADLE.md](GRADLE.md).

Android replies and session history currently use simulated data; the web Gemini backend is not yet integrated into Android.

## Validation

With Node 24+, run `npm test` from `web/` to run the local and Netlify endpoint tests. Android requires its SDK and a separate clean build.
