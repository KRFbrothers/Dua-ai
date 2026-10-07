# Dua AI: gap analysis and build plan for Grok-style features

_Prepared 8 Oct 2026 against `main` at `90708ea` (after PR #63, `chore/project-cleanup`). Summary and phase plan: [ROADMAP.md](../ROADMAP.md)._

**Scope.** Dua is a personal, single-user app for its owner (Fareed): no clients, no other users, no Google Play release, no monetisation. It runs on his own device(s) as a debug or sideloaded APK, and the backend stays locked to his account (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL`). Quotas and caps in this plan exist to protect his keys and spend, not to meter other users.

**Baseline.** All findings refer to `main` at `90708ea`; `./gradlew :app:testDebugUnitTest` passed on that commit. File:line references point at that commit and will drift as the code changes.

**API facts.** Every model name, endpoint and price below comes from official documentation checked on 8 Oct 2026 (list in §E). Prices change often, so check them again before building.

**Effort scale** (one developer working with coding agents): **S** ≤ 2 days, **M** ≈ 1 week, **L** ≈ 2–3 weeks, **XL** > 1 month.

---

## 0. What the code actually does today (key facts the plan depends on)

| Area | Evidence (file:line) | Why it matters |
| --- | --- | --- |
| One request in, one JSON reply out (no streaming) | `web/src/core/providers.mjs:122-135`, `web/src/core/gemini.mjs:51-83`, `app/.../network/DuaApiClient.kt:58-106` (reads one JSON `{text}`) | Without streaming, the app can't show thinking live, show long answers as they arrive, or show tool progress. |
| Thinking is thrown away | `gemini.mjs:82` filters out `p.thought`, and no `thinkingConfig` is sent | Reasoning mode for Gemini is mostly a configuration change. |
| Output cap is 2048 tokens on non-Gemini providers | `providers.mjs:117` (`max_tokens: 2048`) and `:120` (`max_output_tokens: 2048`) | Long answers and code get cut off. A reasoning model can use up the whole cap while thinking. |
| Time limits assume a 60 s function | `web/src/server/service-handler.mjs:11` (55 s), `providers.mjs:92,124` (55 s / 45 s), `PROVIDERS.md:52` ("existing 60-second function limit"), and Android `readTimeout(60s)` in `DuaApiClient.kt:109` | Vercel with Fluid compute now allows 300 s by default (800 s max on Pro). There is no `vercel.json`, so production settings are unknown. Reasoning and agentic tool calls need more than 55 s. |
| Grok uses the **legacy** Chat Completions endpoint | `providers.mjs:11` → `https://api.x.ai/v1/chat/completions` | xAI files Chat Completions under "Legacy". `grok-4.7` lists only the Responses API, and the web_search, x_search and code tools are Responses tools. |
| History is limited by message count, not by tokens | `DuaApiClient.kt:38` (`takeLast(39)`), `providers.mjs:54-55` (40 messages, 256 KB, user text up to 8000 chars) | Today's models have 500k–1M-token context windows, so "long memory" is limited by Dua, not by the model. |
| Attachments are small and inline, and only last one turn | `web/src/core/attachments.mjs:1-16` (3 files, 2 MB total, TXT 64 KB, base64 inside the JSON); `ChatViewModel.kt:655,668` (only the filename goes into history, and files are cleared after the reply) | **You can't ask a follow-up question about a PDF**: on the next turn the model only sees "Files: x.pdf". Vercel's 4.5 MB request-body limit also caps inline uploads. |
| Web search sends the raw prompt as one Brave query | `web/src/core/chat-web.mjs:42-53`, `search.mjs:30-34` | The model doesn't choose its own queries, can't search more than once, and can't search X. Sources are added to the reply as a plain-text footer (`chat-web.mjs:75-80`). The server also returns a `sources[]` array, but the client ignores it (`DuaApiClient.kt:104`). |
| Voice uses device speech recognition and TTS, plus one ElevenLabs voice | `voice/NativeVoiceInput.kt:87` and `NativeReplySpeaker.kt:54` (`Locale.getDefault()`), `advanced-integrations.mjs` (fixed `ELEVENLABS_VOICE_ID`, `eleven_multilingual_v2`), `voice/HandsFreeSession.kt` (half-duplex turns) | There is no voice picker, no language picker, no way to interrupt the reply by speaking, and no realtime voice. |
| GitHub access is read-only and only lists repos | `advanced-integrations.mjs` (`list-repositories` only, 30 repos) | There is no file reading, no code execution and no write access. |
| No image or video output anywhere | `ChatMessage.kt` holds text only. `PROVIDERS.md:35` says "image-generation output remain pending" | Imagine needs a new message type and history format. |
| The backend has no automated tests | `FEATURES.md:71`, and `.github/workflows/checks.yml` runs only Android JVM tests | Adding streaming adapters without tests is risky. Node 24's built-in `node:test` is enough. |
| Docs conflict on WebP for Grok vision | `PROVIDERS.md:32` allows opting in WebP for Grok, but docs.x.ai/docs/models says image input is "`jpg/jpeg` or `png`" | Remove WebP from Grok's allowed types, or convert to JPEG first. |

---

## A. Feature status table

| # | Feature | Exists today (quality) | Gap | Approach for this architecture (Android ↔ Vercel; keys stay on the server) | Provider APIs (verified names) | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| 1a | **Normal chat: Q&A, writing, ideas, step-by-step** | Works with 6 provider choices (`providers.mjs`, `gemini.mjs`). It's solid on safety (input validation, stop, retry, sanitized errors). Weak on quality: no streaming, 2048-token cap on 4 providers, plain-text rendering with no markdown or code blocks (`ChatScreen.kt:441-460`). | No streaming. Long answers get truncated. Markdown shows as raw `**` and `#` characters. | Add an opt-in `stream:true` to `/api/chat` that returns SSE (`event: thinking/text/sources/done`). Keep the JSON path for older clients. Raise output limits. Render markdown on Android. | Gemini `gemini-3.8-flash` (generateContent with streaming, or the newer Interactions API); xAI `grok-4.7` via `POST /v1/responses` (`stream:true`); OpenAI `gpt-6.1-sol` / `gpt-6-luna` via Responses | M |
| 1b | **Reasoning mode with visible, collapsible thinking** | None. Gemini 3.x already thinks by default, but `gemini.mjs:82` discards the thoughts. | No mode switch, no thinking UI, no stored thinking, timeouts too short. | Add `mode:"reasoning"` to the request. **Gemini**: `generationConfig.thinkingConfig = {thinkingLevel:"high", includeThoughts:true}`, then split parts with `part.thought:true` into `thinking`. **xAI**: move the Grok adapter to `/v1/responses` with `reasoning:{effort:"high"}` and stream the `response.reasoning_summary_text.delta` events. **OpenAI**: Responses `reasoning` effort. Add `thinking: String?` to `ChatMessage` (version bump in history and cloud codecs, backward compatible). UI: a "Thought for Ns ▸" block above the answer, collapsed by default. | Gemini `thinkingLevel` low/medium/high (3.8 Flash defaults to medium); xAI `grok-4.7` `reasoning.effort` low/medium/high(default)/xhigh, cannot be turned off, returns readable **summaries** (not raw thoughts); OpenAI `gpt-6.1-sol` reasoning low…max | M |
| 1c | **Long memory / large context** | Local opt-in notes: up to 10 × 200 chars, max 3000 chars sent (`providers.mjs:56`, FEATURES.md). Proposals come only from explicit "remember" statements. History is the last 39 messages. | No token-based context budget, no summary of older turns, no automatic memory. | (1) Replace the 39-message limit with a token or byte budget per provider (models allow 500k–1M tokens). Raise the 256 KB history cap, staying under Vercel's 4.5 MB body limit. (2) Keep a rolling summary: when history passes the budget, the server summarizes older turns into a pinned "conversation summary" stored in the chat. (3) Turn on xAI prompt caching with `prompt_cache_key` = chat id. (4) Later: model-proposed memory notes that the user approves. | `grok-4.7` 500k context, cached input $0.50/M; `gemini-3.8-flash`; OpenAI GPT-6 family 1.05M context | M |
| 2a | **Live web search (latest news and facts)** | Brave, one query, 5 snippets; a Read-link mode for one page; a Sources footer as text. Off by default and resets each session (`chat-web.mjs`, `ChatScreen.kt:412-439`). Works, but it's basic: the model doesn't decide when or what to search, and it only sees snippets. | No multi-step searching, no source chips, no automatic mode. | Use each provider's **built-in search tools** and make Brave the fallback: xAI `tools:[{type:"web_search"}]`; Gemini `tools:[{google_search:{}},{url_context:{}}]`; OpenAI `{type:"web_search"}`. Map each provider's citations to a structured `sources[]` and render chips. In "Auto" mode, turn tools on and let the model decide. Needs streaming and longer timeouts. | xAI `web_search` ($5 per 1k calls); Gemini `google_search` / `url_context` (pricing not checked in this pass); OpenAI `web_search`; Brave (existing) | M |
| 2b | **X (Twitter) search of public posts in real time** | None. | Everything. | **Only xAI offers this.** Add `tools:[{type:"x_search", from_date?, allowed_x_handles?}]` on `/v1/responses` whenever Search mode runs with an xAI key. Show X sources as post chips (@handle and date). Grok can do this no matter which chat model is selected: route Search mode through xAI. | xAI `x_search`: **billed per item**, $5 per 1k posts and $10 per 1k profiles (one docs example fetched 44 posts, about $0.22). The old `search_parameters` "Live Search" is deprecated. | S–M once 2a is done |
| 3 | **Files: PDF, image, spreadsheet, code, audio → summary and answers** | JPEG, PNG, WebP, PDF and TXT only; 3 files, 2 MB total, TXT 64 KB; good photo resizing (`attachments/PhotoPolicy.kt`). Files are sent once and forgotten. Grok and Kimi can't take PDFs, and other providers need MIME env flags. | No CSV/XLSX, code, MD, DOCX or audio. Small size limit. **No follow-up questions about a file.** No previews. | **Quick win (S):** accept text-like types inline (`text/csv`, `text/markdown`, `application/json`, common source-code extensions as UTF-8 text), raise the TXT limit to about 512 KB, and keep the total under 3 MB so base64 fits in 4.5 MB. **Full version (L):** a private Supabase Storage bucket `dua-uploads/{user_id}/…` with row-level security. The new endpoint `POST /api/files/sign` returns `createSignedUploadUrl` (token valid 2 h). Android uploads **straight to Supabase**, bypassing Vercel's 4.5 MB limit; files over 6 MB use resumable TUS. Messages carry `fileRefs[]` (path, mime, size, name) that **stay in history**, so follow-ups work. At chat time the server signs a short-lived download URL and passes it to the provider: xAI `{"type":"input_file","file_url":…}` (or `file_id` after `POST /v1/files`), Gemini external or signed URL (≤ 15 MB) or Files API resumable upload (≤ 2 GB, kept 48 h), OpenAI `input_file`. **Spreadsheets:** CSV as text; XLSX converted to CSV on the server, or analyzed with the code tool. **Audio:** transcribe on the server, then summarize. | xAI Files API + `attachment_search` ($5 per 1k); Gemini Files API (2 GB per file, 48 h); STT: xAI `grok-voice-transcribe-2.0` (REST $0.10/h), OpenAI `gpt-transcribe` (`/v1/audio/transcriptions`, ≤ 25 MB), Gemini `gemini-3.5-transcribe`; Supabase `createSignedUploadUrl` / TUS (**Free plan: 50 MB per file**) | S (quick win) + L |
| 4a | **Imagine: text-to-image and image editing** | None. | Image output type, gallery save and share, edit flow, cost gates. | New `POST /api/imagine` with `{prompt, sourceImages?: fileRefs, aspect_ratio?, n?}`. Edit when the user attached an image. The server calls the provider. Its URLs are **temporary**, so the server copies the image into Supabase Storage (or the client downloads it right away) and returns a stored URL. Don't return `b64_json` through Vercel: the 4.5 MB **response** limit applies too. Android: an image message bubble (needs an image loader such as Coil, a new dependency), full-screen viewer, Save to gallery (MediaStore), Share, "Edit this image". Requires owner sign-in and daily image caps (extend `access-policy.mjs`). | xAI `POST /v1/images/generations` and `POST /v1/images/edits` (JSON only, up to 5 source images, `resolution` 1k/2k, `response_format` url/b64_json); `grok-imagine-image-2.0` $0.04 per image (`grok-imagine-image` $0.02). Gemini "Nano Banana 2" `gemini-3.1-flash-image`, `gemini-nano-banana-2.1`, `gemini-3-pro-image`. OpenAI GPT-Image-2.5 (Sunburst / Flare) | M |
| 4b | **Video from text or an image, up to 1080p** | None. | Async jobs, polling, video playback, storage, much higher cost. | Runs as a background job: `POST /api/imagine/video` submits and returns `request_id`; Android polls `GET /api/imagine/video/:id` every 5 s, so no function runs long. When it finishes, copy the MP4 to Storage, since xAI URLs expire. Play it with Media3/ExoPlayer (new dependency). Gate 1080p behind a setting (it costs more), and default to 480p or 720p. | xAI `POST /v1/videos/generations` + `GET /v1/videos/{request_id}` (statuses: done/failed/expired); 1–15 s long; 480p (default), 720p, **1080p only on `grok-imagine-video-1.5` for text- and image-to-video**. Cost: $0.080/s for 1.5 (a 10 s clip ≈ $0.80), $0.020/s for `-1.5-lite`. Gemini alternatives: `veo-3.1-generate-preview`, `veo-3.1-lite-generate-preview`, `gemini-omni-1.1-flash` | M–L |
| 5a | **Voice: multiple voices and languages** | Device STT and TTS follow the phone's locale. One fixed ElevenLabs voice (owner only). | No picker for voice or language. The voice screen has placeholder controls. | Device side (S): a language picker for `RecognizerIntent.EXTRA_LANGUAGE` and TTS `setLanguage`, plus a device voice list from `TextToSpeech.getVoices()`. Server side (S–M): replace or extend ElevenLabs with a `voice` parameter: xAI TTS (`/v1/tts`, voice list from `GET /v1/tts/voices`, default `eve`) or Gemini `gemini-3.8-flash-tts`. | xAI TTS $15 per 1M chars; Gemini 3.8 Flash TTS / Flash-Lite TTS; ElevenLabs (existing) | S–M |
| 5b | **Hands-free realtime voice conversation (interrupt by talking)** | Half-duplex loop: device STT → text chat → device TTS (`HandsFreeSession.kt`), foreground only. | No realtime speech-to-speech, no interrupting, no streaming audio. | The server mints a short-lived token with `POST https://api.x.ai/v1/realtime/client_secrets` (`{"expires_after":{"seconds":300}}`), so the key never reaches the phone. Android opens an OkHttp WebSocket to `wss://api.x.ai/v1/realtime?model=…`, records mic audio with AudioRecord (16 kHz PCM, with AcousticEchoCanceler), sends it, and plays `response.output_audio.delta` with AudioTrack. Turn detection: `server_vad`. The session can also use `web_search` / `x_search`. Needs a microphone foreground service on Android 14+ for background use. | xAI Voice: `grok-voice-latest` / `grok-voice-think-fast-2.0`, $0.08/min, 120-min session cap, 20+ languages. Alternatives: Gemini Live `gemini-3.8-live`; OpenAI GPT-Realtime-2.1 / GPT-Live 1 | L |
| 6a | **Code execution tool** | None. | Everything. | Use the provider's **hosted** sandbox: add the tool when the user enables "Run code" (or in Reasoning and Build modes). Render code that ran and its output as collapsible blocks. Works with attached CSV/XLSX files for data analysis. | xAI `{type:"code_interpreter"}` (also called `code_execution`), $5 per 1k calls; Gemini `tools:[{code_execution:{}}]` (returns `executableCode` and `codeExecutionResult` parts); OpenAI `{type:"code_interpreter", container:{type:"auto"}}` | S–M |
| 6b | **"Build" mode: reads project files, edits code, runs commands** | GitHub connector lists repos only (read-only, owner-restricted). The "Computer mode" button opens a setup page (`ChatScreen.kt:231`). | Everything. **A full agent that runs commands can't run on the phone or inside a 300 s Vercel function.** | A realistic scope in three steps. **(i) Repo-aware coding chat (M):** extend `/api/connectors/github` with read actions (`get-tree` → `GET /repos/{o}/{r}/git/trees/{sha}?recursive=1`, `get-file` → `GET /repos/{o}/{r}/contents/{path}`) and give them to the model as function-calling tools inside a "Build" chat tied to one repo. The model reads files and answers with diffs. **(ii) Approved changes (M–L):** the model proposes a patch; after the user taps "Create PR", the backend creates a branch, commits and opens a PR with a narrowly scoped token. Nothing is written without that tap. **(iii) Running commands (XL / delegate):** either hand the task to a hosted agent (this repo already gets PRs from the GitHub Copilot coding agent: branches `copilot/*` in PRs #56–#61, so Dua could file an issue and assign it), or start a **Vercel Sandbox** microVM (Hobby: 45-min sessions, 10 at a time; about $0.128 per vCPU-hour + $0.0212 per GB-hour). Google also lists a managed sandbox agent, `antigravity-preview-09-2026` (preview). | xAI `grok-4.7` / `grok-build-0.1` with function calling; GitHub REST; Vercel Sandbox; Copilot coding agent | M (i) · L (ii) · XL (iii) |

---

## B. UI review: "clean and professional"

### B1. Concrete problems (with evidence)

**Structure**
1. **`ChatScreen.kt` does far too much.** It's 751 lines and 57 KB, and holds Home, Chat, the model picker sheet, the Web-mode control, Settings, Attachments and Voice, plus speaker and lifecycle wiring (lines 73-409). Business actions live in the UI: clipboard, intents and audio control.
2. **Navigation is hand-built.** A 21-value `Screen` enum (line 68) is stored in `rememberSaveable` strings, with a custom `detailHistory` back stack and special cases (lines 75-152). There's no Navigation Compose, deep links are hard, and back behavior is fragile.
3. **Dead model fields and data.** `ChatMessage.timestamp = "Just now"`, `model = "Dua 2.0 Flash"`, and `hasCode`, `codeLanguage` and `reaction` are never used (`model/ChatMessage.kt`). `promptSuggestions` (`ChatViewModel.kt:554-579`) is defined but never shown.

**Theming and typography**
4. **There is no design system.** No `ui/theme` package (PR #62 removed the old one). `settings/LocalAppearance.kt` sets only 8 color roles, using the stock Material 3 template purples (`#D0BCFF`, `#6750A4`), so the app looks like a template. 7 more hard-coded colors are scattered through `ui/` (for example `0xFFFFB1C0` for Log out and the cube grays).
5. **Type sizes are ad hoc.** `ui/` has 94 hard-coded `fontSize =` values across 12 sizes (12, 13, 14, 16, 18, 20, 22, 24, 25, 28, 34 sp) instead of `MaterialTheme.typography` roles. The composer text is 20 sp but messages are 16 sp (lines 246, 328). Spacing jumps between 12, 14, 16, 18, 20 and 32 dp with no scale.
6. **Brand basics are missing.** The manifest uses the system default icon `@android:drawable/sym_def_app_icon` and the platform theme `Theme.Material.NoActionBar`. There's no splash screen and no adaptive icon.

**Chat surface**
7. **Messages render as plain text.** No markdown (headings, lists, bold, tables), no code blocks, syntax highlighting or copy-code. Sources show up as a raw "Sources:" text footer (`chat-web.mjs:76`).
8. **Bubbles for both sides**, capped at 320 dp (line 244). Long assistant answers and code get squeezed. Modern assistants (Grok, ChatGPT, Gemini) show assistant replies full-width with no bubble.
9. **Header**: the title "I am Dua", and a "Computer mode" icon that actually opens Feature setup (line 231), which is misleading. The header doesn't show the current model or mode.
10. **Empty state**: a large logo (156×273 dp) and nothing else. No greeting and no suggestions.
11. **Loading**: the text "Dua is thinking…" (line 286), with no animation and no progress.
12. **Errors**: muted gray text above the composer (lines 303-307) that's easy to miss. Wording mixes English with Roman Urdu/Hindi ("Dobara try karein", "Attachments total 2 MB se chhote rakhein" in `gemini.mjs`, `attachments.mjs`, `DuaApiClient.kt`).
13. **Message actions**: three 48 dp icon buttons under every reply, plus extra helper text ("Retry keeps web search.", lines 259-261). It's visually noisy.

**Composer and mode switching**
14. The **"Web: Off ▾" dropdown and a "Search setup" link sit at the top of the composer every time** (lines 323-325, 412-439). It's a technical control, not a mode.
15. **Two ways into voice**: a mic icon, and the send button turns into a voice cube when the draft is empty (lines 343-355). The model button says "Model" when Dua is selected (line 340).
16. **"+" opens a full-screen "Add to chat" page** (lines 556-637) instead of a bottom sheet. That page has an "Add to project — Not available" row, a "Web search" row that just goes back to chat, and a paragraph about resize rules. Selected files show as one comma-separated line (line 312) with no thumbnails or remove buttons.
17. The **model picker** lists both "Dua" and "Gemini", which are the same backend key (`providers.mjs:7-8`), and uses letter avatars.

**Voice screen**
18. **Placeholder controls**: Like/Dislike only show "Liked · Preview"; Share and Copy act on the greeting text; three disabled "Camera / Video / Screen sharing coming soon" buttons (lines 675, 697-712, 734-735). It reads as unfinished.

**Settings**
19. **15 rows in 6 groups**, and three of them ("Computer mode", "Capabilities", "Plugins") open **the same** screen (line 505). An "Info" button is disabled "coming soon" (line 496).
20. **"Feature setup" is a developer diagnostics page** shown as a main settings page: a "‹ Back" text button, a "Refresh setup" button, raw "ready/setup required" provider states, and text that still mentions "Android and web" (`IntegrationSetupScreen.kt:35-90`).
21. **Home list**: 20 sp titles, a cube icon on every row, a delete button on every row (easy to tap by accident), and no date grouping. There's no drawer, so history is a separate screen instead of a side panel.

Worth keeping: accessibility is decent (content descriptions, 48 dp touch targets, live regions). Safety and edge-case handling is strong (retry, stop, keeping the draft).

### B2. Proposed UI structure

```
ui/
  theme/      DuaTheme.kt (light/dark ColorScheme from brand tokens), Type.kt (one type scale),
              Shape.kt, Spacing.kt (4-dp scale), Motion.kt
  navigation/ DuaNavHost.kt (Navigation Compose: chat/{id}, settings/*, voice, imagine/viewer)
  chat/       ChatScreen.kt (scaffold only), ChatTopBar.kt (drawer · model+mode title · new chat)
              MessageList.kt, UserMessage.kt (right bubble), AssistantMessage.kt (full width, no bubble)
              MarkdownText.kt + CodeBlock.kt (language label, copy, horizontal scroll)
              ThinkingBlock.kt ("Thought for 12 s ▸", collapsible, streams live)
              SourceChips.kt (favicon/domain chips → sources bottom sheet; X posts show @handle)
              MessageActions.kt (copy · retry · read aloud · share; small icons, 48-dp targets)
              Composer.kt, ModeChips.kt, AttachmentSheet.kt, AttachmentChips.kt (thumbnails + ✕)
              EmptyState.kt (small logo, "What can I help with?", 4 suggestion chips)
  history/    HistoryDrawer.kt (ModalNavigationDrawer: search, Today/Yesterday/Last 7 days, swipe-to-delete with undo)
  imagine/    ImageMessage.kt, MediaViewer.kt, VideoMessage.kt (later phases)
  voice/      VoiceScreen.kt (clean: orb, live transcript, mute, end), VoicePicker.kt
  settings/   SettingsHome.kt + subpages
```

- **Composer:** a multi-line field (16 sp, grows up to 6 lines). Bottom row: `[+]` `[Auto ▾]` chip, a small model chip, a spacer, then `[🎤]` and `[↑ / ■]`. Mode menu: **Auto** (default; the model may search if tools are configured) · **Reasoning** · **Search** (web, plus X when xAI is configured) · **Imagine** (with an attached image it becomes Edit). **Build** is added in Phase 5. A mode that isn't configured shows a short explanation instead of disappearing silently.
- **Attachment sheet (bottom sheet):** Camera · Photos · Files, plus recent files. Selected files appear as chips with thumbnails above the composer. Size rules move into an info line shown only when something fails.
- **Assistant message layout:** model and mode label (small), then ThinkingBlock (collapsed), the answer in markdown, SourceChips, and MessageActions.
- **Errors:** an inline error card on the failed turn, with "Retry", "Switch model" and similar actions. One language for all strings, moved to `strings.xml` so translation becomes possible.
- **Settings regrouped:** **Account** (Profile, Cloud sync, Log out) · **Personalization** (Appearance: theme and font in one page; Voice: voice and language; Memory) · **Data & privacy** (Data controls, Privacy, Sharing, Permissions, Notifications) · **Connections** (GitHub) · **About** (version, licenses, with *Diagnostics* = the old Feature setup underneath).
- Remove the "Computer mode", "Plugins" and "Capabilities" duplicates and every "coming soon" or disabled placeholder.
- Add an adaptive app icon and the AndroidX SplashScreen. Make dark mode the default to match Grok's look (owner decision).
- **Markdown library:** for example `org.commonmark:commonmark` (CommonMark Java) parsing into an `AnnotatedString` renderer plus a custom `CodeBlock`. Choose and pin a version when implementing; libraries were not evaluated in depth.

---

## C. Recommended phased build order

**Phase 0: setup checks only the owner or deployer can do (no code, about 1 hour)**
- In Vercel, confirm Fluid compute is on and check the plan (Hobby: 300 s max; Pro: 800 s max).
- Check the current `GEMINI_MODEL` value. `gemini-2.0-flash` is **shut down**, and 2.5 models are limited to existing users per ai.google.dev/models, so Dua may already be failing if it uses an old ID. Recommended: `gemini-3.8-flash`.
- Decide which provider keys to fund (§D). If xAI is chosen, set `GROK_API_KEY` and `GROK_MODEL=grok-4.7`.

**Phase 1: highest impact, lowest risk; two PRs**

The evidence points to **UI foundation + Reasoning/streaming**. Full file uploads move to Phase 2, because they need a Supabase Storage bucket and migration, a retention decision, and privacy-doc changes. One cheap file improvement (more text types) fits into Phase 1.

- **PR 1, "UI foundation" (Android only, no API change, L):**
  - `ui/theme` tokens and type scale.
  - Split `ChatScreen.kt` into the structure above, and move to Navigation Compose.
  - Markdown and code-block rendering; full-width assistant replies.
  - New composer with mode chips: Auto and Search map to today's `web` modes; Reasoning stays hidden until PR 2.
  - Attachment bottom sheet and chips.
  - SourceChips parsed from the existing footer (`model/ReplyLinks.kt` already finds the links).
  - Empty state using `promptSuggestions`, history drawer, regrouped settings.
  - Remove placeholder and duplicate controls; adaptive icon and splash screen.
  - Keep every existing behavior (stop, retry, draft recovery, sync) and every existing JVM test passing.
  - Risk: low in function, but a large diff. Reduce it by moving code first in one commit and redesigning in a later one.
- **PR 2, "Reasoning mode + streaming + text files" (backend + Android, M):**
  - `/api/chat` accepts `mode:"auto"|"reasoning"` and opt-in `stream:true` (SSE). Without the flag, the JSON response is unchanged, so older APKs keep working.
  - Gemini: `thinkingConfig {thinkingLevel, includeThoughts:true}`.
  - Grok: move from `/v1/chat/completions` to `/v1/responses` with `reasoning.effort` and streamed reasoning summaries.
  - OpenAI: Responses `reasoning` effort. Claude and Kimi: unchanged (no thinking display).
  - Raise output caps to 8k or more, and move timeouts to an env-configurable deadline that fits the real Vercel `maxDuration` (add `vercel.json` `functions.maxDuration`). The Android OkHttp `readTimeout` becomes a gap between streamed bytes, with server heartbeats.
  - `ChatMessage.thinking` is stored in history, cloud snapshot and export, with a version bump that still reads older files.
  - ThinkingBlock UI. Drop WebP from Grok's allowed image types (or convert to JPEG).
  - Quick file win: inline CSV, MD, JSON and code text up to about 512 KB each, with the total still ≤ 3 MB raw.
  - Add `node:test` tests for each adapter's stream parsing and a CI job for `web/`.
  - Risks: Gemini now labels `generateContent` "Legacy" (in favor of the Interactions API), so keep the adapter thin. Reasoning tokens are billed as output tokens. xAI shows summaries, not raw chain-of-thought.

**Phase 2: Files v2 + Live search v2 (L + M)**
- Supabase Storage bucket with row-level security, migration and `/api/files/sign`. Upload straight from Android (TUS for files over 6 MB). `fileRefs` stay in history so follow-ups work.
- XLSX→CSV on the server, DOCX, audio via STT. Provider handoff by signed URL (xAI `file_url`, Gemini external URL ≤ 15 MB or Files API).
- Update PRIVACY.md, which currently promises file bytes are never stored.
- Search: built-in provider tools (xAI `web_search` + `x_search`, Gemini `google_search` + `url_context`, OpenAI `web_search`), structured `sources[]`, X post chips. Brave stays as the fallback.

**Phase 3: Imagine (M, then M–L)**
- `/api/imagine` for image generation and editing (xAI `grok-imagine-image-2.0` or Gemini Nano Banana 2), stored in Supabase.
- Image messages, viewer, save and share. Owner sign-in and daily caps.
- Then async video (`grok-imagine-video-1.5`; 480/720p by default, 1080p gated) with polling and an ExoPlayer bubble.

**Phase 4: Voice v2 (S–M, then L)**
- Voice and language pickers (device plus xAI or Gemini TTS); clean up the voice screen.
- Then realtime speech-to-speech over WebSocket using ephemeral tokens (`/v1/realtime/client_secrets`), with interrupt-by-talking and echo cancellation.

**Phase 5: Coding (S–M, M, then L / XL)**
- The code-execution tool (provider-hosted), useful together with files.
- Then repo-aware "Build" chat with GitHub read tools.
- Then PRs created only after the user approves.
- Running commands only through delegation (Copilot coding agent, or Vercel Sandbox), never on the phone.

Across all phases: keep the owner-only lock on, and raise `access-policy.mjs` from a request count to **per-feature spend caps** (image, video, voice minutes, X posts) before turning on any paid media feature, so the owner's bill stays bounded.

---

## D. Open questions and decisions only the owner (Fareed) can make

Settled: Dua is personal and owner-only (today's `DUA_ALLOWED_EMAIL` model), with no user tiers, Play Billing or store release; file, Imagine and voice features require the owner's sign-in.

1. **Which provider accounts will you fund?**
   - An **xAI API key** is needed for X search, Grok Imagine (image and video), Grok voice, and the closest "Grok-like" behavior. API credits are billed separately from a SuperGrok subscription.
   - Keep Gemini as the default "Dua" brain, switch to Grok, or use both?
   - Keep ChatGPT, Claude and Kimi in the picker, or simplify to 2 providers?
2. **Monthly budget and caps.** For example $X per month of personal spend, with daily limits per feature. Reference prices: grok-4.7 $2/$6 per 1M tokens; image $0.02–0.05 each; video $0.02–0.08 per second; X search $5 per 1k posts; voice $0.08/min.
3. **Vercel and Supabase plans.** Vercel Hobby caps functions at 300 s; Pro allows 800 s and is needed for multi-region. Supabase Free caps files at 50 MB and storage is limited; Pro removes this.
4. **File retention.** Is it OK to store uploads in Supabase Storage? For how long (for example, keep, or auto-delete after 30 days)?
5. **Thinking display.** Show reasoning summaries collapsed by default? (xAI and Gemini return summaries, not raw thoughts.)
6. **Brand and design input.** Do you have a Figma file, palette, typeface (custom font file?), app icon and logo usage rules? Dark-first like Grok? Keep the "cube" motif?
7. **Language.** English-only UI, or Urdu/Hindi too (which needs right-to-left layout)? Today the error strings mix both.
8. **Search.** Keep Brave (another key and bill) as a fallback, or rely on built-in provider search?
9. **Imagine media.** Should generated images and video sync to the cloud? Provider usage policies still apply to generated content.
10. **Build mode scope.** Read-only repo chat only, or allow Dua to open PRs after your approval? Which repos? Is it OK to hand off to the GitHub Copilot coding agent or Vercel Sandbox, each with its own cost?
11. **Voice.** Keep device voice as the free default? Which paid voice provider (xAI, Gemini or ElevenLabs)? Is $0.08/min realtime voice acceptable?

---

## E. Docs checked (8 Oct 2026)

- xAI: docs.x.ai/docs/models and /developers/pricing (models, Imagine, voice and tool prices); /developers/grok-4-7; /developers/model-capabilities/text/reasoning; /developers/tools/overview, /tools/web-search, /tools/x-search; /developers/model-capabilities/files/chat-with-files; /images/generation, /images/editing, REST /inference/images; /video/generation, REST /inference/videos; /audio/speech-to-speech, /audio/speech-to-text, REST /inference/voice, /audio/ephemeral-tokens; /legacy/chat-completions (search summaries).
- Google: ai.google.dev/gemini-api/docs/models; /docs/thinking; /docs/generate-content/thinking (search summary: `thinkingConfig.thinkingLevel`, `includeThoughts`, `part.thought`); /docs/file-input-methods; /docs/files; generate-content google-search, code-execution and url-context pages (search summary).
- OpenAI: developers.openai.com/api/docs/models; /guides/speech-to-text; tools web-search, code-interpreter and image-generation guides (search summary only; image model IDs not confirmed beyond the names "GPT-Image-2.5 Sunburst/Flare").
- Vercel: vercel.com/docs/functions/limitations (4.5 MB request and response body; 300 s default and Hobby max; 800 s Pro); /docs/sandbox and /sandbox/pricing (search summary).
- Supabase: supabase.com/docs/guides/storage/uploads/file-limits (Free 50 MB per file); `createSignedUploadUrl` / `uploadToSignedUrl` and resumable uploads (search summary; token valid 2 h, TUS recommended above 6 MB).
- Not verified in this pass: Anthropic (Claude) extended-thinking parameters; Gemini grounding prices; Kimi capabilities.
