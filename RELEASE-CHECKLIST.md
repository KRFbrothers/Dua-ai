# Release and production verification checklist

This runbook describes checks for a release; it is not evidence that production, staging, or a physical device has passed them. The repository checkout cannot read Vercel/Supabase dashboard settings. Do not put credentials, tokens, passwords, or signing material in this repository, issues, screenshots, or chat.

## Production access and spend controls

Before enabling online AI in Vercel Production:

- [ ] In Vercel project settings, verify `DUA_REQUIRE_LOGIN=1`.
- [ ] For a personal deployment, set `DUA_ALLOWED_EMAIL` to the intended owner account. Login-required alone permits any account accepted by Supabase; it does not restrict access to one owner.
- [ ] If using a durable request quota, apply `supabase/migrations/202610040002_dua_usage.sql`, set `DUA_DAILY_REQUEST_LIMIT` and the server-only `SUPABASE_SERVICE_ROLE_KEY`, then redeploy. Keep the admin key out of client/public variables.
- [ ] Check `GET https://YOUR-PRODUCTION-DOMAIN/api/setup`. Confirm `account.requireLogin` and, for a personal deployment, `account.ownerRestricted` are `true`; confirm `account.dailyRequestLimit` is the intended positive limit when using the durable quota. This endpoint reports configuration, not proof that the quota's database RPC works.
- [ ] In a separate staging deployment, verify an anonymous request is denied, the configured owner can use online AI, a different signed-in account is denied when owner restriction is configured, and the daily quota returns `429` after the configured number of attempts. Failed upstream attempts consume quota. Avoid quota-exhaustion or provider-billing tests against production.
- [ ] In each enabled AI, search, and generated-audio provider dashboard, set provider-side usage/spending alerts or hard limits where available. The app's daily quota counts requests, not tokens or currency; one chat can trigger both search and a model call. Vercel's IP burst guard is per instance and is not a spending limit.
- [ ] Review Supabase Auth signup/SMTP limits and recovery settings. Confirm that server-only credentials are configured only in the production environment and that preview credentials are separate.
- [ ] Review the tracked Firebase client configuration and restrict its API key in Google Cloud to the required APIs and Android application where supported. A client Firebase key is not a server secret, but it should not be left unrestricted.

**Production status:** Not verified from this repository session. The public status endpoint could not be reached from the audit environment; check these values directly in Vercel and validate behavior in staging before marking the roadmap configured/verified.

## API and deployment runbook

1. Review the change and ensure the `Project checks` workflow passes. Run `npm test` and `npm run build` from `web/`; run `bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2` from the repository root.
2. Deploy the web project from the intended branch with Vercel Root Directory `web`, Node.js 24, and the production-only secrets configured in Vercel. Never copy secret values into `web/dist`, Android resources, or public client configuration.
3. Apply reviewed Supabase migrations before enabling their dependent features. Verify RLS, grants, and function permissions in the Supabase SQL editor; do not grant clients direct snapshot writes or access to daily usage rows.
4. After deployment, smoke-check `/`, `/chat.html`, `/api/config`, `/api/setup`, and `/api/account/session`. Confirm that public responses contain no API keys, service-role key, or owner email. `/api/setup` may expose feature availability and access-policy booleans.
5. Exercise one staging account and one non-owner account before enabling owner restrictions for real users. Check sign-in, refresh, logout, account deletion only with a disposable test account, cloud ownership/conflicts, and quota enforcement.
6. For a failed web deployment, redeploy the last known-good Vercel deployment. Treat database migrations separately: do not assume rolling back the web deployment reverses schema changes.
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

Use a dedicated test account and non-production test data on a supported emulator and at least one physical Android device. Current app backup is enabled; chat history and ordinary app preferences are not excluded by the checked-in backup rules.

- [ ] Create chats, drafts and local memory; force-stop/restart the app and verify expected persistence.
- [ ] Verify Android backup/device-transfer behavior with a test profile, including what data is restored. Do not use real conversations for backup experiments.
- [ ] Test sign-up/sign-in, token refresh, expiry, logout, denied login, owner restriction and account switching. Confirm local chats remain on logout and are not uploaded without explicit sync consent.
- [ ] Test microphone permission allow/deny/revoke, speech-service unavailable, transcript review, hands-free Stop/background behavior, and text-to-speech. Confirm the device/browser speech service may process audio remotely.
- [ ] Test camera capture and photo/document pickers, cancellation, rotation/background interruption, oversized image resizing, and cleanup.
- [ ] Test JSON export/import using Hindi/emoji, malformed/oversized input, cancel, duplicate import, and storage failure. Confirm import does not silently replace existing chats.
- [ ] Test clear-local-data, clear-cloud, and account deletion independently; verify which data remains after each operation.
- [ ] Check small-screen layouts, TalkBack/keyboard navigation, notification permission and notification tap behavior.

These checks require a device/emulator and are not covered by the current JVM-only CI job.

## Android signing and artifact preparation

Release signing is configured to read values from Gradle properties or environment variables; credentials are intentionally not checked in. `assembleRelease` and `bundleRelease` fail unless a complete signing configuration and existing keystore are supplied.

Provide these values securely through a local protected environment or an approved CI secret store:

- `DUA_RELEASE_STORE_FILE` — path to the keystore file.
- `DUA_RELEASE_STORE_PASSWORD`
- `DUA_RELEASE_KEY_ALIAS`
- `DUA_RELEASE_KEY_PASSWORD`

The matching Gradle properties are `duaReleaseStoreFile`, `duaReleaseStorePassword`, `duaReleaseKeyAlias`, and `duaReleaseKeyPassword`. Prefer environment/secret-store injection. Do not commit the keystore or place passwords in tracked `gradle.properties`.

Before distribution:

- [ ] Create and securely back up the release keystore using the organization's approved process; restrict access and document recovery/rotation ownership.
- [ ] Configure the release signing secrets in the trusted build environment, not in pull-request jobs from untrusted branches.
- [ ] Run `bash ./gradlew :app:assembleRelease --no-daemon` and/or `bash ./gradlew :app:bundleRelease --no-daemon` with the release signing values present.
- [ ] Verify the APK/AAB signature and application ID, version code/name, target SDK, permissions, backend URL, Crashlytics behavior and included Firebase configuration.
- [ ] Install the signed artifact on a clean test device, complete the device checks above, and retain the artifact/checksum and test record.
- [ ] Establish an explicit release channel and rollback/update procedure before publishing.

No signing keys, Android release workflow, signed artifacts, or app-store configuration are supplied by this repository.
