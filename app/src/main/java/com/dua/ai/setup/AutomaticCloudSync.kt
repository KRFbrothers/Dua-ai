package com.dua.ai.setup

import com.dua.ai.account.AccountException
import com.dua.ai.account.CloudReview
import com.dua.ai.account.CloudSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutomaticSyncAccount(val configured: Boolean, val accountId: String?)
data class AutomaticSyncState(val enabled: Boolean = false, val phase: String = "disabled", val message: String = "Automatic sync is off.",
    val accountId: String? = null, val reviewRequired: Boolean = false)
class AutomaticSyncLocalException(cause: Exception) : Exception("Local data needs manual review.", cause)

/** Foreground snapshot coordination. Call methods and ports on the scope's serialized UI dispatcher. */
class AutomaticCloudSync(
    private val scope: CoroutineScope,
    private val account: AccountPort,
    private val local: LocalPort,
    private val store: StorePort,
    private val debounceMillis: Long = 1200,
    private val pollMillis: Long = 30_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    interface AccountPort {
        fun currentAccountId(): String?
        suspend fun load(): AutomaticSyncAccount
        suspend fun cloud(expectedAccountId: String): CloudReview
        suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long
    }
    interface LocalPort {
        fun read(): CloudSnapshot
        /** Read includes pending edits; commit persists with rollback and rebinds the active chat's buffers. */
        fun commit(snapshot: CloudSnapshot)
    }
    interface StorePort {
        fun read(): AutomationSyncRecord?
        fun write(value: AutomationSyncRecord)
        fun clear() { write(AutomationSyncRecord()) }
    }

    private val mutable = MutableStateFlow(AutomaticSyncState())
    val state: StateFlow<AutomaticSyncState> = mutable.asStateFlow()
    private var record = AutomationSyncRecord()
    private var storageDamaged = false
    private var foreground = false
    private var online = true
    private var busy = false
    private var closed = false
    private var restored = false
    private var restoreRequested = false
    private var generation = 0L
    private var operation: Job? = null
    private var scheduled: Job? = null
    private var queued = false
    private val networkMutex = Mutex()

    init {
        try {
            record = store.read()?.let(AutomationSyncPolicy::validateRecord) ?: AutomationSyncRecord()
            when {
                record.reviewRequired -> publish("review", "Review cloud data before resuming automatic sync.")
                record.enabled -> publish("paused", "Automatic sync waits for the app to be active.")
            }
        } catch (_: Exception) {
            storageDamaged = true
            publish("storage-error", "Saved automatic sync settings could not be read. Review cloud data, then reset these settings.")
        }
    }

    private fun publish(phase: String, message: String) {
        mutable.value = AutomaticSyncState(record.enabled, phase, message, record.accountId, record.reviewRequired)
    }
    private fun persist(next: AutomationSyncRecord): Boolean {
        try { store.write(AutomationSyncPolicy.validateRecord(next)); record = next; return true }
        catch (_: Exception) {
            storageDamaged = true; record = record.copy(enabled = false); invalidate()
            publish("storage-error", "Automatic sync settings could not be saved. Sync is disabled; local data is kept.")
            return false
        }
    }
    private fun invalidate() {
        generation++; queued = false; scheduled?.cancel(); scheduled = null; operation?.cancel(); operation = null
    }
    private fun review(message: String) {
        queued = false; scheduled?.cancel(); scheduled = null
        if (persist(record.copy(reviewRequired = true))) publish("review", message)
    }
    private fun ready(ticket: Long): Boolean {
        if (ticket != generation || closed || storageDamaged || !record.enabled || record.reviewRequired) return false
        if (account.currentAccountId() != record.accountId) { review("Account changed. Review local data before enabling sync for another account."); return false }
        if (!foreground) { publish("paused", "Automatic sync pauses while the app is in the background."); return false }
        if (!online) { publish("offline", "Offline. Automatic sync waits for connectivity."); return false }
        if (busy) { publish("paused", "Automatic sync pauses while a reply or data action is in progress."); return false }
        if (record.failureCount >= 5) { publish("error", "Sync stopped after five failed attempts. Tap Retry when ready."); return false }
        return true
    }

    fun setForeground(value: Boolean) { if (foreground == value) return; foreground = value; availabilityChanged() }
    fun setOnline(value: Boolean) { if (online == value) return; online = value; availabilityChanged() }
    fun setBusy(value: Boolean) { if (busy == value) return; busy = value; availabilityChanged() }
    private fun availabilityChanged() {
        invalidate()
        if (!record.enabled || storageDamaged) return
        if (!foreground || !online || busy) { ready(generation); return }
        if (restoreRequested && !restored) restoreAttempt() else if (restored) requestSync()
    }
    fun accountChanged() {
        if (record.accountId == null || account.currentAccountId() == record.accountId) return
        invalidate()
        review("Sign-in changed. Automatic sync requires a fresh review before resuming.")
    }
    fun disable() {
        invalidate()
        if (storageDamaged) { record = record.copy(enabled = false); publish("storage-error", "Sync is off. Reset damaged sync settings only after reviewing cloud data."); return }
        if (persist(record.copy(enabled = false))) publish("disabled", "Automatic sync is off.")
    }
    fun reset() {
        invalidate()
        try { store.clear(); record = AutomationSyncRecord(); storageDamaged = false; restored = false; restoreRequested = false; publish("disabled", "Sync settings reset. Review cloud data before enabling automatic sync again.") }
        catch (_: Exception) { storageDamaged = true; record = record.copy(enabled = false); publish("storage-error", "Sync settings could not be reset. Automatic sync remains off.") }
    }
    fun close() { closed = true; invalidate() }

    /** Restore previously granted consent once; a new sign-in never grants consent. */
    fun restore(): Job? {
        if (restoreRequested || closed || storageDamaged) return null
        restoreRequested = true
        if (!record.enabled) { restored = true; return null }
        return restoreAttempt()
    }
    private fun restoreAttempt(): Job? {
        if (!ready(generation)) return null
        return beginConsent(restoring = true, acknowledged = false)
    }
    fun enable(): Job? = beginConsent(restoring = false, acknowledged = false)
    fun resumeAfterReview(): Job? = beginConsent(restoring = false, acknowledged = true)
    private fun beginConsent(restoring: Boolean, acknowledged: Boolean): Job? {
        if (closed || storageDamaged) return null
        if (record.reviewRequired && !acknowledged) { publish("review", "Review cloud data, then choose Resume after review."); return null }
        if (!foreground || !online || busy) { publish(if (!online) "offline" else "paused", "Open the app online and finish the current action before enabling sync."); return null }
        val expectedOwner = account.currentAccountId()
        if (expectedOwner == null) { publish("disabled", "Sign in before enabling automatic sync."); return null }
        invalidate(); val ticket = generation
        publish("checking", "Checking the signed-in account…")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                networkMutex.withLock {
                    val current = account.load()
                    if (ticket != generation || closed || !foreground || !online || busy) return@withLock
                    if (!current.configured || current.accountId == null) {
                        if (record.enabled) review("Account setup or sign-in changed. Review before resuming automatic sync.")
                        else publish("disabled", "Sign in before enabling automatic sync.")
                        return@withLock
                    }
                    if (current.accountId != expectedOwner || account.currentAccountId() != expectedOwner) { review("Account changed while checking sign-in. Review before enabling sync for this account."); return@withLock }
                    if (restoring && current.accountId != record.accountId) { review("Account changed. Existing consent cannot upload to this account."); return@withLock }
                    if (!acknowledged && record.accountId != null && current.accountId != record.accountId) { review("Consent and deletion guards belong to another account. Review local data before resuming for this account."); return@withLock }
                    val next = if (acknowledged) AutomationSyncRecord(enabled = true, accountId = current.accountId)
                        else record.copy(enabled = true, accountId = current.accountId)
                    if (!persist(next)) return@withLock
                    restored = true; restoreRequested = true
                    if (ready(ticket)) reconcile(ticket)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (ticket == generation) {
                    if (record.enabled) recordFailure() else publish("disabled", "Account check failed. Local data is kept; retry when ready.")
                }
            }
            finally { finished(ticket) }
        }
        operation = job; job.start(); return job
    }

    fun localChanged() {
        if (!restored || !ready(generation)) return
        if (operation?.isActive == true) queued = true else schedule(debounceMillis)
    }
    fun retry(): Job? {
        if (closed || storageDamaged || !record.enabled) return null
        if (record.reviewRequired) { publish("review", "Review cloud data before resuming."); return null }
        if (operation?.isActive == true) return operation
        if (!persist(record.copy(failureCount = 0))) return null
        if (!restored) return restoreAttempt()
        return requestSync()
    }
    /** Immediate foreground check without clearing the finite failure budget. */
    fun syncNow(): Job? = requestSync()
    private fun requestSync(): Job? {
        if (!restored || !ready(generation)) return null
        scheduled?.cancel(); scheduled = null
        if (operation?.isActive == true) { queued = true; return operation }
        val ticket = generation
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { networkMutex.withLock { if (ready(ticket)) reconcile(ticket) } }
            finally { finished(ticket) }
        }
        operation = job; job.start(); return job
    }
    private fun finished(ticket: Long) {
        if (ticket != generation || closed) return
        operation = null
        if (!record.enabled || record.reviewRequired || storageDamaged || !foreground || !online || busy || record.failureCount >= 5) return
        if (scheduled == null) schedule(if (record.failureCount > 0) AutomationSyncPolicy.retryDelayMillis(record.failureCount) ?: return else if (queued) debounceMillis else pollMillis)
        queued = false
    }
    private fun schedule(wait: Long) {
        if (!ready(generation)) return
        scheduled?.cancel(); val ticket = generation
        val job = scope.launch(start = CoroutineStart.LAZY) { sleep(wait); if (ticket == generation) { scheduled = null; if (restoreRequested && !restored) restoreAttempt() else requestSync() } }
        scheduled = job; job.start()
    }
    private fun readLocal(): CloudSnapshot = try { AutomationSyncPolicy.validate(local.read()) } catch (failure: Exception) { throw AutomaticSyncLocalException(failure) }
    private fun commitLocal(snapshot: CloudSnapshot) { try { local.commit(snapshot) } catch (failure: Exception) { throw AutomaticSyncLocalException(failure) } }
    private fun recordFailure() {
        val failures = minOf(5, record.failureCount + 1)
        if (!persist(record.copy(failureCount = failures))) return
        if (failures >= 5) publish("error", "Sync stopped after five failed attempts. Tap Retry when ready.")
        else { publish("retrying", "Sync unavailable. Local data is kept; retrying shortly."); schedule(AutomationSyncPolicy.retryDelayMillis(failures)!!) }
    }

    private suspend fun reconcile(ticket: Long) {
        if (!ready(ticket)) return
        val owner = record.accountId ?: return
        publish("syncing", "Checking cloud changes…")
        var attemptedRevision: Long? = null
        try {
            val current = account.load(); if (!ready(ticket)) return
            if (!current.configured || current.accountId != owner) { review("Account changed. Review before syncing."); return }
            val cloud = account.cloud(owner); if (!ready(ticket)) return
            check(cloud.ownerId == owner) { "Cloud account changed." }
            require(cloud.revision in 0..2147483647L) { "Invalid cloud revision." }
            val remote = AutomationSyncPolicy.validate(cloud.snapshot)
            var here = readLocal()
            if (AutomationSyncPolicy.requiresDeletionReview(record.baseline, here)) { review("Local deletion detected. Review cloud data manually before resuming."); return }
            if (AutomationSyncPolicy.requiresDeletionReview(record.baseline, remote)) { review("Cloud deletion detected. Review cloud data manually before resuming."); return }
            val plan = AutomationSyncPolicy.merge(here, remote, record.baseline)
            if (!AutomationSyncPolicy.same(here, plan.snapshot)) {
                if (!ready(ticket)) return
                commitLocal(plan.snapshot)
                if (!ready(ticket)) return
                here = readLocal()
                if (AutomationSyncPolicy.requiresDeletionReview(AutomationSyncPolicy.baseline(plan.snapshot), here)) { review("Local data changed during download. Review before uploading."); return }
            }
            if (plan.requiresContentReview) { review("This conversation changed on both devices. Both versions were kept locally; review before uploading."); return }
            if (!ready(ticket)) return
            if (AutomationSyncPolicy.same(here, remote)) {
                if (persist(record.copy(baseline = AutomationSyncPolicy.baseline(here), revision = cloud.revision, failureCount = 0))) publish("synced", "Cloud copy is up to date.")
                return
            }
            val confirmed = account.load(); if (!ready(ticket)) return
            if (confirmed.accountId != owner || !confirmed.configured) { review("Sign-in changed before upload. Review cloud data again."); return }
            val next = readLocal()
            if (AutomationSyncPolicy.requiresDeletionReview(AutomationSyncPolicy.baseline(here), next)) { review("Local deletion detected before upload. Review cloud data manually."); return }
            if (!ready(ticket)) return
            attemptedRevision = cloud.revision
            val updatedRevision = account.upload(owner, cloud.revision, next)
            if (!ready(ticket)) return
            require(updatedRevision > cloud.revision && updatedRevision <= 2147483647L) { "Invalid cloud revision." }
            if (persist(record.copy(baseline = AutomationSyncPolicy.baseline(next), revision = updatedRevision, failureCount = 0))) publish("synced", "Automatic cloud snapshot saved.")
            if (!AutomationSyncPolicy.same(next, readLocal())) queued = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (!ready(ticket)) return
            if (attemptedRevision != null) {
                var conflict = failure is AccountException && failure.status == 409
                try {
                    val remote = account.cloud(owner); if (!ready(ticket)) return
                    if (remote.ownerId != owner) { review("Cloud account changed. Review before syncing."); return }
                    if (conflict || remote.revision != attemptedRevision) {
                        conflict = true
                        val remoteSnapshot = AutomationSyncPolicy.validate(remote.snapshot); val here = readLocal()
                        if (!AutomationSyncPolicy.requiresDeletionReview(record.baseline, here) && !AutomationSyncPolicy.requiresDeletionReview(record.baseline, remoteSnapshot)) {
                            val plan = AutomationSyncPolicy.merge(here, remoteSnapshot, record.baseline)
                            if (!ready(ticket)) return
                            if (!AutomationSyncPolicy.same(here, plan.snapshot)) commitLocal(plan.snapshot)
                        }
                        if (ready(ticket)) review("Another device changed the cloud copy. Safe additions were kept locally; review before uploading again.")
                        return
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (!ready(ticket)) return; if (conflict) { review("Cloud conflict requires manual review. Local data is kept."); return } }
            }
            if (failure is AutomaticSyncLocalException || failure is IllegalArgumentException || failure.message == "Cloud account changed.") { review("Local data, snapshot limits, or account changes require manual cloud review. Local data is kept."); return }
            recordFailure()
        }
    }
}
