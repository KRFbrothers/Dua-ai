# Spec: Gemini Android interaction design for Dua AI

Status: owner-approved; in-place implementation and local validation completed.
Physical-device acceptance remains pending. The owner explicitly requested an
in-place replacement with one UI implementation, not a second or archived design.

## Objective

Adapt the existing Dua AI Android experience to the owner's Gemini Android
recording, including visual hierarchy, navigation, menus and visible interaction
states. Deliver working Dua actions rather than a static mockup. Preserve Dua AI
branding, owner authentication, storage, backend contracts and existing features.

This is one Android UI adaptation capability. Its areas share the existing
chat/session state and navigation; it does not introduce a suite of independent
Google-equivalent backend products.

## Reference and assumptions

Reference observations and limitations are recorded in
[`docs/ui/GEMINI_ANDROID_OBSERVATIONS.md`](docs/ui/GEMINI_ANDROID_OBSERVATIONS.md).
G01–G19 below refer to that evidence table. The reference is the supplied
159.858-second recording, not a generic recollection of the Gemini application.

Assumptions for review:

1. Change the existing native Android app under `apps/android`, not the website.
2. Apply the reference's layout and state patterns while retaining Dua's original
   calligraphy, name, theme system, provider configuration and account ownership.
3. Preserve existing functional behavior for states absent from the recording.
4. Reference-only Google subscriptions, connected services and feature names do
   not become pretend working Dua capabilities. Use existing Dua equivalents or
   an explicit unavailable state with the reason.
5. Do not seed reference account details, notebook names, messages or media.
6. Replace the existing live UI in place. Remove the archived supplied screen,
   its selection flag, and message-style forks after migrating behavior coverage
   to the canonical screen. Keep one owner per component and no legacy UI toggle.

## UI scope and success criteria

### Home and chat shell — G01–G05

- Header, welcome area, Chat/Task selector and bottom composer follow observed
  hierarchy and responsive proportions, with original Dua brand artwork.
- Chat and Task retain distinct welcome content and the actual existing task
  handler; changing mode alone does not submit a request.
- Composer stays reachable above the keyboard and navigation insets. Empty,
  draft, uploading, reading, sending and streaming states retain correct actions.
- Sending uses the existing ViewModel/session pipeline. Stop targets the actual
  active response and becomes available according to real request state.
- Loading decoration follows real state and respects reduced motion. Decorative
  timing does not imply completion of an unverified external service.

### Messages and conversation options — G06–G07

- Present supported message actions consistently with the observed action row.
- Copy/share/speech/retry invoke their existing handlers and correct message;
  unavailable actions are not rendered as working no-ops.
- Conversation options target the active chat. Existing pin/rename/delete guards,
  persistence, confirmations and error states remain intact.
- Preserve existing message selection, long-press and accessibility behavior
  until a changed behavior has explicit evidence and acceptance criteria.

### Drawer, history and navigation — G10–G12, G17

- Provide the observed navigation hierarchy, clear search/close entry points,
  independently scrollable content, selected states and fixed account/settings
  access, adapted to actual Dua destinations.
- More can expand/collapse secondary navigation without losing chat/draft state.
- Pinned chats reflect real persisted state. Reference status filters only become
  active filters when backed by actual Dua state; no invented task statuses.
- Back/dismiss consistently close the topmost overlay before leaving the
  underlying screen, preserving drafts and active-chat identity.

### Attachments and tools — G08, G18

- Use the captured compact and expanded sheet patterns where the Dua feature
  context genuinely calls for them; determine the context from existing state
  and document it rather than inferring the reference's hidden condition.
- Quick actions can scroll horizontally while the sheet's main content remains
  reachable at small sizes and large font scales.
- Photos, camera and files use existing Android picker/upload flows, including
  actual progress, cancellation, removal, permission and failure behavior.
- Additional tool entries open actual existing Dua functionality or disclose
  capability requirements. Preserve backend availability checks.
- This area includes the protected `AttachmentSheet.kt` layout: approval of this
  scope must explicitly cover adapting that layout before it is edited.

### Voice — G09

- Adapt the observed entry, active controls and attachment-overlay hierarchy
  using existing voice/dictation/live routes and lifecycle ownership.
- Close/back releases active resources according to existing ownership and
  restores the conversation/draft. Handle permission denial and unavailable
  capabilities honestly.
- Screenshare/video controls must reflect the actual current capability; the
  recording alone does not authorize implementing a new streaming backend.

### Profile, settings and Library — G13–G16

- Use grouped settings/profile surfaces and clear navigation, mapped to existing
  Dua account and preference owners. Existing settings persist through the same
  mechanisms, including appearance and accessibility preferences.
- Library has distinguishable loading, content, empty and failure states from
  real data; document cards and media open their actual supported destinations.
