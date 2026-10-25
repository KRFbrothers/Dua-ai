package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.testTag
import com.dua.ai.R
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.HomeHeaderModelStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Uses the selected provider's live config, never a fixed model version. */
internal fun homeModelName(state: ChatUiState): String {
    state.freeModel?.let { return shortModelLabel(it.name) }
    val configured = state.providers.firstOrNull { it.name == state.model }?.model.orEmpty().ifBlank { state.model }
    return shortModelLabel(configured)
}

/** Shorten known model families, never guess a tier from the hosting transport. */
internal fun shortModelLabel(name: String): String {
    val words = name.lowercase().split(Regex("[^a-z0-9]+"))
    return when {
        "flash" in words -> if ("lite" in words) "Flash Lite" else "Flash"
        "pro" in words -> "Pro"
        else -> name.replace(Regex("\\s*\\(Cloud Run\\)", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Dua" }
    }
}

@Composable
internal fun ModelSelector(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, textStyle: TextStyle = HomeHeaderModelStyle) {
    val c = HomeTheme.colors
    val motionAllowed = rememberMotionAllowed()
    val haptic = LocalChatHaptic.current
    // A compact visual pill inside the full accessibility touch target.
    Box(modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
        .clip(HomeShapes.segmentTab).clickable(role = Role.Button, onClick = {
            haptic(ChatHaptic.Tap)
            onClick()
        })
        .semantics(mergeDescendants = true) { contentDescription = "Model, $name" },
        contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = HomeSizes.modelPillMinHeight)
            .background(c.roundButton, HomeShapes.round)
            .testTag("home-model-pill")
            .padding(horizontal = HomeSizes.modelPillPaddingH, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (motionAllowed) {
                AnimatedContent(
                    targetState = name,
                    transitionSpec = {
                        (fadeIn(DuaMotion.gentle()) + slideInVertically(DuaMotion.gentle()) { it / 3 })
                            .togetherWith(fadeOut(DuaMotion.gentle()) + slideOutVertically(DuaMotion.gentle()) { -it / 3 })
                    },
                    label = "ModelMorph",
                ) { targetName ->
                    Text(targetName, style = textStyle, color = c.segmentText, maxLines = 1, modifier = Modifier.width(IntrinsicSize.Max))
                }
            } else {
                Text(name, style = textStyle, color = c.segmentText, maxLines = 1, modifier = Modifier.width(IntrinsicSize.Max))
            }
            Icon(ImageVector.vectorResource(R.drawable.home_expand_more), contentDescription = null, tint = c.segmentText, modifier = Modifier.size(Sizes.iconTiny))
        }
    }
}
