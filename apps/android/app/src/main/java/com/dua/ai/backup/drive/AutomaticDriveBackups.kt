package com.dua.ai.backup.drive

import android.content.Context
import com.dua.ai.account.AccountRepository
import com.dua.ai.backup.*
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class DriveBackupListState(val busy: Boolean = false, val files: List<DriveBackupFile> = emptyList(), val error: String = "")

/** One account repository per application process: background refreshes cannot race a second copy of the Dua session. */
class AutomaticDriveBackups private constructor(private val context: Context) {
    val account = AccountRepository(context)
    val store = DriveBackupStore(context)
    val authorization = DriveAuthorization(context)
    private val listing = MutableStateFlow(DriveBackupListState())
    private var listingGeneration = 0L
    val listState = listing.asStateFlow()
    private var host: Pair<Any, () -> FullBackupSnapshot>? = null
    private val scheduler by lazy { DriveBackupScheduler(context) }
    val engine = DriveBackupEngine(store, File(context.cacheDir, "automatic-drive-stage"),
        currentAccount = { account.state.value.user.let { it?.id to it?.email } },
        capture = { record ->
            val snapshot = captureSnapshot()
            val config = account.request("/api/config")
            val cloud = account.cloud()
            checkOwner(record.owner, record.email)
            check(cloud.ownerId == record.owner) { "Cloud backup account changed." }
            mergeDriveSnapshot(snapshot, cloud.snapshot).copy(cloudFilesEnabled = config.optJSONObject("files")?.opt("enabled") == true)
        }, remote = { record, guard -> remote(record, guard) },
        write = { snapshot, workspace, target, password, progress ->
            FullBackupBuilder(BackupFileTransfer(account)).write(snapshot, workspace, target, password, progress)
        })

    /** Main-thread registration captures live drafts/inline files when a ViewModel exists. */
    fun bind(owner: Any, capture: () -> FullBackupSnapshot) { host = owner to capture }
    fun unbind(owner: Any) { if (host?.first === owner) host = null }

    private suspend fun captureSnapshot(): FullBackupSnapshot {
        val live = withContext(Dispatchers.Main.immediate) { host?.second?.invoke() }
        if (live != null) return live
        // Read both raw values on Main as a single short capture. Decode off Main; never instantiate
        // another ChatViewModel or mutate chat/project/automatic-sync state from the worker.
        val raw = withContext(Dispatchers.Main.immediate) {
            val history = context.getSharedPreferences("dua_chat_history", 0)
            listOf(history.getString("history", null), context.getSharedPreferences("dua_preferences", 0).getString("preferences", null),
                history.getString("deleted_chat_ids", null), history.getString("deleted_chat_owner", null))
        }
        return withContext(Dispatchers.Default) {
            val settings = raw[1]?.let(PreferencesCodec::decode) ?: LocalPreferences()
            val records = raw[0]?.let { ChatHistoryCodec.decode(it).second }.orEmpty()
            val owner = account.state.value.user?.id
            val deleted = if (raw[3] == owner) com.dua.ai.account.CloudDeletionIds.decode(raw[2]?.let(::orgJsonArray)) else emptyList()
            FullBackupSnapshot(ChatBackup(records.filterNot { it.id in deleted }, settings.projects), settings.memory, owner, false, deleted)
        }
    }
    private fun orgJsonArray(value: String) = org.json.JSONArray(value)

