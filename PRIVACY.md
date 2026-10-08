# Privacy Policy

This document outlines how Dua AI handles user data, privacy, and retention across its native Android app, backend API, and cloud services.

Dua is a personal, single-user app: the backend is meant to be restricted to the owner's account (see [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md)) and the app is not distributed to other people. "Users" in this document means the owner. It records how the owner's data flows to device services, Supabase and providers.

## Data Collection & Processing

### 1. Audio & Voice Data

#### Collection
- **Source:** Users initiate voice input via microphone (Android `RecognitionService`).
- **Scope:** Microphone audio is passed to the operating system's speech-recognition service to produce a transcript. Depending on the device, service and settings, recognition may be processed locally or remotely; Dua cannot guarantee on-device processing. Voice-to-draft does not send audio to Dua's chat API. The transcript is sent to the selected AI provider only if the user sends it as a chat message.
- **Generated audio:** If enabled and explicitly selected, reply text is sent to the configured audio provider (ElevenLabs) to generate speech. Device text-to-speech uses the installed speech service and may have its own processing practices.
- **Consent:** Users grant microphone permission at the Android OS level; the app displays a permission notice before first use.

#### Processing
- **Speech recognition:** Recognition is provided by Android's selected `RecognitionService`. Processing location and retention depend on that service; review its settings and privacy terms.
- **Provider transmission:** Chat text, including a reviewed voice transcript, is sent over HTTPS to the selected chat provider when the user sends it. Optional generated reply audio sends the selected reply text over HTTPS to ElevenLabs.
- **Provider Retention:** Retention depends on the provider, product/API, account settings and applicable terms, which can change. Review the selected provider's current terms before sending data.

#### User Control
- Users can disable voice input in Settings.
- Voice recognition does not automatically send a chat or store raw audio in Dua's chat history. Speech services may process or retain audio under their own terms.
- Generated reply audio is optional and user-initiated. Dua does not persist the generated audio as chat history.

---

### 2. Chat History & Conversations

#### Collection
- **Scope:** Chat messages (text and metadata) are stored locally on-device by default.
- **Cloud Sync (Optional):** Users explicitly opt-in to cloud snapshots. No syncing occurs without user action.

#### Local Storage
- **Android:** Chat history and app preferences are stored in app-private `SharedPreferences`; they are not encrypted by the app. Android OS sandboxing protects app-private files, but this is not equivalent to an encrypted database.
- **Backup:** Android app backup and device transfer are enabled. The account-session and automatic-sync preference files are excluded, but chat history and other app preferences are not excluded by the checked-in backup rules. Whether a backup is created or restored depends on Android/device backup settings and the user's account. Users should not assume local chats remain only on one device.
- **Retention:** Retained until the user deletes the data, clears app storage, uninstalls the app, or an enabled device backup restores it. Backup copies may have separate retention.

#### Cloud Storage (Opt-In)
- **Activation:** User must explicitly enable "Cloud Sync" in account settings.
- **Transport:** Messages are sent over HTTPS to Supabase (encrypted at rest).
- **Retention:** 
  - Users can clear their cloud snapshot from the app or delete their account.
  - Account deletion requests removal of the Supabase account and associated app data; provider backup-retention timing is controlled by Supabase and is not guaranteed by Dua.
  - The repository does not define or verify a fixed post-deletion backup-retention period.
- **Encryption:** Supabase Row Level Security (RLS) ensures only the account owner can access their snapshots.

---

### 3. User Account Data

#### Data Stored
- Email (for login & recovery)
- Account display name (optional)
- Local notification, appearance and provider selections are stored in app settings, not Supabase cloud snapshots.
- Provider API keys are configured server-side and are not stored in the app's local account profile.

#### Local Account (No Cloud)
- Users can chat without creating an account (local-only mode).
- No email or sign-up required for basic chat.

#### Cloud Account (Opt-In)
- Required for cloud snapshots, recovery, and multi-device sync.
- Authenticated via Supabase (email + password; native Google sign-in is optional when configured).
- Account data is encrypted and subject to RLS.

#### Retention
- Account data is retained as long as the account is active.
- Account deletion removes the Supabase account and associated cloud data when the configured deletion flow succeeds. It does not clear local chats, drafts, preferences or memories; users must delete those separately. Supabase backup retention is outside Dua's control.

---

### 4. Provider Integrations

