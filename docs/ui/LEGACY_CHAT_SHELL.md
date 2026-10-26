# Android chat shell ownership — restored Dua Home

The owner restored the original Dua calligraphy Home with the Chat/Task switcher.
`ChatScreen` defaults to the original pane for Home and conversations; `ChatRoute`
does not opt into the supplied reference screen. Work remains in the drawer and
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
| `HomeTab`, `HomeTaskPromptIds` | Shared declarations remain in `ui/chat/SuppliedChatUi.kt`; one enum and one resource list. |
| `splitFileNames` | `ui/chat/ChatFileNames.kt`; shared parser, unchanged. |
| `UserMessage`, `MessageList`, `AssistantMessage`, `TypingIndicator` | Original conversation and feature rendering. |
| `DuaCalligraphyLogo` | `ui/components/DuaBrandLogos.kt`; original artwork, unchanged. |

`taskHome` stays owned by `ChatScreen`. Task toggles the existing Agent handler;
a starter fills the draft and focuses the input without sending. Header profile,
model picker, separate dictation/live voice, conversation search, active-mode chips,
swipe-up attachment gesture and swipe-to-reply use their original handlers.

The supplied reference implementation and its tests remain archived, explicitly
opted in with `useSuppliedChatUi = true`. There is no production caller selecting
it, no user-facing switch, and no screenshot-style title/emoji Home in the live path.
Keeping this reference avoids deleting its regression coverage during restoration.

## Verification and release

Original Home header, profile, search, gesture, ambient-motion and Chat/Task tests
are restored alongside the existing supplied reference tests. `HomeScreenshotTest`
renders live Dua Home; `SuppliedHomeScreenshotTest` records only the archived design.
Default captures use `app/build/approved-ui/`; opt-in screenshot tests require
`-Pdua.screenshots`. Do not claim excluded suites or physical-device checks passed.

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
