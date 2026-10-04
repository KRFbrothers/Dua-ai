package com.dua.ai.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray

data class LocalPreferences(val theme: String = "dark", val font: String = "default", val haptic: Boolean = false,
    val notifications: Boolean = false, val memoryEnabled: Boolean = false, val memory: List<String> = emptyList())

object PreferencesCodec {
    fun validate(value: LocalPreferences): LocalPreferences {
        require(value.theme in listOf("dark", "light", "system") && value.font in listOf("default", "system", "serif", "monospace"))
        require(value.memory.size <= 10 && value.memory.all { it.isNotBlank() && it.length <= 200 }) { "Save at most 10 memories, each up to 200 characters." }
        return value
    }
    fun encode(value: LocalPreferences): String {
        validate(value)
        return JSONObject().put("theme", value.theme).put("font", value.font).put("haptic", value.haptic)
            .put("notifications", value.notifications).put("memoryEnabled", value.memoryEnabled)
            .put("memory", JSONArray(value.memory)).toString()
    }
    fun decode(raw: String): LocalPreferences {
        val data = JSONObject(raw); val notes = data.getJSONArray("memory")
        return validate(LocalPreferences(data.getString("theme"), data.getString("font"), data.getBoolean("haptic"),
            data.getBoolean("notifications"), data.getBoolean("memoryEnabled"), (0 until notes.length()).map { (notes.get(it) as? String ?: error("Invalid memory note")) }))
    }
    fun memoryText(value: LocalPreferences): String = if (value.memoryEnabled) value.memory.joinToString("\n") { "- $it" } else ""
}

class DevicePreferences(context: Context) {
    private val storage = context.getSharedPreferences("dua_preferences", Context.MODE_PRIVATE)
    private var damaged = false
    private val mutable = MutableStateFlow(runCatching { storage.getString("preferences", null)?.let(PreferencesCodec::decode) ?: LocalPreferences() }.getOrElse { damaged = true; LocalPreferences() })
    val state = mutable.asStateFlow()
    fun requireReadable() { check(!damaged) { "Saved settings could not be read. Reset settings in Privacy before syncing." } }
    fun reset() { damaged = false; try { update(LocalPreferences()) } catch (error: Exception) { damaged = true; throw error } }
    fun update(value: LocalPreferences) {
        check(!damaged) { "Saved settings could not be read. Reset settings in Privacy to continue." }
        val encoded = PreferencesCodec.encode(value)
        val previous = storage.getString("preferences", null)
        if (!storage.edit().putString("preferences", encoded).commit()) {
            storage.edit().putString("preferences", previous).apply()
            error("Settings could not be saved. Try again.")
        }
        mutable.value = value
    }
}
