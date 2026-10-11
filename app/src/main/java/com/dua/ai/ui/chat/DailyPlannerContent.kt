package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dua.ai.projects.ChatProject
import com.dua.ai.tasks.DailyPlanner
import com.dua.ai.tasks.PlannedTask
import com.dua.ai.tasks.TaskDates
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class PlannerClock(val now: Long, val zone: ZoneId)
private fun currentClock() = PlannerClock(System.currentTimeMillis(), ZoneId.systemDefault())

@Composable
internal fun DailyPlannerScreen(projects: List<ChatProject>, notice: String, enabled: Boolean,
    tasksEnabled: Boolean, onToggle: (String, String) -> Boolean, onOpenTasks: (String) -> Unit, onBack: () -> Unit) {
    var clock by remember { mutableStateOf(currentClock()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                clock = currentClock()
                delay(60_000)
            }
        }
    }
    DailyPlannerContent(projects, notice, enabled, tasksEnabled, clock.now, clock.zone, onToggle, onOpenTasks, onBack)
}

@Composable
internal fun DailyPlannerContent(projects: List<ChatProject>, notice: String, enabled: Boolean, tasksEnabled: Boolean,
    now: Long, zone: ZoneId, onToggle: (String, String) -> Boolean, onOpenTasks: (String) -> Unit, onBack: () -> Unit) {
    // Null follows Today across midnight; a browsed calendar date stays fixed.
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = selectedDate?.let(LocalDate::parse) ?: today
    val active = projects.filterNot { it.archived }
    val selectedProject = active.find { it.id == projectId }
    LaunchedEffect(active.map { it.id }, projectId) {
        if (projectId != null && selectedProject == null) projectId = null
    }
    val plan = remember(projects, date, zone, selectedProject?.id) { DailyPlanner.day(projects, date, zone, selectedProject?.id) }
    val canEdit = enabled && tasksEnabled
    val dayLabel = when (date) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> date.toString() }
    val earlierLabel = if (date <= today) "Overdue before this day" else "Earlier unfinished"
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        item {
            Text("Daily planner", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("$dayLabel · $date · ${zone.id}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedDate = date.minusDays(1).toString() }, modifier = Modifier.semantics { contentDescription = "Previous planner day" }) { Text("Previous") }
                TextButton(onClick = { selectedDate = null }) { Text("Today") }
                TextButton(onClick = { selectedDate = date.plusDays(1).toString() }, modifier = Modifier.semantics { contentDescription = "Next planner day" }) { Text("Next") }
            }
            Box {
                OutlinedButton(onClick = { filterOpen = true }, modifier = Modifier.semantics { contentDescription = "Planner project filter" }) {
                    Text(selectedProject?.name ?: "All active projects")
                }
                DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    DropdownMenuItem(text = { Text("All active projects") }, onClick = { projectId = null; filterOpen = false },
                        modifier = Modifier.semantics { contentDescription = "Show all active projects in planner" })
                    active.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; filterOpen = false },
                        modifier = Modifier.semantics { contentDescription = "Filter planner to ${project.name}" }) }
                }
            }
            Text("${plan.scheduled.size} due on this day · ${plan.earlier.size} earlier unfinished · ${plan.undated.size} without a date")
            Text("Sync task changes to save them on the server. Archived projects are hidden.", style = MaterialTheme.typography.bodySmall)
            if (notice.isNotBlank()) Text(notice)
            if (!tasksEnabled) Text("Refresh setup after deploying the Tasks backend to edit tasks.")
            else if (!enabled) Text("Finish the current action before editing tasks.")
            selectedProject?.let { project -> TextButton(onClick = { onOpenTasks(project.id) }) { Text("Manage ${project.name} tasks") } }
        }
        fun group(label: String, values: List<PlannedTask>, empty: String) {
            item(key = "heading/$label") { Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            if (values.isEmpty()) item(key = "empty/$label") { Text(empty, style = MaterialTheme.typography.bodySmall) }
            items(values, key = { it.key }) { entry ->
                PlannerTaskRow(entry, now, zone, canEdit, onToggle, onOpenTasks)
            }
        }
        group(earlierLabel, plan.earlier, "No earlier unfinished tasks.")
        group("Due on this day", plan.scheduled, "No open tasks due on this day.")
        group("Completed · due on this day", plan.completed, "No completed tasks due on this day.")
        item { Text("Tasks without a date stay in your inbox until you add a deadline in their project.", style = MaterialTheme.typography.bodySmall) }
        group("No due date", plan.undated, "No undated open tasks.")
        item { TextButton(onClick = onBack) { Text("Back to projects") } }
    }
}

@Composable
private fun PlannerTaskRow(entry: PlannedTask, now: Long, zone: ZoneId, enabled: Boolean,
    onToggle: (String, String) -> Boolean, onOpenTasks: (String) -> Unit) {
    val task = entry.task
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.completed, { onToggle(entry.projectId, task.id) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "${if (task.completed) "Reopen" else "Complete"} planner task: ${entry.projectName} · ${task.title}" })
            Column(Modifier.weight(1f)) {
                Text(task.title)
                Text(entry.projectName, style = MaterialTheme.typography.labelMedium)
            }
        }
        task.dueAt?.let {
            val overdue = !task.completed && it < now
            Text("${if (overdue) "Overdue" else "Due"}: ${TaskDates.display(it, zone)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onOpenTasks(entry.projectId) }, modifier = Modifier.semantics { contentDescription = "Open tasks: ${entry.projectName} · ${task.title}" }) { Text("Open project tasks") }
        HorizontalDivider()
    }
}
