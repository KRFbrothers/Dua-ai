package com.dua.ai.voice

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VoiceCodecTest {
    private fun voices(vararg ids: String) = JSONArray(ids.map { JSONObject().put("id", it).put("name", it.replaceFirstChar(Char::uppercase)) })
    private fun languages(vararg ids: String) = JSONArray(ids.map { JSONObject().put("id", it).put("name", it) })
    private fun config() = JSONObject().put("version", 1).put("tts", JSONObject().put("enabled", true).put("defaultProvider", "xai").put("providers", JSONArray()
        .put(JSONObject().put("id", "xai").put("name", "Grok").put("voices", voices("eve", "ara", "rex")).put("defaultVoice", "eve")
            .put("languages", languages("auto", "en", "hi", "ur")).put("speed", JSONObject().put("min", 0.7).put("max", 1.5).put("default", 1)).put("maxCharacters", 2000).put("format", "audio/mpeg"))
        .put(JSONObject().put("id", "gemini").put("name", "Gemini").put("model", "gemini-3.8-flash-tts").put("voices", voices("Kore", "Puck")).put("defaultVoice", "Kore")
            .put("languages", languages("auto")).put("speed", JSONObject().put("min", 0.7).put("max", 1.5)).put("maxCharacters", 1000).put("format", "audio/wav"))
        .put(JSONObject().put("id", "elevenlabs").put("name", "ElevenLabs").put("voices", voices("configured")).put("speed", JSONObject.NULL).put("maxCharacters", 2000))
        .put(JSONObject().put("id", "unknown").put("voices", voices("x")))
        .put(JSONObject().put("id", "xai").put("voices", voices("duplicate")))))

    @Test fun oldBackendsAndUnknownVersionsHideVoiceV2() {
        assertNull(VoiceCodec.config(null))
        assertNull(VoiceCodec.config(JSONObject().put("version", 0)))
        val off = VoiceCodec.config(JSONObject().put("version", 1).put("tts", JSONObject().put("enabled", false).put("reason", "Set GROK_API_KEY.").put("providers", JSONArray())))!!
        assertFalse(off.ttsEnabled); assertEquals("Set GROK_API_KEY.", off.ttsReason); assertNull(VoiceCodec.ttsOptions(off, "xai", "eve", "hi", 1f))
    }

    @Test fun catalogParsesKnownProvidersOnceAndDropsUnknownOnes() {
        val parsed = VoiceCodec.config(config())!!
        assertEquals(listOf("xai", "gemini", "elevenlabs"), parsed.providers.map { it.id })
        assertEquals(listOf("eve", "ara", "rex"), parsed.provider("xai")!!.voices.map { it.id })
        assertEquals("audio/wav", parsed.provider("gemini")!!.format)
        assertNull(parsed.provider("elevenlabs")!!.speed)
        assertEquals("xai", parsed.defaultProvider)
        assertNull(parsed.live)
    }

    @Test fun savedChoicesFallBackToServerDefaultsAndSpeedIsClamped() {
        val parsed = VoiceCodec.config(config())!!
        assertEquals(TtsOptions("xai", "ara", "hi", 1.5f), VoiceCodec.ttsOptions(parsed, "xai", "ara", "hi-IN", 2f))
        assertEquals(TtsOptions("xai", "eve", "auto", 0.7f), VoiceCodec.ttsOptions(parsed, "", "missing", "fr", 0.5f))
        assertEquals(TtsOptions("gemini", "Kore", "auto", 1f), VoiceCodec.ttsOptions(parsed, "gemini", "eve", "ur-PK", 1f))
        assertEquals(TtsOptions("elevenlabs", "configured", "auto", 1f), VoiceCodec.ttsOptions(parsed, "elevenlabs", "", "hi", 1.4f))
    }

    @Test fun requestBodyIsLegacyWithoutOptionsAndExplicitWithThem() {
        assertEquals(setOf("text"), VoiceCodec.requestBody("Hi", null).keys().asSequence().toSet())
        val body = VoiceCodec.requestBody("Hi", TtsOptions("xai", "eve", "hi", 1.25f))
        assertEquals("xai", body.getString("provider")); assertEquals("eve", body.getString("voice")); assertEquals("hi", body.getString("language"))
        assertEquals(1.25, body.getDouble("speed"), 0.0001)
    }

    @Test fun idsAndLanguageTagsAreValidated() {
        assertTrue(VoiceCodec.validId("")); assertTrue(VoiceCodec.validId("Zephyr")); assertFalse(VoiceCodec.validId("../x")); assertFalse(VoiceCodec.validId("a b"))
        for (tag in listOf("auto", "hi", "ur-PK", "pt-BR", "zh-Hant-TW")) assertTrue(tag, VoiceCodec.validLanguage(tag))
        for (tag in listOf("", "h", "hi_IN", "auto-x-y-z-w", "<script>")) assertFalse(tag, VoiceCodec.validLanguage(tag))
        assertNotEquals(TtsOptions("xai", "eve", "hi", 1f).cacheKey("a"), TtsOptions("xai", "ara", "hi", 1f).cacheKey("a"))
    }

    @Test fun liveSectionIsReadWhenPresent() {
        val parsed = VoiceCodec.config(config().put("live", JSONObject().put("enabled", true).put("model", "grok-voice-latest").put("voices", voices("eve", "leo"))
            .put("defaultVoice", "leo").put("dailyMinutes", 30).put("maxSessionMinutes", 10)))!!
        val live = parsed.live!!
        assertTrue(live.enabled); assertEquals("leo", live.defaultVoice); assertEquals(30, live.dailyMinutes); assertEquals(10, live.maxSessionMinutes)
        assertFalse(VoiceCodec.config(config().put("live", JSONObject().put("enabled", true)))!!.live!!.enabled)
    }
}
