# Dua AI Android UI and setup

Dua AI keeps its original name and artwork. This is the owner's personal assistant, with a compact mobile chat layout inspired by ChatGPT. Google owner login and temporary offline cache access are preserved. Phase 1 now adds owner-only email login/signup and password setup for the existing account; unrestricted online guest access remains outside scope.

## Screens and structure

| Screen | Production code / behavior |
| --- | --- |
| Welcome | `ui/onboarding/OnboardingScreen.kt`: full Dua logo, real Google login, Skip for now |
| Home | `ui/chat/ChatScreen.kt`, `ChatTopBar.kt`, `EmptyState.kt`: current-project header, Computer shortcut, logo and suggested prompts that fill the draft, without sending |
| Conversation | `MessageList.kt`, `UserMessage.kt`, `AssistantMessage.kt`, `MarkdownMessage.kt`: bubbles, streamed replies, Markdown, copyable code blocks, sources, typing and partial-stop states |
| Composer | `Composer.kt`, `AttachmentSheet.kt`, `StoredFileChips.kt`: multiline input, send/stop, model choice, mic/voice and file tools |
| History / Aura | `ui/history/HistoryDrawer.kt`, `SidebarPanel.kt`: project search, fixed navigation/footer and independently scrolling recent chats |
| Projects | `ProjectsSheet.kt` and project task/memory/planner components: selected project, instructions, archive, tasks, memory and planner |
| Profile and settings | `ui/navigation/DuaNavHost.kt`, `ui/settings/`, `ui/screens/ProfileScreen.kt`: owner account, appearance/accent, fonts, haptics, notifications; Voice contains its input/mode/reply controls, and History and data contains Data controls/privacy/sharing/permissions |

Production follows the existing MVVM structure: `MainActivity → DuaApp → DuaNavHost → ChatRoute → ChatScreen`. `ChatViewModel` owns state and operations; `ChatUiState`/`ChatActions` keep the chat components reusable. Account, network, history, settings, projects and voice modules hold their existing responsibilities. The backend lives only in `web/`; provider keys never belong in Android source, resources or build configuration.

## Design system

Actual Compose renders: [Dark preview](docs/ui/compact-preview-dark.png) · [Light preview](docs/ui/compact-preview-light.png). These use synthetic chats/projects, not the owner's conversations.

- Existing `ui/theme/Color.kt` neutral surfaces, error/code roles and full logo artwork are preserved.
- `DuaTheme.kt` and `AccentColors.kt` apply Light/Dark plus six accent presets independently. Defaults: Light Purple `#AA4FF6`; Dark Orange `#FF6308`. Accent reset clears only the override.
- `Type.kt` supplies the Inter-based text scale: 16sp body, 14sp secondary/buttons, 20sp page titles; Android font scaling stays enabled. The Home tagline keeps the existing Montserrat family.
- `Spacing.kt` supplies the 4dp spacing scale. Rounded surfaces use `Shape.kt`. Controls retain 48dp touch targets; menu/footer stay fixed while history scrolls. Code/Think/Search use accessible icon-only controls when their labels cannot fit.
- The header and project actions are neutral. Accent is used selectively for selection and settings; the logo is never tinted.
- Existing reduced-motion handling, keyboard insets, Android back navigation and conversation scroll-follow behavior remain in place.
- Diff, voice, danger and media colors now use the extended `LocalDuaColors` roles in both appearances. Screens have no literal Compose palette colors or text sizes; custom branding/code sizes live in `Type.kt`. See the [complete value-to-token mapping](docs/ui/design-system-tokens.md) and contrast checks.

## Run the real app

1. Open the repo in Android Studio. Install JDK 17, Android SDK 35 and build-tools 35.0.0.
2. Build `bash ./gradlew :app:assembleDebug --no-daemon --max-workers=2`. Install `app/build/outputs/apk/debug/app-debug.apk` on an emulator for development.
3. For the owner's phone, use the permanent signed release workflow in [SIGNING.md](SIGNING.md). A development/debug certificate differs from the owner release certificate; do not replace an installed release with a debug APK.
4. The production API defaults to `https://dua-cyan.vercel.app`. For local backend development, install the `web/` dependencies and run `npm start`. Android requires HTTPS in every build: use a trusted HTTPS development endpoint and override it with `-PduaApiBaseUrl=https://YOUR-ENDPOINT`. A raw HTTP emulator URL is rejected. See [README.md](README.md) for backend requirements.
5. Online replies require the deployed backend, owner Google/Supabase account configuration and at least one configured provider. Verify `DUA_REQUIRE_LOGIN=1` and `DUA_ALLOWED_EMAIL` on the server. Local cache access does not bypass that lock.

## Run sample conversations without integrations

The debug build includes a separate `UiPreviewActivity`, using only synthetic fixtures and the same production chat/onboarding components:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dua.ai/.preview.UiPreviewActivity
```

Use Home, Chat, Error and Welcome plus the Light/Dark switch. A persistent **UI preview · sample data · no backend** banner distinguishes these fixtures. Sending/retrying displays a preview notice; it never generates an AI response or contacts a backend. Real account, voice and picker actions direct you to the normal app. The preview neither loads private chats nor creates an account/ViewModel. It is excluded from release source/manifest; the normal launcher remains unchanged.

The default native UI tests render production components with synthetic fixtures, separately from the preview activity. Run `bash ./gradlew :app:testDebugUnitTest -Pdua.ui-record --no-daemon --max-workers=2` from the repository root and inspect `app/build/approved-ui/*.png`. The flag enables PNG recording; without it, the tests still run. Historical screenshot classes are excluded unless `-Pdua.screenshots` is supplied. See [README.md — Android and routine checks](README.md#android-and-routine-checks) for both commands, report locations and CI scope. Recorded PNGs are not proof of physical-device or pixel-baseline verification.

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
| Images gallery, Library, AI Agent, Work, Plugins and other missing settings destinations | Coming soon as listed in [APPROVED_UI_IMPLEMENTATION.md](APPROVED_UI_IMPLEMENTATION.md) |
| Email login/signup and existing-account password setup | Implemented under Account (and Continue with email on Welcome when configured); owner-only, mandatory email confirmation and provider/SMTP setup required. Passwords are transient; password reset/recovery UI remains pending. |
| Unrestricted online guest mode | Outside the owner-only access model |

Code/build/render verification is not proof of live provider, account, Storage, permission or physical-device behavior. Check these with [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md) and the owner's installed release APK.

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
