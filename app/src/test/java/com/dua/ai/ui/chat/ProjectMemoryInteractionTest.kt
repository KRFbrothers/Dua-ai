package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.projects.ProjectMemory
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ProjectMemoryInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pickerAddsToCodingOnlyAndToggleOffRetainsNotes() {
        val projects = mutableStateOf(com.dua.ai.projects.ProjectFixtures.owned)
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectsContent(projects.value, "personal", "", true, {}, { _, _, _, _ -> true },
            memoryEnabled = true, onSaveMemory = { id, enabled, notes ->
                projects.value = projects.value.map { if (it.id == id) it.copy(memory = ProjectMemory(enabled, notes)) else it }; true
            }) } }
        compose.onNodeWithText("Memory · Coding").performScrollTo().performClick()
        compose.onNodeWithText("Coding memory").assertExists()
        compose.onNodeWithText("Add note").performClick()
        compose.onNodeWithText("Memory note").performTextInput("Use Kotlin and Compose")
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Use memory in Coding").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Use memory in Coding").assertIsOn()
        compose.onNodeWithContentDescription("Use memory in Coding").performClick()
        compose.runOnIdle {
            assertEquals(ProjectMemory(false, listOf("Use Kotlin and Compose")), projects.value[1].memory)
            assertEquals(ProjectMemory(), projects.value[0].memory); assertEquals(ProjectMemory(), projects.value[2].memory)
        }
        compose.onNodeWithText("Back to projects").performScrollTo().performClick()
        compose.onNodeWithText("Memory · Business").performScrollTo().performClick()
        compose.onNodeWithText("No notes saved. Add preferences or facts you want this project to remember.").assertExists()
    }
    @Test fun changedNoteDuringEditBlocksSaveAndFailurePreservesTypedText() {
        val project = mutableStateOf(com.dua.ai.projects.ProjectFixtures.owned[1].copy(memory = ProjectMemory(true, listOf("Old note"))))
        var saves = 0
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectMemoryContent(project.value, "", true, "", { _, _, _ -> saves++; false }, {}) } }
        compose.onNodeWithText("Edit note 1").performClick()
        compose.onNodeWithText("Memory note").performTextReplacement("Typed edit")
        compose.runOnIdle { project.value = project.value.copy(memory = ProjectMemory(true, listOf("Server edit"))) }
        compose.onNodeWithText("Save note").assertIsNotEnabled()
        compose.onNodeWithText("Memory note").assertTextContains("Typed edit")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Edit note 1").performScrollTo().performClick()
        compose.onNodeWithText("Memory note").performTextReplacement("Another edit")
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithText("Memory note").assertTextContains("Another edit")
    }
    @Test fun deletionNeedsConfirmationAndDoesNotDeleteAChangedRow() {
        val project = mutableStateOf(com.dua.ai.projects.ProjectFixtures.owned[2].copy(memory = ProjectMemory(false, listOf("Keep me"))))
        var saves = 0
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectMemoryContent(project.value, "", true, "", { _, enabled, notes ->
            saves++; project.value = project.value.copy(memory = ProjectMemory(enabled, notes)); true
        }, {}) } }
        compose.onNodeWithText("Delete note 1").performClick()
        compose.runOnIdle { assertEquals(0, saves); project.value = project.value.copy(memory = ProjectMemory(false, listOf("New server note"))) }
        compose.onNodeWithText("Delete note").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete note 1").performClick()
        compose.onNodeWithText("Delete note").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertTrue(project.value.memory.notes.isEmpty()) }
    }
    @Test fun archivedOrUnavailableMemoryShowsNotesWithoutAllowingChanges() {
        val enabled = mutableStateOf(false)
        var saves = 0
        val project = com.dua.ai.projects.ProjectFixtures.owned[1].copy(archived = true, memory = ProjectMemory(true, listOf("Read only")))
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectMemoryContent(project, "", enabled.value, "Restore this project to edit its memory.", { _, _, _ -> saves++; true }, {}) } }
        compose.onNodeWithText("Read only").assertExists()
        compose.onNodeWithText("Edit note 1").assertIsNotEnabled()
        compose.onNodeWithText("Delete note 1").assertIsNotEnabled()
        compose.onNodeWithText("Add note").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Use memory in Coding").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saves) }
    }
}
