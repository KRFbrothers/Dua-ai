# Home OG polish and visual QA

## Scope

Home keeps the Dua logo, tagline, existing light/dark token objects, purple voice button, header test tags and Chat/Task callbacks. Feature and Project composer styling stays separate.

- Replace the two older extracted Home font faces with the unchanged current Google Fonts Google Sans variable font. Normal/medium use its wght 400/500 axes; the existing global Inter and code typography aliases stay intact.
- Integrate five original Google Material Symbols vector paths: add, attach file, code, public and expand more. Explicit Dua theme tints replace the source XML Android theme attribute. Original path data and 960×960 viewports stay intact.
- Measure Home's actual placeholder with its active font and accessibility scale. Use the existing two-row card when the inline space is too narrow, while regular text retains the capsule. Input and action layout slots stay stable across reflow, typing and window resize.
- Preserve 48 dp touch targets and the existing attachment, dictation, live voice, send/stop and Task handlers. Retain the existing microphone and waveform artwork.

## Sources

- Google Fonts: `google/fonts@47d54ca1ebb14fd864695f1d39dc734f39017c33`, `ofl/googlesans/GoogleSans[GRAD,opsz,wght].ttf`; copied from the OG collection with exact original bytes. Original OFL/trademark files are packaged in the app assets.
- Google Material Symbols: `google/material-design-icons@49d4db35df873165d6bd6ba09b063c7dafbac2f4`, outlined 24 px Android vectors. Original Apache 2.0 license is packaged in the app assets.

## Verification

All 1,324 default Android unit/Compose UI tests passed with zero failures, errors or skips, and `:app:assembleDebug` passed. The run used `-Pdua.ui-record --offline --no-daemon --max-workers=2` with local SDK/test-cache paths. The focused 28 Home cases also passed. Home-specific tests cover readable Chat/Task placeholders, distinct contained touch targets, focus/draft retention through narrow/wide window changes, and existing header/hero behavior. Production Compose captures are written to `app/build/approved-ui/` for light/dark and normal/large text review. Native device rendering and actual microphone/provider calls are outside Robolectric coverage.

Visual review passed for the production Home captures in light/dark and normal/2× text. The prior large-text inline placeholder was cut to “Ask Du…”; the new two-row Home composer shows the full placeholder with separate controls. Original font bytes and all five source vector paths/viewports were verified. The unchanged variable font is 4,974,940 bytes (2,180,075 compressed bytes in the debug APK), replacing 263,368 uncompressed bytes of older faces. No font download is needed.
