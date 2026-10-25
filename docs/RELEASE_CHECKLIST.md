# Personal deploy and install checklist

Dua is a single-user app for its owner. This checklist covers deploying the backend for the owner and installing the app on the owner's own Android device(s). There is no Google Play release, store listing or public distribution.

It describes checks to run; it is not evidence that production, staging, or a physical device has passed them. The repository checkout cannot read Google Cloud/Supabase dashboard settings. Do not put credentials, tokens, passwords, or signing material in this repository, issues, screenshots, or chat.

## Completed reply saving (Phase 5)

- [ ] Deploy the backend first, install the updated APK, refresh Feature setup and confirm `/api/config.history.persistReplies` is true. The existing snapshot migration/RPC is required; no new migration or signing key is needed.
- [ ] With foreground automatic sync off, create a disposable chat and receive a complete reply. On a second owner installation, review a cloud download and confirm the prompt/reply IDs and content restore without uploading from the first phone.
- [ ] Follow up in a synced chat; regenerate its latest reply. Confirm earlier turns, title, pin, project, summary, source footer and code/Build metadata remain. Sync older imported/stopped offline turns before using them as server history.
- [ ] Stop a reply after text starts with automatic sync off. Confirm the cached partial reply remains and no new completed response is published. Reviewed/automatic sync may separately preserve stopped turns.
- [ ] In staging, edit/delete the target or clear the cloud while generating. Confirm saving reports a conflict and cannot resurrect/overwrite history; unrelated draft/title/pin/memory edits must survive.
- [ ] Simulate lost save acknowledgement and expired commit-time auth. Confirm text stays on the phone, cloud review recovers a committed response, and auth recovery does not silently trigger another generation. Verify no repeated recovery copies appear after a normal acknowledged save/restart/sync.

Source/mock tests do not establish live RPC deployment or physical-device behavior for this flow.

## Project memory

- [ ] Deploy the backend and update every APK that syncs this account; old APKs reject snapshots with the new project-memory field. Refresh Feature setup and confirm `projects.enabled` and `projects.memory` are true.
- [ ] In Projects, save distinct Coding and Business notes; sync, send fresh chats and verify replies use only the selected project's notes. Personal notes stay in Settings → Memory.
- [ ] Turn project memory off, sync and verify a new chat excludes notes while the editor retains them. Archive the project and confirm notes become read-only.
- [ ] Edit/delete a note, restart and review a cloud download. Confirm server notes and their switch restore without losing tasks/instructions. If both copies changed the project, review the conflict before proceeding.
- [ ] Export/import project-only JSON, verify project notes/switches are included and differing imported definitions become separate projects. Keep the export private; global Personal notes remain excluded.
- [ ] Check real-phone editor taps, scrolling, keyboard, rotation and returning to Projects. Automated tests do not establish physical-device/live-provider behavior. See [PROJECT_MEMORY.md](features/PROJECT_MEMORY.md).

## Owner-only lock and spend controls

The owner-only lock is the permanent access model: it keeps the server-side keys and provider spend limited to the owner. Before enabling online AI on dua-api (Cloud Run):

- [ ] On the `dua-api` Cloud Run service, verify `DUA_REQUIRE_LOGIN=1` (set by `web/deploy-cloudrun.sh`).
- [ ] Set `DUA_ALLOWED_EMAIL` to the owner's account. Login-required alone permits any account accepted by Supabase; it does not restrict access to the owner.
- [ ] As a daily backstop, apply `infra/database/migrations/202610040002_dua_usage.sql`, set `DUA_DAILY_REQUEST_LIMIT` and the server-only `SUPABASE_SERVICE_ROLE_KEY`, then redeploy. Keep the admin key out of client/public variables.
- [ ] Check `GET https://YOUR-PRODUCTION-DOMAIN/api/setup`. Confirm `account.requireLogin` and `account.ownerRestricted` are `true`; confirm `account.dailyRequestLimit` is the intended positive limit when using the durable quota. This endpoint reports configuration, not proof that the quota's database RPC works.
- [ ] In a separate staging deployment, verify an anonymous request is denied, the owner can use online AI, a different (disposable) signed-in account is denied, and the daily quota returns `429` after the configured number of attempts. Failed upstream attempts consume quota. Avoid quota-exhaustion or provider-billing tests against production.
- [ ] In each enabled AI, search, and generated-audio provider dashboard, set usage/spending alerts or hard limits where available. The app's daily quota counts requests, not tokens or currency; one chat can trigger both search and a model call. There is no IP burst guard on Cloud Run, and the daily quota is not a spending limit.
- [ ] Keep Google enabled. If using owner email authentication, enable Email with Confirm email on, verify SMTP delivery, and keep new sign-ups off after the owner account exists. Set `DUA_ALLOWED_EMAIL` to that account's email. Keep server-only credentials in production configuration and preview credentials separate.
- [ ] For email authentication, verify existing Google account → Set email password → logout → email sign-in retains the same account ID and server history. On a fresh owner installation, verify signup stays signed out until email confirmation and subsequent sign-in. Reject a non-owner email and disabled email-confirmation settings; test Back/cancellation and an uncertain password-update response without claiming completion.
- [ ] Review the tracked Firebase client configuration and restrict its API key in Google Cloud to the required APIs and Android application where supported. A client Firebase key is not a server secret, but it should not be left unrestricted.

