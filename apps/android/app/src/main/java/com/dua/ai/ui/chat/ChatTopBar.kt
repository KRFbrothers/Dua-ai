package com.dua.ai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.dua.ai.ui.components.pressFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomeHeaderStyle
import com.dua.ai.ui.theme.HomeHeaderModelStyle
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

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
    conversationOptions: (@Composable () -> Unit)? = null,
) {
    val shortName = shortModelLabel(modelName)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val brandWidth = with(density) { measurer.measure(AnnotatedString("Dua AI"), HomeHeaderStyle).size.width.toDp() }
    val modelWidth = with(density) { measurer.measure(AnnotatedString(shortName), HomeHeaderModelStyle).size.width.toDp() }
    val pairWidth = brandWidth + Spacing.xs +
        maxOf(Sizes.touchTarget, modelWidth + Sizes.iconTiny + Spacing.xs + HomeSizes.modelPillPaddingH * 2)
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()
        .padding(horizontal = HomeSizes.topBarPaddingH, vertical = HomeSizes.topBarPaddingV)) {
        // Reserve the same action space in both modes so large text never changes
        // header height (and shifts the Home logo) when New chat is hidden in Task.
        val inline = pairWidth + Spacing.xs * 2 <= maxWidth - Sizes.touchTarget * 3
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeaderButton(Icons.Outlined.DragHandle, "Open chat history", onMenu, onLongPress = onFindInChat)
                if (inline) HeaderLabels(shortName, onModel, Modifier.weight(1f).padding(horizontal = Spacing.xs))
                else Spacer(Modifier.weight(1f))
                if (!task) HeaderButton(ReferenceIcons.TemporaryChat, "New chat", onNewChat, enabled = newChatEnabled, iconSize = 22.dp)
                if (conversationOptions != null) conversationOptions() else IconButton(onClick = onProfile, modifier = Modifier.size(Sizes.touchTarget)
                    .semantics { contentDescription = "Open profile and settings" }) {
                    // Keep avatar artwork fixed; surrounding labels still respect font scaling.
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                        AccountAvatar(email = null, displayName = profileName, avatarUrl = profilePhotoUrl, size = 34.dp)
                    }
                }
            }
            // Reflow the pair together when large text cannot fit between the 48 dp controls.
            if (!inline) HeaderLabels(shortName, onModel, Modifier.fillMaxWidth().padding(horizontal = Spacing.xs))
        }
    }
}

@Composable
private fun HeaderLabels(name: String, onModel: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).testTag("home-header-labels"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Dua AI", style = HomeHeaderStyle, color = ChatTheme.colors.text,
            maxLines = 1, modifier = Modifier.width(IntrinsicSize.Max))
        ModelSelector(name, onModel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null, iconSize: Dp = Sizes.icon) {
    val c = HomeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val colors = IconButtonDefaults.iconButtonColors(contentColor = c.roundButtonIcon)
    val body: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.contentColor else colors.disabledContentColor,
            modifier = Modifier.size(iconSize).pressFeedback(interaction))
    }
    if (onLongPress == null) IconButton(onClick = onClick, enabled = enabled, interactionSource = interaction,
        colors = colors,
        modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = description }) { body() }
    else Box(Modifier.size(Sizes.touchTarget)
        .combinedClickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick,
            onLongClickLabel = "Find in chat", onLongClick = onLongPress)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) { body() }
}