#### What Dua Shares with Providers
- **Text Messages:** User prompts and chat history (if using a configured provider).
- **Attachments:** Images, PDFs, text/code files (provider-dependent; not all providers support all formats). Text from attached text/code files is kept in local chat history on the device and re-sent with later messages in the same chat; it is not included in exports or cloud snapshots.
- **Stored files (signed-in owner only):** Larger files, spreadsheets and audio are uploaded from the phone straight to a private Supabase Storage bucket (`dua-files`) in a folder named after your account ID; file names are not part of the stored path. For each reply that uses them, the backend either downloads the file and sends it to the provider (Gemini; Kimi images), gives the provider a link that expires after one hour (OpenAI, xAI/Grok, Claude), or extracts text itself (spreadsheets as CSV, text files, PDFs for providers without PDF input). Gemini may keep a copy for 48 hours when large files go through its Files API. xAI handles attached PDFs with its document search tool.
- **Audio:** Audio files and recorded voice notes are transcribed (by default xAI speech-to-text, then Gemini, then OpenAI, depending on which keys are configured). The transcript is sent to the chat provider and kept with the message on the device, so follow-ups don't transcribe the audio again.
- **Reasoning:** In Reasoning mode the model's thinking summary is saved with the reply in local history, exports and cloud snapshots.
- **Metadata:** Timestamp, model name, message count (to API endpoints).

#### What Dua Does NOT Share
- Email address
- Account data
- Other users' conversations
- Raw audio or voice logs
- Device identifiers

#### Provider Terms
Users are responsible for reviewing the privacy and retention policies of selected providers:
- OpenAI: https://openai.com/policies/privacy-policy
- Google: https://policies.google.com/privacy
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
- When a chat outgrows the selected model's budget, the server asks the same provider to summarise the older turns. The summary is stored with the chat **on your device only** (not in exports or cloud snapshots) and sent with later messages in that chat. Deleting the chat deletes it.

#### User Control
- Both modes are optional and off by default.
- Users can disable search/link reading at any time.

---

### 6. GitHub Integration (Read-Only)

#### Scope
- Uses a server-side GitHub token to list up to 30 repositories owned by the signed-in GitHub account. The endpoint requests `affiliation=owner`; it is not restricted in code to the Dua-ai repository.
- The feature does not write to repositories or modify code/issues.

#### Data Shared
- Repository names, visibility and descriptions returned by GitHub.
- The configured GitHub token is sent only by the server to GitHub; its permissions are determined by the token owner and GitHub configuration.

#### Data NOT Shared
- GitHub account settings.
- Repository content is not fetched by this listing endpoint, but repositories visible to the configured token may appear, including private repositories owned by the account.

#### Retention
- GitHub token is stored securely server-side.
- Tokens can be revoked by the user at any time via GitHub Settings → Applications.

---

## Data Retention Summary

| Data Type | Local | Cloud | Retention |
|-----------|-------|-------|-----------|
| Chat Messages | Android app-private `SharedPreferences` | Opt-in snapshot | Until local deletion/storage removal; Android backup may retain copies; cloud deletion/backup timing is controlled by Supabase |
| Audio/transcripts | Speech service; transcript may be retained locally as chat text if sent | No raw audio in snapshots | Speech-service terms apply; transcript follows chat retention; generated audio is not retained by Dua |
| Account Credentials | Encrypted Android session token; passwords not persisted | Supabase | Until logout, expiry or account deletion; provider backup retention may apply |
| API Keys | Server-side only | Server-side only | Until removed or rotated by the deployment operator; never stored on device |
| Search Queries | None | N/A (Brave) | Per Brave's current privacy policy and service terms |
| Cloud Snapshots | N/A | Supabase | User-managed; deletion and backup retention depend on Supabase project behavior |
| Stored files (uploads) | File references and audio transcripts in local chat history (not exported or synced) | Supabase Storage, private `dua-files` bucket | Kept until the chat that sent them is deleted (deletion is retried later if offline or signed out), until **Delete all uploaded files**, or until account deletion. Provider copies follow provider terms (for example, the Gemini Files API keeps files for 48 h) |

---

## User Rights & Controls

### Access
- Users can export their chat history (JSON format) at any time.
- Users can view their account settings and cloud snapshots in the app.

### Correction
- Users can edit or delete individual messages locally.
- Users can delete entire cloud snapshots.

### Deletion
- **Local Data:** Users can use in-app data controls or clear app storage. Android backup may retain or restore app data according to device settings.
- **Uploaded files:** Deleting a chat deletes the files uploaded in it. Settings → Data & privacy → Data controls shows storage used and has **Delete all uploaded files**. Clearing all chats deletes their files too. Clearing app storage without deleting chats first leaves files in the bucket; use Delete all uploaded files after signing in again.
- **Cloud Data:** Users can delete snapshots manually or request full account deletion.
- **Account Deletion:** Available from Profile when configured. It removes the account/cloud data on successful completion, and first tries to delete your uploaded files; it does not remove local chats or memories. Supabase backup-retention timing is outside Dua's control.

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

This policy is effective as of **2026-10-06**. Updates will be announced via GitHub Releases and the repository's discussions.

**Last Updated:** 2026-10-06
