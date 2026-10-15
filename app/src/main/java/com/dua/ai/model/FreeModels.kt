package com.dua.ai.model

import org.json.JSONObject

/** A free OpenRouter model the Cloud Run engine offers (GET / `freeModels`). Ids always end in ":free". */
data class FreeModel(val id: String, val name: String)

object FreeModels {
    private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}/[a-z0-9][a-z0-9._-]{0,95}:free")
    const val MAX = 8

    fun validId(id: String) = ID.matches(id)

    /** Valid entries of the engine's `freeModels` list (unknown shape or bad entries are dropped). */
    fun parse(json: JSONObject): List<FreeModel> {
        val array = json.optJSONArray("freeModels") ?: return emptyList()
        return (0 until minOf(array.length(), MAX)).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
            val id = item.optString("id"); val name = item.optString("name").trim().take(40)
            if (validId(id) && name.isNotEmpty()) FreeModel(id, name) else null
        }.distinctBy { it.id }
    }
}
