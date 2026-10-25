package com.dua.ai.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dua.ai.account.AccountRepository
import com.dua.ai.network.AgentSavedWorkspace
import com.dua.ai.network.AgentWorkspacesApi
import com.dua.ai.network.AgentWorkspacesResult
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.*
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

@Composable
fun AgentWorkspacesScreen(account: AccountRepository, busy: Boolean, onBack: () -> Unit,
    onContinue: (String) -> Boolean) {
    val accountState by account.state.collectAsState()
    val owner = accountState.user?.id
    val api = remember { AgentWorkspacesApi() }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember(owner) { mutableStateOf<AgentWorkspacesResult?>(null) }
    var confirm by remember(owner) { mutableStateOf<AgentSavedWorkspace?>(null) }
    var message by remember(owner) { mutableStateOf<String?>(null) }
    LaunchedEffect(owner, reload) {
        result = null
        result = if (owner == null) AgentWorkspacesResult.Failed("Sign in from Profile to view saved drafts.") else try {
            account.withToken { api.list(it) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { AgentWorkspacesResult.Failed("Account or connection changed. Refresh to try again.") }
    }
    val c = PageTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        FeatureTopBar("Saved drafts", onBack, {}, showNewSession = false)
        LazyColumn(Modifier.widthIn(max = AgentSizes.maxWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(AgentSizes.padding), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                Text("Continue a coding draft from its saved commit. Commands are not replayed.",
                    style = MaterialTheme.typography.bodyMedium, color = c.subText)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text) }
            }
            when (val state = result) {
                null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                AgentWorkspacesResult.Disabled -> item {
                    Text("Saved drafts are not configured on the engine yet. Current task drafts stay in that task.",
                        style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
                is AgentWorkspacesResult.Failed -> item { Text(state.message, style = MaterialTheme.typography.bodyLarge, color = c.text) }
                is AgentWorkspacesResult.Ok -> {
                    if (state.items.isEmpty()) item { Text("No saved coding drafts yet.", style = MaterialTheme.typography.bodyLarge, color = c.text) }
                    items(state.items, key = { it.id }) { workspace ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(AgentSizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("Draft · ${workspace.base.take(8)}", style = MaterialTheme.typography.titleMedium)
                                Text("Revision ${workspace.revision}", style = MaterialTheme.typography.bodyMedium)
                                Text("Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workspace.updated))}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Expires ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workspace.expires))}",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { confirm = workspace }, enabled = !busy && !accountState.busy && workspace.expires > System.currentTimeMillis(),
                                    modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Continue with Agent") }
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = { reload++ }, enabled = result != null && !accountState.busy,
                modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Refresh") } }
        }
    }
    confirm?.let { workspace ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Continue this draft?") },
            text = { Text("Start a new Agent task and request this saved draft. Review its diff before approving any pull request.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (account.state.value.user?.id != owner || !onContinue(workspace.id))
                    message = "Couldn't start. Finish your current action, send or clear the composer, and check Agent access."
            }, enabled = !busy && !accountState.busy, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Continue with Agent") } },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Cancel") } })
    }
}
