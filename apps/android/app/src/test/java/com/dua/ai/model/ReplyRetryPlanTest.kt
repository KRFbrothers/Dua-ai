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
    @Test fun rejectsEarlierStreamingAndFileBackedResponses() {
        val user = ChatMessage(text = "Hi", isUser = true)
        val reply = ChatMessage(text = "Reply", isUser = false)
        val cases = listOf(listOf(user, reply, user), listOf(user, reply.copy(state = ChatMessage.STREAMING)), listOf(user.copy(text = "Describe\nFiles: doc.pdf"), reply))
        for (messages in cases) {
            try { ReplyRetryPlan.create(messages, reply.id); fail("Invalid retry accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun stoppedBeforeAnyTextKeepsTheOriginalPromptOnce() {
        val user = ChatMessage(text = "Tell a story", isUser = true)
        val reply = ChatMessage(text = "", isUser = false, model = "Gemini", state = "stopped")
        val plan = ReplyRetryPlan.create(listOf(user, reply), reply.id)
        assertEquals("Gemini", plan.model)
        assertEquals(listOf(user), plan.context)
        assertEquals(listOf(user), plan.full)
    }
    @Test fun stoppedReplyStillRequiresItsOriginalUserPrompt() {
        val reply = ChatMessage(text = "", isUser = false, state = "stopped")
        assertThrows(IllegalArgumentException::class.java) { ReplyRetryPlan.create(listOf(reply), reply.id) }
        assertThrows(IllegalArgumentException::class.java) { ReplyRetryPlan.create(listOf(reply.copy(id = "earlier"), reply), reply.id) }
    }
    @Test fun stoppedPartialReplyCanBeRetried() {
        val user = ChatMessage(text = "Hi", isUser = true)
        val reply = ChatMessage(text = "Partial", isUser = false, model = "Grok", state = "stopped")
        val plan = ReplyRetryPlan.create(listOf(user, reply), reply.id)
        assertEquals("Grok", plan.model)
        assertEquals(listOf(user), plan.context)
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
