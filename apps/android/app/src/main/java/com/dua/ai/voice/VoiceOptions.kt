package com.dua.ai.voice

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One selectable voice (server catalog or device engine). */
data class VoiceOption(val id: String, val name: String, val description: String = "")
/** One selectable language. [note] explains a special case, e.g. auto-detected by the provider. */
data class LanguageOption(val id: String, val name: String, val note: String = "")
data class SpeedRange(val min: Float = 0.7f, val max: Float = 1.5f, val default: Float = 1f)

/** A reply-voice provider offered by the backend (`/api/config` › voice.tts.providers). */
data class TtsProvider(
    val id: String, val name: String, val voices: List<VoiceOption>, val defaultVoice: String,
    val languages: List<LanguageOption>, val speed: SpeedRange?, val maxCharacters: Int, val format: String,
    val model: String = "", val price: String = "",
) {
    fun voice(id: String): VoiceOption = voices.firstOrNull { it.id == id } ?: voices.firstOrNull { it.id == defaultVoice } ?: voices.first()
    /** Exact match, then the same base language (hi-IN → hi), then the first entry (normally Auto). */
    fun language(id: String): LanguageOption = languages.firstOrNull { it.id.equals(id, true) }
        ?: languages.firstOrNull { it.id != "auto" && it.id.substringBefore('-').equals(id.substringBefore('-'), true) }
        ?: languages.firstOrNull { it.id == "auto" } ?: languages.first()
}

/** Phase 4 voice capabilities. Null on backends from before Phase 4, which hides the new options. */
data class VoiceConfig(
    val ttsEnabled: Boolean = false, val ttsReason: String = "", val providers: List<TtsProvider> = emptyList(), val defaultProvider: String? = null,
    /** Live voice (xAI realtime); filled by Phase 4B backends. */
    val live: LiveVoiceConfig? = null,
) {
    fun provider(id: String?): TtsProvider? = providers.firstOrNull { it.id == id }
}

/** Live (realtime) voice capabilities; [enabled] false keeps the turn-based hands-free mode. */
data class LiveVoiceConfig(
    val enabled: Boolean = false, val reason: String = "", val model: String = "", val voices: List<VoiceOption> = emptyList(),
    val defaultVoice: String = "eve", val languages: List<LanguageOption> = emptyList(), val dailyMinutes: Int = 0,
    val maxSessionMinutes: Int = 0, val pricePerMinute: String = "",
)

/** Reply voice choice resolved from the saved preferences and the server catalog. */
data class TtsOptions(val provider: String, val voice: String, val language: String, val speed: Float) {
    fun cacheKey(text: String): String = listOf(provider, voice, language, "%.2f".format(Locale.ROOT, speed), text).joinToString("\u0000")
}

object VoiceCodec {
    val PROVIDERS = listOf("xai", "gemini", "elevenlabs")
    private val ID = Regex("[A-Za-z0-9][A-Za-z0-9._#-]{0,119}")
    private val LANGUAGE = Regex("auto|[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,2}")
    fun validId(value: String) = value.isEmpty() || ID.matches(value)
    fun validLanguage(value: String) = LANGUAGE.matches(value)