**Production status:** Not verified from this repository session. The status endpoint could not be reached from the audit environment; check these values directly on the Cloud Run services and validate behavior in staging before marking the roadmap configured/verified.

## API and deployment runbook

1. Review the change and ensure both `Project checks` jobs (Android and Backend tests) pass. Run `bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2` from the repository root and `npm ci && npm test` from `web/`. CI retains Android test reports and a backend JUnit report; backend tests use fake provider responses and require no real provider keys.
2. Deploy the backend API (`web/`) to Cloud Run with `bash web/deploy-cloudrun.sh` (see [web/README.md](../web/README.md)); production secrets live in Secret Manager. Never copy secret values into Android resources or client configuration.
3. Apply reviewed Supabase migrations before enabling their dependent features. Verify RLS, grants, and function permissions in the Supabase SQL editor; do not grant clients direct snapshot writes or access to daily usage rows.
4. After deployment, smoke-check `/api/config`, `/api/setup`, and `/api/account/session`. (`/` serves the website from `web/site` when it was built at deploy; see [WEBSITE.md](WEBSITE.md).) Confirm that responses contain no API keys, service-role key, or owner email. `/api/setup` may expose feature availability and access-policy booleans.
5. In staging, check the owner lock with the owner account and one disposable non-owner account: sign-in, refresh, logout, cloud ownership/conflicts, and quota enforcement. Test account deletion only with the disposable account.
6. In staging, with two separate accounts, verify that `/api/files/sign`, `/api/files/url`, `/api/files/delete`, `/api/files/delete-all`, `/api/files/usage`, `/api/cloud`, `/api/imagine/*`, and `/api/voice/*` cannot read or mutate the other account's data. Verify a signed file URL expires and cannot be reused after expiry.
7. With the disposable account, create a cloud snapshot and upload a file, then run account deletion. Confirm the snapshot row, Storage objects, and account are gone; repeat the owner check afterward to ensure the configured owner still works.
8. For a failed backend deployment, route traffic back to the last known-good `dua-api` revision (Cloud Run → Revisions → Manage traffic). Treat database migrations separately: do not assume rolling back the backend deployment reverses schema changes.
9. Mark a feature configured only after checking deployed environment values, and verified only after successful live/staging behavior. Keep those labels separate from repository unit/mock test results.

## Staging provider checks

- [ ] Use staging-only provider credentials and set the smallest available provider-side budget/limit before sending a request.
- [ ] Test one text request per enabled model; record model ID, date, status and sanitized outcome, not prompts, tokens, or secret values.
- [ ] Test only attachment MIME types explicitly enabled for the configured model.
- [ ] Test stop/cancel, provider timeout, quota rejection, malformed provider response, and retry without changing local data.
- [ ] If enabling Brave Search, verify source links and the no-results/error path. If enabling generated voice, verify user initiation and audio cleanup.
- [ ] Disable staging credentials/features after validation if they are not needed.

No live provider or staging checks were performed as part of this repository change.

## Android device and backup checks

Use non-production test data on an emulator or the owner's phone; do not use real conversations for backup or deletion experiments. App backup is enabled, but chat history, account sessions and automatic-sync consent are excluded by the checked-in rules. Ordinary preferences, including memory and project instructions, may be backed up.

### Server-first restore sequence

Use two owner-controlled installations, or clear app data between each step:

