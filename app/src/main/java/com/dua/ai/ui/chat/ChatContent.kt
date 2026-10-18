package com.dua.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dua.ai.R
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomeHeroStyle
import com.dua.ai.ui.theme.HomeHeroCompactStyle
import com.dua.ai.ui.theme.HomeTaskPromptStyle
import com.dua.ai.ui.theme.HomeTaglineCompactStyle
import com.dua.ai.ui.theme.HomeFontFamily
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

internal val HomeTaskPrompts = listOf("Nudge me to backup my photos", "Learn React by building an app", "Compare views on a topic I follow")

/** Profile data only: never substitute a sample name or expose an email as a greeting. */
internal fun homeGreeting(profileName: String): String {
    val firstName = profileName.trim().split(Regex("\\s+"), limit = 2).first()
    return if (firstName.isBlank() || '@' in firstName || firstName.any { it.isISOControl() }) "Ask away!"
    else "Ask away, ${firstName.take(40)}!"
}

/** A quiet, centered Home canvas. Task starters keep the existing draft/focus handler. */
@Composable
internal fun ChatContent(
    task: Boolean,
    profileName: String = "",
    onTask: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).testTag("home-empty-content")) {
        val compact = maxHeight < HomeSizes.heroCompactHeight
        val headingStyle = if (compact) HomeHeroCompactStyle else HomeHeroStyle
        if (task && compact) {
            // One scroll region avoids independently clipping a heading and the first starter
            // when the keyboard or large accessibility text leaves a short viewport.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                HomeHero(true, headingStyle, profileName)
                TaskStarters(onTask)
            }
        } else {
            val promptMaxHeight = maxHeight * 0.55f
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center) { HomeHero(task, headingStyle, profileName) }
                if (task) TaskStarters(onTask, Modifier.heightIn(max = promptMaxHeight).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun HomeHero(task: Boolean, style: TextStyle, profileName: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl).testTag("home-hero"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        if (!task) DuaCalligraphyLogo(Modifier.width(HomeSizes.heroLogo))
        Text(if (task) "What can I take off\nyour plate?" else homeGreeting(profileName),
            style = style, color = ChatTheme.colors.text, textAlign = TextAlign.Center)
        if (!task) Text("Dua always with you", style = HomeTaglineCompactStyle.copy(fontFamily = HomeFontFamily),
            color = ChatTheme.colors.mutedText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TaskStarters(onTask: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = HomeTheme.colors
    val haptic = LocalChatHaptic.current
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val icons = listOf(R.drawable.home_task_attach_file, R.drawable.home_task_code, R.drawable.home_task_public)
        HomeTaskPrompts.forEachIndexed { i, prompt ->
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
                .clip(HomeShapes.segmentTab)
                .clickable(role = Role.Button) { haptic(ChatHaptic.Tap); onTask(prompt) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap)) {
                Icon(ImageVector.vectorResource(icons[i]), null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
                Text(prompt, style = HomeTaskPromptStyle, color = c.promptText, modifier = Modifier.weight(1f))
            }
        }
    }
}
