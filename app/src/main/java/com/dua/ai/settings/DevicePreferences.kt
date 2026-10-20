package com.dua.ai.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray

data class LocalPreferences(val theme: String = "dark", val font: String = "default", val haptic: Boolean = false,
    val notifications: Boolean = false, val memoryEnabled: Boolean = false, val memory: List<String> = emptyList(), val generatedVoice: Boolean = false,
    /** Imagine defaults: image provider ("" = server default, "xai" or "gemini") and aspect ratio. */
    val imagineProvider: String = "", val imagineAspect: String = "1:1",
    /**
     * Voice v2. [generatedVoice] stays the AI-voice consent switch (off = device voice). [voiceProvider] is the AI provider
     * ("" = server default), [voiceName] the AI voice ("" = provider default), [deviceVoice] the device engine voice
     * ("" = engine default), [replyLanguage] and [inputLanguage] "auto" or a BCP-47 tag, [voiceSpeed] 0.5–2.0
     * (AI voices are clamped to the provider's range).
     */
    val voiceProvider: String = "", val voiceName: String = "", val deviceVoice: String = "", val voiceSpeed: Float = 1f,
    val replyLanguage: String = "auto", val inputLanguage: String = "auto",
    /** Live voice (xAI realtime) voice id; "" = server default. */
    val liveVoice: String = "",
    val projects: List<com.dua.ai.projects.ChatProject> = com.dua.ai.projects.ProjectCodec.defaults,
    /** Chat dictation: send automatically when you stop speaking (off = review in the composer first). */
    val dictationAutoSend: Boolean = false,
    /** Chosen free OpenRouter model id ("" = none; the normal model answers). Used only when the engine still offers it. */
    val freeModel: String = "",
    /** Small on-device AI (chat titles, Imagine prompt polish). Used only on phones that support it; off = always the server. */
    val onDeviceAi: Boolean = true,
    /** Offline voice typing (Vosk): "off", "fallback" (only when the phone's recognizer is missing) or "always". */
    val offlineDictation: String = "off",
    /** Downloaded Vosk model used for offline voice typing (id from OfflineModels.catalog). */
    val offlineModel: String = "hi")

