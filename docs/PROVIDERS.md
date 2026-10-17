# AI providers and web search setup

Dua and Gemini retain the existing Gemini setup and attachment support. ChatGPT, Claude, Grok and Kimi have server adapters for conversations, attachments, history, retry and optional memory context. Their setup remains inactive until provider credentials and a model ID are configured. This change does not provision keys or create subscriptions.

## Build mode: read-only repository chat (Phase 5B)

For repository/path examples, missing setup, GitHub credentials, rate limits and safe PR retry steps, see [GitHub connector and Build recovery](GITHUB_TROUBLESHOOTING.md).

Build mode ties a chat to one of the owner's GitHub repositories. Dua/Gemini answers questions about the code and proposes changes as unified diffs (```diff blocks). It is **strictly read-only**: the server only sends `GET` requests to five allowlisted GitHub paths (repository list, repository info, `git/trees/HEAD?recursive=1`, `contents/{path}`, `search/code` inside the chosen repository). The model has no branch, commit, pull request or command tool. Pull requests (Phase 5C, below) are a separate, opt-in step that only the owner's confirmed tap can trigger.

Request: `"mode": "build", "repository": "owner/name"` on `POST /api/chat` (Dua or Gemini only). Checks run in this order, each with a clear error: server setup (503 with the reason), signed-in owner (`DUA_ALLOWED_EMAIL`, 401/403), then the repository (404 if the token cannot read it). The server loads the repository info and file tree, puts a trimmed file list in the system prompt (all paths if they fit in 12,000 characters, otherwise the shallowest first), and runs a Gemini function-calling loop with three tools:

| Tool | GitHub call | Limits |
| --- | --- | --- |
| `list_files(path_prefix?)` | from the tree already loaded (no extra call) | 300 paths per call |
| `read_file(path, start_line?, end_line?)` | `GET /repos/{o}/{r}/contents/{path}` (default branch) | `DUA_BUILD_FILE_MAX_KB` per file (default 100); repeated reads come from a per-reply cache; binary, folder, over-1 MB and `..` paths are refused |
| `search_code(query)` | `GET /search/code?q=<query> repo:{o}/{r}` | 3 searches per reply (GitHub allows about 10 a minute); `repo:`/`org:`/`user:` qualifiers from the model are stripped so search stays in the chosen repository |

Loop limits: at most `DUA_BUILD_MAX_ROUNDS` Gemini calls per reply (default 6, range 1–12), 8 tool calls per round, `DUA_BUILD_MAX_FILES` files (default 40) and `DUA_BUILD_MAX_KB` characters read in total (default 600). The last round, a used-up budget, or less than 60 s left before the chat deadline (`DUA_CHAT_DEADLINE_SECONDS`, default 280) forces a plain answer (`functionCallingConfig.mode: NONE`). Model turns are echoed back verbatim so Gemini 3 thought signatures are kept. File contents and search results are marked as untrusted repository data in the prompt.

Stream events: `{"type":"build","repository":"owner/name","branch":"main","files":[{"path","bytes","truncated?"}],"rounds":2,"searches":0}` at the start, after each tool round and before the answer text (the latest replaces earlier ones), then `text` and `done`. JSON replies carry `build` with the same shape. `read_file` also reports the file's blob `sha`, which pull requests use to detect stale proposals. Attachments, web search and Run code are not used in Build mode (the reply says so). `/api/config.build` reports `{enabled, reason?, providers, readOnly: true, tools, maxRounds, maxFiles, fileMaxKb, totalMaxKb}`; the app shows `reason` in the Build sheet instead of hiding the mode.

The GitHub connector route (`POST /api/connectors/github`, owner sign-in required) offers the same read actions: `list-repositories`, `get-tree` (2,000 paths), `get-file` (100 KB) and `search-code`.

| Variable | Default | Required | Effect |
| --- | --- | --- | --- |
| `ENABLE_GITHUB_CONNECTOR` | off | yes for Build | `1` enables the connector |
| `GITHUB_CONNECTOR_TOKEN` | — | yes for Build | Server-side token. Use a fine-grained token with **read-only** Contents and Metadata on the repositories you want |
| `DUA_ALLOWED_EMAIL` | — | yes for Build | Only this signed-in account can use Build mode or the connector |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | — | yes for Build | Sign-in used for the owner check |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | — | yes for Build | Build mode uses Gemini function calling |
| `DUA_BUILD` | on | optional | `0` turns Build mode off |
| `DUA_BUILD_MAX_ROUNDS` | 6 | optional | Gemini calls per reply (1–12) |
| `DUA_BUILD_FILE_MAX_KB` | 100 | optional | Characters read per file, in KB (4–1000) |
| `DUA_BUILD_MAX_KB` | 600 | optional | Characters read per reply, in KB (16–3000) |
| `DUA_BUILD_MAX_FILES` | 40 | optional | Files read per reply (1–100) |

