package com.dua.ai.account

import org.json.JSONArray

/** Server-confirmed IDs, without message content; retained to suppress stale cache uploads. */
object CloudDeletionIds {
    const val MAX_IDS = 10_000
    fun validate(ids: List<String>) {
        require(ids.size <= MAX_IDS && ids.distinct().size == ids.size && ids.all { it.isNotBlank() && it.length <= 200 }) {
            "Invalid cloud deletion metadata."
        }
    }
    fun decode(json: JSONArray?): List<String> {
        if (json == null) return emptyList()
        require(json.length() <= MAX_IDS) { "Invalid cloud deletion metadata." }
        return (0 until json.length()).map { json.get(it) as? String ?: error("Invalid cloud deletion metadata.") }
            .also(::validate).sorted()
    }
}
