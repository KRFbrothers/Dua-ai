package com.dua.ai.ui.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.projects.ProjectCodec
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
class ProjectsInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pickerSelectsActiveProjectsAndKeepsArchivedProjectSettingsAccessible() {
        var selected = "personal"
        val projects = ProjectCodec.defaults.map { if (it.id == "business") it.copy(archived = true) else it }
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectsContent(projects, "personal", "", true, { selected = it }, { _, _, _, _ -> true }) } }
        compose.onNodeWithText("Coding").performClick()
        compose.runOnIdle { assertEquals("coding", selected) }
        compose.onNodeWithText("Business (archived)").assertIsNotEnabled()
        compose.onAllNodesWithText("Edit")[2].performClick()
        compose.onNodeWithText("Custom instructions").assertExists()
    }
    @Test fun customProjectEditorSavesNameAndInstructionsAndKeepsFailedEdits() {
        var saved: Pair<String, String>? = null
        var accept = false
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectsContent(ProjectCodec.defaults, "personal", "", true, {},
            { _, name, instructions, _ -> saved = name to instructions; accept }) } }
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").performTextInput("Client work")
        compose.onNodeWithText("Custom instructions").performTextInput("Cite document pages")
        compose.onNodeWithText("Save project").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Client work" to "Cite document pages", saved) }
        compose.onNodeWithText("Project settings").assertExists()
        compose.runOnIdle { accept = true }
        compose.onNodeWithText("Save project").performClick()
        compose.onNodeWithText("New project").assertExists()
    }
}
