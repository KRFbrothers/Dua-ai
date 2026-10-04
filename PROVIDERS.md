# AI providers and web search setup

Dua and Gemini retain the existing Gemini setup and attachment support. ChatGPT, Claude, Grok and Kimi now have server adapters for text conversations, history, retry and optional memory context. Their setup remains inactive until provider credentials and a model ID are configured. This change does not provision keys or create subscriptions.

## Server variables

Add variables to Vercel's server environment or a local ignored `.env` file. API keys must never be added to Android resources, browser JavaScript, public `NEXT_PUBLIC_` variables or GitHub commits.

| Choice | API key | Model ID |
| --- | --- | --- |
| Dua / Gemini | `GEMINI_API_KEY` | `GEMINI_MODEL` |
| ChatGPT | `OPENAI_API_KEY` | `OPENAI_MODEL` |
| Claude | `ANTHROPIC_API_KEY` | `ANTHROPIC_MODEL` |
| Grok | `GROK_API_KEY` | `GROK_MODEL` |
| Kimi | `KIMI_API_KEY` | `KIMI_MODEL` |

Use an API model ID available to your account, rather than a marketing label such as “Instant” or the app choice “ChatGPT”. Explicit model configuration avoids silently selecting a paid or retired model. Endpoints are fixed to OpenAI Responses, Anthropic Messages, xAI chat completions and Moonshot international chat completions. Kimi needs credentials for `api.moonshot.ai`, rather than the separate China API. Custom base URLs are unsupported.

The earlier decision to defer OpenAI key setup remains in effect. No key was created, inspected or used during development. Verification uses mocked responses. Provider APIs can have separate billing from consumer chat subscriptions; enable only providers you want.

Redeploy after setting server variables. `/api/config` exposes provider availability, model IDs and attachment capabilities, without keys. Missing credentials or a model ID leave that provider unavailable. Only Dua/Gemini support attachments; the other adapters reject media explicitly. Requests support cancellation, a 45-second provider timeout, bounded history/output and sanitized errors. OpenAI requests use `store: false`.

## Web search

Set `BRAVE_SEARCH_API_KEY` for Brave Search API access. Explicit `POST /api/search` JSON `{ "query": "your search" }` returns `{ "query": "your search", "results": [{ "title": "...", "url": "https://...", "snippet": "..." }] }` with at most five results.

Search is separate from ordinary chat; queries are sent when search is invoked. Links are bounded, deduplicated and HTTPS only. Links with embedded credentials and literal IP/local addresses are excluded. Snippets are plain text and untrusted source material rather than instructions. There is no automatic webpage crawling. Search times out after 15 seconds. Missing credentials and rate limits return setup guidance instead of invented results.

No live provider or search requests were made during development. Before personal use, enable one provider at a time and check a prompt, retry/stop, quota handling and (for Brave) working citation links.
