package com.dua.ai.backup.drive

import com.dua.ai.backup.FullBackupSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.IOException
import java.util.UUID

enum class DriveRunResult { Success, Retry, NeedsSetup }

/** A point-in-time data snapshot; only verified completed uploads authorize three-version retention. */
class DriveBackupEngine(private val store: DriveBackupStore, private val root: File,
    private val currentAccount: () -> Pair<String?, String?>,
    private val capture: suspend (DriveBackupRecord) -> FullBackupSnapshot,
    private val remote: suspend (DriveBackupRecord, () -> Unit) -> DriveBackupRemote,
    private val write: suspend (FullBackupSnapshot, File, File, CharArray, (String) -> Unit) -> Unit,
    private val mutex: Mutex = Mutex(), private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun execute(onProgress: (String, Int?) -> Unit = { _, _ -> }): DriveRunResult {
        if (!mutex.tryLock()) return DriveRunResult.Retry
        val expected = store.state.value
        val workspace = File(root, UUID.randomUUID().toString())
        var password: CharArray? = null
        var uploaded = false
        fun guard() {
            store.requireCurrent(expected)
            val (owner, email) = currentAccount()
            check(owner == expected.owner && email.equals(expected.email, true)) { "Account changed. Connect Drive for the current account." }
        }
        fun progress(text: String, percent: Int? = null) {
            guard(); store.report(expected, "running", text, percent); onProgress(text, percent)
        }
        try {
            withContext(Dispatchers.IO) { root.listFiles()?.forEach { it.deleteRecursively() } }
            if (!expected.enabled) return DriveRunResult.Success
            guard(); progress("Checking Drive backup permission…")
            val api = remote(expected, ::guard)
            if (!api.email().equals(expected.email, true)) throw DriveSetupRequired("Choose the same Google account as the signed-in Dua account before enabling backup.")
            api.recoverInterruptedUploads().maxByOrNull { it.createdAt }?.let { recovered ->
                guard()
                if (recovered.createdAt > store.state.value.lastSuccess) store.success(expected, recovered.id, recovered.createdAt, recovered.size)
            }
            guard()
            val snapshot = capture(expected)
            check(snapshot.ownerId == expected.owner) { "The backup snapshot belongs to a different account." }
            if (!snapshot.cloudFilesEnabled) throw DriveSetupRequired("Enable private cloud files in Feature setup before automatic full backups.")
            password = try { store.password(expected) } catch (_: Exception) { throw DriveSetupRequired("The saved backup password is unavailable. Disable automatic backup, then enable it with your password again.") }
            withContext(Dispatchers.IO) {
                // No other automatic archive can be active while this process-wide mutex is held.
                check(workspace.mkdirs()) { "Backup workspace is unavailable." }
            }
            val encrypted = File(workspace, "backup.duabackup")
            progress("Preparing encrypted full backup…")
            write(snapshot, workspace, encrypted, password) { progress(it) }
            password.fill('\u0000'); password = null
            guard(); progress("Uploading encrypted backup to Drive…", 0)
            val file = api.upload(encrypted) { progress("Uploading encrypted backup to Drive…", it) }
            guard(); store.success(expected, file.id, clock(), file.size); uploaded = true
            try { api.retainThree(file) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { store.report(expected, "warning", "Backup saved and verified. Older-version cleanup could not finish; it will retry after the next successful backup.") }
            return DriveRunResult.Success
        } catch (cancelled: CancellationException) {
            if (!uploaded) store.report(expected, "queued", "Backup interrupted. Android can retry on an unmetered network; existing backups were kept.")
            throw cancelled
        } catch (failure: DriveSetupRequired) {
            store.report(expected, "setup", failure.message.orEmpty())
            return DriveRunResult.NeedsSetup
        } catch (failure: Exception) {
            val message = when (failure) {
                is DriveFailure -> failure.message.orEmpty()
                is IOException -> "Backup could not connect. Retry on an unmetered network. Existing backups were kept."
                else -> failure.message?.takeIf { it.length <= 500 } ?: "Full backup could not finish. Existing backups were kept; retry."
            }
            store.report(expected, "error", message)
            return if (failure is IOException || failure is DriveFailure && failure.retryable) DriveRunResult.Retry else DriveRunResult.NeedsSetup
        } finally {
            withContext(NonCancellable) {
                password?.fill('\u0000')
                try { withContext(Dispatchers.IO) { workspace.deleteRecursively() } } finally { mutex.unlock() }
            }
        }
    }
}
