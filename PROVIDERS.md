# AI providers and web search setup

Dua and Gemini retain the existing Gemini setup and attachment support. ChatGPT, Claude, Grok and Kimi have server adapters for conversations, attachments, history, retry and optional memory context. Their setup remains inactive until provider credentials and a model ID are configured. This change does not provision keys or create subscriptions.

## Server variables

Add variables to Vercel's server environment or a local ignored `.env` file. API keys must never be added to Android resources, public client configuration or GitHub commits.

| Choice | API key | Model ID |
| --- | --- | --- |
| Dua / Gemini | `GEMINI_API_KEY` | `GEMINI_MODEL` |
| ChatGPT | `OPENAI_API_KEY` | `OPENAI_MODEL` |
| Claude | `ANTHROPIC_API_KEY` | `ANTHROPIC_MODEL` |
| Grok | `GROK_API_KEY` | `GROK_MODEL` |
| Kimi | `KIMI_API_KEY` | `KIMI_MODEL` |

Use an API model ID available to your account, rather than a marketing label such as “Instant” or the app choice “ChatGPT”. Explicit model configuration avoids silently selecting a paid or retired model. Endpoints are fixed to Gemini `generateContent`/`streamGenerateContent`, OpenAI Responses, xAI Responses (`api.x.ai/v1/responses`), Anthropic Messages and Moonshot international chat completions. For Gemini, `gemini-3.8-flash` is a good default `GEMINI_MODEL`; for Grok, `grok-4.7`. Kimi needs credentials for `api.moonshot.ai`, rather than the separate China API. Custom base URLs are unsupported.

The earlier decision to defer OpenAI key setup remains in effect. No key was created, inspected or used during development. Verification uses mocked responses. Provider APIs can have separate billing from consumer chat subscriptions; enable only providers you want.

Redeploy after setting server variables. `/api/config` exposes provider availability, model IDs, `supportsAttachments` and the supported `attachmentTypes` MIME array, without keys. Missing credentials or a model ID leave that provider unavailable. Requests support cancellation, a configurable whole-request deadline (`DUA_CHAT_DEADLINE_SECONDS`, default 280), bounded history/output and sanitized errors. OpenAI and xAI requests use `store: false`. `/api/config` also reports `streaming: true` and, per provider, `reasoning`.

## Streaming, Reasoning and output limits

`/api/chat` keeps its JSON reply unless the request opts in with `"stream": true`; then it answers with Server-Sent Events: `context` (a new conversation summary, sent first), `thinking`, `text`, `sources`, `notice`, `files` (audio transcripts), `done` and `error` (each `data:` is JSON with the same `type`), plus a `: ping` comment every 15 seconds. Validation, login, quota and upstream errors that happen before the first byte still return the usual JSON error and status (including `Retry-After`). If an adapter cannot stream, the reply arrives as one `text` event. Web search and Read link work in both modes; the `Sources:` footer arrives as the final text.

`"mode": "reasoning"` asks the model to think first. Thinking summaries are only sent in Reasoning mode and are saved with the chat (local history, export and cloud snapshot; older files without them still load).

| Choice | Reasoning | Effort variable (default) | Output cap variable (Auto / Reasoning default) |
| --- | --- | --- | --- |
| Dua / Gemini | `thinkingConfig.includeThoughts`; Gemini 3 uses `thinkingLevel`, 2.5 uses a dynamic `thinkingBudget` | `GEMINI_THINKING_LEVEL` (`high`; `low`/`medium`/`high`) | `GEMINI_MAX_OUTPUT_TOKENS` (model default) |
| ChatGPT | Responses `reasoning` with `summary: auto` — needs a reasoning model (`o`-series / GPT-5 family) | `OPENAI_REASONING_EFFORT` (`high`) | `OPENAI_MAX_OUTPUT_TOKENS` (16384 / 32768) |
| Grok | xAI Responses `reasoning.effort`; Grok 4 always reasons | `GROK_REASONING_EFFORT` (`high`) | `GROK_MAX_OUTPUT_TOKENS` (16384 / 32768) |
| Claude | No visible thinking yet; the reply says so | — | `ANTHROPIC_MAX_OUTPUT_TOKENS` (8192) |
| Kimi | Shows `reasoning_content` when the model returns it | — | `KIMI_MAX_OUTPUT_TOKENS` (8192) |

