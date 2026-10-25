# Dua AI website

The personal web version of Dua AI. Source: [`apps/web/`](../apps/web) (Vite + React + TypeScript); developer notes: [apps/web/README.md](../apps/web/README.md).

URL: `https://dua-api-228305912525.us-central1.run.app/`

## Same-origin serving (#224)

- `apps/web/` is built into `apps/api/site` and shipped inside the **dua-api** image by [`apps/api/deploy-cloudrun.sh`](../apps/api/deploy-cloudrun.sh). No separate hosting or cost.
- [`static-site.mjs`](../apps/api/src/server/static-site.mjs) serves it for every non-`/api` path: SPA fallback to `index.html`, hashed `assets/` cached long-term, `index.html` not cached, CSP / `nosniff` / frame-deny headers, no files outside `apps/api/site`. `/api/*` is unchanged; without a built site `/` returns 404.
- [`server.mjs`](../apps/api/src/server/server.mjs) builds the request URL from `X-Forwarded-Proto`, so the same-origin checks in dua-api see Cloud Run's `https` origin.
- The DUA logo is copied at build time from `apps/android/app/src/main/res/drawable-nodpi/dua_logo_home.webp`.

## Sign-in (#226)

- Email + password through dua-api `POST /api/account/email-login`, the same Supabase owner account as the Android app. Only `DUA_ALLOWED_EMAIL` can sign in; others see an owner-only message.
- Allowed only for a same-origin browser request (`Origin` equals the dua-api origin, `Sec-Fetch-Site` absent or `same-origin`). The session is kept in HttpOnly, Secure, SameSite=Strict cookies scoped to `/api`; tokens are never returned to page scripts.
- Sign-up and password changes stay Android-only. Google sign-in is Android-only too: set an email password from the app's Account screen first.
- No Supabase redirect URL is needed (no OAuth redirect). Email auth with "Confirm email" must be enabled in Supabase.

## Engine access from the browser

[`web_cors.py`](../apps/ai-engine/web_cors.py) (wired by [`wsgi.py`](../apps/ai-engine/wsgi.py)) handles browser calls to dua-ai-engine:

| Setting | Default | Meaning |
| --- | --- | --- |
| `WEB_ORIGINS` | dua-api URL and `http://localhost:5173` | Comma-separated exact origins that get CORS headers and preflight answers. `off` disables; `*` is ignored. |
| `WEB_SKIP_APP_TOKEN` | off | `1` lets allowed web origins skip the optional `DUA_APP_TOKEN` gate (a browser cannot keep it secret). Owner login is always required. |

Streaming (SSE) replies pass through unbuffered; requests without `Origin` (Android) are unchanged.

## Chat (PR 2)

- The page calls dua-api `POST /api/web/chat` ([`service-handler.mjs`](../apps/api/src/server/service-handler.mjs)). It accepts only a same-origin browser request (`Origin` equals the dua-api origin, `Sec-Fetch-Site` absent or `same-origin`) with the `dua_access` HttpOnly cookie; otherwise 403 / 401 before any engine call.
- dua-api forwards `{model, messages, mode, stream: true}` to dua-ai-engine `/api/chat` with the cookie session as the owner `Authorization: Bearer` token (plus `X-Dua-App-Token` from `DUA_APP_TOKEN` when set) and streams the SSE reply back unbuffered. Tokens never reach page scripts.
- Engine URL: `DUA_ENGINE_URL` (optional) or the default `https://dua-ai-engine-228305912525.us-central1.run.app`. No deploy change is needed.
- Website: streamed replies with simple markdown (code blocks, inline code, bold), Agent progress label, Stop button, Chat/Agent toggle, model picker. A 401 refreshes the session once and retries.

## Roadmap

1. **PR 1 (#224, #226):** layout, same-origin serving, email sign-in, engine CORS — done.
2. **PR 2 — chat:** done (basic streamed chat, Agent toggle, stop). Still to do: sources, regenerate, thinking view. Original plan: streamed chat with the engine. The website's session lives in HttpOnly cookies the page cannot read, so the plan is a small dua-api **engine proxy** route (cookie session → owner bearer token → dua-ai-engine `/api/chat`, streamed back). Markdown, code blocks, model/mode picker, Agent progress chips, sources, stop/regenerate.
3. **PR 3 — history:** read/write the same chats as the phone through `/api/cloud`.
4. **PR 4 — extras:** attachments, Imagine, notes view, voice.
