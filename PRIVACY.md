# Privacy Policy

This document outlines how Dua AI handles user data, privacy, and retention across its native Android app, backend API, and cloud services.

Dua is a personal, single-user app: the backend is meant to be restricted to the owner's account (see [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md)) and the app is not distributed to other people. "Users" in this document means the owner. It records how the owner's data flows to device services, Supabase and providers.

## Data Collection & Processing

### Completed chat replies

With the updated APK and configured owner backend, sending a signed-in chat writes its completed prompt/reply pair to the owner's Supabase snapshot before completion is acknowledged, even when optional foreground sync is off. Stored metadata includes stable message IDs, summaries, source footers, file references/transcripts, code results and Build metadata where used. Raw inline file bytes are not added to the snapshot. An incomplete/cancelled generation is not saved by this completion path; cached partial turns can still be included in a later reviewed/automatic sync. A save already in flight may commit before its acknowledgement is lost. Imagine and live realtime voice retain their existing storage flows. Cloud data remains protected by owner access/RLS and is not end-to-end encrypted by Dua.

### 1. Audio & Voice Data

#### Collection
- **Source:** Users initiate voice input via microphone (Android `RecognitionService`).
- **Scope:** Microphone audio is passed to the operating system's speech-recognition service to produce a transcript. Depending on the device, service and settings, recognition may be processed locally or remotely; Dua cannot guarantee on-device processing. Voice-to-draft does not send audio to Dua's chat API. The transcript is sent to the selected AI provider only if the user sends it as a chat message.
- **Generated audio:** If an AI voice is chosen in Settings › Voice and a reply (or a voice sample) is explicitly played, the text of that reply, cleaned of links and code, is sent through Dua's backend to the chosen provider (xAI, Google Gemini or ElevenLabs) to generate speech. Generated audio is kept only in memory for replays during the app session and is never written to disk or chat history. Device text-to-speech uses the installed speech service and may have its own processing practices.
- **Live voice (optional, signed-in owner only):** While a live conversation is running, microphone audio is streamed over an encrypted WebSocket directly from the phone to xAI (Grok Voice Agent) and xAI's spoken reply is streamed back. Audio is sent only between pressing Start and the session ending (End, Close, the notification's End action, the time limit or a lost connection); muting stops sending. The session is configured by Dua's backend with Dua's instructions, your Memory notes (only when Memory is on) and the last few turns of the open chat. Dua does not record or store the audio; the text captions of the conversation are added to that chat when the session ends and are then handled like any other chat message. A foreground notification is shown for the whole session. xAI processes the audio under its own terms.
- **Recognition language:** The speech-recognition language chosen in Settings › Voice is passed to Android's recognizer; it does not change where audio is processed.
- **Consent:** Users grant microphone permission at the Android OS level; the app displays a permission notice before first use.

#### Processing
- **Speech recognition:** Recognition is provided by Android's selected `RecognitionService`. Processing location and retention depend on that service; review its settings and privacy terms.
- **Provider transmission:** Chat text, including a reviewed voice transcript, is sent over HTTPS to the selected chat provider when the user sends it. Optional generated reply audio sends the selected reply text over HTTPS to the chosen voice provider (xAI `/v1/tts`, Google Gemini Interactions with `store: false`, or ElevenLabs).
- **Provider Retention:** Retention depends on the provider, product/API, account settings and applicable terms, which can change. Review the selected provider's current terms before sending data.

#### User Control
- Users can disable voice input in Settings.
- Voice recognition does not automatically send a chat or store raw audio in Dua's chat history. Speech services may process or retain audio under their own terms.
- Generated reply audio is optional and user-initiated. Dua does not persist the generated audio as chat history.
- Live voice is optional and starts only when you press Start; End, mute and the notification's End action stop audio leaving the phone. Turn it off on the server with `DUA_VOICE_REALTIME=0`.

---

### 2. Chat History & Conversations

