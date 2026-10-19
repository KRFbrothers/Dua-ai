# Home input and responsive layout polish

The approved Home remains a centered Dua logo/personalized greeting, blue Chat
or violet Task background, Chat/Task controls above the composer, and the real
menu/model/profile/voice actions. No new quick-start chips or attachment-sheet
layout changes are introduced.

## Behavior

- With the keyboard open, the Home-style composer uses 16 dp side margins and
  an 8 dp vertical inset for its inline layout. A regular 393 dp phone changes
  from a 329 dp-wide, 80 dp-high idle capsule to a 361 dp-wide, 64 dp-high input.
  Large text gets a separate input row when needed to fit the placeholder;
  multiline drafts retain their separate input row. Closing the
  keyboard restores the idle margins; the actual text-field node, draft and
  focus remain mounted.
- Focus gives the composer a purple outline using the existing voice accent.
  Placeholder and unselected mode-label neutrals have stronger contrast, while
  the logo, font family, gradient colors and purple voice action remain in use.
- Mode selection colors transition in place without changing touch targets or
  tab semantics. Home fades, hero spacing/logo changes, the ambient mode blend
  and composer resizing respect the live Remove animations preference.
- Empty Home content and standalone Home-style composers use the existing
  696 dp maximum column width. Tablet task rows stay centered rather than
  stretching to the screen edges; draft wrapping uses the actual bounded card
  width. Feature composers keep their existing sizing profile.

The Home surface still owns the IME/navigation-bar union, so these changes do
not add another bottom inset. Generic feature layout, existing mode/send/stop
guards, drawer/model/profile behavior and the locked + attachment sheet keep
their existing handlers.

## Verification

Existing keyboard tests check wider/shorter geometry, the same input-node
identity and draft/focus retention, restoration after closing the keyboard, and
non-accumulating IME/navigation insets. New wide-window cases cover centered
hero/task rows with Remove animations enabled, bounded composer width, multiline
wrapping and focus/draft retention when narrowing the window. The existing suite
also exercises attachment controls, Chat/Task handlers and unavailable Task mode.

Native captures are recorded under `app/build/approved-ui/`, including
`home-gradient-keyboard-*`, `home-gradient-wide-chat-*`,
`home-gradient-wide-task-*` and `home-polish-wide-draft-*`.

Robolectric injects platform insets; the blank area in a keyboard capture is not
a rendered keyboard. Physical-device IME animation synchronization, frame rate,
battery impact and authenticated photo loading are not established by these
tests or screenshots.

Validated on 2026-10-19: `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline` passed 1,385 tests across 182 classes with zero failures/errors/skips. Native light/dark phone, 2× text and wide-window captures were reviewed. `git diff --check` passed, and the attachment-sheet/shared sheet files have no changes. Device keyboard timing remains unverified.
