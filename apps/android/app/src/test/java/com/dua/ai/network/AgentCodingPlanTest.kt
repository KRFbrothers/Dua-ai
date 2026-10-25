package com.dua.ai.network

import com.dua.ai.model.AgentCoding
import com.dua.ai.model.AgentCodingUpdate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentCodingPlanTest {
    private fun event(revision: Int = 1, status: String = "in_progress") = JSONObject().put("type", "coding")
        .put("plan", JSONObject().put("revision", revision).put("explanation", "Working through the change")
            .put("steps", JSONArray().put(JSONObject().put("step", "Read code").put("status", status))))
    private fun apply(json: JSONObject, previous: AgentCoding? = null) = requireNotNull(AgentCodingUpdate.fromEvent(json)).apply(previous)

    @Test fun newerPlansReplaceOlderPlansAndKeepOtherActivity() {
        val previous = AgentCoding(diff = "diff preview")
        val first = apply(event(), previous)
        val second = apply(event(2, "completed"), first)
        assertEquals("diff preview", second.diff)
        assertEquals(2, second.plan?.revision)
        assertEquals("completed", second.plan?.steps?.single()?.status)
        assertEquals(second, apply(event(), second))
        assertEquals(second, apply(event(2, "pending"), second))
    }

    @Test fun replyEndingFreezesPlanWithoutMarkingStepsDone() {
        val ended = apply(event()).ended()
        assertTrue(requireNotNull(ended.plan).ended)
        assertEquals("in_progress", ended.plan?.steps?.single()?.status)
        assertEquals(ended, apply(event(2, "completed"), ended))
    }

    @Test fun malformedOrOversizedPlansAreRejected() {
        assertNull(AgentCodingUpdate.fromEvent(event(0)))
        assertNull(AgentCodingUpdate.fromEvent(event(13)))
        assertNull(AgentCodingUpdate.fromEvent(event(status = "verified")))
        val twoActive = event()
        twoActive.getJSONObject("plan").getJSONArray("steps").put(JSONObject().put("step", "Edit").put("status", "in_progress"))
        assertNull(AgentCodingUpdate.fromEvent(twoActive))
        val tooLong = event()
        tooLong.getJSONObject("plan").put("explanation", "x".repeat(501))
        assertNull(AgentCodingUpdate.fromEvent(tooLong))
    }

    @Test fun wirePlanAppearsInTheSnapshotAndIsEndedWithTheReply() {
        val parser = ChatStreamParser()
        val accumulator = StreamAccumulator(0L) { 1000L }
        listOf(event().toString(), """{"type":"done","truncated":false}""", event(2, "completed").toString()).forEach { data ->
            listOf("data: $data", "").forEach { parser.line(it)?.let(accumulator::apply) }
        }
        val plan = requireNotNull(accumulator.snapshot().coding?.plan)
        assertTrue(plan.ended)
        assertEquals("Read code", plan.steps.single().text)
        assertEquals("in_progress", plan.steps.single().status)
    }
}
