# Drawer reference QA

Current reference: the user's follow-up to `1001718195.jpg`, `1001718193.jpg` and `1001718194.jpg`: keep New chat at the bottom left and restore the profile icon at the bottom right so Settings remains reachable.

The drawer has a fixed brand/search header, scrollable destinations and real chat history, and two fixed bottom actions. New chat retains the existing `drawer-new-chat` tag, a Start a new chat accessibility label, a 48 dp touch target and the existing enabled state/callback. Its label is distinct from the Home header control while the drawer is offscreen. The right profile action uses the real account name/photo through the shared AccountAvatar, with a guest fallback. Its 48 dp button closes the drawer before opening the existing Settings destination. The footer has no account-name label, subscription badge, separate settings gear or divider. Home's avatar and account menu remain available through their existing header entry.

Responsive modal widths (277 dp at 360 dp, 253 dp at 320 dp), theme colors, Google Sans labels, predictive Back, Aura expansion, search, All/Pinned filtering, inline pins and existing destinations remain in use. Rename and local/server deletion retain their existing handlers.

## Verification

Existing drawer tests check New chat on the left and profile/settings on the right, clear of scrollable history, with 48 dp touch targets and a 44 dp avatar at normal/2× text. Disabled New chat does not disable Settings. Both callbacks are exercised; integrated ChatScreen coverage verifies drawer dismissal before Settings navigation and preserves the Home account entry. Existing selection, search, filtering, pinning, navigation and long-press tests remain in the default suite.

Native captures in `app/build/approved-ui/drawer-reference-*.png` cover light/dark, normal/2× text, populated/empty history, filtering and search. Capture `drawer-reference-bottom-actions-*.png` shows the current fixed icon footer. Preview chat fixtures are test-only.

Physical-device gestures and keyboard animation are outside Robolectric validation.

Validated on 2026-10-19: all 1,371 default Android unit/Compose UI tests passed across 181 classes, with zero failures/errors/skips. Debug APK assembly passed. The run used `-Pdua.ui-record --offline`; current light/dark and 2× text captures were reviewed. Header/account access, new-chat callbacks, search/pins and predictive Back coverage remain passing.

After syncing main’s three Windows-compatibility test fixes, 55 affected Home/drawer/storage/preferences tests passed across 5 classes with zero failures/errors/skips; debug APK assembly passed again. `git diff --check` passed.
