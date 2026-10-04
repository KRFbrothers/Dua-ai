# Optional connectors, media, and generated voice

These foundations are for a personal installation. The browser capability page can preview a camera or screen locally. The GitHub action lists repositories using a separately configured server token. Generated audio uses an optional ElevenLabs adapter; existing device speech remains the default.

## Access and credentials

The router must verify Supabase sign-in and the personal-account allowlist before calling `handleAdvanced(request, {env, fetcher, authorized: true})`. The adapter rejects calls without authorization and rejects foreign browser origins. Every operation starts from a user action; it never runs a connector or generated-audio request because a model suggests it.

Environment variables belong in server settings, never in the browser or an Android APK:

| Variable | Value | Required for |
| --- | --- | --- |
| `ENABLE_GITHUB_CONNECTOR` | `1`, after reviewing token permissions | GitHub action |
| `GITHUB_CONNECTOR_TOKEN` | A fine-grained GitHub token scoped to selected personal repositories with read-only repository access | GitHub action |
| `ENABLE_GENERATED_VOICE` | `1`, after checking provider quota/pricing | Generated audio |
| `ELEVENLABS_API_KEY` | API key from your ElevenLabs account | Generated audio |
| `ELEVENLABS_VOICE_ID` | A permitted voice ID from that account | Generated audio |

Leave enable flags unset to keep the optional services disabled. `advancedConfiguration(env)` exposes only configured flags, supported actions, and limits; no token, API key, or voice ID. Remove `ENABLE_GITHUB_CONNECTOR` or set it to `0` to disable new requests, then revoke the token in GitHub Settings → Developer settings → Personal access tokens. This is server-token setup, not an OAuth connection or a browser token store.

## GitHub read-only action

`POST /api/connectors/github` accepts only `{"action":"list-repositories"}`. It requests up to 30 repositories owned by the token's GitHub user. Results may include private repository names; access must remain restricted to the owner's authenticated account. The token's permissions determine which repositories are visible. There are no repository writes, arbitrary URLs, plugin execution, or background polling. Upstream redirects are refused and provider errors never return credentials.

## Local camera and screen previews

Load `/media-session.js` and create `DuaMediaSession({preview: videoElement, onState, onInterrupt})`. Call `startCamera()` or `startScreen()` from a button click and use `stop()` to release capture. No permissions are requested when constructing the session. Screen capture is commonly unavailable on Android browsers; the page reports support and suggests desktop Chrome or Edge. Use HTTPS, or localhost for development.

Both capture methods request video without microphone audio, attach it to a muted local preview, and never record or upload it. Browser Stop Sharing, page hiding, navigation, and `destroy()` stop tracks. A pending permission request that finishes after Stop releases its tracks immediately. If a browser denies or cancels permission, retry from the same Start button.

This setup does not provide AI screen understanding, a remote computer, Android native capture, or computer control. Those need a separately authorized transport and platform-specific implementation. A preview's success must not be described as remote control or video analysis.

## Voice and interruption

`interrupt()` cancels device speech and calls the page's `onInterrupt` callback; wire this callback to pause/abort any generated-audio playback and generation request. This establishes explicit foreground interruption. Background voice, always-on listening, full duplex, and automatic interruption from speech detection remain unfinished.

`POST /api/voice/audio` accepts only `{"text":"Text to speak"}`. After opt-in server setup and account authorization, the adapter requests MP3 from ElevenLabs' fixed text-to-speech endpoint using `eleven_multilingual_v2`. Input is limited to 2000 characters; output is limited to 8 MiB. Request audio only after a user selects generated voice and presses Speak. Provider plans and quotas may charge for use; this adapter stays disabled by default.

When playing the response, create an object URL for its audio blob and revoke the URL after Stop, playback completion, or navigation. Send only the reply the user selected, not chat history or files. An unavailable provider must leave device speech and local chat usable. Deploy after adding environment variables and test with a short, non-private phrase before using personal content.

## Validation

Run `node --test advanced-integrations.test.mjs` from `web/`. The tests use mock providers and media devices; they check authorization, fixed read-only actions, bounded requests/audio, disabled defaults, permission denial, interruption, and capture cleanup. Actual permission dialogs and Android/desktop browser differences still need device testing.