Output caps accept 256–1,000,000; output tokens include thinking tokens. A reply that hits the cap ends with `done.truncated` and the app shows a note. `DUA_CHAT_DEADLINE_SECONDS` (10–790) must stay below the Vercel function duration: `web/vercel.json` sets `maxDuration: 300` for `api/**/*.js` (Hobby with Fluid compute allows 300 s; Pro allows up to 800 s). The Android app allows 75 s between bytes and 330 s per call.

## Attachments

All providers accept UTF-8 text and code files (TXT, CSV, TSV, MD, JSON, XML, YAML, LOG, HTML and common source files), sent to the model as text. For the four optional providers, enable images or PDFs only after confirming that your configured API model accepts that input. The capability variables are comma-separated MIME types; TXT remains available automatically. Unsupported or misspelled types are ignored, and the app and server reject files outside the published list.

| Provider | Capability variable | Available types |
| --- | --- | --- |
| Dua / Gemini | Existing support; no additional variable | TXT, JPEG, PNG, WebP, PDF |
| ChatGPT | `OPENAI_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP, PDF through Responses |
| Claude | `ANTHROPIC_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP, PDF through Messages |
| Grok | `GROK_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG through Responses (xAI has no WebP; the app converts WebP photos to JPEG) |
| Kimi | `KIMI_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP with a vision-capable model |

For example, set `OPENAI_ATTACHMENT_TYPES=image/jpeg,image/png,image/webp,application/pdf` for an OpenAI model that supports all these formats. Choose only the formats supported by the model in your provider account. This setting advertises your configuration; it does not probe or activate model capabilities. Inline PDFs for Grok/Kimi and image-generation output remain pending; stored PDFs work for every provider (see Stored files).

Select up to three files, with a combined decoded size of 2 MB. Each text file must be at most 256 KB. Encoding, MIME type, file signatures and UTF-8 are checked before an upstream request. Validated content is added only to the final user turn. Unsupported selections leave the local draft and files available to change or remove. File bytes are not persisted in chat history or cloud snapshots; retrying a completed reply does not recover earlier file bytes. The Android app keeps the text of attached text files on the device (up to 32,000 characters per message, never exported or synced) and re-sends it as `attachmentText` on earlier turns of the streaming request so follow-up questions can refer to the file.

Schema, validation, cancellation and error tests use mocked provider responses. Live model acceptance still needs verification after you choose to configure credentials. Native capability checks are included in source; installing them requires a later Android release. No APK is built for this change.

## Stored files (Phase 2)

When the owner is signed in and stored files are set up (see SUPABASE.md §1b), the app uploads files of up to 50 MB straight to the private `dua-files` bucket. Chat messages then carry references (`messages[].files: [{path, name, mimeType, size, transcript?}]`) instead of bytes, so follow-ups re-attach the same files. `DUA_HISTORY_FILES` limits how many earlier files are re-sent in full. This is additive: requests without `files` behave exactly as before, and the small inline path (3 files, 2 MB) still works when signed out.

How each provider gets a stored file:

| Kind | Dua / Gemini | ChatGPT (Responses) | Claude (Messages) | Grok (xAI Responses) | Kimi |
| --- | --- | --- | --- | --- | --- |
| Image (JPEG/PNG/WebP) | `inlineData` (≤ 18 MB per request), otherwise a Files API upload (`fileData.fileUri`) | `input_image` with a 1-hour signed URL (needs `OPENAI_ATTACHMENT_TYPES`) | `image` with a `url` source (needs `ANTHROPIC_ATTACHMENT_TYPES`) | `input_image` with a signed URL, JPEG/PNG only (needs `GROK_ATTACHMENT_TYPES`) | data URL (needs `KIMI_ATTACHMENT_TYPES`) |
| PDF | inline or Files API | `input_file` + `file_url` (opt-in), otherwise extracted text | `document` with a `url` source (opt-in), otherwise extracted text | `input_file` + `file_url`; xAI runs `attachment_search` (billed per search; `GROK_FILE_INPUTS=0` sends extracted text instead) | extracted text |
| Spreadsheet (XLSX/XLS/ODS/CSV) | parsed on the server to CSV text (SheetJS), capped rows/columns with a truncation note | same | same | same | same |
| Text (TXT/MD/JSON) | UTF-8 text, capped | same | same | same | same |
| Audio (M4A/AAC/MP3/WAV/OGG/OPUS/WEBM/FLAC) | transcribed first, then the transcript is sent as text | same | same | same | same |