    suspend fun connect(token: String, expectedOwner: String, expectedEmail: String) {
        checkOwner(expectedOwner, expectedEmail)
        val api = DriveBackupApi(token, expectedOwner, { checkOwner(expectedOwner, expectedEmail) }, onUnauthorized = { authorization.invalidate(token) })
        if (!api.email().equals(expectedEmail, true)) throw DriveSetupRequired("Select the same Google account as your signed-in Dua account. No backup permission was saved.")
        checkOwner(expectedOwner, expectedEmail)
        withContext(Dispatchers.IO) {
            checkOwner(expectedOwner, expectedEmail)
            store.connect(expectedOwner, expectedEmail)
            if (store.state.value.enabled) { scheduler.ensureDaily(); scheduler.backupNow() }
        }
    }
    suspend fun enable(password: CharArray) {
        try {
            val record = store.state.value
            checkOwner(record.owner, record.email)
            withContext(Dispatchers.IO) {
                checkOwner(record.owner, record.email)
                store.enable(record.owner, password)
                try { scheduler.ensureDaily(); scheduler.backupNow() }
                catch (failure: Exception) { store.disable("Android could not schedule backups. Enable automatic backup again."); throw failure }
            }
        } finally { password.fill('\u0000') }
    }
    fun disable() { try { store.disable() } finally { runCatching { scheduler.cancel() } } }
    fun backupNow() {
        val record = store.state.value
        checkOwner(record.owner, record.email); store.requireCurrent(record)
        store.report(record, "queued", "Waiting for Wi-Fi or another unmetered network. Android will start the backup when constraints allow.")
        scheduler.backupNow()
    }
    fun accountChanged(owner: String?) {
        val before = store.state.value
        store.accountChanged(owner)
        if (before.owner.isNotEmpty() && before.owner != owner) {
            listingGeneration++; listing.value = DriveBackupListState()
            runCatching { scheduler.cancel() }
        }
    }
    private fun checkOwner(owner: String, email: String) {
        val user = account.state.value.user
        check(owner.isNotBlank() && user?.id == owner && user.email.equals(email, true)) { "Sign in to the account shown before accessing Drive backups." }
    }
    private suspend fun remote(record: DriveBackupRecord, guard: () -> Unit): DriveBackupRemote {
        guard(); checkOwner(record.owner, record.email)
        val access = authorization.request(record.email)
        if (access !is DriveAccess.Token) throw DriveSetupRequired()
        guard()
        return DriveBackupApi(access.value, record.owner, guard, onUnauthorized = { authorization.invalidate(access.value) })
    }
    private suspend fun browsingRemote(record: DriveBackupRecord): DriveBackupRemote {
        val guard = { store.requireCurrent(record, enabled = false); checkOwner(record.owner, record.email) }
        return remote(record, guard).also { if (!it.email().equals(record.email, true)) throw DriveSetupRequired("Connect the Google account shown in Storage before browsing backups.") }
    }
    suspend fun loadBackups() {
        if (listing.value.busy) return
        val record = store.state.value
        val ticket = ++listingGeneration
        fun current() = ticket == listingGeneration && store.state.value.generation == record.generation && account.state.value.user?.id == record.owner
        listing.value = DriveBackupListState(busy = true)
        try {
            val files = browsingRemote(record).backups().take(3)
            checkOwner(record.owner, record.email); store.requireCurrent(record, enabled = false)
            if (current()) listing.value = DriveBackupListState(files = files)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (current()) listing.value = DriveBackupListState(error = if (failure is DriveFailure) failure.message.orEmpty() else "Drive backups could not be loaded. Connect Drive and retry.") }
        finally { if (ticket == listingGeneration) listing.value = listing.value.copy(busy = false) }
    }
    suspend fun downloadBackup(id: String): File {
        val record = store.state.value
        val api = browsingRemote(record)
        val selected = api.backups().firstOrNull { it.id == id } ?: throw DriveFailure("Drive backup not found. Refresh the list.")
        val directory = File(context.cacheDir, "drive-restore").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.duabackup")
        try { api.download(selected, target); return target }
        catch (failure: Exception) { target.delete(); throw failure }
    }
    companion object {
        @Volatile private var current: Pair<Context, AutomaticDriveBackups>? = null
        @Synchronized fun get(context: Context): AutomaticDriveBackups {
            val app = context.applicationContext
            return current?.takeIf { it.first === app }?.second ?: AutomaticDriveBackups(app).also { current = app to it }
        }
    }
}
