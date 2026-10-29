# Android Home keyboard reference refinement

The installed reference inspected on 29 October 2026 hides its Task greeting while
the empty composer has the keyboard open, leaving three task starters available.
Dua's installed build retained the compact Task greeting and exposed only the
first starter in the captured viewport. The existing `ChatContent` now omits the
Task hero while IME insets are nonzero and lets the starter area use the remaining
height, scrolling when necessary. Closing the keyboard restores the hero. Empty
Chat retains its greeting; a nonblank draft still hides the whole Home body.

The comparison also confirmed that Dua already wraps a long draft above its
action row, changes Voice mode to Send, and retains the unsent draft after Back
hides the keyboard. No additional defect in `ComposerFields` was established, so
its stable field slot, six-line limit and action-row measurement are retained.
The reference's exact multiline cap and internal scrolling threshold were not
measured; this change makes no claim of exact visual parity.

## Ownership

- `ChatContent` only reads IME insets; `ChatScreen` continues to apply the single
  union of keyboard/navigation bottom insets around Home.
- `ChatUiState.draft` and the existing controlled `Composer` remain the draft
  owners. No additional screen, composer, draft store or Back handler is added.
- Dua calligraphy, theme tokens, prompt copy, attachment layout, request routes,
  owner authentication and cloud data code are unchanged.

## Verification scope

`HomeKeyboardReferenceUiTest` covers Task/Chat and simulated open/closed keyboard
viewports, starter reachability, retained focus/draft, multiline height bounds,
action clearance, disabled IME submission, deferred-send retention and the
Voice/Send transition in light/dark themes at normal and 2x font scale.
Its keyboard insets are synthetic: it does not exercise Android's real Back
dispatcher, IME animation, speech recognition or process recreation.

Existing `DynamicComposerUiTest`, `HomeComposerPolishUiTest`,
`ComposerRecoveryUiTest`, `GeminiHomeReferenceUiTest` and
`HomeWelcomeTransitionUiTest` provide related composer/Home regression coverage.
Only executed checks and their observed results should be reported as passing.

For this change, the five existing classes above plus `HomeKeyboardReferenceUiTest` produced
80 targeted test cases with zero failures/skips across two runs; `assembleDebug`
also completed successfully. The entire Android unit suite was not run.

Local REA captures and logs are under the ignored `build/device-review/` directory:
`android-before-home`, `android-before-task-ime`, `android-before-long-draft` and
`android-before-long-back`. Reference captures are under `build/gemini-review/`.
Robolectric renders are generated under `apps/android/app/build/approved-ui/`.
These artifacts are local evidence, not committed assets.

No new APK was installed. Updated real-device keyboard/Back behavior, real voice
dictation, backend sending, authentication and cloud history were not exercised
by this change. The last device capture during cleanup showed another foreground
application; inputs were stopped, so cleanup of the temporary unsent Dua draft
was not verified. `android-baseline-restored` is a capture label, not evidence that
Dua was restored.
