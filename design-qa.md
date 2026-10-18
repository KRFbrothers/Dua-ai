# Home greeting, gradient and keyboard QA

## Scope

The empty Home screen adds a sky-blue wash in Chat and a blue/violet wash in Task, with subdued equivalents in dark mode. These are decorative Home layers; shared DuaTheme/Home/Chat color tokens, conversation backgrounds, feature screens, composer colors and the purple voice button are unchanged. The gradient is cached by size/theme/mode and ends above the keyboard.

Chat keeps the original Dua calligraphy lockup and “Dua always with you” tagline. The main greeting uses the current profile's first name (“Ask away, Fareed!” for that profile), updates with profile changes, and falls back to “Ask away!” for guests or email-only profile values. Task keeps its heading, three starter prompts and existing Agent handlers. No sample account, chat or project data is added.

Home owns the union of IME and navigation-bar padding around the header/content/composer surface. Its composer applies neither inset again. Dedicated feature/project composers retain their existing inset handling. Chat/Task targets and composer actions clip touch feedback to rounded shapes; Task rows gain horizontal breathing room. Existing Home tags, offline Google Sans, icon artwork, draft, attachment, dictation, send/stop and voice handlers are retained.

The supplied Gemini-generated Compose reference informs motion and inset handling: mode content fades over 180–240 ms, the ambient wash changes over 500 ms, and hero spacing/logo width compact over 250 ms while the keyboard is open. Brushes are cached; animated opacity is read only while drawing. The logo returns from 36 dp to its existing 48 dp width on dismissal. The screenshot layout keeps the switcher above the composer, all three Task starters, and the current theme/identity. Existing app state and callbacks power all actions.

## Verification

All 1,338 default Android unit/Compose UI tests passed across 177 classes, with zero failures, errors or skips. `:app:assembleDebug` and `git diff --check` passed. The Gradle run used `-Pdua.ui-record --offline --no-daemon --max-workers=2` with local SDK/test-cache paths. This includes the 14 new greeting/gradient/IME cases and every existing default test; optional dedicated screenshot-package tests are outside this default suite. The focused Home/composer run also passed all 90 cases.

Focused coverage dispatches real Android IME insets through the Compose view, checks the exact 280 px movement of the gradient/composer, verifies tabs remain above the input, and keeps the input focused with its draft after dismissal and Chat/Task transitions. A 24 px navigation bar with a 280 px keyboard moves the composer by 256 px; repeated toggles restore its original position. Tests check logo compaction/restoration in light/dark and normal/2× text. Profile-switch/guest tests cover the greeting, screenshot pixels verify the wash is drawn, and navigation into a conversation or Agent feature removes the decorative Home layer. Existing Home/composer tests are also retained.

Production Compose previews are recorded in `app/build/approved-ui/home-gradient-*.png`. Keyboard previews reserve the IME region; Robolectric does not draw the phone's actual keyboard. Physical-device keyboard animation and real microphone/provider calls still require a device check.

## Retained original assets

- Google Fonts `google/fonts@47d54ca1ebb14fd864695f1d39dc734f39017c33`: offline Google Sans variable font at weights 400/500, integrated in the previous change. Global Inter/code typography aliases are unchanged.
- Google Material Symbols `google/material-design-icons@49d4db35df873165d6bd6ba09b063c7dafbac2f4`: existing original add, attach-file, code, public and expand-more paths, plus the previously integrated microphone/waveform. Original font/icon notices remain packaged. No additional third-party assets or dependencies are introduced here.
