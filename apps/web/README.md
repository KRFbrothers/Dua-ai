# Dua AI website

A personal web version of Dua AI (Vite + React + TypeScript). It is built into `apps/api/site`
and served by **dua-api** at `/`, on the same origin as `/api/*`, so sign-in cookies and
same-origin checks just work. No separate hosting, no extra cost.

- Sign-in: dua-api email + password (`/api/account/email-login`), the same Supabase owner
  account as the Android app. Only `DUA_ALLOWED_EMAIL` can sign in. If you normally use
  Google in the app, set an email password from the app's Account screen first.
- Original artwork and licensed Inter are copied at build time from the existing
  Android assets and the SVG conversions in `design/assets`.
- The connected client uses the APK's Chat/Task structure, one greeting/composer,
  themed original artwork and a searchable, read-only view of Android cloud history.
- The normal same-origin host streams replies and retains its HttpOnly sessions
  and explicit Agent approval flow.

## AppDeploy connection

The existing public preview can run this same client through `hosting/entry.ts`,
mapped to the host's SDK entry. `hosting/backend.ts` and `dua-bridge.mjs` form a
fixed-origin, JSON-only gateway to the owner's existing Dua API. Provider keys,
owner checks, request quotas and model configuration stay on that server.

Run `node hosting/prepare.mjs > /tmp/dua-host-source.json` to generate source from
the canonical client, with the existing artwork and licensed font embedded.
The packager excludes the cookie/SSE fallback using the TypeScript syntax tree;
the host uses only the installed SDK connection. The existing HTML-static host
needs React/ReactDOM versions from this package and `esbuild.jsx: 'automatic'`
in its Vite configuration; preserve the host's other settings when updating.
Keep its owner-gate, invalid-form and connection-failure QA scenarios in
`tests/tests.json`. Positive owner workflows require owner sign-in and must not
use an authentication bypass.

This host uses the existing native email-login/refresh endpoints. Access and
refresh tokens live only in the page's module memory, never browser storage;
reloading requires another sign-in. Passwords are not persisted. Backend calls
use `api` from the AppDeploy client and `router/json` from its server SDK.

The hosted bridge receives complete text replies together, with a 25-second
upstream deadline. Stop waiting discards the result in the browser; it cannot
promise cancellation of server work. Task agents, provider approvals, attachments,
dictation and live voice are explicitly unavailable on this host. The same-origin
Cloud Run client retains its streaming/Agent transport.

Cloud history is authenticated and read-only. New browser conversations remain
in the current tab and are not uploaded to, or merged over, Android data.

Validation: `npm run build`, `npm test`, and (with local Vite at port 4175 and
Python Playwright/Chromium) `python test/connected_browser.py`. The latter uses
controlled SDK/HTTP fixtures; it does not sign in as the real owner. Real account
sign-in and an authenticated live reply require the owner to use the published UI.

Composer regression checks can also run against the built same-origin site with
`node test/composer_browser.mjs` (requires an externally installed `playwright-core`
and its Chromium). Set `DUA_BROWSER_TEST_URL` for the local site,
`DUA_BROWSER_CDP_URL` to reuse an isolated debugging browser, and
`DUA_PLAYWRIGHT_MODULE` to an installed module URL if it is outside this package.
`DUA_BROWSER_ARTIFACTS` selects the screenshot/JSON report directory. The checks
use controlled HTTP fixtures and never sign in as the owner or call live AI.

## Local development

```bash
cd apps/web
npm ci
DUA_API_URL=https://dua-api-228305912525.us-central1.run.app npm run dev   # http://localhost:5173
```

## Deploy

`PROJECT_ID=global-healer-m6shk bash apps/api/deploy-cloudrun.sh` builds this folder into
`apps/api/site` and ships it inside the dua-api image.

## Engine access from the browser (dua-ai-engine)

`apps/ai-engine/web_cors.py` allows browser calls from `WEB_ORIGINS` (default: the
dua-api URL and `http://localhost:5173`). `WEB_SKIP_APP_TOKEN=1` lets allowed web origins
skip the optional `DUA_APP_TOKEN` gate; owner login is always required.
