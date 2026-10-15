package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.ComposerTextStyle
import com.dua.ai.ui.theme.HomeModelLabelStyle
import com.dua.ai.ui.theme.HomeSegmentStyle
import com.dua.ai.ui.theme.HomeSegmentSelectedStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

const val AUTO_MODEL = "Auto"

internal enum class HomeMode { Chat, Task }

/** Same track, tab, text and selection colors as the former top-bar pill. */
@Composable
internal fun HomeModeSwitcher(mode: HomeMode, onSelect: (HomeMode) -> Unit, enabled: Boolean) {
    val c = HomeTheme.colors
    val inset = HomeSizes.segmentTrackInset
    Row(Modifier.heightIn(min = Sizes.touchTarget).drawBehind {
        val h = (HomeSizes.segmentTab + inset * 2).toPx()
        drawRoundRect(c.segmentTrack, Offset(0f, (size.height - h) / 2), Size(size.width, h),
            CornerRadius(HomeSizes.segmentTab.toPx() / 2 + inset.toPx()))
    }.padding(horizontal = inset).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        HomeMode.entries.forEach { tab ->
            val selected = mode == tab
            Box(Modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(min = HomeSizes.segmentTab).clip(HomeShapes.segmentTab)
                    .background(if (selected) c.segmentSelected else Color.Transparent)
                    .padding(horizontal = HomeSizes.segmentTabPaddingH), contentAlignment = Alignment.Center) {
                    Text(tab.name, style = if (selected) HomeSegmentSelectedStyle else HomeSegmentStyle,
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
    placeholder: String = "Ask Dua AI",
    sendEnabled: Boolean = true,
    aboveInput: @Composable () -> Unit = {},
    attachmentPopup: @Composable () -> Unit = {},
    /** Inline dictation (chat). Null = the mic calls [onMic] (opens the Voice page). */
    dictation: ChatDictation? = null,
    /** [value] is the source of truth (chat). Defaults to true whenever [state] is given. */
    controlled: Boolean = state != null,
    typing: Boolean = (state as? ChatUiState)?.typing == true,
    uploading: Boolean = (state as? ChatUiState)?.uploads?.any { it.uploading } == true,
) {
    var localText by remember { mutableStateOf(value) }
    val activeText = if (controlled) value else if (value.isNotEmpty()) value else localText

    val c = HomeTheme.colors
    fun submit() {
        if (activeText.isNotBlank() && sendEnabled) {
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
        Box(Modifier.align(Alignment.CenterHorizontally)) { aboveInput() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HomeShapes.bar)
                .background(c.bar)
                .border(HomeSizes.barBorder, c.barBorder, HomeShapes.bar)
                .padding(horizontal = HomeSizes.barPaddingH, vertical = HomeSizes.barPaddingV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dictation?.listening == true) { DictationBar(dictation.level, dictation.cancel, dictation.confirm); return@Row }
            Box { IconButton(onClick = onAttach, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Default.Add, contentDescription = "Add photos and files", tint = c.barIcon,
                    modifier = Modifier.size(HomeSizes.plusIcon))
            }; attachmentPopup() }
            BasicTextField(
                value = activeText,
                onValueChange = {
                    localText = it
                    onValueChange(it)
                    onDraftChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget)
                    .padding(horizontal = Spacing.sm)
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                    .onFocusChanged { onTextFieldFocus?.invoke(it.isFocused) }
                    .onGloballyPositioned { onTextFieldBounds?.invoke(it.boundsInRoot()) }
                    .semantics { contentDescription = "Message" },
                textStyle = ComposerTextStyle.copy(color = c.input),
                cursorBrush = SolidColor(c.input),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { innerTextField ->
                    Box(Modifier.heightIn(min = Sizes.touchTarget), contentAlignment = Alignment.CenterStart) {
                        if (activeText.isEmpty()) Text(placeholder, style = ComposerTextStyle, color = c.placeholder)
                        innerTextField()
                    }
                }
            )
            IconButton(onClick = { dictation?.start?.invoke() ?: onMic() }, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Default.Mic, contentDescription = "Dictate", tint = c.barIcon, modifier = Modifier.size(HomeSizes.micIcon))
            }
            val (icon, label, action) = when {
                typing -> Triple(Icons.Default.Stop, "Stop response", { onStop() })
                uploading && activeText.isBlank() -> Triple(Icons.Default.Close, "Cancel upload", { onCancelUpload() })
                activeText.isNotBlank() -> Triple(Icons.Default.ArrowUpward, "Send message", { submit() })
                else -> Triple(ImageVector.vectorResource(R.drawable.ls_live_wave), "Voice mode", { onVoiceMode() })
            }
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clickable(enabled = typing || activeText.isBlank() || sendEnabled, role = Role.Button, onClick = action)
                    .semantics(mergeDescendants = true) { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(HomeSizes.voiceButton).clip(HomeShapes.round).background(c.voice), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = c.onVoice, modifier = Modifier.size(HomeSizes.voiceIcon))
                }
            }
        }
    }
}
