# Third-party notices

Dua AI is licensed under the GNU GPL, version 3 or (at your option) any later version (GPL-3.0-or-later, see [LICENSE](LICENSE)); it was MIT-licensed before it included Telegram code. Some source files contain code adapted from
the open-source projects below. Each adapted file keeps a header naming its source, the original
copyright and the license. Only code was reused: no logos, brand art, images, videos or fonts.

## Androidify

- Source: <https://github.com/android/androidify> (read through the fork
  <https://github.com/KRFbrothers/androidify>, commit `a4be10fe`)
- Copyright 2025 The Android Open Source Project
- License: Apache License, Version 2.0. Full text: [third_party/androidify/LICENSE](third_party/androidify/LICENSE)
  (also <https://www.apache.org/licenses/LICENSE-2.0>)
- The upstream project has no NOTICE file.

| Dua file | Adapted from (Androidify) | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/attachments/DroppedFiles.kt` | `data/.../DropBehaviourFactory.kt` | Any file type, up to three items, byte cap, original names, private cache folder |
| `app/src/main/java/com/dua/ai/ui/chat/ChatDropTarget.kt` | `data/.../DropBehaviourFactory.kt`, `feature/creation/.../PhotoPrompt.kt` | Whole-chat drop target, text drops, Dua provider URIs rejected, Dua overlay |
| `app/src/main/java/com/dua/ai/diagnostics/DuaLog.kt` | `app/.../CrashlyticsTree.kt` | No Timber; release-only; never sends message text; per-kind cap |
| `app/src/main/java/com/dua/ai/ui/components/PressScale.kt` | `core/theme/.../components/ScaleIndication.kt` | Shrinks instead of grows, keeps the ripple, follows "Remove animations" |
| `app/src/main/java/com/dua/ai/ui/components/Shimmer.kt` | `core/theme/.../transitions/LoadingShimmerOverlay.kt` | Dua colours, combined with Dua's pulse, follows reduce-motion |
| `app/src/main/java/com/dua/ai/ondevice/GeminiNanoTextModel.kt` | `data/.../GeminiNanoDownloaderImpl.kt`, `data/.../GeminiNanoGenerationDataSource.kt` | No Remote Config or Hilt, AICore presence check, failures become "unsupported"/null, no logging of prompts |
| `app/src/main/java/com/dua/ai/ondevice/MlKitBackgroundRemover.kt` | `core/network/.../ondevice/LocalSegmentationDataSource.kt` | No Hilt or Timber, Play services check, download timeout, segmenter closed after use, Dua error messages |
| `app/src/main/java/com/dua/ai/ui/screens/AppPausedScreen.kt` | `feature/home/.../AppInactiveScreen.kt` | Dua calligraphy logo (no Android robot), server message, Try again button, no top bar |
| `app/src/main/java/com/dua/ai/ui/components/ColorSplash.kt` | `core/theme/.../transitions/ColorSplashTransition.kt` | Circle grows from the tap point over Home for Voice mode / Imagine, Dua colours, instant when animations are off |

The adapted portions keep their Apache License 2.0 notices; Apache-2.0 is compatible with GPL-3.0, so they are distributed as part of the GPL-3.0-or-later work. "Android" is a trademark of Google LLC; Dua does not use the Android robot or
other Google brand assets.

## Android AI samples

- Source: <https://github.com/android/ai-samples> (fork <https://github.com/KRFbrothers/ai-samples>)
- Copyright 2025 The Android Open Source Project
- License: Apache License, Version 2.0 (per-file headers; same Apache-2.0 text as [third_party/androidify/LICENSE](third_party/androidify/LICENSE))

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ondevice/WritingTools.kt` | `samples/genai-summarization/.../GenAISummarizationViewModel.kt`, `samples/genai-writing-assistance/.../GenAIWritingAssistanceViewModel.kt` | Same check → download → run → result/error flow, but on Dua's existing ML Kit GenAI Prompt client (no extra Summarization/Rewriting libraries), Dua prompts, output clean-up, visible server fallback |

## Jetchat and Reply (Compose samples)

- Source: <https://github.com/android/compose-samples> (fork <https://github.com/KRFbrothers/compose-samples>), `Jetchat/` and `Reply/`
- Copyright 2020 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/chat/JumpToLatest.kt` | `conversation/JumpToBottom.kt`, `conversation/Conversation.kt` (same-author grouping) | Only the same-author grouping and new-replies count remain; the button is now Telegram's page-down (`TelegramPageDown.kt`) |
| `app/src/main/java/com/dua/ai/ui/chat/WideLayout.kt` | `Reply/.../ui/ReplyApp.kt`, `Reply/.../ui/navigation/ReplyNavigationComponents.kt` (fork commit `0bc770fd`) | Width from `BoxWithConstraints` instead of the window-size-class library; only switches chat history between a permanent side panel and the existing modal drawer |
| `benchmark/build.gradle.kts`, `benchmark/src/main/java/com/dua/ai/benchmark/*.kt` | `benchmarks/build.gradle.kts`, `benchmarks/.../startup/StartupBenchmark.kt`, `.../baselineprofile/StartupBaselineProfile.kt`, `.../GeneralActions.kt` | Dua journeys (onboarding skip, drawer, chat, scroll, send with airplane mode), connected phone only, no managed emulator |

## Now in Android

- Source: <https://github.com/android/nowinandroid> (fork <https://github.com/KRFbrothers/nowinandroid>, commit `a49ed253`)
- Copyright 2022 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/network/NetworkMonitor.kt` | `core/data/.../util/ConnectivityManagerNetworkMonitor.kt`, `NetworkMonitor.kt` | No Hilt or tracing, default network only, requires a validated connection, repeated values filtered |
| `app/src/main/java/com/dua/ai/ui/chat/WideLayout.kt` | `app/.../ui/NiaApp.kt` (adaptive shell: permanent navigation on expanded widths) | Same expanded-width rule, applied to Dua's chat history panel |

## Android snippets

- Source: <https://github.com/android/snippets> (fork <https://github.com/KRFbrothers/snippets>, commit `c31bceff`)
- Copyright 2024 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/components/PredictiveBack.kt` | `compose/snippets/.../predictiveback/PredictiveBackSnippets.kt` (`PredictiveBackHandler` example) | Reusable progress state and a small scale/fade, no motion when animations are off |

## SociaLite

- Source: <https://github.com/android/socialite> (fork <https://github.com/KRFbrothers/socialite>, commit `d7385bbe`)
- Copyright 2023 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/entry/ChatShortcuts.kt` | `app/.../repository/NotificationHelper.kt` (`Contact.toShortcut`, `pushShortcut`) | Dua chats instead of contacts, no Person/LocusId/bubbles/notifications, Dua icon, max four chats, launcher actions, deleted chats removed |
| `app/src/main/res/xml/sharing_shortcuts.xml` | `app/src/main/res/xml/sharing_shortcuts.xml` | Dua activity and share category |

## Vosk offline speech recognition

- Library: `com.alphacephei:vosk-android:0.3.75` from Maven Central (binary AAR with `libvosk.so`; not copied source).
  Source: <https://github.com/alphacep/vosk-api>. Copyright Alpha Cephei Inc. License: Apache License, Version 2.0.
- It depends on JNA `net.java.dev.jna:jna:5.18.1` (binary AAR), dual-licensed Apache License 2.0 or LGPL 2.1; Dua uses it under
  Apache 2.0. <https://github.com/java-native-access/jna>
- The integration (`voice/offline/VoskRecognizer.kt`) follows the usage in the official sample
  <https://github.com/alphacep/vosk-android-demo> (Apache 2.0); no demo source was copied, and its ProGuard keep rules for JNA are
  reused in `app/proguard-rules.pro`.
- Speech models are **not bundled**. The user downloads one from <https://alphacephei.com/vosk/models> in Settings › Voice.
  The models Dua offers are listed there as **Apache 2.0**: `vosk-model-small-hi-0.22` (Hindi),
  `vosk-model-small-en-in-0.4` (Indian English), `vosk-model-small-en-us-0.15` (US English). Size and MD5 come from the
  official `model-list.json`. Vosk publishes no small Urdu model, so Urdu is not offered.

## Google ML Kit and Google Play services libraries

The Android app depends on `com.google.mlkit:genai-prompt` and
`com.google.android.gms:play-services-mlkit-subject-segmentation` as binary Maven libraries (not copied source). They are
provided by Google under the ML Kit terms (<https://developers.google.com/ml-kit/terms>) and the Android SDK / Google APIs
terms; their bundled open-source licenses are listed by Google Play services at runtime. Dua uses only the plain text label
"On-device" for these features and no Gemini or Google brand art.

## Telegram for Android

- Source: <https://github.com/DrKLO/Telegram>, commit `68c6775ac373f4bdb30ae9986282dd5ec0407756`
- Copyright Nikolai Kudashov, 2013-2018 (and Telegram contributors)
- License: GNU GPL v. 2 **or later** (stated in Telegram's source file headers, e.g. `ChatMessageCell.java`,
  `ActionBarPopupWindow.java`; the repository LICENSE file is the GPLv2 text). Dua uses the "or later" option and
  is distributed under GPL-3.0-or-later.
- Java/Android Views code translated to Kotlin/Jetpack Compose. Each Dua file carries a GPL header and an
  "Adapted from Telegram <path> (commit)" line. No Telegram logos, icons, stickers or other brand art are used.

| Dua file | Adapted from (Telegram, `TMessagesProj/src/main/java/org/telegram/`) | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramInterpolators.kt` | `ui/Components/CubicBezierInterpolator.java`, `ui/recyclerview/ChatListItemAnimator.java` (`DEFAULT_INTERPOLATOR`, `DEFAULT_DURATION`) | Compose `Easing` instead of `Interpolator` |
| `app/src/main/java/com/dua/ai/ui/chat/ChatSwipeReply.kt` | `ui/ChatActivity.java` (chat list swipe tracking: 0.4 cm start, `|dx|/3 > dy`, dp(80) clamp, dp(50) haptic/reply, 180 ms decelerate return, `drawReplyButton` progress), `ui/recyclerview/ChatListItemAnimator.java` (`animateAddImpl`) | Compose pointer input; mirrored to a right swipe; reply opens Dua's quote preview |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramMessagePopup.kt` | `ui/ActionBar/ActionBarPopupWindow.java` (`startAnimation`, `startChildAnimation`, `setBackScaleY`, `dismiss`) | Compose `Popup`; Dua colours and actions; "More" opens Dua's full actions sheet |
| `app/src/main/java/com/dua/ai/ui/history/TelegramDialogSwipe.kt` | `ui/DialogsActivity.java` (`SwipeController`: 0.45 threshold, no fling, 200 ms cancel), `ui/Cells/DialogCell.java` (swipe background, reveal circle 300 ms accelerate, icon `BounceInterpolator` 170 ms, haptic on threshold change), `ui/Components/UndoView.java` (5 s undo countdown) | Compose draggable row; left = Delete (with Undo), mirrored right = Pin/Unpin; Dua colours and Material icons |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramPageDown.kt` | `ui/Components/chat/layouts/ChatActivitySideControlsButtonsLayout.java` (page-down visibility: alpha, scale 0.7→1, 80 dp rise, 280 ms decelerate, counter visibility), `ui/Components/chat/buttons/ChatActivityBlurredRoundPageDownButton.java` (56 dp round button, counter on top), `ui/ChatActivity.java` (`newUnreadMessageCount`) | Compose; no blur; counts Dua replies below the viewport; replaces the Jetchat "Latest reply" pill |
| `app/src/main/java/com/dua/ai/ui/components/TelegramThemeSwitch.kt` | `ui/LaunchActivity.java` (`needSetDayNightTheme`: window snapshot, circular reveal from the tap, `finalRadius`, 400 ms), `ui/Components/Easings.java` (`easeInOutQuad`) | Compose `GraphicsLayer` snapshot + clip path instead of `ViewAnimationUtils`; no sun Lottie; off when animations are off |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramImageReveal.kt` | `messenger/ImageReceiver.java` (150 ms crossfade only for non-memory-cache loads) | Compose modifier; adds the roadmap's 25 dp → 0 blur over 500 ms (Android 12+) |

Apache-2.0 and MIT dependencies remain compatible with GPL-3.0-or-later. Note: closed-source Google Play services /
ML Kit binaries are not GPL-compatible "system libraries" in a strict reading; see the PR discussion.

## ADK engine dependencies

The Python engine imports these open-source libraries as packages; their source
is not vendored into Dua. Their licenses remain applicable to distributed
packages/images; see the installed distributions for full license texts.

| Component | Source / license | Use |
| --- | --- | --- |
| Google ADK Python v2.12.0 | [google/adk-python](https://github.com/google/adk-python/tree/v2.12.0), Apache-2.0 | Runtime, sessions, typed tools and model adapters |
| Google Gen AI Python | [googleapis/python-genai](https://github.com/googleapis/python-genai), Apache-2.0 | Vertex model/embedding API client |
| SQLAlchemy | [sqlalchemy/sqlalchemy](https://github.com/sqlalchemy/sqlalchemy), MIT | SQL persistence |
| asyncpg | [MagicStack/asyncpg](https://github.com/MagicStack/asyncpg), Apache-2.0 | Async PostgreSQL driver |
| Psycopg | [psycopg/psycopg](https://github.com/psycopg/psycopg), LGPL-3.0 | Sync PostgreSQL driver; binary wheel includes separate libpq notices |
| pgvector / pgvector-python | [pgvector](https://github.com/pgvector/pgvector), PostgreSQL License; [Python client](https://github.com/pgvector/pgvector-python), MIT | Vector storage and SQLAlchemy integration |
| LiteLLM open-source Python SDK | [BerriAI/litellm](https://github.com/BerriAI/litellm), MIT | Official ADK adapter's multi-provider client |

Exact Python dependency closures are recorded in the engine lock files. The
Docker PostgreSQL image is a disposable CI fixture; production database setup is
separate. No proprietary app code, logos or fonts are added by these integrations.