object PreferencesCodec {
    fun validate(value: LocalPreferences): LocalPreferences {
        com.dua.ai.projects.ProjectCodec.validate(value.projects)
        require(value.theme in listOf("dark", "light", "system") && value.font in listOf("default", "system", "serif", "monospace"))
        require(value.imagineProvider in listOf("", "xai", "gemini") && value.imagineAspect in com.dua.ai.imagine.ImagineCodec.ASPECTS) { "Invalid Imagine default." }
        require((value.voiceProvider.isEmpty() || value.voiceProvider in com.dua.ai.voice.VoiceCodec.PROVIDERS) && com.dua.ai.voice.VoiceCodec.validId(value.voiceName)
            && value.deviceVoice.length <= 200 && value.voiceSpeed in 0.5f..2f && com.dua.ai.voice.VoiceCodec.validLanguage(value.replyLanguage)
            && com.dua.ai.voice.VoiceCodec.validLanguage(value.inputLanguage) && com.dua.ai.voice.VoiceCodec.validId(value.liveVoice)) { "Invalid voice setting." }
        require(value.offlineDictation in com.dua.ai.voice.offline.OfflineModels.MODES && com.dua.ai.voice.offline.OfflineModels.byId(value.offlineModel) != null) { "Invalid offline voice setting." }
        require(value.freeModel.isEmpty() || com.dua.ai.model.FreeModels.validId(value.freeModel)) { "Invalid free model." }
        require(value.memory.size <= 10 && value.memory.all { it.isNotBlank() && it.length <= 200 }) { "Save at most 10 memories, each up to 200 characters." }
        return value
    }
    fun encode(value: LocalPreferences): String {
        validate(value)
        return JSONObject().put("theme", value.theme).put("font", value.font).put("haptic", value.haptic)
            .put("notifications", value.notifications).put("memoryEnabled", value.memoryEnabled)
            .put("memory", JSONArray(value.memory)).put("generatedVoice", value.generatedVoice)
            .put("imagineProvider", value.imagineProvider).put("imagineAspect", value.imagineAspect)
            .put("voiceProvider", value.voiceProvider).put("voiceName", value.voiceName).put("deviceVoice", value.deviceVoice)
            .put("voiceSpeed", value.voiceSpeed.toDouble()).put("replyLanguage", value.replyLanguage).put("inputLanguage", value.inputLanguage).put("liveVoice", value.liveVoice)
            .put("dictationAutoSend", value.dictationAutoSend).put("freeModel", value.freeModel).put("onDeviceAi", value.onDeviceAi)
            .put("offlineDictation", value.offlineDictation).put("offlineModel", value.offlineModel)
            .put("projects", com.dua.ai.projects.ProjectCodec.toJson(value.projects)).toString()
    }
    fun decode(raw: String): LocalPreferences {
        val data = JSONObject(raw); val notes = data.getJSONArray("memory")
        val generatedVoice = if (!data.has("generatedVoice")) false else data.get("generatedVoice") as? Boolean ?: error("Invalid voice preference")
        // Optional since Imagine; unknown values fall back to the defaults instead of failing all settings.
        val imagineProvider = data.optString("imagineProvider").takeIf { it in listOf("", "xai", "gemini") } ?: ""
        val imagineAspect = data.optString("imagineAspect", "1:1").takeIf { it in com.dua.ai.imagine.ImagineCodec.ASPECTS } ?: "1:1"
        // Optional since Voice v2, with the same fall-back-to-default rule.
        val voiceProvider = data.optString("voiceProvider").takeIf { it.isEmpty() || it in com.dua.ai.voice.VoiceCodec.PROVIDERS } ?: ""
        val voiceName = data.optString("voiceName").takeIf(com.dua.ai.voice.VoiceCodec::validId) ?: ""
        val deviceVoice = data.optString("deviceVoice").takeIf { it.length <= 200 } ?: ""
        val voiceSpeed = data.optDouble("voiceSpeed", 1.0).toFloat().takeIf { it in 0.5f..2f } ?: 1f
        val replyLanguage = data.optString("replyLanguage", "auto").takeIf(com.dua.ai.voice.VoiceCodec::validLanguage) ?: "auto"
        val inputLanguage = data.optString("inputLanguage", "auto").takeIf(com.dua.ai.voice.VoiceCodec::validLanguage) ?: "auto"
        val liveVoice = data.optString("liveVoice").takeIf(com.dua.ai.voice.VoiceCodec::validId) ?: ""
        return validate(LocalPreferences(data.getString("theme"), data.getString("font"), data.getBoolean("haptic"),
            data.getBoolean("notifications"), data.getBoolean("memoryEnabled"), (0 until notes.length()).map { (notes.get(it) as? String ?: error("Invalid memory note")) }, generatedVoice,
            imagineProvider, imagineAspect, voiceProvider, voiceName, deviceVoice, voiceSpeed, replyLanguage, inputLanguage, liveVoice,
            com.dua.ai.projects.ProjectCodec.fromJson(if (data.has("projects")) data.getJSONArray("projects") else null),
            dictationAutoSend = data.optBoolean("dictationAutoSend", false),
            freeModel = data.optString("freeModel").takeIf { it.isEmpty() || com.dua.ai.model.FreeModels.validId(it) } ?: "",
            onDeviceAi = data.optBoolean("onDeviceAi", true),
            offlineDictation = data.optString("offlineDictation", "off").takeIf { it in com.dua.ai.voice.offline.OfflineModels.MODES } ?: "off",
            offlineModel = data.optString("offlineModel", "hi").takeIf { com.dua.ai.voice.offline.OfflineModels.byId(it) != null } ?: "hi"))
    }
    fun memoryText(value: LocalPreferences): String = if (value.memoryEnabled) value.memory.joinToString("\n") { "- $it" } else ""
}

class DevicePreferences(context: Context) {
    private val context = context.applicationContext
    private val storage = context.getSharedPreferences("dua_preferences", Context.MODE_PRIVATE)
    private var damaged = false
    private val mutable = MutableStateFlow(runCatching { storage.getString("preferences", null)?.let(PreferencesCodec::decode) ?: LocalPreferences() }.getOrElse { damaged = true; LocalPreferences() })
    val state = mutable.asStateFlow()
    fun requireReadable() { check(!damaged) { "Saved settings could not be read. Reset settings in Privacy before syncing." } }
    fun reset() {
        com.dua.ai.tasks.TaskReminders.reset(context)
        val projects = mutable.value.projects
        damaged = false
        try { update(LocalPreferences(projects = projects)) } catch (error: Exception) { damaged = true; throw error }
    }
    fun update(value: LocalPreferences) {
        check(!damaged) { "Saved settings could not be read. Reset settings in Privacy to continue." }
        val encoded = PreferencesCodec.encode(value)
        val previous = storage.getString("preferences", null)
        if (!storage.edit().putString("preferences", encoded).commit()) {
            storage.edit().putString("preferences", previous).apply()
            error("Settings could not be saved. Try again.")
        }
        mutable.value = value
        com.dua.ai.tasks.TaskReminders.refresh(context)
    }
}
