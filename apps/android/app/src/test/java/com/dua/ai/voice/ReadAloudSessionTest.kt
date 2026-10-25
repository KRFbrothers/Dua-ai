package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test

class ReadAloudSessionTest {
    private class Output : SpeechOutput {
        val spoken = mutableListOf<Pair<String, String>>()
        var stops = 0
        var accepted = true
        override fun speak(text: String, utteranceId: String): Boolean { spoken += text to utteranceId; return accepted }
        override fun stop() { stops++ }
    }
    @Test fun playbackIsExplicitAndChunksAdvanceUntilComplete() {
        val output = Output(); val session = ReadAloudSession(output) {}
        session.availability(true); assertTrue(output.spoken.isEmpty())
        session.toggle("reply", "नमस्ते Dua. ".repeat(100))
        while (session.state.activeId != null) session.completed(output.spoken.last().second)
        assertTrue(output.spoken.size > 1); assertNull(session.state.activeId); assertEquals(3, output.stops)
    }
    @Test fun stopAndReplacementIgnoreStaleCompletionCallbacks() {
        val output = Output(); val session = ReadAloudSession(output) {}; session.availability(true)
        session.toggle("first", "x".repeat(700)); val old = output.spoken.last().second
        session.toggle("second", "Latest reply"); val new = output.spoken.last().second
        session.completed(old); assertEquals("second", session.state.activeId); assertEquals(2, output.spoken.size)
        session.toggle("second", "Latest reply"); session.completed(new)
        assertNull(session.state.activeId); assertEquals(2, output.spoken.size)
    }
    @Test fun unavailableAndFailedEnginesGiveRetryStateWithoutPlayback() {
        val output = Output(); val session = ReadAloudSession(output) {}
        session.toggle("reply", "Hello"); assertTrue(output.spoken.isEmpty()); assertFalse(session.state.available)
        session.availability(true); output.accepted = false; session.toggle("reply", "Hello")
        assertNull(session.state.activeId); assertTrue(session.state.status.contains("retry"))
    }
    @Test fun chunkingPreservesHindiAndEmojiWithoutSplittingSurrogatePairs() {
        val text = "नमस्ते 😀 Dua! ".repeat(200)
        val chunks = SpeechChunks.split(text)
        assertEquals(text.filterNot(Char::isWhitespace), chunks.joinToString("").filterNot(Char::isWhitespace))
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 300 && !Character.isLowSurrogate(it.first()) && !Character.isHighSurrogate(it.last()) })
        assertTrue(SpeechChunks.split("  ").isEmpty())
    }
}
