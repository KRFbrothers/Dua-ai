package com.dua.ai.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.dua.ai.ui.components.pressFeedback
import com.dua.ai.ui.theme.DuaTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dua.ai.R
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
import com.dua.ai.ui.theme.ComposerInputStyle
import com.dua.ai.ui.theme.HomeComposerInputStyle
import com.dua.ai.ui.theme.ComposerModeStyle
import com.dua.ai.ui.theme.ComposerSizes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.ui.theme.HomeModelLabelStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.rememberMotionAllowed

const val AUTO_MODEL = "Auto"

internal enum class HomeMode { Chat, Task }

/** Compact visual pills retain full 48 dp selectable touch targets. */
@Composable
internal fun HomeModeSwitcher(mode: HomeMode, onSelect: (HomeMode) -> Unit, enabled: Boolean, floating: Boolean = false) {
    val c = HomeTheme.colors
    val duration = if (rememberMotionAllowed()) DuaMotion.DurationFast else 0
    Row(Modifier.heightIn(min = Sizes.touchTarget).selectableGroup().testTag("home-mode-switcher"), verticalAlignment = Alignment.CenterVertically) {
        HomeMode.entries.forEach { tab ->
            val selected = mode == tab
            val fill by animateColorAsState(if (selected) (if (floating) c.segmentFloatSelected else c.segmentSelected) else Color.Transparent,
                tween(duration), label = "Home tab fill")
            val text by animateColorAsState(if (selected) c.segmentSelectedText else c.segmentText,
                tween(duration), label = "Home tab text")
            Box(Modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
                .clip(ComposerSizes.tabShape)
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(min = HomeSizes.segmentTab).clip(ComposerSizes.tabShape)
                    .background(fill)
                    .then(if (selected) Modifier.border(HomeSizes.barBorder, c.barBorder, ComposerSizes.tabShape) else Modifier)
                    .padding(horizontal = HomeSizes.segmentTabPaddingH), contentAlignment = Alignment.Center) {
                    Text(tab.name, style = ComposerModeStyle,
                        color = text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Composer(
    modifier: Modifier = Modifier,
    state: Any? = null,
    focusRequester: FocusRequester? = null,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onDraftChange: (String) -> Unit = onValueChange,
    onSend: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onClearAttachments: () -> Unit = {},
    onMic: () -> Unit = {},
    onVoiceClick: () -> Unit = onMic,
    onVoiceMode: () -> Unit = onVoiceClick,
    onModelClick: () -> Unit = {},
    onModel: (String) -> Unit = {},
    onStop: () -> Unit = {},
    onCancelUpload: () -> Unit = {},
    onImagineOptions: () -> Unit = {},
    modeToggles: Any? = null,
    onTextFieldFocus: ((Boolean) -> Unit)? = null,
    onTextFieldBounds: ((Rect) -> Unit)? = null,
    currentModel: String = "Auto",
    showModelSelector: Boolean = true,
    placeholder: String = "Ask Dua AI...",
    sendEnabled: Boolean = true,
    aboveInput: (@Composable () -> Unit)? = null,
    attachments: List<ChatAttachment> = (state as? ChatUiState)?.attachments.orEmpty(),
    uploads: List<PendingUpload> = (state as? ChatUiState)?.uploads.orEmpty(),
    onRemoveAttachment: (Int) -> Unit = {},
    /** On-device "Remove background" for image attachments; null hides it. */
    onRemoveAttachmentBackground: ((Int) -> Unit)? = null,
    /** "Summarize" / "Rewrite" in the message box's text menu (long-press); null hides them. A null task = choose a rewrite style. */
    writingTools: ((com.dua.ai.ondevice.WritingTask?) -> Unit)? = null,
    onRemoveUpload: (String) -> Unit = {},
    attachmentPopup: @Composable () -> Unit = {},
    /** Inline dictation (chat). Null = the mic calls [onMic] (opens the Voice page). */
    dictation: ChatDictation? = null,
    /** [value] is the source of truth (chat). Defaults to true whenever [state] is given. */
    controlled: Boolean = state != null,
    typing: Boolean = (state as? ChatUiState)?.typing == true,
    uploading: Boolean = uploads.any { it.uploading },
    /** Spacious reference layout is limited to Home, preserving feature and project composers. */
    homeLayout: Boolean = false,
    /** Empty Home only; keeps feature and conversation composers on their existing surface. */
    homePill: Boolean = false,
    /** Conversation look: translucent glass bar, faint animated gradient behind it, focus pop (ComposerGlass.kt). */
    glass: Boolean = false,
    /** Prompt chips shown above an empty glass composer; tapping one calls [onSuggestion]. */
    suggestions: List<ComposerSuggestion> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    /** Image button next to the mic (glass composer); null hides it. */
    onImage: (() -> Unit)? = null,
    imageSelected: Boolean = false,
) {
    var localText by remember { mutableStateOf(value) }
    var inputFocused by remember { mutableStateOf(false) }
    val activeText = if (controlled) value else if (value.isNotEmpty()) value else localText

    val c = HomeTheme.colors
    val motion = rememberMotionAllowed()
    val border by animateColorAsState(if (inputFocused) MaterialTheme.colorScheme.primary.copy(alpha = .65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (homePill) .45f else .35f),
        if (motion) DuaMotion.fast() else snap(), label = "Home input focus")
    val hasAttachments = attachments.isNotEmpty() || uploads.isNotEmpty()
    val inputStyle = if (homeLayout) HomeComposerInputStyle else ComposerInputStyle
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Keep idle Chat/Task geometry stable even when their hints have different lengths.
    val minimumInputWidth = remember(textMeasurer, inputStyle, density) {
        textMeasurer.measure(AnnotatedString("Ask Dua AI..."), style = inputStyle,
            softWrap = false, maxLines = 1).size.width
    }
    val hasContent = activeText.isNotBlank() || hasAttachments
    val expanded = (!homeLayout && hasContent) || hasAttachments || '\n' in activeText
    val canSubmit = hasContent && sendEnabled && !typing && !uploading
    fun submit() {
        if (canSubmit) {
            onSend(activeText)
            // The state owner clears controlled drafts only after accepting the request.
            // Validation/preparation may reject or defer sending without consuming the text.
            if (!controlled) {
                localText = ""
                onValueChange("")
                onDraftChange("")
            }
        }
    }

    // Keep the input spacious on regular phones and leave room for controls on narrow windows.
    BoxWithConstraints(modifier.widthIn(max = HomeSizes.composerMaxWidth).fillMaxWidth()) {
        val keyboardOpen = homeLayout && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val margin = (if (homeLayout && !keyboardOpen) HomeSizes.composerHorizontalMargin(maxWidth) else HomeSizes.composerCompactMarginH)
            // The image shortcut needs another 48 dp slot. Use the glass card's own margin
            // rather than also inheriting the empty Home's wider hero margins.
            .let { if (glass) ComposerGlass.MarginH else it }
        // Large text gets its own input row before the placeholder loses space to the controls.
        // Keep the field in the same Layout slot so resizing never discards focus or the draft.
        val inlineTextWidth = maxWidth - margin * 2 - HomeSizes.barPaddingH * 2 - Sizes.touchTarget * (if (onImage != null) 4 else 3) - Spacing.sm * 2
        val stacked = expanded || (homeLayout && maxOf(
            minimumInputWidth,
            textMeasurer.measure(AnnotatedString(placeholder), style = inputStyle, softWrap = false, maxLines = 1).size.width,
            textMeasurer.measure(AnnotatedString(activeText), style = inputStyle, softWrap = false, maxLines = 1).size.width,
        ) > with(LocalDensity.current) { inlineTextWidth.roundToPx() })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = Spacing.sm)
                // Home owns the union of IME/navigation insets around the whole surface.
                .then(if (homeLayout) Modifier else Modifier.navigationBarsPadding().imePadding())
        ) {
            if (glass && !typing && activeText.isEmpty() && !hasAttachments && dictation?.listening != true) {
                ComposerSuggestionChips(suggestions, onSuggestion)
                if (suggestions.isNotEmpty()) Spacer(Modifier.height(Spacing.xs))
            }
            // Standalone composers can retain this row; Home opens the model sheet from its header.
            if (showModelSelector && !homePill) Row(
                Modifier.align(Alignment.End)
                    .heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
                    .clip(HomeShapes.segmentTab)
                    .clickable(role = Role.Button, onClick = onModelClick)
                    .semantics(mergeDescendants = true) { contentDescription = "Model, $currentModel" }
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(currentModel, style = HomeModelLabelStyle, color = c.modelLabel, maxLines = 1)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = c.modelLabel, modifier = Modifier.size(Sizes.iconTiny))
            }
            if (aboveInput != null) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { aboveInput() }
                Spacer(Modifier.height(Spacing.md))
            }
            val shape = if (homePill) HomeShapes.homePill else if (homeLayout && !stacked) HomeShapes.homeBar else if (glass) ComposerGlass.Shape else if (stacked) ComposerSizes.expandedShape else HomeShapes.bar
            // Keep the rounded surface/shadow outside animateContentSize's rectangular clip.
            // Only the inner layout resizes, so its shadow can feather beyond all four edges.
            ComposerSurface(homePill, glass, homeLayout, shape, border,
                Modifier.fillMaxWidth().testTag("composer-container").composerFocusPop(inputFocused, glass)) {
                Column(Modifier.fillMaxWidth()
                    .animateContentSize(animationSpec = if (motion) DuaMotion.gentle() else snap())
                    .padding(horizontal = HomeSizes.barPaddingH, vertical = when {
                        homePill && !stacked && !keyboardOpen -> HomeSizes.pillIdlePaddingV
                        glass -> ComposerGlass.PaddingV
                        homeLayout && !stacked && !keyboardOpen -> HomeSizes.composerIdlePaddingV
                        homeLayout && !stacked -> HomeSizes.composerKeyboardPaddingV
                        else -> Spacing.sm
                    })) {
                    if (hasAttachments) AttachmentBar(attachments, uploads, !typing, onRemoveAttachment, onRemoveUpload, onRemoveAttachmentBackground)
                    if (dictation?.listening == true) {
                        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                            DictationBar(dictation.level, dictation.cancel, dictation.confirm)
                            if (typing) ComposerAction(Icons.Rounded.Stop, "Stop response", onClick = onStop)
                        }
                    } else ComposerFields(expanded = stacked,
                        input = {
                            val view = androidx.compose.ui.platform.LocalView.current
                            val currentText by rememberUpdatedState(activeText)
                            val currentTools by rememberUpdatedState(writingTools)
                            val platformToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
                            val toolbar = remember(view) {
                                DuaTextToolbar(view) {
                                    val tools = currentTools
                                    if (tools == null || currentText.isBlank()) emptyList()
                                    else listOf(TextToolbarExtra("Summarize") { tools(com.dua.ai.ondevice.WritingTask.Summarize) },
                                        TextToolbarExtra("Rewrite") { tools(null) })
                                }
                            }
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalTextToolbar provides
                                if (writingTools != null) toolbar else platformToolbar) {
                            BasicTextField(
                                value = activeText,
                                onValueChange = { localText = it; onValueChange(it); onDraftChange(it) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.sm)
                                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                                    .onFocusChanged { inputFocused = it.isFocused; onTextFieldFocus?.invoke(it.isFocused) }
                                    .onGloballyPositioned { onTextFieldBounds?.invoke(it.boundsInRoot()) }
                                    .semantics { contentDescription = "Message" },
                                textStyle = inputStyle.copy(color = c.input),
                                cursorBrush = SolidColor(c.input),
                                singleLine = !stacked,
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                decorationBox = { innerTextField ->
                                    Box(Modifier.heightIn(min = Sizes.touchTarget), contentAlignment = Alignment.CenterStart) {
                                        if (activeText.isEmpty()) Text(placeholder, style = inputStyle, color = c.placeholder,
                                            modifier = if (homeLayout) Modifier.fillMaxWidth() else Modifier,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        innerTextField()
                                    }
                                }
                            )
                            }
                        },
                        attach = {
                            Box {
                                IconButton(onClick = onAttach, modifier = Modifier.size(Sizes.touchTarget)) {
                                    Icon(if (homePill) Icons.Default.Add else if (homeLayout) ImageVector.vectorResource(R.drawable.home_add) else Icons.Default.Add,
                                        "Add photos and files", tint = c.barIcon,
                                        modifier = Modifier.size(if (homeLayout) HomeSizes.referenceIcon else HomeSizes.plusIcon))
                                }
                                attachmentPopup()
                            }
                        },
                        controls = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onImage != null) IconButton(onClick = onImage, modifier = Modifier.size(Sizes.touchTarget)) {
                                    Icon(Icons.Outlined.Image, if (imageSelected) "Imagine on" else "Create image",
                                        tint = if (imageSelected) c.voice else c.barIcon, modifier = Modifier.size(HomeSizes.micIcon))
                                }
                                IconButton(onClick = { dictation?.start?.invoke() ?: onMic() }, modifier = Modifier.size(Sizes.touchTarget)) {
                                    Icon(if (homePill) Icons.Outlined.MicNone else if (homeLayout) ImageVector.vectorResource(R.drawable.ls_microphone_outline) else Icons.Outlined.MicNone,
                                        "Dictate", tint = c.barIcon, modifier = Modifier.size(if (homeLayout) HomeSizes.referenceIcon else HomeSizes.micIcon))
                                }
                                val (icon, label, action) = when {
                                    typing -> Triple(Icons.Rounded.Stop, "Stop response", { onStop() })
                                    uploading -> Triple(Icons.Default.Close, "Cancel upload", { onCancelUpload() })
                                    hasContent -> Triple(Icons.Default.ArrowUpward, "Send message", { submit() })
                                    else -> Triple(if (homePill) Icons.Outlined.GraphicEq else ImageVector.vectorResource(R.drawable.ls_live_wave), "Voice mode", { onVoiceMode() })
                                }
                                if (homePill) FilledIconButton(onClick = action,
                                    enabled = typing || uploading || !hasContent || canSubmit,
                                    modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = label },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    )) {
                                    Icon(icon, null, modifier = Modifier.size(Sizes.icon))
                                } else ComposerAction(icon, label, enabled = typing || uploading || !hasContent || canSubmit, onClick = action,
                                    iconSize = if (homeLayout && !typing && !hasContent) HomeSizes.referenceIcon else HomeSizes.voiceIcon)
                            }
                        })
                }
            }
        }
    }
}