Cost: each Build reply makes up to `DUA_BUILD_MAX_ROUNDS` Gemini requests, and every round resends the conversation plus the files read so far. A reply that reads several large files can use a few hundred thousand input tokens. On the free AI Studio key this counts toward the per-minute and per-day request and token limits, so lower `DUA_BUILD_MAX_ROUNDS` or `DUA_BUILD_MAX_KB` if you hit 429s. GitHub API calls are free (5,000 an hour per token; code search about 10 a minute).

## Build mode: pull requests after approval (Phase 5C)

When enabled, Gemini gets one more tool, `propose_changes(title, summary, changes[])`, where each change is `{path, action: create|update|delete, content?}` with the **complete new file content**. The tool only validates the proposal (paths, caps, and that every updated or deleted file was read in full in this reply) and returns it to the app. It has no token and no network access, so the model cannot write to GitHub no matter what it asks. After a proposal the loop forces a plain text answer. Stream event `{"type":"proposal","id","repository","baseBranch","branch":"dua/…","title","summary","createdAt","files":[{"path","action","content?","baseSha?","diff","additions","deletions","truncated?"}]}` (once, before the text); JSON replies carry `proposal`.

The app shows a review card (title, summary, files with coloured diffs and a **Create PR** button). Only the owner's tap, confirmed in a dialog, calls `POST /api/build/pr` (owner sign-in required, same burst group as chat). The server re-checks everything and then:

1. verifies the repository is allowed (below) and is not archived,
2. checks each file's current blob sha on the default branch against the sha the proposal was based on, and refuses with 409 ("…ask Dua to regenerate") when anything changed, was added or was deleted in between; symlinks, submodules, folders and file/folder clashes are refused,
3. creates the change with the Git Data API (blobs → tree on top of the current default branch → commit → a new ref) and opens a pull request from the new branch into the default branch.

The branch is always `dua/<slug>-<yymmdd-hhmmss>` (UTC), never the default branch. There is no force-push, merge or branch deletion: the write client is an allowlist of `GET` and `POST` only (no `PATCH`, `PUT` or `DELETE`), and new refs must be `refs/heads/dua/*`. The PR body says it was created by Dua from the owner's request and carries the proposal id. A second tap with the same proposal returns the existing pull request instead of opening another (the commit message contains `Dua-Proposal-Id`). If the repository cannot have draft pull requests, a normal one is opened and the response says so.

Rejected paths: `.github/workflows/**`, `.git/**`, `.gitmodules`, absolute paths, `..`, empty segments, control characters. Caps: `DUA_BUILD_PR_MAX_FILES` files (default 20), `DUA_BUILD_PR_FILE_MAX_KB` per file (default 200 KB) and `DUA_BUILD_PR_MAX_KB` total (default 1 MB), all UTF-8 bytes; text files only. Updates and deletes are limited to files the model read in full in the same reply, so files over `DUA_BUILD_FILE_MAX_KB` (default 100 KB) cannot be changed unless you raise it. `/api/config.build.pr` reports `{enabled, reason?, approval: "owner", draft, restricted, maxFiles, fileMaxKb, totalMaxKb}` and never the token or the repository list. The app shows `reason` on the review card.

### Write token

Create a **separate** fine-grained personal access token (GitHub → Settings → Developer settings → Fine-grained tokens) and keep the existing read-only `GITHUB_CONNECTOR_TOKEN` for reading:

- Resource owner: your account. Repository access: **Only select repositories** (the ones Dua may open pull requests in).
- Repository permissions: **Contents: Read and write**, **Pull requests: Read and write**, **Metadata: Read-only** (GitHub requires Metadata). Nothing else: no Workflows, Actions, Administration or Secrets permissions.
- Account permissions: none.

With those permissions the token cannot change Actions workflows (GitHub blocks that unless the token has the Workflows permission, which this design never asks for) and the server rejects workflow paths anyway.

