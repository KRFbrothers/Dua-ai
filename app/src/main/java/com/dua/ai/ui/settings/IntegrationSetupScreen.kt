package com.dua.ai.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dua.ai.setup.IntegrationState
import com.dua.ai.setup.RepositoryResult
import com.dua.ai.setup.SearchResult
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.SheetTonalButton
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.PageBody
import com.dua.ai.ui.components.PageNote
import com.dua.ai.ui.components.PageStatus
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsRowDivider
import com.dua.ai.ui.components.SettingsToggleRow
import com.dua.ai.ui.components.SettingsValueRow
import com.dua.ai.ui.components.pageTextFieldColors
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.viewmodel.ChatViewModel

/** Every Feature setup action, so the page can be driven without a view model (tests, previews). */
class IntegrationSetupActions(
    val onRefresh: () -> Unit = {}, val onSearch: (String) -> Unit = {}, val onOpenUrl: (String) -> Unit = {}, val onCloud: () -> Unit = {},
    val onProposeMemory: () -> Unit = {}, val onAcceptMemory: (String) -> Unit = {}, val onDismissMemory: (String) -> Unit = {},
    val onListRepositories: () -> Unit = {}, val onGeneratedVoice: (Boolean) -> Unit = {}, val onProfile: () -> Unit = {},
)

@Composable
fun IntegrationSetupScreen(viewModel: ChatViewModel, onBack: () -> Unit, onProfile: () -> Unit, onCloud: () -> Unit) {
    val state by viewModel.integrations.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val repositories by viewModel.connectorRepositories.collectAsState()
    val memoryCandidates by viewModel.memoryCandidates.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val preferences by viewModel.localSettings.collectAsState()
    val context = LocalContext.current
    fun open(url: String) {
        val safe = com.dua.ai.setup.IntegrationCapabilities.safeUrl(url) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe))) }
            .onFailure { viewModel.integrationNotice("A browser is unavailable on this device.") }
    }
    LaunchedEffect(Unit) { viewModel.loadIntegrations(); viewModel.loadProviderConfiguration() }
    IntegrationSetupContent(state, results, repositories, memoryCandidates, signedIn = account.user != null, generatedVoice = preferences.generatedVoice,
        onBack = onBack, actions = IntegrationSetupActions(
            onRefresh = { viewModel.loadIntegrations(); viewModel.loadProviderConfiguration() }, onSearch = { viewModel.searchWeb(it) }, onOpenUrl = ::open,
            onCloud = onCloud, onProposeMemory = { viewModel.proposeMemoryNotes() }, onAcceptMemory = { viewModel.acceptMemoryNote(it) },
            onDismissMemory = { viewModel.dismissMemoryNote(it) }, onListRepositories = { viewModel.listConnectorRepositories() },
            onGeneratedVoice = { viewModel.updateSettings(preferences.copy(generatedVoice = it)) }, onProfile = onProfile))
}

@Composable
fun IntegrationSetupContent(state: IntegrationState, results: List<SearchResult>, repositories: List<RepositoryResult>, memoryCandidates: List<String>,
    signedIn: Boolean, generatedVoice: Boolean, onBack: () -> Unit, actions: IntegrationSetupActions) {
    var query by rememberSaveable { mutableStateOf("") }
    DuaPageScaffold(title = "Feature setup", onBack = onBack) {
        PageNote("Diagnostics for providers, search, sync, memory, connectors and voice. Provider keys and account connections are managed securely on the backend.")
        SheetTonalButton("Refresh setup", onClick = actions.onRefresh, enabled = !state.busy)
        state.groups.forEach { group ->
            SettingsGroup(title = group.title) {
                SettingsValueRow("Status", when (group.state) { "configured" -> "Configured"; "available" -> "Available"; "partial" -> "Partly available"; else -> "Setup required" })
                if (group.id == "voice") {
                    SettingsRowDivider()
                    SettingsToggleRow(icon = null, title = "Use AI voice for reply speaker", checked = generatedVoice,
                        enabled = !state.busy && (generatedVoice || (state.generatedVoiceConfigured && signedIn)), onCheckedChange = actions.onGeneratedVoice)
                }
                SettingsRowDivider()
                Column(Modifier.fillMaxWidth().padding(PageSizes.cardPadding), verticalArrangement = Arrangement.spacedBy(PageSizes.itemGap)) {
                    group.limitations.forEach { PageNote(it) }
                    when (group.id) {
                        "providers" -> state.providers.forEach { provider -> PageBody("${provider.name}: ${if (provider.configured) "ready" else "setup required"}") }
                        "search" -> {
                            OutlinedTextField(query, { query = it.take(500) }, label = { Text("Search the web") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                                colors = pageTextFieldColors())
                            SheetTonalButton("Search", onClick = { actions.onSearch(query) }, enabled = state.searchConfigured && !state.busy && query.isNotBlank())
                            results.forEach { result ->
                                SheetTextButton(result.title.ifBlank { result.url }, onClick = { actions.onOpenUrl(result.url) })
                                PageBody(result.snippet)
                                PageNote(result.url)
                            }
                        }
                        "sync" -> {
                            PageBody("Dua supports opt-in automatic snapshots while the app is open. Enable it in Cloud sync on each device. Conflicts and deletions pause for review; sync stops in the background.")
                            SheetTextButton("Open cloud sync", onClick = actions.onCloud)
                        }
                        "memory" -> {
                            PageBody("Suggest notes from explicit ‘remember’ requests in this chat. Review each note before saving. Notes are sent with chat only when Memory is enabled.")
                            SheetTonalButton("Suggest memory notes", onClick = actions.onProposeMemory, enabled = !state.busy)
                            memoryCandidates.forEach { note ->
                                PageBody(note)
                                Row {
                                    SheetTextButton("Save note", onClick = { actions.onAcceptMemory(note) })
                                    SheetTextButton("Dismiss", onClick = { actions.onDismissMemory(note) })
                                }
                            }
                        }
                        "connectors" -> {
                            PageBody("GitHub lists repositories only. It does not edit code or send messages.")
                            SheetTonalButton("List GitHub repositories", onClick = actions.onListRepositories, enabled = !state.busy && signedIn && group.state == "configured")
                            repositories.forEach { repository -> SheetTextButton(repository.name, onClick = { actions.onOpenUrl(repository.url) }); PageBody(repository.description) }
                        }
                        "computer" -> PageBody("Browser screen capture setup does not control another computer. Native screen sharing/video capture is not active.")
                        "voice" -> {
                            PageBody("Device voice is the free default. Optional AI voice sends only a completed reply after you tap its speaker. Provider quota/charges may apply. No automatic audio requests or background conversation.")
                            if (!state.generatedVoiceConfigured) PageNote("AI voice backend setup is required. Device voice stays available.")
                            else if (!signedIn) SheetTextButton("Sign in for AI voice", onClick = actions.onProfile)
                            PageNote("AI voice supports replies up to 2000 characters. Audio stays in memory and is released on Stop, navigation, backgrounding or sign-in changes. Voice mode uses it when enabled, else device speech.")
                        }
                        "account" -> SheetTextButton("Profile and account controls", onClick = actions.onProfile)
                    }
                }
            }
        }
        PageStatus(state.notice)
    }
}
