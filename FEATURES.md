# Dua incomplete features and implementation order

Updated 3 October 2026. Web and Android share the intended product behavior; platform implementations differ. This list preserves planned integrations.

| Order | Feature | Current state | Remaining work |
| --- | --- | --- | --- |
| 1 | Persistent chat history | Implemented: saved chats, search, reopen, draft/model recovery and confirmed deletion on web/Android | Physical Android restart verification; export/delete-all controls; account/cloud sync is separate |
| 2 | Attachment processing | Implemented inline JPEG/PNG/WebP/PDF/TXT input on web and Android, with validation and retry/Stop selection retention | Live Gemini and physical Android verification; larger files, DOCX and durable/provider file storage |
| 3 | Camera/photos | Web image selection/capture and native full-photo camera output/photo picker feed attachment processing | Physical-device camera/picker verification; image resizing/compression for captures above 2 MiB |
| 4 | AI voice conversation | Web speech input is partial; native Voice is animation/UI only | Native recording/transcription, AI audio output, mute/interruption and permission/error handling |
| 5 | Account sign-in/profile/logout | Design screens only | Authentication provider, sessions, real profile and logout behavior |
| 6 | Cloud history sync | Not implemented | Authenticated ownership, backend storage, cross-device sync and conflict handling |
| 7 | Other AI providers | ChatGPT, Claude, Grok and Kimi remain unavailable | Server-side provider credentials/configuration, routing, errors, quotas and tests |
| 8 | Web search | Disabled/Coming soon | Search provider, citations and user controls |
| 9 | Memory | Disabled/Coming soon | Opt-in storage, retrieval, inspect/edit/delete controls and account ownership |
| 10 | Connectors/plugins | Placeholder UI | Integration selection, authorization, actual tools and revocation |
| 11 | Computer mode/screen sharing/video | Coming soon/design states | Define supported desktop/device capabilities and implement permissions/session lifecycle |
| 12 | Appearance/font settings | Design samples; launcher uses dark styling | Apply supported choices across screens and persist them consistently |
| 13 | Notifications | Design sample | Browser/native permission flows, notification delivery and settings |
| 14 | Data controls/privacy/sharing | Mostly design content; individual local chat deletion now works | Export, delete-all, account deletion, real sharing and retention settings |
| 15 | Microphone/camera permissions | Permission screens are mostly informational | Real native permission requests, denial/settings recovery; browser support checks |
| 16 | Usage/account protection | IP burst limit added; endpoints are not authenticated | Account-based access policy and project-wide daily/spend budgeting |

## First implementation: local history

Production web uses `localStorage` at `dua-history-v2`, migrates the previous in-tab chat once, and preserves separate chats when New chat is selected. Android uses app-private SharedPreferences and serialized chat records. Both retain messages, draft and selected model; Home supports title/preview search, reopening and confirmed deletion. History stays on its device/browser and does not sync between web and Android.

Deleting a chat removes that local record. Clearing browser/app storage also removes local history. No cloud backup/sync service, account ownership or end-to-end encryption feature is implemented. OS-level Android backup policy is unchanged. The separate `/preview/` design sandbox still uses simulated replies.

Validation: 43 web tests, including migration/reload/deletion/storage-error coverage; 12 Android JVM tests including history serialization; Android debug APK assembly; mobile Chromium checks with mocked Gemini for multiple chats, draft restore, search and deletion. Physical Android process death/restart and live Gemini were not tested.

Implement the remaining items one at a time with matching web/native behavior. Provider/account integrations require configured server-side services; do not place private provider credentials in the browser or APK.

## Second implementation: attachments

Both clients send at most 3 files / 2 MiB total with the current message; TXT is limited to 64 KiB. The shared backend validates contents and adds inline image/PDF or decoded text parts only to the final user message. Successful replies clear selection; Stop/errors retain it. Persistent history stores filenames and replies without file bytes. Pending bytes are browser-session/Android-memory only.

Validation: 50 web tests and 16 Android JVM tests passed; Android debug APK assembled. Mobile Chromium file selection, sending and selection clearing passed with a mocked Gemini response. Live Gemini and physical Android file picker/camera tests remain pending.

## Third implementation: camera capture

Android delegates capture to the device camera app through TakePicture and a restricted FileProvider cache path. Original JPEG bytes are read through the bounded attachment reader without thumbnail conversion or resizing. A cancelled/failed capture keeps the previous selected attachment. Output files are removed after reading, on failure/cancel, or when launch fails; files abandoned by process death are pruned after 24 hours when capture next starts. The pending filename survives normal activity recreation. App cache may also be cleared by Android. No direct Camera API or CAMERA permission is needed for delegated capture.

Web uses the browser's capture input where supported, resets the input to allow reselection, preserves prior selection on cancellation/failure, and ignores late reads from older selections. Both platforms retain the 2 MiB total limit; larger photos require resizing outside the app for now. Camera JPEG metadata is retained along with the original bytes.

Validation: 54 web tests and 20 Android JVM tests passed; debug APK assembled. Mobile Chromium checked camera-input selection, input reset and return to Chat without layout overflow or browser errors. A physical Android camera app, rotation/process restoration with external camera open, and live Gemini media replies remain untested.
