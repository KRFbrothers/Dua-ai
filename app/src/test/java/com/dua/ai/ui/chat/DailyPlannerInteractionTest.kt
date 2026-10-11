package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class DailyPlannerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2030-01-02T12:00:00Z").toEpochMilli()
    private fun scrollTo(matcher: SemanticsMatcher) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)

    @Test fun browsedDayAndProjectFilterSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { DuaTheme(LocalPreferences()) { DailyPlannerContent(ProjectCodec.defaults, "", true, true, now, utc, { _, _ -> true }, {}, {}) } }
        compose.onNodeWithContentDescription("Next planner day").performClick()
        compose.onNodeWithContentDescription("Planner project filter").performClick()
        compose.onNodeWithContentDescription("Filter planner to Business").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tomorrow · 2030-01-03 · UTC").assertExists()
        compose.onNodeWithContentDescription("Planner project filter").assertTextContains("Business")
        compose.onNodeWithText("Manage Business tasks").assertExists()
    }

    @Test fun completionTargetsTheCorrectProjectWhenTaskIdsMatchAndKeepsTheDeadline() {
        val due = now + 60_000
        val projects = mutableStateOf(ProjectCodec.defaults.map { if (it.id == "personal") it else it.copy(tasks = listOf(ProjectTask("same", "${it.name} work", dueAt = due))) })
        compose.setContent { DuaTheme(LocalPreferences()) { DailyPlannerContent(projects.value, "", true, true, now, utc,
            { projectId, taskId -> projects.value = projects.value.map { if (it.id == projectId) it.copy(tasks = it.tasks.map { task -> if (task.id == taskId) task.copy(completed = !task.completed) else task }) else it }; true }, {}, {}) } }
        val complete = hasContentDescription("Complete planner task: Coding · Coding work")
        scrollTo(complete)
        compose.onNode(complete).performClick()
        compose.runOnIdle {
            assertTrue(projects.value[1].tasks.single().completed)
            assertFalse(projects.value[2].tasks.single().completed)
            assertEquals(due, projects.value[1].tasks.single().dueAt)
        }
        scrollTo(hasContentDescription("Reopen planner task: Coding · Coding work"))
        compose.onNodeWithContentDescription("Reopen planner task: Coding · Coding work").performClick()
        compose.runOnIdle { assertFalse(projects.value[1].tasks.single().completed) }
    }

    @Test fun disabledBackendLeavesCachedTasksReadableAndAFailedToggleKeepsItsState() {
        val enabled = mutableStateOf(false)
        val project = ProjectCodec.defaults[0].copy(tasks = listOf(ProjectTask("one", "Cached task", dueAt = now - 1)))
        var toggles = 0
        compose.setContent { DuaTheme(LocalPreferences()) { DailyPlannerContent(listOf(project), "", true, enabled.value, now, utc,
            { _, _ -> toggles++; false }, {}, {}) } }
        val toggle = hasContentDescription("Complete planner task: Personal · Cached task")
        scrollTo(toggle)
        compose.onNode(toggle).assertIsNotEnabled()
        compose.runOnIdle { enabled.value = true }
        compose.onNode(toggle).performClick().assertIsOff()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithText("Overdue: 2030-01-02 11:59").assertExists()
    }

    @Test fun todayFollowsMidnightAndTimeZoneButABrowsedDateStaysFixed() {
        val clock = mutableStateOf(now)
        val zone = mutableStateOf(utc)
        compose.setContent { DuaTheme(LocalPreferences()) { DailyPlannerContent(ProjectCodec.defaults, "", true, true, clock.value, zone.value, { _, _ -> true }, {}, {}) } }
        compose.onNodeWithText("Today · 2030-01-02 · UTC").assertExists()
        compose.onNodeWithContentDescription("Next planner day").performClick()
        compose.onNodeWithText("Tomorrow · 2030-01-03 · UTC").assertExists()
        compose.runOnIdle { clock.value += 2 * 86_400_000L }
        compose.onNodeWithText("2030-01-03 · 2030-01-03 · UTC").assertExists()
        compose.onNodeWithText("Today", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Today · 2030-01-04 · UTC").assertExists()
        compose.runOnIdle { clock.value = Instant.parse("2030-01-04T22:00:00Z").toEpochMilli(); zone.value = ZoneId.of("Asia/Kolkata") }
        compose.onNodeWithText("Today · 2030-01-05 · Asia/Kolkata").assertExists()
    }

    @Test fun filterResetsAfterArchivingAndOpenProjectTasksReturnsToTheSamePlannerDayAndFilter() {
        val projects = mutableStateOf(ProjectCodec.defaults.map { if (it.id == "coding") it.copy(tasks = listOf(ProjectTask("one", "Code inbox"))) else it })
        compose.setContent { DuaTheme(LocalPreferences()) { ProjectsContent(projects.value, "personal", "", true, {}, { _, _, _, _ -> true }, tasksEnabled = true) } }
        compose.onNodeWithText("Daily planner").performClick()
        compose.onNodeWithContentDescription("Next planner day").performClick()
        compose.onNodeWithContentDescription("Planner project filter").performClick()
        compose.onNodeWithContentDescription("Filter planner to Coding").performClick()
        scrollTo(hasContentDescription("Open tasks: Coding · Code inbox"))
        compose.onNodeWithContentDescription("Open tasks: Coding · Code inbox").performClick()
        compose.onNodeWithText("Coding · Tasks").assertExists()
        compose.onNodeWithText("Back to planner").performScrollTo().performClick()
        // SaveableStateHolder retains both filter and browsed date when the task screen is opened.
        scrollTo(hasContentDescription("Planner project filter"))
        compose.onNodeWithContentDescription("Planner project filter").assertTextContains("Coding")
        compose.onNodeWithText("Tomorrow", substring = true).assertExists()
        compose.runOnIdle { projects.value = projects.value.map { if (it.id == "coding") it.copy(archived = true) else it } }
        compose.onNodeWithText("All active projects").assertExists()
        compose.onNodeWithContentDescription("Open tasks: Coding · Code inbox").assertDoesNotExist()
    }
}
