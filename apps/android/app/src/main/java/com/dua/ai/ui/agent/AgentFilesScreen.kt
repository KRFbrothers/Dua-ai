package com.dua.ai.ui.agent

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.account.AccountRepository
import com.dua.ai.network.AgentDocument
import com.dua.ai.network.AgentDocumentsApi
import com.dua.ai.network.AgentDocumentsResult
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val FILES_DISABLED_MESSAGE = "Files aren't switched on for Dua yet. Ask the owner to turn on document search on the server."

/** UI state for the Agent Files page. */
internal data class AgentFilesState(val documents: List<AgentDocument> = emptyList(), val loading: Boolean = false,
    val disabled: Boolean = false, val message: String? = null)

/** Agent "Files": text files the Agent can search with its search_documents tool. */
@Composable
fun AgentFilesScreen(account: AccountRepository, onBack: () -> Unit, api: AgentDocumentsApi = remember { AgentDocumentsApi() }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AgentFilesState(loading = true)) }
    BackHandler(onBack = onBack)
    suspend fun refresh() {
        state = state.copy(loading = true)
        state = when (val r = account.withToken { api.list(it) }) {
            is AgentDocumentsResult.Ok -> state.copy(documents = r.value, loading = false, disabled = false)
            AgentDocumentsResult.Disabled -> state.copy(documents = emptyList(), loading = false, disabled = true)
            is AgentDocumentsResult.Failed -> state.copy(loading = false, message = r.message)
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = state.copy(loading = true, message = null)
            val (name, text) = withContext(Dispatchers.IO) {
                val display = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { input ->
                    val limit = AgentDocumentsApi.MAX_FILE_BYTES + 1
                    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
                    while (total < limit) { val n = input.read(buf, 0, minOf(buf.size, limit - total)); if (n < 0) break; out.write(buf, 0, n); total += n }
                    out.toByteArray() } }.getOrNull()
                AgentDocumentsApi.documentName(display) to (bytes?.let { AgentDocumentsApi.extractText(it) }
                    ?: Result.failure(IllegalArgumentException("Couldn't open that file.")))
            }
            val body = text.getOrElse { state = state.copy(loading = false, message = it.message); return@launch }
            when (val r = account.withToken { api.add(name, body, it) }) {
                is AgentDocumentsResult.Ok -> { state = state.copy(message = "Added $name"); refresh() }
                AgentDocumentsResult.Disabled -> state = state.copy(loading = false, disabled = true)
                is AgentDocumentsResult.Failed -> state = state.copy(loading = false, message = r.message)
            }
        }
    }
    AgentFilesContent(state, onBack, onAdd = { picker.launch(AgentDocumentsApi.PICKER_TYPES) }, onDelete = { doc ->
        scope.launch {
            state = state.copy(loading = true, message = null)
            when (val r = account.withToken { api.delete(doc.id, it) }) {
                is AgentDocumentsResult.Ok -> { state = state.copy(message = "Deleted ${doc.name}"); refresh() }
                AgentDocumentsResult.Disabled -> state = state.copy(loading = false, disabled = true)
                is AgentDocumentsResult.Failed -> state = state.copy(loading = false, message = r.message)
            }
        }
    })
}

@Composable
internal fun AgentFilesContent(state: AgentFilesState, onBack: () -> Unit, onAdd: () -> Unit, onDelete: (AgentDocument) -> Unit) {
    val c = PageTheme.colors
    var confirm by remember { mutableStateOf<AgentDocument?>(null) }
    Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        FeatureTopBar("Files", onBack, onAdd, newSessionEnabled = !state.loading && !state.disabled, actionLabel = "Add a text file")
        Column(Modifier.widthIn(max = AgentSizes.maxWidth).fillMaxWidth().align(Alignment.CenterHorizontally).padding(AgentSizes.padding)) {
            Text("Add text files (TXT, MD, CSV, JSON) so Dua Agent can search them. PDFs aren't supported yet. Up to 100,000 characters each.",
                style = MaterialTheme.typography.bodyMedium, color = c.subText)
            Spacer(Modifier.height(Spacing.md))
            if (state.disabled) Text(FILES_DISABLED_MESSAGE, Modifier.testTag("files-disabled"), style = MaterialTheme.typography.bodyLarge, color = c.text)
            state.message?.let { Text(it, Modifier.padding(bottom = Spacing.sm).testTag("files-message"), style = MaterialTheme.typography.bodyMedium, color = c.text) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
            if (!state.disabled) {
                Button(onClick = onAdd, enabled = !state.loading, modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag("files-add")) { Text("Add text file") }
                Spacer(Modifier.height(Spacing.md))
                if (state.documents.isEmpty() && !state.loading) Text("No files yet.", color = c.subText, textAlign = TextAlign.Start)
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(state.documents, key = { it.id }) { doc ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                            Text(doc.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                            IconButton(onClick = { confirm = doc }, enabled = !state.loading, modifier = Modifier.size(Sizes.touchTarget)) {
                                Icon(Icons.Default.Delete, "Delete ${doc.name}", tint = c.subText)
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { doc ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Delete file?") },
            text = { Text("Dua Agent will no longer search \"${doc.name}\".") },
            confirmButton = { TextButton(onClick = { confirm = null; onDelete(doc) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}
