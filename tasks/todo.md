# Android UI replacement tasks

Owner approved implementation and removal of duplicate/archived UI.

- [x] Shared ownership and production Kotlin compilation: keep HomeTab/task
  prompts in canonical owners and preserve unrelated work.
- [x] Home/header/composer: implement reference hierarchy and responsive greeting,
  state-correct composer and conversation options in the current route. Verify
  Home, draft/focus, request state and header interaction tests.
- [x] One screen/rendering path: remove supplied screen/flag/style branches;
  migrate archived tests to the canonical production route. Verify Work,
  feature/project isolation, message actions and canonical screenshot fixtures.
- [x] History/options: reference navigation/More/account layout, working search,
  shared pin/rename/delete controls with existing guards. Verify search, target
  identity, rename persistence, pin and confirmation behavior.
- [x] Attachments: reference quick-action carousel and tool rows, real picker/
  upload handlers, enabled/cancel/error states. Verify all existing attachment
  callbacks and busy-state semantics.
- [x] Voice/settings/Library: align existing surfaces, preserve lifecycle and
  persistence, real loading/content/empty/error states. Verify existing relevant
  navigation, settings and voice tests.
- [x] Final local verification: run supported unit/UI tests, debug build and lint;
  inspect production-route renders; check no alternate supplied screen or style
  switch remains; update ownership/spec status and report device-only blockers.

## Original implementation validation (before latest main integration)

- Full Android unit/Compose suite, including opt-in screenshot cases:
  **1,884 tests; zero failures, errors or skips**. Command:
  `:app:testDebugUnitTest -Pdua.screenshots -Pdua.ui-record`.
- After the final voice photo-preparation/error notice and busy guards, the
  voice packages passed **114 tests; zero failures, errors or skips**.
- Latest `:app:assembleDebug` and `:app:lintDebug` completed successfully in the
  sequential final run. Lint reports zero errors, 68 warnings and 3 informational
  items; warnings were not globally suppressed or baselined.
- Inspected native production renders of Home, Task, Settings and Voice.
  Existing Compose cases also cover both themes, narrow/large-text layouts,
  keyboard insets, draft preservation and reduced motion.
- Verified no production `SuppliedChatUi`, `ComposerGlass`, `useSuppliedChatUi`
  or `suppliedStyle` remains. Compatibility test tags live on the single shell.
- Review mutation: deliberately inverting compact Tools disclosure made the
  relevant test fail. Restored the exact source and reran both attachment-context
  tests successfully; no mutation remains in source or the delivered APK.
- Shared header/drawer rename tests edit and persist the targeted chat, with
  the draft preserved and the originating popup dismissed first.
- Build tools, SDK, proxy and writable caches were configured outside source;
  dependency versions and protected CI/deploy/database paths were not changed.

## Integrated main validation

- Integrated main through `e701db2`, retaining its cleanup changes, scoped Home
  palette and Agent boundary handling. Kept main's canonical wiring/capture test
  filenames; the obsolete HTML previews remain removed.
- The integrated opt-in run executed **1,893 cases: 1,889 passed, 4 failed**.
  Three failures were bounded account/socket timeouts; both unchanged account
  classes passed their subsequent 31-case serial rerun.
- Adapted the conversation Agent test to use the Tools entry, preserve original
  history and exercise Back through its accessible action after modal dismissal.
  The final combined Agent/header/drawer/account rerun passed **37 tests**, with
  zero failures, errors or skips. Physical touch dispatch is still device-only
  acceptance; the accessible action test does not establish it.
- Review the latest [PR #483 checks](https://github.com/KRFbrothers/Dua-ai/pull/483)
  for full CI results. The first integrated CI attempt exposed the same Agent
  navigation test failure; the corrected test is included in the follow-up.

## Remaining acceptance and delivery

- Physical Android checks remain unrun: Back/IME, actual pickers and permissions,
  microphone/live voice, live backend and resource cleanup on the owner's phone.
  Local renders and Robolectric tests do not replace these checks.
- The owner authorized publishing this implementation as a pull request to the
  existing repository. Local validation is complete; PR CI results and device
  acceptance must be assessed separately before merging.
