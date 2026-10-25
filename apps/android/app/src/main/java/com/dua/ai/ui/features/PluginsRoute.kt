package com.dua.ai.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dua.ai.ui.components.SettingsToggleRow
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(vm: ChatViewModel, onBack: () -> Unit, onSetup: () -> Unit, onCloud: () -> Unit, onAgent: () -> Unit) {
    val state by vm.integrations.collectAsState()
    val model by vm.selectedModel.collectAsState()
    val web by vm.webMode.collectAsState()
    val code by vm.runCode.collectAsState()
    val typing by vm.isTyping.collectAsState()
    val dataBusy by vm.dataBusy.collectAsState()
    val account by vm.account.state.collectAsState()
    val preferences by vm.localSettings.collectAsState()
    val repository by vm.buildRepo.collectAsState()
    val repos by vm.connectorRepositories.collectAsState()
    val error by vm.error.collectAsState()
    val agentAvailable by vm.agentAvailable.collectAsState()
    var memory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadIntegrations(); vm.loadProviderConfiguration(); vm.refreshEngineCapabilities() }
    PluginsContent(state, model, web == "search", code, typing || dataBusy, preferences.memoryEnabled, account.user != null,
        repository ?: repos.joinToString { it.fullName.ifBlank { it.name } }, listOf(state.notice, error.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"),
        onBack, onSearch = { vm.setWebMode(if (it) "search" else null) }, onCode = vm::setRunCode, onSetup,
        onMemory = { memory = true }, onRefresh = { vm.loadIntegrations(); vm.loadProviderConfiguration(); vm.refreshEngineCapabilities() },
        separateEngine = com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL.trimEnd('/') != com.dua.ai.BuildConfig.DUA_API_BASE_URL.trimEnd('/'), agentAvailable = agentAvailable, onAgent = onAgent)
    if (memory) DuaPageTheme {
        ModalBottomSheet(onDismissRequest = { memory = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Memory", style = MaterialTheme.typography.titleLarge)
                SettingsToggleRow(icon = null, title = "Use memory in replies", checked = preferences.memoryEnabled, enabled = !typing && !dataBusy,
                    onCheckedChange = { vm.updateSettings(vm.localSettings.value.copy(memoryEnabled = it)) })
                Text(if (preferences.memory.isEmpty()) "No saved notes." else preferences.memory.joinToString("\n\n"), style = MaterialTheme.typography.bodyLarge)
                Text("Changes use the existing device cache and cloud-sync flow. Review notes in Feature setup before saving them.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { memory = false; onSetup() }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Review memory suggestions") }
                TextButton(onClick = { memory = false; onCloud() }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Cloud sync") }
            }
        }
    }
}
