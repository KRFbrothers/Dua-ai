package com.nova.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.EmptyState
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaTextField
import com.nova.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

data class NoteItem(val id: String, val title: String, val preview: String, val date: String)

private val SampleNotes = listOf(
    NoteItem("1", "Nova motion notes", "Splash should ease, not bounce. Keep the mark still.", "Today"),
    NoteItem("2", "Week plan", "Mon deep work. Tue reviews. Fri off-grid walk.", "Mon"),
    NoteItem("3", "Reading", "Cal Newport — slow productivity. One project at a time.", "Sun")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(onOpen: (String) -> Unit, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val notes = SampleNotes.filter {
        query.isBlank() || it.title.contains(query, true) || it.preview.contains(query, true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New note", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 24.dp)) {
            NovaTextField(value = query, onValueChange = { query = it }, label = "Search notes")
            Spacer(Modifier.height(16.dp))
            if (notes.isEmpty()) {
                EmptyState("No notes", "Write the smallest useful sentence.", "New note", onAdd)
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        NovaCard(onClick = { onOpen(note.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(note.title, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text(note.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(note.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(noteId: String, onBack: () -> Unit) {
    val seed = SampleNotes.find { it.id == noteId }
    var title by rememberSaveable { mutableStateOf(seed?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(seed?.preview ?: "") }
    var saved by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(title, body) {
        saved = false
        delay(600)
        saved = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (saved) "Saved" else "Editing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Outlined.PushPin, contentDescription = "Pin note") }
                    IconButton(onClick = { }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Untitled") },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxSize().weight(0.25f)
            )
            TextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Start writing…") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxSize().weight(0.75f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesLight() { NovaTheme(false) { NotesListScreen({}, {}) } }

@Preview(showBackground = true)
@Composable
private fun NoteDetailLight() { NovaTheme(false) { NoteDetailScreen("1", {}) } }
