# Testing index

Where tests live, how CI runs them, and which focused tests cover the flagship Android features.

## CI jobs (`.github/workflows/checks.yml`)

Every pull request and push to `main` runs four jobs:

| Job | What runs | Local command |
| --- | --- | --- |
| Google Cloud engine tests | Python tests in `google-cloud-backend/tests` run twice: minimal Flask + pytest, then production ADK/GenAI dependencies with scripted offline models; no live provider, Supabase or GitHub calls | `python -m pytest -q google-cloud-backend/tests` (repo root) |
| Backend tests | Node tests in `web/test` (`npm run test:ci`, needs Node 24) | `cd web && npm ci && npm test` |
| Website build | `website/`: `npm ci`, type-check and Vite build into `web/site`, then checks `index.html` and the logo exist | `cd website && npm ci && npm run build` |
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
