package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dua.ai.ui.icons.DuaIcons
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
 * Rounded multi-line composer. Bottom row: attach (+) · Auto · mic · send/stop.
 * The mode chips (Run code, Reasoning, Search, …) sit above it in [ModeChipRow].
 * Auto = no reasoning, web, code, Imagine or Build for the next reply.
 */
@Composable
fun Composer(
    state: ChatUiState,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClearAttachments: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onAuto: () -> Unit = {},
    onCancelUpload: (String) -> Unit = {},
    onImagineOptions: () -> Unit = {},
) {
    val colors = DuaTheme.colors
    val scheme = MaterialTheme.colorScheme
    val idle = !state.typing && !state.readingFiles
    val inputStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp)
    Surface(shape = DuaShape.composer, color = colors.composer, border = BorderStroke(1.dp, colors.composerBorder),
        modifier = modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm)) {
        Column(Modifier.padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs, bottom = Spacing.xs)) {
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
                textStyle = inputStyle.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).focusRequester(focusRequester)
                    .padding(start = Spacing.md + Spacing.xxs, end = Spacing.md, top = Spacing.md, bottom = Spacing.md).semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box {
                        if (state.draft.isEmpty()) Text(placeholder(state), style = inputStyle, color = scheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            if (state.imagine) ImagineOptionChips(state, enabled = idle, onClick = onImagineOptions,
                modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs))
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xxs, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                RoundComposerButton(Icons.Outlined.Add, "Add photos and files", enabled = idle, onClick = onAttach)
                AutoPill(selected = state.auto, enabled = idle, onClick = onAuto)
                Spacer(Modifier.weight(1f))
                RoundComposerButton(Icons.Outlined.Mic, "Voice input", enabled = true, onClick = onMic)
                SendButton(typing = state.typing, canSend = state.canSend, onSend = onSend, onStop = onStop)
            }
        }
    }
}

/**
 * Mode chips above the composer: Run code (when the server offers it), Reasoning and Search, plus chips
 * for modes that are on (Build, Imagine, Read link) and X search (Grok only, when offered). The chips
 * share the row width when they fit and scroll sideways otherwise (small screens, extra chips).
 */
@Composable
fun ModeChipRow(
    state: ChatUiState,
    onWebMode: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onReasoning: (Boolean) -> Unit = {},
    onXSearch: (Boolean) -> Unit = {},
    onImagine: (Boolean) -> Unit = {},
    onRunCode: (Boolean) -> Unit = {},
    onBuild: () -> Unit = {},
) {
    val idle = !state.typing && !state.readingFiles
    FillOrScrollRow(gap = Spacing.sm, horizontalPadding = Spacing.lg, modifier = modifier.fillMaxWidth().padding(bottom = Spacing.md)) {
        // Build mode on: the chat's repository; tap to change it or turn Build off. (Build is started from the + sheet.)
        if (state.buildRepo != null) ModeChip("Build · ${state.buildRepo.substringAfter('/')}", Icons.Outlined.Build, isSelected = true, enabled = idle,
            description = "Build mode on, read-only, repository ${state.buildRepo}. Tap to change", trailing = Icons.Outlined.ExpandMore) { onBuild() }
        // Imagine on (started from the + sheet): tap to turn it off.
        if (state.imagine) ModeChip("Imagine", Icons.Outlined.Brush, isSelected = true, enabled = idle,
            description = "Imagine on. Tap to turn off", trailing = Icons.Outlined.Close) { onImagine(false) }
        // Run code: the provider runs Python in its own sandbox (shown when the backend offers it).
        if (state.codeConfig != null) ModeChip("Run code", Icons.Outlined.Terminal, isSelected = state.runCode, enabled = idle,
            description = when {
                state.runCode -> "Run code on: the AI can run Python for this reply. Tap to turn off"
                state.codeConfig.available(state.model) -> "Let the AI run Python code"
                state.codeConfig.enabled -> "Run code, not available for ${state.modelLabel}"
                else -> "Run code, turned off on the server"
            }) { onRunCode(!state.runCode) }
        ModeChip("Reasoning", Icons.Outlined.Lightbulb, isSelected = state.reasoning, enabled = idle,
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
}

/** Lays children out in one row: stretched equally to fill the width when they fit, otherwise at their natural width in a sideways scroll. */
@Composable
private fun FillOrScrollRow(gap: Dp, horizontalPadding: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth - with(LocalDensity.current) { (horizontalPadding * 2).roundToPx() }
        Layout(content, Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = horizontalPadding)) { measurables, constraints ->
            val gapPx = gap.roundToPx()
            val widths = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val natural = widths.sum() + gapPx * (measurables.size - 1).coerceAtLeast(0)
            val extra = if (measurables.isNotEmpty() && natural < available) (available - natural) / measurables.size else 0
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(minWidth = widths[index] + extra, maxWidth = widths[index] + extra, maxHeight = constraints.maxHeight))
            }
            val width = placeables.sumOf { it.width } + gapPx * (placeables.size - 1).coerceAtLeast(0)
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                var x = 0
                placeables.forEach { it.placeRelative(x, 0); x += it.width + gapPx }
            }
        }
    }
}