#### Collection
- **Scope:** The server is the source of truth for chat messages, memory notes and project instructions; the phone keeps only a disposable cache for offline viewing and speed.
- **Server-backed storage:** For the owner’s signed-in account, the server snapshot is the source of truth for chat messages, memory notes and project instructions. The phone keeps a disposable cache for offline viewing and startup; sync refreshes that cache from the server. Without an account, chat is temporary cache-only mode and cannot become authoritative.

#### On-device cache
- **Android:** A cache of chat history and app preferences is stored in app-private `SharedPreferences`; they are not encrypted by the app. Android OS sandboxing protects app-private files, but this is not equivalent to an encrypted database.
- **History search:** Queries scan the current project's loaded messages, drafts and saved Run code/code output on the phone. They are not sent to providers or the backend, and no search index is persisted. Query/snippets are transient UI state, excluded from server snapshots and exports; Android UI saved-state restoration can retain the query. Project switching clears it. Search does not retrieve raw stored-file contents or hidden attachment/reasoning/memory fields. Sync refreshes the disposable cache from the authoritative server snapshot. See [HISTORY-SEARCH.md](HISTORY-SEARCH.md).
- **Backup:** Android app backup and device transfer are enabled. Chat history, account-session and automatic-sync preference files are excluded by the checked-in backup rules. Other app preferences, including cached memory notes and project instructions, are not excluded. Whether a backup is created or restored depends on Android/device backup settings and the user's account. Users should not assume the phone cache is the authoritative copy.
- **Retention:** Retained until the user deletes the data, clears app storage, uninstalls the app, or an enabled device backup restores it. Backup copies may have separate retention.

#### Cloud Storage (Opt-In)
- **Activation:** The owner confirms manual cloud writes/deletions or enables automatic foreground Cloud Sync. Sign-in alone does not upload cached chats.
- **Transport:** Messages are sent over HTTPS to Supabase (encrypted at rest).
- **Retention:** 
  - Users can clear their cloud snapshot from the app or delete their account.
  - **Delete chat** removes that conversation from the current server snapshot and this phone after server confirmation. Other updated devices apply the deletion when syncing. Only chat IDs are retained as deletion metadata (up to 10,000 IDs, within the 4 MiB snapshot limit), including after clearing a snapshot, so stale clients cannot upload the same deleted ID. These IDs contain no message text or titles. Stored files and existing exports are kept; importing a backup creates new chat IDs and can intentionally restore a copy. Supabase backup retention is separate and has not been verified.
  - Account deletion requests removal of the Supabase account and associated app data; provider backup-retention timing is controlled by Supabase and is not guaranteed by Dua.
  - The repository does not define or verify a fixed post-deletion backup-retention period.
- **Access control:** Supabase Row Level Security (RLS) restricts snapshot access to the signed-in account. The backend also checks the configured owner email. RLS is an access rule, not encryption.

---

### 3. User Account Data

#### Data Stored
- Email (from the Google account)
- Account name (from the Google account)
- Local notification, appearance and provider selections are stored in app settings, not Supabase cloud snapshots.
- Provider API keys are configured server-side and are not stored in the app's local account profile.

#### Signed-out cache mode
- Saved cached chats remain viewable while signed out. The configured owner-only backend requires owner sign-in for online replies.

#### Cloud Account (Opt-In)
- Required for cloud snapshots and multi-device sync.
- Authenticated via Supabase with native Google sign-in or owner-only email/password authentication. Email signup requires confirmation; Google accounts can set a password while signed in.
- The welcome quick-continue button uses a real last account profile held in memory (name, email, avatar and login provider only). It contains no tokens or contact number and is forgotten when the process restarts after logout. Account deletion clears it.
- Account sessions and their last successful login method are cached in encrypted device storage; cloud chat snapshots are protected by RLS and the owner lock.
- The optional contact phone number is saved in Supabase Auth user metadata (`dua_phone_number`) and cached with the encrypted account session. It is included in session responses and may appear in Supabase access-token metadata. It is not sent as chat context or included in chat backups. It does not verify phone ownership or enable phone sign-in. Account → Phone number can edit it or remove it by saving an empty value; logout clears the local session, and the server contact remains until removed or the account is deleted.

