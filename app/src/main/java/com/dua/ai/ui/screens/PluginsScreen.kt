package com.dua.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.setup.IntegrationState
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.*

@Composable
fun PluginsContent(state: IntegrationState, model: String, search: Boolean, code: Boolean, busy: Boolean,
    memory: Boolean, signedIn: Boolean, repository: String, notice: String, onBack: () -> Unit,
    onSearch: (Boolean) -> Unit, onCode: (Boolean) -> Unit, onSetup: () -> Unit, onMemory: () -> Unit, onRefresh: () -> Unit,
    separateEngine: Boolean = false, agentAvailable: Boolean = false, onAgent: () -> Unit = onSetup) = DuaPageTheme {
    val grounding = !separateEngine && model in setOf("Dua", "Gemini") && model in state.nativeSearch
    val runner = !separateEngine && state.codeExecution?.available(model) == true
    val github = state.groups.any { it.id == "connectors" && it.state in setOf("configured", "partial") } || state.build?.enabled == true
    Column(Modifier.fillMaxSize().background(PageTheme.colors.background).navigationBarsPadding().testTag("plugins-feature")) {
        FeatureTopBar("Plugins", onBack, {}, showNewSession = false)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text("Connected tools", style = MaterialTheme.typography.headlineSmall)
            PluginCard("Google Search Grounding", when { separateEngine && agentAvailable -> "Available in AI Agent"; grounding && search -> "Active"; grounding -> "Available for $model"; else -> "Setup required for $model" },
                if (separateEngine) "Cloud engine search is managed by AI Agent, not the standalone Search switch." else "Live research in Search mode. Agent manages its own search tools.",
                if (separateEngine) null else search, !busy && (grounding || search), onSearch, if (separateEngine && agentAvailable) onAgent else onSetup,
                openLabel = if (separateEngine && agentAvailable) "Open AI Agent" else null)
            PluginCard("GitHub Repository & PR Connector", if (github) "Configured" else "Setup required",
                "Base API connector: " + repository.ifBlank { "View configured repositories, permissions and PR setup." }, onOpen = onSetup)
            PluginCard("Code Runner / Sandbox", when { separateEngine && agentAvailable -> "Available in AI Agent"; runner && code -> "Active"; runner -> "Available for $model"; else -> "Setup required for $model" },
                if (separateEngine) "AI Agent can request provider-hosted Python execution. A standalone code switch is not supported by this chat engine."
                else state.codeExecution?.reason?.ifBlank { null } ?: "Provider-hosted execution for supported models; language support depends on the provider.",
                if (separateEngine) null else code, !busy && (runner || code), onCode, if (separateEngine && agentAvailable) onAgent else onSetup,
                openLabel = if (separateEngine && agentAvailable) "Open AI Agent" else null)
            PluginCard("Cloud Memory & Context", if (memory) "Memory on" else "Memory off",
                if (signedIn) "Account-backed notes with a device cache. Open memory controls and sync status." else "Sign in to sync notes. Guest notes remain on this device.", onOpen = onMemory)
            if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium, color = PageTheme.colors.subText)
            OutlinedButton(onClick = onRefresh, enabled = !busy && !state.busy, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Refresh capabilities") }
        }
    }
}

@Composable
private fun PluginCard(title: String, status: String, detail: String, checked: Boolean? = null,
    enabled: Boolean = true, onToggle: (Boolean) -> Unit = {}, onOpen: () -> Unit, openLabel: String? = null) {
    Surface(shape = RoundedCornerShape(Spacing.xxl), color = PageTheme.colors.card, border = BorderStroke(1.dp, PageTheme.colors.cardBorder), modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(Spacing.lg)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelLarge, color = PageTheme.colors.subText)
                Text(detail, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
            }
            HorizontalDivider(color = PageTheme.colors.divider, thickness = 1.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget).testTag("plugin-open-$title")) { Text(openLabel ?: if (checked == null) "Open controls" else "Setup and details") }
                if (checked != null) Switch(checked, onToggle, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = Sizes.touchTarget, minHeight = Sizes.touchTarget)
                        .semantics { contentDescription = title }.testTag("plugin-$title"), thumbContent = null)
            }
        }
    }
}
