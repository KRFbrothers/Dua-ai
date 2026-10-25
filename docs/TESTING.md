# Testing index

Where tests live, how CI runs them, and which focused tests cover the flagship Android features.

Android app tests currently live under `app/src/test` (JVM, Compose and Robolectric).
There is no `app/src/androidTest` suite; the separate `benchmark` module has device
tests for baseline profiles. These do not provide app instrumentation coverage.
Android lint is available through the Android Gradle plugin even without an explicit
`lint {}` block. Its error/report settings are now explicit in `app/build.gradle.kts`.
Run `./gradlew :app:lintDebug` locally; the current checks workflow does not run lint.
No lint baseline has been generated or existing findings suppressed.

## CI jobs (`.github/workflows/checks.yml`)

Every pull request and push to `main` runs four jobs:

| Job | What runs | Local command |
| --- | --- | --- |
| Google Cloud engine tests | Python tests in `google-cloud-backend/tests` run twice: minimal Flask + pytest, then production ADK/GenAI dependencies with scripted offline models; no live provider, Supabase or GitHub calls | `python -m pytest -q google-cloud-backend/tests` (repo root) |
| Backend tests | Node tests in `apps/api/test` (`npm run test:ci`, needs Node 24) | `cd apps/api && npm ci && npm test` |
| Website build | `apps/web/`: `npm ci`, type-check and Vite build into `apps/api/site`, then checks `index.html` and the logo exist | `cd apps/web && npm ci && npm run build` |
| Android build and tests | `./gradlew :app:testDebugUnitTest -Pdua.ui-record` (JVM, Compose and Robolectric tests) | see below |

The [personal APK workflow](../.github/workflows/personal-apk.yml) is a separate, manually started signing/build path ([SIGNING.md](../SIGNING.md)). Tests use mocked integrations; passing them does not verify deployed Auth, provider billing, live RLS or physical-device behavior.

## Agent / ADK SDK checks

The engine CI job keeps the minimal dependency run to verify lazy imports and the
normal-loop fallback. It then installs `google-cloud-backend/requirements.txt`
into a separate environment and reruns the suite with the **real pinned ADK**.
An explicit SDK import fails the job if installation/import is broken, rather
than silently skipping the real-SDK tests. Both JUnit reports are retained.

To run that second pass locally, in a dedicated virtual environment:

```sh
python -m pip install -r google-cloud-backend/requirements.txt pytest==8.3.5
python -c "import google.adk, google.genai"
python -m pytest -q google-cloud-backend/tests
```

`test_agent_adk.py` drives the real `ParallelAgent` / `SequentialAgent` with a
scripted `BaseLlm`: tools use fake search/notes, writes remain pending approval,
and disconnect prevents subsequent model calls. The final-answer case returns
multiple GenAI text Parts so losing paragraphs/citations fails the SDK test too.
Runner tests also cover thought delivery before generation finishes, cleanup on
session/model/callback failures, disconnect while the real SDK awaits a model
response, and deadline cleanup before the merger can run. Cleanup probes use the
SDK's `App` plugin configuration, and all model responses are scripted locally.
Planner/proposal cases reject invalid JSON field types and unmatched file pairs;
empty file contents remain valid. A real-SDK looping model proves each helper
stops at its own call allowance and the merger still runs with incomplete-result
markers. Another scripted model repairs an invalid two-file proposal, yielding
exactly one complete pending approval and no repository write. Callback cases
also verify cancellation priority and independent helper/request call budgets.
These tests do not measure a deployed model's quality, latency or billing, and do
not enable `AGENT_ENGINE=adk` in production.

## Android test commands

Run the complete standard Android test task (includes Compose/Robolectric tests):

```sh
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

For a focused run, add one or more `--tests` options, for example:

```sh
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2 \
  --tests com.dua.ai.ui.chat.ThinkingTreeTest \
  --tests com.dua.ai.model.MessageArtifactsTest \
  --tests com.dua.ai.ui.chat.ArtifactCanvasTest \
  --tests com.dua.ai.ui.chat.RichSourceCardsTest \
  --tests com.dua.ai.settings.AppearancePreferencesTest \
  --tests com.dua.ai.ui.settings.PersonalitySettingsTest \
  --tests com.dua.ai.viewmodel.PersonalityPromptingTest \
  --tests com.dua.ai.voice.BargeInDetectorTest \
  --tests com.dua.ai.ui.voice.VoiceBargeInIntegrationTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and XML under `app/build/test-results/testDebugUnitTest/`. CI runs the full task with `-Pdua.ui-record` to preserve UI captures. Separate opt-in snapshot-recording classes under `screenshots/` keep the repository's existing `dua.screenshots` configuration.