#### Retention
- Account data is retained as long as the account is active.
- Account deletion removes the Supabase account and associated server data when the configured deletion flow succeeds. A phone cache may remain until cleared; clear it separately if needed. Supabase backup retention is outside Dua's control.

---

### 4. Provider Integrations

#### What Dua Shares with Providers
- **Text Messages:** User prompts and chat history (if using a configured provider).
- **Attachments:** Images, PDFs, text/code files (provider-dependent; not all providers support all formats). For the signed-in owner, bounded text from attached text/code files and stored-file metadata are retained with the server-backed chat so follow-ups work across devices. User-initiated JSON backups include that extracted text, stored-file references and audio transcripts. Raw attachment/audio bytes stay in private Storage and are not placed in JSON snapshots or backups. Signed-out chats remain cache-only.
- **Stored files (signed-in owner only):** Larger files, spreadsheets and audio are uploaded from the phone straight to a private Supabase Storage bucket (`dua-files`) in a folder named after your account ID; file names are not part of the stored path. For each reply that uses them, the backend either downloads the file and sends it to the provider (Gemini; Kimi images), gives the provider a link that expires after one hour (OpenAI, xAI/Grok, Claude), or extracts text itself (spreadsheets as CSV, text files, PDFs for providers without PDF input). Gemini may keep a copy for 48 hours when large files go through its Files API. xAI handles attached PDFs with its document search tool.
- **Audio:** Audio files and recorded voice notes are transcribed (by default xAI speech-to-text, then Gemini, then OpenAI, depending on which keys are configured). The transcript is sent to the chat provider and retained as bounded chat metadata in the signed-in server snapshot and phone cache, so follow-ups don't transcribe the audio again.
- **Imagine (signed-in owner only):** Your description and any photo you attach for editing or animating are sent to xAI (Grok Imagine) or Google (Gemini, with `store: false`). Photos are read from your private bucket by the backend and sent as inline data, not as public links. Generated images come back as data; finished videos are downloaded by the backend from xAI's temporary link. Both are saved to your folder in the private `dua-files` bucket, and the app downloads them into its cache to show, save or share them. Saving copies a file into your phone's gallery (Pictures/Dua or Movies/Dua); sharing hands it to the app you pick. Daily usage counts (not prompts) are stored in Supabase to enforce caps.
- **Run code:** When you turn on Run code, the provider writes and runs Python in its own hosted sandbox (Google for Dua/Gemini; OpenAI or xAI only if the owner enabled them). The code works on what is already in the conversation, including spreadsheets and text files you attached. Nothing runs on your phone or on Dua's server. The code and its output are shown in the reply and saved in the server snapshot and phone cache; exports remain user-initiated.
- **Reasoning:** In Reasoning mode the model's thinking summary is saved with the reply in the server snapshot, phone cache and exports.
- **Metadata:** Timestamp, model name, message count (to API endpoints).

#### What Dua Does NOT Share
- Email address
- Account data
- Other users' conversations
- Raw audio or voice logs (except live voice, which streams microphone audio to xAI only while a session runs; see §1)
- Device identifiers

#### Provider Terms
Users are responsible for reviewing the privacy and retention policies of selected providers:
- OpenAI: https://openai.com/policies/privacy-policy
- Google: https://policies.google.com/privacy
- xAI: https://x.ai/legal/privacy-policy
- Anthropic: https://www.anthropic.com/privacy-policy
- Kimi/Moonshot: https://www.moonshot.cn/privacy

API keys are stored server-side (Vercel Functions or Supabase) and are never exposed to the frontend.

---

### 5. Search & Browsing (Chat Web Feature)

