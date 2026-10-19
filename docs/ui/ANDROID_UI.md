# Dua AI Android UI: setup, design system and approved navigation

Merged from the former root `ANDROID_UI_GUIDE.md` and `APPROVED_UI_IMPLEMENTATION.md`. Colour/type token mapping: [design-system-tokens.md](design-system-tokens.md).

Dua AI keeps its original name and artwork. This is the owner's personal assistant, with a compact mobile chat layout. Google owner login and temporary offline cache access are preserved. Phase 1 now adds owner-only email login/signup and password setup for the existing account; unrestricted online guest access remains outside scope.

## Screens and structure

| Screen | Production code / behavior |
| --- | --- |
| Welcome | `ui/onboarding/OnboardingScreen.kt`: full Dua logo, real Google login, Skip for now |
| Home | `ui/chat/ChatScreen.kt`, `ChatTopBar.kt`, `EmptyState.kt`: current-project header, Computer shortcut, logo and suggested prompts that fill the draft, without sending |
| Conversation | `MessageList.kt`, `UserMessage.kt`, `AssistantMessage.kt`, `MarkdownMessage.kt`: bubbles, streamed replies, Markdown, copyable code blocks, sources, typing and partial-stop states |
| Composer | `Composer.kt`, `AttachmentSheet.kt`, `StoredFileChips.kt`: multiline input, send/stop, model choice, mic/voice and file tools |
| History / Aura | `ui/history/HistoryDrawer.kt`: project search, fixed navigation/footer and independently scrolling recent chats |
| Projects | `ProjectsSheet.kt` and project task/memory/planner components: selected project, instructions, archive, tasks, memory and planner |
| Profile and settings | `ui/navigation/DuaNavHost.kt`, `ui/settings/`, `ui/screens/ProfileScreen.kt`: owner account, appearance/accent, fonts, haptics, notifications; Voice contains its input/mode/reply controls, and History and data contains Data controls/privacy/sharing/permissions |

Production follows the existing MVVM structure: `MainActivity → DuaApp → DuaNavHost → ChatRoute → ChatScreen`. `ChatViewModel` owns state and operations; `ChatUiState`/`ChatActions` keep the chat components reusable. Account, network, history, settings, projects and voice modules hold their existing responsibilities. The backend lives only in `web/`; provider keys never belong in Android source, resources or build configuration.

Chat interactions: only the latest finished assistant reply shows the permanent Copy/Retry/Share/Speak row, and the row stays hidden while a reply is pending/streaming. Long-press a user bubble or assistant turn to open message actions, with the existing preference-aware haptic. Copy/Share preserve the full message payload (including executed code for replies); Speak uses the existing selected-reply voice handler. Retry is visible for assistant turns but enabled only where the existing last-reply handler allows it. User-message editing has no existing handler, so no Edit action is shown. Generated-media actions retain their existing handlers and gallery controls.

Each adjacent assistant group has one 24dp original Dua logo mark in a 48dp-aligned gutter, including typing indicators. Consecutive assistant chunks keep the same gutter without repeating the avatar. User bubbles remain aligned right; the avatar and sheet actions have TalkBack labels and action targets remain at least 48dp. No backend or message-storage behavior changes.

## Design system

Actual Compose renders: [Dark preview](compact-preview-dark.png) · [Light preview](compact-preview-light.png). These use synthetic chats/projects, not the owner's conversations.

