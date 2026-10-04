package com.dua.ai.model

import org.junit.Assert.*
import org.junit.Test

class ReplyRetryPlanTest {
    @Test fun excludesOldResponseAndKeepsPromptOnceWithOriginalModel() {
        val user = ChatMessage(text = "Hi", isUser = true)
        val reply = ChatMessage(text = "Old reply", isUser = false, model = "Gemini")
        val result = ReplyRetryPlan.create(listOf(user, reply), reply.id)
        assertEquals("Gemini", result.model)
        assertEquals(listOf(user), result.context)
        assertEquals("Old reply", reply.text)
    }
    @Test fun rejectsEarlierStoppedAndFileBackedResponses() {
        val user = ChatMessage(text = "Hi", isUser = true)
        val reply = ChatMessage(text = "Reply", isUser = false)
        val cases = listOf(listOf(user, reply, user), listOf(user, reply.copy(state = "stopped")), listOf(user.copy(text = "Describe\nFiles: doc.pdf"), reply))
        for (messages in cases) {
            try { ReplyRetryPlan.create(messages, reply.id); fail("Invalid retry accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun boundsContextWithoutAddingDuplicateUserMessage() {
        val history = (0..49).map { ChatMessage(text = "Message $it", isUser = it % 2 == 1) }
        val reply = ChatMessage(text = "Response", isUser = false)
        val plan = ReplyRetryPlan.create(history + reply, reply.id)
        assertEquals(39, plan.context.size)
        assertEquals(history.last(), plan.context.last())
        assertFalse(plan.context.contains(reply))
    }
}
