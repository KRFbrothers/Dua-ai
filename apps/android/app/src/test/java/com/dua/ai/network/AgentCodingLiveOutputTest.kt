package com.dua.ai.network

import com.dua.ai.model.AgentCodingUpdate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentCodingLiveOutputTest {
    private val first = "a".repeat(16)
    private val second = "b".repeat(16)
    private fun update(state: String, output: String = "", id: String = first, command: String? = null): AgentCodingUpdate {
        val terminal = JSONObject().put("id", id).put("state", state).put("output", output).put("profile", "python")
        command?.let { terminal.put("command", it) }
        return requireNotNull(AgentCodingUpdate.fromEvent(JSONObject().put("terminal", terminal)))
    }

    @Test fun snapshotsReplaceOutputAndPreserveCommandAndCardOrder() {
        var coding = update("preparing", command = "python main.py").apply(null)
        coding = update("preparing", id = second).apply(coding)
        coding = update("queued").apply(coding)
        coding = update("running", "one\n").apply(coding)
        coding = update("running", "one\ntwo\n").apply(coding)
        assertEquals(listOf(first, second), coding.terminals.map { it.id })
        assertEquals("python main.py", coding.terminals.first().command)
        assertEquals("one\ntwo\n", coding.terminals.first().output)
        assertNull(coding.terminals.first().exitCode)
    }

    @Test fun replyEndingMarksEveryActiveStateInterruptedAndRetainsOutput() {
        listOf("preparing", "queued", "running").forEach { state ->
            val coding = update(state, "partial\n").apply(null).ended()
            assertEquals("interrupted", coding.terminals.single().state)
            assertEquals("partial\n", coding.terminals.single().output)
            assertEquals(coding, update("running", "late\n").apply(coding))
        }
    }

    @Test fun lateLiveEventsCannotReopenFinalCardsOrRegressRunningToQueued() {
        listOf("succeeded", "failed", "timed_out", "cancelled", "error").forEach { state ->
            val coding = update(state, "final\n").apply(null)
            assertEquals(coding, update("running", "late\n").apply(coding))
        }
        val running = update("running", "working\n").apply(null)
        assertEquals(running, update("queued").apply(running))
    }

    @Test fun liveCodingEventsReachTheStreamSnapshotAndEndAsInterrupted() {
        val parser = ChatStreamParser()
        val accumulator = StreamAccumulator(0L) { 1000L }
        listOf(
            """{"type":"coding","terminal":{"id":"$first","state":"running","output":"live","profile":"python"}}""",
            """{"type":"done","truncated":false}"""
        ).forEach { data ->
            listOf("data: $data", "").forEach { line -> parser.line(line)?.let(accumulator::apply) }
        }
        val terminal = requireNotNull(accumulator.snapshot().coding).terminals.single()
        assertEquals("interrupted", terminal.state)
        assertEquals("live", terminal.output)
    }
}
