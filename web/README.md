# Dua browser app and Gemini backend

This folder contains the browser app created from the supplied references. The native Android application lives separately under `app/`.

## Deploy from GitHub on Netlify

1. Connect this repository to the existing Netlify project under Project configuration → Build & deploy → Continuous deployment → Link repository, or import it as a new project.
2. Select the branch containing these files. The root `netlify.toml` sets base directory `web`, publish directory `dist`, and functions directory `netlify/functions`. No build command is needed.
3. In Netlify Environment variables, add `GEMINI_API_KEY` as a secret available to Functions. Also add `GEMINI_MODEL` using an available model ID from your Google AI Studio project. There is no hardcoded model fallback. Never add your key to GitHub or browser code.
4. Deploy from the repository. A drag-and-drop HTML ZIP does not deploy the Gemini functions.
5. Open `/api/config` on the deployed origin to check configuration. It returns only whether both the key and model are configured, never its value.

Gemini is available through Dua and Gemini in the model picker. Other providers remain unavailable. Voice mode provides browser speech input, not AI audio replies. Camera/screen sharing in voice mode are unavailable. Web search and Memory remain disabled/off and marked Coming soon. JPEG, PNG, WebP, PDF and UTF-8 TXT files can be sent to Gemini with the current message.

## Local use

With Node 24+, copy `.env.example` to `.env`, enter your key and available model ID there, and run `npm start` from `web/`. Open http://localhost:3000. Opening HTML files directly provides only the interface.

Run `npm test` for Node and Netlify endpoint tests with mocked Gemini responses. Live Gemini API testing requires a configured key and has not been performed.

## Mobile interface

The default browser pages now follow the native Android mobile layout, including the responsive composer, model sheet, Settings detail pages, attachment selection cards and Voice cube. Chat calls the real `/api/chat` backend, clears accepted drafts and supports Stop with client-side request cancellation. Failed requests restore the draft. Local browser storage now preserves multiple chats, drafts and selected models across visits. Home supports search, reopening and confirmed deletion. The previous in-tab chat migrates once; storage failures show a warning. This is device-local history, not account/cloud synchronization. See [../FEATURES.md](../FEATURES.md) for the remaining feature list.

`/preview/` remains a separate design sandbox with simulated replies. Account, appearance, notification and permission samples remain explicitly labelled design states. Voice keeps browser speech input where supported; native Voice currently demonstrates animation without recording.

## Chat request protection

On Netlify, `/api/chat` has an edge-enforced limit of 5 requests per 60 seconds for each IP/domain combination. Requests above the allowance are rewritten to a provider-free JSON endpoint returning HTTP 429 and `Retry-After: 60`. `/api/config` does not consume this allowance. Browser and native Android clients receive the same retry guidance; clients sharing a public IP also share the allowance.

This rule limits bursts, not total daily Gemini usage or spending. The endpoint still accepts native requests without an Origin header and does not authenticate users. Multiple IPs can consume the shared provider quota. Account authentication and project-wide quota budgeting remain pending work; no private token is embedded in browser code or the APK.

Netlify enforces the rule only on deployed requests, not the local Node server or direct function unit tests. After deployment, confirm the rule in the Netlify deploy log's rate-limiting section. Unit tests validate routing and the safe 429 response; they do not simulate Netlify's counters or prove live edge enforcement. See [Netlify rate-limiting documentation](https://docs.netlify.com/manage/security/secure-access-to-sites/rate-limiting/).

## Attachments

Web and native Android send base64 file contents through the shared `/api/chat` endpoint. Select up to 3 files with a combined size of 2 MiB; each TXT file is limited to 64 KiB. The backend validates file type, signatures, base64 and size before contacting Gemini. Requests are limited to 3 MiB, while conversation JSON retains its separate 256 KiB limit.

Files apply only to the current user message. Reattach a file for later analysis. Pending bytes live in browser session storage or Android ViewModel memory; persistent history keeps filenames and replies, not file contents. Successful replies clear the selection. Failed or stopped requests retain it for retry. Remove/New chat clears it. Native camera capture currently supplies a preview-sized JPEG, not a full-resolution photo.

Large files, DOCX, video/audio, durable file storage and provider File API uploads remain pending. Mobile Chromium was checked with a mocked provider; live Gemini media handling and physical Android picker/camera behavior remain unverified.
