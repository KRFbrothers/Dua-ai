# Storage and password-protected Drive backups

Open **Settings → Storage** to see real cached chat/project counts, private app-cache size, downloaded-media cache size (a subset of app cache), and authenticated private-cloud file usage. Unknown/loading/error usage is not displayed as zero. Refresh retries the measurements. **Manage chats and files** opens the existing Data controls; this page does not delete data itself.

## Save a complete data snapshot

1. Sign in to the account that owns your files. Use **Account cloud sync** first if chats, projects or Personal notes exist only on the server.
2. Finish any reply/upload/voice session. Send or remove pending attachments.
3. Tap **Save full backup**. Choose and confirm a password of 12–256 characters. Save it separately in your password manager: Dua AI cannot recover it.
4. In Android's file picker, select **Google Drive**, choose your folder and save the `.duabackup` file. Drive must be installed/available as a document provider; another document provider also works.
5. Wait for the app's completion message, then check Drive's own upload status. The picker accepting a file does not prove its remote upload has finished.

This is manual document-provider backup/restore and needs no Drive API setup. Google sign-in alone does not connect automatic Drive backups; the separate opt-in automatic mode below requests its own limited Drive permission.

One encrypted archive contains:

- Cached chats and drafts, project definitions/instructions/memory/tasks/planner settings, and Personal memory notes.
- Every listed private-cloud file for the current account, including raw photos, documents, audio and stored generated media, even when a file is not currently referenced by a chat.
- Raw inline attachments still available in the current process. Restoring these creates persistent private-cloud files and chat references.

The snapshot excludes account sessions, login/provider tokens, API keys, device permissions, other device settings, and unrelated phone-gallery files. Raw inline media already lost on a previous process restart cannot be reconstructed. A referenced missing cloud file, truncated inventory, unsupported file, incomplete download or temporary generated-media URL stops the backup with an error instead of claiming completeness.

## Automatic daily Drive backups

Automatic mode uses Google Drive's **hidden app data folder**, accessible only to this app. It requests only `https://www.googleapis.com/auth/drive.appdata`, not access to your entire Drive. These backups are browsed/restored inside **Storage → Browse Drive backups**; they are separate from visible files created with the manual picker above.

### One-time Google Cloud setup

1. Open the Google Cloud project containing your existing Dua AI **Android Google sign-in OAuth client**. This may differ from your Cloud Run engine project.
2. Open **APIs & Services → Library**, search **Google Drive API**, and click **Enable**.
3. In **Google Auth Platform → Data Access**, add `https://www.googleapis.com/auth/drive.appdata`. If the consent app is in Testing, ensure the owner Google account is an allowed test user under **Audience**.
4. Under **Clients**, keep the existing Android client's package `com.dua.ai` and the SHA-1 of the APK's existing signing certificate. Use the same Google Cloud/OAuth project across updates so the private app folder remains accessible. Do not change the signing key. No client secret, service account or Drive refresh token belongs in the APK/backend.

### Enable and operate

1. Install the updated APK and sign in to your owner account. Open **Settings → Storage → Connect Google Drive**. Grant the separate Drive permission using the same Google account; the app checks the Drive account email before saving the connection.
2. Enable **Daily automatic backup**, choose/confirm the archive password, read the three-version retention explanation and explicitly confirm. The password is stored encrypted with an installation-bound Android Keystore AES-GCM key, bound to the owner, backend and consent generation. Keep a separate copy in your password manager for reinstall/new-device restores.
3. A first backup is queued immediately; future work repeats approximately every 24 hours. **Back up now/Retry backup** queues another attempt using the same constraints. WorkManager requires Wi-Fi **or another unmetered network**, battery-not-low and storage-not-low. Android chooses the actual run time and can delay work in Doze/battery restrictions. Force-stopping the app prevents background work until you reopen it.
4. Large backups use a `dataSync` foreground worker with a low-priority backup notification. The page shows queued/running/error/setup status, upload progress and this installation's last successful upload. No Google consent UI is launched from the background: revoked/missing authorization asks you to reconnect in Storage.
5. Each run reads the fresh signed-in server snapshot and combines it with offline phone drafts, Personal notes, differing project context and still-available inline attachments **only inside the archive**. Existing deletion tombstones are respected. It does not apply server data to the live phone cache or upload a cloud-sync snapshot. If merging would exceed existing chat/project/10-note limits, it fails clearly and keeps older backups.
6. Archives reuse the manual full-data format and bounded streaming encryption. Drive uploads are resumable in 8 MiB chunks. The completed encrypted file's size/Drive MD5 are checked before marking it complete; cryptographic integrity is still enforced by the archive's authenticated encryption on restore. Only then can retention remove completed app-owned backups older than the newest three for this owner. Manual picker backups, other files and unfinished uploads are never retention-deleted. Failed upload/checksum/inventory/ownership checks do not authorize pruning.
7. Use **Browse Drive backups**, select a completed backup and enter the password used to create that archive. The encrypted file is downloaded and checked before the existing authenticated archive review/explicit restore confirmation. It never silently restores using the saved automatic-backup password. You can cancel the download or verification.

Turning automatic backup off removes its saved password and cancels scheduled work while keeping Drive backups and the limited Google grant. Signing out/changing the Dua account clears the local Drive connection and password too. Jobs also re-check owner/consent generation around network operations to reject stale completions/deletions. Google Play services manages and refreshes Google access tokens; this app never stores those tokens, refresh tokens or upload session URLs. A rejected token is invalidated in its SDK cache and requires reconnecting.

