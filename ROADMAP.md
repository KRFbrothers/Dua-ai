# Dua AI Improvement Roadmap

## Goal
Make Dua AI easier to maintain, safer to extend, faster to understand, and more reliable across the Android app, the web/backend layer, and the Google Cloud engine.

## Current shape of the project
- **Android app** in `app/`
- **Backend / orchestration layer** in `web/`
- **Cloud Run engine** in `google-cloud-backend/`
- **Tests** in `web/test/` and `google-cloud-backend/tests/`
- **Docs** in `README.md` and `docs/`

The codebase already has strong safety ideas: repository validation, path validation, read-only GitHub access in some flows, and CI checks. This roadmap focuses on making those strengths easier to grow and maintain.

---

## 1) Quick wins: 1–3 days

### 1.1 Improve documentation
- Expand the main `README.md` with a clearer architecture overview.
- Add a "How the pieces fit together" section for:
  - Android app
  - Vercel backend in `web/`
  - Google Cloud Run engine
  - GitHub connector / build flows
- Add a simple local setup guide.
- List required environment variables in one place.
- Add screenshots or a short feature tour if possible.

### 1.2 Add a contributor-friendly project map
- Create a `docs/ARCHITECTURE.md` or `docs/PROJECT_STRUCTURE.md`.
- Explain what each major directory is for.
- Describe the safe boundaries between read-only and write-capable code.

### 1.3 Make errors easier to understand
- Standardize user-facing error messages.
- Replace technical failures with actionable hints where possible.
- Add examples for common failures:
  - bad repository name
  - invalid file path
  - GitHub auth failures
  - missing environment variables

### 1.4 Add `.env.example` files
- Provide sample environment variables for:
  - `web/`
  - `google-cloud-backend/`
- Mark secrets clearly and keep unsafe values empty.

---

## 2) Stability and safety: 1–2 weeks

### 2.1 Centralize validation
- Keep repository name validation in one shared place.
- Keep repository path validation in one shared place.
- Keep file size and request size limits documented and consistent.
- Avoid duplicate validation logic across modules when possible.

### 2.2 Harden GitHub workflows
- Review all code paths that can read from GitHub.
- Keep write permissions limited to the smallest possible surface.
- Ensure workflow file edits and `.git` paths remain blocked where intended.
- Add tests for unsafe path patterns and repo names.

### 2.3 Improve agent and build safety
- Add more tests for proposal creation and PR generation.
- Validate branch naming, file ordering, and change limits more strictly.
- Make stale-state / version mismatch messages easier to recover from.
- Ensure any "agent" flow cannot silently escalate permissions.

### 2.4 Add structured logging
- Log key request stages:
  - repository loading
  - search
  - file reads
  - proposal creation
  - PR submission
- Keep logs concise and privacy-aware.
- Add correlation IDs or request IDs if not already present.

---

## 3) Testing and quality: 2–4 weeks

### 3.1 Expand unit tests
Add coverage for:
- invalid repository strings
- unsafe paths
- branch name generation
- file read truncation
- search result limits
- GitHub API error handling
- PR validation edge cases

### 3.2 Add integration tests
- Test Android-to-backend request flows.
- Test backend-to-GitHub connector behavior with mocks.
- Test Cloud Run engine auth and ownership checks.
- Test build/chat flows with realistic repository data.

### 3.3 Add regression tests for important bug classes
- path traversal attempts
- null / empty / oversized inputs
- duplicate file changes
- network failures and timeout behavior
- malformed GitHub responses

### 3.4 Improve CI coverage
- Run linting if available.
- Keep test suites separated by layer:
  - Android
  - web backend
  - Google Cloud engine
- Make failing tests easier to triage with artifacts.

---

## 4) Architecture improvements: 1–2 months

### 4.1 Refactor shared logic
- Move shared validation and formatting into smaller utility modules.
- Reduce duplicated constants across files.
- Normalize repository/file handling patterns.

### 4.2 Separate responsibilities more clearly
- Keep UI, API orchestration, and GitHub access cleanly separated.
- Reduce "mega modules" with too many responsibilities.
- Make it easier to swap providers or backend behavior later.

### 4.3 Improve caching and efficiency
- Cache repeated repository reads where safe.
- Avoid unnecessary repeated GitHub calls.
- Consider incremental tree/file loading for large repositories.
- Keep rate-limit behavior visible to the user.

### 4.4 Make feature flags and config clearer
- Document which features depend on which env vars.
- Separate optional integrations from required runtime config.
- Make unsupported features fail gracefully.

---

## 5) Product and UX improvements: 1–3 months

### 5.1 Improve onboarding
- Add a first-run guide.
- Explain the main modes and constraints.
- Show what is local, what is server-backed, and what needs sign-in.

### 5.2 Improve feedback during long operations
- Better loading/progress states.
- Clear "what the agent is doing" indicators.
- Better progress for build/chat/search flows.

### 5.3 Improve repository workflow UX
- Show current repository context more clearly.
- Add better branch / PR status visibility.
- Make proposal review easier.
- Show source files or references in a more structured way.

### 5.4 Improve settings discoverability
- Group advanced integration settings.
- Explain what each toggle changes.
- Make dangerous options harder to enable accidentally.

---

## 6) Security and privacy hardening: ongoing

### 6.1 Principle of least privilege
- Keep tokens scoped as narrowly as possible.
- Use read-only access wherever possible.
- Keep write actions isolated and auditable.

### 6.2 Protect sensitive data
- Avoid logging secrets or tokens.
- Sanitize errors before showing them to users.
- Review where cached content is stored and for how long.

### 6.3 Review authentication flows
- Verify ownership checks remain strict.
- Ensure fallback or offline paths do not bypass auth rules.
- Add tests for untrusted input sources.

### 6.4 Audit dangerous operations
- Repository write actions
- Branch creation
- PR creation
- File edits
- Signed upload flows

---

## Suggested priority order

### Phase 1 (1–3 days)
- Documentation
- `.env.example`
- Better error messages
- Basic roadmap / architecture docs

### Phase 2 (1–2 weeks)
- Validation centralization
- More tests
- CI improvements
- Logging

### Phase 3 (1–2 months)
- Refactoring and modularization
- Caching and performance
- UX polish
- Security hardening

---

## Success criteria
You'll know the repo is better when:
- a new contributor can understand the structure in 10–15 minutes
- common errors are easy to diagnose
- tests catch invalid inputs before runtime
- GitHub-related operations remain tightly controlled
- new features can be added without touching too many files
- Android, backend, and cloud engine responsibilities stay clear

---

## Implementation guide

### For Phase 1, start with:
1. **Documentation** – update `README.md` with architecture section
2. **`.env.example`** – create in `web/` and `google-cloud-backend/`
3. **`docs/ARCHITECTURE.md`** – explain the structure
4. **Error messages** – review and standardize in `web/src/core/github.mjs` and `web/src/core/build-pr.mjs`

### For each phase:
- Create an issue or pull request per section
- Keep changes focused and testable
- Document what you changed and why
- Get feedback before Phase 2 starts

---

## Notes
This roadmap is intentionally practical: it focuses on improving the existing project instead of redesigning it from scratch. It respects the strong safety-first design that's already in place.

**Ready to start?** Pick one item from Phase 1 and let me help you implement it!
