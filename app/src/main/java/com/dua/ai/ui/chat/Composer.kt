package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.SubcomposeLayout
import com.dua.ai.ui.theme.rememberMotionAllowed
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
import androidx.compose.material.icons.outlined.GraphicEq
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
 * Rounded multi-line composer. Bottom row: attach (+) · model pill (Auto / model ⌄) · voice mode or send/stop.
 * The mode chips (Run code, Reasoning, Search, …) sit above it in [ModeChipRow].
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
    onModel: () -> Unit = {},
    /** Opens full-screen voice mode; the round waveform button shows while the field is empty. */
    onVoiceMode: () -> Unit = {},
    /** Text field focus and its bounds in the root, so the swipe-up gesture can leave the text field alone while typing. */
    onTextFieldFocus: (Boolean) -> Unit = {},
    onTextFieldBounds: (Rect) -> Unit = {},
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
                    .onFocusChanged { onTextFieldFocus(it.isFocused) }.onGloballyPositioned { onTextFieldBounds(it.boundsInRoot()) }
                    .padding(start = Spacing.md + Spacing.xxs, end = Spacing.md, top = Spacing.md, bottom = Spacing.md).semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box {
                        if (state.draft.isEmpty()) Text(placeholder(state), style = inputStyle, color = scheme.onSurface.copy(alpha = .62f))
                        inner()
                    }
                },
            )
            if (state.imagine) ImagineOptionChips(state, enabled = idle, onClick = onImagineOptions,
                modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs))
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xxs, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                RoundComposerButton(Icons.Outlined.Add, "Add photos and files", enabled = idle, onClick = onAttach)
                ModelPill(model = state.model, enabled = idle, onClick = onModel)
                Spacer(Modifier.weight(1f))
                // Empty field: voice mode. Once there is something to send (or a reply to stop) it becomes Send/Stop.
                if (state.typing || state.canSend || state.draft.isNotBlank()) SendButton(typing = state.typing, canSend = state.canSend, onSend = onSend, onStop = onStop)
                else VoiceModeButton(onVoiceMode)
            }
        }
    }
}

/**
 * Mode chips above the composer. On the empty Home: Run code (when the server offers it), Reasoning and
 * Search, plus X (Grok only, when offered) and any mode that is on; they share the row width, and switch to
 * tighter padding or icon-only chips rather than scroll when the labels don't fit. In a conversation only
 * the modes that are ON show (tap one to turn it off); the rest are in the + sheet. Shows/hides with a
 * short fade and size change (none when the system removes animations).
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
    home: Boolean = true,
) {
    val idle = !state.typing && !state.readingFiles
    val all = modeChipSpecs(state, onWebMode, onReasoning, onXSearch, onImagine, onRunCode, onBuild)
    val specs = if (home) all else all.filter { it.selected }.map { if (it.trailing == null) it.copy(trailing = Icons.Outlined.Close) else it }
    // Keep the last chips on screen while the row animates out (plain holder, not state: no recomposition loop).
    val last = remember { arrayOf(specs) }
    if (specs.isNotEmpty()) last[0] = specs
    val motion = rememberMotionAllowed()
    val duration = if (motion) 220 else 0
    AnimatedVisibility(visible = specs.isNotEmpty(), modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration), expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration), shrinkTowards = Alignment.Bottom)) {
        AdaptiveChipRow(specs.ifEmpty { last[0] }, fill = home, enabled = idle,
            modifier = Modifier.fillMaxWidth().animateContentSize(tween(duration)).padding(bottom = if (home) Spacing.md else Spacing.xs))
    }
}

/** Mode switches for the + sheet in a conversation: Run code (when offered), Reasoning, Search and X (Grok only). */
internal fun modeToggles(state: ChatUiState, onWebMode: (String?) -> Unit, onReasoning: (Boolean) -> Unit, onXSearch: (Boolean) -> Unit,
    onRunCode: (Boolean) -> Unit): List<ModeToggle> =
    modeChipSpecs(state, onWebMode, onReasoning, onXSearch, {}, onRunCode, {})
        .mapNotNull { spec ->
            val detail = when (spec.key) {
                "code" -> "Let the AI run Python code"
                "reasoning" -> "Think before answering"
                "search" -> "Search the web for this reply"
                "x" -> "Search posts on X with Grok"
                else -> return@mapNotNull null
            }
            ModeToggle(spec.label, detail, spec.icon, spec.selected) { spec.onClick() }
        }

/** One mode chip: label, icon, state and what a tap does. */
internal data class ModeChipSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val description: String,
    val trailing: ImageVector? = null,
    val onClick: () -> Unit,
)

