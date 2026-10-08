package com.dua.ai.settings

import org.junit.Assert.*
import org.junit.Test

class PreferencesCodecTest {
    @Test fun generatedVoiceDefaultsOffForExistingSettingsAndRequiresStrictBooleanConsent() {
        val old = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("generatedVoice") }.toString()
        assertFalse(PreferencesCodec.decode(old).generatedVoice)
        val enabled = LocalPreferences(generatedVoice = true)
        assertEquals(enabled, PreferencesCodec.decode(PreferencesCodec.encode(enabled)))
        assertNotNull(runCatching { PreferencesCodec.decode(org.json.JSONObject(old).put("generatedVoice", "true").toString()) }.exceptionOrNull())
    }
    @Test fun memoryIsExplicitAndPreferencesRoundTrip() {
        val off = LocalPreferences(theme = "light", font = "serif", haptic = true, memory = listOf("Prefer Hindi"))
        assertEquals("", PreferencesCodec.memoryText(off))
        val enabled = off.copy(memoryEnabled = true)
        assertEquals(enabled, PreferencesCodec.decode(PreferencesCodec.encode(enabled)))
        assertEquals("- Prefer Hindi", PreferencesCodec.memoryText(enabled))
    }
    @Test fun invalidMemoryAndUnknownAppearanceAreRejected() {
        for (value in listOf(LocalPreferences(memory = List(11) { "note" }), LocalPreferences(memory = listOf("x".repeat(201))), LocalPreferences(memory = listOf(" ")), LocalPreferences(theme = "invalid"))) {
            try { PreferencesCodec.encode(value); fail("Invalid settings accepted") } catch (_: IllegalArgumentException) { }
        }
        try { PreferencesCodec.decode("corrupt"); fail("Corrupt settings accepted") } catch (_: Exception) { }
    }
    @Test fun voiceSettingsRoundTripAndOldOrDamagedValuesFallBackToDefaults() {
        val voice = LocalPreferences(generatedVoice = true, voiceProvider = "gemini", voiceName = "Kore", deviceVoice = "hi-in-x-hia-local",
            voiceSpeed = 1.25f, replyLanguage = "hi", inputLanguage = "ur-PK")
        assertEquals(voice, PreferencesCodec.decode(PreferencesCodec.encode(voice)))
        val old = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).apply {
            listOf("voiceProvider", "voiceName", "deviceVoice", "voiceSpeed", "replyLanguage", "inputLanguage").forEach { remove(it) } }
        assertEquals(LocalPreferences(), PreferencesCodec.decode(old.toString()))
        val damaged = old.put("voiceProvider", "other").put("voiceName", "../x").put("voiceSpeed", 9).put("replyLanguage", "<b>").put("inputLanguage", 5)
        assertEquals(LocalPreferences(), PreferencesCodec.decode(damaged.toString()))
        for (value in listOf(LocalPreferences(voiceProvider = "other"), LocalPreferences(voiceSpeed = 3f), LocalPreferences(inputLanguage = "hi_IN"))) {
            try { PreferencesCodec.encode(value); fail("Invalid voice setting accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
