package com.nova.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.EmptyState
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.theme.NovaTheme

data class HabitItem(val name: String, val doneToday: Boolean, val weekDone: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(onBack: () -> Unit) {
    val habits = remember {
        mutableStateListOf(
            HabitItem("Morning light walk", true, 4),
            HabitItem("Read 10 pages", false, 3),
            HabitItem("No phone first hour", false, 2)
        )
    }
    var showNew by rememberSaveable { mutableStateOf(false) }
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val today = 1 // sample: Tuesday

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Habits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNew = true },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New habit", tint = MaterialTheme.colorScheme.onSecondary)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEachIndexed { i, d ->
                    val isToday = i == today
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (habits.isEmpty()) {
                EmptyState("Start a tiny habit", "Pick something you can do in two minutes.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(habits) { index, habit ->
                        NovaCard {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                HabitRing(progress = habit.weekDone / 7f, done = habit.doneToday)
                                Column(Modifier.weight(1f)) {
                                    Text(habit.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${habit.weekDone}/7 this week",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        habits[index] = habit.copy(
                                            doneToday = !habit.doneToday,
                                            weekDone = (habit.weekDone + if (!habit.doneToday) 1 else -1).coerceIn(0, 7)
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = if (habit.doneToday) "Undo today" else "Mark done today",
                                        tint = if (habit.doneToday) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showNew) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New habit") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Daily", "Weekdays").forEach {
                            FilterChip(selected = it == "Daily", onClick = {}, label = { Text(it) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) habits.add(0, HabitItem(name, false, 0))
                    showNew = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HabitRing(progress: Float, done: Boolean) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Canvas(Modifier.size(48.dp)) {
        drawArc(track, -90f, 360f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawArc(fill, -90f, 360f * animated, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitsLight() { NovaTheme(false) { HabitsScreen({}) } }

@Preview(showBackground = true)
@Composable
private fun HabitsDark() { NovaTheme(true) { HabitsScreen({}) } }
