# Flagship capability verification

The five capability implementations are merged together on `main`. This suite
uses the real parsers, Compose components, DataStore, ViewModel request path and
voice cancellation classes. HTTP is intercepted before network access; audio
playback uses a fake sink. Tests do not require provider keys or a user account.

| Capability | Focused tests | What they verify |
| --- | --- | --- |
| Thinking step tree | `ThinkingTreeTest` | Explicit summary headings/details, streamed new steps, collapse when answering starts, manual reopen, Agent summaries remaining opt-in; no invented steps. |
| Artifact detection / Canvas | `MessageArtifactsTest`, `ArtifactCanvasTest` | Supported/unsupported and incomplete fences, completed-stream detection using final source, Preview/Code, exact copy payload, renderer disposal, system back and large text. |
| Rich citation cards | `ReplySourcesTest`, `RichSourceCardsTest` | Parsed/structured sources, two-line titles, scroll, unchanged accessibility labels, safe URL opening, new citation data replacing the old initial/target, light/dark and large text. |
| Personality persistence / prompting | `AppearancePreferencesTest`, `PersonalitySettingsTest`, `PersonalityPromptingTest`, `DuaApiClientEngineTest` | All presets, restart/reset and invalid IDs, accessible selection, real ViewModel send/retry HTTP payloads for both engine routes, selected-current-preset behavior, disabled memory retained and unsupported backends leaving the draft untouched. |
| Voice barge-in | `BargeInDetectorTest`, `VoiceBargeInIntegrationTest`, `VoiceModeSessionTest`, `VoiceExperienceTest` | Noise/onset gating, real ReplyVoice cancellation releasing/wiping generated audio before listening, exactly-once interruption, stale completions not changing a newer reply, mute/pause/close guards and accessible tap interruption. |

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

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and XML under
`app/build/test-results/testDebugUnitTest/`. CI runs the full task with
`-Pdua.ui-record` to preserve UI captures. Separate opt-in snapshot-recording
classes under `screenshots/` retain the repository's existing `dua.screenshots`
configuration; no standard tests are excluded or weakened by this change.

These automated checks do not certify physical microphone echo cancellation,
Bluetooth routing, interruption latency, 60fps, PSS below 180MB or full Chromium
rendering. Use [voice device checks](VOICE_RELIABILITY.md),
[Canvas checks](ARTIFACTS_CANVAS.md) and
[personality rollout/phone checks](PERSONALITY_PRESETS.md) for those results.
