# Dua web app

Production runs at https://dua-cyan.vercel.app with static pages and server-only Vercel API functions. Android uses the same backend.

Use Node 24+. Copy `.env.example` to an ignored `.env` for local configuration, then run:

```sh
npm start
npm test
npm run build
```

Existing Gemini chat supports text, bounded image/PDF/TXT attachments, history, draft recovery, voice input and device speech. Local appearance, memory, sharing and data controls are included. Supabase provides email accounts and reviewed manual cloud snapshots.

The next eight integration groups have a Feature setup page, provider adapters, explicit search, web opt-in sync, reviewed memory proposals, a read-only GitHub connector, local media preview, optional generated audio and account recovery/protection. Configuration and incomplete capabilities are documented in [EIGHT-FEATURE-SETUP.md](../EIGHT-FEATURE-SETUP.md). Preparing an adapter does not activate an external service.

See [FEATURES.md](../FEATURES.md) for current scope, [VERCEL.md](../VERCEL.md) for hosting and [SUPABASE.md](../SUPABASE.md) for cloud ownership. New setup endpoints use Vercel/local Node; legacy Netlify functions retain earlier Gemini behavior.

Keep API keys server-side. Never put private credentials in `dist`, Android, exports or GitHub.
