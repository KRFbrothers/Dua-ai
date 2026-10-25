# Third-party notices

Dua AI is licensed under the GNU GPL, version 3 or (at your option) any later version (GPL-3.0-or-later, see [LICENSE](LICENSE)); it was MIT-licensed before it included Telegram code. Some source files contain code adapted from
the open-source projects below. Each adapted file keeps a header naming its source, the original
copyright and the license. Only code was reused: no logos, brand art, images or videos. Bundled open-licensed fonts are listed under [Fonts](#fonts).

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
| `app/src/main/java/com/dua/ai/ui/AppPausedScreen.kt` | `feature/home/.../AppInactiveScreen.kt` | Dua calligraphy logo (no Android robot), server message, Try again button, no top bar |
| `app/src/main/java/com/dua/ai/ui/components/ColorSplash.kt` | `core/theme/.../transitions/ColorSplashTransition.kt` | Circle grows from the tap point over Home for Voice mode / Imagine, Dua colours, instant when animations are off |
| `app/src/main/java/com/dua/ai/ui/chat/ComposerGlass.kt` | `core/theme/.../components/AnimatingGradientBrush.kt` (reused through `AgentEdgeGlow.kt`) | Faint blue-purple-pink-orange gradient behind the chat composer; animates only while resumed, with animations on and battery saver off |

The adapted portions keep their Apache License 2.0 notices; Apache-2.0 is compatible with GPL-3.0, so they are distributed as part of the GPL-3.0-or-later work. "Android" is a trademark of Google LLC; Dua does not use the Android robot or
other Google brand assets.

## Android AI samples

- Source: <https://github.com/android/ai-samples> (fork <https://github.com/KRFbrothers/ai-samples>)
- Copyright 2025 The Android Open Source Project
- License: Apache License, Version 2.0 (per-file headers; same Apache-2.0 text as [third_party/androidify/LICENSE](third_party/androidify/LICENSE))

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ondevice/WritingTools.kt` | `samples/genai-summarization/.../GenAISummarizationViewModel.kt`, `samples/genai-writing-assistance/.../GenAIWritingAssistanceViewModel.kt` | Same check → download → run → result/error flow, but on Dua's existing ML Kit GenAI Prompt client (no extra Summarization/Rewriting libraries), Dua prompts, output clean-up, visible server fallback |

## ADK samples (llm-auditor)

- Source: <https://github.com/google/adk-samples> at commit `be63596f7eaeae0858c6a0746f48ed22429d1124`
- Copyright 2026 Google LLC
- License: Apache License, Version 2.0

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `apps/ai-engine/agent_adk.py` (reviewer step) | `contrib/kotlin/llm-auditor/.../LlmAuditorAgent.kt` (critic then reviser in a SequentialAgent) | Python, Dua prompts, JSON verdict, reviser runs at most once and only on `needs_fix`, behind `DUA_AGENT_REVIEW` |

## Jetchat and Reply (Compose samples)

- Source: <https://github.com/android/compose-samples> (fork <https://github.com/KRFbrothers/compose-samples>), `Jetchat/` and `Reply/`
- Copyright 2020 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/chat/JumpToLatest.kt` | `conversation/JumpToBottom.kt`, `conversation/Conversation.kt` (same-author grouping) | Only the same-author grouping and new-replies count remain; the button is now Telegram's page-down (`TelegramPageDown.kt`) |
| `app/src/main/java/com/dua/ai/ui/chat/ComposerGlass.kt` (used by `ChatComposer.kt`) | `Jetchat/.../conversation/UserInput.kt` (fork commit `0bc770fd`: focus scale pop, translucent rounded input card) | Smaller pop (1.02), Dua colours, opaque elevated backing with faint gradient tint, no mesh glow, instant when animations are off |
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

## Agent edge glow + step spinner

| Dua file | Adapted from | Licence |
|---|---|---|
| `app/src/main/java/com/dua/ai/ui/components/AgentEdgeGlow.kt` | Androidify `core/theme/.../theme/components/AnimatingGradientBrush.kt` (https://github.com/android/androidify, commit a4be10f), Copyright 2025 The Android Open Source Project | Apache-2.0 |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramRadialProgress.kt` | Telegram `TMessagesProj/src/main/java/org/telegram/ui/Components/RadialProgressView.java` (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), Copyright Nikolai Kudashov 2013-2018 | GPL-2.0-or-later (used under GPL-3.0-or-later) |
## Animation runtimes and assets

Libraries (binary Maven dependencies, not vendored):

| Library | Version | License | Use |
| --- | --- | --- | --- |
| [rive-android](https://github.com/rive-app/rive-android) (`app.rive:rive-android`) | 10.4.5 | MIT, Copyright (c) 2020-2024 Rive | Chat "thinking" loader |
| [lottie-android](https://github.com/airbnb/lottie-android) (`com.airbnb.android:lottie-compose`) | 6.7.1 | Apache-2.0, Copyright Airbnb | Animated Settings row icons |

Bundled animation files (no logos or brand art; each has a static fallback):

| Dua file | Source | Author | License |
| --- | --- | --- | --- |
| `app/src/main/res/raw/rive_thinking_dots.riv` | [rive-android `app/src/main/res/raw/progress.riv`](https://github.com/rive-app/rive-android/blob/c8e596cd00550f3f7bc344f681a0f5fb7ac650c0/app/src/main/res/raw/progress.riv) (commit `c8e596cd`) | Rive (sample asset, added by Maxwell Talbot, 2021) | MIT (rive-android repository licence, Copyright (c) 2020-2024 Rive) |
| `app/src/main/assets/lottie/settings.json`, `notification.json`, `microphone.json`, `shield.json`, `lock.json`, `database.json` | [useAnimations/react-useanimations `icons/`](https://github.com/useAnimations/react-useanimations/tree/035f164df779da512f06f1de4b0b59f7bf5624ba/icons) (commit `035f164d`) | useAnimations (https://useanimations.com) | MIT, Copyright (c) 2018-2026 useAnimations |

MIT License text (applies to the Rive and useAnimations items above with their copyright lines):

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
> documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
> rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
> persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice
> and this permission notice shall be included in all copies or substantial portions of the Software.
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
> WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
> COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
> OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Fonts

Bundled unchanged for offline use; full license texts ship inside the APK under `app/src/main/assets/licenses/`.

| Font | Dua file | Source | License |
| --- | --- | --- | --- |
| Google Sans (variable, Home only) | `app/src/main/res/font/home_google_sans_variable.ttf` | <https://github.com/google/fonts/tree/47d54ca1ebb14fd864695f1d39dc734f39017c33/ofl/googlesans> (`GoogleSans[GRAD,opsz,wght].ttf`, SHA-256 `d0a87d835a944b8b40d0e82a5651bb59ab97b936a2aeed5946eb57e7b2a3a90a`) | SIL Open Font License 1.1, Copyright 2025 The Google Sans Project Authors. [GoogleSans-OFL.txt](app/src/main/assets/licenses/GoogleSans-OFL.txt), [GoogleSans-NOTICE.txt](app/src/main/assets/licenses/GoogleSans-NOTICE.txt) |
| Inter | `app/src/main/res/font/inter_*.ttf` | <https://github.com/rsms/inter> | SIL Open Font License 1.1. [Inter-OFL.txt](app/src/main/assets/licenses/Inter-OFL.txt) |

"Google" and "Google Sans" are trademarks of Google LLC (see
[GoogleSans-TRADEMARKS.md](app/src/main/assets/licenses/GoogleSans-TRADEMARKS.md)). The font is used only as a
typeface; Dua does not use Google logos or brand assets, and the font is not modified or re-branded.

## Dua OG Assets: generation backdrop and attachment icons

Selected sources were reused from [KRFbrothers/Dua-OG-Assets-](https://github.com/KRFbrothers/Dua-OG-Assets-/tree/f11b3c4f2f998665377079cdb7517a456bd8d502), collection commit `f11b3c4f2f998665377079cdb7517a456bd8d502`.
Each component retains its original license; the collection is not treated as having a blanket license.

- `app/src/main/java/com/dua/ai/ui/chat/BlobDrawable.java` adapts
  `code/telegram/org/telegram/ui/Components/BlobDrawable.java` (source blob
  `d2519fab2b7916e1cc3861fbdd4671dddde451f9`), originally
  [Telegram for Android](https://github.com/DrKLO/Telegram/blob/f32ffeec763f0bdbf4bec25b52d654f77bff733d/TMessagesProj/src/main/java/org/telegram/ui/Components/BlobDrawable.java).
  Telegram contributors; GPL-2.0-or-later, used under Dua's GPL-3.0-or-later.
  Changes: package, deterministic random seed, removal of Telegram-specific LiteMode guards.
  Original blob geometry and interpolation remain. Dua's Compose host supplies the palette,
  drawing position, generation state, lifecycle and reduced-motion handling.
  The original license ships as [DuaOG-Telegram-LICENSE.txt](app/src/main/assets/licenses/DuaOG-Telegram-LICENSE.txt).
- `og_photo_camera.xml`, `og_photo_library.xml`, `og_upload.xml`, and `og_graphic_eq.xml`
  under `app/src/main/res/drawable/` come from the collection's
  `icons/material-symbols/symbols/android/{name}/materialsymbolsoutlined/{name}_24px.xml`.
  Original Google Material Symbols, Apache-2.0; path data retained unchanged.
  Removed only the root Android theme tint so Dua's existing light/dark icon colors apply.
  License: [MaterialSymbols-LICENSE.txt](app/src/main/assets/licenses/MaterialSymbols-LICENSE.txt).

The generation background is a Dua integration of Telegram's open-source geometry, not
proprietary Google Gemini UI code. Existing Dua branding, fonts and Live icon are reused
without adding duplicate copies. No RikkaHub/AGPL source module is bundled by this change.
