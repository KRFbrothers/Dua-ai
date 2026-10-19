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

The adapted portions stay under the Apache License 2.0; the rest of Dua stays under MIT. "Android" is a trademark of Google LLC; Dua does not use the Android robot or
other Google brand assets.

## Android AI samples

- Source: <https://github.com/android/ai-samples> (fork <https://github.com/KRFbrothers/ai-samples>)
- Copyright 2025 The Android Open Source Project
- License: Apache License, Version 2.0 (per-file headers; same Apache-2.0 text as [third_party/androidify/LICENSE](third_party/androidify/LICENSE))

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ondevice/WritingTools.kt` | `samples/genai-summarization/.../GenAISummarizationViewModel.kt`, `samples/genai-writing-assistance/.../GenAIWritingAssistanceViewModel.kt` | Same check → download → run → result/error flow, but on Dua's existing ML Kit GenAI Prompt client (no extra Summarization/Rewriting libraries), Dua prompts, output clean-up, visible server fallback |

## Jetchat (Compose samples)

- Source: <https://github.com/android/compose-samples> (fork <https://github.com/KRFbrothers/compose-samples>), `Jetchat/`
- Copyright 2020 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/chat/JumpToLatest.kt` | `conversation/JumpToBottom.kt`, `conversation/Conversation.kt` (same-author grouping) | Dua colours, 48 dp target, new-replies count with a dot, no slide when animations are off, no backdrop magnifier |

## Now in Android

- Source: <https://github.com/android/nowinandroid> (fork <https://github.com/KRFbrothers/nowinandroid>, commit `a49ed253`)
- Copyright 2022 The Android Open Source Project
- License: Apache License, Version 2.0 (same text as above)

| Dua file | Adapted from | What changed |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/network/NetworkMonitor.kt` | `core/data/.../util/ConnectivityManagerNetworkMonitor.kt`, `NetworkMonitor.kt` | No Hilt or tracing, default network only, requires a validated connection, repeated values filtered |

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