- The captured blank Media body is not an accepted final loading/error design.

## Tech stack and source structure

- Kotlin, Jetpack Compose and Material 3; Android `:app` module.
- Use pinned versions in `apps/android/gradle/libs.versions.toml`; currently
  AGP 8.8.0, Kotlin 2.4.21, Compose BOM 2026.06.00 and Gradle wrapper 8.14.4.
- Android compile SDK 35, minimum SDK 26 and Java compilation target 17.
- Production entry: `MainActivity → DuaApp → DuaNavHost → ChatRoute → ChatScreen`.
- Reuse `ui/chat/`, `ui/history/`, `ui/settings/`, `ui/voice/`, `ui/features/`
  and `ui/theme/`; keep ViewModel, account and persistence responsibilities.
- Existing unit/Compose tests are under `app/src/test/java/com/dua/ai/`.
- Update the ownership documentation as the single live implementation replaces
  the old layout. Do not retain the archived supplied screen as an alternate UI.

## Commands

From the repository root with the configured Android SDK and a supported JDK:

```sh
bash apps/android/gradlew -p apps/android :app:assembleDebug --no-daemon --max-workers=2
bash apps/android/gradlew -p apps/android :app:testDebugUnitTest -Pdua.ui-record --no-daemon --max-workers=2
bash apps/android/gradlew -p apps/android :app:lintDebug --no-daemon --max-workers=2
```

For opt-in screenshot tests, run the relevant fixture after inspecting its
current setup, for example:

```sh
bash apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'com.dua.ai.screenshots.HomeScreenshotTest' -Pdua.screenshots -Pdua.ui-record --no-daemon --max-workers=2
```

On Windows, from `apps\android`, use `gradlew.bat` with the corresponding tasks.
Existing Gradle dependencies, SDK configuration and native tooling remain
prerequisites. Recorded local results and remaining device checks are listed in `tasks/todo.md`.

## Code style

Follow existing state/action separation, theme roles, named typography and
touch-target tokens. A representative callback-based UI pattern is:

```kotlin
IconButton(
    onClick = onProfile,
    modifier = Modifier.size(Sizes.touchTarget)
        .semantics { contentDescription = "Open profile and settings" },
) {
    Icon(icon, contentDescription = null, tint = HomeTheme.colors.roundButtonIcon)
}
```

Inspect complete target files before changes. Avoid raw color and text-size
literals outside their existing theme owners; avoid broad format-only changes,
new parallel state stores or replacement backend implementations.

## Testing strategy and acceptance

- First verify the existing build/test baseline; distinguish pre-existing
  failures and environmental blockers from changes made for this scope.
- Reuse existing behavioral Compose tests for header, Chat/Task, composer,
  history, model picker, voice and settings. Add focused tests for meaningful
  changes: state-dependent actions, dismissal order, targeting the correct chat,
  and preservation of drafts/session identity.
- Compare captures of the actual production route with matching reference
  states. Use neutral fixtures, never personal content from the reference.
- Exercise narrow windows, a large font scale, keyboard insets, both Dua themes
  and reduced-motion settings. Only light reference appearance was supplied;
  dark appearance must remain usable but cannot be called reference-verified.
- Physical-device acceptance covers Back/IME, Android pickers/permissions,
  speech/microphone, real scrolling and resource cleanup. Report which checks
  were actually run and which remain blocked.
- Every visible control must have an actual action or accurate unavailable
  state, a meaningful accessibility label, and at least a 48dp target.

## Boundaries

- Always: preserve unrelated work, original Dua identity, owner lock, backend
  credentials, provider/capability checks, existing routes, data and test tags.
- Review first: owner approval of this spec and the protected attachment layout
  scope; material deviations arising from further reference evidence.
- Never: edit unrelated protected deployment/database/vendor paths, include
  reference account data in source, copy competitor branding/fonts as Dua's
  identity, fabricate passing tests, or present a placeholder as a working
  integration.

## Open reference gaps and proposed defaults

| Missing evidence | Proposed default |
| --- | --- |
| Model picker dropdown | Preserve existing configured-provider selector |
| Pin/rename/delete outcomes | Preserve existing persistence, dialogs and guards |
| Message overflow and long-press outcomes | Preserve existing supported handlers |
| Completed uploads and permission/error states | Preserve and test current Android flows |
| Filter state semantics and reference-specific destinations | Use real Dua data/capabilities; no invented status mapping |
| Dark theme, rotation, accessibility and precise animation curves | Follow Dua tokens, existing responsive/motion contracts and device checks |

Implementation and verification progress is recorded in `tasks/todo.md`.
Screenshots render the production implementation. They do not substitute for
physical-device or live-backend acceptance.
