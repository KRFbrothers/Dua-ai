# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, use GitHub's **Private vulnerability reporting** or create a private security advisory for this repository. If neither option is available, contact a repository maintainer privately. **Do not** open a public issue for security vulnerabilities. No monitored security email address is published in this repository; repository administrators should enable private vulnerability reporting in GitHub settings.

Include:
- Description of the vulnerability
- Steps to reproduce (if applicable)
- Potential impact
- Your recommended fix (if any)

We will acknowledge reports within 48 hours and aim to provide an update within 7 days.

## Security Guidelines

### Credentials & Secrets
- **Never commit API keys, tokens, or credentials** to version control.
- Use `.env` files (ignored by `.gitignore`) for local development only.
- Store production secrets in:
  - GitHub Actions Secrets for CI/CD
  - Cloud provider Secret Manager (Google Cloud Secret Manager, AWS Secrets Manager, etc.)
  - Supabase Vault for application credentials
- Verify repository/org-level secret scanning and push protection in GitHub settings; this repository does not configure those hosted settings.

### Dependency Management
- Keep dependencies up to date; review the Android dependency graph and run dependency checks supported by the configured environment.
- Verify GitHub dependency graph, Dependabot alerts and update PR settings in repository/org settings; they are not configured by the checked-in CI workflow.

### Code Review & Branch Protection
- Configure branch protection/rulesets to require review and the `Project checks` status before merging to `main`.
- Branch protection, required reviews/checks and force-push rules are GitHub-hosted settings and must be verified by a repository administrator.

### Privacy & Data Handling

#### Audio & Voice
- Audio data captured for speech recognition is:
  - Passed to the selected Android recognition service; processing may be local or remote depending on that service and its settings.
  - Not automatically sent to the chat provider. A reviewed transcript is sent only when the user submits it.
  - Subject to the selected speech service's own privacy and retention terms.
- Optional generated reply audio sends selected reply text to the configured xAI, Google Gemini, or ElevenLabs service.

#### Server-backed Chat and Memory
- Signed-in chat history and memory are authoritative in the owner’s Supabase snapshot; the phone keeps a disposable cache for startup and offline viewing.
- Automatic foreground refresh is opt-in per device. Signed-out use is cache-only and cannot write an authoritative server history.
- Snapshots use Supabase RLS and per-user access controls. They are not end-to-end encrypted by Dua.
- Users can delete snapshots manually; post-deletion backup retention depends on Supabase project policy and has not been verified here.
- Users can request full data export/deletion via account settings.

#### Owner Lock and Storage Verification
- Production access depends on both `DUA_REQUIRE_LOGIN=1` and the exact `DUA_ALLOWED_EMAIL` value. Login-required mode alone does not make the server owner-only.
- Stored-file paths are account-scoped and must be checked against the verified Supabase user before signing, opening, deleting, or re-attaching a file.
- A private `dua-files` bucket, RLS policies, signed-link expiry, and account deletion cleanup require live staging verification; repository tests cannot prove deployed project settings.

#### Request Burst Limits
- The former Vercel per-IP burst guard was removed with Vercel. On Cloud Run, every data route still requires the verified owner session (`DUA_ALLOWED_EMAIL`), and Imagine, voice and request quotas are enforced by the Supabase daily counters. Cloud Run's maximum-instances setting bounds concurrency.

#### Device Cache
- Android cache data remains in app-private storage for offline viewing and fast startup; it is not the authoritative copy.
- Export/import operate on the cached view. Deleting the cache does not delete the signed-in server snapshot.

### Secrets Scanning
- GitHub secret scanning and push-protection status are controlled by repository/org settings and must be checked by an administrator.
- Revoke or rotate any credential exposed in a commit; do not assume a provider has automatically revoked it.

## Compliance & Audits

### Current Compliance Status
- ✅ MIT License
- ✅ Privacy guidelines documented
- Repository/org settings such as branch protection, required reviews/checks, secret scanning and Dependabot are not verifiable from this checkout.

### Testing & CI/CD
- Backend (`web/`): Node tests cover authentication, owner access, storage, account deletion, providers and sync behavior. Run `cd web && npm test`.
- Android: Gradle JVM unit tests and source compilation; no APK or instrumented tests in CI.
- The checked-in workflows do not run dependency or secret scanning. Verify any GitHub-hosted scanning configuration separately.

## Updates & Notifications

Security updates to this policy are made on a rolling basis. Critical changes are announced in the repository's discussions and via GitHub Releases.

---

*Last Updated: 2026-10-06*
