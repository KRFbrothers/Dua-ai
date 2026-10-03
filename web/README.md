# Dua browser app and Gemini backend

This folder contains the browser app created from the supplied references. The native Android application lives separately under `app/`.

## Deploy from GitHub on Netlify

1. Connect this repository to the existing Netlify project under Project configuration → Build & deploy → Continuous deployment → Link repository, or import it as a new project.
2. Select the branch containing these files. The root `netlify.toml` sets base directory `web`, publish directory `dist`, and functions directory `netlify/functions`. No build command is needed.
3. In Netlify Environment variables, add `GEMINI_API_KEY` as a secret available to Functions. Also add `GEMINI_MODEL` using an available model ID from your Google AI Studio project. There is no hardcoded model fallback. Never add your key to GitHub or browser code.
4. Deploy from the repository. A drag-and-drop HTML ZIP does not deploy the Gemini functions.
5. Open `/api/config` on the deployed origin to check configuration. It returns only whether both the key and model are configured, never its value.

Gemini is available through Dua and Gemini in the model picker. Other providers remain unavailable. Voice mode provides browser speech input, not AI audio replies. Camera/screen sharing in voice mode are unavailable. Web search and Memory remain disabled/off and marked Coming soon. Files are selected locally, and only filenames/metadata are retained; they are not uploaded or sent to Gemini.

## Local use

With Node 24+, copy `.env.example` to `.env`, enter your key and available model ID there, and run `npm start` from `web/`. Open http://localhost:3000. Opening HTML files directly provides only the interface.

Run `npm test` for Node and Netlify endpoint tests with mocked Gemini responses. Live Gemini API testing requires a configured key and has not been performed.

## Mobile interface

The default browser pages now follow the native Android mobile layout, including the responsive composer, model sheet, Settings detail pages, attachment selection cards and Voice cube. Chat calls the real `/api/chat` backend, clears accepted drafts and supports Stop with client-side request cancellation. Failed requests restore the draft. In-tab session storage preserves the current chat across navigation; this is not account-backed persistent history.

`/preview/` remains a separate design sandbox with simulated replies. Account, appearance, notification and permission samples remain explicitly labelled design states. Voice keeps browser speech input where supported; native Voice currently demonstrates animation without recording.
