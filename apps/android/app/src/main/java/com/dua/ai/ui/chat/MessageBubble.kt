package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import com.dua.ai.model.ChatArtifact
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.MessageArtifacts
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

internal data class ArtifactCanvasSession(val messageId: String, val artifacts: List<ChatArtifact>)

/** Message-level artifact entry. The chat list owns its modal so scrolling cannot dispose the viewer. */
@Composable
internal fun MessageArtifactCanvas(message: ChatMessage, onOpenCanvas: ((List<ChatArtifact>) -> Unit)? = null) {
    if (message.isUser || message.streaming) return
    val artifacts = remember(message.text) { MessageArtifacts.find(message.text) }
    if (artifacts.isEmpty()) return
    var localArtifacts by remember(message.id) { mutableStateOf<List<ChatArtifact>>(emptyList()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalChatHaptic.current
    Box(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Surface(onClick = {
            focus.clearFocus(); keyboard?.hide(); haptic(ChatHaptic.Tap)
            if (onOpenCanvas != null) onOpenCanvas(artifacts) else localArtifacts = artifacts
        }, shape = DuaShape.chip, color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shadowElevation = Spacing.xxs,
            modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = Sizes.touchTarget).testTag("open-canvas")) {
            Row(Modifier.padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Outlined.Code, null, Modifier.size(Sizes.iconSmall))
                Text("Open Canvas", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (localArtifacts.isNotEmpty()) ArtifactCanvasSheet(localArtifacts) { localArtifacts = emptyList() }
}
