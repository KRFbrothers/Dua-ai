# Approved Android navigation UI

Reference: [Dua AI tldraw board](https://www.tldraw.com/f/6aV_i2gXeMVcvKMTbg7NP).

Read Page 1, **Dua AI — Navigation Flow**, and **Dua AI — Complete UI Map** before implementation. The approved Sidebar, expanded Aura, Profile & Settings, accent and appearance mockups define the layout. The additive GitHub source gallery describes the earlier source inventory (304014a); it is not the target design. The latest source also has planner, history search and editable project memory, which remain available. No `AGENTS.md` or `Dua_AI_UI_Placement_List.md` was present in this checkout.

## Placement and real actions

| Placement | Implementation / destination |
| --- | --- |
| Main sidebar | Cube + Dua AI, search, Images, Library, Projects, Computer, AI Agent, Work, Aura; fixed footer New chat and account avatar |
| Aura | Expanded panel with Back, Aura, search, Plugins, Computer, AI Agent and Work; same fixed footer |
| Recent chats | The only scrolling region in either panel; date groups, current-chat highlight, project-scoped title/message/draft search, clear search and existing cache-removal confirmation preserved |
| Projects | Existing Projects sheet, project instructions, task due dates, reminders, daily planner and editable project memory |
| Computer | Existing Computer placeholder; the Home button also remains |
| Profile avatar | Profile & Settings hub; Account, Personalization, Settings, preserved voice/data/setup entries, About Dua AI and confirmed Logout |
| Account | Existing Google sign-in, sign-in errors, account status and typed account-deletion confirmation; Logout moved to the profile hub |
| Personalization | Appearance, existing fonts and haptics |
| Memory, Notifications, Voice | Existing memory editor, notification/reminder controls and voice settings; voice input, voice mode/live voice and languages retained |
| More settings / Voice / Data / Setup | Existing Sync review, Imagine settings, dictation, conversation modes, generated-voice control, Data controls, Privacy, Sharing, Permissions and feature diagnostics |

The old `settings` route still resolves to the hub, so restored navigation state remains valid. Existing cache operations keep navigation guards and reply audio stops on destination changes. Chat models, markdown/copy/retry, attachments, generated media, code execution, Build/repository reading and PR approval remain unchanged.

## Coming soon

Dedicated Images gallery, Library, AI Agent, Work, Plugins, General, Usage and limits, Safety, Security and login, Storage, History and data, settings search and profile-photo editing have no corresponding implementation in the current source. They open a title/back/Coming soon page. Imagine generation and existing Data controls remain available separately; they are not disguised as these missing screens. The approved chat-options Rename and Pin entries explicitly show Coming soon; existing Remove cached chat still uses its real confirmation.

## Preferences and theme preservation

- Appearance (`system`, `light`, `dark`) and optional accent override use separate keys in Preferences DataStore `dua_appearance`. Fresh installs use System default; an existing valid appearance is copied once from the legacy JSON without deleting or modifying its memory/projects.
- Presets: Purple `#AA4FF6`, Orange `#FF6308`, Blue `#2979FF`, Green `#16A34A`, Pink `#E84393`, Teal `#00897B`.
- An override applies in both themes. **Reset to default** removes only the accent key; it keeps the appearance choice. Without an override Light uses Purple and Dark uses Orange. Device settings reset also resets appearance to System default and clears the override.
- Exact preset colors are used for swatches and New chat. Default page text/icons use the theme foreground; foreground colors and Material text roles are adjusted for contrast, while original neutral/error/code/brand tokens in `Color.kt` stay intact. The full original logo assets and Home/Onboarding image rendering are untouched; the artwork is never recolored with the accent.
- These are device appearance preferences, not server history or memory. They are not part of a cloud snapshot. DataStore failures show a storage/retry notice.

## Responsive behavior

The sidebar is at most 360dp wide; Aura expands to at most 520dp and is constrained by the available window. Both use system/keyboard insets. At short heights, fixed navigation uses two compact columns with at least 48dp targets, leaving the list and footer reachable. Regular heights use the approved single-column arrangement. Fixed menu labels can ellipsize at large font sizes; accessibility retains the complete text. Profile rows grow and scroll instead of overlapping. Accent previews stack when font scaling or narrow width requires it.

## Validation

Automated tests cover DataStore restart persistence, independent theme/accent writes, migration, reset defaults, all six accents in both themes (text contrast at least 4.5:1), fixed sidebar/Aura controls during list scrolling, real project/account/sync/Imagine routes and logout confirmation. Compose UI checks run in both appearances at 360dp and at 320 × 568dp with 2× font scaling. CI records those UI renders in the Android test-report artifact; they use fixture chats and an example account, never production data.

These are code/build/automated rendering checks. Physical-device Google login, live production, keyboard behavior and TalkBack remain device checks; this UI change does not claim to have verified them.

Run locally:

```sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --no-daemon --max-workers=2
```

Review `app/build/approved-ui/*.png` and the test reports. The opt-in full historical screenshot suite remains separate (`-Pdua.screenshots`). Do not merge or replace the permanent signing key as part of UI review.
