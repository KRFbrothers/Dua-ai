# Android chat shell ownership — restored Dua Home

Related: [settings-card and loading ownership](COMPONENT_OWNERSHIP.md).

The owner restored the original Dua calligraphy Home with the Chat/Task switcher.
`ChatScreen` has one chat pane for Home, conversations and feature/custom headers.
The archived supplied screen and its opt-in/style flags have been removed. Work remains in the drawer and
shows Coming soon without a composer.

```text
ChatRoute → ChatScreen (drawer, sheets, task state, media and scroll host)
                     → ChatTopBar + ChatContent (empty Home Chat / Task)
                     → MessageList (conversation)
                     → existing feature / ProjectChat pane and custom header
```

## Ownership

| Symbol | Owner / use |
| --- | --- |
| `ChatTopBar` | `ui/chat/ChatTopBar.kt`; original history, model, profile and New chat header. |
| `ChatContent`, `HomeHero`, `TaskStarters` | `ui/chat/ChatContent.kt`; original calligraphy logo, tagline and Task starters. |
| `homeBackdrop` | `ui/chat/HomeBackdrop.kt`; Home/conversation decoration, excluded from features. |
| `HomeModeSwitcher` | Locked `ui/chat/ChatComposer.kt`; reused unchanged in empty Home and conversations. |
| `HomeTab`, `HomeTaskPromptIds` | `ui/chat/ChatTopBar.kt` owns the destination enum; `ui/chat/ChatContent.kt` owns the unchanged prompt resource list. |
| `splitFileNames` | `ui/chat/ChatFileNames.kt`; shared parser, unchanged. |
| `UserMessage`, `MessageList`, `AssistantMessage`, `TypingIndicator` | Original conversation and feature rendering. |
| `DuaCalligraphyLogo` | `ui/components/DuaBrandLogos.kt`; original artwork, unchanged. |

`taskHome` stays owned by `ChatScreen`. Task toggles the existing Agent handler;
a starter fills the draft and focuses the input without sending. Header profile,
model picker, separate dictation/live voice, conversation search, active-mode chips,
swipe-up attachment gesture and swipe-to-reply use their original handlers.

The parallel title/emoji shell, composer, user/assistant wrappers and loading shimmer
are deleted. MessageList and AssistantMessage retain only the original live rendering.
The now-unused semantic-colour adapter and alternate welcome style are also removed;
Material/theme values, appearance defaults and live typography stay unchanged.

## Verification and release

Original Home header, profile, search, gesture, ambient-motion and Chat/Task tests
remain. The old supplied wiring/screenshot test files are renamed to
`ChatWiringUiTest` and `RestoredHomeScreenshotTest`, keeping their test cases and
adapting assertions to the live Home. Ambient motion now exercises the shared media
shimmer instead of the deleted alternate shimmer. No tests are deleted.
`HomeScreenshotTest` and `RestoredHomeScreenshotTest` render the same live Home.
Default captures use `app/build/approved-ui/`; the opt-in screenshot package still
requires `-Pdua.screenshots`. Do not claim excluded suites or phone checks passed.

The conservative drawer clearance from PR441 remains. Its pointer regression now
checks the restored Voice mode control: one tap dismisses the drawer, the next opens
live voice. The locked composer and attachment-sheet files are unchanged.

Before accepting the APK, check Home Chat, Task prompts, Work from the drawer,
model/profile, dictation/live voice, Images and Project chat. Keep the installed
working APK until the restored version is accepted. No backend deployment is needed.

## Projects compatibility entry

`ChatSheet.Projects` is a compatibility entry for restored sheet state. `ChatScreen`
handles it before composing chat and delegates to `ProjectsFeaturePage`, which uses
the shared Projects list and new-project editor. The later modal call and its
otherwise unused `ProjectsSheet` wrapper have been removed.

`ProjectsContent` stays in `ProjectsSheet.kt`: the dedicated Projects flow still
uses it for Tasks/Memory tools, and component tests exercise its planner, editing
and selection actions. Its implementation is unchanged. This cleanup does not
change the restored Dua Home, Project chat, project persistence or the locked UI.
