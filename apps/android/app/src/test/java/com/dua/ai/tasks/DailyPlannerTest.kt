package com.dua.ai.tasks

import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectTask
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyPlannerTest {
    private val utc = ZoneId.of("UTC")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private fun task(id: String, time: String?, completed: Boolean = false) = ProjectTask(id, id, completed, time?.let(::at))

    @Test fun calendarBoundariesPartitionTasksWithoutIncludingLaterOrFinishedInboxItems() {
        val project = ChatProject("personal", "Personal", tasks = listOf(
            task("earlier", "2030-01-01T23:59:59.999Z"),
            task("start", "2030-01-02T00:00:00Z"), task("end", "2030-01-02T23:59:59.999Z"),
            task("tomorrow", "2030-01-03T00:00:00Z"), task("finished-earlier", "2030-01-01T12:00:00Z", true),
            task("finished-day", "2030-01-02T12:00:00Z", true), task("inbox", null), task("finished-inbox", null, true)))
        val plan = DailyPlanner.day(listOf(project), LocalDate.parse("2030-01-02"), utc)
        assertEquals(listOf("earlier"), plan.earlier.map { it.task.id })
        assertEquals(listOf("start", "end"), plan.scheduled.map { it.task.id })
        assertEquals(listOf("finished-day"), plan.completed.map { it.task.id })
        assertEquals(listOf("inbox"), plan.undated.map { it.task.id })
        assertEquals(listOf("tomorrow"), DailyPlanner.day(listOf(project), LocalDate.parse("2030-01-03"), utc).scheduled.map { it.task.id })
    }

    @Test fun sameTaskIdInDifferentProjectsHasDistinctKeysAndArchivedProjectsAreHidden() {
        val task = task("shared", "2030-01-02T10:00:00Z")
        val projects = listOf(ChatProject("coding", "Coding", tasks = listOf(task)),
            ChatProject("business", "Business", tasks = listOf(task.copy(title = "Proposal"))),
            ChatProject("archived", "Archived", archived = true, tasks = listOf(task)))
        val all = DailyPlanner.day(projects, LocalDate.parse("2030-01-02"), utc).scheduled
        assertEquals(2, all.size)
        assertEquals(2, all.map { it.key }.distinct().size)
        assertEquals(setOf("Business", "Coding"), all.map { it.projectName }.toSet())
        assertEquals(listOf("Proposal"), DailyPlanner.day(projects, LocalDate.parse("2030-01-02"), utc, "business").scheduled.map { it.task.title })
        for (filter in listOf("removed", "archived")) assertEquals(all, DailyPlanner.day(projects, LocalDate.parse("2030-01-02"), utc, filter).scheduled)
    }

    @Test fun deadlineIsReclassifiedInTheCurrentTimeZoneWithoutChangingItsInstant() {
        val deadline = at("2030-01-01T22:00:00Z")
        val projects = listOf(ChatProject("personal", "Personal", tasks = listOf(ProjectTask("one", "One", dueAt = deadline))))
        val date = LocalDate.parse("2030-01-02")
        assertEquals(1, DailyPlanner.day(projects, date, utc).earlier.size)
        val local = DailyPlanner.day(projects, date, ZoneId.of("Asia/Kolkata"))
        assertTrue(local.earlier.isEmpty())
        assertEquals(deadline, local.scheduled.single().task.dueAt)
        assertEquals(deadline, projects.single().tasks.single().dueAt)
    }

    @Test fun springDayHas23HoursAndFallDayHas25HoursIncludingBothClockBackOccurrences() {
        val berlin = ZoneId.of("Europe/Berlin")
        for ((day, times) in listOf(
            "2026-03-29" to listOf("2026-03-28T23:00:00Z", "2026-03-29T21:59:59.999Z", "2026-03-29T22:00:00Z"),
            "2026-10-25" to listOf("2026-10-24T22:00:00Z", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z", "2026-10-25T22:59:59.999Z", "2026-10-25T23:00:00Z"))) {
            val projects = listOf(ChatProject("personal", "Personal", tasks = times.mapIndexed { index, time -> task("task-$index", time) }))
            val plan = DailyPlanner.day(projects, LocalDate.parse(day), berlin)
            assertEquals(times.dropLast(1).map(::at), plan.scheduled.map { it.task.dueAt })
            assertTrue(plan.earlier.isEmpty())
        }
    }

    @Test fun newSnapshotChangesCompletionDueDateAndProjectNamesWithoutMutatingThePreviousOne() {
        val date = LocalDate.parse("2030-01-02")
        val original = ChatProject("coding", "Coding", tasks = listOf(task("one", "2030-01-02T10:00:00Z")))
        val complete = original.copy(name = "Code work", tasks = original.tasks.map { it.copy(completed = true) })
        assertEquals("Code work", DailyPlanner.day(listOf(complete), date, utc).completed.single().projectName)
        assertTrue(DailyPlanner.day(listOf(complete), date, utc).scheduled.isEmpty())
        val undated = complete.copy(tasks = complete.tasks.map { it.copy(completed = false, dueAt = null) })
        assertEquals("one", DailyPlanner.day(listOf(undated), date, utc).undated.single().task.id)
        assertEquals(1, DailyPlanner.day(listOf(original), date, utc).scheduled.size)
        assertTrue(DailyPlanner.day(emptyList(), date, utc).undated.isEmpty())
    }
}
