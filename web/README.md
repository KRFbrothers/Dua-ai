# Dua incomplete features and implementation order

Updated 3 October 2026. Web and Android share the intended product behavior; platform implementations differ. This list preserves planned integrations.

| Order | Feature | Current state | Remaining work |
| --- | --- | --- | --- |
| 1 | Persistent chat history | Implemented: saved chats, search, reopen, draft/model recovery and confirmed deletion on web/Android | Physical Android restart/file-picker verification; import/restore; account/cloud sync is separate |
| 2 | Attachment processing | Implemented inline JPEG/PNG/WebP/PDF/TXT input on web and Android, with validation and retry/Stop selection retention | Live Gemini and physical Android verification; larger files, DOCX and durable/provider file storage |
| 3 | Camera/photos | Web image selection/capture and native full-photo camera output/photo picker feed attachment processing | Automatic photo resizing added; physical-device camera/picker, native compression/EXIF and low-memory verification |
| 4 | AI voice conversation | Speech-to-draft, reply playback and foreground hands-free turns | Physical speech/TTS/permission verification; provider-generated audio, full duplex and background sessions |
| 5 | Account sign-in/profile/logout | Design screens only | Authentication provider, sessions, real profile and logout behavior |
| 6 | Cloud history sync | Not implemented | Authenticated ownership, backend storage, cross-device sync and conflict handling |
| 7 | Other AI providers | ChatGPT, Claude, Grok and Kimi remain unavailable | Server-side provider credentials/configuration, routing, errors, quotas and tests |
| 8 | Web search | Disabled/Coming soon | Search provider, citations and user controls |
| 9 | Memory | Opt-in local notes with inspect/edit/delete controls | Cloud/account ownership and automatic extraction/retrieval |
| 10 | Connectors/plugins | Placeholder UI | Integration selection, authorization, actual tools and revocation |
| 11 | Computer mode/screen sharing/video | Coming soon/design states | Define supported desktop/device capabilities and implement permissions/session lifecycle |
| 12 | Appearance/font settings | Persisted theme/font/haptic settings on web and Android | Physical rendering/vibration verification |
| 13 | Notifications | Explicit permission, generic local reply notification and test action | Physical delivery; server push/durable background work |
| 14 | Data controls/privacy/sharing | Local JSON export, confirmed delete-all and individual deletion now work on web/Android | Import/restore and completed-message text sharing implemented; cloud retention/account deletion deferred |
| 15 | Microphone/camera permissions | Voice requests native microphone permission with denial/settings recovery; camera capture is delegated to the external camera app | Settings requests supported real permissions; physical verification and new capabilities remain |
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

Web uses the browser's capture input where supported, resets the input to allow reselection, preserves prior selection on cancellation/failure, and ignores late reads from older selections. Both platforms retain the 2 MiB total limit; automatic photo resizing is described below. Unmodified camera JPEG metadata is retained; resized copies omit original metadata.

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

Data controls now exports local chats and unsent drafts as JSON on both platforms, using the same `dua-chat-export` version-1 envelope with exportedAt and chats. Android uses the system CreateDocument picker; web requests a named JSON download. Models, message roles/text and timestamps are retained; no attachment bytes or audio are exported. Version-1 import/restore is described below.

Clear all requires a confirmation. Web removes local records and current-tab pending attachments/voice handoff, keeps an empty history marker to block legacy migration, and increments a storage generation. Existing Chat tabs cancel pending replies/playback and return Home; stale saves cannot restore deleted history. Android cancels pending replies, persists an empty history before updating the visible list, removes the unreadable-history backup, clears the current draft/selection and attempts camera-cache cleanup. Failed storage writes report errors rather than displaying a successful deletion.

This manages only the current browser/device. Previously exported files, other devices, provider-side records and OS backups are outside this control; Android backup policy remains unchanged. Pending attachment/voice session storage in other browser tabs is not globally erased. JSON exports are plain text and include unsent drafts.

Validation: 76 web tests and 28 Android JVM tests passed; debug APK assembled. Mobile Chromium checked generated JSON contents including Hindi/emoji, cancel without deletion, confirmed deletion, empty Home and an open second Chat tab returning Home without resurrecting data. Physical Android document-provider writing, disk-failure recovery and restart after deletion remain unverified.

## Local data controls

Settings → Data controls offers Export chats and confirmed Clear all on web and Android. Exports use the shared `dua-chat-export` version-1 JSON envelope and include messages, models, timestamps and unsent drafts without attachment bytes/audio. Web requests a dated JSON download; Android uses the system file picker. Version-1 import/restore is described below.

