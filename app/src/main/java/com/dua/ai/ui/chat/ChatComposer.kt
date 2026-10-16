package com.dua.ai.ui.chat

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
import com.dua.ai.ui.theme.ComposerInputStyle
import com.dua.ai.ui.theme.ComposerModeStyle
import com.dua.ai.ui.theme.ComposerSizes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.ui.theme.HomeModelLabelStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

const val AUTO_MODEL = "Auto"

internal enum class HomeMode { Chat, Task }

/** Compact visual pills retain full 48 dp selectable touch targets. */
@Composable
internal fun HomeModeSwitcher(mode: HomeMode, onSelect: (HomeMode) -> Unit, enabled: Boolean) {
    val c = HomeTheme.colors
    Row(Modifier.heightIn(min = Sizes.touchTarget).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        HomeMode.entries.forEach { tab ->
            val selected = mode == tab
            Box(Modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(min = HomeSizes.segmentTab).clip(ComposerSizes.tabShape)
                    .background(if (selected) c.segmentSelected else Color.Transparent)
                    .then(if (selected) Modifier.border(HomeSizes.barBorder, c.barBorder, ComposerSizes.tabShape) else Modifier)
                    .padding(horizontal = HomeSizes.segmentTabPaddingH), contentAlignment = Alignment.Center) {
                    Text(tab.name, style = ComposerModeStyle,
                        color = if (selected) c.segmentSelectedText else c.segmentText, maxLines = 1)
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
    onRemoveUpload: (String) -> Unit = {},
    attachmentPopup: @Composable () -> Unit = {},
    /** Inline dictation (chat). Null = the mic calls [onMic] (opens the Voice page). */
    dictation: ChatDictation? = null,
    /** [value] is the source of truth (chat). Defaults to true whenever [state] is given. */
    controlled: Boolean = state != null,
    typing: Boolean = (state as? ChatUiState)?.typing == true,
    uploading: Boolean = uploads.any { it.uploading },
) {
    var localText by remember { mutableStateOf(value) }
    val activeText = if (controlled) value else if (value.isNotEmpty()) value else localText

    val c = HomeTheme.colors
    val hasAttachments = attachments.isNotEmpty() || uploads.isNotEmpty()
    val hasContent = activeText.isNotBlank() || hasAttachments
    val expanded = hasContent || '\n' in activeText
    val canSubmit = hasContent && sendEnabled && !typing && !uploading
    fun submit() {
        if (canSubmit) {
            onSend(activeText)
            localText = ""
            onValueChange("")
            onDraftChange("")
        }
    }

    // Reference shell's bottom pill bar: + | Ask Dua AI | mic | purple voice/send. 16/8 dp outer margin as in its Column.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Standalone composers can retain this row; Home opens the model sheet from its header.
        if (showModelSelector) Row(
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
        val shape = if (expanded) ComposerSizes.expandedShape else HomeShapes.bar
        Column(Modifier.fillMaxWidth().animateContentSize().testTag("composer-container")
            .clip(shape).background(c.bar).border(HomeSizes.barBorder, c.barBorder, shape)
            .padding(horizontal = HomeSizes.barPaddingH, vertical = Spacing.sm)) {
            if (hasAttachments) AttachmentBar(attachments, uploads, !typing, onRemoveAttachment, onRemoveUpload)
            if (dictation?.listening == true) {
                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                    DictationBar(dictation.level, dictation.cancel, dictation.confirm)
                }
            } else ComposerFields(expanded = expanded,
                input = {
                    BasicTextField(
                        value = activeText,
                        onValueChange = { localText = it; onValueChange(it); onDraftChange(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.sm)
                            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                            .onFocusChanged { onTextFieldFocus?.invoke(it.isFocused) }
                            .onGloballyPositioned { onTextFieldBounds?.invoke(it.boundsInRoot()) }
                            .semantics { contentDescription = "Message" },
                        textStyle = ComposerInputStyle.copy(color = c.input),
                        cursorBrush = SolidColor(c.input),
                        singleLine = !expanded,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        decorationBox = { innerTextField ->
                            Box(Modifier.heightIn(min = Sizes.touchTarget), contentAlignment = Alignment.CenterStart) {
                                if (activeText.isEmpty()) Text(placeholder, style = ComposerInputStyle, color = c.placeholder,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                innerTextField()
                            }
                        }
                    )
                },
                attach = {
                    Box {
                        IconButton(onClick = onAttach, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(Icons.Default.Add, "Add photos and files", tint = c.barIcon, modifier = Modifier.size(HomeSizes.plusIcon))
                        }
                        attachmentPopup()
                    }
                },
                controls = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dictation?.start?.invoke() ?: onMic() }, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(Icons.Outlined.MicNone, "Dictate", tint = c.barIcon, modifier = Modifier.size(HomeSizes.micIcon))
                        }
                        val (icon, label, action) = when {
                            typing -> Triple(Icons.Rounded.Stop, "Stop response", { onStop() })
                            hasContent -> Triple(Icons.Default.ArrowUpward, "Send message", { submit() })
                            uploading -> Triple(Icons.Default.Close, "Cancel upload", { onCancelUpload() })
                            else -> Triple(ImageVector.vectorResource(R.drawable.ls_live_wave), "Voice mode", { onVoiceMode() })
                        }
                        Box(Modifier.size(Sizes.touchTarget)
                            .clickable(enabled = typing || !hasContent || canSubmit,
                                role = Role.Button, onClick = action)
                            .semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(ComposerSizes.actionButton).clip(HomeShapes.round).background(c.voice), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = c.onVoice, modifier = Modifier.size(HomeSizes.voiceIcon))
                            }
                        }
                    }
                })
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
