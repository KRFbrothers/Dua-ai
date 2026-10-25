package com.dua.ai.tasks

import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PlannedTask(val projectId: String, val projectName: String, val task: ProjectTask) {
    // Task IDs are project-local. Valid project/task IDs cannot contain '/'.
    val key get() = "$projectId/${task.id}"
}

data class PlannerDay(
    val earlier: List<PlannedTask>,
    val scheduled: List<PlannedTask>,
    val completed: List<PlannedTask>,
    val undated: List<PlannedTask>,
)

/** A view of the current project snapshot, never a second task store or a completion history. */
object DailyPlanner {
    fun day(projects: List<ChatProject>, date: LocalDate, zone: ZoneId, projectId: String? = null): PlannerDay {
        val active = projects.filterNot { it.archived }
        // A removed/archived filter falls back to all active projects after a restore/sync.
        val filter = projectId?.takeIf { id -> active.any { it.id == id } }
        val tasks = active.filter { filter == null || it.id == filter }
            .flatMap { project -> project.tasks.map { PlannedTask(project.id, project.name, it) } }
            .sortedWith(compareBy({ it.task.dueAt ?: Long.MAX_VALUE }, { it.projectId }, { it.task.id }))
        val earlier = mutableListOf<PlannedTask>()
        val scheduled = mutableListOf<PlannedTask>()
        val completed = mutableListOf<PlannedTask>()
        val undated = mutableListOf<PlannedTask>()
        for (entry in tasks) {
            val due = entry.task.dueAt
            if (due == null) {
                if (!entry.task.completed) undated += entry
                continue
            }
            // Compare local calendar days, not 24-hour buckets: DST days can have 23 or 25 hours.
            val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            when {
                dueDate == date -> if (entry.task.completed) completed += entry else scheduled += entry
                dueDate < date && !entry.task.completed -> earlier += entry
            }
        }
        return PlannerDay(earlier, scheduled, completed, undated)
    }
}
