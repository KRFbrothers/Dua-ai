package com.dua.ai.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearancePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun appearanceAndAccentPersistIndependentlyAndResetRestoresThemeSpecificDefaults() = runBlocking {
        val file = temporary.root.resolve("appearance.preferences_pb")
        var job = SupervisorJob()
        fun store() = AppearanceStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }))
        var prefs = store()
        assertEquals(AppearancePreferences(), prefs.data.first())
        prefs.setAccent(AccentPreset.Teal); prefs.setTheme("light")
        assertEquals(AppearancePreferences("light", AccentPreset.Teal), prefs.data.first())
        prefs.setTheme("dark")
        assertEquals(AccentPreset.Teal, prefs.data.first().accent)
        job.cancelAndJoin(); job = SupervisorJob(); prefs = store()
        try {
            assertEquals(AppearancePreferences("dark", AccentPreset.Teal), prefs.data.first())
            prefs.setAccent(null)
            val reset = prefs.data.first()
            assertEquals("dark", reset.theme)
            assertNull(reset.accent)
            assertEquals(AccentPreset.Purple, reset.resolvedAccent(false))
            assertEquals(AccentPreset.Orange, reset.resolvedAccent(true))
            prefs.setAccent(AccentPreset.Pink); prefs.reset()
            assertEquals(AppearancePreferences(), prefs.data.first())
        } finally { job.cancelAndJoin() }
    }
    @Test fun legacyMigrationCopiesThemeOnceWithoutDeletingMemoryOrProjectJson() = runBlocking {
        val legacy = """{"theme":"light","memory":["Keep private note"],"projects":[{"id":"coding"}]}"""
        val migration = AppearanceMigration { legacy }
        val copied = migration.migrate(emptyPreferences())
        assertEquals("light", AppearanceStore.decode(copied).theme)
        assertFalse(migration.shouldMigrate(copied))
        migration.cleanUp()
        assertTrue(legacy.contains("Keep private note"))
        val existing = preferencesOf(AppearanceStore.THEME to "dark", AppearanceStore.ACCENT to "Blue")
        assertEquals(AppearancePreferences("dark", AccentPreset.Blue), AppearanceStore.decode(migration.migrate(existing)))
    }
    @Test fun absentOrInvalidLegacyChoicesUseSystemAndUnknownAccentUsesThemeDefault() {
        for (raw in listOf(null, "not json", "{}", "{\"theme\":\"invalid\"}")) assertEquals("system", legacyTheme(raw))
        val invalid = preferencesOf(AppearanceStore.THEME to "obsolete", AppearanceStore.ACCENT to "obsolete")
        assertEquals(AppearancePreferences(), AppearanceStore.decode(invalid))
    }
    @Test fun personalitySurvivesRestartAndAppearanceChangesAndResetClearsIt() = runBlocking {
        val file = temporary.root.resolve("personality.preferences_pb")
        var job = SupervisorJob()
        fun store() = AppearanceStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }))
        var prefs = store()
        try {
            assertNull(prefs.data.first().personality)
            DuaPersonality.entries.forEach { preset ->
                prefs.setPersonality(preset)
                assertEquals(preset, prefs.data.first().personality)
            }
            prefs.setTheme("dark"); prefs.setAccent(AccentPreset.Blue)
            job.cancelAndJoin(); job = SupervisorJob(); prefs = store()
            assertEquals(AppearancePreferences("dark", AccentPreset.Blue, DuaPersonality.CreativeBrainstormer), prefs.data.first())
            prefs.setAccent(null); prefs.setTheme("light")
            assertEquals(DuaPersonality.CreativeBrainstormer, prefs.data.first().personality)
            prefs.reset()
            assertEquals(AppearancePreferences(), prefs.data.first())
        } finally { job.cancelAndJoin() }
    }
    @Test fun unknownPersonalityDoesNotSelectAnUnrelatedPresetAndMigrationKeepsAValidChoice() = runBlocking {
        assertNull(AppearanceStore.decode(preferencesOf(AppearanceStore.PERSONALITY to "retired")).personality)
        val existing = preferencesOf(AppearanceStore.PERSONALITY to DuaPersonality.SeniorArchitect.id)
        assertEquals(DuaPersonality.SeniorArchitect, AppearanceStore.decode(AppearanceMigration { "{}" }.migrate(existing)).personality)
        assertEquals(4, DuaPersonality.entries.size)
        assertEquals(4, DuaPersonality.entries.map { it.id }.toSet().size)
        DuaPersonality.entries.forEach { assertTrue(it.instruction.isNotBlank() && it.instruction.length <= 1000) }
    }
}
