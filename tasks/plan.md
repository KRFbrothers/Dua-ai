# Plan: replace Dua's Android UI from the supplied Gemini recording

The owner approved `SPEC-gemini-android-ui.md` and explicitly requested a single
replacement, with no second or archived design. This authorizes adapting the
attachment sheet described in the approved spec and removing obsolete UI forks.

## Architecture

- Keep the existing ChatRoute → ChatScreen path, ViewModel actions, theme and
  original Dua artwork. Update existing components in place.
- Remove SuppliedChatUi and its opt-in selection/style flags. Move its genuinely
  shared declarations to current owners, and migrate its interaction coverage.
- Preserve request/picker/voice ownership and accurate capability availability.
- Reuse shared chat options and rename UI rather than adding another dialog.
- Preserve existing test tags on the canonical hierarchy where needed for test
  compatibility; test identifiers are not an alternate screen implementation.

## Execution order

1. Establish build baseline, inspect current owners, and migrate shared types.
2. Replace the existing Home/header/composer and remove the archived screen path.
3. Unify message rendering and migrate archived interaction/screenshot coverage.
4. Update history/options and attachment layouts using existing actions.
5. Align voice, profile/settings and Library with existing real destinations.
6. Verify focused behavior, full supported checks, screenshot states, and the
   absence of alternate screens or old style switches; update ownership docs.

Each slice must preserve the functioning app and have relevant focused checks.
The detailed acceptance and verification list is in `tasks/todo.md`.

## Baseline and risks

- Initial Gradle wrapper execution failed before compilation because Java did
  not use the environment's HTTP proxy. The declared Gradle ZIP was fetched via
  the inherited proxy, checked against its pinned SHA-256, and unpacked outside
  the repository. A writable Gradle user home now uses the same inherited proxy.
- No Android source/dependency versions will be changed to hide baseline errors.
- The supplied recording is light mode only. Preserve usable dark tokens and
  reduced motion; do not claim reference verification of unseen states.
- Actual phone, voice, pickers and backend verification require the owner's
  device/session. Report those checks separately from local builds and renders.
