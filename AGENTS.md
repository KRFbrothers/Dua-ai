# AGENTS.md — Guide for AI coding agents (Dua AI)

## Project overview
- **Dua AI** is an **Android-only personal app** (Kotlin + Jetpack Compose). There is no iOS app.
- Backend runs on **Google Cloud Run**, project `global-healer-m6shk`, region `us-central1`:
  - `dua-api` — Node.js API (source in `web/`), also serves the built website from `web/site/`.
  - `dua-ai-engine` — Python AI engine (source in `google-cloud-backend/`).
- Marketing/website is a Vite + React + TypeScript app in `website/`.

## Folder map
| Path | What it is |
|---|---|
| `app/` | Android app (Compose). Code in `app/src/main/java/com/dua/ai/`, unit tests in `app/src/test/` |
| `app/src/main/java/com/dua/ai/ui/theme/` | Theme, colours, `DuaTheme.semantic` |
| `web/` | `dua-api` Node service (`src/server/`, tests in `web/test/`) |
| `website/` | Vite React website (builds into `web/site/`) |
| `google-cloud-backend/` | `dua-ai-engine` Python service (tests in `google-cloud-backend/tests/`) |
| `supabase/` | Supabase config and migrations (do not change migrations) |
| `docs/`, `scripts/`, `benchmark/`, `third_party/` | Docs, helper scripts, benchmarks, vendored code |
| `.github/workflows/` | CI (`checks.yml`, `personal-apk.yml`) — **do not touch** |

## Build and test commands
These match the 4 CI checks in `.github/workflows/checks.yml`.
- **Android build + unit tests:** `./gradlew :app:testDebugUnitTest -Pdua.ui-record --no-daemon --max-workers=2`
- **Backend (dua-api) tests:** `cd web && npm ci --ignore-scripts && npm test` (Node >= 24)
- **Website build:** `cd website && npm ci && npm run build` (Node >= 20; type-check + Vite build into `web/site/`)
- **Engine (dua-ai-engine) tests:** `python -m pytest -q google-cloud-backend/tests`

## RULES for AI agents
1. The owner is a **non-coder**. Explain everything in **simple Hinglish**.
2. **Before any change**, state: what will change, what can break, and how many files.
3. Work only per `AUDIT_REPORT.md`, and **confirm with the owner before writing code**.
4. Branch names must start with `dua/` or `refactor/`.
5. **One PR = one task**, maximum **5 files** per PR.
6. **Never merge** a PR, **never push to `main`**, **never force-push**.
7. Never touch: `.github/workflows/`, `.git`, `.gitmodules`, `app/src/main/assets/vendor/mermaid/`.
8. **Never delete old files.**
9. **Never change deploy config or database migrations.**
10. **No secrets** (keys, tokens, passwords) in code, commits, screenshots, or chat.
11. Cloud Run env changes only with `--update-env-vars` (never `--set-env-vars`, which wipes existing vars).
12. If the owner says **"Stop"**, stop immediately.
13. Colours: **never hardcode `Color(0x...)` outside `ui/theme`**. Use `MaterialTheme.colorScheme` or `DuaTheme.semantic`.
14. After each task, send a **5-line report**: what changed, files touched, tests/CI result, risks, next step.
