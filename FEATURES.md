# Dua incomplete features and implementation order

Updated 3 October 2026. Web and Android share the intended product behavior; platform implementations differ. This list preserves planned integrations.

| Order | Feature | Current state | Remaining work |
| --- | --- | --- | --- |
| 1 | Persistent chat history | Local implementation in this PR: saved chats, search, reopen, draft/model recovery and confirmed deletion on web/Android | Physical Android restart verification; export/delete-all controls; account/cloud sync is separate |
| 2 | Attachment processing | File selection and metadata UI exist; files are not sent to Gemini | Real uploads, size/type validation, provider media input, upload/retry/cancel states and retention |
| 3 | Camera/photos | Selection UI exists | Actual capture and media-processing integration on both platforms |
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
