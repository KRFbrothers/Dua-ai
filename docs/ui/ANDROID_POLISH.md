# Android polish: Home, composer and history

Polish the existing production components in place. Reference evidence is G01–G05
and G10–G12 in [the owner's Android recording observations](GEMINI_ANDROID_OBSERVATIONS.md).
The original Dua artwork, shared palette, account ownership and one-screen
architecture remain the design contract.

## Finish criteria

- Home greeting: balance wrapped heading lines so short final words do not sit
  alone. Keep the existing type scale, font scaling and scrollable compact hero.
- Task starters: use the existing soft surface and rounded-row tokens, with
  consistent spacing and touch feedback. On compact/large-text screens, greeting
  and starter rows scroll together so the fixed starter area cannot clip the
  greeting. Selecting a starter still fills the real draft; nothing submits
  until Send.
- Composer: visually distinguish disabled controls using Material's themed
  disabled colors. Reading files must disable voice entry as well as Send,
  Dictate and Add. Stop and Cancel upload remain actionable in their real states.
  Preserve the draft, field identity, IME behavior and 48 dp targets.
- History: show a useful explanation for empty, pinned-empty and no-match
  states. Offer Clear search or Show all chats where those actions recover the
  list. Keep real chats, filters and the fixed account/settings footer; report
  selection semantics instead of relying on the selected-row color alone.

Check light/dark appearances, 320 dp windows and 2x text using the current
behavioral/screenshot fixtures. Inspect before/after native renders. Add tests
only for changed interactions and state guards, not copies of visual constants.
Do not add a parallel screen, change the protected attachment sheet, introduce
new provider/network behavior or seed reference account data.

## Verification

Local validation completed with `:app:testDebugUnitTest :app:assembleDebug
:app:lintDebug -Pdua.ui-record`: 1,744 tests passed with no failures or skips,
and the debug APK and lint tasks completed. New behavior cases failed before
their fixes; inverting the file-reading guard was also rejected in all four
light/dark/large-text cases before restoring the source.

Native light/dark, compact and 2x-text captures were inspected. Physical-device
IME, microphone, picker and TalkBack acceptance remain separate from native
Robolectric rendering and semantic/callback checks.
