package com.dua.ai.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import com.dua.ai.ui.theme.*

@Composable internal fun ProjectComposer(draft: String, enabled: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit,
    onAttach: () -> Unit, onMic: () -> Unit, onVoice: () -> Unit, attachmentPopup: @Composable () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || draft.isNotBlank()
    val c = ProjectTheme.colors
    val send = { if (enabled && draft.isNotBlank()) onSend() }
    Surface(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).navigationBarsPadding().imePadding()
        .testTag("project-composer"), shape = ProjectShapes.attachments, color = c.surface, shadowElevation = ProjectSizes.searchElevation) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs)) {
            val field: @Composable (Modifier) -> Unit = { modifier ->
                BasicTextField(draft, onDraft, modifier.heightIn(min = Sizes.touchTarget).wrapContentHeight().onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "New project chat message" }, enabled = enabled,
                    maxLines = if (expanded) 6 else 1, textStyle = MaterialTheme.typography.bodyLarge.merge(ProjectInputStyle).copy(color = c.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { input -> Box(contentAlignment = Alignment.CenterStart) { if (draft.isEmpty()) Text("New chat", style = MaterialTheme.typography.bodyLarge.merge(ProjectInputStyle), color = c.subText, maxLines = 1); input() } })
            }
            if (expanded) field(Modifier.fillMaxWidth().padding(horizontal = Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box { IconButton(onAttach, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget)) { Icon(ProjectIcons.Plus, "Add project chat attachment", tint = c.text) }; attachmentPopup() }
                if (expanded) Spacer(Modifier.weight(1f)) else field(Modifier.weight(1f))
                IconButton(onMic, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget)) { Icon(ProjectIcons.Mic, "Dictate project message", tint = c.text) }
                IconButton(if (draft.isNotBlank()) send else onVoice, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget)) {
                    Surface(Modifier.size(ProjectSizes.smallCircle), CircleShape, color = c.voice) {
                        Box(contentAlignment = Alignment.Center) { Icon(if (draft.isNotBlank()) androidx.compose.material.icons.Icons.Outlined.ArrowUpward else ProjectIcons.Voice,
                            if (draft.isNotBlank()) "Send project message" else "Project voice mode", tint = c.onVoice) }
                    }
                }
            }
        }
    }
}