/** Shared fields/callbacks; only empty Home opts into the Material pill surface. */
@Composable
private fun ComposerSurface(homePill: Boolean, glass: Boolean, homeLayout: Boolean, shape: Shape,
    border: Color, modifier: Modifier, content: @Composable () -> Unit) {
    val c = HomeTheme.colors
    if (homePill) Surface(modifier = modifier, shape = shape, color = c.bar,
        border = BorderStroke(HomeSizes.barBorder, border), shadowElevation = HomeSizes.pillElevation,
        tonalElevation = HomeSizes.noElevation, content = content)
    else Box(modifier
        .shadow(if (glass) ComposerGlass.Elevation else if (homeLayout) HomeSizes.composerElevation else HomeSizes.noElevation, shape, clip = false)
        .clip(shape).background(c.bar).background(DuaTheme.colors.floatingSurface).composerAurora(glass)
        .border(HomeSizes.barBorder, border, shape)) { content() }
}

@Composable
private fun ComposerAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit, iconSize: Dp = HomeSizes.voiceIcon) {
    val c = HomeTheme.colors
    val motion = com.dua.ai.ui.theme.rememberMotionAllowed()
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.size(Sizes.touchTarget).clip(HomeShapes.round).clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(ComposerSizes.actionButton).pressFeedback(interaction).clip(HomeShapes.round).background(c.voice), contentAlignment = Alignment.Center) {
            if (motion) AnimatedContent(icon, contentAlignment = Alignment.Center,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f))
                }, label = "Composer action") { target ->
                Icon(target, null, tint = c.onVoice, modifier = Modifier.size(iconSize))
            } else Icon(icon, null, tint = c.onVoice, modifier = Modifier.size(iconSize))
        }
    }
}