Clear all deletes local history and the current session's draft/selected files. Open browser Chat tabs stop pending work and return Home; a storage-generation check blocks stale tabs from re-saving deleted history. Other devices, previously exported files, provider records and OS backups are unaffected. Other tabs' temporary file/voice selections are not globally erased. Android removes its unreadable-history backup and attempts camera-cache cleanup. Native file-picker and restart/disk-failure behavior still need physical-device verification.

### Import local chats

Data controls → Import chats accepts a `dua-chat-export` version-1 UTF-8 JSON file. The file is checked before showing its chat count and an explicit confirmation. Import adds separate copies with new local IDs, retaining existing chats and drafts; repeated imports create additional copies. The selected chat and pending attachment selections are preserved. Cancel or invalid data leaves history unchanged. No Gemini request or automatic Send occurs.

Limits are 4 MiB/file, 100 chats/file, 500 messages/chat, draft length 8000 UTF-16 code units, message length 1,048,576 code units and nesting depth 64. Required schema, supported model/role/state, non-negative safe-integer timestamps and unique source IDs are validated. Unknown fields are ignored. Empty exports do nothing. Oversized exports are not truncated; exports above these limits require reduction before import. Imported attachment bytes/audio are unavailable. Stored and displayed message text remains plain text, including markup-like strings.

Web validates bounded UTF-8 bytes, re-reads history when confirming, and adds copies with one atomic write while preserving active ID and generation. Other open tabs retain their chats and future saves preserve imported copies. Android uses OpenDocument and a bounded background stream reader, then commits merged history before changing its list. Failed storage writes retain the review for retry. Imported stopped responses stay stopped, are not read aloud and are excluded from request history on both clients.

Validation: 84 web tests, 32 native JVM tests and APK assembly; mobile browser checks with a Hindi/emoji fixture cover confirmation/cancel, invalid input, preservation of an existing draft and reopening the imported chat. Android physical document-provider I/O, disk failure and restart are still pending.

### Larger photo preparation

Camera/Photos/Files accept JPEG, PNG and WebP sources up to 10 MiB each. If the entire selection already fits 2 MiB, it is passed through unchanged. Otherwise document bytes are reserved and remaining upload capacity is divided across the selected images. Images over their share are locally resized/re-encoded into JPEG, with a longest side no greater than 2048 pixels; dimensions and quality are reduced further if needed. Converted filenames end in `-resized.jpg` and originals on the device are unchanged.

Resized images apply EXIF orientation, flatten transparency on white, use the first animation frame and omit original metadata. These are lossy copies; output differs between browser/native codecs while respecting the same limits. Invalid/oversized dimensions (>16000 on either side or >40 million pixels), source files above 10 MiB, encoding failure and selections that cannot fit produce errors while preserving the previous selection. PDF/TXT are never recompressed: total uploaded file bytes remain <=2 MiB, at most three files, TXT <=64 KiB.

Web processes images sequentially, checks dimension headers before decoding, closes decoded bitmaps and releases canvas buffers. Android bounds source streaming, uses sampled decoding, EXIF transforms and bitmap cleanup on a background dispatcher; camera-cache files are cleaned after processing. No image is uploaded before the explicit Send action. Stop/errors still retain prepared attachments for retry.

Validation: 93 web tests, 38 native JVM tests and APK assembly. A real browser decode/encode check compressed a rotated JPEG and transparent PNG plus TXT to 1,851,571 bytes, verified orientation/white background and sent the prepared bytes through the shared backend with a mocked provider. Android physical codec/EXIF, camera/picker and low-memory testing, plus live Gemini, remain pending.


## Local feature batch

Cloud setup is deferred by user choice. Production Settings persists theme/font/haptics and exposes actual permission controls, local reply notifications, opt-in manually managed memory and completed-message plain-text sharing. Voice offers explicit foreground hands-free Start/Stop with speech → Gemini text → TTS turns and background/error cleanup. Memory is limited to ten notes of 200 characters and is forwarded only when enabled. Chat deletion and preference/memory reset are separate controls. No live auth, push server, other provider routing or cross-device sync is configured. The notification service worker does not cache assets.

See the ninth implementation in [FEATURES.md](../FEATURES.md) for data handling, limits, deferred work and verification (103 web tests, 43 native JVM tests, APK build and mocked mobile browser checks). Physical Android speech/notifications and live providers remain unverified.

## Supabase account integration

Email account/profile/logout and reviewed manual chat/memory cloud snapshots are implemented for web and Android. They require a Supabase project and shared backend environment configuration. See [SUPABASE.md](../SUPABASE.md) for setup, ownership checks, limits and remaining live verification. Local features continue without account setup; signing in does not upload automatically.
