# Animation impact map: audit and first implementation

Reference: the supplied `Dua Ai Animation Impact Map.md`. Audit date: 2026-10-19.

## Existing foundations

| Area | Current source evidence | Implication |
| --- | --- | --- |
| Home (initial audit) | Cached blue/purple waves, Chat/Task fades, keyboard-aware layout and draft-driven visibility | The first phase retained these. The later owner-approved ambient pass replaces the waves; see [native ambient QA](home-ambient-motion.md). |
| Chat streaming | `ChatViewModel.collectReply` applies every event to the accumulator before conflating UI snapshots, with `STREAM_FRAME_MS = 24L` | Buffering already exists. A 24 ms delay allows at most about 42 UI snapshots/sec before processing overhead; it does not promise 120 FPS or reduce server response time. |
| Motion and haptics | `DuaMotion` spring tokens, `rememberMotionAllowed`, preference-aware `ChatActions.haptic` / `LocalChatHaptic` | Reuse these paths. The initial helper cached the animator setting once; direct custom-vibrator calls in ChatScreen bypassed the existing preference-aware callback. |
| Voice | Cached waveform buffers, draw-phase level reads, a lifecycle-bound frame loop | Audio-reactive Canvas rendering is already present. This is amplitude/envelope feedback, not measured pitch tracking or viseme lip-sync. |
| Rive/Lottie | The initial audit found runtime dependencies with no app `.riv` asset/state machine or Rive/Lottie screen integration | The subsequent cleanup removes these unused dependencies. Reintroduce a runtime only with an actual integration. |
| Shaders and tiering | The initial audit found an unused `DuaMotionConfig` checking API 33 and low-RAM status; no RuntimeShader rendering call was found | The subsequent cleanup removes the unreferenced helper and factory. No device-tier measurement or shader feature is implemented by that former flag. |
| Startup | Hand-written `baseline-prof.txt` and ProfileInstaller dependency already exist | Startup profiling is not absent. Device-generated profile coverage and cold-start measurements are still needed to establish its benefit. |
| Compatibility | App minSdk 26; Material 3 Android 1.3.1 | The fallback target includes Android 8+, not only Android 10–12. This phase uses the existing Compose/Material APIs without a platform-wide library upgrade. |

## Implemented quick wins

- Composer Voice/Send/Stop glyphs fade and scale within the existing fixed touch target. The current action, label and enabled state remain authoritative throughout the visual transition. With reduced motion the glyph switches immediately.
- Model-picker letter badges animate their fill/foreground and rounded-square-to-circle shape with the existing gentle spring. The trailing selection slot stays reserved, avoiding horizontal text movement. Provider availability, free-model selection and existing haptic callbacks remain intact; no invented provider tiers are added.
- The drawer receives the actual parent `DrawerState` through the stateful Material `ModalDrawerSheet` overload, enabling the framework's predictive Back handling. The redundant outer BackHandler is removed. Layout widths, search, pins, destinations and framework gesture ownership are retained; no full-screen blur or second drag controller is introduced.
- Drawer and streaming feedback dispatch through `ChatActions.haptic`, whose real route checks the app preference and uses `View.performHapticFeedback` for system touch-feedback preferences. Streaming feedback is throttled using monotonic uptime independently of text delivery. The subsequent cleanup removes the unused standalone custom-vibration helper and factory; active callbacks and throttling remain in place.
- `rememberMotionAllowed` observes animator-scale changes and refreshes on resume, unregistering its observers on disposal. Existing custom voice frame loops can now stop when Remove animations changes without recreating the screen.

## Later phases and measurable limits

| Proposal | Required next work |
| --- | --- |
| AGSL voice orb / image shimmer / edge aura | Build API-33 rendering separately from the minSdk fallback; gate active loops by lifecycle and task state. Profile frame time, GPU load, thermals and battery on representative devices before expanding their use. |
| Rive mascot, step tracker, lip-sync | Choose actual `.riv` assets and state-machine inputs. Lip-sync requires timed phoneme/viseme data from the speech pipeline; microphone amplitude alone is insufficient. Measure runtime/assets per ABI in a release build. |
| Imagine blur/reveal and shared transitions | Connect transitions to actual image-load/generation states and navigation identity, with loading/error/cancel behavior intact. |
| Circular theme reveal | Review theme propagation, screenshot allocation, accessibility and interrupted changes before adding a window capture. |
| A2UI agent cards | Define/validate supported backend payloads, renderer capabilities and permitted actions. This is a protocol and application behavior change, not only an animation. |