/** Imagine options as compact chips above the mode row; each opens the options sheet. */
@Composable
private fun ImagineOptionChips(state: ChatUiState, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val options = state.imagineOptions
    val video = options.kind == com.dua.ai.imagine.ImagineInfo.VIDEO
    val labels = buildList {
        add((if (video) "Video" else "Image") to (if (video) Icons.Outlined.Movie else Icons.Outlined.Image))
        add(com.dua.ai.imagine.ImagineCodec.aspectLabel(options.aspectRatio) to Icons.Outlined.AspectRatio)
        if (video) { add("${options.duration} s" to Icons.Outlined.Timer); add(options.resolution to Icons.Outlined.HighQuality) }
        else if (options.count > 1) add("×${options.count}" to Icons.Outlined.GridView)
    }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics(mergeDescendants = true) {
        contentDescription = "Imagine options: ${imagineSummary(options, state.imagineConfig)}. Tap to change"; role = Role.Button
    }, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, (label, icon) ->
            ModeChip(label, icon, isSelected = index == 0, enabled = enabled, description = label, trailing = if (index == 0) Icons.Outlined.ExpandMore else null, compact = true, onClick = onClick)
        }
    }
}

internal fun placeholder(state: ChatUiState): String = when {
    state.buildRepo != null -> "Ask about ${state.buildRepo.substringAfter('/')}"
    !state.imagine -> "Ask anything"
    state.imagineOptions.kind == com.dua.ai.imagine.ImagineInfo.VIDEO -> if (state.imagineSource) "Describe the motion (optional)" else "Describe a video"
    state.imagineSource -> "Describe the edit"
    else -> "Describe an image"
}

/** Pill chip used for modes (above the composer) and Imagine options. Selected chips use the accent container. */
@Composable
private fun ModeChip(label: String, icon: ImageVector, isSelected: Boolean, enabled: Boolean, description: String,
    trailing: ImageVector? = null, compact: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = DuaTheme.colors
    Surface(
        onClick = onClick, enabled = enabled, shape = DuaShape.chip,
        color = if (isSelected) scheme.primaryContainer else colors.composer,
        contentColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface.copy(alpha = .88f),
        border = BorderStroke(1.dp, if (isSelected) scheme.primary.copy(alpha = .5f) else colors.composerBorder),
        modifier = Modifier.semantics { contentDescription = description; selected = isSelected; role = Role.Button },
    ) {
        Row(Modifier.heightIn(min = if (compact) 32.dp else Sizes.modeChipHeight).padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) Sizes.iconTiny else Sizes.iconSmall))
            Text(label, maxLines = 1, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier.padding(start = if (compact) Spacing.xs else Spacing.sm))
            if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconTiny))
        }
    }
}

/** Round 36 dp button inside the composer (+, mic), with a 48 dp touch target. */
@Composable
private fun RoundComposerButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .86f else .38f),
            modifier = Modifier.size(Sizes.composerButton).semantics { contentDescription = description; role = Role.Button }) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
        }
    }
}

/** "Auto" pill: filled while no mode is on; tapping it turns every mode off for the next reply. */
@Composable
private fun AutoPill(selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = DuaShape.chip,
        color = if (selected) scheme.surfaceContainerHighest else Color.Transparent,
        contentColor = scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, DuaTheme.colors.composerBorder),
        modifier = Modifier.padding(start = Spacing.xs).semantics { contentDescription = "Auto mode, reasoning and web off"; this.selected = selected; role = Role.RadioButton }) {
        Row(Modifier.height(Sizes.composerButton).padding(start = Spacing.md, end = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(DuaIcons.Sparkle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text("Auto", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

/** Round send button: dimmed while there is nothing to send; a stop button while a reply streams. */
@Composable
private fun SendButton(typing: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val colors = DuaTheme.colors
    val active = typing || canSend
    Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
        Surface(onClick = { if (typing) onStop() else onSend() }, enabled = active, shape = CircleShape,
            color = if (active) colors.action else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (active) colors.onAction else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
            modifier = Modifier.size(Sizes.sendButton).semantics { contentDescription = if (typing) "Stop response" else "Send message"; role = Role.Button }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (typing) Icons.Filled.Stop else Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall + 2.dp))
            }
        }
    }
}