private fun modeChipSpecs(
    state: ChatUiState,
    onWebMode: (String?) -> Unit,
    onReasoning: (Boolean) -> Unit,
    onXSearch: (Boolean) -> Unit,
    onImagine: (Boolean) -> Unit,
    onRunCode: (Boolean) -> Unit,
    onBuild: () -> Unit,
): List<ModeChipSpec> = buildList {
    // Build mode on: the chat's repository; tap to change it or turn Build off. (Build is started from the + sheet.)
    if (state.buildRepo != null) add(ModeChipSpec("build", "Build · ${state.buildRepo.substringAfter('/')}", Icons.Outlined.Build, true,
        "Build mode on, read-only, repository ${state.buildRepo}. Tap to change", Icons.Outlined.ExpandMore) { onBuild() })
    // Imagine on (started from the + sheet): tap to turn it off.
    if (state.imagine) add(ModeChipSpec("imagine", "Imagine", Icons.Outlined.Brush, true, "Imagine on. Tap to turn off", Icons.Outlined.Close) { onImagine(false) })
    // Run code: the provider runs Python in its own sandbox (shown when the backend offers it).
    if (state.codeConfig != null) add(ModeChipSpec("code", "Run code", Icons.Outlined.Terminal, state.runCode,
        when {
            state.runCode -> "Run code on: the AI can run Python for this reply. Tap to turn off"
            state.codeConfig.available(state.model) -> "Let the AI run Python code"
            state.codeConfig.enabled -> "Run code, not available for ${state.modelLabel}"
            else -> "Run code, turned off on the server"
        }) { onRunCode(!state.runCode) })
    add(ModeChipSpec("reasoning", "Reasoning", Icons.Outlined.Lightbulb, state.reasoning,
        when {
            state.reasoning -> "Reasoning on. Tap to turn off"
            state.reasoningSupported -> "Think before answering"
            else -> "Think before answering, not supported by ${state.modelLabel}"
        }) { onReasoning(!state.reasoning) })
    add(ModeChipSpec("search", "Search", Icons.Outlined.Language, state.webMode == "search",
        when {
            state.webMode == "search" -> "Search the web on. Tap to turn off"
            state.webSearchConfigured -> "Search the web"
            else -> "Search the web, needs setup"
        }) { onWebMode(if (state.webMode == "search") null else "search") })
    // X posts via xAI x_search: Grok only, off by default, billed by xAI per post returned.
    if (state.xSearchAvailable) add(ModeChipSpec("x", "X", Icons.Outlined.Tag, state.xSearch,
        if (state.xSearch) "X search on: Grok also searches posts on X. Tap to turn off" else "Search posts on X with Grok") { onXSearch(!state.xSearch) })
    if (state.webMode == "page") add(ModeChipSpec("page", "Read link", Icons.Outlined.Link, true, "Read link mode. Tap to turn off", Icons.Outlined.Close) { onWebMode(null) })
}

/** How much room a chip gets: label with normal padding, label with tight padding, or icon only. */
internal enum class ChipFit { Full, Tight, Icon }

/**
 * One row of chips, never cut off: tries labels with normal padding, then tighter padding, then icon-only
 * chips (below 360 dp the normal padding is skipped). With [fill] the chips share the full width. Scrolls
 * sideways only if even icon-only chips can't fit (many modes on at once on a very narrow screen).
 */
@Composable
private fun AdaptiveChipRow(specs: List<ModeChipSpec>, fill: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val pad = Spacing.lg
        val narrow = maxWidth < 360.dp
        val available = with(LocalDensity.current) { (maxWidth - pad * 2).roundToPx() }
        SubcomposeLayout(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = pad)) { constraints ->
            val gapPx = Spacing.sm.roundToPx()
            val gaps = gapPx * (specs.size - 1).coerceAtLeast(0)
            val fits = if (narrow) listOf(ChipFit.Tight, ChipFit.Icon) else ChipFit.entries
            var measurables: List<Measurable> = emptyList()
            var widths: List<Int> = emptyList()
            for (fit in fits) {
                measurables = subcompose(fit) { specs.forEach { spec -> key(spec.key) { ModeChip(spec, fit, enabled, height = if (fill) Sizes.modeChipHeight else Sizes.composerButton) } } }
                widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
                if (widths.sum() + gaps <= available) break
            }
            val natural = widths.sum() + gaps
            val extra = if (fill && measurables.isNotEmpty() && natural < available) (available - natural) / measurables.size else 0
            val placeables = measurables.mapIndexed { index, measurable ->
                val width = widths[index] + extra
                measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
            }
            val width = placeables.sumOf { it.width } + gaps
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                var x = 0
                placeables.forEach { it.placeRelative(x, 0); x += it.width + gapPx }
            }
        }
    }
}

@Composable
private fun ModeChip(spec: ModeChipSpec, fit: ChipFit, enabled: Boolean, height: Dp) =
    ModeChip(spec.label, spec.icon, spec.selected, enabled, spec.description, trailing = spec.trailing, fit = fit, height = height, onClick = spec.onClick)

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

