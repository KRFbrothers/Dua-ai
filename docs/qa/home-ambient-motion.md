# Approved native Home ambient motion

Fareed approved the `index (1).html` direction after reviewing an interactive Dua
Home adaptation. Source: [chat ambient reference](../ui/previews/chat-ambient-motion.html).
The separate [app-wide theme](../ui/previews/app-wide-theme.html) remains a reference.

## Scope and behavior

- `HomeBackdrop.kt` replaces the silky waves with four cached radial blobs,
  drifting over 26 seconds and breathing over 11 seconds. The blue Chat wash
  and violet Task wash retain their approved mode distinction. Standard
  conversations use the preview's quieter intensity/wash; Work and feature
  surfaces keep their existing backgrounds.
- `ThinkingOrb.kt` replaces pending-reply dots with the approved 28 dp glowing
  sweep/pulse/sparkle. The redundant skeleton lines are removed. The existing
  “Dua is thinking” live-region announcement remains the accessible status;
  decorative canvas details are hidden from accessibility.
- `AmbientTokens.kt` keeps shared palettes, dimensions and timings together.
  Brushes, arrays and paths are cached per size/theme. Animated state is read
  in the draw phase rather than rebuilding Home, messages or composer each frame.
  Backdrop drawing is clipped to its own bounds, including the IME-adjusted surface.
- Infinite transitions exist only while the existing motion preference permits
  them and the owner lifecycle is resumed. Otherwise the same feedback renders
  as a static frame. This does not cancel generation, microphone work or uploads.
- The approved Dua logo/font/purple voice control, real account/model state,
  bottom Chat/Task, native inset ownership, draft/focus, Stop/Retry/approval and
  attachment handlers remain in their existing components. No browser sample
  names, header simplifications, fake generation or preview notices enter the app.
- No new runtime library, shader API gate, downloaded asset or backend change.

## Verification

`AmbientMotionUiTest` checks backdrop and orb independently in native pixel
captures at separated clock times: active motion changes, live reduced motion
and paused lifecycle stay static, and resume animates without recreating content.
Both appearances and normal/2× text are covered.

`HomeGradientUiTest` keeps the existing real-profile, keyboard/inset, same-field
identity and draft/focus checks. A real Task-tab tap with motion disabled changes
pixels at the unobstructed backdrop edge; text/layout changes cannot satisfy that
color assertion. This uses normal UI-idle rendering, separately from the paused
clock used to compare animation frames. It verifies that standard conversations receive
the subdued decoration and Agent features do not. `ReplyMotionUiTest` retains
first-token Markdown visibility, actual step status, stopped-reply Retry and
reduced-motion feedback checks.

Native captures are recorded under `app/build/approved-ui/`:
`ambient-*`, `home-gradient-*` and `reply-motion-*`. The existing Home, composer,
drawer and attachment suites also run in the full standard Android check.

The initial ambient integration was validated locally on 2026-10-19: the focused motion/Home/reply run passed all 36
cases; the full `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline`
run passed 1,429 tests across 189 classes with zero failures/errors/skips and
built the debug APK. `git diff --check` passed. Reviewed native light/dark Home,
violet Task, conversation, orb and 2× text/keyboard captures. Shared composer
and attachment-sheet files have no changes. CI results are recorded in the PR.

Pixel captures do
not establish physical keyboard synchronization, FPS, battery/GPU cost, TalkBack
speech behavior or real provider/login success. Verify these on the owner's
signed APK; this UI-only change does not require a server deployment.

## Chat feedback polish

The first installed dark-mode screenshots exposed a square edge around the orb:
the 47.6 dp halo was clipped to the 28 dp glyph box while still visibly colored
at that box's edges. The orb now lets its radial alpha reach transparency inside
the existing 48 dp status row and text gap. Glyph size/position, row layout,
status semantics, cached drawing and motion/lifecycle guards remain unchanged.
The full-screen backdrop retains its own clipping and inset ownership.

The Copy button reserves the width of “Copied” using the actual button font and
text scale. Its temporary confirmation no longer pushes Retry/Share/Listen to
another row in narrow, large-text layouts. The measurement-only label is neither
drawn nor exposed to accessibility. Clipboard payloads, two-second feedback,
Android sharing, retry mode, speech callbacks and enabled states are unchanged.

Before the fix, both theme variants of `ThinkingOrbHaloUiTest` reproduced the
clipped halo, and `MessageActionsInteractionTest` reproduced Retry moving after
Copy at 2× text in a 240 dp column. The same assertions remain: halo pixels must
extend beyond the glyph and fade out before the capture edge, layout stays
28 × 28 dp, and the other actions keep their positions. Narrow-column checks
also cover Share's full 48 dp target. Existing native motion/reply tests continue
to cover reduced motion, background/resume, streaming replacement and Retry.

Follow-up validation on 2026-10-19: 20 focused cases passed; the complete Android
suite and debug APK assembly passed with 1,432 tests across 190 classes and zero
failures/errors/skips. Native light/dark halo and 2× text Copy/Copied captures
were reviewed. `git diff --check` passed. No device frame-time or battery result
is claimed; the owner can verify this follow-up with the next signed APK.
