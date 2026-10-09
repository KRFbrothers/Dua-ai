# Google Drive backup

Local implementation; no backend change or GitHub push is required.

In Settings → Cloud sync, sign in, then choose **Enable automatic Drive backup**. Google requests `https://www.googleapis.com/auth/drive.appdata` separately from Dua sign-in. The request targets the Google account matching the signed-in Dua email. Tokens stay in memory; Google Play services manages authorization and refresh. No broad Drive permission, client secret or extra Android manifest permission is needed.

Dua creates immutable JSON snapshots in Google's hidden `appDataFolder`. The first backup is scheduled immediately and subsequent backups run about every 24 hours with network connectivity. WorkManager survives app restarts and reboots; Android battery restrictions can delay execution. Identical snapshots are skipped. Older snapshots are retained and use Drive storage; this version has no automatic retention cleanup. Turning backup off or logging out cancels scheduled work. A request already sent to Google may finish. Drive permission can be revoked in Google account settings; reconnect in Cloud sync if access expires.

The snapshot contains saved chats, drafts, reasoning summaries and memory notes using the existing validated cloud-snapshot format (up to 4 MiB, 100 chats, 10 memory notes). It does not contain settings, sign-in tokens, file/audio bytes, local conversation summaries or Build repository state. References to stored media may remain in messages, but their bytes are not backed up. The backup is JSON protected by Google account access and HTTPS; Dua does not add a separate password encryption layer.

**Review latest Drive backup** loads the newest tagged Dua snapshot in this account across devices. Review the chat list and memories, then confirm. Restore merges into current local data, preserving conflicts as copies and preserving the memory on/off setting. It never replaces the entire history. Local deletion, Supabase deletion or account deletion does not erase older Drive backups. You can delete hidden app data through Google Drive's Manage apps controls.

## Google setup before device verification

1. Enable **Google Drive API** in the Google Cloud project used for Dua's OAuth configuration.
2. Configure the OAuth consent screen and add `drive.appdata`. For an external consent screen in Testing, add the owner's Google account as a test user. Google controls testing grants and their expiry.
3. Register an **Android OAuth client** for package `com.dua.ai` and the SHA-1 of the key signing the installed APK. Release and debug keys may require separate clients. Keep the existing web OAuth client for Supabase login; no client secret belongs in the Android app.
4. Build/install the APK, sign in with the owner account, open Cloud sync, grant Drive permission and confirm a completed backup timestamp.

## Verification

Run `bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2`. `DriveBackupApiTest` covers private-folder uploads, latest-backup selection, missing backups, rejected permissions and payload validation.

Device checks still required: accept/cancel consent; initial and background backups; process restart/reboot; revoked permission/reconnect; logout and account switching; offline retry; unchanged-data deduplication; conflicting-chat restore; invalid/oversized backup rejection; memory-limit failure preserving local data. Verify permissions and Drive API configuration with the actual signing key. Unit tests cannot verify Google's consent UI or Android's scheduling behavior.
