# Storage and password-protected Drive backups

Open **Settings → Storage** to see real cached chat/project counts, private app-cache size, downloaded-media cache size (a subset of app cache), and authenticated private-cloud file usage. Unknown/loading/error usage is not displayed as zero. Refresh retries the measurements. **Manage chats and files** opens the existing Data controls; this page does not delete data itself.

## Save a complete data snapshot

1. Sign in to the account that owns your files. Use **Account cloud sync** first if chats, projects or Personal notes exist only on the server.
2. Finish any reply/upload/voice session. Send or remove pending attachments.
3. Tap **Save full backup**. Choose and confirm a password of 12–256 characters. Save it separately in your password manager: Dua AI cannot recover it.
4. In Android's file picker, select **Google Drive**, choose your folder and save the `.duabackup` file. Drive must be installed/available as a document provider; another document provider also works.
5. Wait for the app's completion message, then check Drive's own upload status. The picker accepting a file does not prove its remote upload has finished.

This is manual document-provider backup/restore. Google sign-in alone does not connect automatic Drive backups; there is no Drive OAuth integration or scheduled upload in this change.

One encrypted archive contains:

- Cached chats and drafts, project definitions/instructions/memory/tasks/planner settings, and Personal memory notes.
- Every listed private-cloud file for the current account, including raw photos, documents, audio and stored generated media, even when a file is not currently referenced by a chat.
- Raw inline attachments still available in the current process. Restoring these creates persistent private-cloud files and chat references.

The snapshot excludes account sessions, login/provider tokens, API keys, device permissions, other device settings, and unrelated phone-gallery files. Raw inline media already lost on a previous process restart cannot be reconstructed. A referenced missing cloud file, truncated inventory, unsupported file, incomplete download or temporary generated-media URL stops the backup with an error instead of claiming completeness.

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

Redeploy the existing **Node `web/` service** after merge (for Cloud Run, run the existing `web/deploy-cloudrun.sh` with your configured project). Install the new APK too. No new secrets, Google Drive API setup, tables or RLS policy changes are required. Keep the existing private file bucket, `DUA_FILES=1`, owner authentication and Supabase Storage migration configured. Stored video recovery also requires the existing Imagine migration's `video/mp4` bucket allowance.

The additive owner-only `POST /api/files/restore-sign` accepts exactly `{path, mimeType, size}`. It signs an insert into the verified user's UUID folder using that user's Supabase JWT, never the service-role key, and never enables upsert. `/api/config` advertises `files.restore: true`. Older servers can still provide backup file downloads, but raw-file restore stays unavailable until this capability is present. The Python chat engine is unchanged.

## Archive implementation and verification

`BackupCipher` uses random Tink AES-256-GCM-HKDF streaming keys. A random salt and PBKDF2-HMAC-SHA256 (600,000 iterations) derive the password wrapping key; AES-GCM wraps the keyset. The complete versioned header is authenticated as associated data. Passwords are not saved in preferences/state bundles; supplied character arrays are cleared after operations. Plaintext staging stays in app-private cache, is removed on completion/cancellation and abandoned workspaces are cleaned on the next backup operation. Android excludes cache directories from system backup.

`FullBackupArchive` reads a strict sequential ZIP format (`manifest.json`, `chats.json`, then generated `files/NNNNN` entries). It bounds decompression, file counts and metadata, rejects extra/missing entries and does not extract archive-provided filesystem paths. `FullBackupController` guards concurrent app operations and reuses the existing reviewed-import transaction for chat/project/memory commit and rollback.

Automated checks cover multi-segment encryption, wrong password/header/ciphertext/truncation/trailing-data rejection, raw-byte fidelity, ZIP traversal, ownership, failed/retried recovery, conflict rejection, inline attachment recovery, chat/draft/note persistence and Storage UI in light/dark at 320dp with 1×/1.8× text. Backend tests mock authenticated insert-only signing and rejected foreign/invalid requests. No tests contact production Supabase or Drive.

On a physical phone, verify Drive save/upload completion, restore after reinstall, picker/password cancellation, rotation while the picker is open, network failure/retry, and offline availability/error copy. Automated file-URI tests cannot establish a particular Drive provider's upload behavior.
