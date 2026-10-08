package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/**
 * Rounded multi-line composer. Bottom row: attach · mode chips · mic · send/stop.
 * Auto = no reasoning and web off. Reasoning asks the model to think first (shown as a
 * Thinking block) and combines with Search or Read link (read the first HTTPS link, chosen
 * from the attach sheet).
 */
@Composable
fun Composer(
    state: ChatUiState,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onWebMode: (String?) -> Unit,
    onAttach: () -> Unit,
    onClearAttachments: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onReasoning: (Boolean) -> Unit = {},
    onAuto: () -> Unit = { onWebMode(null) },
    onXSearch: (Boolean) -> Unit = {},
    onCancelUpload: (String) -> Unit = {},
) {
    val colors = DuaTheme.colors
    val idle = !state.typing && !state.readingFiles
    Surface(shape = DuaShape.composer, color = colors.composer, border = BorderStroke(1.dp, colors.composerBorder),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Column(Modifier.padding(top = Spacing.sm, start = Spacing.xs, end = Spacing.xs, bottom = Spacing.xs)) {
            if (state.uploads.isNotEmpty())
                UploadChips(state.uploads, removeEnabled = !state.typing, onCancel = onCancelUpload,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs))
            if (state.attachments.isNotEmpty() || state.attachmentStatus.isNotBlank())
                AttachmentChips(state.attachments, state.attachmentStatus, removeEnabled = idle, onClear = onClearAttachments,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs))
            BasicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                enabled = !state.typing,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).focusRequester(focusRequester)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md).semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box {
                        if (state.draft.isEmpty()) Text("Ask anything", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach, enabled = idle) { Icon(Icons.Outlined.Add, contentDescription = "Add photos and files") }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    ModeChip("Auto", Icons.Outlined.AutoAwesome, isSelected = state.auto, enabled = idle,
                        description = "Auto mode, reasoning and web off") { onAuto() }
                    ModeChip("Reasoning", Icons.Outlined.Psychology, isSelected = state.reasoning, enabled = idle,
                        description = when {
                            state.reasoning -> "Reasoning on. Tap to turn off"
                            state.reasoningSupported -> "Think before answering"
                            else -> "Think before answering, not supported by ${state.modelLabel}"
                        }) { onReasoning(!state.reasoning) }
                    ModeChip("Search", Icons.Outlined.Language, isSelected = state.webMode == "search", enabled = idle,
                        description = if (state.webSearchConfigured) "Search the web" else "Search the web, needs setup") {
                        onWebMode(if (state.webMode == "search") null else "search")
                    }
                    // X posts via xAI x_search: Grok only, off by default, billed by xAI per post returned.
                    if (state.xSearchAvailable) ModeChip("X", Icons.Outlined.Tag, isSelected = state.xSearch, enabled = idle,
                        description = if (state.xSearch) "X search on: Grok also searches posts on X. Tap to turn off" else "Search posts on X with Grok") {
                        onXSearch(!state.xSearch)
                    }
                    if (state.webMode == "page") ModeChip("Read link", Icons.Outlined.Link, isSelected = true, enabled = idle,
                        description = "Read link mode. Tap to turn off", trailing = Icons.Outlined.Close) { onWebMode(null) }
                }
                IconButton(onClick = onMic) { Icon(Icons.Outlined.Mic, contentDescription = "Voice input") }
                SendButton(typing = state.typing, canSend = state.canSend, onSend = onSend, onStop = onStop)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: ImageVector, isSelected: Boolean, enabled: Boolean, description: String,
    trailing: ImageVector? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = DuaShape.chip,
        color = if (isSelected) scheme.secondaryContainer else scheme.surface.copy(alpha = 0f),
        contentColor = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (isSelected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.semantics { contentDescription = description; selected = isSelected; role = Role.RadioButton },
    ) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconTiny))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.xs))
            if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconTiny))
        }
    }
}

@Composable
private fun SendButton(typing: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val colors = DuaTheme.colors
    val active = typing || canSend
    IconButton(onClick = { if (typing) onStop() else onSend() }, enabled = active) {
        Box(Modifier.size(36.dp).clip(CircleShape)
            .background(if (active) colors.action else MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(if (typing) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                contentDescription = if (typing) "Stop response" else "Send message",
                tint = if (active) colors.onAction else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}
