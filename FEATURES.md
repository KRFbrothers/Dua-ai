# Dua features: current state and remaining work

Updated 8 October 2026. Dua is an Android app (`app/`) backed by a server-side API in `web/` deployed on Vercel. The former web client (pages, browser storage, browser speech/camera previews and the `/preview/` design sandbox) was removed; this document describes only the Android app and the backend. "Prepared" means implemented in source but dependent on server configuration that this repository cannot verify. See [ROADMAP.md](ROADMAP.md) for phase planning.

| Order | Feature | Current state | Remaining work |
| --- | --- | --- | --- |
| 1 | Persistent chat history | Saved chats, search, reopen, draft/model recovery and confirmed deletion in app-private storage | Physical Android restart/file-picker/import verification |
| 2 | Attachment processing | Inline JPEG/PNG/WebP/PDF and UTF-8 text/code (TXT, CSV, MD, JSON, source) input with validation, automatic photo resizing and retry/Stop selection retention | Live provider and physical Android verification; larger documents, DOCX and durable/provider file storage |
| 3 | Camera/photos | Delegated full-photo camera capture and photo/document pickers feed attachment processing | Physical-device camera/picker, compression/EXIF and low-memory verification |
| 4 | AI voice conversation | Speech-to-draft, explicit reply read-aloud, foreground hands-free turns and optional generated reply voice | Physical speech/TTS/permission verification; full duplex/barge-in and background sessions |
| 5 | Account sign-in/profile/logout | Supabase email auth, encrypted native sessions, display name, device logout, email recovery code, optional native Google sign-in and optional account deletion | Live email/Google/deletion configuration and physical-device verification |
| 6 | Cloud history sync | Reviewed manual chat/draft/memory snapshots with RLS ownership and revision conflicts; opt-in foreground automatic sync | Apply migration/configure project; live cross-device verification; background/realtime sync |
| 7 | Other AI providers | ChatGPT, Claude, Grok and Kimi adapters with a configured-provider picker; TXT everywhere, opt-in images/PDF per provider | Configure keys/model IDs and verify live requests; Grok/Kimi PDF ingestion |
| 8 | Web search and link reading | Chat Web: Off/Search/Read link modes with source footers; Search uses Brave, Read link reads one public HTTPS page | Configure Brave key; live retrieval verification |
| 9 | Memory | Opt-in local notes (inspect/edit/delete), reviewed remember-statement proposals, notes included in reviewed cloud snapshots | Model-based extraction/embeddings |
| 10 | Connectors/plugins | Read-only GitHub repository listing with owner restriction, feature flag and token | OAuth and other connector/plugin tools |
| 11 | Computer mode/screen sharing/video | Not implemented beyond photo capture for attachments | Native screen capture, analysis and remote control |
| 12 | Appearance/font settings | Persisted Dark/Light/System themes, Default/System/Serif/Monospace fonts and haptic preference | Physical font/language rendering and vibration verification |
| 13 | Notifications | Opt-in generic local reply notifications, permission flow and test action | Physical delivery/tap verification; server push and durable background processing |
| 14 | Data controls/privacy/sharing | Local JSON export/import, confirmed delete-all, individual deletion, plain-text sharing of completed messages and reviewed cloud clearing | Physical document-provider verification |
| 15 | Microphone/camera permissions | Native microphone permission with denial/settings recovery; camera capture delegated to the external camera app | Physical verification and new capabilities |
| 16 | Usage/account protection | Per-instance IP burst limit; optional login requirement, owner restriction and durable daily request quota | Activate migration/configuration and verify in staging. Currency spend limits remain future |

## Local history

Android stores serialized chat records in app-private SharedPreferences. Messages, draft and selected model are retained; Home supports title/preview search, reopening and confirmed deletion. Deleting a chat removes that local record; clearing app storage removes all local history. Account/cloud sync is separate and opt-in (see below). Android backup and device transfer can include chat history according to device settings; see [PRIVACY.md](PRIVACY.md).

## Attachments and photos

The app sends at most 3 files / 2 MiB total with the current message; each text/code file is limited to 256 KiB. The backend validates contents and adds inline image/PDF or decoded text parts only to the final user message. Successful replies clear the selection; Stop/errors retain it. History stores filenames and replies without file bytes; pending bytes are held only in memory. The Android app keeps the decoded text of text/code files with the user message on the device (up to 32,000 characters, not exported or synced) so follow-up questions can use it.

Camera capture is delegated to the device camera app through `TakePicture` and a restricted FileProvider cache path, so no CAMERA permission is needed. A cancelled/failed capture keeps the previous selection. Capture files are removed after reading, on failure/cancel or when launch fails; files abandoned by process death are pruned after 24 hours when capture next starts.

When a selection exceeds 2 MiB, PDF/TXT bytes are reserved and the remaining capacity is shared across selected JPEG/PNG/WebP images. Oversized images are resized to at most 2048 pixels on the longest side, with lower dimension/quality attempts until each fits; nothing is upscaled. Sources are bounded to 10 MiB and to 16000 pixels per side / 40 million pixels, decoding is sampled to bound bitmap memory, EXIF rotation is applied, transparency is flattened on white, and output is a metadata-free JPEG named `-resized.jpg`. Selections that cannot fit leave the previous selection intact.

