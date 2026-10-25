package com.dua.ai.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRetryTest {
    private fun offlineWithQueued(text: String = "hello"): OfflineRetry = OfflineRetry().apply {
        onConnectivity(online = false, activeChatId = "a", draft = "", busy = false)
        assertTrue(sendFailed("a", text, offline = true))
    }

    @Test fun resendsOnceWhenBackOnline() {
        val retry = offlineWithQueued()
        assertEquals("hello", retry.onConnectivity(true, "a", "hello", busy = false))
        assertFalse(retry.hasPending)
        // Flapping or repeated callbacks never send it twice.
        assertNull(retry.onConnectivity(true, "a", "hello", busy = false))
        retry.onConnectivity(false, "a", "hello", busy = false)
        assertNull(retry.onConnectivity(true, "a", "hello", busy = false))
    }

    @Test fun onlineFailureIsNotQueued() {
        assertFalse(OfflineRetry().sendFailed("a", "hello", offline = false))
    }

    @Test fun noResendWhileStillOffline() {
        val retry = offlineWithQueued()
        assertNull(retry.onConnectivity(false, "a", "hello", busy = false))
        assertTrue(retry.hasPending)
    }

    @Test fun editedDraftOrOtherChatCancelsTheResend() {
        val edited = offlineWithQueued()
        assertNull(edited.onConnectivity(true, "a", "hello there", busy = false))
        assertFalse(edited.hasPending)
        val switched = offlineWithQueued()
        assertNull(switched.onConnectivity(true, "b", "hello", busy = false))
        assertFalse(switched.hasPending)
    }

    @Test fun manualSendClearsTheQueue() {
        val retry = offlineWithQueued()
        retry.clear()
        assertNull(retry.onConnectivity(true, "a", "hello", busy = false))
    }

    @Test fun busyDoesNotSendAndKeepsQueue() {
        val retry = offlineWithQueued()
        assertNull(retry.onConnectivity(true, "a", "hello", busy = true))
        assertTrue(retry.hasPending)
    }

    @Test fun automaticResendIsAttemptedOnlyOnce() {
        val retry = offlineWithQueued()
        assertEquals("hello", retry.onConnectivity(true, "a", "hello", busy = false))
        retry.onConnectivity(false, "a", "hello", busy = false)
        // The automatic resend failed offline again: the user must send it by hand.
        assertFalse(retry.sendFailed("a", "hello", offline = true))
        retry.delivered()
        assertTrue(retry.sendFailed("a", "hello", offline = true))
    }
}
