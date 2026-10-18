# Home source integration and visual QA

Date: 2026-10-18

## Source and scope

The primary inputs are the owner's supplied Gemini and Google App archives. Inspection used ZIP readers, Android SDK aapt2/apkanalyzer and DEX table readers; no APK was installed or executed. The implementation remains native Dua Compose, adapted to Dua's state, accessibility and existing handlers. It does not copy Google's obfuscated application classes or claim to recover original Kotlin sources.

- Gemini: `com.google.android.apps.bard`, version `1.0.995038376` (345), 35 APKs. Its entry activity launches `com.google.android.googlequicksearchbox`. Its Google Sans Text resource is a downloadable font declaration, with no font binaries in this bundle.
- Google App: `com.google.android.googlequicksearchbox`, version `17.64.24.sa.arm64` (301817511), seven APKs. The eight supplied archive parts were joined and extracted successfully. The inner ZIP SHA-256 is `00f6d3e3fe7a15150a5ebc9b8e11dae33fe749db49836909089d958b18e30c9d`.
- Google App `font/google_sans_local` maps to `base.apk/res/Yfl.ttf` (Google Sans Regular); `font/google_sans_medium_local` maps to `res/PSY.ttf` (Google Sans Medium). Both are bundled unchanged for offline Home typography. They are proprietary Google fonts, separate from the repository's MIT license; provenance and embedded notices are retained in `app/src/main/assets/licenses/GoogleSans-NOTICE.txt`. These are Google Sans local faces, not a claim to have extracted Google Sans Text or Flex provider fonts.
- Google App `assistant_robin_input_oneline` (`res/lFZ.xml`) explicitly sets the input to 18 sp. `assistant_robin_chat_input_max_width` is 696 dp, and input button touch slots are 48 dp. These informed the corresponding Dua tokens.
- The microphone vector is the exact `ls_mic_wght320_vd_theme_28` path from Gemini `res/M_.xml`, also confirmed in Google App `res/DrJ.xml`. The existing Dua Live waveform already matches the supplied reference path.
- The provided menu and temporary-chat resource variants are three bars and a rectangular dashed bubble respectively. They differ from the requested Home icons, so the existing two-line menu and dashed-circle pen remain.

The remaining spacing, responsive behavior and 80 dp capsule are native Dua adaptations. Existing light/dark Home and Chat color objects, purple voice color, global typography preferences, feature/project composer fonts and Dua logo vector paths remain unchanged. The original Dua lockup is scaled as one asset to 48 dp; its tagline is retained. Chat's extra greeting and starter carousel are removed.

## Capture and comparison

- User's Gemini screenshot: `1001132322.jpg`, 581 × 1280; previous Dua screenshot: `1001132325.jpg`. Screenshots validate the integration; they are not represented as extracted source.
- Reference app-owned crop: `(0, 64, 581, 1244)`, excluding phone status/navigation chrome, resized to 393 × 798.
- Production Compose capture: `app/build/approved-ui/gemini-home-false-false.png`, the tagged 393 × 798 mdpi app viewport at font scale 1. The root device viewport is 393 × 866 and is deliberately excluded from the comparison.
- Additional production captures cover dark mode, 320 × 568 at font scale 2, Task, dynamic composer states and the open drawer. The compact Task screenshot is taken after scrolling to every starter; its partially scrolled heading is expected scroll position, not permanently clipped content.
- Full-view comparison: `/tmp/dua-home-full-comparison-final.png`; focused header/composer comparison: `/tmp/dua-home-controls-comparison-final.png`; dark/large text comparison: `/tmp/dua-home-dark-font2-review-final.png`; Task/drawer comparison: `/tmp/dua-home-task-drawer-review-final.png`.
- CI uploads the production captures under the Android unit test report artifact. The temporary comparison images are local review artifacts, not production assets.

## Reviewed surfaces

- Typography: actual offline Google Sans Regular/Medium on Home; shared 19 sp header/model baseline, 32/40 hero (28/36 in short viewports), 18 sp input and 14 sp tabs. Other screens retain their existing font settings.
- Composition: compact centered original Dua logo and tagline, quiet Chat canvas, Task heading with the existing three real task starters, tabs 12 dp above the input. Responsive 32/16 dp margins keep all input actions inside the capsule.
- Palette and assets: existing Home/Chat theme tokens and purple voice button are unchanged. Intentional differences from Gemini include Dua branding/copy, original white/dark backgrounds and purple voice treatment. No personal profile photo or fake account/model data is committed.
- Accessibility: 48 dp action targets, full targets within the composer and outside the open drawer, readable wrapped large text, scrollable short Task canvas. The large-font header retains the existing accessible two-row fallback.
- Behavior: model/profile/new-chat callbacks and existing tags remain. Task starters fill the draft and send through agent mode. Existing attachment, dictation, live voice, send/stop, feature isolation and project composer paths are retained.

## Findings and iterations

1. Wider Home margins initially put 9 px of the right-hand voice target behind the open drawer at 360 dp. The drawer now uses the same responsive composer inset: 277 dp wide at 360 dp and 253 dp at 320 dp; its maximum token remains 286 dp. The existing drawer touch test and final capture verify the fix.
2. The initial captured root included device chrome and mismatched the source viewport. The new reference test captures the tagged app viewport; both comparison images now use equal dimensions.
3. Independent scroll areas on a short, 2× font Task canvas could clip the heading and first starter. Short Task viewports now use one scroll area; tests scroll to each starter and submit through the existing agent callback.
4. A draft used estimated input sizing before the complete Google App arrived. The final integration uses the decoded 18 sp value and bundled Google Sans faces, plus the extracted 696 dp maximum input width.

## Verification

Final visual review: passed for the scoped source integration and intentional Dua adaptations. Full and focused comparisons plus light/dark/large-font/Task/drawer captures were inspected after the actual font integration. No unresolved clipping or overlapping action targets were found.

Final local verification: `:app:testDebugUnitTest` passed 1,316 tests with zero failures, errors or skips, and `:app:assembleDebug` passed. The run used `-Pdua.ui-record --no-daemon --max-workers=2`; the workspace init script only supplies local SDK/runtime cache locations. Existing tests are preserved, with expected logo/drawer geometry updated. New parameterized tests cover real Chat/Task/send/stop/voice callbacks, responsive touch containment and Home-only offline font selection. No authenticated network, real microphone, physical-device rendering or Google profile-photo integration is claimed by Robolectric testing.