| Variable | Default | Required | Effect |
| --- | --- | --- | --- |
| `DUA_BUILD_PR` | off | yes | `1` allows pull requests from Build mode. Off unless this and the write token are both set |
| `GITHUB_BUILD_WRITE_TOKEN` | — | yes | The fine-grained token above. Separate from `GITHUB_CONNECTOR_TOKEN`; never sent to the app or to Gemini |
| `DUA_BUILD_PR_REPOS` | unset | optional | Comma-separated `owner/repo` (case-insensitive) that pull requests are allowed in. When unset, only repositories **owned by the write token's account** are allowed, so a token that can also see other people's repositories cannot open pull requests there |
| `DUA_BUILD_PR_DRAFT` | on | optional | `0` opens pull requests ready for review instead of drafts |
| `DUA_BUILD_PR_MAX_FILES` | 20 | optional | Files per pull request (1–50) |
| `DUA_BUILD_PR_FILE_MAX_KB` | 200 | optional | Per-file size in KB (1–1000) |
| `DUA_BUILD_PR_MAX_KB` | 1024 | optional | Total size of new contents in KB (1–3000) |

`DUA_BUILD=0` turns off the whole of Build mode, including proposals and pull requests. Running commands is still not implemented; see [ROADMAP.md](ROADMAP.md).

## Server variables

Add variables to Secret Manager (same name as the variable) and re-run `web/deploy-cloudrun.sh`, or to a local ignored `.env` file. API keys must never be added to Android resources, public client configuration or GitHub commits.

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

`/api/chat` keeps its JSON reply unless the request opts in with `"stream": true`; then it answers with Server-Sent Events: `context` (a new conversation summary, sent first), `thinking`, `text`, `sources`, `notice`, `files` (audio transcripts), `done` and `error` (each `data:` is JSON with the same `type`), plus `code` (Run code, see below) and a `: ping` comment every 15 seconds. Validation, login, quota and upstream errors that happen before the first byte still return the usual JSON error and status (including `Retry-After`). If an adapter cannot stream, the reply arrives as one `text` event. Web search and Read link work in both modes; the `Sources:` footer arrives as the final text.

`"mode": "reasoning"` asks the model to think first. Thinking summaries are only sent in Reasoning mode and are saved with the chat in the server snapshot, phone cache and export; older files without them still load.

| Choice | Reasoning | Effort variable (default) | Output cap variable (Auto / Reasoning default) |
| --- | --- | --- | --- |
| Dua / Gemini | `thinkingConfig.includeThoughts`; Gemini 3 uses `thinkingLevel`, 2.5 uses a dynamic `thinkingBudget` | `GEMINI_THINKING_LEVEL` (`high`; `low`/`medium`/`high`) | `GEMINI_MAX_OUTPUT_TOKENS` (model default) |
| ChatGPT | Responses `reasoning` with `summary: auto` — needs a reasoning model (`o`-series / GPT-5 family) | `OPENAI_REASONING_EFFORT` (`high`) | `OPENAI_MAX_OUTPUT_TOKENS` (16384 / 32768) |
| Grok | xAI Responses `reasoning.effort`; Grok 4 always reasons | `GROK_REASONING_EFFORT` (`high`) | `GROK_MAX_OUTPUT_TOKENS` (16384 / 32768) |
| Claude | No visible thinking yet; the reply says so | — | `ANTHROPIC_MAX_OUTPUT_TOKENS` (8192) |
| Kimi | Shows `reasoning_content` when the model returns it | — | `KIMI_MAX_OUTPUT_TOKENS` (8192) |

