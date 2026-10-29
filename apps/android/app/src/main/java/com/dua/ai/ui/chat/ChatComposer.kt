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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
internal fun HomeModeSwitcher(mode: HomeMode, onSelect: (HomeMode) -> Unit, enabled: Boolean) {
    val c = HomeTheme.colors
    val duration = if (rememberMotionAllowed()) DuaMotion.DurationFast else 0
    Row(Modifier.heightIn(min = Sizes.touchTarget).selectableGroup().testTag("home-mode-switcher"), verticalAlignment = Alignment.CenterVertically) {
        HomeMode.entries.forEach { tab ->
            val selected = mode == tab
            val fill by animateColorAsState(if (selected) c.segmentSelected else Color.Transparent,
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
    readingFiles: Boolean = (state as? ChatUiState)?.readingFiles == true,
    uploading: Boolean = uploads.any { it.uploading },
    /** Spacious reference layout is limited to Home, preserving feature and project composers. */
    homeLayout: Boolean = false,

) {
    var localText by remember { mutableStateOf(value) }
    var inputFocused by remember { mutableStateOf(false) }
    val activeText = if (controlled) value else if (value.isNotEmpty()) value else localText

    val c = HomeTheme.colors
    val motion = rememberMotionAllowed()
    val border by animateColorAsState(if (inputFocused) MaterialTheme.colorScheme.primary.copy(alpha = .65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f),
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
    val canSubmit = hasContent && sendEnabled && !typing && !uploading && !readingFiles
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
        // Large text gets its own input row before the placeholder loses space to the controls.
        // Keep the field in the same Layout slot so resizing never discards focus or the draft.
        val inlineTextWidth = maxWidth - margin * 2 - HomeSizes.barPaddingH * 2 - Sizes.touchTarget * 3 - Spacing.sm * 2
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
            // Standalone composers can retain this row; Home opens the model sheet from its header.
            if (showModelSelector && !homeLayout) Row(
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
            val shape = if (homeLayout && !stacked) HomeShapes.homeBar else if (stacked) ComposerSizes.expandedShape else HomeShapes.bar
            // Keep the rounded surface/shadow outside animateContentSize's rectangular clip.
            // Only the inner layout resizes, so its shadow can feather beyond all four edges.
            Box(Modifier.fillMaxWidth()
                .testTag("composer-container")
                .semantics {
                    stateDescription = when {
                        typing -> "Responding"
                        uploading -> "Uploading files"
                        readingFiles -> "Reading files"
                        dictation?.listening == true -> "Listening"
                        hasContent && !sendEnabled -> "Send unavailable"
                        hasContent -> "Ready to send"
                        else -> "Ready for voice or text"
                    }
                }
                .shadow(if (homeLayout) HomeSizes.composerElevation else HomeSizes.noElevation, shape, clip = false)
                .clip(shape).background(c.bar).background(DuaTheme.colors.floatingSurface).border(HomeSizes.barBorder, border, shape)) {
                Column(Modifier.fillMaxWidth().testTag("supplied-composer")
                    .animateContentSize(animationSpec = if (motion) DuaMotion.gentle() else snap())
                    .padding(horizontal = HomeSizes.barPaddingH, vertical = when {
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
                                IconButton(onClick = onAttach, enabled = !typing && !readingFiles,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = c.barIcon), modifier = Modifier.size(Sizes.touchTarget)) {
                                    Icon(if (homeLayout) ImageVector.vectorResource(R.drawable.home_add) else Icons.Default.Add,
                                        "Add photos and files", tint = LocalContentColor.current,
                                        modifier = Modifier.size(if (homeLayout) HomeSizes.referenceIcon else HomeSizes.plusIcon))
                                }
                                attachmentPopup()
                            }
                        },
                        controls = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { dictation?.start?.invoke() ?: onMic() }, enabled = !typing && !uploading && !readingFiles,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = c.barIcon), modifier = Modifier.size(Sizes.touchTarget)) {
                                    Icon(if (homeLayout) ImageVector.vectorResource(R.drawable.ls_microphone_outline) else Icons.Outlined.MicNone,
                                        "Dictate", tint = LocalContentColor.current, modifier = Modifier.size(if (homeLayout) HomeSizes.referenceIcon else HomeSizes.micIcon))
                                }
                                val (icon, label, action) = when {
                                    typing -> Triple(Icons.Rounded.Stop, "Stop response", { onStop() })
                                    uploading -> Triple(Icons.Default.Close, "Cancel upload", { onCancelUpload() })
                                    hasContent -> Triple(Icons.Default.ArrowUpward, "Send message", { submit() })
                                    else -> Triple(ImageVector.vectorResource(R.drawable.ls_live_wave), "Voice mode", { onVoiceMode() })
                                }
                                ComposerAction(icon, label, enabled = typing || uploading || (!readingFiles && (!hasContent || canSubmit)), onClick = action,
                                    status = if (readingFiles && !typing && !uploading) "Reading files" else null,
                                    iconSize = if (homeLayout && !typing && !hasContent) HomeSizes.referenceIcon else HomeSizes.voiceIcon)
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun ComposerAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit,
    iconSize: Dp = HomeSizes.voiceIcon, status: String? = null) {
    val c = HomeTheme.colors
    val motion = com.dua.ai.ui.theme.rememberMotionAllowed()
    val interaction = remember { MutableInteractionSource() }
    val colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.voice, contentColor = c.onVoice)
    val fill by animateColorAsState(if (enabled) colors.containerColor else colors.disabledContainerColor,
        if (motion) DuaMotion.fast() else snap(), label = "Composer action fill")
    val ink by animateColorAsState(if (enabled) colors.contentColor else colors.disabledContentColor,
        if (motion) DuaMotion.fast() else snap(), label = "Composer action ink")
    Box(Modifier.size(Sizes.touchTarget).clip(HomeShapes.round).clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label; if (status != null) stateDescription = status }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(ComposerSizes.actionButton).pressFeedback(interaction).clip(HomeShapes.round).background(fill), contentAlignment = Alignment.Center) {
            if (motion) AnimatedContent(icon, contentAlignment = Alignment.Center,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f))
                }, label = "Composer action") { target ->
                Icon(target, null, tint = ink, modifier = Modifier.size(iconSize))
            } else Icon(icon, null, tint = ink, modifier = Modifier.size(iconSize))
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
