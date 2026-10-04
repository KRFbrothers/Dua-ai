package com.dua.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun IntegrationSetupScreen(viewModel: ChatViewModel, onBack: () -> Unit, onProfile: () -> Unit, onCloud: () -> Unit) {
    val state by viewModel.integrations.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val repositories by viewModel.connectorRepositories.collectAsState()
    val memoryCandidates by viewModel.memoryCandidates.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val preferences by viewModel.localSettings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    fun open(url: String) {
        val safe = com.dua.ai.setup.IntegrationCapabilities.safeUrl(url) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe))) }
            .onFailure { viewModel.integrationNotice("A browser is unavailable on this device.") }
    }
    LaunchedEffect(Unit) { viewModel.loadIntegrations(); viewModel.loadProviderConfiguration() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Feature setup", fontSize = 24.sp)
        Text("Check which integrations are configured. Account connections and provider keys are managed securely on the backend.")
        FilledTonalButton(enabled = !state.busy, onClick = { viewModel.loadIntegrations(); viewModel.loadProviderConfiguration() }) { Text("Refresh setup") }
        state.groups.forEach { group ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, fontSize = 18.sp)
                    Text(when (group.state) { "configured" -> "Configured"; "available" -> "Available"; "partial" -> "Partly available"; else -> "Setup required" })
                    group.limitations.forEach { Text(it, fontSize = 14.sp) }
                    when (group.id) {
                        "providers" -> state.providers.forEach { provider -> Text("${provider.name}: ${if (provider.configured) "ready" else "setup required"}", fontSize = 14.sp) }
                        "search" -> {
                            OutlinedTextField(query, { query = it.take(500) }, label = { Text("Search the web") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                            FilledTonalButton(enabled = state.searchConfigured && !state.busy && query.isNotBlank(), onClick = { viewModel.searchWeb(query) }) { Text("Search") }
                            results.forEach { result ->
                                TextButton(onClick = { open(result.url) }) { Text(result.title.ifBlank { result.url }) }
                                Text(result.snippet, fontSize = 14.sp)
                                Text(result.url, fontSize = 12.sp)
                            }
                        }
                        "sync" -> {
                            Text("Android and web support opt-in automatic snapshots while the app is open. Enable it in Cloud sync on each device. Conflicts and deletions pause for review; sync stops in the background.", fontSize = 14.sp)
                            TextButton(onClick = onCloud) { Text("Open cloud sync") }
                        }
                        "memory" -> {
                            Text("Suggest notes from explicit ‘remember’ requests in this chat. Review each note before saving. Notes are sent with chat only when Memory is enabled.", fontSize = 14.sp)
                            FilledTonalButton(enabled = !state.busy, onClick = { viewModel.proposeMemoryNotes() }) { Text("Suggest memory notes") }
                            memoryCandidates.forEach { note ->
                                Text(note, fontSize = 14.sp)
                                Row {
                                    TextButton(onClick = { viewModel.acceptMemoryNote(note) }) { Text("Save note") }
                                    TextButton(onClick = { viewModel.dismissMemoryNote(note) }) { Text("Dismiss") }
                                }
                            }
                        }
                        "connectors" -> {
                            Text("GitHub lists repositories only. It does not edit code or send messages.", fontSize = 14.sp)
                            FilledTonalButton(enabled = !state.busy && account.user != null && group.state == "configured", onClick = { viewModel.listConnectorRepositories() }) { Text("List GitHub repositories") }
                            repositories.forEach { repository -> TextButton(onClick = { open(repository.url) }) { Text(repository.name) }; Text(repository.description, fontSize = 14.sp) }
                        }
                        "computer" -> Text("Browser screen capture setup does not control another computer. Native screen sharing/video capture is not active.", fontSize = 14.sp)
                        "voice" -> {
                            Text("Device voice is the free default. Optional AI voice sends only a completed reply after you tap its speaker. Provider quota/charges may apply. No automatic audio requests or background conversation.", fontSize = 14.sp)
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Use AI voice for reply speaker", Modifier.weight(1f))
                                Switch(checked = preferences.generatedVoice, onCheckedChange = { viewModel.updateSettings(preferences.copy(generatedVoice = it)) },
                                    enabled = !state.busy && (preferences.generatedVoice || (state.generatedVoiceConfigured && account.user != null)),
                                    modifier = Modifier.semantics { contentDescription = "Use optional AI generated reply voice" })
                            }
                            if (!state.generatedVoiceConfigured) Text("AI voice backend setup is required. Device voice stays available.", fontSize = 14.sp)
                            else if (account.user == null) TextButton(onClick = onProfile) { Text("Sign in for AI voice") }
                            Text("AI voice supports replies up to 2000 characters. Audio stays in memory and is released on Stop, navigation, backgrounding or sign-in changes. Hands-free keeps using device speech.", fontSize = 14.sp)
                        }
                        "account" -> TextButton(onClick = onProfile) { Text("Profile and account controls") }
                    }
                }
            }
        }
        Text(state.notice, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
