# Android UI consistency refinement

This updates the canonical Compose UI described in [LEGACY_CHAT_SHELL.md](LEGACY_CHAT_SHELL.md).
No parallel Home, composer, navigation route or state store is introduced.

## Reference and design decisions

The supplied recording observations in
[GEMINI_ANDROID_OBSERVATIONS.md](GEMINI_ANDROID_OBSERVATIONS.md) and the local REA
Home/Task captures guide hierarchy and state transitions, rather than exact
measurements or another product's branding. Dua keeps its calligraphy, palette,
accent choices, bundled typography and real backend capabilities. Dark reference
appearance, precise animation timings and many system interactions were not
observed; Dua's existing accessibility and theme contracts govern those cases.

The design contract is one readable header, a stable composer/draft, tonal
selection feedback, grouped settings and bounded account content. Existing
48dp targets, large-font reflow and reduced-motion behavior remain mandatory.
Unsupported providers stay disabled with a setup reason; no new feature buttons
or fabricated history/account data are added.

## Implementation

1. **Theme and composer:** Home, conversation, drawer, sheet, settings and page
   token accessors resolve from the active Material scheme. Previously most
   selected a static light/dark palette, losing saved accent or dynamic scheme
   changes outside Home. Palette constants remain available for previews/tests.
   Composer semantics now describe responding, uploading, reading, listening,
   unavailable Send and ready states without replacing its input or handlers.
2. **Conversation and navigation:** model rows use a rounded tonal selection and
   expose one radio group. Message copy feedback is a polite live region and
   toolbar icons use the semantic secondary foreground. History search gets a
   Back handler after the drawer sheet: while IME is visible it does not intercept
   platform Back; after IME dismissal the next Back closes search before drawer.
   Existing filtering, browse scroll position, model callbacks and message actions
   stay with their current owners.
3. **Profile and settings:** the existing account dialog wraps short content in a
   bounded card and scrolls its body when space is limited; Close stays outside
   that body. Settings keeps Back/title/About above its scrolling groups. Shared
   dialogs, sheets and the profile window apply light/dark system-bar icon flags
   to their own Window, rather than relying on the activity's flags.

The attachment layout, authentication, API contracts, streaming, voice pipeline,
history/cloud persistence and appearance DataStore are not changed. Theme roles
also reach existing sheets; this does not add or rearrange attachment controls.

## Verification

The new `ThemeConsistencyUiTest` exercises Light/Dark/System resolution and accent
changes across all six token families, draft retention and composer state
semantics. Search Back coverage extends `DrawerSearchUiTest`; selected-model
coverage extends `ModelPickerMotionUiTest`. `SettingsPolishUiTest` checks sticky
controls, real destinations and About dismissal at 320dp and 2x font scale.
`HomeProfileUiTest` checks bounded content, real profile actions, draft preservation
and the dialog Window's bar flags. Existing interaction, persistence, permission
recovery and reduced-motion tests are retained.

From the repository root, use the existing wrapper and SDK/JDK configuration:

```sh
bash apps/android/gradlew -p apps/android :app:testDebugUnitTest -Pdua.ui-record
bash apps/android/gradlew -p apps/android :app:assembleDebug :app:lintDebug
```

Generated renders are under `apps/android/app/build/approved-ui/`, APKs under
`apps/android/app/build/outputs/apk/debug/`, and session logs/evidence under the
ignored `build/ui-consistency/` and `build/device-review/` directories. They are
not committed. Only observed command results should be reported as passed.

Observed scoped validation during implementation: phase 1 ran 58 cases, phase 2
ran 42, and phase 3 ran 53; each completed with zero failures/skips and a successful
debug build. `lintDebug` completed with no errors (110 warnings and 3 hints).
These are overlapping scoped runs, not a full-suite result. The Windows appearance
store tests use their existing in-memory storage adapter; they do not prove real
device DataStore persistence. Full-suite and live backend acceptance are separate.
After the shared modal changes, a final affected-suite run completed 118 cases
with zero failures/skips and a successful debug build.

### Full-suite palette compatibility

The first CI full-suite run reported 50 failures: 24 Home cases, 24 feature-hub
cases and two audio-sheet cases. Their shared fixtures still expected the static
seed palette, while `DuaTheme` applies the saved/default accent to the active
Material scheme. The assertions failed before those interaction checks could run.

`HomeChatTaskUiTest`, `FeatureHubUiTest` and `RemainingSheetsDabbaUiTest` now expect
accent roles from the active Material scheme. Whole-palette comparisons retain
the existing neutral-role expectations, and all navigation, input, callback and
touch-target checks remain. No tests are removed or skipped, and the production
theme behavior is retained. The affected three classes pass all 60 cases.

The subsequent Linux/JDK 17 run with the committed dependency catalog completed
all 1,768 Android unit tests with zero failures, errors or skips. The combined
`:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --no-daemon --max-workers=2`
command completed successfully. This does not establish real-device acceptance.

## Device acceptance boundary

The inspected installed app uses the permanent owner signing identity. A local
debug build uses a different identity and lower versionCode, so it must not be
installed over that app. No uninstall, downgrade, data clear or new signing key
is used. Device-after acceptance requires a compatible build of this branch from
the existing permanent signing pipeline.

Installed-build keyboard/Back observations and reference screenshots are baseline
evidence, not proof of the new code on a phone. Rotation, TalkBack speech, real
dictation, IME/caret behavior, permission dialogs, live sending, authentication and
cloud history need separate device acceptance where not actually exercised.
