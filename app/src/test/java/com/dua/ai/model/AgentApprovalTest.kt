package com.dua.ai.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApprovalTest {
    private val id = "AbCdEfGhIjKlMnOpQrStUvWx"
    private fun event(id: String = this.id, tool: String = "create_issue", title: String = "Create a GitHub issue", expiresIn: Int = 900) =
        JSONObject().put("type", "approval").put("id", id).put("tool", tool).put("title", title)
            .put("details", JSONArray().put("Title: Crash").put("  Text:   steps  ").put(7)).put("expiresIn", expiresIn)

    @Test fun parsesAValidEventAndCleansDetails() {
        val approval = AgentApprovals.fromEvent(event(), now = 1_000L)!!
        assertEquals("create_issue", approval.tool)
        assertEquals(listOf("Title: Crash", "Text: steps"), approval.details)
        assertEquals(1_000L + 900_000L, approval.expiresAt)
        assertEquals(AgentApproval.Status.Pending, approval.status)
    }

    @Test fun rejectsMalformedEventsAndCapsExpiry() {
        assertNull(AgentApprovals.fromEvent(event(id = "short")))
        assertNull(AgentApprovals.fromEvent(event(id = "bad id with spaces and more!!")))
        assertNull(AgentApprovals.fromEvent(event(tool = "Drop Table")))
        assertNull(AgentApprovals.fromEvent(event(title = "   ")))
        assertEquals(900_000L, AgentApprovals.fromEvent(event(expiresIn = 999_999), now = 0L)!!.expiresAt)
    }

    @Test fun expiresOnlyWhilePending() {
        val approval = AgentApprovals.fromEvent(event(expiresIn = 1), now = 0L)!!
        assertFalse(approval.expired(500L))
        assertTrue(approval.expired(1_000L))
        assertFalse(approval.copy(status = AgentApproval.Status.Done).expired(5_000L))
    }

    @Test fun mergeReplacesByIdAndCapsPerReply() {
        val a = AgentApprovals.fromEvent(event())!!
        val list = AgentApprovals.merge(AgentApprovals.merge(emptyList(), a), a.copy(title = "new"))
        assertEquals(listOf("new"), list.map { it.title })
        var many = emptyList<AgentApproval>()
        repeat(5) { many = AgentApprovals.merge(many, a.copy(id = "id-number-$it-padding-xx")) }
        assertEquals(AgentApprovals.MAX_PER_REPLY, many.size)
    }

    @Test fun decisionBodySendsOnlyTheIdAndDecision() {
        val body = AgentApprovals.decisionBody(id, approve = true)
        assertEquals(setOf("id", "decision"), body.keys().asSequence().toSet())
        assertEquals("approve", body.getString("decision"))
        assertEquals("reject", AgentApprovals.decisionBody(id, approve = false).getString("decision"))
        assertTrue(runCatching { AgentApprovals.decisionBody("x", true) }.isFailure)
    }

    @Test fun outcomeMapsServerStatus() {
        assertEquals(AgentApproval.Status.Done to "Issue created: #5", AgentApprovals.outcome(JSONObject("""{"status":"done","text":"Issue created: #5"}""")))
        assertEquals(AgentApproval.Status.Rejected, AgentApprovals.outcome(JSONObject("""{"status":"rejected","text":"x"}""")).first)
        assertEquals(AgentApproval.Status.Failed, AgentApprovals.outcome(JSONObject("""{"status":"weird"}""")).first)
    }

    @Test fun outcomeLineIsSavedBeforeTheSourcesFooter() {
        val text = "Here is the plan [1].\n\nSources:\n[1] Docs\nhttps://docs.example.com/a"
        val declined = AgentApprovals.fromEvent(event())!!.copy(status = AgentApproval.Status.Rejected)
        val updated = AgentApprovals.appendOutcome(text, AgentApprovals.transcriptLine(declined))
        val parts = ReplySources.split(updated)
        assertTrue(parts.body.endsWith("You declined: Create a GitHub issue. Nothing was changed."))
        assertEquals(listOf("https://docs.example.com/a"), parts.sources.map { it.url })
        val done = declined.copy(status = AgentApproval.Status.Done, result = "Issue created: #5")
        assertEquals("Plain\n\n\u2705 Approved: Create a GitHub issue. Issue created: #5",
            AgentApprovals.appendOutcome("Plain", AgentApprovals.transcriptLine(done)))
        assertEquals("Plain", AgentApprovals.appendOutcome("Plain", ""))
    }
}
