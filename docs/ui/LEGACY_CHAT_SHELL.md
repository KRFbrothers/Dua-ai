# Android chat shell ownership

The owner approved the supplied recording spec and requested one in-place UI
replacement. This document keeps its original path so existing links remain valid.
The current implementation has one `ChatScreen`, composer and message-rendering
path. The archived supplied screen and its opt-in/style flags have been removed.

```text
DuaNavHost → ChatRoute → ChatScreen
                       → ChatTopBar + ChatContent (empty Chat / Task)
                       → MessageList → UserMessage / AssistantMessage
                       → Composer
                       → existing feature / ProjectChat header and state
```

| Component | Single owner and behavior |
| --- | --- |
| Home | `ChatContent.kt`: original Dua calligraphy, account-based greeting, distinct Task welcome and real starter handlers. |
| Home modes | `HomeTab` in `ChatUiState.kt`; task prompt IDs in `ChatContent.kt`; `HomeModeSwitcher` in `ChatComposer.kt`, on empty Home only. |
| Header | `ChatTopBar.kt`: history, configured model, new chat, account or active-chat options. |
| Chat options | `HistoryDrawer.kt`: shared `ChatOptionsButton` and `RenameChatDialog`; existing pin/rename actions and ChatScreen deletion confirmations. |
| Composer | `ChatComposer.kt`: stable input/focus, attachment progress, dictation, send/cancel/stop and keyboard insets. |
| Messages | `MessageList.kt`, `AssistantMessage.kt`, `MessageActions.kt`: canonical markdown, progress and actual retry/share/copy/more/speech actions. |
| Attachments | `AttachmentSheet.kt`: one renderer and picker callbacks. Chat Home expands tools; Task, conversation and feature contexts start compact. Tools remain discoverable when applicable. File limits expand on request. |
| History | `HistoryDrawer.kt`: top New chat, primary destinations, expandable More, real pinned/all filters, search and fixed account/settings footer. |
| Profile/settings | Existing `HomeProfileDialog.kt` and `SettingsScreen.kt`; real account fields and existing preference destinations. |
| Library | `LibraryScreen.kt`: existing inventory as documents and three-column media; explicit loading, empty, search and failure/retry states. Uses the shared media loader. |
| Voice | Existing `VoiceModeScreen.kt` / `VoiceModeContent.kt`: attachment menu, photo picker, actual camera, mic, orb and close. Files return to chat. Screen sharing is explicitly unavailable. Live voice retains its separate realtime controller and puts its waveform with bottom controls. |

Feature/project routes keep their state and custom headers while sharing the same
composer and message owners. They are not alternate copies of the Home UI. Work
remains the existing Coming soon destination.

The `supplied-chat` and `supplied-composer` tags are compatibility identifiers on
the canonical nodes, not another screen. Archived tests were migrated to
`ChatWiringUiTest` and `RestoredHomeScreenshotTest`.

Reference observations and unseen behavior are listed in
[GEMINI_ANDROID_OBSERVATIONS.md](GEMINI_ANDROID_OBSERVATIONS.md). Dua branding,
authentication, backend contracts and data ownership remain unchanged. There is
no copied reference account content or seeded library inventory.

## Validation

Use the Android commands in `SPEC-gemini-android-ui.md`. Actual results and local
environment constraints are recorded in `tasks/todo.md`. Opt-in screenshot tests
require `-Pdua.screenshots`; their fixtures render the same production screen.
Phone acceptance is still needed for IME/Back, Android pickers, permissions,
microphone/speech and TalkBack. No backend deployment is needed for this UI.
