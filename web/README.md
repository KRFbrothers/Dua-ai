# Dua web app

Production runs at https://dua-cyan.vercel.app with static pages and server-only Vercel API functions. Android uses the same backend.

Use Node 24+. Copy `.env.example` to an ignored `.env` for local configuration, then run:

```sh
npm start
npm test
npm run build
```

Chat supports configurable providers, bounded model-supported attachments, history, draft recovery, copy/retry/speaker actions, voice input and device speech. Web: Off/Search/Read link modes add source-backed replies and clickable links. Local appearance, reviewed memory, sharing and data controls are included. Supabase provides email accounts, reviewed snapshots and opt-in foreground automatic sync on web and Android.

Feature setup reports provider/search availability, a limited read-only GitHub connector, browser local media preview, optional generated reply audio and account recovery/protection. Configuration and incomplete capabilities are documented in [EIGHT-FEATURE-SETUP.md](../EIGHT-FEATURE-SETUP.md), [PROVIDERS.md](../PROVIDERS.md) and [SUPABASE.md](../SUPABASE.md). Production and release checks are in [RELEASE-CHECKLIST.md](../RELEASE-CHECKLIST.md). Preparing an adapter does not activate an external service.

See [FEATURES.md](../FEATURES.md) for current scope, [VERCEL.md](../VERCEL.md) for hosting and [SUPABASE.md](../SUPABASE.md) for cloud ownership. Endpoints run on Vercel or the local Node handler.

Keep API keys server-side. Never put private credentials in `dist`, Android, exports or GitHub.
