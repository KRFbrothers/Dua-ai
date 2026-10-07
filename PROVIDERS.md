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

Use an API model ID available to your account, rather than a marketing label such as “Instant” or the app choice “ChatGPT”. Explicit model configuration avoids silently selecting a paid or retired model. Endpoints are fixed to OpenAI Responses, Anthropic Messages, xAI chat completions and Moonshot international chat completions. Kimi needs credentials for `api.moonshot.ai`, rather than the separate China API. Custom base URLs are unsupported.

The earlier decision to defer OpenAI key setup remains in effect. No key was created, inspected or used during development. Verification uses mocked responses. Provider APIs can have separate billing from consumer chat subscriptions; enable only providers you want.

Redeploy after setting server variables. `/api/config` exposes provider availability, model IDs, `supportsAttachments` and the supported `attachmentTypes` MIME array, without keys. Missing credentials or a model ID leave that provider unavailable. Requests support cancellation, a 45-second provider timeout, bounded history/output and sanitized errors. OpenAI requests use `store: false`.

## Attachments

All providers accept UTF-8 TXT files. For the four optional providers, enable images or PDFs only after confirming that your configured API model accepts that input. The capability variables are comma-separated MIME types; TXT remains available automatically. Unsupported or misspelled types are ignored, and the app and server reject files outside the published list.

| Provider | Capability variable | Available types |
| --- | --- | --- |
| Dua / Gemini | Existing support; no additional variable | TXT, JPEG, PNG, WebP, PDF |
| ChatGPT | `OPENAI_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP, PDF through Responses |
| Claude | `ANTHROPIC_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP, PDF through Messages |
| Grok | `GROK_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP through chat completions |
| Kimi | `KIMI_ATTACHMENT_TYPES` | TXT; opt-in JPEG, PNG, WebP with a vision-capable model |

For example, set `OPENAI_ATTACHMENT_TYPES=image/jpeg,image/png,image/webp,application/pdf` for an OpenAI model that supports all these formats. Choose only the formats supported by the model in your provider account. This setting advertises your configuration; it does not probe or activate model capabilities. Grok/Kimi PDF ingestion and image-generation output remain pending.

Select up to three files, with a combined decoded size of 2 MB. Each TXT file must be at most 64 KB. Encoding, MIME type, file signatures and UTF-8 are checked before an upstream request. Validated content is added only to the final user turn. Unsupported selections leave the local draft and files available to change or remove. File bytes are not persisted in chat history or cloud snapshots; retrying a completed reply does not recover earlier file bytes.

Schema, validation, cancellation and error tests use mocked provider responses. Live model acceptance still needs verification after you choose to configure credentials. Native capability checks are included in source; installing them requires a later Android release. No APK is built for this change.

## Web search

Set `BRAVE_SEARCH_API_KEY` for Brave Search API access. Explicit `POST /api/search` JSON `{ "query": "your search" }` returns `{ "query": "your search", "results": [{ "title": "...", "url": "https://...", "snippet": "..." }] }` with at most five results.

Chat has a **Web: Off / Search / Read link** control in the Android app. It starts Off when opening a chat; choosing a mode opts subsequent sends in for that session. Modes are not stored or synced. `/api/config.web` reports Search and Read link availability. Older backends without these fields keep both modes unavailable.

- **Search:** Each opted-in Send runs one Brave search, then gives the model up to five result snippets as untrusted evidence. The final prompt is normalized and limited to 500 characters for the search query. Earlier turns, memory notes and file names/bytes are excluded from the search query. The model still receives the ordinary conversation, selected attachments and enabled memory. This does not fetch every result page.
- **Read link:** Put a public HTTPS URL in the message. Send reads only its first HTTPS link, then passes a bounded text excerpt to the model. This mode needs no additional API key. It still uses the configured chat provider for the answer.

Successful grounded replies append the actual source titles/URLs in a Sources footer, with safe clickable links in the Android app. The footer is part of the ordinary reply text, so copy, local history, sharing and cloud snapshots retain it without a schema change. The model is instructed to cite numbered sources and treat source content as untrusted data; generated claims still need judgment. A search snippet is not a verified full page. Retrieval errors and empty searches stop the request without an invented sourced answer, restoring the draft/files. Stop cancels retrieval and the subsequent model call, discarding late responses. In-memory retries retain the original mode; after the app process restarts, retries use Off and explain that choice.

Search links are bounded, deduplicated and HTTPS only. Links with embedded credentials and literal IP/local addresses are excluded. Search has a 15-second timeout; an opted-in chat has a 55-second overall deadline within the existing 60-second function limit. Account/login and request-attempt controls apply before retrieval. One chat attempt can make both a search and a chat-provider call; provider billing controls remain separate.

## Public page limits

Read link accepts UTF-8 HTML/plain text only, up to 512 KiB downloaded and 12,000 excerpt characters. It extracts title/text, removes scripts/styles and common navigation content, and never executes JavaScript or fetches subresources. It rejects sign-in pages, unsupported encodings/compression and empty content. Sites requiring a browser, cookies, authentication or rendered JavaScript remain unsupported; PDF uses the attachment workflow.

Page requests use HTTPS on port 443 without credentials or auth-sensitive query parameters. DNS answers are checked for public addresses and the connection is pinned to the validated address with TLS hostname checking. Private/local/reserved addresses and IP literals are rejected. Each redirect repeats URL/DNS validation, with at most three redirects and a 15-second overall retrieval limit. No arbitrary proxy endpoint or separate public page-fetch route is added.

No live provider/search request or key activation was made during development. Tests mock search, page DNS/HTTPS transport and model responses. Before personal use, enable only desired providers and check a prompt, retry/stop, quota handling and citation links. Physical Android verification requires a later installation; no APK is assembled for this change.
