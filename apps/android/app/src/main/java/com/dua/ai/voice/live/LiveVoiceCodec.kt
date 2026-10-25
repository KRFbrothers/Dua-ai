package com.dua.ai.voice.live

import com.dua.ai.model.ChatMessage
import com.dua.ai.voice.LiveVoiceConfig
import com.dua.ai.voice.SpeakableText
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** What `/api/voice/session` returns: a short-lived xAI client secret and the session to configure. */
class LiveGrant(val sessionId: String, val token: String, val expiresAt: Long, val url: String, val model: String,
    val session: JSONObject, val maxSeconds: Int, val usage: LiveUsage) {
    override fun toString() = "LiveGrant(sessionId=$sessionId, model=$model, maxSeconds=$maxSeconds)"
}
data class LiveUsage(val usedSeconds: Int, val activeSeconds: Int, val limitSeconds: Int, val resetAt: Long) {
    val leftSeconds: Int get() = (limitSeconds - usedSeconds - activeSeconds).coerceAtLeast(0)
}
/** Voice, language and speed for a live session, resolved against the server's live catalog. */
data class LiveOptions(val voice: String, val language: String, val speed: Float)

object LiveVoiceCodec {
    private val SESSION_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val TOKEN = Regex("[\\x21-\\x7e]{8,4096}")
    const val CONTEXT_MESSAGES = 12
    const val CONTEXT_CHARS = 6000

    fun grant(json: JSONObject): LiveGrant {
        val sessionId = json.optString("sessionId").lowercase()
        require(SESSION_ID.matches(sessionId)) { "Invalid live voice session." }
        val token = json.optString("token")
        require(TOKEN.matches(token)) { "Invalid live voice token." }
        val url = json.optString("url")
        val uri = runCatching { URI(url) }.getOrNull()
        // The token only ever goes to xAI's realtime endpoint.
        require(uri != null && uri.scheme == "wss" && uri.host == "api.x.ai" && uri.path == "/v1/realtime" && uri.userInfo == null) { "Invalid live voice address." }
        val session = json.optJSONObject("session") ?: throw IllegalArgumentException("Invalid live voice session.")
        val maxSeconds = json.optInt("maxSeconds")
        require(maxSeconds in 30..7200) { "Invalid live voice session." }
        return LiveGrant(sessionId, token, json.optLong("expiresAt"), url, json.optString("model").take(120), session, maxSeconds, usage(json.optJSONObject("usage")))
    }
    fun usage(json: JSONObject?): LiveUsage = LiveUsage(json?.optInt("usedSeconds")?.coerceAtLeast(0) ?: 0, json?.optInt("activeSeconds")?.coerceAtLeast(0) ?: 0,
        json?.optInt("limitSeconds")?.coerceAtLeast(0) ?: 0, json?.optLong("resetAt") ?: 0)

    fun options(config: LiveVoiceConfig, voice: String, language: String, speed: Float): LiveOptions {
        val chosen = config.voices.firstOrNull { it.id == voice }?.id ?: config.defaultVoice
        val languages = config.languages
        val lang = languages.firstOrNull { it.id.equals(language, true) }?.id
            ?: languages.firstOrNull { it.id != "auto" && it.id.substringBefore('-').equals(language.substringBefore('-'), true) }?.id ?: "auto"
        return LiveOptions(chosen, lang, speed.coerceIn(0.7f, 1.5f))
    }

    /**
     * Body for `/api/voice/session`: the chosen voice, memory notes (only when Memory is on) and the newest
     * completed turns of the current chat as plain text, so Dua can continue the conversation by voice.
     */
    fun sessionRequest(options: LiveOptions, memory: String, messages: List<ChatMessage>): JSONObject {
        val turns = mutableListOf<JSONObject>()
        var used = 0
        for (message in messages.asReversed()) {
            if (turns.size >= CONTEXT_MESSAGES) break
            if (message.state != "complete" || message.isImagine) continue
            val text = SpeakableText.clean(message.text).take(2000).trim()
            if (text.isEmpty()) continue
            if (used + text.length > CONTEXT_CHARS) break
            used += text.length
            turns.add(0, JSONObject().put("role", if (message.isUser) "user" else "assistant").put("text", text))
        }
        return JSONObject().put("voice", options.voice).put("language", options.language).put("speed", Math.round(options.speed * 100) / 100.0)
            .put("memory", memory.take(3000)).put("context", JSONArray(turns))
    }

    /** Chat messages for a finished live session (empty turns dropped, interrupted replies marked). */
    fun messages(captions: List<LiveCaption>, model: String = "Grok voice"): List<ChatMessage> = captions.filter { it.text.isNotBlank() }.map { caption ->
        ChatMessage(text = caption.text.take(20_000) + if (!caption.user && caption.interrupted) " …" else "", isUser = caption.user, model = model,
            state = if (!caption.user && caption.interrupted) "stopped" else "complete")
    }
}