Gemini's Files API keeps uploads for 48 hours; large files are uploaded again on each request that needs them. PDF text extraction uses `unpdf`; scanned PDFs without a text layer get a note suggesting Dua/Gemini.

**Transcription** (`DUA_TRANSCRIBE_PROVIDER`, default `auto`): xAI `POST /v1/stt` with `model=grok-voice-transcribe-2.0` and the signed URL (`url` field; $0.10 per hour of audio), then Gemini `generateContent` with the audio, then OpenAI `/v1/audio/transcriptions` (`gpt-transcribe`, ≤ 25 MB). The streamed reply starts with a `files` event (`{"type":"files","files":[{"path","transcript"}]}`); the JSON path returns `files` too. The app saves the transcript with the message, and later requests send it back, so the audio isn't transcribed again.

## Imagine: images and video (Phase 3)

The composer's **Imagine** chip creates images and short videos. It needs the signed-in owner (`DUA_ALLOWED_EMAIL`), stored files (SUPABASE.md §1b) and the Imagine migration (SUPABASE.md §1c). Keys stay on the server. Results are saved to the owner's folder in the private `dua-files` bucket with the owner's own token, so the app never handles provider URLs or base64 through Vercel. `/api/config.imagine` tells new apps what is on; older apps ignore it, and new apps on an older backend hide the chip.

| Feature | Provider and endpoint | Default model (env to change) | Price (provider docs, October 2026) |
| --- | --- | --- | --- |
| Images (create) | xAI `POST /v1/images/generations` (`response_format: b64_json`, `aspect_ratio`, `n` 1–4) | `grok-imagine-image-2.0` (`DUA_IMAGINE_XAI_IMAGE_MODEL`) | $0.04 per image (`grok-imagine-image` $0.02, `grok-imagine-image-quality` $0.05) |
| Images (edit) | xAI `POST /v1/images/edits` (JSON; source photos as data URIs, up to 3) | same | billed for input and output images |
| Images (alternative) | Gemini Interactions API `POST /v1beta/interactions` (`response_format: {type: "image"}`, `store: false`) | `gemini-nano-banana-2.1` (`DUA_IMAGINE_GEMINI_IMAGE_MODEL`) | about $0.034 per 1K image |
| Video | xAI `POST /v1/videos/generations`, then `GET /v1/videos/{request_id}` | `grok-imagine-video-1.5` (`DUA_IMAGINE_XAI_VIDEO_MODEL`) | $0.08 per second (`-1.5-lite` $0.02/s, `grok-imagine-video` $0.05/s) |

Prices change; check docs.x.ai and ai.google.dev before raising caps. The image provider is chosen per request (Settings › Imagine sets the default). If the chosen provider is down (5xx, 429, missing key or model), the other configured one is tried once, unless `DUA_IMAGINE_IMAGE_FALLBACK=0`. Content-policy refusals are never retried: the app shows "The provider declined this request under its content policy…" (HTTP 422, `code: "content_policy"`). Gemini video (Veo) is not wired up yet.

**Video jobs:** `POST /api/imagine/video` returns a signed job token straight away (HMAC with a key derived from `GROK_API_KEY`, bound to the owner, valid 48 hours). The app polls `POST /api/imagine/status` every ~5 s, also after leaving the chat or restarting the app. When xAI reports `done`, the backend downloads the MP4 once and stores it (up to the storage limit, 50 MB on Free); later polls reuse the stored file. Bigger videos are returned as xAI's temporary link with a notice. xAI has no cancel call: Cancel in the app stops waiting, but the job may still finish and be billed.

