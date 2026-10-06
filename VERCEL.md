# Dua personal hosting on Vercel Hobby

Production uses `https://dua-cyan.vercel.app` on Vercel Hobby for this personal app, with the existing Supabase Free project. Both plans have quotas; model/search/audio provider usage is separate. Legacy Netlify files retain the earlier backend contract.

## Import the repository

1. In Vercel, select your Hobby account, Add New → Project, and import the private `KRFbrothers/Dua-ai` GitHub repository. Grant the Vercel GitHub app access to that repository if prompted.
2. Set **Root Directory** to `web`, **Framework Preset** to Other, **Node.js** to 24.x. The checked-in `web/vercel.json` sets build command `npm run build` and output directory `dist`. Eight function entry files live in `web/api`, including dynamic account and voice routes; shared backend code stays outside the published static directory.
3. Add the existing server environment values to Production:
   - `GEMINI_API_KEY` — your existing Gemini key, encrypted/sensitive.
   - `GEMINI_MODEL` — the actual enabled model ID from your current configuration.
   - `SUPABASE_URL` — your existing project's HTTPS URL.
   - `SUPABASE_ANON_KEY` — its public publishable/anon key, never service-role/secret.
   Do not commit these values or paste passwords/private keys into chat. Set Preview values separately only when intentionally testing API access on a preview deployment. The old Netlify environment does not automatically transfer.
4. Deploy from the merged main branch. Copy the stable **production project domain** from Vercel, not a temporary branch/deployment URL. Verify `/`, `/chat.html`, `/api/config`, and `/api/account/session`.
5. In Supabase Authentication → URL Configuration, use `https://dua-cyan.vercel.app/profile.html` as Site URL and add that exact URL to Redirect URLs. Substitute your own stable production domain for another deployment. Keep the existing email provider/SMTP, table, RLS and SQL function. Optional web Google login needs its separate callback configuration described in [EIGHT-FEATURE-SETUP.md](EIGHT-FEATURE-SETUP.md#optional-server-variables).

The web app uses plain HTML/JS and Node API functions. Configuration names and optional integrations are documented in [web/.env.example](web/.env.example) and [EIGHT-FEATURE-SETUP.md](EIGHT-FEATURE-SETUP.md); keys remain server-side.

## Android backend

Android already defaults to `https://dua-cyan.vercel.app`. For source checks against another HTTPS backend, override the Gradle property:

```bash
bash ./gradlew :app:testDebugUnitTest -PduaApiBaseUrl=https://YOUR-PRODUCTION-DOMAIN
```

Alternatively set `DUA_API_BASE_URL`; the Gradle property takes precedence over the environment, followed by the Vercel production default. Routine tests compile source without assembling an APK. Website deployment updates the web/backend; changing installed native code or its compiled backend URL requires a later Android release. APK packaging is currently deferred.

Changing backend invalidates the endpoint-bound encrypted native session; sign in again. Existing local chats/drafts/notes remain. Preview deployment protection should stay enabled. Test protected previews with authenticated Vercel tooling; use the production domain for a normal Android client.

## Local data when changing web domains

Browser chats/preferences are origin-scoped. Vercel cannot read the old Netlify browser storage. Export local chats from Data controls on the old site and import on the new site, or explicitly upload/download a cloud snapshot after account setup. Review memory notes separately if using JSON chat export; that export does not include memory. The Android local store remains on the device.

## Limits and checks

Vercel functions use a 60-second maximum duration. Chat has a 55-second deadline including authentication/quota work; model calls have a 45-second timeout. Search and public page retrieval have 15-second limits. App upload bounds remain below the platform's 4.5 MB request cap. API responses are no-store and secure account cookies are retained. Eight function entry files under `web/api/` cover config, chat, cloud, account actions/Google callback, search, setup, GitHub listing and generated audio.

The Vercel adapter includes bounded **per-instance** burst guards: five chat/search/optional connector/audio requests or twenty account/cloud requests per IP per minute. Cold starts and different instances have independent windows. Optional Supabase-verified login/owner policies and a durable per-account daily request-attempt limit are implemented; see [EIGHT-FEATURE-SETUP.md](EIGHT-FEATURE-SETUP.md#start-with-the-existing-services) and [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md#production-access-and-spend-controls). Request counts do not cap currency spending. Provider quotas/spending controls and Supabase Auth limits remain separate.

Local verification uses mocked providers and does not spend your quota. It covers API routing, browser/native token responses, cookie preservation, cloud ownership/conflicts and burst rejection. Live credentials, production deployment and physical Android login checks must be performed after the account connection and environment setup.

Before release, follow the production, staging, device and signing gates in [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

## Production Security Configuration

Before deploying to production, configure these environment variables in Vercel:

1. **DUA_REQUIRE_LOGIN** (Required)
   - Set to `1` to enforce login requirement
   - Default: disabled (test/dev mode)
   - Production: MUST be set to `1`

2. **DUA_ALLOWED_EMAIL** (Optional)
   - Restrict access to specific email address
   - Default: any authenticated user

3. **DUA_DAILY_REQUEST_LIMIT** (Optional)
   - Set daily API request limit (e.g., 100)
   - Default: unlimited

## Google sign-in (web and Android)

Dua uses Supabase Auth as the Google OAuth broker, so the Google client secret never reaches Vercel or the apps.

1. In Google Cloud create an OAuth *Web application* client. Add Supabase's callback (`https://<project>.supabase.co/auth/v1/callback`) as an authorized redirect URI. Put the client ID and secret in Supabase → Authentication → Providers → Google (`GOOGLE_CLIENT_SECRET` stays only there).
2. **Web** (authorization code + PKCE): set `SUPABASE_AUTH_REDIRECT_URL=https://<your-domain>/api/account/oauth/callback` and add it to Supabase Redirect URLs. Tokens are kept in HttpOnly, Secure cookies; `/api/account/session`, `/api/account/refresh` and `/api/account/logout` provide session check, refresh and sign-out.
3. **Android**: create an Android OAuth client (package `com.dua.ai`, your signing SHA-1) in the same Google project, add the *web* client ID to Supabase's "Authorized Client IDs", and build with `GOOGLE_CLIENT_ID=<web-client-id>` (or `-PduaGoogleWebClientId=...`). The app sends the Google ID token with a random nonce to `POST /api/account/google-token`; Supabase verifies signature, audience, expiry and nonce. The resulting session is stored encrypted with an Android Keystore key and refreshed automatically.
4. Access control is unchanged: `DUA_REQUIRE_LOGIN`, `DUA_ALLOWED_EMAIL` and `DUA_DAILY_REQUEST_LIMIT` apply to Google-authenticated accounts.
