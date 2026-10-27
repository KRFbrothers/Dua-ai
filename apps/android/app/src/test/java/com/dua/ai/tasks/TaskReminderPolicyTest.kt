package com.dua.ai.tasks

import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectTask
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskReminderPolicyTest {
    private val at = 2000L
    private val task = ProjectTask("task-1", "Private title", dueAt = at)
    private fun projects(tasks: List<ProjectTask> = listOf(task), archived: Boolean = false) = listOf(ChatProject("personal", "Personal", archived = archived, tasks = tasks))
    @Test fun dueTasksGroupAndOnlyOneEarliestAlarmIsNeeded() {
        val values = projects(listOf(task, task.copy(id = "second", dueAt = 5000), task.copy(id = "undated", dueAt = null)))
        val upcoming = TaskReminderPolicy.plan(values, emptySet(), 1000, true)
        assertEquals(2000L, upcoming.nextAt)
        val due = TaskReminderPolicy.plan(values, emptySet(), 3000, true)
        assertEquals(setOf(TaskReminderPolicy.key("personal", task.id, at)), due.due)
        assertEquals(4000L, due.nextAt)
        assertEquals(5000L, TaskReminderPolicy.plan(values, due.due, 3000, true).nextAt)
        assertFalse(upcoming.toString().contains(task.title))
    }
    @Test fun completedArchivedDeletedAndDisabledTasksCannotTriggerReminders() {
        for (values in listOf(projects(listOf(task.copy(completed = true))), projects(archived = true), projects(emptyList()))) {
            val plan = TaskReminderPolicy.plan(values, emptySet(), 3000, true)
            assertNull(plan.nextAt); assertTrue(plan.due.isEmpty())
        }
        assertNull(TaskReminderPolicy.plan(projects(), emptySet(), 3000, false).nextAt)
    }
    @Test fun deliveryKeysSurviveCompletionAndArchiveAndChangeOnlyForNewDeadlinesOrProjects() {
        val delivered = setOf(TaskReminderPolicy.key("personal", task.id, at))
        assertEquals(delivered, TaskReminderPolicy.plan(projects(listOf(task.copy(completed = true))), delivered, 3000, true).known)
        assertEquals(delivered, TaskReminderPolicy.plan(projects(archived = true), delivered, 3000, true).known)
        assertTrue(TaskReminderPolicy.plan(projects(), delivered, 3000, true).due.isEmpty())
        assertEquals(1, TaskReminderPolicy.plan(projects(listOf(task.copy(dueAt = 2500))), delivered, 3000, true).due.size)
        assertNotEquals(delivered.first(), TaskReminderPolicy.key("coding", task.id, at))
    }
    @Test fun calendarValidationHandlesLeapDaysAndClockChangesWithoutSilentlyMovingTimes() {
        assertNull(TaskDates.parse("", ""))
        assertEquals(Instant.parse("2028-02-29T10:15:00Z").toEpochMilli(), TaskDates.parse("2028-02-29", "10:15", ZoneId.of("UTC")))
        for ((date, time) in listOf("2027-02-29" to "10:15", "2028-01-01" to "24:00", "2028-01-01" to "", "" to "10:15", "2028-1-01" to "10:15")) {
            assertTrue(runCatching { TaskDates.parse(date, time, ZoneId.of("UTC")) }.isFailure)
        }
        assertTrue(runCatching { TaskDates.parse("2026-03-29", "02:30", ZoneId.of("Europe/Berlin")) }.isFailure)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), TaskDates.parse("2026-10-25", "02:30", ZoneId.of("Europe/Berlin")))
        assertEquals("2028-02-29 15:45", TaskDates.display(Instant.parse("2028-02-29T10:15:00Z").toEpochMilli(), ZoneId.of("Asia/Kolkata")))
    }
}