/**
 * Pill chip for modes and Imagine options. Selected: silver fill on dark, ink fill on light, with a gold
 * icon, so it reads clearly against the unselected outlined chips without a new accent colour.
 */
@Composable
private fun ModeChip(label: String, icon: ImageVector, isSelected: Boolean, enabled: Boolean, description: String,
    trailing: ImageVector? = null, compact: Boolean = false, fit: ChipFit = ChipFit.Full, height: Dp = Sizes.modeChipHeight, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = DuaTheme.colors
    val content = if (isSelected) colors.onChipSelected else scheme.onSurface.copy(alpha = .88f)
    Surface(
        onClick = onClick, enabled = enabled, shape = DuaShape.chip,
        color = if (isSelected) colors.chipSelected else colors.composer,
        contentColor = content,
        border = BorderStroke(1.dp, if (isSelected) colors.chipSelected else colors.composerBorder),
        modifier = Modifier.semantics { contentDescription = description; selected = isSelected; role = Role.Button },
    ) {
        val horizontal = when { compact -> Spacing.md; fit == ChipFit.Full -> Spacing.md; fit == ChipFit.Tight -> Spacing.sm + Spacing.xxs; else -> Spacing.md }
        Row(Modifier.heightIn(min = if (compact) 32.dp else height).padding(horizontal = horizontal),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isSelected) colors.chipSelectedIcon else content,
                modifier = Modifier.size(if (compact) Sizes.iconTiny else Sizes.iconSmall))
            if (fit != ChipFit.Icon) {
                Text(label, maxLines = 1, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.padding(start = when { compact -> Spacing.xs; fit == ChipFit.Tight -> Spacing.xs + Spacing.xxs; else -> Spacing.sm }))
                if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconTiny))
            }
        }
    }
}

/**
 * Round button in the composer (+, mic, send): a 36-38 dp circle drawn inside a 48 dp touch target, so the
 * whole 48 dp area takes the tap while the visual size matches the design.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundComposerButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit,
    visualSize: Dp = Sizes.composerButton, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .86f else .38f), iconSize: Dp = Sizes.icon,
    onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null) {
    Box(
        Modifier.minimumInteractiveComponentSize().size(Sizes.touchTarget)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = Sizes.touchTarget / 2),
                enabled = enabled, role = Role.Button, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visualSize).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
        }
    }
}

/** Label for the composer's model pill: "Auto" for the default model (Dua), otherwise the model's short name. */
internal fun modelPillLabel(model: String): String = if (model == AUTO_MODEL) "Auto" else model

/** The default model; the pill shows it as "Auto". */
internal const val AUTO_MODEL = "Dua"

/**
 * Model pill (replaces the old top-bar "Dua ⌄" title): shows "Auto" or the chosen model with a chevron and
 * opens the model picker. Long-press (or hover) shows a short hint; TalkBack reads the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPill(model: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = modelPillLabel(model)
    val hint = if (model == AUTO_MODEL) "Model: Auto (Dua). Tap to choose a model" else "Model: $model. Tap to choose a model"
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState()) {
        Surface(onClick = onClick, enabled = enabled, shape = DuaShape.chip, color = scheme.surfaceContainerHighest, contentColor = scheme.onSurface,
            modifier = Modifier.padding(start = Spacing.xs).semantics { contentDescription = hint; role = Role.Button }) {
            Row(Modifier.height(Sizes.composerButton).padding(start = Spacing.md, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(DuaIcons.Sparkle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Text(label, maxLines = 1, color = scheme.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = Spacing.sm).widthIn(max = 120.dp), overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = scheme.onSurface.copy(alpha = .86f),
                    modifier = Modifier.padding(start = Spacing.xxs).size(Sizes.iconSmall))
            }
        }
    }
}

/** Round send button: dimmed while there is nothing to send; a stop button while a reply streams. 48 dp touch target. */
@Composable
private fun VoiceModeButton(onClick: () -> Unit) {
    val colors = DuaTheme.colors
    RoundComposerButton(Icons.Outlined.GraphicEq, "Voice mode", enabled = true, onClick = onClick, visualSize = Sizes.sendButton,
        container = colors.action, contentColor = colors.onAction, iconSize = Sizes.iconSmall + 2.dp)
}

@Composable
private fun SendButton(typing: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val colors = DuaTheme.colors
    val active = typing || canSend
    RoundComposerButton(if (typing) Icons.Filled.Stop else Icons.Filled.ArrowUpward, if (typing) "Stop response" else "Send message",
        enabled = active, onClick = { if (typing) onStop() else onSend() }, visualSize = Sizes.sendButton,
        container = if (active) colors.action else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) colors.onAction else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
        iconSize = Sizes.iconSmall + 2.dp)
}
