# Home wave and drawer-mark QA

Visual reference: the updated user-supplied `Dua AI Home & Composer (Jetpack Compose).kt (1).txt`, particularly `DuaSilkyWaveBackground` and `DuaIsometricCubeLogo`, alongside the supplied Home/Task/drawer screenshots.

The empty Home background now uses curved lower-screen wave geometry and a faint crest over the existing blue/purple mode washes. Its upper half stays clear of the wash. Chat and Task end-state paths and brushes are cached by size/theme, with animated opacity read during drawing. A stable-size mode transition does not recreate Path/Brush objects each frame. The existing keyboard inset owner still bounds the decorative surface above the IME, and conversations/dedicated features retain their own backgrounds.

The drawer header uses the supplied isometric-cube geometry, replacing the Material scanner-style cube. Its paths/stroke are cached and its tint uses existing DrawerTheme colors in light/dark. Header dimensions and semantics are retained.

The app continues using its real Dua flame/divider asset, offline Google Sans, purple voice button, active-model/profile state, real chats and existing voice/chat/navigation callbacks. The reference's demo ViewModel, sample pinned conversations/attachments, fixed name/PRO badge, custom replacement flame, four-bar voice icon and pale voice-button colors are not app defaults. No global palette, backend, auth or dependencies are changed.

The complete supplied prototype compiled in a temporary source probe against the current project dependencies. Production builds use the existing app entry point and state, without the temporary prototype package.

## Verification

Existing native Home/Task/keyboard and drawer captures in `app/build/approved-ui/` are used for comparison with the supplied references. Physical-device animation smoothness and frame/memory metrics require a device measurement; no numeric performance claim is made from Robolectric captures.

Verified on 2026-10-19:

- `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline`: passed, 1,350 tests across 178 classes; zero failures, errors or skips.
- Reviewed native light Chat/Task, dark Chat, dark keyboard-open at 2× font scale, and light drawer captures. The lower waves remain behind content, the composer remains above the simulated IME, and the header cube fits its existing bounds.
- The keyboard capture uses injected platform IME insets; its blank lower area represents the keyboard region, not a rendered keyboard app.
- Debug APK DEX files contain no `com/example/geminiapp` prototype package.
- `git diff --check`: passed.
