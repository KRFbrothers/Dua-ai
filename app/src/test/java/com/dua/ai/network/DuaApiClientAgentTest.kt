package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Agent mode (Cloud Run engine) sends mode "agent" on the existing streaming /api/chat path. */
class DuaApiClientAgentTest {
    private val history = listOf(ChatMessage(text = "Latest AI news?", isUser = true))
    private val api = DuaApiClient("https://engine.example.run.app", sendAccountToken = false)

    private fun body(agent: Boolean, reasoning: Boolean = false, build: String? = null): JSONObject {
        val request = api.buildRequest("Dua", history, emptyList(), "", null, null, reasoning = reasoning, stream = true, build = build, agent = agent)
        return JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun agentSendsAgentMode() {
        assertEquals("agent", body(agent = true).getString("mode"))
        assertEquals("agent", body(agent = true, reasoning = true).getString("mode"))
    }

    @Test fun withoutAgentModesAreUnchanged() {
        assertEquals("auto", body(agent = false).getString("mode"))
        assertEquals("reasoning", body(agent = false, reasoning = true).getString("mode"))
    }
}
