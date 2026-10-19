# Drawer reference QA

Reference: the supplied Dua AI drawer screenshot, `1001718092.jpg`.

The native Compose drawer uses a flat themed surface, cube-style header icon, transparent search action, a neutral full-width New chat row at the top, regular Google Sans navigation labels, an All/Pinned filter and a separately measured account footer. The real account name reflows below the avatar at large text sizes. Existing light/dark color objects and Home/feature styling are unchanged; no new dependencies or external assets are added.

The existing responsive modal widths (277 dp at 360 dp, 253 dp at 320 dp) are retained to keep Home's profile and voice controls in the scrim. Images and Projects remain directly reachable alongside the reference destinations. Plugins is a direct row; Aura still expands to Work. The supplied reference's sample chat titles, identity and PRO badge are not populated in the app. Real user chats/profile data remain the only content source.

Search uses the existing title/message search. All/Pinned composes with that query. Inline pin buttons call the same feature-gated handler as long-press options without opening the chat; rename and local/server deletion paths remain intact. The main body scrolls between fixed New chat and profile areas, and consumes keyboard insets. Existing drawer entry/search/list test tags remain available.

## Visual and interaction review

Native Compose captures in `app/build/approved-ui/drawer-reference-*.png` cover normal and 2x text in light/dark modes, populated/empty history, Pinned filtering and search. Reviewed the supplied reference and these rendered states. At large text, the footer name has no measured visual overflow, its settings action stays visible, and history can scroll clear of the footer. At normal text, the avatar, account name and settings icon share one row.

Existing navigation, Aura expansion, new-chat/profile callbacks, recent-chat deduplication/draft hiding, search and long-press options remain covered. New tests cover filtered real state, inline pin isolation, guest/empty states, disabled new chat and footer geometry/name reflow. The drawer avatar uses its own tag so the existing Home avatar identifier remains unique even while the drawer is offscreen. Preview fixtures are test-only; they are not app defaults.

Intentional adaptations: preserve all existing destinations and the modal scrim width; use existing Material/ReferenceIcons navigation artwork; omit an unsupported subscription label. No open P0/P1/P2 visual issues remain in the reviewed states. Real authenticated photo downloading and physical-device keyboard animation are outside Robolectric validation.

Validation: all 1,350 default Android unit/Compose UI tests passed across 178 classes on the latest main containing the Home changes, with zero failures, errors or skips. This includes all 20 Home Chat/Task cases, Home gradient/keyboard coverage and 12 new drawer cases. The initial focused drawer/history run passed 46 cases. Debug APK assembly and `git diff --check` passed. The Gradle run used `-Pdua.ui-record --offline --no-daemon --max-workers=2` with the local SDK/test cache. Optional dedicated screenshot-package tests are outside this default suite.

Final result: passed for the reviewed native states.
