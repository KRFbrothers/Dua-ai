package com.dua.ai.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

enum class AccentPreset(val label: String, val argb: Long) {
    Purple("Purple", 0xFFAA4FF6), Orange("Orange", 0xFFFF6308), Blue("Blue", 0xFF2979FF),
    Green("Green", 0xFF16A34A), Pink("Pink", 0xFFE84393), Teal("Teal", 0xFF00897B);
    companion object {
        fun fromName(value: String?) = entries.find { it.name == value }
        fun defaultFor(dark: Boolean) = if (dark) Orange else Purple
    }
}

data class AppearancePreferences(val theme: String = "system", val accent: AccentPreset? = null) {
    fun resolvedAccent(dark: Boolean) = accent ?: AccentPreset.defaultFor(dark)
}

/** Copy only the old appearance choice. Never remove the shared JSON containing memory/projects. */
internal class AppearanceMigration(private val legacyJson: () -> String?) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[AppearanceStore.MIGRATED] != true
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        if (this[AppearanceStore.THEME] == null) this[AppearanceStore.THEME] = legacyTheme(legacyJson())
        this[AppearanceStore.MIGRATED] = true
    }
    override suspend fun cleanUp() = Unit
}

internal fun legacyTheme(raw: String?): String = runCatching { JSONObject(raw ?: "{}").optString("theme") }
    .getOrNull()?.takeIf { it in listOf("system", "light", "dark") } ?: "system"

private val Context.appearanceDataStore by preferencesDataStore(name = "dua_appearance", produceMigrations = { context ->
    listOf(AppearanceMigration { context.getSharedPreferences("dua_preferences", Context.MODE_PRIVATE).getString("preferences", null) })
})

class AppearanceStore internal constructor(private val store: DataStore<Preferences>, val initial: AppearancePreferences = AppearancePreferences()) {
    constructor(context: Context) : this(context.applicationContext.appearanceDataStore,
        AppearancePreferences(legacyTheme(context.getSharedPreferences("dua_preferences", Context.MODE_PRIVATE).getString("preferences", null))))
    private val mutableNotice = MutableStateFlow("")
    val notice = mutableNotice.asStateFlow()
    val data = store.data.retryWhen { failure, _ ->
        if (failure !is IOException) return@retryWhen false
        mutableNotice.value = "Appearance settings could not be read. The previous appearance is shown; retry after checking device storage."
        // Keep the last visible StateFlow value and recover when device storage becomes readable.
        delay(2_000)
        true
    }.map { decode(it) }.onEach { mutableNotice.value = "" }
    suspend fun setTheme(value: String) {
        require(value in listOf("system", "light", "dark"))
        store.edit { it[THEME] = value }
        mutableNotice.value = ""
    }
    suspend fun setAccent(value: AccentPreset?) {
        store.edit { if (value == null) it.remove(ACCENT) else it[ACCENT] = value.name }
        mutableNotice.value = ""
    }
    suspend fun reset() {
        store.edit { it[THEME] = "system"; it.remove(ACCENT) }
        mutableNotice.value = ""
    }
    companion object {
        internal val THEME = stringPreferencesKey("appearance")
        internal val ACCENT = stringPreferencesKey("accent_override")
        internal val MIGRATED = booleanPreferencesKey("legacy_appearance_copied")
        internal fun decode(p: Preferences) = AppearancePreferences(p[THEME]?.takeIf { it in listOf("system", "light", "dark") } ?: "system", AccentPreset.fromName(p[ACCENT]))
    }
}
