package com.dua.ai.voice

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GeneratedVoiceSessionTest {
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var owner: String? = "owner-a"
        var configured = true
        var calls = 0
        var plays = 0
        var released = 0
        var played: ByteArray? = null
        var finished: ((Boolean) -> Unit)? = null
        var generate: suspend (String) -> ByteArray = { byteArrayOf(1, 2, 3) }
        val output = object : GeneratedAudioOutput {
            override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) { plays++; played = bytes; finished = onFinished }
            override fun stop() { released++; played = null }
        }
        val session = GeneratedVoiceSession(scope, { calls++; generate(it) }, output, { owner }, { configured })
        fun ready() { session.setForeground(true); session.setOnline(true) }
        fun close() { session.close(); scope.cancel() }
    }
    @Test fun constructionPreferencesAndForegroundReturnNeverGenerate() {
        val f = Fixture(); f.ready(); f.session.setForeground(false); f.session.setForeground(true)
        f.session.setOnline(false); assertEquals("", f.session.state.value.status)
        assertEquals(0, f.calls); assertEquals(0, f.plays); f.close()
    }
    @Test fun onlyCompletedBoundedRepliesFromOnlineSignedInConfiguredDeviceCanGenerate() {
        val f = Fixture(); f.ready()
        for ((text, complete) in listOf("pending" to false, " " to true, "x".repeat(2001) to true)) f.session.toggle("a", text, complete)
        f.owner = null; f.session.toggle("a", "Hello", true)
        f.owner = "owner-a"; f.configured = false; f.session.toggle("a", "Hello", true)
        f.configured = true; f.session.setOnline(false); f.session.toggle("a", "Hello", true)
        assertEquals(0, f.calls); assertEquals(0, f.plays); f.close()
    }
    @Test fun explicitReplyIsSentAloneAndSameSpeakerTapStopsAndWipesIt() {
        val f = Fixture(); f.ready(); var selected = ""; val audio = byteArrayOf(4,5,6)
        f.generate = { selected = it; audio }; f.session.toggle("one", "  Selected Hindi reply  ", true)
        assertEquals("Selected Hindi reply", selected); assertEquals("playing", f.session.state.value.phase)
        f.session.toggle("one", "Selected Hindi reply", true)
        assertEquals(1, f.calls); assertEquals(1, f.plays); assertArrayEquals(ByteArray(3), audio)
        assertNull(f.session.state.value.activeId); f.close()
    }
    @Test fun completionAndErrorReleaseAudioWithoutRetry() {
        for (success in listOf(true,false)) {
            val f = Fixture(); f.ready(); val audio = byteArrayOf(9); f.generate = { audio }
            f.session.toggle("one", "Reply", true); f.finished!!(success)
            assertNull(f.session.state.value.activeId); assertArrayEquals(ByteArray(1), audio)
            assertEquals(1, f.calls); f.close()
        }
    }
    @Test fun stopBackgroundOfflineAccountChangeAndCloseRejectLateNoncancellableAudio() = runBlocking {
        val actions = listOf<(Fixture)->Unit>({ it.session.stop() }, { it.session.setForeground(false) },
            { it.session.setOnline(false) }, { it.owner = "owner-b"; it.session.accountChanged() }, { it.session.close() })
        actions.forEach { action ->
            val f = Fixture(); f.ready(); val gate = CompletableDeferred<Unit>(); val audio = byteArrayOf(1,2)
            f.generate = { withContext(NonCancellable) { gate.await() }; audio }
            f.session.toggle("one", "Reply", true); action(f); gate.complete(Unit); yield()
            assertEquals(0, f.plays); assertArrayEquals(ByteArray(2), audio); assertNull(f.session.state.value.activeId)
            f.close()
        }
    }
    @Test fun newReplyInvalidatesOldCompletionAndOldDownload() = runBlocking {
        val f = Fixture(); f.ready(); val gate = CompletableDeferred<Unit>(); val old = byteArrayOf(1); val latest = byteArrayOf(2)
        f.generate = { text -> if (text == "Old") { withContext(NonCancellable) { gate.await() }; old } else latest }
        f.session.toggle("old", "Old", true); f.session.toggle("new", "New", true); gate.complete(Unit); yield()
        assertEquals(1, f.plays); assertEquals("new", f.session.state.value.activeId); assertArrayEquals(ByteArray(1), old)
        val oldFinished = f.finished!!; f.session.toggle("third", "Third", true); oldFinished(true)
        assertEquals("third", f.session.state.value.activeId); f.close()
    }
    @Test fun changedCapabilityAndMalformedAudioFinishInsteadOfLeavingGenerationActive() = runBlocking {
        val f = Fixture(); f.ready(); val gate = CompletableDeferred<Unit>(); val audio = byteArrayOf(1)
        f.generate = { gate.await(); audio }; f.session.toggle("one", "Reply", true); f.configured = false; gate.complete(Unit); yield()
        assertNull(f.session.state.value.activeId); assertEquals(0, f.plays); assertArrayEquals(ByteArray(1), audio)
        f.configured = true; f.generate = { ByteArray(0) }; f.session.toggle("one", "Reply", true)
        assertNull(f.session.state.value.activeId); assertEquals(0, f.plays); f.close()
    }
    @Test fun outputFailureReleasesBeforeWipingAndDoesNotRetry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val bytes = byteArrayOf(7); var held = false; var stopped = false; var calls = 0
        val output = object : GeneratedAudioOutput {
            override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) { held = true; error("Playback unavailable") }
            override fun stop() { if (held) { assertEquals(7, bytes[0].toInt()); stopped = true; held = false } }
        }
        val session = GeneratedVoiceSession(scope, { calls++; bytes }, output, { "owner" }, { true })
        session.setForeground(true); session.setOnline(true); session.toggle("one", "Reply", true)
        assertTrue(stopped); assertArrayEquals(ByteArray(1), bytes); assertEquals(1, calls)
        session.close(); scope.cancel()
    }

    @Test fun chunkedRepliesPrefetchTheNextChunkAndPlayInOrder() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requested = mutableListOf<String>(); val played = mutableListOf<Byte>(); var finished: ((Boolean) -> Unit)? = null
        val output = object : GeneratedAudioOutput {
            override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) { played += bytes[0]; finished = onFinished }
            override fun stop() {}
        }
        val session = GeneratedVoiceSession(scope, { requested += it; byteArrayOf(it.last().code.toByte()) }, output, { "owner" }, { true },
            plan = { listOf("one 1", "two 2", "three 3") })
        session.setForeground(true); session.setOnline(true)
        session.toggle("reply", "ignored", true)
        assertEquals(listOf("one 1", "two 2"), requested)
        assertEquals("Playing AI voice (1 of 3)", session.state.value.status)
        finished!!(true); assertEquals(listOf("one 1", "two 2", "three 3"), requested)
        finished!!(true); finished!!(true)
        assertEquals(listOf('1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte()), played)
        assertNull(session.state.value.activeId)
        session.close(); scope.cancel()
    }

    @Test fun stoppingDuringAChunkedReplyWipesThePrefetchedChunk() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val chunks = mutableListOf<ByteArray>(); val gate = CompletableDeferred<Unit>()
        val output = object : GeneratedAudioOutput {
            override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) {}
            override fun stop() {}
        }
        val session = GeneratedVoiceSession(scope, { text -> if (text == "b") withContext(NonCancellable) { gate.await() }; byteArrayOf(7, 7).also { chunks += it } }, output,
            { "owner" }, { true }, plan = { listOf("a", "b") })
        session.setForeground(true); session.setOnline(true)
        session.toggle("reply", "ignored", true)
        session.stop(); gate.complete(Unit)
        assertTrue(chunks.all { it.all { byte -> byte == 0.toByte() } })
        assertNull(session.state.value.activeId)
        session.close(); scope.cancel()
    }

    @Test fun planFailuresAreShownWithoutGenerating() {
        val f = Fixture(); f.ready()
        val session = GeneratedVoiceSession(f.scope, { f.calls++; byteArrayOf(1) }, f.output, { "owner" }, { true }, plan = { throw IllegalArgumentException("Too long for AI voice.") })
        session.setForeground(true); session.setOnline(true); session.toggle("a", "x", true)
        assertEquals("Too long for AI voice.", session.state.value.status); assertEquals(0, f.calls); f.close()
    }
}
