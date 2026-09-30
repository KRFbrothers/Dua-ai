# Dua

Dua is a mobile-style chat workspace. Conversations, settings, and the image gallery stay in this browser. A built-in demo model answers without credentials. OpenAI, Anthropic, and Gemini models appear in the model menu when their API keys are set. Settings also has Your LLM, which stores an OpenAI-style address, model name, and optional key in this browser.

The interface includes onboarding, chat with streaming replies, search, profile, appearance and language settings, voice, and local image previews.

## Run

```bash
npm ci
npm run dev
```

Open http://localhost:3000.

## Scripts

| Command | Purpose |
| --- | --- |
| `npm run dev` | Development server on port 3000 |
| `npm test` | Unit tests for replies, streaming parsers, and model availability |
| `npm run lint` | Typecheck with `tsc --noEmit` |
| `npm run build` | Production build |

## Models

Copy `.env.example` to `.env.local` and set any keys you have:

| Variable | Models |
| --- | --- |
| `OPENAI_API_KEY` | GPT-4o mini, GPT-4o |
| `OPENAI_BASE_URL` | Optional OpenAI-compatible base URL |
| `ANTHROPIC_API_KEY` | Claude Sonnet 4.5 |
| `GEMINI_API_KEY` | Gemini 2.5 Flash |

`Dua Demo` remains available with no keys. For any other OpenAI-style service, open Settings, choose Your LLM, and enter the address plus model name. The key stays in this browser and is sent only with that chat request. Public addresses must use https. A server on this computer can use `http://127.0.0.1`.

## In the app

- New chat, search, rename, pin, and delete
- Streaming replies, stop, regenerate, and edit-and-resend
- Markdown, tables, and copyable code blocks
- Instructions and temperature
- Light and dark themes
- Export the active chat as Markdown
- `Ctrl` or `Cmd` + `K` starts a new chat
