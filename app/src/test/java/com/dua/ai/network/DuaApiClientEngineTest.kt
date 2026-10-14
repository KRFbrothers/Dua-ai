package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Separate chat engine (Cloud Run): optional app token header, never the account session token. */
class DuaApiClientEngineTest {
    private val history = listOf(ChatMessage(text = "Hi", isUser = true))

    @Test fun separateEngineGetsAppTokenButNoAccountToken() {
        val api = DuaApiClient("https://engine.example.run.app", appToken = "abc", sendAccountToken = false)
        val request = api.buildRequest("Dua", history, emptyList(), "", "session.token", null, reasoning = false, stream = true)
        assertEquals("https://engine.example.run.app/api/chat", request.url.toString())
        assertEquals("abc", request.header("X-Dua-App-Token"))
        assertNull(request.header("Authorization"))
    }

    @Test fun defaultClientSendsAccountTokenAndNoAppToken() {
        val api = DuaApiClient("https://dua.example.app")
        val request = api.buildRequest("Dua", history, emptyList(), "", "session.token", null, reasoning = false, stream = true)
        assertEquals("Bearer session.token", request.header("Authorization"))
        assertNull(request.header("X-Dua-App-Token"))
    }
}