    fun config(json: JSONObject?): VoiceConfig? {
        if (json == null || json.optInt("version", 0) < 1) return null
        val tts = json.optJSONObject("tts")
        val providers = tts?.optJSONArray("providers")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::provider) } }.orEmpty()
            .distinctBy { it.id }
        val enabled = tts?.opt("enabled") == true && providers.isNotEmpty()
        return VoiceConfig(ttsEnabled = enabled, ttsReason = tts?.optString("reason").orEmpty().take(300), providers = if (enabled) providers else emptyList(),
            defaultProvider = tts?.optString("defaultProvider")?.takeIf { id -> providers.any { it.id == id } } ?: providers.firstOrNull()?.id,
            live = live(json.optJSONObject("live")))
    }
    private fun provider(item: JSONObject): TtsProvider? {
        val id = item.optString("id").takeIf { it in PROVIDERS } ?: return null
        val voices = voices(item.optJSONArray("voices"))
        if (voices.isEmpty()) return null
        val languages = languages(item.optJSONArray("languages")).ifEmpty { listOf(LanguageOption("auto", "Auto")) }
        val speed = item.optJSONObject("speed")?.let { SpeedRange(it.optDouble("min", 0.7).toFloat(), it.optDouble("max", 1.5).toFloat(), it.optDouble("default", 1.0).toFloat()) }
            ?.takeIf { it.min > 0f && it.min < it.max && it.max <= 4f }
        return TtsProvider(id, item.optString("name", id).take(60), voices, item.optString("defaultVoice").takeIf { d -> voices.any { it.id == d } } ?: voices.first().id,
            languages, speed, item.optInt("maxCharacters", 1000).coerceIn(100, 5000), item.optString("format", "audio/mpeg").take(40),
            item.optString("model").take(120), item.optString("price").take(120))
    }
    private fun live(item: JSONObject?): LiveVoiceConfig? {
        item ?: return null
        val voices = voices(item.optJSONArray("voices"))
        return LiveVoiceConfig(enabled = item.opt("enabled") == true && voices.isNotEmpty(), reason = item.optString("reason").take(300), model = item.optString("model").take(120),
            voices = voices, defaultVoice = item.optString("defaultVoice").takeIf { d -> voices.any { it.id == d } } ?: voices.firstOrNull()?.id.orEmpty(),
            languages = languages(item.optJSONArray("languages")), dailyMinutes = item.optInt("dailyMinutes").coerceIn(0, 1440),
            maxSessionMinutes = item.optInt("maxSessionMinutes").coerceIn(0, 120), pricePerMinute = item.optString("pricePerMinute").take(40))
    }
    private fun voices(array: JSONArray?): List<VoiceOption> = array?.let { (0 until it.length()).mapNotNull { index -> it.optJSONObject(index) } }.orEmpty()
        .mapNotNull { v -> v.optString("id").takeIf { ID.matches(it) }?.let { VoiceOption(it, v.optString("name", it).take(60), v.optString("description").take(160)) } }
        .distinctBy { it.id }.take(200)
    private fun languages(array: JSONArray?): List<LanguageOption> = array?.let { (0 until it.length()).mapNotNull { index -> it.optJSONObject(index) } }.orEmpty()
        .mapNotNull { l -> l.optString("id").takeIf { LANGUAGE.matches(it) }?.let { LanguageOption(it, l.optString("name", it).take(60), l.optString("note").take(160)) } }
        .distinctBy { it.id }.take(200)

    /**
     * Resolves the saved choice against the server catalog: an unknown provider, voice or language falls back to the
     * server default instead of failing, and speed is clamped to what the provider accepts. Null = device voice.
     */
    fun ttsOptions(config: VoiceConfig?, provider: String, voice: String, language: String, speed: Float): TtsOptions? {
        val catalog = config?.takeIf { it.ttsEnabled } ?: return null
        val selected = catalog.provider(provider) ?: catalog.provider(catalog.defaultProvider) ?: return null
        val range = selected.speed
        return TtsOptions(selected.id, selected.voice(voice).id, selected.language(language).id,
            if (range == null) 1f else speed.coerceIn(range.min, range.max))
    }
    fun requestBody(text: String, options: TtsOptions?): JSONObject = JSONObject().put("text", text).apply {
        if (options != null) put("provider", options.provider).put("voice", options.voice).put("language", options.language)
            .put("speed", Math.round(options.speed * 100) / 100.0)
    }
}

/** Device speech languages offered for recognition and for the device reply voice (availability is checked at runtime). */
object SpeechLanguages {
    val INPUT = listOf(
        LanguageOption("auto", "Auto (device language)"), LanguageOption("en-IN", "English (India)"), LanguageOption("en-US", "English (US)"),
        LanguageOption("en-GB", "English (UK)"), LanguageOption("hi-IN", "Hindi (India)"), LanguageOption("ur-PK", "Urdu (Pakistan)"),
        LanguageOption("ur-IN", "Urdu (India)"), LanguageOption("pa-IN", "Punjabi (India)"), LanguageOption("bn-IN", "Bengali (India)"),
        LanguageOption("ar-SA", "Arabic (Saudi Arabia)"), LanguageOption("ta-IN", "Tamil (India)"), LanguageOption("te-IN", "Telugu (India)"),
        LanguageOption("mr-IN", "Marathi (India)"), LanguageOption("gu-IN", "Gujarati (India)"), LanguageOption("fr-FR", "French"),
        LanguageOption("de-DE", "German"), LanguageOption("es-ES", "Spanish (Spain)"), LanguageOption("tr-TR", "Turkish"),
    )
    /** Device reply languages use the same list. */
    val DEVICE_REPLY = INPUT
    fun name(id: String): String = INPUT.firstOrNull { it.id == id }?.name ?: id
    /** BCP-47 tag for the recognizer/engine; "auto" means the device language. */
    fun locale(id: String, fallback: Locale = Locale.getDefault()): Locale = if (id == "auto" || id.isBlank()) fallback else Locale.forLanguageTag(id)
}
