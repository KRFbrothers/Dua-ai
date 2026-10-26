# Android legacy chat shell ownership

Source inventory checked against `main` at `665cbc8c21390e3dc03c6ae8c152f0932a89c269`.
This is an ownership map, not a deletion or migration plan. Legacy-only means outside
the live Home rendering path; these symbols still have callers and must stay for now.

## Live Home and legacy routing

The live Home chat is `SuppliedChatScreen` in
[SuppliedChatUi.kt](../../apps/android/app/src/main/java/com/dua/ai/ui/chat/SuppliedChatUi.kt).
[ChatRoute.kt](../../apps/android/app/src/main/java/com/dua/ai/ui/chat/ChatRoute.kt)
sets `useSuppliedChatUi = feature == null && homeTab == HomeTab.Chat && topBar == null`.
[ChatScreen.kt](../../apps/android/app/src/main/java/com/dua/ai/ui/chat/ChatScreen.kt)
keeps hosting the drawer, sheets and existing state/actions. Its `chatPane` selects
`SuppliedChatScreen` when the flag is true, no feature is selected and the tab is Chat;
otherwise it invokes `legacyChatPane`.

```text
ChatRoute → ChatScreen (drawer/sheets host)
                     → SuppliedChatScreen: Home chat, including its conversation
                     → legacyChatPane: features, Work/Task home, custom topBar
```

Task-home controls remain within the legacy pane. A direct `ChatScreen` caller that
omits `useSuppliedChatUi` also gets the legacy pane because the flag defaults to false.
The legacy pane remains live for these routes; it is not unused code.

## Legacy-only UI symbols and branches — keep for now

Paths below are relative to `apps/android/app/src/main/java/com/dua/ai/`.

| Symbol / branch | Owner file | Current call path |
| --- | --- | --- |
| `ChatTopBar` | `ui/chat/ChatTopBar.kt` | Called inside `ChatScreen.legacyChatPane` when neither a custom topBar nor a feature top bar is supplied. |
| `ChatContent`, private `HomeHero`, private `TaskStarters` | `ui/chat/ChatContent.kt` | `legacyChatPane` calls `ChatContent` for its empty Home; `HomeHero` and `TaskStarters` are private helpers within it. |
| `Modifier.homeBackdrop` | `ui/chat/HomeBackdrop.kt` | Applied inside `legacyChatPane` for non-feature, non-Work chat surfaces. |
| `UserMessage` | `ui/chat/UserMessage.kt` | `MessageList` selects it for user turns when `suppliedStyle` is false; live Home selects `SuppliedUserMessage` instead. The file also contains the shared `splitFileNames` helper: keep the file. |
| `ChatSizes.legacyFileChipNameMax` | `ui/theme/ChatTokens.kt` | Still used by `UserMessage` for fallback filename chips. Keep this token and its owner file. |
| `!suppliedStyle` and equivalent false-style branches | `ui/chat/MessageList.kt`, `ui/chat/AssistantMessage.kt` | Legacy message animation, spacing/wrapping, user-bubble selection, action-row gating, reply placeholder and streaming cursor/fade behavior. Only these branches belong to the legacy style; both files remain shared. |
| `HomeModeSwitcher` | `ui/chat/ChatComposer.kt` | Called from the legacy pane's Home/composer and floating conversation controls. The owner file is locked: document only; do not edit or remove it. |

## Shared symbols — keep

| Symbol | Evidence / reason |
| --- | --- |
| `splitFileNames` | Defined in `UserMessage.kt`; also called by `SuppliedUserMessage` in `SuppliedChatUi.kt` and `ReplyQuote.of` in `ChatSwipeReply.kt`. Its owner file cannot be removed with the legacy bubble. |
| `DuaCalligraphyLogo` | Defined in `ui/components/DuaBrandLogos.kt`; used by legacy `HomeHero` and `ui/AppPausedScreen.kt`. It remains shared brand artwork even though live Home does not call that hero. |
| `TypingIndicator` | Defined in `MessageList.kt`; used by the legacy waiting turn and by `ReplyPlaceholder` in `StreamingPolish.kt`. Keep the reusable indicator and its owner file; live supplied-style waits select `GeminiLoadingShimmer`. |
| `MessageList` | Both `legacyChatPane` and `SuppliedChatScreen` call it. Live Home passes `suppliedStyle = true`; legacy callers use the false default. |
| `AssistantMessage` | Called by the shared `MessageList`, which forwards `suppliedStyle`. Markdown, sources, code results and other reply rendering remain shared; false-style branches do not make the entire component legacy-only. |

## Test coverage boundaries

The historical chat-shell screenshot fixtures under
[app/src/test/java/com/dua/ai/screenshots](../../apps/android/app/src/test/java/com/dua/ai/screenshots)
(such as `HomeScreenshotTest` and `DuaScreenshotTest`) call `ChatScreen` without
`useSuppliedChatUi = true`. Those chat-shell captures still exercise the legacy pane,
not live Home. Other screenshots in that directory cover their respective screens or
sheets; they are not evidence of live Home wiring. The historical screenshot suite is
opt-in with `-Pdua.screenshots`.

[SuppliedChatWiringUiTest](../../apps/android/app/src/test/java/com/dua/ai/ui/chat/SuppliedChatWiringUiTest.kt)
explicitly passes `useSuppliedChatUi = true` and exercises the live Home path, including
existing send/stream/stop handlers, drawer restoration, attachments, keyboard and errors.
It records `supplied-wired-*` renders. Legacy component tests remain valid for their own
callers; do not delete or relabel them as live Home coverage.

## Re-check commands

Run from the repository root before any future cleanup:

```sh
cd apps/android/app/src
rg -n 'ChatTopBar\(|ChatContent\(|homeBackdrop\(|HomeModeSwitcher\(|UserMessage\(|DuaCalligraphyLogo\(|splitFileNames|TypingIndicator\(|legacyFileChipNameMax' -g '*.kt'
rg -n 'useSuppliedChatUi' -g '*.kt'
```

Compare declarations, production callers and test fixtures separately. A shared helper
can live beside a legacy-only composable; filename placement is not proof that it can
be deleted. This documentation changes no Kotlin, tests, resources or build behavior.
