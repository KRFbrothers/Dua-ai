package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ChatStreamTest {
    private fun parse(vararg lines: String): List<ReplyEvent> {
        val parser = ChatStreamParser()
        return lines.mapNotNull { parser.line(it) } + listOfNotNull(parser.finish())
    }

    @Test fun parsesEventsAndIgnoresHeartbeatsAndUnknownTypes() {
        val events = parse(
            ": stream", "",
            "event: thinking", "data: {\"type\":\"thinking\",\"delta\":\"Plan\"}", "",
            ": ping", "",
            "event: text", "data: {\"type\":\"text\",\"delta\":\"नमस्ते \"}", "",
            "event: future", "data: {\"type\":\"future\",\"x\":1}", "",
            "event: sources", "data: {\"type\":\"sources\",\"sources\":[{\"title\":\"A\",\"url\":\"https://a.example\"}]}", "",
            "event: done", "data: {\"type\":\"done\",\"truncated\":true}", "",
        )
        assertEquals(listOf(ReplyEvent.Thinking("Plan"), ReplyEvent.Text("नमस्ते "), ReplyEvent.Sources(listOf(com.dua.ai.model.ReplySource(0, "A", "https://a.example"))), ReplyEvent.Done(true)), events)
    }

    @Test fun multiLineDataTrailingEventAndErrorsDecode() {
        assertEquals(listOf(ReplyEvent.Text("a")), parse("data: {\"type\":", "data: \"text\",\"delta\":\"a\"}"))
        assertEquals(listOf(ReplyEvent.Failed("Gemini quota reached.", 429)), parse("data: {\"type\":\"error\",\"message\":\"Gemini quota reached.\",\"status\":429}", ""))
        val fallback = parse("data: {\"type\":\"error\"}", "").single() as ReplyEvent.Failed
        assertEquals(502, fallback.status); assertTrue(fallback.message.contains("Partial reply kept"))
        assertTrue(parse("data: not json", "", "data: {\"type\":\"text\",\"delta\":\"\"}", "").isEmpty())
    }

    @Test fun oversizedEventIsRejected() {
        val parser = ChatStreamParser()
        assertThrows(IllegalArgumentException::class.java) { parser.line("data: " + "x".repeat(ChatStreamParser.MAX_EVENT_CHARS + 1)) }
    }

    @Test fun accumulatorTimesThinkingUntilTheAnswerStarts() {
        var now = 1_000L
        val stream = StreamAccumulator(startedAt = 1_000L) { now }
        stream.apply(ReplyEvent.Thinking("Step one. "))
        now = 5_000
        stream.apply(ReplyEvent.Thinking("Step two."))
        assertNull(stream.snapshot().thinkingSeconds)
        now = 12_200
        stream.apply(ReplyEvent.Text("Answer"))
        now = 30_000
        stream.apply(ReplyEvent.Text(" done"))
        stream.apply(ReplyEvent.Done(false))
        val snapshot = stream.snapshot()
        assertEquals(StreamSnapshot("Answer done", "Step one. Step two.", 11, null, true), snapshot)
    }

    @Test fun stoppedWhileThinkingFixesTimeAndTruncationAddsNotice() {
        var now = 0L
        val stream = StreamAccumulator(0L) { now }
        stream.apply(ReplyEvent.Thinking("Still thinking"))
        now = 3_000
        assertEquals(3, stream.snapshot(ended = true).thinkingSeconds)
        assertFalse(stream.finished)
        val plain = StreamAccumulator(0L) { now }
        plain.apply(ReplyEvent.Text("Long"))
        plain.apply(ReplyEvent.Notice("Claude does not show its thinking."))
        assertEquals("Claude does not show its thinking.", plain.snapshot().notice)
        plain.apply(ReplyEvent.Done(truncated = true))
        assertNull(plain.snapshot().thinking); assertNull(plain.snapshot().thinkingSeconds)
        assertTrue(plain.snapshot().notice!!.contains("output limit"))
    }

    @Test fun thinkingIsCapped() {
        val stream = StreamAccumulator(0L) { 0L }
        repeat(30) { stream.apply(ReplyEvent.Thinking("x".repeat(1000))) }
        assertTrue(stream.snapshot().thinking!!.length <= ChatMessage.MAX_THINKING_CHARS + 1000)
    }
}
