# Privacy Policy

This document outlines how Dua AI handles user data, privacy, and retention across its native Android app, web app, and cloud services.

## Data Collection & Processing

### 1. Audio & Voice Data

#### Collection
- **Source:** Users initiate voice input via microphone (Android `RecognitionService`, Web Speech API).
- **Scope:** Audio is captured locally and transmitted only when explicitly sent as part of a chat message.
- **Consent:** Users grant microphone permissions at the OS level (Android) or browser level (Web); the app displays a permission notice before first use.

#### Processing
- **Local Processing:** Device speech recognition (Android, Web) runs on-device; audio is NOT stored.
- **Provider Transmission:** If audio is sent to an AI provider (OpenAI, Google Gemini, Anthropic, etc.), it is transmitted securely (HTTPS).
- **Provider Retention:** Each provider has its own retention policy:
  - OpenAI: Up to 30 days (see OpenAI privacy policy)
  - Google Gemini: Up to 3 days (see Google privacy policy)
  - Anthropic/Claude: Up to 30 days (see Anthropic privacy policy)
  - User is responsible for reviewing provider terms.

#### User Control
- Users can disable voice input in Settings.
- Voice messages are not automatically saved; users choose to send them.
- Audio is never stored by Dua AI itself (no server-side logs of raw audio).

---

### 2. Chat History & Conversations

#### Collection
- **Scope:** Chat messages (text and metadata) are stored locally on-device by default.
- **Cloud Sync (Optional):** Users explicitly opt-in to cloud snapshots. No syncing occurs without user action.

#### Local Storage
- **Android:** Stored in encrypted local database (Room).
- **Web:** Stored in browser local storage; survives page refresh but cleared if cache is cleared.
- **Retention:** Indefinite unless user manually deletes.

#### Cloud Storage (Opt-In)
- **Activation:** User must explicitly enable "Cloud Sync" in account settings.
- **Transport:** Messages are sent over HTTPS to Supabase (encrypted at rest).
- **Retention:** 
  - User-managed: Users can delete snapshots manually at any time.
  - Admin retention: 90 days after user deletion (for backup/audit purposes).
  - Account deletion: All snapshots deleted immediately upon account closure.
- **Encryption:** Supabase Row Level Security (RLS) ensures only the account owner can access their snapshots.

---

### 3. User Account Data

#### Data Stored
- Email (for login & recovery)
- Account display name (optional)
- Notification preferences
- Model & provider selections
- API key references (keys themselves are NOT stored; only whether integration is enabled)

#### Local Account (No Cloud)
- Users can chat without creating an account (local-only mode).
- No email or sign-up required for basic chat.

#### Cloud Account (Opt-In)
- Required for cloud snapshots, recovery, and multi-device sync.
- Authenticated via Supabase (email + password or OAuth).
- Account data is encrypted and subject to RLS.

#### Retention
- Account data is retained as long as the account is active.
- Upon account deletion, all associated data (including cloud snapshots, email, preferences) is deleted within 30 days.

---

### 4. Provider Integrations

#### What Dua Shares with Providers
- **Text Messages:** User prompts and chat history (if using a configured provider).
- **Attachments:** Images, PDFs, TXT files (provider-dependent; not all providers support all formats).
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

#### User Control
- Both modes are optional and off by default.
- Users can disable search/link reading at any time.

---

### 6. GitHub Integration (Read-Only)

#### Scope
- Limited read-only access to the Dua-ai repository.
- No write access; no modifications to code or issues.

#### Data Shared
- GitHub username (if authenticated)
- OAuth token scope: read-only repository contents.

#### Data NOT Shared
- Other repositories.
- User's GitHub settings or private repos.

#### Retention
- GitHub token is stored securely server-side.
- Tokens can be revoked by the user at any time via GitHub Settings → Applications.

---

### 7. Google Agent (Optional Automation)

#### Scope
- Separate Python agent for code automation (not the chat app).
- Used for reviewing code and generating PR suggestions.

#### Data Access
- Read-only: Dua-ai repository contents at a pinned commit.
- Write-only: Creates branches and draft PRs (no merging, no write to main).

#### Token Permissions
- **Minimal scope:** Contents (read/write), Pull Requests (read/write), Actions (read).
- **Repository-specific:** Token access is limited to KRFbrothers/Dua-ai only.
- **No administrative access:** Token cannot modify workflows, settings, or repository administration.

#### Retention
- Token is stored in Google Cloud Secret Manager (production) or `.env` (development).
- Token is rotated at least quarterly.

---

## Data Retention Summary

| Data Type | Local | Cloud | Retention |
|-----------|-------|-------|-----------|
| Chat Messages | On-device DB | Opt-in snapshot | Indefinite (local); user-managed (cloud); 90 days post-deletion (admin backup) |
| Audio | On-device (not logged) | N/A | Immediate (device); per provider (if sent) |
| Account Credentials | N/A | Supabase | Until account deletion; 30 days for audit logs |
| API Keys | Server-side only | Server-side only | Until revoked by user; never stored on device |
| Search Queries | None | N/A (Brave) | Per Brave Search policy (~7 days) |
| Cloud Snapshots | N/A | Supabase | User-managed; 90 days post-deletion (backup); deleted on account closure |

---

## User Rights & Controls

### Access
- Users can export their chat history (JSON format) at any time.
- Users can view their account settings and cloud snapshots in the app.

### Correction
- Users can edit or delete individual messages locally.
- Users can delete entire cloud snapshots.

### Deletion
- **Local Data:** Users can clear app cache or uninstall to remove local data.
- **Cloud Data:** Users can delete snapshots manually or request full account deletion.
- **Account Deletion:** Accessible via Account → Settings → Delete Account; all associated data deleted within 30 days.

### Portability
- Users can export chat history and settings in standard JSON format.

---

## Third-Party Services

Dua AI integrates with:
- **Supabase** (auth, cloud storage): https://supabase.com/privacy
- **Vercel** (web hosting, API): https://vercel.com/legal/privacy-policy
- **Firebase Crashlytics** (error reporting, Android only): https://firebase.google.com/support/privacy
- **AI Providers** (OpenAI, Google, Anthropic, etc.): See provider privacy policies above.

---

## Security Measures

- HTTPS encryption for all data in transit.
- Supabase Row Level Security (RLS) for cloud data isolation.
- Server-side credential storage (no API keys on client devices).
- GitHub Secret Scanning enabled.
- Quarterly security audits and dependency reviews.

---

## Policy Updates

This policy is effective as of **2026-10-05**. Updates will be announced via GitHub Releases and the repository's discussions.

**Last Updated:** 2026-10-05