#### Search Mode
- **Activation:** User enables "Search Mode" in Chat Web settings.
- **Requirement:** User configures Brave Search API key.
- **Data Sent:** User query is sent to Brave Search API; results are returned and displayed in chat.
- **Retention:** Brave Search retains query data per their privacy policy.

#### Read Link Mode
- **Activation:** User enables "Read Link Mode" and clicks a web link in chat.
- **Data Sent:** Full page content (HTML, text) is fetched via the configured provider's API (e.g., Claude's web reading tool).
- **Retention:** Provider retention policy applies.

#### Native search and X search
- **Search with Gemini, ChatGPT or Grok:** the provider's own search tool runs the search (Google Search, OpenAI web search, xAI web search). The provider sees the conversation it is answering and decides the queries; its retention policy applies. Claude and Kimi use Brave.
- **X search (Grok only):** off for every message unless turned on. Grok searches public X posts through xAI.
- **Source icons:** to show a site icon on a source chip, the app requests `https://<site>/favicon.ico` directly from that site (no cookies). The site learns your IP address and that its icon was requested.

#### Long conversations
- When a chat outgrows the selected model's budget, the server asks the same provider to summarise the older turns. The summary is stored with the chat in the server snapshot and phone cache, included in user-initiated JSON backups, and sent with later messages in that chat. Removing a cached chat removes its cached summary; the server version remains until the server snapshot is changed or cleared.

#### User Control
- Both modes are optional and off by default.
- Users can disable search/link reading at any time.

---

### 6. GitHub Integration (Read-Only)

#### Scope
- Uses a server-side GitHub token to list up to 30 repositories owned by the signed-in GitHub account. The endpoint requests `affiliation=owner`; it is not restricted in code to the Dua-ai repository.
- Only the configured owner account (`DUA_ALLOWED_EMAIL`, signed in) can use it.
- Phase 5B adds read actions: the file tree, file contents (up to 100 KB per file) and code search inside one chosen repository.
- Reading uses only GET requests to an allowlisted set of GitHub paths.
- Pull requests (Phase 5C) use a **separate** token (`GITHUB_BUILD_WRITE_TOKEN`), a fine-grained token limited to selected repositories with Contents and Pull requests read/write. The server writes to GitHub only when you tap Create PR and confirm in the app: it creates one new `dua/…` branch, one commit and one pull request in the repository you chose. It cannot merge, force-push, delete branches or change GitHub Actions workflows. The token and the repository allowlist are never sent to the app or to Gemini.

#### Build mode
- When you use Build mode, the server reads the files Gemini asks for from the repository you picked and sends their contents, the file list and your messages to Google's Gemini API to produce the answer. Up to 40 files and 600 KB per reply by default.
- Private repositories are included if your token can read them, so their code is sent to Gemini when Build mode reads it. Google's Gemini API terms apply (on the free AI Studio tier, Google may use prompts to improve its products).
- The app keeps the repository name and the list of files read with the chat in the server snapshot and phone cache. A change proposal and its eventual pull-request link are kept in the same snapshot and cache. Pending proposal contents are retained up to 512 Ki characters in total; contents are omitted after a PR exists or when that limit is exceeded. Saved diffs are capped at 20,000 characters per file. Repository file bytes read for a reply are not separately archived, but bounded proposal text is stored in the owner's server snapshot. Export remains user-initiated.

#### Data Shared
- Repository names, visibility and descriptions returned by GitHub.
- In Build mode, file contents, paths and search results from the chosen repository are sent to Gemini. The model's bounded proposed file contents are also retained in the owner's Supabase snapshot when synced. They are sent to GitHub only when you confirm Create PR, as the new commit.
- The configured GitHub tokens (read token and, when set, the separate write token) are sent only by the server to GitHub; their permissions are determined by the token owner and GitHub configuration. Neither is ever sent to the app or to Gemini.

