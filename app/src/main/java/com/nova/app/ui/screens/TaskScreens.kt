package com.nova.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.EmptyState
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaTextField
import com.nova.app.ui.theme.NovaTheme

data class TaskItem(val id: String, val title: String, val due: String, val done: Boolean, val priority: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val tasks = remember {
        mutableStateListOf(
            TaskItem("1", "Review Nova splash motion", "Today", false, "High"),
            TaskItem("2", "Write weekly note", "Today", false, "Med"),
            TaskItem("3", "Walk 20 minutes", "Today", true, "Low")
        )
    }
    val visible = tasks.filter {
        when (filter) {
            "Today" -> it.due == "Today" && !it.done
            "Done" -> it.done
            else -> true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search tasks")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add task", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Today", "Done").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option) },
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (visible.isEmpty()) {
                EmptyState("No tasks yet", "Add one small thing you can finish today.", "Add your first task", onAdd)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { task ->
                        NovaCard(onClick = { onEdit(task.id) }) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = {
                                        val i = tasks.indexOfFirst { it.id == task.id }
                                        if (i >= 0) tasks[i] = task.copy(done = it)
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                    Text("${task.due} · ${task.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(taskId: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val editing = !taskId.isNullOrBlank()
    var title by rememberSaveable { mutableStateOf(if (editing) "Review Nova splash motion" else "") }
    var notes by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("Med") }
    var remind by rememberSaveable { mutableStateOf(false) }
    var titleError by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit task" else "New task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (title.isBlank()) titleError = true else onSaved()
                    }) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            NovaTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = "Title",
                isError = titleError,
                supportingText = if (titleError) "A task needs a title." else null
            )
            Spacer(Modifier.height(16.dp))
            NovaTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                singleLine = false,
                minLines = 4
            )
            Spacer(Modifier.height(16.dp))
            Text("Due", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text("Today, 6:00 PM", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Low", "Med", "High").forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p) },
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Remind me", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = remind, onCheckedChange = { remind = it })
            }
            if (editing) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onBack) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete task", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TasksLight() { NovaTheme(false) { TaskListScreen({}, {}) } }

@Preview(showBackground = true)
@Composable
private fun TaskEditLight() { NovaTheme(false) { TaskEditScreen(null, {}, {}) } }