## Voice

- **Voice input to draft:** Android `SpeechRecognizer` with a user-initiated start, `RECORD_AUDIO` permission (denial links to app Settings), service-availability checks and a 30-second timeout. A completed transcript is reviewed/edited and appended to the existing draft; sending remains a separate action. No raw audio is stored by the app, but the selected device speech service may process audio remotely.
- **Read aloud:** explicit Read aloud / Stop on completed replies using Android `TextToSpeech`. Long replies are split into chunks of at most 300 code points; a stalled chunk times out after 45 seconds. Playback requests transient audio focus and stops on focus loss, backgrounding, navigation or a new prompt.
- **Foreground hands-free:** an explicit Start for a separate conversation. Final speech sends automatically, the reply is read with device TTS, then the next turn listens. Stop/Back/background cancels recognition, the pending request and playback; returning to the foreground never auto-starts. Errors stop the loop. This is device speech recognition plus a text chat reply plus TTS, not simultaneous listening/speaking.
- **Optional generated reply voice:** uses the authenticated, owner-restricted ElevenLabs endpoint (`/api/voice/audio`). Device speech remains the default; only tapping a completed reply sends its text. Audio is bounded, kept in memory and released on Stop/navigation/background/offline/account changes. Hands-free still uses device speech.

## Local data controls

Data controls export local chats and unsent drafts as a version-1 `dua-chat-export` JSON envelope through the system CreateDocument picker; no attachment bytes or audio are exported. Import uses OpenDocument, shows the chat count for explicit confirmation and adds each imported chat as a separate copy with a fresh local ID; existing chats, the active draft and pending attachments are kept. Files are limited to 4 MiB, 100 chats, 500 messages per chat, 8000 UTF-16 code units per draft and 1,048,576 per message; invalid files are rejected entirely. Clear all requires confirmation, cancels pending replies, persists empty history before updating the UI and attempts camera-cache cleanup. Storage failures are reported rather than shown as success.

## Settings, notifications, memory and sharing

- **Appearance:** Dark, Light and System themes; Default, System, Serif and Monospace fonts; and a haptic preference, persisted in `dua_preferences`.
- **Permissions:** native microphone permission links to app Settings; camera/files are delegated to camera/photo/document apps. Status refreshes when returning from Settings.
- **Reply notifications:** explicit opt-in and permission, generic content without chat text, a test action and a conversation tap destination. Only replies that complete while the process is alive can notify; there is no server push.
- **Local memory:** off by default, at most ten notes of 200 UTF-16 code units each, with inspect/edit/delete and confirmed delete-all. Enabled notes join new chat requests as untrusted preference context. Remember-statement proposals require per-note approval.
- **Sharing:** review and share/copy completed messages as plain text. Unsent drafts, stopped/pending replies, file bytes and audio are excluded. No public hosted link is created.

## Accounts and cloud sync

See [SUPABASE.md](SUPABASE.md). The app supports email sign-up/sign-in, profile display name, device logout, recovery-code password reset, optional native Google sign-in (`/api/account/google-token`), optional account deletion, and explicit cloud review/upload/download/clear through the backend's `/api/account/*` and `/api/cloud` endpoints. Tokens are encrypted with Android Keystore and excluded from backup/device transfer.

Foreground automatic cloud sync requires explicit device/account consent and uses foreground/online synchronization, local-change debounce, remote polling and bounded retry. Backgrounding, logout, account switches, pending replies and manual data/account operations stop or pause pending work. Deletions, competing edits, revision conflicts and invalid storage pause for review. Background services and realtime collaborative editing remain future work.

## Providers, web sources and integrations

Gemini (Dua/Gemini), ChatGPT, Claude, Grok and Kimi are routed by the backend. All accept validated UTF-8 TXT attachments; explicit server MIME settings enable model-supported images and, for ChatGPT/Claude, PDFs. The app checks the published formats before sending and keeps unsupported selections and drafts. See [PROVIDERS.md](PROVIDERS.md).

Chat has session-only Web: Off/Search/Read link modes, Off by default. Search runs one Brave query and supplies bounded result snippets; Read link reads one public HTTPS text page with DNS pinning, redirect checks and size/time limits. Replies carry a Sources footer with safe clickable HTTPS links that persists through copy, history, sharing and cloud snapshots.

The read-only GitHub connector (`/api/connectors/github`) lists repositories for the signed-in owner when a token, owner email and feature flag are configured. `/api/setup` reports which integration groups are configured without exposing credentials.

## Validation status

Android JVM unit tests run in CI (`bash ./gradlew :app:testDebugUnitTest`) and cover history, import/export, attachments/photo policy, camera store, accounts, cloud sync, voice sessions, network transport and integration capabilities using mocked services or local TLS servers. The backend has no automated test suite in this repository. No live provider, Supabase, search or audio request is part of routine checks; physical-device behavior (speech, audio focus, camera/pickers, notifications, Keystore, lifecycle) and live deployment checks remain to be verified using [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).
