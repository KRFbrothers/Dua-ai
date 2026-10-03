# Dua incomplete features and implementation order

Updated 3 October 2026. Web and Android share the intended product behavior; platform implementations differ. This list preserves planned integrations.

| Order | Feature | Current state | Remaining work |
| --- | --- | --- | --- |
| 1 | Persistent chat history | Implemented: saved chats, search, reopen, draft/model recovery and confirmed deletion on web/Android | Physical Android restart/file-picker/import verification; account/cloud sync is separate |
| 2 | Attachment processing | Implemented inline JPEG/PNG/WebP/PDF/TXT input on web and Android, with validation and retry/Stop selection retention | Live Gemini and physical Android verification; larger documents, DOCX and durable/provider file storage |
| 3 | Camera/photos | Web image selection/capture and native full-photo camera output/photo picker feed attachment processing | Automatic photo resizing added; physical-device camera/picker, native compression/EXIF and low-memory verification |
| 4 | AI voice conversation | Speech-to-draft input and user-triggered reply read aloud on web/Android; native mic permission and playback lifecycle cleanup | Physical speech/TTS/permission verification; hands-free conversational turns, provider-generated audio and interruption |
| 5 | Account sign-in/profile/logout | Design screens only | Authentication provider, sessions, real profile and logout behavior |
| 6 | Cloud history sync | Not implemented | Authenticated ownership, backend storage, cross-device sync and conflict handling |
| 7 | Other AI providers | ChatGPT, Claude, Grok and Kimi remain unavailable | Server-side provider credentials/configuration, routing, errors, quotas and tests |
| 8 | Web search | Disabled/Coming soon | Search provider, citations and user controls |
| 9 | Memory | Disabled/Coming soon | Opt-in storage, retrieval, inspect/edit/delete controls and account ownership |
| 10 | Connectors/plugins | Placeholder UI | Integration selection, authorization, actual tools and revocation |
| 11 | Computer mode/screen sharing/video | Coming soon/design states | Define supported desktop/device capabilities and implement permissions/session lifecycle |
| 12 | Appearance/font settings | Design samples; launcher uses dark styling | Apply supported choices across screens and persist them consistently |
| 13 | Notifications | Design sample | Browser/native permission flows, notification delivery and settings |
| 14 | Data controls/privacy/sharing | Local JSON export/import, confirmed delete-all and individual deletion now work on web/Android | Account deletion, real sharing and cloud retention settings |
| 15 | Microphone/camera permissions | Voice requests native microphone permission with denial/settings recovery; camera capture is delegated to the external camera app | Physical permission/recognizer verification; remaining Settings permission samples and other device capabilities |
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

Android delegates capture to the device camera app through TakePicture and a restricted FileProvider cache path. Full JPEG output is prepared through the bounded photo reader; oversized captures are resized as described in the eighth implementation. A cancelled/failed capture keeps the previous selected attachment. Output files are removed after reading, on failure/cancel, or when launch fails; files abandoned by process death are pruned after 24 hours when capture next starts. The pending filename survives normal activity recreation. App cache may also be cleared by Android. No direct Camera API or CAMERA permission is needed for delegated capture.

Web uses the browser's capture input where supported, resets the input to allow reselection, preserves prior selection on cancellation/failure, and ignores late reads from older selections. Both platforms retain the 2 MiB total limit; automatic photo resizing is described in the eighth implementation below. Unmodified camera JPEG metadata is retained; resized copies are re-encoded without original metadata.

Validation: 54 web tests and 20 Android JVM tests passed; debug APK assembled. Mobile Chromium checked camera-input selection, input reset and return to Chat without layout overflow or browser errors. A physical Android camera app, rotation/process restoration with external camera open, and live Gemini media replies remain untested.

## Fourth implementation: voice input to draft

Production Voice now supports speech-to-text through the browser SpeechRecognition API and Android SpeechRecognizer. Tap mic to start; review/edit a completed transcript and choose Use in chat to append it to the existing draft. Sending remains a separate action; recognition does not call Gemini. Blank/over-8000-character transcripts are rejected, and drafts are never silently overwritten or truncated. Web restores the originating chat and consumes its temporary handoff once. Android retains completed review text through normal activity recreation.

Start is user initiated. Android requests RECORD_AUDIO, handles denial with an app Settings action, checks speech-service availability, and releases its recognizer on Stop/background/disposal. Web checks support, handles permission/service errors, aborts on Stop/background/page exit, prevents overlapping starts and ignores late callbacks. Both use a 30-second timeout. Recognition uses the device/browser language. No raw audio is stored by this application, but the selected device/browser speech service may process audio remotely. Voice input and final transcript review do not implement AI audio replies, video or screen sharing.

Validation: 61 web tests and 22 Android JVM tests, plus debug APK assembly. Mobile Chromium with mocked speech results checked Hindi/English editing and transfer to an existing draft with no automatic send. Real microphone/service behavior, Android permission denial/recovery and rotation with the microphone active remain unverified on a physical device.

## Fifth implementation: reply read aloud

Completed AI replies now have explicit Read aloud / Stop controls on both clients. Browser SpeechSynthesis and Android TextToSpeech use the device/browser language and installed voices. Nothing speaks automatically. Long replies are split into <=300-code-point chunks with whitespace/sentence boundaries where practical, preserving Hindi and emoji. Replacing/stopping playback invalidates old callbacks; a stalled chunk times out after 45 seconds.

Playback stops on backgrounding/navigation and when a new prompt is submitted. Android also requests transient audio focus, stops on focus loss and releases focus at Stop/completion. Unsupported engines, missing native language/data and playback errors show guidance. Pending/stopped web replies have no read-aloud action. Button labels are excluded from stored message text and subsequent API history.