#### Data NOT Shared
- GitHub account settings.
- Repository content is read only in Build mode and through the read actions above, and written only as the branch, commit and pull request you approved. Repositories visible to the configured read token may appear in the list, including private repositories owned by the account.

#### Retention
- GitHub token is stored securely server-side.
- Tokens can be revoked by the user at any time via GitHub Settings → Applications.

---

## Data Retention Summary

| Data Type | Local | Cloud | Retention |
|-----------|-------|-------|-----------|
| Chat Messages | Android app-private `SharedPreferences` | Opt-in snapshot | Until cache deletion/storage removal; chat cache is excluded from Android backup and device transfer; cloud deletion/backup timing is controlled by Supabase |
| Audio/transcripts | Speech service; transcript may be retained locally as chat text if sent | No raw audio in snapshots | Speech-service terms apply; transcript follows chat retention; generated audio is not retained by Dua |
| Account Credentials | Encrypted Android session token (Google or email sign-in); email passwords are used transiently over HTTPS and are not saved by Dua | Supabase | Until logout, expiry or account deletion; provider backup retention may apply |
| API Keys | Server-side only | Server-side only | Until removed or rotated by the deployment operator; never stored on device |
| Search Queries | None | N/A (Brave) | Per Brave's current privacy policy and service terms |
| Cloud Snapshots | N/A | Supabase | User-managed; deletion and backup retention depend on Supabase project behavior |
| Generated images and videos (Imagine) | References and prompts in local chat history; cached copies in the app cache (Android may clear it) | Supabase Storage, private `dua-files` bucket; references (not bytes) in exports and cloud snapshots | Cached chat deletion keeps stored files. Explicit **Delete all uploaded files** removes them; account deletion attempts cleanup. Unreferenced abandoned results may be cleaned up. Provider-side retention follows xAI and Google terms; videos too large to store stay only at xAI's temporary link |
| Stored files (uploads) | File references and audio transcripts in the phone cache; raw bytes are not exported | Supabase Storage, private `dua-files` bucket; bounded references/transcripts in the server snapshot | Cached chat deletion keeps stored files. Explicit **Delete all uploaded files** removes them; account deletion attempts cleanup. Discarded unsent uploads are cleaned up, with retries if offline or signed out. Provider copies follow provider terms (for example, the Gemini Files API keeps files for 48 h) |

---

## User Rights & Controls

### Access
- The owner can export the cached chat view as JSON within the documented backup limits. Backups include drafts, extracted attachment text, stored-file references/transcripts, code outputs, Build proposals/PR links, summaries and Imagine metadata. They exclude global Personal memory notes, account session tokens and raw file/audio bytes. Project-specific notes and their switches are included with project definitions; treat JSON exports as private files. Import creates separate cache copies; files still require authenticated owner access to the original Storage objects.
- Users can view their account settings and cloud snapshots in the app.

### Correction
- Users can edit or delete individual messages locally.
- Users can delete entire cloud snapshots.

### Deletion
- **Local Data:** Users can use in-app data controls or clear app storage. Android backup may retain or restore app data according to device settings.
- **Uploaded files:** Removing individual cached chats, clearing cached history or clearing Android app storage keeps files needed by server chats. Profile & Settings → History and data → Data controls shows storage used and has a separate reviewed **Delete all uploaded files** action, which permanently removes files even when server chats reference them. Discarding unsent attachments cleans up temporary uploads. Per-file cleanup retains paths referenced by the current server snapshot and fails without deleting files if that snapshot cannot be read; old queued chat-deletion requests receive the same protection. Removing a chat from the server snapshot does not itself delete its stored files.
- **Cloud Data:** Users can delete snapshots manually or request full account deletion.
- **Account Deletion:** Available from Profile when configured. It removes the account/cloud data on successful completion, and first tries to delete your uploaded files; it does not remove the phone cache; clear the cache separately if you want the device copy removed. Supabase backup-retention timing is outside Dua's control.

