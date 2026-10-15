package com.dua.ai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Retained for the existing Work destination; Home's Chat/Task control is independent. */
enum class HomeTab(val label: String) { Chat("Chat"), Work("Work") }

@Composable
fun ChatTopBar(
    onMenu: () -> Unit = {},
    task: Boolean = false,
    modelName: String = "Dua",
    profileName: String = "",
    profilePhotoUrl: String = "",
    onModel: () -> Unit = {},
    onProfile: () -> Unit = {},
    onNewChat: () -> Unit = {},
    newChatEnabled: Boolean = true,
    onFindInChat: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().statusBarsPadding()
        .padding(horizontal = HomeSizes.topBarPaddingH, vertical = HomeSizes.topBarPaddingV),
        verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Outlined.DragHandle, "Open chat history", onMenu, onLongPress = onFindInChat)
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = Spacing.xs)) {
            val brand: @Composable () -> Unit = {
                Text("Dua AI", style = MaterialTheme.typography.titleLarge, color = ChatTheme.colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (maxWidth < 160.dp || LocalDensity.current.fontScale > 1.3f) {
                Column { brand(); ModelSelector(modelName, onModel) }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                brand()
                ModelSelector(modelName, onModel, Modifier.weight(1f))
            }
        }
        if (!task) RoundButton(ReferenceIcons.NewChat, "New chat", onNewChat, enabled = newChatEnabled)
        IconButton(onClick = onProfile, modifier = Modifier.size(Sizes.touchTarget)
            .semantics { contentDescription = "Open profile and settings" }) {
            // An avatar initial is artwork inside a fixed 34 dp circle; its 48 dp labelled
            // button stays accessible while large-font settings scale the surrounding text.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                AccountAvatar(email = null, displayName = profileName, avatarUrl = profilePhotoUrl, size = 34.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null) {
    val c = HomeTheme.colors
    val body: @Composable () -> Unit = {
        Box(Modifier.size(HomeSizes.roundButton).clip(HomeShapes.round).background(c.roundButton), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.roundButtonIcon)
        }
    }
    if (onLongPress == null) IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = description }) { body() }
    else Box(Modifier.size(Sizes.touchTarget)
        .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick,
            onLongClickLabel = "Find in chat", onLongClick = onLongPress)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) { body() }
}
