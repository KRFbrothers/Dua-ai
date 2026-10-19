# Drawer reference QA

Current reference: the user's follow-up to `1001718195.jpg`, `1001718193.jpg` and `1001718194.jpg`: keep New chat at the bottom left and restore the profile icon at the bottom right so Settings remains reachable.

The drawer has a fixed brand/search header, scrollable destinations and real chat history, and two fixed bottom actions. New chat retains the existing `drawer-new-chat` tag, a Start a new chat accessibility label, a 48 dp touch target and the existing enabled state/callback. Its label is distinct from the Home header control while the drawer is offscreen. The right profile action uses the real account name/photo through the shared AccountAvatar, with a guest fallback. Its 48 dp button closes the drawer before opening the existing Settings destination. The footer has no account-name label, subscription badge, separate settings gear or divider. Home's avatar and account menu remain available through their existing header entry.

Responsive modal widths (277 dp at 360 dp, 253 dp at 320 dp), theme colors, Google Sans labels, predictive Back, Aura expansion, search, All/Pinned filtering, inline pins and existing destinations remain in use. Rename and local/server deletion retain their existing handlers.

## Verification

Existing drawer tests check New chat on the left and profile/settings on the right, clear of scrollable history, with 48 dp touch targets and a 44 dp avatar at normal/2× text. Disabled New chat does not disable Settings. Both callbacks are exercised; integrated ChatScreen coverage verifies drawer dismissal before Settings navigation and preserves the Home account entry. Existing selection, search, filtering, pinning, navigation and long-press tests remain in the default suite.

Native captures in `app/build/approved-ui/drawer-reference-*.png` cover light/dark, normal/2× text, populated/empty history, filtering and search. Capture `drawer-reference-bottom-actions-*.png` shows the current fixed icon footer. Preview chat fixtures are test-only.

Physical-device gestures and keyboard animation are outside Robolectric validation.

## Search and keyboard polish

Search temporarily replaces the destination rows with the existing All/Pinned
controls and matching chat titles. Results start immediately below the field,
so the navigation menu cannot push them behind the keyboard. Browsing and search
have separate scroll positions: changing a query/filter starts results at the
top, and closing search restores the browse position and any expanded Aura.

Opening search in an open drawer focuses the field. Clear chat search empties
the query and returns focus to the input; the keyboard Search action dismisses
input focus while retaining results. Closing search clears the query and hides
the keyboard. Input uses the existing 200 UTF-16-unit query limit. Search/Aura
transitions and changing chat titles respect the live Remove animations setting.
The normal drawer layout and fixed New chat/profile footer retain their existing
placement, colors, real account state and handlers.

`DrawerSearchUiTest` covers immediate selected-chat results above an injected
IME, clear/no-match/Search-key actions, restored browse position with 30 chats,
query changes from the end of a list, Aura access and reduced motion. It runs
in light/dark themes at normal and 2× text. Native captures use
`app/build/approved-ui/drawer-search-*.png`; the injected IME area is blank,
not a rendered keyboard. Real-device IME timing and TalkBack still need a phone.

Validated search polish on 2026-10-19: `:app:testDebugUnitTest :app:assembleDebug
-Pdua.ui-record --offline` passed 1,393 tests across 183 classes with zero
failures/errors/skips. Native light/dark, normal/2× text search captures were
reviewed. `git diff --check` passed. No attachment-sheet, Home/composer, backend,
account-storage or signing files changed.

Validated on 2026-10-19: all 1,371 default Android unit/Compose UI tests passed across 181 classes, with zero failures/errors/skips. Debug APK assembly passed. The run used `-Pdua.ui-record --offline`; current light/dark and 2× text captures were reviewed. Header/account access, new-chat callbacks, search/pins and predictive Back coverage remain passing.

After syncing main’s three Windows-compatibility test fixes, 55 affected Home/drawer/storage/preferences tests passed across 5 classes with zero failures/errors/skips; debug APK assembly passed again. `git diff --check` passed.
