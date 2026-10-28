# Android UI audit status

Historical audit below. The owner-approved 2026-10-28 UI replacement supersedes
the archived-Home decision; see [current shell ownership](LEGACY_CHAT_SHELL.md).
Current replacement validation is recorded in `tasks/todo.md`.

Checked on 2026-10-26 against main
`9514a1863906f0cb8abc957c860811db647371ba` (through PR453). Since PR446 the
following were merged: PR447 (owner-requested removal of the archived parallel
shell documented below), PR448 (unused HomeTaglineCompactStyle token removed),
PR451 (live VoiceOrb renamed to LiveVoiceWaveformOrb), PR452 (Task Home switch
sets agentStartAfterId and closes the sheet) and PR453 (Home tagline docs synced
with HomeTaglineQuietStyle + Google Sans).
This is the follow-up to the owner's `Dua_AI_Android_Design_UI_Audit.md`.
The confirmed-safe cleanup and ownership review are complete. Lint, the opt-in
screenshot package and owner phone acceptance remain unverified; this is not a
claim that every feature is complete.

## Audit findings and decisions

| Original finding | Current result |
| --- | --- |
| Duplicate 1: two Home shells | Original Dua logo + Chat/Task Home remains live. The archived SuppliedChatUi implementation, opt-in/style flags, parallel bubbles/composer and shimmer are now deleted. Wiring/screenshot tests are renamed and adapted; no test cases deleted. |
| Duplicate 2: colour classes | Keep live semantic/component token contracts. The unused DuaSemanticColors adapter was removed after its alternate-shell callers were deleted; no live palette/default changes. |
| Duplicate 3: Projects flows | PR444 removed the unreachable modal call and unused wrapper. Shared list/editor compatibility and live Tasks/Memory/planner content remain. |
| Duplicate 4: Voice screens | Dictation review, hands-free and realtime Live Voice serve different modes. Keep. |
| Duplicate 5: settings/page/backup cards | PR445 shared the identical settings-card container. Page surfaces and backup rows/scaffolding keep their distinct contracts. |
| Duplicate 6: loading variants | Removed only the unused alternate-shell shimmer. ReplyPlaceholder still delegates to TypingIndicator; orb fallback, progress/thinking and shared media shimmer remain live. |
| Duplicate 7: logo formats | Keep artwork for its distinct onboarding, voice, drawer and theme placements. |
| Duplicate 8: XML asset sets | Different fills, dimensions/viewports or shapes prevent treating complete resources as duplicates. Keep. |
| Duplicate 9: camera/wave pairs | Different geometry/bar counts and roles. Keep widget/composer/locked-sheet resources. |
| Duplicate 10: press modifiers | Different indication/draw versus artwork graphicsLayer contracts. Keep existing behavior. |
| Duplicate 11: thin wrappers | Separate route/component contracts remain; JumpToLatest also owns MessageGrouping rules. File size is not deletion evidence. |
| Outdated 1: nine Supplied colour constants | Removed in PR435. |
| Outdated 2: theme getters/backing colours | Three getters and four backing values removed in PR435. |
| Outdated 3: legacyFileChipNameMax | Still used by UserMessage filename chips. Keep. |
| Outdated 4: old token descriptions | ChatTokens wording corrected in PR440; PageParts comments now describe the actual Material/PageTheme implementation. No colour or token values changed. |
| Outdated 5: old Android paths | Current documented commands use apps/android. |
| Outdated 6: accents/test counts | Seven presets including Copper. Existing Light Purple/Dark Orange defaults stay. Old counts are historical; dated evidence is below. |
| Outdated 7: HTML prototypes | Preserved design references; their README now reflects the restored native Home. HTML claims are not native verification. |
| Outdated 8: Coming soon destinations | Product backlog remains: Search settings, Edit profile photo, Privacy, Sharing, Permissions, Screenshare/Video, plus Work and the separate Computer placeholder. These require explicit feature scope. |

Details: [Home/Projects ownership](LEGACY_CHAT_SHELL.md) and
[settings/loading ownership](COMPONENT_OWNERSHIP.md). PR440 also corrected the
documentation/KDoc and PR441 fixed narrow-screen drawer/Voice clearance. The
restored Home retains that conservative clearance and its pointer regression.
The four locked sheet/composer files were not edited by these follow-ups
(`git diff f32cd8a..9514a18` shows no changes to them through PR453).

## Verified evidence

- Main [Project checks](https://github.com/KRFbrothers/Dua-ai/actions/runs/40334168549): Android, Backend, Website and engine jobs passed.
- Downloaded main [Android reports/renders](https://github.com/KRFbrothers/Dua-ai/actions/runs/40334168549/artifacts/12656846559): 1,716 tests, zero failures/errors/skips; 528 recorded PNGs.
- Main [personal APK build](https://github.com/KRFbrothers/Dua-ai/actions/runs/40335353824): passed. This does not establish installation, sign-in or phone acceptance.
- PR445 [verification](https://github.com/KRFbrothers/Dua-ai/actions/runs/40332641111): all 28 settings/account/appearance/backup/personality/accent captures were byte-identical to the previous green artifact. Of 528 total captures, one untouched Retry-icon animation frame differed within 6×6 pixels.

The evidence above describes the pre-removal source snapshot; it does not claim
verification of this removal. Default CI runs the unit/UI suite
with `-Pdua.ui-record`; it excludes `screenshots/**` and does not run lint.
Recorded fixture PNGs do not establish pixel-baseline, phone or live-service acceptance.

## Remaining validation — not passed yet

Local Gradle startup failed before project configuration: network-interface/socket
creation is not permitted in this execution sandbox, so FileLockContentionHandler
could not determine a usable wildcard IP. A separate permission-enabled attempt
did not start (no Gradle log was created). Neither event is a lint/test result.
No CI/build files were changed to bypass this restriction.

Run from the repository root in Android Studio's terminal or another configured
machine (JDK 17, Android SDK 35/build-tools 35.0.0):

```sh
bash apps/android/gradlew -p apps/android :app:lintDebug --no-daemon --max-workers=2
bash apps/android/gradlew -p apps/android :app:testDebugUnitTest -Pdua.screenshots -Pdua.ui-record --no-daemon --max-workers=2
```

Inspect `apps/android/app/build/reports/lint-results-debug.html`,
`apps/android/app/build/reports/tests/testDebugUnitTest/index.html`,
`apps/android/app/build/outputs/roborazzi/` and `apps/android/app/build/approved-ui/`.
Record each command's actual result before replacing the unverified status.
Recording screenshots is separate from comparison against approved baselines.

On the usual signed APK, the owner still needs to check Home Chat, Task starter
draft/send, Work from the drawer, profile/model/history/search, dictation/live voice,
Images and Project chat. With the drawer open, one tap over the Voice target should
dismiss it and a second should open Voice mode. Check TalkBack, large text, keyboard
insets and reduced motion on the phone; use the existing
[release checklist](../RELEASE_CHECKLIST.md) for account/live-service acceptance.
Keep the working APK until the restored version is accepted. No backend deploy,
data migration, signing change or switch/default change is required by this cleanup.
