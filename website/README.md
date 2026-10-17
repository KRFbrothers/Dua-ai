# Dua AI website

A personal web version of Dua AI (Vite + React + TypeScript). It is built into `web/site`
and served by **dua-api** at `/`, on the same origin as `/api/*`, so sign-in cookies and
same-origin checks just work. No separate hosting, no extra cost.

- Sign-in: dua-api email + password (`/api/account/email-login`), the same Supabase owner
  account as the Android app. Only `DUA_ALLOWED_EMAIL` can sign in. If you normally use
  Google in the app, set an email password from the app's Account screen first.
- Logo: copied at build time from `app/src/main/res/drawable-nodpi/dua_logo_home.webp`.
- Chat streaming arrives in the next PR; this one ships the layout and sign-in.

## Local development

```bash
cd website
npm ci
DUA_API_URL=https://dua-api-228305912525.us-central1.run.app npm run dev   # http://localhost:5173
```

## Deploy

`PROJECT_ID=global-healer-m6shk bash web/deploy-cloudrun.sh` builds this folder into
`web/site` and ships it inside the dua-api image.

## Engine access from the browser (dua-ai-engine)

`google-cloud-backend/web_cors.py` allows browser calls from `WEB_ORIGINS` (default: the
dua-api URL and `http://localhost:5173`). `WEB_SKIP_APP_TOKEN=1` lets allowed web origins
skip the optional `DUA_APP_TOKEN` gate; owner login is always required.