Output caps accept 256–1,000,000; output tokens include thinking tokens. A reply that hits the cap ends with `done.truncated` and the app shows a note. `DUA_CHAT_DEADLINE_SECONDS` (10–790) must stay below the Cloud Run request timeout: `web/deploy-cloudrun.sh` sets the Cloud Run timeout.js` (Hobby with Fluid compute allows 300 s; Pro allows up to 800 s). The Android app allows 75 s between bytes and 330 s per call.

For streaming Auto/Reasoning chat, the backend opens SSE after access, input, project and provider-setup checks, before web/file preparation, context summarization or waiting for provider response headers. An initial comment and 15-second heartbeat comments keep the Android read timeout from expiring during these waits. Stop cancels the preparation/provider work; the whole-request deadline still applies. Failures after SSE opens use a terminal `error` event carrying `message`, `status` and optional `retryAfterSeconds` (HTTP remains 200); access/setup failures before it opens remain JSON with an HTTP error status. Non-streaming JSON replies keep their existing HTTP error behavior. Build mode uses its existing separate stream path.

Provider HTTP failures also include `upstreamStatus` in Auto/Reasoning error events when available. Gemini HTTP 500/502/503/504 messages identify the actual upstream status; the backend still maps these failures to status 502 for the existing client contract. Cloud Run logs record only `[dua-gemini] Upstream request failed (HTTP N).`, without provider bodies, credentials or prompts. If chat fails, use that status and a same-project/model test in Google AI Studio to distinguish provider availability from configuration problems. No automatic paid retry or model switch is performed.

## Server reply saving (Phase 5)

Completed streaming replies can opt into server persistence with owner-bound `persistence` metadata when `/api/config.history.persistReplies` is `true`. The backend stores the completed user/reply pair in the existing Supabase snapshot before sending a `saved` event followed by `done`; client sync consent is not needed for this reply write. Preparation/generation failures and cancelled incomplete streams are not stored as completed replies. Post-generation save failures end with an error event while retaining received text; commit-time auth failure does not request an automatic whole-generation retry. See [SUPABASE.md](../SUPABASE.md#completed-reply-saving) for prerequisites, compatibility, conflict recovery and limits.

## Run code: hosted code execution (Phase 5)

The Run code chip asks the provider to run Python in **its own sandbox**; Dua never executes code on the phone or on the dua-api server. Request field: `"code": true` (streaming path; older servers never receive it). `/api/config.code` reports `{enabled, reason?, providers, language, maxRuns, maxChars}`.

| Choice | Tool sent | Default | Cost and limits (provider docs, Oct 2026) |
| --- | --- | --- | --- |
| Dua / Gemini | `tools: [{ code_execution: {} }]` → parts `executableCode` / `codeExecutionResult` | On when `GEMINI_API_KEY` + `GEMINI_MODEL` are set | No extra charge: code and output are billed as normal tokens, so it works on the free AI Studio key (free-tier rate limits still apply, and code turns use more tokens). Python only, 30 s per run, the model may retry a failing run up to 5 times; libraries such as pandas, numpy, matplotlib, openpyxl; no custom installs |
| ChatGPT | `{ type: 'code_interpreter', container: { type: 'auto' } }` + `include: ['code_interpreter_call.outputs']` | Off; `OPENAI_CODE_EXECUTION=1` | OpenAI bills a container per session in addition to tokens |
| Grok | `{ type: 'code_interpreter' }` (xAI SDK name `code_execution`) | Off; `GROK_CODE_EXECUTION=1` | xAI bills per tool call (about $5 per 1,000) in addition to tokens |
| Claude, Kimi | — | — | The reply answers without code and says so |

Variables: `DUA_CODE_EXECUTION` (default on; `0` removes Run code everywhere), `GEMINI_CODE_EXECUTION` (default on; `0` turns off Gemini only), `OPENAI_CODE_EXECUTION` / `GROK_CODE_EXECUTION` (default off; `1` turns on). Code execution and Google Search grounding can run in the same Gemini request.

Stream events: `{"type":"code","index":0,"language":"python","code":"…"}` when code runs, then `{"type":"code","index":0,"output":"…","outcome":"ok"}` (`ok`, `error` or `timeout`). JSON replies carry `code: [{language, code, output, outcome}]`. At most 10 runs per reply; code and output are each capped at 20,000 characters. Charts that code draws (inline images) are not shown yet; the reply gets a note instead. If the model rejects the tool with HTTP 400, the request is sent again without it and the reply carries a notice. Run code is not switched on automatically in Reasoning mode: Google notes that code execution can make other output (for example stories) worse, so it stays an explicit choice. The xAI and OpenAI paths are implemented from their docs and covered only by mocked tests.

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

The composer's **Imagine** chip creates images and short videos. It needs the signed-in owner (`DUA_ALLOWED_EMAIL`), stored files (SUPABASE.md §1b) and the Imagine migration (SUPABASE.md §1c). Keys stay on the server. Results are saved to the owner's folder in the private `dua-files` bucket with the owner's own token, so the app never handles provider URLs or base64 through the Node API. `/api/config.imagine` tells new apps what is on; older apps ignore it, and new apps on an older backend hide the chip.

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

## Voice v2: reply voices and languages (Phase 4)

Settings › Voice picks how replies are read aloud: the **device voice** (free; language and installed voice chosen in the app, speed 0.5–2×) or an **AI voice** from the server. AI voices need the signed-in owner (`DUA_ALLOWED_EMAIL` plus Supabase sign-in); keys stay on the server. `/api/config.voice` lists the providers, voices, languages and speed range that are on, so a new app on an older backend keeps the single "AI reply voice" switch, and an older app on this backend keeps working (a text-only request still gets the old ElevenLabs/Grok MP3).

| Provider | Endpoint (verified against provider docs, October 2026) | Voices | Languages | Speed | Price |
| --- | --- | --- | --- | --- | --- |
| Grok (xAI) | `POST https://api.x.ai/v1/tts` with `text`, `voice_id`, `language` (BCP-47 or `auto`), `output_format {codec: mp3, sample_rate: 24000, bit_rate: 64000}`, `speed` | 28 (Eve default; Ara, Rex, Sal, Leo, …) | Auto plus the provider list (English, Hindi, Arabic, Bengali, …). Urdu is not on xAI's list, so it is sent as `auto` and detected from the text | 0.7–1.5 | $15 per 1M characters |
| Gemini | Interactions API `POST /v1beta/interactions` with `response_format {type: audio, mime_type: audio/wav}`, `generation_config.speech_config [{voice}]`, `store: false` | 30 prebuilt (Kore default; Puck, Charon, Zephyr, …) | Detected from the text (Hindi supported; Urdu is not on Google's list) | Sent as a speaking-style hint (slowly / quickly) | about $0.00225 per 10 s of audio |
| ElevenLabs | Existing `text-to-speech/{voice}` call | The one voice in `ELEVENLABS_VOICE_ID` | Multilingual model | Fixed | Your ElevenLabs plan |

`POST /api/voice/audio` accepts `{text, provider?, voice?, language?, speed?}` and returns `audio/mpeg` (Grok, ElevenLabs) or `audio/wav` (Gemini, 24 kHz mono). The app cleans markdown, links, code and citations out of the reply, then asks for a short first chunk (~220 characters) and larger later chunks (≤ 600, never above the provider's per-request limit) so playback starts quickly; the next chunk is fetched while the current one plays. Generated chunks are cached in memory (about 12 MB, per provider/voice/language/speed/text) so replays and voice previews are free; nothing is written to disk. Voice requests have their own burst window (40 per minute) and do not use the daily chat request quota.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DUA_VOICE_TTS` | on | `0` turns the Grok and Gemini reply voices off (ElevenLabs keeps its own switch). |
| `DUA_VOICE_TTS_PROVIDER` | first available (`xai`, then `gemini`, then `elevenlabs`) | Default AI voice provider. |
| `DUA_VOICE_GEMINI_TTS_MODEL` | `gemini-3.8-flash-tts` | Gemini speech model. |
| `GROK_API_KEY` / `GEMINI_API_KEY` | — | Turn on the Grok / Gemini voices. |
| `ENABLE_GENERATED_VOICE`, `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID` | — | Optional ElevenLabs voice (unchanged). |

Speech recognition (voice input and hands-free) uses Android's recognizer with the language picked in Settings › Voice (Auto, Hindi `hi-IN`, Urdu `ur-PK`, English `en-IN`, …). Support depends on the phone's speech service.

### Live voice (Phase 4B)

Settings › Hands-free (and the Voice screen) open a full-screen **live conversation** with Grok when the server allows it: you talk, Grok answers with voice, and you can interrupt by talking over it. It uses the xAI Speech to Speech API (Grok Voice Agent, verified on docs.x.ai, October 2026):

- `POST /api/voice/session` (signed-in owner only) reserves minutes, then mints a short-lived **client secret** with `POST https://api.x.ai/v1/realtime/client_secrets` `{"expires_after": {"seconds": N}}` (N = the reserved session length + 60 s, at most 3600). The response carries that secret, the session configuration (Dua's system prompt, Memory notes when Memory is on, the last ≤ 12 turns of the open chat, voice, `server_vad` turn detection, PCM 24 kHz in and out, `grok-transcribe` captions with a language hint, speed) and today's usage. `GROK_API_KEY` never leaves the server.
- The phone opens `wss://api.x.ai/v1/realtime?model=grok-voice-latest` with the secret (`Authorization: Bearer`), sends `session.update`, streams microphone audio (`input_audio_buffer.append`, echo cancelling, 40 ms frames) and plays `response.output_audio.delta`. Speaking while Grok talks (`input_audio_buffer.speech_started`) flushes playback at once; the rest of that reply is ignored. Captions come from `conversation.item.input_audio_transcription.*` and `response.output_audio_transcript.*`.
- A microphone **foreground service** keeps the session running with the screen off (notification with an End action). Audio routes to a connected Bluetooth headset when there is one.
- `POST /api/voice/session-end` `{sessionId}` closes the reservation and charges the time actually used; `GET /api/voice/usage` returns today's minutes. When the session ends, the captions are added to the chat it was started from (an interrupted reply ends with "…").
- If live voice is off, out of minutes or not set up, the screen offers the turn-based hands-free mode instead.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DUA_VOICE_REALTIME` | on (when `GROK_API_KEY` and Supabase are set) | `0` turns live voice off; the app falls back to hands-free mode. |
| `DUA_VOICE_DAILY_MINUTES` | `30` | Live minutes per UTC day (0–1440; `0` = off). Enforced in Postgres when a session token is minted. |
| `DUA_VOICE_SESSION_MAX_MINUTES` | `10` | Longest single session (1–120). The phone also stops at this limit. |
| `DUA_VOICE_REALTIME_MODEL` | `grok-voice-latest` | Realtime model id. |

**Cost and caps.** xAI bills live voice per minute (about **$0.08/min**; 30 min/day ≈ $2.40/day at most). Each session reserves `min(session max, minutes left)` in `dua_voice_sessions` before the token is minted, so parallel or repeated starts cannot exceed the daily cap. Ending a session charges `least(reserved, elapsed)`; a session that is never ended (app killed) is charged its full reservation once it is overdue. Needs the Phase 4 migration (SUPABASE.md §1d); without it `/api/voice/session` answers with a setup message.

Live voice languages follow the reply language: xAI's list needs regional variants for Spanish and Portuguese, and Urdu is not on the list, so Urdu is asked for in the instructions and the caption language is auto-detected.

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

Values accept 20,000–4,000,000. When a conversation goes over the budget, the server asks the **current provider** (non-streaming, no Reasoning) to merge the older turns into a running summary (up to 12,000 characters), keeps the newest turns verbatim down to about half the budget, and puts the summary in the system prompt after memory. The summary is sent back as the first stream event (`{"type":"context","summary","covered"}`; the JSON reply has `context`). The app saves it with the chat (phone cache; the server snapshot is the source of truth) together with the id of the last summarised message, and next time sends only the summary plus the turns after it, so summaries are regenerated incrementally and only occasionally. Stored-file references in the kept turns are still resolved; older files are named in the summary. Memory keeps working because it is a separate system part.

If summarising fails, the chat still works: the oldest turns are left out and a notice says so. Older app versions (which do not send `context`) never trigger summaries; they get the same "oldest turns left out" behaviour when over the budget. The request body cap is 4 MB (history 2.5 MB, 400 messages).

**Prompt caching:** provider prompts keep a stable prefix (system prompt, memory, summary, then turns in order; search and page material only in the last turn). For Grok the server also sends `prompt_cache_key` (a UUID derived from the conversation id) so xAI routes the conversation to the same cache. OpenAI and Gemini cache stable prefixes automatically for supported models. `/api/config.context` reports `summaries: true` and the budgets.

## Public page limits

Read link accepts UTF-8 HTML/plain text only, up to 512 KiB downloaded and 12,000 excerpt characters. It extracts title/text, removes scripts/styles and common navigation content, and never executes JavaScript or fetches subresources. It rejects sign-in pages, unsupported encodings/compression and empty content. Sites requiring a browser, cookies, authentication or rendered JavaScript remain unsupported; PDF uses the attachment workflow.

Page requests use HTTPS on port 443 without credentials or auth-sensitive query parameters. DNS answers are checked for public addresses and the connection is pinned to the validated address with TLS hostname checking. Private/local/reserved addresses and IP literals are rejected. Each redirect repeats URL/DNS validation, with at most three redirects and a 15-second overall retrieval limit. No arbitrary proxy endpoint or separate public page-fetch route is added.

No live provider/search request or key activation was made during development. Tests mock search, page DNS/HTTPS transport and model responses. Before personal use, enable only desired providers and check a prompt, retry/stop, quota handling and citation links. Physical Android verification requires a later installation; no APK is assembled for this change.