This is device/browser text-to-speech for existing text replies, not a provider audio API or hands-free live conversation. The app stores no audio output; installed/network speech voices may process reply text through their service.

Validation: 67 web tests and 26 Android JVM tests; debug APK assembly. Mobile Chromium with mocked speech output checked explicit playback, Stop, clean saved history and 48px controls at 320px width. Actual speaker output, installed voices/languages and Android audio-focus/background behavior still need physical-device verification.

## Sixth implementation: local data controls

Data controls now exports local chats and unsent drafts as JSON on both platforms, using the same `dua-chat-export` version-1 envelope with exportedAt and chats. Android uses the system CreateDocument picker; web requests a named JSON download. Models, message roles/text and timestamps are retained; no attachment bytes or audio are exported. Version-1 import/restore was added in the seventh implementation below.

Clear all requires a confirmation. Web removes local records and current-tab pending attachments/voice handoff, keeps an empty history marker to block legacy migration, and increments a storage generation. Existing Chat tabs cancel pending replies/playback and return Home; stale saves cannot restore deleted history. Android cancels pending replies, persists an empty history before updating the visible list, removes the unreadable-history backup, clears the current draft/selection and attempts camera-cache cleanup. Failed storage writes report errors rather than displaying a successful deletion.

This manages only the current browser/device. Previously exported files, other devices, provider-side records and OS backups are outside this control; Android backup policy remains unchanged. Pending attachment/voice session storage in other browser tabs is not globally erased. JSON exports are plain text and include unsent drafts.

Validation: 76 web tests and 28 Android JVM tests passed; debug APK assembled. Mobile Chromium checked generated JSON contents including Hindi/emoji, cancel without deletion, confirmed deletion, empty Home and an open second Chat tab returning Home without resurrecting data. Physical Android document-provider writing, disk-failure recovery and restart after deletion remain unverified.

## Seventh implementation: local JSON import/restore

Data controls accepts the same version-1 Dua export on web and Android. Choose a JSON file, review its chat count and explicitly confirm Import. Existing chats, the active draft and pending attachment selections are retained. Each imported chat receives a fresh local ID and is added as a separate copy, preserving model, timestamps, message roles/text, stopped states and unsent drafts. Importing the same file again creates more copies; there is no silent overwrite or automatic deduplication. Imported stopped messages remain excluded from provider history and read-aloud playback.

Files are limited to 4 MiB, 100 chats per file, 500 messages per chat, 8000 UTF-16 code units per draft and 1,048,576 per message. Schema/version/model/role/state/timestamp and duplicate source-ID checks reject the entire file without importing a subset. UTF-8 input is validated, nested structures are bounded, empty exports change nothing, and unknown fields are ignored rather than executed or stored. Exports exceeding import limits must be reduced outside the app; content is never silently truncated.

Web re-reads local history at confirmation to preserve other tabs’ intervening saves and uses one atomic localStorage write. Android reads through the system OpenDocument picker on a background dispatcher, bounds bytes while streaming, retains review through normal activity recreation, cancels an in-flight reply when confirmed, and commits merged history before publishing it to the UI. Storage failures keep the checked import available for retry. File reads and confirmation do not call Gemini or send messages. Exported attachments remain filenames/text only; file bytes and audio cannot be restored. Local import is separate from account/cloud sync.

Validation: 84 web tests and 32 Android JVM tests, plus debug APK assembly. Mobile Chromium verified review/cancel without changes, confirmed import with the existing Hindi draft retained, invalid-file rejection, imported Hindi/emoji messages and draft/model reopening without overflow or browser errors. Physical Android document-provider import, storage-failure recovery and restart remain unverified.

## Eighth implementation: fit larger photos into the upload limit

Camera, Photos and Files now prepare JPEG/PNG/WebP images locally on web and Android. When the complete selection fits 2 MiB, original bytes and filenames are retained without decoding/recompression. When it exceeds the limit, PDF/TXT bytes are reserved and the remaining capacity is shared across selected images. Images exceeding their budget are resized to at most 2048 pixels on the longest side, with lower dimension/quality attempts until each fits; no image is upscaled. The backend limit remains 3 files / 2 MiB total and TXT remains 64 KiB.

Image sources are bounded to 10 MiB each. Resizing rejects invalid or excessive dimensions (above 16000 on either side or 40 million pixels) before full decoding. Android additionally samples the decode to bound bitmap memory and releases temporary bitmaps; web closes ImageBitmaps and releases canvas buffers. EXIF rotation/mirroring is applied to resized copies. Resized PNG/WebP transparency is flattened on white and animated media uses its first frame. Output becomes lossy JPEG named `-resized.jpg`, with original metadata omitted. Original device files are unchanged. Exact output bytes/resolution depend on the platform codec; both clients follow the same size/quality bounds.

A source exceeding the safety limits, decode/encode failure or a result that cannot fit leaves the previous attachment selection intact. Native camera output cleanup runs after processing success/failure. Selecting newer files still invalidates earlier reads. No automatic message/API request occurs until Send, and Stop/retry behavior is preserved. This does not add HEIC/GIF, larger documents or durable file storage.

Validation: 93 web tests and 38 Android JVM tests plus debug APK assembly. Mobile Chromium selected a 4.5 MB EXIF-rotated JPEG, a 6.5 MB transparent PNG and UTF-8 TXT together. The prepared selection totaled 1,851,571 bytes; camera orientation and a white transparent region were checked from decoded JPEG output. The shared backend accepted the actual converted bytes and forwarded six content parts to a mocked provider; successful sending cleared selection with no overflow/browser errors. Native policy/byte pass-through and camera processing cleanup are covered by JVM tests. Real Android bitmap compression/EXIF, camera/picker behavior, low-memory handling and live Gemini remain unverified on a physical device.