The map's 40–50% perceived-latency reduction, locked 90/120 FPS, APK-size ranges, GPU percentages, <2% battery impact and “100% crash-free” statements are unmeasured estimates. None are acceptance results for this implementation. Display refresh rate, UI snapshot rate and animation-frame delivery are different measurements. Existing unit/Compose tests cannot establish physical-device frame rate, battery cost or thermal behavior.

## Verification

Targeted checks cover live animator-setting updates, streaming-feedback throttling, drawer Back cancellation/completion, one callback per drawer state change, existing model availability/selection, and composer action/focus behavior. Native Home/drawer captures retain the existing theme and responsive geometry. Device performance measurements remain pending.

Verified results on 2026-10-19:

- Full `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline` run passed: 1,376 tests across 182 classes, zero failures/errors/skips.
- Focused motion/Home/drawer/composer run passed 91 cases; the model-picker availability/selection run passed four light/dark and animations-on/off cases.
- Reviewed selected/unavailable model rows in native light/dark captures and the current dark drawer at 2× text. Existing Home, composer and drawer captures were regenerated by the default suite.
- `git diff --check` passed; debug APK contains no supplied-demo `com/example/geminiapp` package in DEX.

The later drawer/profile and unused-runtime cleanup is documented in [project cleanup QA](project-cleanup.md); the verification counts above describe the original animation implementation.

## Reply-feedback follow-up

Pending-reply feedback, the streaming cursor, Agent-step pulse
and spinner, reasoning badge/expansion and message placement now follow the live
Remove animations setting and resumed lifecycle. Reduced motion keeps static
feedback visible; the active Agent step uses a clock icon rather than a spinner.
Completed-step checks still come only from actual completed steps. Backgrounding
removes decorative infinite transitions and pauses the reasoning-label timer;
resuming recomputes elapsed time from the existing start time. These changes do
not cancel reply generation or change the streamed message/approval state.

Streamed text remains fully visible with motion disabled. The first-word fade
snaps to visible on pause or a preference change and does not replay when motion
returns. Typing/step accessibility labels, Stop/Retry handlers, Markdown and the
latest completed/stopped reply actions retain their existing behavior. The
Agent approval callback from the current main branch is preserved.

`ReplyMotionUiTest` compares native captures at separated animation-clock times:
foreground feedback changes, reduced-motion and paused feedback stays still,
and feedback resumes on the same mounted screen. Separate cases exercise first
token replacement, real step completion, visible streamed text and stopped-reply
Retry. Cases cover both themes and normal/2× text; fixtures use synthetic replies
and steps. Captures are under `app/build/approved-ui/reply-motion-*.png`.

With the test clock paused, one frame after changing streamed text was too early
to assert Markdown visibility. The four theme/text-size variants reproduced this
failure locally; the answer became visible on the next 16 ms test-clock frame.
The transition test now advances the clock until the answer/stopped-state layout
is visible, with a 1,000 ms simulated-time bound, before retaining its strict
visibility, step-state and Retry callback assertions. It does not sleep, skip the
test or remove an assertion. All four previously failing cases pass locally.

Local Gradle verification is available again through the supported permissions
path. Full-suite/build results are recorded in the PR and CI checks;
`git diff --check` passes. Device frame timing, battery effects and TalkBack still
require device verification.

## Approved ambient follow-up

The owner-approved browser preview is now adapted to native Compose: cached
drifting radial washes on Home, a subdued standard-conversation backdrop and a
rotating/pulsing thinking orb. The orb replaces both the pending dots and the
old skeleton lines; the existing status announcement and first-token replacement
remain. All decorative loops use the live motion/resumed-lifecycle guard above.
See [native ambient QA](home-ambient-motion.md) for scope and verification.
