# Home recording interactions

Reference: the user's 39.9-second Gemini recording, `Record_2026-10-19-06-24-37_b783bf344239542886fee7b48fa4b892.mp4`.

## Interaction mapping

- Chat typing/clearing (approximately 2–11 s): greeting, logo and tagline fade out when a draft contains content and return when cleared. The empty canvas keeps its size; the composer and Chat/Task controls remain mounted. Short Home drafts keep the inline pill and switch Voice to Send. Text wider than the available inline space, explicit newlines or attachments expand the composer. Large text retains space for its full placeholder. Attachments/uploads also count as composer content.
- Task and keyboard (12–24 s): while the keyboard is visible and the draft is empty, suggestions remain reachable and the large Task heading is hidden. Typing hides suggestions too; clearing restores them. Closing the keyboard restores the empty Task heading. Chat/Task changes preserve the draft and composer focus.
- Keyboard dismissal: tapping empty Home space or opening the drawer/account panel clears input focus and requests IME dismissal without clearing the draft. The existing IME/navigation-bar union still owns the Home inset; a second bottom animation or padding is not added.
- Drawer (about 26 s): retain the real saved chats, search, pinned filter, inline pins, navigation, account footer, scrim and close gesture from the merged drawer implementation.
- Account overlay (30–34 s): the avatar opens a dismissible native dialog over Home. It displays the real account name/photo/email, or Guest with Sign in. Manage account/Sign in, Usage limits, Personal memory, Notifications and Settings open existing Dua destinations. Close, Android Back and outside dismissal return to the same Home state.

The recording's Google account services and subscription badge are not Dua features. The menu uses working Dua destinations and the existing sheet palette. Dua's logo, Google Sans Home typography, purple voice action, gradients, dynamic model label, real dictation/send handlers and global theme remain in use.

## Verification

Native Compose tests exercise draft/focus retention across typing, keyboard and mode changes; Task starters still fill the real composer and send through the Agent handler. Dialog coverage includes live identity updates, guest sign-in, each destination, Settings, explicit close and Android Back. Existing drawer tests cover navigation/search/pins and scrim sizing.

Robolectric IME captures use injected platform insets; their blank keyboard area is not a keyboard rendering. Real device frame timing, animated system-keyboard synchronization, authenticated photo downloads and navigation into external Google services are not measured by these captures.

Verified on 2026-10-19:

- Full `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline` run passed: 1,366 tests across 179 classes, zero failures/errors/skips.
- Focused Home/drawer/profile coverage passed; separate composer coverage verified short inline drafts, multiline expansion, clearance, window resize, focus and existing generic-composer actions.
- Reviewed native light/dark owner/guest account panels, including actual 2× text inside the dialog, and keyboard-open typing captures. The regular-font short draft stays inline; larger text keeps a separate input row where needed.
- Debug APK DEX files contain no `com/example/geminiapp` prototype package. `git diff --check` passed.