Changing the chosen password affects future archives; older versions still require their original password. Cleanup failure after a verified upload preserves the successful-upload timestamp and reports a warning; cleanup can run after the next successful backup. Network failures receive bounded WorkManager retries and can be retried manually. If Drive received the final bytes but the process lost its acknowledgement, the next automatic run can verify their final size/checksum against the pre-upload metadata and mark that archive complete before preparing a fresh backup. Incomplete bytes are not promoted or pruned. Interrupted private plaintext staging is cleaned on the next automatic attempt. The app does not claim a live Drive connection until authorization and the account check succeed.

Archives are limited to 1 GiB, raw assets to 256 MiB each, and 10,000 assets total; existing chat JSON and project limits still apply. Private temporary phone space (roughly three times the raw cloud bytes plus metadata) is needed. Network access is required to collect cloud files. Downloads and encryption finish before the chosen destination is opened. Cancelling during the final write can leave an incomplete destination file: delete that copy and retry.

## Restore safely

1. Install the APK and sign in to the original account for cloud-file recovery. Refresh **Feature setup** after deploying the backend described below.
2. In **Storage → Restore full backup**, select the archive from Drive, then enter its password.
3. The app authenticates the entire encrypted stream, validates the ZIP inventory and verifies every raw file's length/SHA-256 **before** offering a preview. Wrong passwords, tampering and truncated files fail without importing phone data.
4. Review chat, project, Personal-note and file counts, then explicitly tap **Restore backup**.
5. Missing raw cloud files are uploaded with insert-only signed URLs. Existing files must match the backed-up length and SHA-256 and are kept; conflicts stop the import. No existing cloud file is overwritten/deleted.
6. Chats and differing project settings are imported as copies; existing active chat/draft remain selected. Personal notes merge with existing notes, subject to the current 10-note limit. Memory's On/Off setting stays unchanged. Sync afterwards to save restored chats/projects/notes to the account server.

File recovery precedes the local metadata transaction. If a restore fails/cancels midway, recovered cloud files remain and phone metadata is not imported; retry verifies/skips matching recovered files. Repeating a **successful** restore creates additional chat copies. Restore supports only Dua AI `.duabackup` files. Existing plain JSON exports/imports remain unchanged under Data controls and do not include raw bytes or global Personal notes.

## Backend rollout

The full-backup restore feature needs the existing **Node `web/` service** with `/api/files/restore-sign` deployed (for Cloud Run, use the existing `web/deploy-cloudrun.sh` with your configured project). Install the new APK too. Automatic scheduling adds no backend changes, new secrets, tables or RLS policy changes; it does require the Google Drive setup above. Keep the existing private file bucket, `DUA_FILES=1`, owner authentication and Supabase Storage migration configured. Stored video recovery also requires the existing Imagine migration's `video/mp4` bucket allowance.

The additive owner-only `POST /api/files/restore-sign` accepts exactly `{path, mimeType, size}`. It signs an insert into the verified user's UUID folder using that user's Supabase JWT, never the service-role key, and never enables upsert. `/api/config` advertises `files.restore: true`. Older servers can still provide backup file downloads, but raw-file restore stays unavailable until this capability is present. The Python chat engine is unchanged.

## Archive implementation and verification

`BackupCipher` uses random Tink AES-256-GCM-HKDF streaming keys. A random salt and PBKDF2-HMAC-SHA256 (600,000 iterations) derive the password wrapping key; AES-GCM wraps the keyset. The complete versioned header is authenticated as associated data. Manual passwords are not saved in preferences/state bundles; automatic mode saves only the Keystore-encrypted password after explicit opt-in. Supplied/decrypted character arrays are cleared after operations. `dua_drive_backup.xml` is excluded from Android cloud backup **and** device transfer; cache staging is also excluded. Plaintext staging stays in app-private cache, is removed on completion/cancellation and abandoned workspaces are cleaned on the next backup operation.

`FullBackupArchive` reads a strict sequential ZIP format (`manifest.json`, `chats.json`, then generated `files/NNNNN` entries). It bounds decompression, file counts and metadata, rejects extra/missing entries and does not extract archive-provided filesystem paths. `FullBackupController` guards concurrent app operations and reuses the existing reviewed-import transaction for chat/project/memory commit and rollback.

Automated checks cover multi-segment encryption, wrong password/header/ciphertext/truncation/trailing-data rejection, raw-byte fidelity, ZIP traversal, ownership, failed/retried recovery, conflict rejection, inline attachment recovery, chat/draft/note persistence and Storage UI in light/dark at 320dp with 1×/1.8× text. Backend tests mock authenticated insert-only signing and rejected foreign/invalid requests. No tests contact production Supabase or Drive.

Automatic-backup tests additionally cover the owner-bound app-folder authorization request, encrypted password/AAD persistence, sign-out/disable invalidation, unique unmetered WorkManager scheduling/cancellation, server/offline snapshot preservation, chunked upload/checksum verification, paginated retention, redirects/foreign IDs, interrupted work/lock release and accessible enable/list/status controls. Google Identity and Android Keystore integration still require the physical-device checks below; JVM tests use SDK result fixtures and a standard-JCA protector.

On a physical phone, verify manual Drive save/upload completion, restore after reinstall, picker/password cancellation, rotation while the picker is open, network failure/retry, and offline availability/error copy. For automatic mode, verify the separate Google consent, first queued upload on Wi-Fi, daily/Doze behavior, notification, three-version retention, Drive permission revocation/reconnect, turning off/signing out mid-upload, and restore after reinstall with the separately saved password. Automated tests cannot establish live OAuth setup, device Keystore or a particular Drive provider's behavior.