1. On installation A, sign in and enable Cloud Sync for that device.
2. Create a chat containing a draft, memory note, text attachment context, stored-file reference/transcript, Run code result, Build metadata, Imagine state, and a long-context summary where available.
3. Upload/review the snapshot and record its revision.
4. On installation B, sign in to the same owner account and sync. Confirm the server snapshot restores the chat state without relying on installation A's cache.
5. Edit the same chat offline on A while changing the server copy on B. Sync A and confirm the server copy remains authoritative while the offline edit is retained as one clearly labelled `unsynced-*` copy.
6. Delete a memory note on A and sync. Confirm the documented memory-deletion policy removes that note from the server snapshot; add a new note and confirm it is retained.
7. Import a JSON export while signed out. Confirm it is labelled/treated as phone-cache data until an authenticated sync.
8. Clear app storage on B, sign in again, and confirm the server snapshot restores the intended state. Log out and confirm the server snapshot remains intact while signed-out chats are temporary cache-only data.
9. With a different cached draft for an existing server chat open, use Review download → Confirm. Confirm the server version keeps its original ID, the open chat displays the server draft/messages/model/summary/Build repository, and the phone version survives as an `unsynced-*` copy. Edit the restored draft, restart and verify the server messages remain. Repeat download without edits and verify no extra copy appears. Check that phone-only chats remain and server memory replaces cached memory, including an empty server memory list.
10. Review a download whose union with the phone cache would exceed 100 chats or 4 MiB. Confirm it fails before replacing chat history or memory; reduce the cache only after preserving any needed phone-only content.
11. Sync a test chat containing a stored upload and generated media. With automatic sync off, remove that cached chat, then restore it from the server and open both files. Repeat using Clear cached chats. Confirm neither action queues deletion of the saved files; discarded unsent temporary uploads may still be cleaned up. Check an old queued per-file delete against a server-referenced path returns retained without deleting it. Test Delete all uploaded files separately with disposable data; that reviewed action intentionally breaks file links.
12. With automatic sync enabled and an existing sync baseline, keep a chat open on A while changing its server version on B. Confirm A displays the downloaded messages, draft, model, summary and Build repository. Edit the draft, restart, and confirm no stale messages/metadata return. Repeat while A has a just-typed draft waiting for its save debounce: verify the phone edit survives in an unsynced copy. Repeat an unchanged sync and verify no extra copies appear. Confirm sync pauses during PR creation, and cold-start restores the selected Build repository before the first draft save.
13. With automatic sync settings reset and a cached chat sharing a server ID, enable sync over a slow connection and type a different draft while the account/cloud check is pending. Confirm the server version stays at its original ID and the typed draft, messages and metadata survive in one `unsynced-*` copy. Repeat sync and restart; verify no additional copy appears. Repeat without typing: the unchanged cached version yields to the server. Check server memory remains authoritative. With 100 server chats and a live edit requiring a 101st recovery copy, confirm sync pauses for review with the typed cache unchanged and no upload.
13. Export a disposable chat with extracted attachment text, a stored upload/audio transcript, code results, Build metadata/proposal/PR link, summary and Imagine state. Import it as a separate copy and verify those fields after restart, including the summary's copied-message reference. Confirm legacy text-only exports still import, and malformed metadata or an oversized combined import leaves existing chats unchanged. Confirm an unimportable export is rejected before opening/truncating the destination. File references still need owner sign-in and existing Storage objects; no raw file/audio bytes or memory notes are backed up in chat JSON.

Record the Android version, app version, backend deployment, snapshot revision, and pass/fail result. Do not record prompts, tokens, file contents, or account credentials.

- [ ] Create chats, drafts and local memory; force-stop/restart the app and verify expected persistence.
- [ ] Verify Android backup/device-transfer behavior with a test profile, including what data is restored.
- [ ] Test sign-in, token refresh, expiry, logout, and that a non-owner account is denied. Confirm the phone cache is clearly separate from the authoritative server snapshot after logout; signed-out chats must be described as temporary cache-only data.
- [ ] Test microphone permission allow/deny/revoke, speech-service unavailable, transcript review, hands-free Stop/background behavior, and text-to-speech. Confirm the device speech service may process audio remotely.
- [ ] Test camera capture and photo/document pickers, cancellation, rotation/background interruption, oversized image resizing, and cleanup.
- [ ] Test JSON export/import using Hindi/emoji, malformed/oversized input, cancel, duplicate import, and storage failure. Confirm import does not silently replace existing chats.
- [ ] Test clear-local-data, clear-cloud, and account deletion (disposable account) independently; verify which data remains after each operation.
- [ ] Check small-screen layouts, TalkBack/keyboard navigation, notification permission and notification tap behavior.

These checks require a device/emulator and are not covered by the current JVM-only CI job.

## Personal install

The app is installed directly on the owner's device(s); there is no store upload.

**Debug APK (development).** Gradle signs with the build machine's Android debug key. Crashlytics collection is disabled in debug builds. Use the permanent personal release signer for the owner's installed app; a different debug certificate will not match the registered Android OAuth client or an installed release APK.

