package com.dua.ai.account

import java.util.concurrent.FutureTask

/**
 * Cold start: decrypts the saved session (Android Keystore + AES-GCM) on a background thread while the activity
 * and first frame start, instead of on the main thread when the ViewModel is created. Used once: [store] serves the
 * preloaded result to the first read and then always reads [SessionPersistence] directly. A failed preload falls back
 * to a normal read, so errors behave exactly as before.
 */
object AccountSessionPreload {
    @Volatile private var task: FutureTask<Result<AccountSession?>>? = null

    fun start(store: SessionPersistence) {
        if (task != null) return
        val next = FutureTask { runCatching { store.read() } }
        task = next
        Thread(next, "dua-session-preload").apply { isDaemon = true }.start()
    }

    internal fun take(): Result<AccountSession?>? {
        val pending = task ?: return null
        task = null
        return runCatching { pending.get() }.getOrNull()
    }

    fun store(delegate: SessionPersistence): SessionPersistence = object : SessionPersistence {
        @Volatile private var first = true
        override fun read(): AccountSession? {
            if (first) { first = false; take()?.takeIf { it.isSuccess }?.let { return it.getOrNull() } }
            return delegate.read()
        }
        override fun write(value: AccountSession?) { first = false; task = null; delegate.write(value) }
    }
}
