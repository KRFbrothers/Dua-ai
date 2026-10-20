# Third-party notices

Dua AI is MIT-licensed (see [LICENSE](LICENSE)). Some source files contain code adapted from
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

The adapted portions stay under the Apache License 2.0; the rest of Dua stays under MIT. "Android" is a trademark of Google LLC; Dua does not use the Android robot or
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
| `app/src/main/java/com/dua/ai/ui/chat/JumpToLatest.kt` | `conversation/JumpToBottom.kt`, `conversation/Conversation.kt` (same-author grouping) | Dua colours, 48 dp target, new-replies count with a dot, no slide when animations are off, no backdrop magnifier |
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

## Google ML Kit and Google Play services libraries

The Android app depends on `com.google.mlkit:genai-prompt` and
`com.google.android.gms:play-services-mlkit-subject-segmentation` as binary Maven libraries (not copied source). They are
provided by Google under the ML Kit terms (<https://developers.google.com/ml-kit/terms>) and the Android SDK / Google APIs
terms; their bundled open-source licenses are listed by Google Play services at runtime. Dua uses only the plain text label
"On-device" for these features and no Gemini or Google brand art.

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
