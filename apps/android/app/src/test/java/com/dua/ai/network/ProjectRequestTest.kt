package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ConversationContext
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectRequestTest {
    private val api = DuaApiClient("https://dua.example")
    private fun request(context: ConversationContext?): JSONObject {
        val built = api.buildRequest("Dua", listOf(ChatMessage(text = "Review code", isUser = true)), emptyList(), "", "fake-session", null,
            reasoning = false, stream = true, context = context)
        val buffer = Buffer(); built.body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }
    @Test fun projectIdIsSentBesideConversationContextWithoutInlineInstructions() {
        val json = request(ConversationContext("chat-id-1234", "Earlier code review", "coding"))
        assertEquals("coding", json.getString("projectId"))
        assertEquals("chat-id-1234", json.getJSONObject("context").getString("id"))
        assertEquals(setOf("id", "summary"), json.getJSONObject("context").keys().asSequence().toSet())
        assertFalse(json.has("projectInstructions"))
        assertEquals("", json.getString("memory"))
    }
    @Test fun legacyContextsOmitTheProjectFieldAndMalformedProjectIdsFailBeforeNetwork() {
        assertFalse(request(ConversationContext("chat-id-1234", null)).has("projectId"))
        assertFalse(request(null).has("projectId"))
        try { request(ConversationContext("chat-id-1234", null, "../coding")); fail("Invalid project request accepted") }
        catch (_: IllegalArgumentException) {}
    }
}