**Spend caps** are durable (Supabase RPC, UTC day) and counted when a request is accepted, before the provider is called. Failed or refused requests still count, which keeps spend conservative.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DUA_IMAGINE_IMAGE` | on | `0` turns image creation off (the chip says why). |
| `DUA_IMAGINE_VIDEO` | on | `0` turns video off. Polling for already started jobs keeps working. |
| `DUA_IMAGINE_DAILY_IMAGES` | `20` | Images per day (0–1000). `0` turns images off. |
| `DUA_IMAGINE_DAILY_VIDEO_SECONDS` | `60` | Video seconds per day (0–3600). `0` turns video off. |
| `DUA_IMAGINE_IMAGE_PROVIDER` | `xai` | Default image provider when both keys exist: `xai` or `gemini`. |
| `DUA_IMAGINE_IMAGE_FALLBACK` | on | `0` disables trying the other image provider on outages. |
| `DUA_IMAGINE_VIDEO_1080P` | off | `1` offers 1080p (only on `grok-imagine-video-1.5`). 480p and 720p are always offered. |
| `DUA_IMAGINE_VIDEO_MAX_SECONDS` | `15` | Longest video the app may ask for (1–15). |
| `DUA_IMAGINE_MAX_SOURCE_MB` | `15` | Largest source photo sent for edits or image-to-video (1–20). |
| `GROK_API_KEY` / `GEMINI_API_KEY` | — | xAI is needed for video and Grok images; Gemini is optional for images. |

API routes (owner sign-in required): `POST /api/imagine/image`, `POST /api/imagine/video`, `POST /api/imagine/status`, `GET /api/imagine/usage`. A burst guard allows 30 requests a minute per instance. Local smoke test without keys: `cd web && node scripts/imagine-smoke.mjs` (fake providers and Supabase).

## Web search

Chat has a **Web: Off / Search / Read link** control in the Android app, plus an **X** toggle when Grok is selected. Modes start Off for each chat and are not stored or synced.

### Native search (Phase 2)

With Search on, Gemini, ChatGPT and Grok search with their provider's own tool; Claude and Kimi use Brave.

| Choice | Tool sent | Citations come from |
| --- | --- | --- |
| Dua / Gemini | `tools: [{ "google_search": {} }]` (Grounding with Google Search) | `groundingMetadata.groundingChunks` (titles are site domains; links are `vertexaisearch.cloud.google.com` redirects to the page) |
| ChatGPT | Responses `tools: [{ "type": "web_search" }]` | `url_citation` annotations |
| Grok | xAI Responses `tools: [{ "type": "web_search" }]` (+ `{ "type": "x_search" }` with X on) | `url_citation` annotations, then `citations` |
| Claude, Kimi | Brave (below) | Brave results |

The server streams the reply text, then a structured `sources` event (`{"type":"sources","sources":[{"id","title","url","domain"}]}`, HTTPS only, deduplicated, at most 10), then the usual `Sources:` footer. The footer stays part of the saved reply, so older app versions keep their links and newer versions draw source chips (favicon, page title, domain) from it.

| Variable | Default | Effect |
| --- | --- | --- |
| `DUA_NATIVE_SEARCH` | on | `0` turns native search off for every provider (Brave is used when configured). |
| `GEMINI_NATIVE_SEARCH`, `OPENAI_NATIVE_SEARCH`, `GROK_NATIVE_SEARCH` | on | `0` turns it off for one provider. |
| `DUA_SEARCH_PREFER_BRAVE` | `0` | `1` uses Brave for every provider when `BRAVE_SEARCH_API_KEY` is set. |
| `BRAVE_SEARCH_API_KEY` | — | Brave for Claude/Kimi, the prefer flag and the fallback. |
| `DUA_X_SEARCH` | on | `0` hides the X toggle and rejects `xSearch` requests (403). |

If a provider rejects its search tool (HTTP 400, for example a model without search support) and a Brave key is set, the request is retried once with Brave and the reply carries a notice. Native search is billed by the provider per search in addition to tokens: check Google's grounding pricing, OpenAI's web search tool pricing and xAI's tool pricing for your models. xAI server-side tools need a Grok 4 family model.

**X search (Grok only):** the X toggle adds xAI's `x_search` tool, so Grok can read public X posts. It costs about **$5 per 1,000 posts** fetched (and $10 per 1,000 user profiles, per xAI's pricing page as of September 2026), on top of web search ($5 per 1,000 calls) and tokens, so it is off for every message unless you turn it on, and resets when you start a new chat or switch away from Grok. `/api/config.web.xSearch` advertises it; set `DUA_X_SEARCH=0` to disable it on the server.

**Google Search grounding terms:** Google's terms require apps that show grounded answers to display the returned Search Suggestions (`groundingMetadata.searchEntryPoint.renderedContent`) unchanged, next to the answer. The app does not render them yet. Dua is a personal single-user app; set `GEMINI_NATIVE_SEARCH=0` (Brave is then used for Dua/Gemini when configured) if that matters for your use.

`/api/config.web` keeps `searchConfigured` (Brave key present) and `pageReadingAvailable`, and adds `nativeSearch` (configured providers with native search), `xSearch` and `preferBrave`. Older apps ignore the new fields.

### Brave

Set `BRAVE_SEARCH_API_KEY` for Brave Search API access. Explicit `POST /api/search` JSON `{ "query": "your search" }` returns `{ "query": "your search", "results": [{ "title": "...", "url": "https://...", "snippet": "..." }] }` with at most five results.

- **Search via Brave:** Each opted-in Send runs one Brave search, then gives the model up to five result snippets as untrusted evidence. The final prompt is normalized and limited to 500 characters for the search query. Earlier turns, memory notes and file names/bytes are excluded from the search query. This does not fetch every result page.
- **Read link:** Put a public HTTPS URL in the message. Send reads only its first HTTPS link, then passes a bounded text excerpt to the model. This mode needs no additional API key. It still uses the configured chat provider for the answer.

Source instructions for Brave and Read link travel with the final user turn (not the system prompt), so the system prompt stays identical between turns. The model is told to cite numbered sources and treat source content as untrusted data; generated claims still need judgment. Retrieval errors and empty searches stop the request without an invented sourced answer, restoring the draft/files. Stop cancels retrieval and the subsequent model call. In-memory retries retain the original mode; after the app process restarts, retries use Off and explain that choice.

Search links are bounded, deduplicated and HTTPS only. Links with embedded credentials and literal IP/local addresses are excluded. Search has a 15-second timeout; an opted-in chat shares the chat deadline (`DUA_CHAT_DEADLINE_SECONDS`, default 280 s).

## Long conversations (Phase 2)

The old 39-message cut is replaced by a per-provider character budget for the history sent with each message (text, file text, audio transcripts and about 200 characters per stored file):

| Variable | Default (characters) |
| --- | --- |
| `GEMINI_CONTEXT_CHARS` | 400000 |
| `OPENAI_CONTEXT_CHARS` | 200000 |
| `ANTHROPIC_CONTEXT_CHARS` | 200000 |
| `GROK_CONTEXT_CHARS` | 300000 |
| `KIMI_CONTEXT_CHARS` | 120000 |

Values accept 20,000–4,000,000. When a conversation goes over the budget, the server asks the **current provider** (non-streaming, no Reasoning) to merge the older turns into a running summary (up to 12,000 characters), keeps the newest turns verbatim down to about half the budget, and puts the summary in the system prompt after memory. The summary is sent back as the first stream event (`{"type":"context","summary","covered"}`; the JSON reply has `context`). The app saves it with the chat (local history only) together with the id of the last summarised message, and next time sends only the summary plus the turns after it, so summaries are regenerated incrementally and only occasionally. Stored-file references in the kept turns are still resolved; older files are named in the summary. Memory keeps working because it is a separate system part.

If summarising fails, the chat still works: the oldest turns are left out and a notice says so. Older app versions (which do not send `context`) never trigger summaries; they get the same "oldest turns left out" behaviour when over the budget. The request body cap is 4 MB (history 2.5 MB, 400 messages).

**Prompt caching:** provider prompts keep a stable prefix (system prompt, memory, summary, then turns in order; search and page material only in the last turn). For Grok the server also sends `prompt_cache_key` (a UUID derived from the conversation id) so xAI routes the conversation to the same cache. OpenAI and Gemini cache stable prefixes automatically for supported models. `/api/config.context` reports `summaries: true` and the budgets.

## Public page limits

Read link accepts UTF-8 HTML/plain text only, up to 512 KiB downloaded and 12,000 excerpt characters. It extracts title/text, removes scripts/styles and common navigation content, and never executes JavaScript or fetches subresources. It rejects sign-in pages, unsupported encodings/compression and empty content. Sites requiring a browser, cookies, authentication or rendered JavaScript remain unsupported; PDF uses the attachment workflow.

Page requests use HTTPS on port 443 without credentials or auth-sensitive query parameters. DNS answers are checked for public addresses and the connection is pinned to the validated address with TLS hostname checking. Private/local/reserved addresses and IP literals are rejected. Each redirect repeats URL/DNS validation, with at most three redirects and a 15-second overall retrieval limit. No arbitrary proxy endpoint or separate public page-fetch route is added.

No live provider/search request or key activation was made during development. Tests mock search, page DNS/HTTPS transport and model responses. Before personal use, enable only desired providers and check a prompt, retry/stop, quota handling and citation links. Physical Android verification requires a later installation; no APK is assembled for this change.
