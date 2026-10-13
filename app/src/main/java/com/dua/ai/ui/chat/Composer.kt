package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.ComposerTextStyle
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.PillLabelStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

const val AUTO_MODEL = "Auto"

@Composable
fun ModelChipRow(
    modifier: Modifier = Modifier,
    selectedModel: String = "Auto",
    onModelClick: () -> Unit = {}
) {
    // Model chip header stub
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
    currentModel: String = "Auto"
) {
    var localText by remember { mutableStateOf(value) }
    val chatState = state as? ChatUiState
    val activeText = if (state != null) value else if (value.isNotEmpty()) value else localText

    val colors = DuaTheme.colors
    val scheme = MaterialTheme.colorScheme
    fun submit() {
        if (activeText.isNotBlank()) {
            onSend(activeText)
            localText = ""
            onValueChange("")
            onDraftChange("")
        }
    }

    // Dabba / ChatGPT composer: one rounded "Ask anything" surface with the tools row inside it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DuaShape.composer)
                .background(colors.composer)
                .border(1.dp, colors.composerBorder, DuaShape.composer)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
        ) {
            val textFieldModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onFocusChanged { onTextFieldFocus?.invoke(it.isFocused) }
                .onGloballyPositioned { onTextFieldBounds?.invoke(it.boundsInRoot()) }
                .semantics { contentDescription = "Message" }

            BasicTextField(
                value = activeText,
                onValueChange = {
                    localText = it
                    onValueChange(it)
                    onDraftChange(it)
                },
                modifier = textFieldModifier,
                textStyle = ComposerTextStyle.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.onSurface),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (activeText.isEmpty()) {
                            Text("Ask anything", style = ComposerTextStyle, color = scheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttach, modifier = Modifier.size(Sizes.composerRound)) {
                    Icon(Icons.Default.Add, contentDescription = "Add photos and files", tint = scheme.onSurface,
                        modifier = Modifier.size(Sizes.icon))
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = Sizes.touchTarget)
                        .clip(DuaShape.pill)
                        .clickable(role = Role.Button, onClick = onModelClick)
                        .semantics(mergeDescendants = true) { contentDescription = "Model, $currentModel" }
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny))
                    Text(currentModel, style = PillLabelStyle, color = scheme.onSurface, maxLines = 1)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMic, modifier = Modifier.size(Sizes.composerRound)) {
                    Icon(Icons.Default.Mic, contentDescription = "Dictate", tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconSmall))
                }
                val (icon, label, action) = when {
                    chatState?.typing == true -> Triple(Icons.Default.Stop, "Stop response", { onStop() })
                    activeText.isNotBlank() -> Triple(Icons.Default.ArrowUpward, "Send message", { submit() })
                    else -> Triple(Icons.Default.GraphicEq, "Voice mode", { onVoiceMode() })
                }
                Box(
                    modifier = Modifier
                        .size(Sizes.composerRound)
                        .clickable(role = Role.Button, onClick = action)
                        .semantics(mergeDescendants = true) { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(Sizes.sendButton).clip(CircleShape).background(colors.action), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = colors.onAction, modifier = Modifier.size(Sizes.iconSmall))
                    }
                }
            }
        }
    }
}