- [ ] Build with `bash ./gradlew :app:assembleDebug --no-daemon` (output: `app/build/outputs/apk/debug/app-debug.apk`), or use Android Studio's Run.
- [ ] Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the phone and allow installing from that source.
- [ ] Confirm the app reaches the intended backend (default `https://dua-api-228305912525.us-central1.run.app` (dua-api on Cloud Run); chat uses `https://dua-ai-engine-228305912525.us-central1.run.app`; override with `duaApiBaseUrl` / `DUA_API_BASE_URL` at build time) and that signing in with the owner account works.

**Release APK (personal install).** [SIGNING.md](../SIGNING.md) describes permanent key storage and GitHub builds. `bash ./gradlew :app:assembleRelease --no-daemon` builds a shrunk APK (R8 code shrinking and optimization plus resource shrinking; class names are kept with `-dontobfuscate`, so Crashlytics traces stay readable and no mapping file is uploaded). Output: `app/build/outputs/apk/release/app-release.apk`. Crashlytics collection is enabled in release builds.

- Every release requires the permanent personal keystore. Missing credentials, incorrect alias/password, and a SHA-256 certificate different from `app/signing-certificate.properties` fail the build before packaging. There is no debug-key fallback.
- Set all four values below; a partial set fails the build.

- `DUA_RELEASE_STORE_FILE` — path to the keystore file.
- `DUA_RELEASE_STORE_PASSWORD`
- `DUA_RELEASE_KEY_ALIAS`
- `DUA_RELEASE_KEY_PASSWORD`

The matching Gradle properties are `duaReleaseStoreFile`, `duaReleaseStorePassword`, `duaReleaseKeyAlias`, and `duaReleaseKeyPassword`. Prefer environment variables. Do not commit the keystore or place passwords in tracked `gradle.properties`.

- [ ] Save the original personal keystore and its passwords in a password manager and a separate encrypted backup. Test restoration; use that same key for every update.
- [ ] Run `bash ./gradlew :app:assembleRelease --no-daemon`, then install the APK as above (`apksigner verify --print-certs` shows the signing SHA-1).
- [ ] Check the version code/name, permissions, backend URL and Crashlytics behavior.

