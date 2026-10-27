package com.dua.ai.network

import androidx.annotation.VisibleForTesting

/**
 * Remembers at most one chat message that could not be sent because the phone was offline, and
 * hands it back exactly once when the connection returns. A message is retried automatically only
 * once: if the automatic resend fails too, the user sends it again by hand.
 */
class OfflineRetry {
    private data class Pending(val chatId: String, val text: String)
    private var pending: Pending? = null
    private var lastAutoRetried: Pending? = null
    private var wasOnline = true

    @VisibleForTesting val hasPending: Boolean get() = pending != null

    /** A send failed before any reply text arrived. Returns true when it will be retried on reconnect. */
    fun sendFailed(chatId: String, text: String, offline: Boolean): Boolean {
        val candidate = Pending(chatId, text)
        if (!offline || text.isBlank() || candidate == lastAutoRetried) { pending = null; return false }
        pending = candidate
        return true
    }

    /** A reply arrived: the same text may be queued again after a future failure. */
    fun delivered() { lastAutoRetried = null }

    /** The user sent, edited, stopped or switched chats: forget the queued message. */
    fun clear() { pending = null }

    /**
     * Connectivity changed. Returns the text to resend when the phone just came back online, the
     * same chat is open, the composer still holds that exact text and nothing else is running.
     */
    fun onConnectivity(online: Boolean, activeChatId: String, draft: String, busy: Boolean): String? {
        val reconnected = online && !wasOnline
        wasOnline = online
        val queued = pending ?: return null
        if (!reconnected || busy) return null
        if (queued.chatId != activeChatId || queued.text != draft) { pending = null; return null }
        pending = null
        lastAutoRetried = queued
        return queued.text
    }
}
