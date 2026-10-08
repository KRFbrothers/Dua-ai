package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Spacing

private val MODEL_ORDER = listOf("Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi")

/** Model picker. Only providers the backend reports as configured can be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(current: String, providers: List<ProviderCapability>, onSelect: (String) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Choose a model", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.sm).semantics { heading() })
            Text("Pick who responds in this chat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm).padding(bottom = Spacing.sm))
            MODEL_ORDER.forEach { name ->
                val capability = providers.firstOrNull { it.name == name }
                val available = capability?.configured == true
                val provider = when (name) { "ChatGPT" -> "OpenAI"; "Claude" -> "Anthropic"; "Grok" -> "xAI"; "Kimi" -> "Moonshot"; else -> "Gemini" }
                val selected = current == name
                val alpha = if (available) 1f else .45f
                Surface(color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, shape = DuaShape.card) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .selectable(selected = selected, enabled = available, role = Role.RadioButton) { onSelect(name) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha), shape = DuaShape.codeBlock) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Text(name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha))
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                            Text(if (available) "$provider · ${capability?.model.orEmpty()}" else "$provider · Setup required",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text("Available models follow backend configuration. Provider keys stay on the server.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenSetup) { Text("Feature setup") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
