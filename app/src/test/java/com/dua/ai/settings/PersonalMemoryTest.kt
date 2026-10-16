package com.dua.ai.settings

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PersonalMemoryTest {
    @Test fun addEditDeleteAndDisablePersistWithoutChangingOtherPreferences() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("dua_preferences", 0).edit().clear().commit()
        val store = DevicePreferences(app)
        val baseline = LocalPreferences(font = "serif", notifications = true, memoryEnabled = true)
        store.update(baseline.copy(memory = PersonalMemory.save(emptyList(), null, "", "  Prefer Hinglish  ")))
        assertEquals(listOf("Prefer Hinglish"), DevicePreferences(app).state.value.memory)
        store.update(store.state.value.copy(memory = PersonalMemory.save(store.state.value.memory, 0, "Prefer Hinglish", "Use short replies")))
        store.update(store.state.value.copy(memoryEnabled = false))
        assertEquals(baseline.copy(memoryEnabled = false, memory = listOf("Use short replies")), DevicePreferences(app).state.value)
        assertEquals("", PreferencesCodec.memoryText(DevicePreferences(app).state.value))
        store.update(store.state.value.copy(memory = PersonalMemory.delete(store.state.value.memory, 0, "Use short replies")))
        assertEquals(baseline.copy(memoryEnabled = false), DevicePreferences(app).state.value)
    }

    @Test fun staleEditsAndDeletesCannotTouchAReplacementNote() {
        assertThrows(IllegalArgumentException::class.java) { PersonalMemory.save(listOf("Synced replacement"), 0, "Old note", "Edited") }
        assertThrows(IllegalArgumentException::class.java) { PersonalMemory.delete(listOf("Synced replacement"), 0, "Old note") }
        assertThrows(IllegalArgumentException::class.java) { PersonalMemory.delete(emptyList(), 0, "Old note") }
    }

    @Test fun limitsRejectBlankOversizeAndEleventhNotesButAllowEditingAtCapacity() {
        val full = (1..10).map { "Note $it" }
        for (bad in listOf(" ", "x".repeat(201))) assertThrows(IllegalArgumentException::class.java) { PersonalMemory.save(full, 0, full[0], bad) }
        assertThrows(IllegalArgumentException::class.java) { PersonalMemory.save(full, null, "", "Eleventh") }
        assertEquals(10, PersonalMemory.save(full, 0, full[0], "Updated").size)
    }
}
