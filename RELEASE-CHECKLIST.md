# Personal deploy and install checklist

Dua is a single-user app for its owner. This checklist covers deploying the backend for the owner and installing the app on the owner's own Android device(s). There is no Google Play release, store listing or public distribution.

It describes checks to run; it is not evidence that production, staging, or a physical device has passed them. The repository checkout cannot read Vercel/Supabase dashboard settings. Do not put credentials, tokens, passwords, or signing material in this repository, issues, screenshots, or chat.

## Owner-only lock and spend controls

The owner-only lock is the permanent access model: it keeps the server-side keys and provider spend limited to the owner. Before enabling online AI in Vercel Production:

- [ ] In Vercel project settings, verify `DUA_REQUIRE_LOGIN=1`.
- [ ] Set `DUA_ALLOWED_EMAIL` to the owner's account. Login-required alone permits any account accepted by Supabase; it does not restrict access to the owner.
- [ ] As a daily backstop, apply `supabase/migrations/202610040002_dua_usage.sql`, set `DUA_DAILY_REQUEST_LIMIT` and the server-only `SUPABASE_SERVICE_ROLE_KEY`, then redeploy. Keep the admin key out of client/public variables.
- [ ] Check `GET https://YOUR-PRODUCTION-DOMAIN/api/setup`. Confirm `account.requireLogin` and `account.ownerRestricted` are `true`; confirm `account.dailyRequestLimit` is the intended positive limit when using the durable quota. This endpoint reports configuration, not proof that the quota's database RPC works.
- [ ] In a separate staging deployment, verify an anonymous request is denied, the owner can use online AI, a different (disposable) signed-in account is denied, and the daily quota returns `429` after the configured number of attempts. Failed upstream attempts consume quota. Avoid quota-exhaustion or provider-billing tests against production.
- [ ] In each enabled AI, search, and generated-audio provider dashboard, set usage/spending alerts or hard limits where available. The app's daily quota counts requests, not tokens or currency; one chat can trigger both search and a model call. Vercel's IP burst guard is per instance and is not a spending limit.
- [ ] Review Supabase Auth signup/SMTP limits and recovery settings. Confirm that server-only credentials are configured only in the production environment and that preview credentials are separate.
- [ ] Review the tracked Firebase client configuration and restrict its API key in Google Cloud to the required APIs and Android application where supported. A client Firebase key is not a server secret, but it should not be left unrestricted.

**Production status:** Not verified from this repository session. The status endpoint could not be reached from the audit environment; check these values directly in Vercel and validate behavior in staging before marking the roadmap configured/verified.

## API and deployment runbook

1. Review the change and ensure the `Project checks` workflow passes. Run `bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2` from the repository root.
2. Deploy the backend API (`web/`) from the intended branch with Vercel Root Directory `web`, Node.js 24, and the production-only secrets configured in Vercel. Never copy secret values into Android resources or client configuration.
3. Apply reviewed Supabase migrations before enabling their dependent features. Verify RLS, grants, and function permissions in the Supabase SQL editor; do not grant clients direct snapshot writes or access to daily usage rows.
4. After deployment, smoke-check `/api/config`, `/api/setup`, and `/api/account/session`. (There is no web client; `/` is not served.) Confirm that responses contain no API keys, service-role key, or owner email. `/api/setup` may expose feature availability and access-policy booleans.
5. In staging, check the owner lock with the owner account and one disposable non-owner account: sign-in, refresh, logout, cloud ownership/conflicts, and quota enforcement. Test account deletion only with the disposable account.
6. For a failed backend deployment, redeploy the last known-good Vercel deployment. Treat database migrations separately: do not assume rolling back the backend deployment reverses schema changes.
7. Mark a feature configured only after checking deployed environment values, and verified only after successful live/staging behavior. Keep those labels separate from repository unit/mock test results.

## Staging provider checks

