package com.dua.ai.voice.live

import com.dua.ai.model.ChatMessage
import com.dua.ai.voice.LanguageOption
import com.dua.ai.voice.LiveVoiceConfig
import com.dua.ai.voice.VoiceOption
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveVoiceCodecTest {
    private val grant = JSONObject().put("sessionId", "0F8FAD5B-D9CB-469F-A165-70867728950E").put("token", "xai-realtime-client-secret-abc")
        .put("expiresAt", 1791500000).put("url", "wss://api.x.ai/v1/realtime?model=grok-voice-latest").put("model", "grok-voice-latest")
        .put("session", JSONObject().put("voice", "eve")).put("maxSeconds", 600)
        .put("usage", JSONObject().put("usedSeconds", 120).put("activeSeconds", 600).put("limitSeconds", 1800).put("resetAt", 1791590400))

    @Test fun grantIsValidatedAndTheTokenOnlyGoesToXai() {
        val parsed = LiveVoiceCodec.grant(grant)
        assertEquals("0f8fad5b-d9cb-469f-a165-70867728950e", parsed.sessionId)
        assertEquals(600, parsed.maxSeconds)
        assertEquals(1080, parsed.usage.leftSeconds)
        assertFalse("token is not printed", parsed.toString().contains("secret"))
        for ((key, value) in listOf("url" to "wss://evil.example/v1/realtime", "url" to "ws://api.x.ai/v1/realtime", "url" to "wss://user@api.x.ai/v1/realtime",
            "token" to "", "token" to "has space here", "sessionId" to "1", "maxSeconds" to 5)) {
            assertNotNull("$key=$value", runCatching { LiveVoiceCodec.grant(JSONObject(grant.toString()).put(key, value)) }.exceptionOrNull())
        }
        assertNotNull(runCatching { LiveVoiceCodec.grant(JSONObject(grant.toString()).apply { remove("session") }) }.exceptionOrNull())
    }

    @Test fun optionsFollowTheLiveCatalog() {
        val config = LiveVoiceConfig(enabled = true, voices = listOf(VoiceOption("eve", "Eve"), VoiceOption("leo", "Leo")), defaultVoice = "eve",
            languages = listOf(LanguageOption("auto", "Auto"), LanguageOption("hi", "Hindi"), LanguageOption("es-MX", "Spanish (Mexico)")))
        assertEquals(LiveOptions("leo", "hi", 1.5f), LiveVoiceCodec.options(config, "leo", "hi-IN", 2f))
        assertEquals(LiveOptions("eve", "auto", 0.7f), LiveVoiceCodec.options(config, "", "ur-PK", 0.5f))
        assertEquals("es-MX", LiveVoiceCodec.options(config, "eve", "es", 1f).language)
    }

    @Test fun sessionRequestSendsRecentCompletedTurnsAsPlainText() {
        val messages = listOf(
            ChatMessage(text = "Old question", isUser = true),
            ChatMessage(text = "## Answer\n**Bold** reply [1]", isUser = false),
            ChatMessage(text = "Still streaming", isUser = false, state = "streaming"),
            ChatMessage(text = "Make an image", isUser = true),
            ChatMessage(text = "", isUser = false, imagine = com.dua.ai.imagine.ImagineInfo(kind = "image", prompt = "cat")),
        )
        val body = LiveVoiceCodec.sessionRequest(LiveOptions("ara", "hi", 1.1f), "- Prefers Hindi", messages)
        assertEquals("ara", body.getString("voice")); assertEquals("hi", body.getString("language")); assertEquals(1.1, body.getDouble("speed"), 0.001)
        assertEquals("- Prefers Hindi", body.getString("memory"))
        val context = body.getJSONArray("context")
        assertEquals(listOf("user" to "Old question", "assistant" to "Answer\nBold reply", "user" to "Make an image"),
            (0 until context.length()).map { context.getJSONObject(it).let { item -> item.getString("role") to item.getString("text") } })
        val many = List(30) { ChatMessage(text = "m$it " + "x".repeat(900), isUser = it % 2 == 0) }
        val trimmed = LiveVoiceCodec.sessionRequest(LiveOptions("eve", "auto", 1f), "", many).getJSONArray("context")
        assertTrue(trimmed.length() <= LiveVoiceCodec.CONTEXT_MESSAGES)
        assertTrue(trimmed.getJSONObject(trimmed.length() - 1).getString("text").startsWith("m29"))
    }

    @Test fun transcriptBecomesChatMessages() {
        val messages = LiveVoiceCodec.messages(listOf(LiveCaption("user:1", true, "Hi Dua", true), LiveCaption("dua:1", false, "Hello! How", true, interrupted = true),
            LiveCaption("user:2", true, "", false), LiveCaption("dua:2", false, "Sure.", true)))
        assertEquals(listOf(true, false, false), messages.map { it.isUser })
        assertEquals("Hello! How …", messages[1].text); assertEquals("stopped", messages[1].state)
        assertEquals("complete", messages[2].state); assertEquals("Grok voice", messages[2].model)
    }
}