### Portability
- Users can export chat history and settings in standard JSON format.

---

## Third-Party Services

Dua AI integrates with:
- **Supabase** (auth, cloud storage): https://supabase.com/privacy
- **Vercel** (backend API hosting): https://vercel.com/legal/privacy-policy
- **Firebase Crashlytics** (error reporting, Android only): https://firebase.google.com/support/privacy
- Crashlytics collection is disabled by default for debug builds and enabled for Android release builds by the checked-in Gradle configuration. Review the Firebase privacy terms and the deployed app's crash-reporting behavior.
- **AI Providers** (OpenAI, Google, Anthropic, etc.): See provider privacy policies above.

---

## Security Measures

- HTTPS encryption for network requests.
- Supabase Row Level Security (RLS) for cloud data isolation.
- Server-side credential storage (no API keys on client devices).
- Android account tokens are encrypted with Android Keystore; local chat history is not app-encrypted.
- Repository/org-level secret and dependency scanning settings must be verified by the repository administrator; they are not established by application code.

---

## Policy Updates

This policy is effective as of **2026-10-11**. Updates will be announced via GitHub Releases and the repository's discussions.

**Last Updated:** 2026-10-11

## Projects and custom instructions

Project names, custom instructions, archive flags, task IDs/titles/completion/deadlines, project memory notes/switches and chat project IDs are included in the owner’s server snapshot, cached on the phone, and included in user-initiated JSON chat exports. Other settings (appearance, voice choices and the Personal memory on/off switch) remain device preferences. Project instructions are sent to the selected chat provider for text replies, including hands-free Voice mode and Build chat. Global Personal memory is omitted from Coding, Business and custom project requests. Each non-Personal project uses only its own enabled notes read from the server; the backend ignores client-supplied memory text. Project-aware Personal chat also reads note text from the server while keeping its device-controlled switch. Other project names/notes/tasks are not added to the prompt. Notes are untrusted owner context and cannot override system instructions. See [MEMORY.md](MEMORY.md). Project scopes are organizational separation within one owner account, not separate accounts or storage encryption. No other project’s chats are automatically added to a reply; stored-file references known to belong to another project are rejected in project-aware chat requests. Old APK requests use their legacy Personal protocol, so use the updated APK for project separation. Live voice is limited to Personal without custom instructions until project-aware realtime sessions are implemented.

Task titles are not automatically sent to AI providers and chat cannot change stored tasks. They are included in project preferences that may be backed up/transferred by Android and in owner-initiated JSON exports. Sync baselines store project digests rather than task plaintext. Device reminder notifications are optional and show a generic due-task count, with no task titles. This phone’s opt-in and delivery digests are excluded from Android backup/device transfer; due dates remain in server/project backups. No server scheduler or new push service is added. See [REMINDERS.md](REMINDERS.md). See [TASKS.md](TASKS.md). The daily planner groups this same cached server snapshot by date and project on the phone, without a separate task store, new external service or AI request. See [PLANNER.md](PLANNER.md).

### Email authentication

Email/password requests go over HTTPS to the Dua backend and then Supabase Auth. Dua sends only the email/password needed for login/signup, or a password and expected account ID for an authenticated password change. The backend requires the configured owner email and never forwards a different signup/login email to Supabase. It checks that Email auth is enabled and email auto-confirmation is disabled before any email credential operation. Supabase validates passwords and manages its credential storage; Dua does not log request credentials or include them in chat history, memory, preferences, JSON exports or Android saved UI state.

The form clears passwords on submission, mode change, leaving the screen and backgrounding. Login stores only the existing encrypted session-token format. A confirmation-pending signup stores no session and performs no chat upload. Leaving the email screen invalidates pending authentication responses; a request already received by Supabase may still have created an account, sent confirmation mail or changed its password. Try signing in to check before repeating a password update after an uncertain network result. No password reset/recovery, MFA challenge or confirmation-link token import is implemented.