- [ ] Use staging-only provider credentials and set the smallest available provider-side budget/limit before sending a request.
- [ ] Test one text request per enabled model; record model ID, date, status and sanitized outcome, not prompts, tokens, or secret values.
- [ ] Test only attachment MIME types explicitly enabled for the configured model.
- [ ] Test stop/cancel, provider timeout, quota rejection, malformed provider response, and retry without changing local data.
- [ ] If enabling Brave Search, verify source links and the no-results/error path. If enabling generated voice, verify user initiation and audio cleanup.
- [ ] Disable staging credentials/features after validation if they are not needed.

No live provider or staging checks were performed as part of this repository change.

## Android device and backup checks

Use non-production test data on an emulator or the owner's phone; do not use real conversations for backup or deletion experiments. Current app backup is enabled; chat history and ordinary app preferences are not excluded by the checked-in backup rules.

- [ ] Create chats, drafts and local memory; force-stop/restart the app and verify expected persistence.
- [ ] Verify Android backup/device-transfer behavior with a test profile, including what data is restored.
- [ ] Test sign-in, token refresh, expiry, logout, and that a non-owner account is denied. Confirm local chats remain on logout and are not uploaded without explicit sync consent.
- [ ] Test microphone permission allow/deny/revoke, speech-service unavailable, transcript review, hands-free Stop/background behavior, and text-to-speech. Confirm the device speech service may process audio remotely.
- [ ] Test camera capture and photo/document pickers, cancellation, rotation/background interruption, oversized image resizing, and cleanup.
- [ ] Test JSON export/import using Hindi/emoji, malformed/oversized input, cancel, duplicate import, and storage failure. Confirm import does not silently replace existing chats.
- [ ] Test clear-local-data, clear-cloud, and account deletion (disposable account) independently; verify which data remains after each operation.
- [ ] Check small-screen layouts, TalkBack/keyboard navigation, notification permission and notification tap behavior.

These checks require a device/emulator and are not covered by the current JVM-only CI job.

## Personal install

The app is installed directly on the owner's device(s); there is no store upload.

**Debug APK (simplest).** No signing setup is needed; Gradle signs with the build machine's Android debug key. Crashlytics collection is disabled in debug builds.

- [ ] Build with `bash ./gradlew :app:assembleDebug --no-daemon` (output: `app/build/outputs/apk/debug/app-debug.apk`), or use Android Studio's Run.
- [ ] Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the phone and allow installing from that source.
- [ ] Confirm the app reaches the intended backend (default `https://dua-cyan.vercel.app`; override with `duaApiBaseUrl` / `DUA_API_BASE_URL` at build time) and that signing in with the owner account works.

**Release APK with a personal key (optional).** Release builds read signing values from Gradle properties or environment variables; credentials are intentionally not checked in. `assembleRelease` fails unless a complete signing configuration and existing keystore are supplied. Crashlytics collection is enabled in release builds.

- `DUA_RELEASE_STORE_FILE` — path to the keystore file.
- `DUA_RELEASE_STORE_PASSWORD`
- `DUA_RELEASE_KEY_ALIAS`
- `DUA_RELEASE_KEY_PASSWORD`

The matching Gradle properties are `duaReleaseStoreFile`, `duaReleaseStorePassword`, `duaReleaseKeyAlias`, and `duaReleaseKeyPassword`. Prefer environment variables. Do not commit the keystore or place passwords in tracked `gradle.properties`.

- [ ] Create a personal keystore and back it up privately.
- [ ] Run `bash ./gradlew :app:assembleRelease --no-daemon` with the signing values present, then install the APK as above.
- [ ] Check the version code/name, permissions, backend URL and Crashlytics behavior.

**Updates.** Android only installs an update signed with the same key as the installed app. Switching between debug and release builds, or building on a machine with a different debug key, requires uninstalling first, which deletes local chats. Export chats (or sync to the cloud) before uninstalling.

No signing keys, Android release workflow or signed artifacts are supplied by this repository.
