package com.dua.ai.setup

import org.json.JSONObject

/**
 * Remote kill switches from `/api/config` › `switches` (dua-api Cloud Run env vars). Everything defaults
 * to off, and a missing or unreadable block (older backend, offline, timeout) means "off" so the app
 * keeps its normal behaviour (fail open).
 */
data class RemoteSwitches(
    val agent: Boolean = false,
    val imagine: Boolean = false,
    val voice: Boolean = false,
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
) {
    val message: String get() = maintenanceMessage.ifBlank { DEFAULT_MAINTENANCE_MESSAGE }

    fun toJson(): String = JSONObject().put("agent", agent).put("imagine", imagine).put("voice", voice)
        .put("maintenance", JSONObject().put("enabled", maintenance).put("message", maintenanceMessage)).toString()

    companion object {
        const val DEFAULT_MAINTENANCE_MESSAGE = "Dua is taking a short break for maintenance. Please check back soon."
        const val AGENT_PAUSED = "Agent mode is paused right now. Normal chat still works; try Agent again later."
        const val IMAGINE_PAUSED = "Imagine is paused right now. Please try again later."
        const val VOICE_PAUSED = "AI voices and live voice are paused right now. Device voice still works."
        /** A cached value older than this is ignored at start (the fresh fetch decides). */
        const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L

        /** Reads the `switches` block of a full /api/config response. */
        fun fromConfig(config: JSONObject?): RemoteSwitches = parse(config?.optJSONObject("switches"))

        fun parse(block: JSONObject?): RemoteSwitches {
            if (block == null) return RemoteSwitches()
            val maintenance = block.optJSONObject("maintenance")
            val enabled = maintenance?.optBoolean("enabled", false) == true
            val message = maintenance?.optString("message", "").orEmpty()
                .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").trim().take(300)
            return RemoteSwitches(block.optBoolean("agent", false), block.optBoolean("imagine", false), block.optBoolean("voice", false),
                enabled, if (enabled) message else "")
        }

        fun fromCache(raw: String?, savedAt: Long, now: Long): RemoteSwitches =
            if (raw.isNullOrBlank() || now - savedAt !in 0..CACHE_MAX_AGE_MS) RemoteSwitches() else runCatching { parse(JSONObject(raw)) }.getOrDefault(RemoteSwitches())
    }
}
