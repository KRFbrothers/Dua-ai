package com.dua.ai.model

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Run code (Phase 5): `code` stream events, merging, local history and the request flag. */
class CodeRunsTest {
    private fun parse(vararg lines: String): List<ReplyEvent> {
        val parser = ChatStreamParser()
        return lines.mapNotNull { parser.line(it) } + listOfNotNull(parser.finish())
    }

    @Test fun codeEventsDecodeAndInvalidOnesAreIgnored() {
        val events = parse(
            "event: code", "data: {\"type\":\"code\",\"index\":0,\"language\":\"python\",\"code\":\"print(2+2)\"}", "",
            "event: code", "data: {\"type\":\"code\",\"index\":0,\"output\":\"4\\n\",\"outcome\":\"ok\"}", "",
            "event: code", "data: {\"type\":\"code\",\"index\":99,\"code\":\"x\"}", "",
            "event: code", "data: {\"type\":\"code\",\"index\":1}", "",
        )
        assertEquals(listOf(ReplyEvent.Code(0, "python", "print(2+2)"), ReplyEvent.Code(0, null, null, "4\n", "ok")), events)
    }

    @Test fun accumulatorMergesCodeThenOutputAndKeepsRunsInOrder() {
        val accumulator = StreamAccumulator(0L) { 0L }
        accumulator.apply(ReplyEvent.Text("Let me check."))
        accumulator.apply(ReplyEvent.Code(0, "PYTHON", "sum(range(10))"))
        assertTrue(accumulator.snapshot().codeRuns.single().running)
        accumulator.apply(ReplyEvent.Code(0, output = "45", outcome = "ok"))
        accumulator.apply(ReplyEvent.Code(1, "python", "1/0"))
        accumulator.apply(ReplyEvent.Code(1, outcome = "error"))
        // An out-of-order index (no run 2 yet) cannot create a hole.
        accumulator.apply(ReplyEvent.Code(3, "python", "skipped"))
        val runs = accumulator.snapshot().codeRuns
        assertEquals(listOf(CodeRun("python", "sum(range(10))", "45", "ok"), CodeRun("python", "1/0", "", "error")), runs)
        assertFalse(runs[0].failed); assertTrue(runs[1].failed)
    }

    @Test fun mergeCapsSizeAndUnknownOutcomes() {
        val big = "x".repeat(CodeRuns.MAX_CHARS + 10)
        val runs = CodeRuns.merge(emptyList(), 0, "bad language!", big, null, null)
        assertEquals("python", runs.single().language)
        assertEquals(CodeRuns.MAX_CHARS, runs.single().code.length)
        assertNull(CodeRuns.merge(runs, 0, null, null, "out", "exploded").single().outcome)
        assertEquals(runs, CodeRuns.merge(runs, CodeRuns.MAX_RUNS, "python", "x", null, null))
    }

    @Test fun codeRunsAreSavedInLocalHistoryButNotExported() {
        val reply = ChatMessage(id = "r", text = "It is 4.", isUser = false, model = "Dua", codeRuns = listOf(CodeRun("python", "print(2+2)", "4", "ok"), CodeRun("python", "while True: pass", "", "timeout")))
        val chat = SavedChat("c", "Dua", "", listOf(ChatMessage(id = "u", text = "2+2?", isUser = true, model = "Dua"), reply), 5)
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "c")).second.single()
        assertEquals(reply.codeRuns, restored.messages[1].codeRuns)
        assertFalse(ChatHistoryCodec.export(listOf(chat)).contains("print(2+2)"))
        // Older history entries without the field still load.
        val legacy = JSONObject(ChatHistoryCodec.encode(listOf(chat), "c"))
        legacy.getJSONArray("chats").getJSONObject(0).getJSONArray("messages").getJSONObject(1).remove("codeRuns")
        assertTrue(ChatHistoryCodec.decode(legacy.toString()).second.single().messages[1].codeRuns.isEmpty())
    }

    @Test fun copyTextAddsCodeAndOutputAsFences() {
        val text = CodeRuns.copyText(listOf(CodeRun("python", "print(1)\n", "1\n", "ok")))
        assertEquals("\n\n```python\nprint(1)\n```\nOutput:\n```\n1\n```", text)
    }

    @Test fun requestCarriesCodeOnlyOnTheStreamingPath() {
        val api = DuaApiClient("https://dua.example/")
        val history = listOf(ChatMessage(text = "2+2?", isUser = true))
        fun body(stream: Boolean, code: Boolean) = JSONObject(Buffer().also { api.buildRequest("Dua", history, emptyList(), "", null, null, false, stream, code = code).body!!.writeTo(it) }.readUtf8())
        assertTrue(body(stream = true, code = true).getBoolean("code"))
        assertFalse(body(stream = true, code = false).has("code"))
        assertFalse(body(stream = false, code = true).has("code"))
    }

    @Test fun configParsesRunCodeAndOlderBackendsHideIt() {
        val config = IntegrationCapabilities.configuration(JSONObject("{\"providers\":[],\"code\":{\"enabled\":true,\"providers\":[\"Dua\",\"Gemini\",\"Evil\"]}}"), IntegrationState())
        assertEquals(setOf("Dua", "Gemini"), config.codeExecution!!.providers)
        assertTrue(config.codeExecution!!.available("Dua")); assertFalse(config.codeExecution!!.available("Claude"))
        val off = IntegrationCapabilities.configuration(JSONObject("{\"code\":{\"enabled\":false,\"reason\":\"Run code is turned off on this server (DUA_CODE_EXECUTION=0).\",\"providers\":[]}}"), IntegrationState())
        assertFalse(off.codeExecution!!.enabled); assertTrue(off.codeExecution!!.reason.contains("DUA_CODE_EXECUTION"))
        assertNull(IntegrationCapabilities.configuration(JSONObject("{}"), IntegrationState()).codeExecution)
    }
}
