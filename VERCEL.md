# Dua personal hosting on Vercel Hobby

Use Vercel Hobby for this personal/non-commercial app and retain the existing Supabase Free project. Both plans have quotas; Gemini usage remains separate. This change adds Vercel support while keeping Netlify configuration so the existing published site is not deleted.

## Import the repository

1. In Vercel, select your Hobby account, Add New → Project, and import the private `KRFbrothers/Dua-ai` GitHub repository. Grant the Vercel GitHub app access to that repository if prompted.
2. Set **Root Directory** to `web`, **Framework Preset** to Other, **Node.js** to 24.x. The checked-in `web/vercel.json` sets build command `npm run build` and output directory `dist`. Four serverless entry points live in `web/api`; shared backend code stays outside the published static directory.
3. Add the existing server environment values to Production:
   - `GEMINI_API_KEY` — your existing Gemini key, encrypted/sensitive.
   - `GEMINI_MODEL` — the actual enabled model ID from your current configuration.
   - `SUPABASE_URL` — your existing project's HTTPS URL.
   - `SUPABASE_ANON_KEY` — its public publishable/anon key, never service-role/secret.
   Do not commit these values or paste passwords/private keys into chat. Set Preview values separately only when intentionally testing API access on a preview deployment. The old Netlify environment does not automatically transfer.
4. Deploy from the merged main branch. Copy the stable **production project domain** from Vercel, not a temporary branch/deployment URL. Verify `/`, `/chat.html`, `/api/config`, and `/api/account/session`.
5. In Supabase Authentication → URL Configuration, change Site URL to `https://YOUR-PRODUCTION-DOMAIN/profile.html` and add that exact URL to Redirect URLs. Keep your existing email provider/SMTP, table, RLS and SQL function; no new database is needed. Leave old redirect URLs only while you intentionally use the old site.

The current local app is plain HTML/JS, not Next.js. Do not add Next.js scaffolding or expose server keys as NEXT_PUBLIC variables. A project import before this change is merged will not contain the Vercel API functions.

## Android backend

Rebuild Android with the same production URL:

```bash
bash ./gradlew :app:assembleDebug -PduaApiBaseUrl=https://YOUR-PRODUCTION-DOMAIN
```

Or set the `DUA_API_BASE_URL` environment variable before building; the Gradle property takes precedence over the environment, and `https://dua-cyan.vercel.app` is the default backend. Normal Android Studio Run uses that production URL unless you set `duaApiBaseUrl` in your local Gradle properties or configure the environment. A hosted deployment cannot change an already installed APK: rebuild and install it.

Changing backend invalidates the endpoint-bound encrypted native session; sign in again. Existing local chats/drafts/notes remain. Preview deployment protection should stay enabled. Test protected previews with authenticated Vercel tooling; use the production domain for a normal Android client.

## Local data when changing web domains

Browser chats/preferences are origin-scoped. Vercel cannot read the old Netlify browser storage. Export local chats from Data controls on the old site and import on the new site, or explicitly upload/download a cloud snapshot after account setup. Review memory notes separately if using JSON chat export; that export does not include memory. The Android local store remains on the device.

## Limits and checks

Vercel functions use 60-second max duration; the shared Gemini timeout is 45 seconds. App upload bounds remain below the traditional 4.5 MB platform request cap. API responses are no-store and secure account cookies are retained. Four function entries handle `/api/config`, `/api/chat`, `/api/cloud`, `/api/account/:action`; unknown routes do not serve a fake successful API response.

The Vercel adapter includes bounded **per-instance** burst guards: five chat requests or twenty account/cloud requests per IP per minute. This is weaker than Netlify's edge-enforced distributed limits: cold starts and different instances have independent windows. Configure a Vercel Firewall rate-limit rule for `/api/chat` if your current plan supports it; do not assume the code guard is a project-wide daily budget. Gemini quotas and Supabase Auth rate limits remain in effect. Account-based Gemini access and global spend budgets are future work.

Local verification uses mocked providers and does not spend your quota. It covers API routing, browser/native token responses, cookie preservation, cloud ownership/conflicts and burst rejection. Live credentials, production deployment and physical Android login checks must be performed after the account connection and environment setup.
