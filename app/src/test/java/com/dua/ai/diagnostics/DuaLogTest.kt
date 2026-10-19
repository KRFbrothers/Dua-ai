package com.dua.ai.diagnostics

import com.dua.ai.account.AccountException
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DuaLogTest {
    private val sent = mutableListOf<Pair<SoftErrorArea, SoftError>>()
    private var original: ((SoftErrorArea, SoftError) -> Unit)? = null

    @Before fun capture() { original = DuaLog.sink; DuaLog.resetForTest(); DuaLog.sink = { area, error -> sent += area to error } }
    @After fun restore() { DuaLog.sink = original; DuaLog.resetForTest() }

    private fun SoftError.everyText(): String = generateSequence(this as Throwable) { it.cause }.joinToString("\n") { it.toString() + it.stackTraceToString() }

    @Test fun debugBuildsSendNothing() {
        assertNull("testDebugUnitTest runs the debug build, which must not report", original)
    }

    @Test fun reportsOnlyAreaAndClassNamesNeverTheMessage() {
        val secret = "owner@example.com Bearer eyJhbGciOi.secret-token user asked: my private chat text"
        val failure = IllegalStateException(secret, IOException("socket said $secret"))
        DuaLog.soft(SoftErrorArea.ChatReply, failure)
        val (area, error) = sent.single()
        assertEquals(SoftErrorArea.ChatReply, area)
        assertEquals("chat_reply: java.lang.IllegalStateException", error.message)
        assertEquals("java.io.IOException", error.cause!!.message)
        val text = error.everyText()
        listOf("owner@example.com", "Bearer", "secret-token", "private chat").forEach { assertFalse("leaked $it", text.contains(it)) }
        assertArrayEquals(failure.stackTrace, error.stackTrace)
    }

    @Test fun keepsTheHttpStatusOfApiErrors() {
        DuaLog.soft(SoftErrorArea.CloudSync, AccountException(503, "Service down for owner@example.com"))
        assertEquals("cloud_sync: com.dua.ai.account.AccountException (status 503)", sent.single().second.message)
    }

    @Test fun cancellationAndNoInternetAreNotBugs() {
        DuaLog.soft(SoftErrorArea.ChatReply, CancellationException("left the screen"))
        DuaLog.soft(SoftErrorArea.ChatReply, IOException("wrapped", java.net.UnknownHostException("dua-api")))
        DuaLog.soft(SoftErrorArea.DriveBackup, java.net.ConnectException("refused"))
        assertTrue(sent.isEmpty())
    }

    @Test fun eachKindIsCappedPerRun() {
        repeat(10) { DuaLog.soft(SoftErrorArea.DriveBackup, IOException("x")) }
        DuaLog.soft(SoftErrorArea.DriveBackup, IllegalStateException("y"))
        DuaLog.soft(SoftErrorArea.ChatRetry, IOException("z"))
        assertEquals(DuaLog.MAX_PER_KIND + 2, sent.size)
    }

    @Test fun aBrokenSinkNeverCrashesTheApp() {
        DuaLog.sink = { _, _ -> throw IllegalStateException("Crashlytics not ready") }
        DuaLog.soft(SoftErrorArea.ChatReply, IOException("x"))
    }

    @Test fun selfReferencingCausesStop() {
        val loop = object : Exception("a") { override val cause: Throwable get() = this }
        DuaLog.soft(SoftErrorArea.ChatReply, loop)
        assertNull(sent.single().second.cause)
    }
}
