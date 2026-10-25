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
            voiceSpeed = 1.25f, replyLanguage = "hi", inputLanguage = "ur-PK", liveVoice = "ara")
        assertEquals(voice, PreferencesCodec.decode(PreferencesCodec.encode(voice)))
        val old = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).apply {
            listOf("voiceProvider", "voiceName", "deviceVoice", "voiceSpeed", "replyLanguage", "inputLanguage", "liveVoice").forEach { remove(it) } }
        assertEquals(LocalPreferences(), PreferencesCodec.decode(old.toString()))
        val damaged = old.put("voiceProvider", "other").put("voiceName", "../x").put("voiceSpeed", 9).put("replyLanguage", "<b>").put("inputLanguage", 5).put("liveVoice", "a b")
        assertEquals(LocalPreferences(), PreferencesCodec.decode(damaged.toString()))
        for (value in listOf(LocalPreferences(voiceProvider = "other"), LocalPreferences(voiceSpeed = 3f), LocalPreferences(inputLanguage = "hi_IN"), LocalPreferences(liveVoice = "../eve"))) {
            try { PreferencesCodec.encode(value); fail("Invalid voice setting accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun offlineDictationDefaultsOffRoundTripsAndRejectsUnknown() {
        val old = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("offlineDictation"); remove("offlineModel") }.toString()
        assertEquals("off", PreferencesCodec.decode(old).offlineDictation)
        assertEquals("hi", PreferencesCodec.decode(old).offlineModel)
        val saved = PreferencesCodec.decode(PreferencesCodec.encode(LocalPreferences(offlineDictation = "always", offlineModel = "en-in")))
        assertEquals("always", saved.offlineDictation); assertEquals("en-in", saved.offlineModel)
        val bad = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).put("offlineDictation", "cloud").put("offlineModel", "ur").toString()
        assertEquals("off", PreferencesCodec.decode(bad).offlineDictation); assertEquals("hi", PreferencesCodec.decode(bad).offlineModel)
        assertThrows(IllegalArgumentException::class.java) { PreferencesCodec.encode(LocalPreferences(offlineDictation = "cloud")) }
    }

    @Test fun onDeviceAiDefaultsOnForOldSettingsAndRoundTripsOff() {
        val old = org.json.JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("onDeviceAi") }.toString()
        assertTrue(PreferencesCodec.decode(old).onDeviceAi)
        val off = LocalPreferences(onDeviceAi = false)
        assertEquals(off, PreferencesCodec.decode(PreferencesCodec.encode(off)))
    }
}
