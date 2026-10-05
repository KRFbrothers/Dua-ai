# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please email security@dua-ai.local or create a private security advisory on GitHub. **Do not** open a public issue for security vulnerabilities.

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
- All credential-carrying files are monitored by GitHub's secret scanning.

### Dependency Management
- Keep dependencies up to date; run `npm audit` and `gradle --refresh-dependencies` regularly.
- Dependency scanning is enabled; critical vulnerabilities trigger CI alerts.
- Review and approve Dependabot PRs promptly.

### Code Review & Branch Protection
- All changes to `main` require at least one approved PR review.
- Status checks (web tests, Android compilation/tests) must pass before merge.
- Force-push to `main` is disabled.

### Privacy & Data Handling

#### Audio & Voice
- Audio data captured for speech recognition is:
  - Processed on-device when possible (Android RecognitionService, Web Speech API).
  - Transmitted to the configured provider only when sent as part of a message.
  - Retained according to provider terms (OpenAI, Google, Anthropic, etc.).
- Users are informed via privacy notice that voice input may be transmitted to AI providers.

#### Cloud Snapshots & Sync
- Cloud sync is opt-in; users explicitly enable it.
- Snapshots are stored encrypted in Supabase with per-user access controls.
- Retention: Users can delete snapshots manually; admin retention is 90 days post-deletion per Supabase policy.
- Users can request full data export/deletion via account settings.

#### Local Data
- Android local data remains on the device; it is not automatically uploaded.
- Web local data uses browser local storage; it is not synced without explicit opt-in.
- Export/import are user-initiated.

### Google Agent Permissions
The Google ADK agent uses a GitHub Personal Access Token with **minimal scope**:
- ✅ `Contents: read/write` — only for Dua-ai repository, for creating branches and PRs.
- ✅ `Pull requests: read/write` — only for managing the agent's own PRs.
- ✅ `Actions: read/write` — only to read workflow results, not to modify workflows.
- ❌ NO Administration, Workflows, or Organization permissions.
- ❌ NO access to other repositories.

Token storage:
- Development: Stored in `google-agent/dua_coder/.env` (ignored, not committed).
- Production: Stored in Google Cloud Secret Manager (`dua-github-token` secret).
- Token rotation: Refresh at least quarterly; monitor for unusual activity in audit logs.

### Secrets Scanning
- GitHub Secret Scanning is enabled (push protection).
- Secrets found in commits are automatically revoked if supported by the provider.
- Manual audits of git history are performed quarterly.

## Compliance & Audits

### Current Compliance Status
- ✅ MIT License
- ✅ Branch protection on `main`
- ✅ Required PR approvals & status checks
- ✅ GitHub Secret Scanning (enterprise feature)
- ✅ Dependency Scanning (GitHub/Dependabot)
- ✅ Privacy guidelines documented
- ✅ Minimal GitHub Agent token permissions

### Testing & CI/CD
- Web: Jest/Node tests for all backend logic, Vercel integration tests.
- Android: Gradle unit tests for compilation and logic; no APK testing in CI.
- Security: Dependency scanning on each push; manual audits quarterly.

## Updates & Notifications

Security updates to this policy are made on a rolling basis. Critical changes are announced in the repository's discussions and via GitHub Releases.

---

*Last Updated: 2026-10-05*
