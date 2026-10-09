package com.dua.ai.history

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.FutureTask

/**
 * Persists chat history off the main thread. The in-memory chat list stays authoritative; only the
 * JSON encode and the SharedPreferences write move to [dispatcher].
 *
 * - [save] queues the latest list. A newer save supersedes an older one, so an older encode that
 *   finishes late is dropped and never overwrites newer data.
 * - [flush] writes the latest unsaved list on the caller's thread (onStop and onCleared), so
 *   nothing queued is lost when Android later kills the stopped process.
 * - [writeDirectly] runs the few transactional writes (import, cloud apply, sync, clear) under the
 *   same lock and retires anything queued before them, so a stale background write cannot land after.
 */
class ChatHistoryWriter(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val encode: (List<SavedChat>, String) -> String = ChatHistoryCodec::encode,
    private val write: (String) -> Unit,
) {
    private class Pending(val version: Long, val chats: List<SavedChat>, val activeId: String)
    private val lock = Any()
    private var version = 0L
    private var written = 0L
    private var latest: Pending? = null
    private var workerRunning = false

    fun save(chats: List<SavedChat>, activeId: String) {
        val start = synchronized(lock) {
            latest = Pending(++version, chats, activeId)
            (!workerRunning).also { workerRunning = true }
        }
        if (start) scope.launch(dispatcher) { drain() }
    }

    /** True while a save has not reached SharedPreferences yet. */
    val hasPendingWrite: Boolean get() = synchronized(lock) { latest?.let { written < it.version } == true }

    private fun drain() {
        try {
            while (true) {
                val next = synchronized(lock) {
                    val value = latest
                    if (value == null || written >= value.version) { workerRunning = false; return }
                    value
                }
                commitIfCurrent(next, encode(next.chats, next.activeId))
            }
        } catch (failure: Throwable) {
            synchronized(lock) { workerRunning = false }
            throw failure
        }
    }

    private fun commitIfCurrent(value: Pending, encoded: String) = synchronized(lock) {
        if (value.version == version && written < value.version) { write(encoded); written = value.version }
    }

    /** Writes the latest unsaved list now, on the calling thread. */
    fun flush() {
        val value = synchronized(lock) { latest?.takeIf { written < it.version } } ?: return
        commitIfCurrent(value, encode(value.chats, value.activeId))
    }

    /** Runs [block] (a transactional history write) under the writer lock; earlier queued saves are retired. */
    fun <T> writeDirectly(block: () -> T): T = synchronized(lock) {
        try { block() } finally { version++; written = version; latest = null }
    }
}

/**
 * Reads and decodes saved chats on a background thread while the activity starts, so the
 * ViewModel does not have to decode on the main thread. [take] only uses the preloaded result when
 * it was decoded from exactly the text that is stored now; otherwise it decodes [raw] itself.
 */
object ChatHistoryPreload {
    private class Loaded(val raw: String, val decoded: Result<Pair<String, List<SavedChat>>>)
    @Volatile private var task: FutureTask<Loaded?>? = null

    fun start(preferences: SharedPreferences, key: String = "history") {
        if (task != null) return
        val next = FutureTask { preferences.getString(key, null)?.let { raw -> Loaded(raw, runCatching { ChatHistoryCodec.decode(raw) }) } }
        task = next
        Thread(next, "dua-history-preload").apply { isDaemon = true }.start()
    }

    /** Decoded history for [raw], the text currently stored. Throws like [ChatHistoryCodec.decode]. */
    fun take(raw: String): Pair<String, List<SavedChat>> {
        val pending = task; task = null
        val loaded = pending?.let { runCatching { it.get() }.getOrNull() }
        return if (loaded != null && (loaded.raw === raw || loaded.raw == raw)) loaded.decoded.getOrThrow() else ChatHistoryCodec.decode(raw)
    }
}

/**
 * Runs the last scheduled action [delayMs] after the latest [schedule] call. Main-thread use only.
 * [runNow] runs a pending action immediately (also after [scope] was cancelled, e.g. in onCleared).
 */
class Debouncer(private val scope: CoroutineScope, private val delayMs: Long) {
    private var job: kotlinx.coroutines.Job? = null
    private var action: (() -> Unit)? = null
    val pending: Boolean get() = action != null

    fun schedule(next: () -> Unit) {
        job?.cancel()
        action = next
        job = scope.launch { kotlinx.coroutines.delay(delayMs); val run = action; action = null; job = null; run?.invoke() }
    }
    fun cancel() { job?.cancel(); job = null; action = null }
    fun runNow() { val run = action ?: return; cancel(); run() }
}