- Existing `ui/theme/Color.kt` neutral surfaces, error/code roles and full logo artwork are preserved.
- `DuaTheme.kt` and `AccentColors.kt` apply Light/Dark plus six accent presets independently. Defaults: Light Purple `#AA4FF6`; Dark Orange `#FF6308`. Accent reset clears only the override.
- `Type.kt` supplies the Inter-based text scale: 16sp body, 14sp secondary/buttons, 20sp page titles; Android font scaling stays enabled. The Home tagline keeps the existing Montserrat family.
- `Spacing.kt` supplies the 4dp spacing scale. Rounded surfaces use `Shape.kt`. Controls retain 48dp touch targets; menu/footer stay fixed while history scrolls. Code/Think/Search use accessible icon-only controls when their labels cannot fit.
- The header and project actions are neutral. Accent is used selectively for selection and settings; the logo is never tinted.
- Existing reduced-motion handling, keyboard insets, Android back navigation and conversation scroll-follow behavior remain in place.
- Shared motion helpers in `ui/components/`: `PressScale.kt` (`Modifier.pressScale`, a 4% press shrink added next to the normal ripple; used by Home action chips and source cards) and `Shimmer.kt` (`rememberShimmerAlpha` and `ShimmerBox`, the one loading pulse for the thinking badge, Agent step label and media placeholders). Both stay still when the system removes animations or the app is in the background.
- Drag and drop (`ui/chat/ChatDropTarget.kt`, `attachments/DroppedFiles.kt`): photos and files dragged from another app (split screen, tablet, foldable, desktop mode) are copied into private cache (`cache/dropped-files/`, cleared after a day) and go through the normal attachment path; dropped text is added to the draft. Up to 3 files per drop; Dua's own provider URIs are rejected, as for Share. The + attachment sheet is unchanged.
- On-device AI (`ondevice/`, setting: General → Device preferences → **On-device AI**): on phones where Android AICore offers Gemini Nano (through ML Kit GenAI Prompt), new chat titles are made on the phone first and the Imagine sheet shows **Polish my prompt** with the text label "On-device" (no brand art). It rewrites the composer draft in place; nothing is sent until the user sends. Unsupported phones, the setting off, a timeout or an empty answer quietly use the existing server title path, and the polish row is hidden. Titles never start a model download; only a polish tap may ask the system to download the model.
- Remove background (`ondevice/MlKitBackgroundRemover.kt`, `ondevice/Cutouts.kt`): Imagine images get a **Remove background** button in the full-screen viewer, and photo attachments get a small 60 dp "On-device" chip next to them (same height as the attachment, so the composer does not grow; the photo's own remove button is unchanged) that opens **Remove background · On-device**. ML Kit subject segmentation runs on the phone (the model is downloaded once by Google Play services) and the transparent PNG is saved to Pictures/Dua with the same gallery code as Save (storage permission on Android 8–9). Hidden on phones without Google Play services. Uploaded files (stored, not inline) are not offered yet.
- Diff, voice, danger and media colors now use the extended `LocalDuaColors` roles in both appearances. Screens have no literal Compose palette colors or text sizes; custom branding/code sizes live in `Type.kt`. See the [complete value-to-token mapping](design-system-tokens.md) and contrast checks.

## Run the real app

1. Open the repo in Android Studio. Install JDK 17, Android SDK 35 and build-tools 35.0.0.
2. Build `bash ./gradlew :app:assembleDebug --no-daemon --max-workers=2`. Install `app/build/outputs/apk/debug/app-debug.apk` on an emulator for development.
3. For the owner's phone, use the permanent signed release workflow in [SIGNING.md](../../SIGNING.md). A development/debug certificate differs from the owner release certificate; do not replace an installed release with a debug APK.
4. The production API defaults to `https://dua-api-228305912525.us-central1.run.app` (dua-api on Cloud Run). For local backend development, install the `web/` dependencies and run `npm start`. Android requires HTTPS in every build: use a trusted HTTPS development endpoint and override it with `-PduaApiBaseUrl=https://YOUR-ENDPOINT`. A raw HTTP emulator URL is rejected. See [README.md](../../README.md) for backend requirements.
5. Online replies require the deployed backend, owner Google/Supabase account configuration and at least one configured provider. Verify `DUA_REQUIRE_LOGIN=1` and `DUA_ALLOWED_EMAIL` on the server. Local cache access does not bypass that lock.

## Run sample conversations without integrations

The debug build includes a separate `UiPreviewActivity`, using only synthetic fixtures and the same production chat/onboarding components:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dua.ai/.preview.UiPreviewActivity
```

Use Home, Chat, Error and Welcome plus the Light/Dark switch. A persistent **UI preview · sample data · no backend** banner distinguishes these fixtures. Sending/retrying displays a preview notice; it never generates an AI response or contacts a backend. Real account, voice and picker actions direct you to the normal app. The preview neither loads private chats nor creates an account/ViewModel. It is excluded from release source/manifest; the normal launcher remains unchanged.

The default native UI tests render production components with synthetic fixtures, separately from the preview activity. Run `bash ./gradlew :app:testDebugUnitTest -Pdua.ui-record --no-daemon --max-workers=2` from the repository root and inspect `app/build/approved-ui/*.png`. The flag enables PNG recording; without it, the tests still run. Historical screenshot classes are excluded unless `-Pdua.screenshots` is supplied. See [README.md — Android and routine checks](../../README.md#android-and-routine-checks) for both commands, report locations and CI scope. Recorded PNGs are not proof of physical-device or pixel-baseline verification.

## Implemented versus integration-dependent or pending

| Feature | Status |
| --- | --- |
| Suggested prompts, multiline composer, model selector, code/Think/Search controls | Implemented; a suggestion fills the draft and requires an explicit Send |
| Google login, owner lock, server-first history/memory/projects | Implemented; require matching Google signing/client configuration, Supabase and deployed backend |
| Streaming, stop, Markdown/code, copy and response regeneration | Implemented; actual generation/regeneration requires a configured provider; retry preserves the existing request mode |
| Reply sharing | Implemented through Android's share chooser; no automatic external send |
| History search, opening chats and removing a cached chat | Implemented; cache removal preserves server history and stored files. This is not server-history deletion |
| Rename | Long-press a recent chat → Rename; 1–120 character title metadata survives cache, JSON backup/import and cloud sync. Requires backend `history.rename` support. |
| Pin / Unpin | Long-press a recent chat; a project-scoped Pinned group, cache/JSON backup/cloud persistence, and metadata-aware sync. Requires backend `history.pin` support; saving locally queues sync. |
| Delete chat | Long-press → Delete chat → confirm. Owner sign-in and backend `history.delete` support required. Server deletion IDs prevent stale snapshots from restoring the same chat; stored files remain. Failed deletion keeps the confirmation open for retry. |
| Remove cached chat | Removes only the phone copy; server history and files remain. Available while signed out. |
| Editing an old user message, thumbs-up/down feedback | Pending; no history rewriting or feedback-storage endpoint is claimed |
| Voice input, voice mode, photos/documents/camera, upload progress/errors | Implemented production flows; microphone/speech service, Android pickers, private Storage and provider format support are required. Upload failure recovery currently means removing/reselecting the file |
| Theme/accent, font family, notifications/reminders, account/data controls | Implemented. General UI language and an in-app text-size slider are pending; Android system text scaling and existing voice-language controls work |
| Empty history, loading, request errors and offline cached viewing | Implemented. No offline AI generation; a dedicated connectivity status/retry banner is pending |
| Images gallery, Library, AI Agent, Work, Plugins and other missing settings destinations | Images, Library and Plugins are implemented ([FEATURE_HUBS.md](../FEATURE_HUBS.md)); remaining placeholders are listed under [Coming soon](#coming-soon) |
| Email login/signup and existing-account password setup | Implemented under Account (and Continue with email on Welcome when configured); owner-only, mandatory email confirmation and provider/SMTP setup required. Passwords are transient; password reset/recovery UI remains pending. |
| Unrestricted online guest mode | Outside the owner-only access model |

Code/build/render verification is not proof of live provider, account, Storage, permission or physical-device behavior. Check these with [RELEASE-CHECKLIST.md](../RELEASE_CHECKLIST.md) and the owner's installed release APK.

## Validation for the compact-chat update

- Local debug APK build and the default 462-test Android suite passed with zero failures/errors/skips; that count excludes the opt-in historical screenshot classes.
- After adding the share-chooser regression check and correcting the screenshot fixture background, 33 UI/action tests passed. The new share test checks Android's chooser receives the full reply including executed code.
- Home, Projects, Sidebar/Aura and settings were rendered/reviewed in Light/Dark, with 320 × 568dp at 2× font scaling. The debug APK manifest contains the isolated preview activity.
- Local release-task verification requires the permanent signing credentials and was not completed. Use the existing main-only signed GitHub workflow after merging; do not create a new keystore.
- Physical-device interaction and live backend/account/permissions remain checks for the owner's installed release.

## Voice and data grouping

Profile has one **Settings → Voice** entry. Voice input, voice mode/live conversation, reply providers, samples, speed and language controls live inside it; the separate profile Voice card is removed. The older-backend AI reply-voice switch remains available there with its original account/setup gating.

The separate profile Data card is also removed. **Settings → History and data** opens the existing Data controls, Your data, Sharing and Permissions destinations. Their data operations and confirmations are unchanged; navigating into this group does not modify any data.

For this grouping update, debug APK packaging and 55 focused UI/voice tests passed. Native renders were checked in both themes and at 320 × 568dp with 2× font scaling. The owner's installed APK still needs a phone navigation check after updating.

## Approved navigation UI

Reference: [Dua AI tldraw board](https://www.tldraw.com/f/6aV_i2gXeMVcvKMTbg7NP).

### Owner-requested compact styling

After trying the approved-navigation APK on the phone, the owner requested a more compact Home, Sidebar and Projects presentation. The navigation placements and existing actions remain, with these visual adjustments:

- Home uses a single header: history, Dua AI/current-project selector, Computer shortcut and New chat. The orange project row is removed. The full original logo is smaller, with a quieter tagline; its artwork is unchanged.
- Sidebar/Aura use the existing 16sp body scale and 48dp menu targets. The footer New chat is neutral rather than a large accent block; only recent chats scroll.
- Projects have left-aligned names in grouped cards, a subtle selected state, neutral Edit/Memory/Tasks actions and a short explanation. Daily planner, project editing/archive, tasks and project memory remain available.
- The composer model control is neutral and can shrink to fit beside the attachment and voice/send controls at large font sizes. Code/Think/Search modes remain available.

Six accent presets and independent persisted appearance still work; the accent is used selectively for selection and theme controls rather than every navigation action. This owner feedback supersedes the earlier large-label/accent-footer styling, not the approved navigation flow.

The sections above cover the Light/Dark previews, the sample-conversation debug entry point, setup and remaining features.

Read Page 1, **Dua AI — Navigation Flow**, and **Dua AI — Complete UI Map** before implementation. The approved Sidebar, expanded Aura, Profile & Settings, accent and appearance mockups define the layout. The additive GitHub source gallery describes the earlier source inventory (304014a); it is not the target design. The latest source also has planner, history search and editable project memory, which remain available. No `AGENTS.md` or `Dua_AI_UI_Placement_List.md` was present in this checkout.

### Placement and real actions

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
| Settings → Voice | Dictation, voice mode/live conversation, reply voice/provider, speed, samples and languages; the legacy AI reply-voice switch remains available on older backends |
| Settings → History and data | Existing Data controls, Your data/privacy, Sharing and Permissions destinations; the profile no longer repeats the Voice/Data cards |
| More settings / Setup | Existing Sync review, Imagine settings and feature diagnostics |

The old `settings` route still resolves to the hub, so restored navigation state remains valid. Existing cache operations keep navigation guards and reply audio stops on destination changes. Chat models, markdown/copy/retry, attachments, generated media, code execution, Build/repository reading and PR approval remain unchanged.

### Coming soon

Current placeholders (from `DuaDestination.comingSoonTitle`): **Search settings**, **Edit profile photo**, **Privacy**, **Sharing**, **Permissions** and **Screenshare / Video**. They open a title/back/Coming soon page and are not disguised as working screens. Images, Library and Plugins ([FEATURE_HUBS.md](../FEATURE_HUBS.md)) and General, Usage and limits, Safety, Security and login and Storage ([SETTINGS_DEVICE_CHECK.md](../SETTINGS_DEVICE_CHECK.md), [STORAGE_BACKUP.md](../STORAGE_BACKUP.md)), which earlier versions listed here, now have real screens. Rename and Pin in chat options are implemented ([HISTORY_SEARCH.md](../features/HISTORY_SEARCH.md)); Remove cached chat still uses its real confirmation.

### Preferences and theme preservation

- Appearance (`system`, `light`, `dark`) and optional accent override use separate keys in Preferences DataStore `dua_appearance`. Fresh installs use System default; an existing valid appearance is copied once from the legacy JSON without deleting or modifying its memory/projects.
- Presets: Purple `#AA4FF6`, Orange `#FF6308`, Blue `#2979FF`, Green `#16A34A`, Pink `#E84393`, Teal `#00897B`.
- An override applies in both themes. **Reset to default** removes only the accent key; it keeps the appearance choice. Without an override Light uses Purple and Dark uses Orange. Device settings reset also resets appearance to System default and clears the override.
- Exact preset colors are used for swatches and accent previews. Default page text/icons use the theme foreground; foreground colors and Material text roles are adjusted for contrast, while original neutral/error/code/brand tokens in `Color.kt` stay intact. The full original logo assets are untouched; Home uses a smaller placement, and the artwork is never recolored with the accent.
- These are device appearance preferences, not server history or memory. They are not part of a cloud snapshot. DataStore failures show a storage/retry notice.

### Responsive behavior

The sidebar is at most 360dp wide; Aura expands to at most 520dp and is constrained by the available window. Both use system/keyboard insets. At short heights, fixed navigation uses two compact columns with at least 48dp targets, leaving the list and footer reachable. Regular heights use the approved single-column arrangement. Fixed menu labels can ellipsize at large font sizes; accessibility retains the complete text. Profile rows grow and scroll instead of overlapping. Accent previews stack when font scaling or narrow width requires it.

### Validation

Automated tests cover DataStore restart persistence, independent theme/accent writes, migration, reset defaults, all six accents in both themes (text contrast at least 4.5:1), fixed sidebar/Aura controls during list scrolling, real project/account/sync/Imagine routes and logout confirmation. Compose UI checks run in both appearances at 360dp and at 320 × 568dp with 2× font scaling. CI records those UI renders in the Android test-report artifact; they use fixture chats and an example account, never production data.

Local validation on 2026-10-11: debug APK packaging and 454 Android tests passed, including 20 Compose UI checks and 20 render PNGs. The extra interaction check for the existing delete-account dialog encountered Robolectric idling; its dialog interaction remains a physical-device check.

These are code/build/automated rendering checks. Physical-device Google login, live production, keyboard behavior and TalkBack remain device checks; this UI change does not claim to have verified them.

Run locally:

```sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --no-daemon --max-workers=2
```

Review `app/build/approved-ui/*.png` and the test reports. The opt-in full historical screenshot suite remains separate (`-Pdua.screenshots`). Do not merge or replace the permanent signing key as part of UI review.