## Flagship feature tests

These suites use the real parsers, Compose components, DataStore, ViewModel request path and voice cancellation classes. HTTP is intercepted before network access; audio playback uses a fake sink. Tests do not require provider keys or a user account.

| Capability | Focused tests | What they verify |
| --- | --- | --- |
| Thinking step tree | `ThinkingTreeTest` | Explicit summary headings/details, streamed new steps, collapse when answering starts, manual reopen, Agent summaries remaining opt-in; no invented steps. |
| Artifact detection / Canvas | `MessageArtifactsTest`, `ArtifactCanvasTest` | Supported/unsupported and incomplete fences, completed-stream detection using final source, Preview/Code, exact copy payload, renderer disposal, system back and large text. |
| Rich citation cards | `ReplySourcesTest`, `RichSourceCardsTest` | Parsed/structured sources, two-line titles, scroll, unchanged accessibility labels, safe URL opening, new citation data replacing the old initial/target, light/dark and large text. |
| Personality persistence / prompting | `AppearancePreferencesTest`, `PersonalitySettingsTest`, `PersonalityPromptingTest`, `DuaApiClientEngineTest` | All presets, restart/reset and invalid IDs, accessible selection, real ViewModel send/retry HTTP payloads for both engine routes, selected-current-preset behavior, disabled memory retained and unsupported backends leaving the draft untouched. |
| Voice barge-in | `BargeInDetectorTest`, `VoiceBargeInIntegrationTest`, `VoiceModeSessionTest`, `VoiceExperienceTest` | Noise/onset gating, real ReplyVoice cancellation releasing/wiping generated audio before listening, exactly-once interruption, stale completions not changing a newer reply, mute/pause/close guards and accessible tap interruption. |

These automated checks do not certify physical microphone echo cancellation, Bluetooth routing, interruption latency, 60fps, PSS below 180MB or full Chromium rendering. Use [voice device checks](VOICE_RELIABILITY.md), [Canvas and preview checks](REPLIES_CODE_CANVAS.md#tests-and-device-checks) and [personality rollout/phone checks](PERSONALITY_PRESETS.md) for those results.

## Other focused guides with tests

- Feature hubs: [FEATURE_HUBS.md](FEATURE_HUBS.md#validation)
- Settings: [SETTINGS_DEVICE_CHECK.md](SETTINGS_DEVICE_CHECK.md#automated-checks)
- Storage/backups: [STORAGE_BACKUP.md](STORAGE_BACKUP.md#archive-implementation-and-verification)
- GitHub policy and logs: [GITHUB_VALIDATION.md](GITHUB_VALIDATION.md#verification), [GITHUB_LOGGING.md](GITHUB_LOGGING.md#verify)
- Design tokens: [ui/design-system-tokens.md](ui/design-system-tokens.md#checks)

### ADK completion integration checks

The production SDK pass installs `requirements-adapters-lock.txt` and explicitly
imports SDK/database/provider adapters. A disposable pgvector/PostgreSQL CI
service exercises native vectors, least-privilege schema setup and official SDK
resume, in addition to SQLite/mock tests. Tests never call paid providers.
The new `test_adk_capabilities.py` covers context/streaming, usage, retries,
custom tools, persistence, CLI/evaluation and rollback commands.

Locally, set `ADK_TEST_POSTGRES_URL` only for a disposable loopback test database
administrator. `test_adk_postgres.py` creates its own random database/role and
cleans only those; it refuses non-local hosts. Without that opt-in, the native
PostgreSQL case skips and cannot establish PostgreSQL verification. Quality
scoring is separate from regression counts; see `evals/adk-v1.json` and
`python google-cloud-backend/adk_cli.py evaluate --help`.