/** Keeps the same text-field node/focus while moving between capsule and two-row card. */
@Composable
private fun ComposerFields(expanded: Boolean, input: @Composable () -> Unit, attach: @Composable () -> Unit, controls: @Composable () -> Unit) {
    Layout(content = {
        Box { input() }
        Box { attach() }
        Box { controls() }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val plus = measurables[1].measure(loose)
        val right = measurables[2].measure(loose)
        val actionHeight = maxOf(plus.height, right.height)
        val fieldWidth = if (expanded) constraints.maxWidth else (constraints.maxWidth - plus.width - right.width).coerceAtLeast(0)
        // Reserve the action row first when IME/font scaling leaves less room than six lines.
        val fieldHeight = if (expanded) (constraints.maxHeight - actionHeight).coerceAtLeast(0) else constraints.maxHeight
        val field = measurables[0].measure(loose.copy(minWidth = fieldWidth, maxWidth = fieldWidth, maxHeight = fieldHeight))
        val height = if (expanded) field.height + actionHeight else maxOf(field.height, actionHeight)
        layout(constraints.maxWidth, height) {
            field.placeRelative(if (expanded) 0 else plus.width, if (expanded) 0 else (height - field.height) / 2)
            plus.placeRelative(0, if (expanded) field.height else (height - plus.height) / 2)
            right.placeRelative(constraints.maxWidth - right.width, if (expanded) field.height else (height - right.height) / 2)
        }
    }
}
