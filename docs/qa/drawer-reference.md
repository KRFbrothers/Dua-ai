# Drawer reference QA

Current reference: the user's `1001718195.jpg`, `1001718193.jpg` and `1001718194.jpg` request: remove the top New chat row and the bottom profile/settings area; keep only a New chat icon at the bottom right.

The drawer has a fixed brand/search header, scrollable destinations and real chat history, and one fixed bottom-right New chat icon. The icon retains the existing `drawer-new-chat` tag, a Start a new chat accessibility label, a 48 dp touch target and the existing enabled state/callback. Its label is distinct from the Home header control while the drawer is offscreen. The footer has no avatar, account name, settings control, text label or divider. The removed account/footer arguments are no longer passed to HistoryDrawer. Home's avatar and account menu remain available through their existing header entry.

Responsive modal widths (277 dp at 360 dp, 253 dp at 320 dp), theme colors, Google Sans labels, predictive Back, Aura expansion, search, All/Pinned filtering, inline pins and existing destinations remain in use. Rename and local/server deletion retain their existing handlers.

## Verification

Existing drawer tests now check that the only New chat action is at the lower right, remains clear of scrollable history and respects its disabled state. They check that profile/settings/avatar UI is absent. Existing selection, search, filtering, pinning, navigation and long-press tests remain in the default suite.

Native captures in `app/build/approved-ui/drawer-reference-*.png` cover light/dark, normal/2× text, populated/empty history, filtering and search. Capture `drawer-reference-bottom-new-chat-*.png` shows the current fixed icon footer. Preview chat fixtures are test-only.

Physical-device gestures and keyboard animation are outside Robolectric validation.

Validated on 2026-10-19: all 1,376 default Android unit/Compose UI tests passed across 182 classes, with zero failures/errors/skips. Debug APK assembly and `git diff --check` passed. The run used `-Pdua.ui-record --offline`; current light/dark and 2× text captures were reviewed. Header/account access, new-chat callbacks, search/pins and predictive Back coverage remain passing.
