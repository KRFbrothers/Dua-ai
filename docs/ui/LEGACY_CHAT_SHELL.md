# Android chat shell ownership after the Home cleanup

Home uses `SuppliedChatScreen` whenever the resolved feature and custom top bar are
both absent. This selection lives in `ChatScreen`; callers no longer choose a shell.
`ChatRoute` only passes state/actions and any feature-specific top bar.

```text
ChatRoute → ChatScreen (drawer, sheets, task state, media and scroll host)
                     → SuppliedChatScreen: Home Chat, Task and Work
                     → legacyChatPane: feature or custom top bar
```

`ProjectChatRoute` supplies both the Projects feature and its own header, so it stays
on the legacy pane. Images and Agent also keep their feature header, message rendering
and composer. Their source files and production styling are unchanged by this cleanup.
The old Home header, empty-state hero and backdrop files have been removed.

## Shared ownership — keep

Paths are relative to `apps/android/app/src/main/java/com/dua/ai/`.

| Symbol | Owner / callers |
| --- | --- |
| `HomeTab` | `ui/chat/SuppliedChatUi.kt`; shared Chat/Work destination enum. |
| `HomeTaskPromptIds` | `ui/chat/SuppliedChatUi.kt`; unchanged resource list consumed by the supplied Task prompts and test fixtures. |
| `splitFileNames` | `ui/chat/ChatFileNames.kt`; unchanged parser used by both user-bubble renderers and reply quotes. |
| `UserMessage`, `ChatSizes.legacyFileChipNameMax` | Legacy feature/ProjectChat user bubble and fallback filename-chip width. Both remain. |
| `DuaCalligraphyLogo` | `ui/components/DuaBrandLogos.kt`; shared brand artwork, including the paused screen. |
| `TypingIndicator` | `ui/chat/MessageList.kt`; retained for legacy waits and the shared reply-placeholder component. |
| `MessageList`, `AssistantMessage` | Shared by supplied and legacy panes. False-style branches still belong to feature/ProjectChat rendering. |
| `HomeModeSwitcher` | Locked `ui/chat/ChatComposer.kt`; reused as-is by empty supplied Home. Do not edit its owner. |

`taskHome` remains owned by `ChatScreen`. Task selection calls the existing Agent
handler; a starter only fills the draft, focuses the input and requests the keyboard.
Work shows Coming soon without a composer. No backend or ViewModel behavior is added.

## Known Home parity gaps

The supplied design does not expose the old direct profile button, separate voice-mode
button, conversation-search header entry, active-mode chips/swipe-up sheet gesture,
floating conversation switcher or swipe-to-reply entry wiring. This cleanup does not
add those features. Existing Voice, Add, title-long-press model selection and drawer
settings/new-chat actions remain the supported entry points.

Tests tied only to removed Home decorations/header entry points are removed or rewritten;
this is a coverage change, not proof that the missing controls were ported. Pure search
and parser tests, reusable component tests, feature tests and ProjectChat routing checks
remain. The locked composer and attachment sheet still serve legacy feature routes.

## Screenshot and release checks

`HomeScreenshotTest` now records supplied Home. `SuppliedChatWiringUiTest` uses the
normal host selection and covers live Home, Task, Work plus explicit Images/custom
ProjectChat-header routing. Existing default UI recording captures are under
`app/build/approved-ui/`; the historical screenshot package still requires
`-Pdua.screenshots`. Do not label an excluded suite as executed.

Keep the PR4 APK until the 5b APK passes owner review on the phone: Home Chat, Task,
Work, Images and Project chat. Confirm feature renders are unchanged, inspect new Home
renders and check the parity gaps above. Do not merge before review.
