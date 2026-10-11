package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.projects.ProjectTask
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
class ProjectTasksInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun projectPickerOpensOnlyThatProjectsTasksAndAddsThenCompletesATask() {
        val projects = mutableStateOf(ProjectCodec.defaults)
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectsContent(projects.value, "personal", "", true, {}, { _, _, _, _ -> true },
            tasksEnabled = true, onSaveTask = { projectId, _, title ->
                projects.value = projects.value.map { if (it.id == projectId) it.copy(tasks = it.tasks + ProjectTask("new-task", title)) else it }; true
            }, onToggleTask = { projectId, taskId ->
                projects.value = projects.value.map { if (it.id == projectId) it.copy(tasks = it.tasks.map { t -> if (t.id == taskId) t.copy(completed = !t.completed) else t }) else it }; true
            }) } }
        compose.onAllNodesWithText("Tasks (0)")[1].performClick()
        compose.onNodeWithText("Coding · Tasks").assertExists()
        compose.onNodeWithText("Add task").performClick()
        compose.onNodeWithText("Task title").performTextInput("Review diff")
        compose.onNodeWithText("Save task").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Complete task: Review diff").performClick()
        compose.runOnIdle {
            assertTrue(projects.value[1].tasks.single().completed)
            assertTrue(projects.value[0].tasks.isEmpty())
        }
        compose.onNodeWithText("0 open · 1 completed. Sync changes to save them on the server.").assertExists()
    }
    @Test fun failedEditKeepsTypedTitleAndDeleteRequiresConfirmation() {
        val project = mutableStateOf(ProjectCodec.defaults[1].copy(tasks = listOf(ProjectTask("task-1", "Review diff"))))
        var deletes = 0
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectTasksContent(project.value, "", true, "", { _, _, _ -> false }, { _, _ -> true },
            { _, id -> deletes++; project.value = project.value.copy(tasks = project.value.tasks.filterNot { it.id == id }); true }, {}) } }
        compose.onNodeWithContentDescription("Edit task: Review diff").performClick()
        compose.onNodeWithText("Task title").performTextReplacement("Keep unsaved edit")
        compose.onNodeWithText("Save task").performClick()
        compose.onNodeWithText("Keep unsaved edit").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Delete task: Review diff").performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Delete task: Review diff").performClick()
        compose.onNodeWithText("Delete task").performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
        compose.onNodeWithText("No tasks yet.").assertExists()
    }
    @Test fun unavailableTasksRemainReadableButCannotBeEdited() {
        val project = ProjectCodec.defaults[1].copy(tasks = listOf(ProjectTask("task-1", "Saved task")))
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectTasksContent(project, "", false, "Restore this project to edit its tasks.",
            { _, _, _ -> fail("Disabled save"); false }, { _, _ -> fail("Disabled toggle"); false }, { _, _ -> fail("Disabled delete"); false }, {}) } }
        compose.onNodeWithText("Saved task").assertExists()
        compose.onNodeWithText("Add task").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Complete task: Saved task").assertIsNotEnabled()
    }
}
