# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, use GitHub's **Private vulnerability reporting** or create a private security advisory for this repository, if the feature is enabled. **Do not** open a public issue for security vulnerabilities. No monitored security email address is published in this repository.

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
- All changes to `main` require at least one approved PR review.
- Status checks (web tests, Android compilation/tests) must pass before merge.
- Force-push to `main` is disabled.

### Privacy & Data Handling

#### Audio & Voice
- Audio data captured for speech recognition is:
  - Passed to the selected Android recognition service or browser speech service; processing may be local or remote depending on that service and its settings.
  - Not automatically sent to the chat provider. A reviewed transcript is sent only when the user submits it.
  - Subject to the selected speech service's own privacy and retention terms.
- Optional generated reply audio sends selected reply text to the configured ElevenLabs service.

#### Cloud Snapshots & Sync
- Cloud sync is opt-in; users explicitly enable it.
- Snapshots use Supabase RLS and per-user access controls. They are not end-to-end encrypted by Dua.
- Users can delete snapshots manually; post-deletion backup retention depends on Supabase project policy and has not been verified here.
- Users can request full data export/deletion via account settings.

#### Local Data
- Android local data remains on the device; it is not automatically uploaded.
- Web local data uses browser local storage; it is not synced without explicit opt-in.
- Export/import are user-initiated.

### Secrets Scanning
- GitHub secret scanning and push-protection status are controlled by repository/org settings and must be checked by an administrator.
- Revoke or rotate any credential exposed in a commit; do not assume a provider has automatically revoked it.

## Compliance & Audits

### Current Compliance Status
- ✅ MIT License
- ✅ Branch protection on `main`
- ✅ Required PR approvals & status checks
- ✅ Privacy guidelines documented
- Repository/org settings such as branch protection, secret scanning and Dependabot are not verifiable from this checkout.

### Testing & CI/CD
- Web: Node.js built-in test runner with mocked services.
- Android: Gradle JVM unit tests and source compilation; no APK or instrumented tests in CI.
- The checked-in workflows do not run dependency or secret scanning. Verify any GitHub-hosted scanning configuration separately.

## Updates & Notifications

Security updates to this policy are made on a rolling basis. Critical changes are announced in the repository's discussions and via GitHub Releases.

---

*Last Updated: 2026-10-06*