**Updates.** Android only installs an update signed with the same key as the installed app. Switching to a different key (a personal release key, or another machine's debug key) requires uninstalling first, which deletes the phone cache. Confirm the authoritative server snapshot is synced before uninstalling.

No private signing keys are supplied by this repository. The manual `Build personal APK` workflow requires configured Actions Secrets and publishes an APK, checksum, and public certificate fingerprints in repository Releases. It does not upload signing secrets.

## Phase 6 repository validation record (2026-10-10)

- [x] `:app:assembleDebug` completed successfully with the documented Android/Gradle cache environment. APK: `app/build/outputs/apk/debug/app-debug.apk` (25 MB).
- [x] Core Android JVM tests for `history`, `network`, `account`, `setup`, and `model` completed successfully (164 tests across 25 test classes, zero failures/errors in the saved JUnit XML). The previously reported 31 was the Gradle task count, not the test count.
- [ ] The full Android JVM suite is not green in this environment: 40 Roborazzi/Compose interaction tests fail before assertions in `MavenDependencyResolver` with `FileNotFoundException`. Re-run with a writable, fully populated Maven/Gradle cache before treating this as an app regression.
- [ ] Backend tests could not be completed because `npm ci` was blocked fetching the locked SheetJS tarball from `cdn.sheetjs.com` (HTTP 403). The prior import error for `xlsx` is therefore an environment/dependency-install issue, not a verified production failure.
- [ ] Physical-device, staging-provider, Supabase, owner-lock, and live Cloud Run checks remain required; this validation was repository/build-only.

## Later validation with permanent signing (2026-10-10)

- **Automated:** [Project checks on main](https://github.com/KRFbrothers/Dua-ai/actions/runs/38072957524) passed on commit `4e7140c`. This later CI result resolves the local Android suite blocker above for that commit; the earlier environment failure remains recorded for reference.
- **Automated:** [Build personal APK](https://github.com/KRFbrothers/Dua-ai/actions/runs/38076223122) passed on the same commit, including release signing and APK certificate verification. The [personal release](https://github.com/KRFbrothers/Dua-ai/releases/tag/personal-apk-38076223122-1) contains the APK, checksum and public certificate fingerprints.
- **Owner-reported device check:** Google sign-in and chat replies worked after installing the permanently signed APK. This was reported by the owner, not observed through an attached device by the audit agent.
- **Still pending:** The remaining staging, owner-lock, quota, backup/restore and provider checks above. Successful login and a chat reply do not verify every production control.

## Backend CI verification (2026-10-11, Asia/Calcutta)

- [x] The Backend tests job in [Project checks for PR #117](https://github.com/KRFbrothers/Dua-ai/actions/runs/38078632200) installed the locked dependencies on Node 24 and passed all 148 tests, with zero failures or skipped tests, on commit `91628f5`.
- [x] The `backend-unit-test-report` JUnit artifact was uploaded successfully. This resolves the earlier full-backend-suite verification gap using GitHub's runner; the local SheetJS network restriction remains an environment limitation.
- This is automated regression coverage using fake upstream responses, not live verification of deployed providers, Supabase policies or the owner lock.

## Projects update

- Deploy the backend and confirm `/api/config.projects.enabled` is true for the owner configuration; build/install the updated APK.
- Confirm old chats open in Personal. Create/edit a Coding project, sync it, and verify the reply follows its instructions.
- Switch Coding/Business with unsent drafts, restart, and check each project’s history and draft. Personal memory must not appear in other project replies.
- Review project settings in Cloud Sync, round-trip a JSON export/import, archive/restore a project, and confirm archived chats cannot send replies.
- Verify divergent instruction edits pause automatic sync for manual review. Keep live voice in Personal without custom instructions; other projects use Voice mode.


## Project tasks update

- Deploy backend and verify `/api/config.projects.enabled` and `projects.tasks` before installing the updated APK. No SQL migration is needed.
- Add different Coding/Business tasks, sync, restart, and verify titles/completion stay in the correct project.
- Edit a completed task, reopen it, cancel/confirm deletion; check archived tasks are readable and cannot be edited.
- Review full task lists in cloud download/upload. Export a task-only JSON backup and import differing tasks; existing tasks must remain in their original project.
- Rename/archive/restore/reset settings and confirm tasks are kept. Simulate divergent same-project edits and confirm automatic sync pauses for review.
- Due dates/device reminders and the daily planner are covered by the following updates. Physical-device and production verification are pending.


## Task reminders update

- Deploy the backend with `projects.dueDates`, install the new APK, and sync dates before checking another device.
- Save a valid due date/time; reject invalid/partial input. Edit only the title and confirm the original exact deadline is retained; remove the date and verify sync/export restores its absence.
- Enable task reminders in Settings → Notifications. Verify generic/private notification delivery after backgrounding the app, without a network connection.
- Complete/delete/archive a task before its due time and confirm its stale alarm cannot notify. Edit the due date and ensure only the new deadline is scheduled.
- Check reboot/package-update/clock-change scheduling, denied notifications, permission revocation and reminder reset. Confirm task dates survive while consent/history remain device-local and excluded from backup/transfer.
- Android battery saving may delay delivery; exact alarms are not implemented. These device checks are pending.

## Full-message history search update

- In Coding, search a literal phrase present only in an old middle reply, a first message beyond its 60-character title, a draft and saved Run code output. Check a matching snippet, highlight and the correct conversation opening.
- Clear the query and confirm all current-project chats return. Try mixed case, code symbols and an unmatched phrase; no-results should suggest refreshing history by sync.
- Type quickly and edit/restore the snapshot while a search is pending. Old query/snapshot results must disappear, and the final query must show current matches.
- Switch projects with an active query: it must clear, and another project's matches must stay hidden. Verify no extra network/provider request or persistent search index.
- On the phone, check typing responsiveness, emoji/long-line snippets, restart/rotation and sync-refresh results. These device/live checks remain pending; see [HISTORY_SEARCH.md](features/HISTORY_SEARCH.md).

## Daily planner update

- Open Projects → Daily planner. Check Today, Previous/Next and project filters using Personal/Coding/Business tasks: previous-day overdue, today's elapsed/future deadlines, tomorrow, completed and undated tasks.
- Verify every task shows its project; archived projects are hidden, and a removed/archived selected filter returns to all active projects after sync.
- Complete/reopen matching task IDs from different projects and verify only the intended task changes. A failed save must keep the task state and show an error.
- Open a project task list from the planner, edit a deadline, and return with Back to planner. The selected day/filter must remain and the new deadline group must update. An empty selected project must offer Manage tasks.
- Verify resume/midnight/phone-zone changes refresh Today and overdue labels within one minute; a browsed date stays fixed. Confirm the same absolute due timestamp remains after a time-zone change, including clock-change days.
- Sync completion/date edits, restart and review/download on another device. Verify the planner reflects the current server snapshot and creates no separate task store or provider request. Completed sections describe due dates, not completion history.
- See [PLANNER.md](features/PLANNER.md). Physical-device and authenticated production checks remain pending; recurrence and separate planned dates are not implemented.
