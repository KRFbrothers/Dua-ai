# Dua AI documentation index

For a visual tour of the whole project, open the [complete project mind map](PROJECT_MIND_MAP.md): overview, subsystem maps, all six ADK categories/25 requirements, source links and pending work.

Start with [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit, and [OPERATIONS.md](OPERATIONS.md) for deploying and running them. Root guides ([README](../README.md), [SUPABASE](../SUPABASE.md), [SIGNING](../SIGNING.md), [SECURITY](../SECURITY.md)) cover setup and stay at the repository root because app/backend messages or the build point to them; the [engine README](../google-cloud-backend/README.md), [web/README](../web/README.md) and [website/README](../website/README.md) cover each service.

## Docs in this folder

| Doc | What it covers |
| --- | --- |
| [PROJECT_MIND_MAP.md](PROJECT_MIND_MAP.md) | Whole-project mind maps, source locations, all top-level folders and implemented/configured/pending boundaries |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Runtime map, request flow, data ownership, where to change what |
| [OPERATIONS.md](OPERATIONS.md) | Project/region, redeploy commands, safe settings changes, secrets, `dua-backup`, monitoring, logs, credit expiry |
| [AGENT.md](AGENT.md) | Agent tool loop vs ADK, Agent flags, Agent notes vs phone Memory, GitHub token permissions |
| [ADK_IMPLEMENTATION.md](ADK_IMPLEMENTATION.md) | ADK persistence/resume, pgvector RAG, provider routes, custom tools, usage/evals, CLI and staged rollback |
| [ADK_SCOPE.md](ADK_SCOPE.md) | Fareed's final six-part ADK scope, 25 requirements, implementation gaps and official open-source references |
| [WEBSITE.md](WEBSITE.md) | Website served by dua-api, email sign-in, browser access to the engine, roadmap |
| [TESTING.md](TESTING.md) | CI jobs, test commands, flagship feature tests |
| [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) | Owner-only lock, spending limits, staging/device checks, installing the APK |
| [PROVIDERS.md](PROVIDERS.md) | Node API AI provider keys, models, reasoning, Run code, attachments, web search, voice, Imagine |
| [PRIVACY.md](PRIVACY.md) | What data goes where and to which providers |
| [KILL_SWITCHES.md](KILL_SWITCHES.md) | Remote kill switches (Agent, Imagine, Voice, maintenance) via Cloud Run env vars, off by default |
| [PERFORMANCE.md](PERFORMANCE.md) | Baseline Profile, `:benchmark` module and startup benchmarks (needs a connected phone) |
| [ROADMAP.md](ROADMAP.md) | Dua AI improvement roadmap and phases |
| [REPLIES_CODE_CANVAS.md](REPLIES_CODE_CANVAS.md) | Thinking summaries, code blocks, previews, Run code, Artifacts Canvas |
| [ANDROID_ENTRY_POINTS.md](ANDROID_ENTRY_POINTS.md) | Home widget and share targets |
| [FEATURE_HUBS.md](FEATURE_HUBS.md) | Images, Library and Plugins pages |
| [PERSONALITY_PRESETS.md](PERSONALITY_PRESETS.md) | Personality presets and backend rollout |
| [PROJECTS_REFERENCE_FLOW.md](PROJECTS_REFERENCE_FLOW.md) | Projects screens, sources and backend setup |
| [SETTINGS_DEVICE_CHECK.md](SETTINGS_DEVICE_CHECK.md) | Settings hub and phone checks |
| [STORAGE_BACKUP.md](STORAGE_BACKUP.md) | Phone encrypted backups to Google Drive |
| [VOICE_RELIABILITY.md](VOICE_RELIABILITY.md) | Voice waveform, barge-in, typography tokens, device checks |
| [GITHUB_VALIDATION.md](GITHUB_VALIDATION.md) | GitHub repository/path policy and size limits |
| [GITHUB_TROUBLESHOOTING.md](GITHUB_TROUBLESHOOTING.md) | GitHub connector and Build recovery |
| [GITHUB_LOGGING.md](GITHUB_LOGGING.md) | dua-api GitHub request/stage logs |
| [ui/ANDROID_UI.md](ui/ANDROID_UI.md) | Android UI guide: screens/MVVM map, sample-conversation preview, approved navigation, Coming soon list |
| [ui/design-system-tokens.md](ui/design-system-tokens.md) | Android colour and typography tokens |
| [ui/previews/README.md](ui/previews/README.md) | Fareed's standalone chat animation and app-wide theme HTML previews |

## Feature guides ([features/](features/))

| Doc | What it covers |
| --- | --- |
| [features/FEATURES.md](features/FEATURES.md) | Feature overview table and status |
| [features/PROJECTS.md](features/PROJECTS.md) | Projects and custom instructions |
| [features/PROJECT_MEMORY.md](features/PROJECT_MEMORY.md) | Project memory notes |
| [features/TASKS.md](features/TASKS.md) | Project tasks |
| [features/REMINDERS.md](features/REMINDERS.md) | Task due dates and device reminders |
| [features/PLANNER.md](features/PLANNER.md) | Projects daily planner |
| [features/HISTORY_SEARCH.md](features/HISTORY_SEARCH.md) | History search, rename, pin, find in chat, delete |

## Environment variables (names and purpose only)

Values live in Secret Manager or the Cloud Run service settings — never in code or docs. Change them with `--update-env-vars` only ([OPERATIONS.md](OPERATIONS.md#changing-settings-safely)). Templates: [`google-cloud-backend/.env.example`](../google-cloud-backend/.env.example), [`web/.env.example`](../web/.env.example).

| Service | Variable(s) | Purpose |
| --- | --- | --- |
| both | `SUPABASE_URL`, `SUPABASE_ANON_KEY` (engine also accepts `SUPABASE_PUBLISHABLE_KEY`) | Supabase project and public key for owner sign-in checks |
| both | `DUA_ALLOWED_EMAIL` | The only account allowed to use Dua AI |
| both | `DUA_REQUIRE_LOGIN` | Login requirement flag (the engine always requires owner login) |
| both | `PORT` | Set by Cloud Run |
| engine | `GOOGLE_CLOUD_PROJECT`, `VERTEX_LOCATION`, `VERTEX_MODEL`, `VERTEX_THINKING_LEVEL`, `TITLE_TOKENS` | Vertex Gemini chat model, location, thinking level, title length |
| engine | `AGENT_*` (`AGENT_MODEL`, `AGENT_ENGINE`, `AGENT_VERIFY`, `AGENT_TEST_CODE`, `AGENT_NOTES`, …) | Agent behaviour and limits — full list in [AGENT.md](AGENT.md#settings-verified-from-code) |
| engine | `CODING_MAX_STEPS`, `CODING_MAX_SECONDS`, `CODING_STEP_TOKENS`, `GITHUB_TOKEN`, `DUA_REPO` | Optional coding agent limits, token and target repository |
| engine | `OPENROUTER_API_KEY`, `OPENROUTER_MAX_TOKENS` | Optional free OpenRouter models |
| engine | `DUA_IMAGINE`, `IMAGINE_MODEL`, `IMAGINE_DAILY_IMAGES` | Image generation on Google Cloud |
| engine | `DUA_APP_TOKEN` | Optional extra gate header (not a replacement for owner login) |
| engine | `WEB_ORIGINS`, `WEB_SKIP_APP_TOKEN` | Browser (website) access rules — [WEBSITE.md](WEBSITE.md#engine-access-from-the-browser) |
| backup job | `BACKUP_BUCKET`, `BACKUP_TABLES`, `DUA_FILES_BUCKET`, `SUPABASE_SERVICE_ROLE_KEY` | Target bucket, tables to export, Storage bucket to mirror, service-role key (job only) |
| dua-api | `SUPABASE_SERVICE_ROLE_KEY` | Optional server admin key: daily request accounting (with `DUA_DAILY_REQUEST_LIMIT`) and account deletion |
| dua-api | `NODE_ENV`, `K_SERVICE` | Runtime mode (set by the deploy script / Cloud Run) |
| dua-api | `DUA_DAILY_REQUEST_LIMIT`, `DUA_CHAT_DEADLINE_SECONDS` | Daily chat request cap and whole-request chat deadline |
| dua-api | `GEMINI_*`, `ANTHROPIC_*`, `GROK_*`, `KIMI_*`, `OPENAI_*` (`_API_KEY`, `_MODEL`, …) | Optional provider keys/models for Node API chat, transcription and code execution |
| dua-api | `DUA_NATIVE_SEARCH`, `DUA_X_SEARCH`, `DUA_SEARCH_PREFER_BRAVE`, `BRAVE_SEARCH_API_KEY` | Web search options |
| dua-api | `DUA_CODE_EXECUTION`, `GEMINI_CODE_EXECUTION`, `GROK_CODE_EXECUTION`, `OPENAI_CODE_EXECUTION` | Run code on/off per provider |
| dua-api | `ENABLE_GITHUB_CONNECTOR`, `GITHUB_CONNECTOR_TOKEN`, `DUA_BUILD_*`, `DUA_BUILD_PR`, `GITHUB_BUILD_WRITE_TOKEN`, `DUA_BUILD_PR_*` | GitHub read connector, Build mode limits and owner-confirmed Build PRs |
| dua-api | `DUA_FILES`, `DUA_FILES_BUCKET`, `DUA_FILES_MAX_MB`, `DUA_HISTORY_FILES`, `DUA_FILE_TEXT_MAX_CHARS`, `DUA_SHEET_MAX_*` | Stored files and file/spreadsheet reading limits |
| dua-api | `DUA_TRANSCRIBE_PROVIDER`, `*_TRANSCRIBE_MODEL` | Audio transcription |
| dua-api | `DUA_IMAGINE_*` | Imagine image/video providers, models and daily caps |
| dua-api | `DUA_VOICE_*`, `ENABLE_GENERATED_VOICE`, `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID` | Reply voices, live voice and voice caps |
