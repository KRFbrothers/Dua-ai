package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.R
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.*
import com.dua.ai.ui.voice.firstNameOf
import com.dua.ai.ui.voice.voiceGreeting

/** Shared task resources have one owner, alongside the production Home. */
internal val HomeTaskPromptIds = listOf(R.string.home_task_prompt_backup,
    R.string.home_task_prompt_react, R.string.home_task_prompt_compare)

/** Home's decoration changes independently of the stable composer and its draft/focus. */
@Composable
internal fun ChatContent(task: Boolean, composing: Boolean = false, profileName: String = "", onTask: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).testTag("home-empty-content")) {
        val density = LocalDensity.current
        val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
        val compact = maxHeight < HomeSizes.heroCompactHeight
        val promptMaxHeight = maxHeight * .55f
        val motion = rememberMotionAllowed()
        val enter = if (motion) HomeMotion.fadeIn else 0
        val exit = if (motion) HomeMotion.fadeOut else 0
        AnimatedVisibility(!composing, modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(enter)), exit = fadeOut(tween(exit))) {
            // At large text/small heights, fixed starter rows would clip the
            // greeting. Let the complete Task body scroll as one document.
            val scrollTogether = task && maxHeight < (HomeSizes.heroCompactHeight + Sizes.touchTarget) * density.fontScale
            Column(Modifier.fillMaxSize().then(if (scrollTogether) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                val hero = if (scrollTogether) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())
                Box(hero.fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("home-hero"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        DuaCalligraphyLogo(Modifier.width(if (compact || keyboardOpen) HomeSizes.heroCompactLogo else HomeSizes.heroLogo))
                        AnimatedContent(task, transitionSpec = {
                            fadeIn(tween(enter)) togetherWith fadeOut(tween(exit))
                        }, label = "Home welcome") { selectedTask ->
                            Text(if (selectedTask) "What can I tackle for you?" else voiceGreeting(firstNameOf(profileName)),
                                style = HomeGreetingStyle, color = HomeTheme.colors.promptText,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (task) TaskStarters(onTask, if (scrollTogether) Modifier else
                    Modifier.heightIn(max = promptMaxHeight).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun TaskStarters(onTask: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = HomeTheme.colors
    val haptic = LocalChatHaptic.current
    val icons = listOf(R.drawable.home_task_attach_file, R.drawable.home_task_code, R.drawable.home_task_public)
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("supplied-task-starters"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HomeTaskPromptIds.forEachIndexed { i, id ->
            val prompt = stringResource(id)
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
                .clip(HomeShapes.promptRow).background(colors.promptRow)
                .clickable(role = Role.Button) { haptic(ChatHaptic.Tap); onTask(prompt) }
                .padding(horizontal = HomeSizes.promptRowPaddingH, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap)) {
                Icon(ImageVector.vectorResource(icons[i]), null, tint = colors.promptIcon,
                    modifier = Modifier.size(HomeSizes.promptIcon))
                Text(prompt, style = HomeTaskPromptStyle, color = colors.promptText, modifier = Modifier.weight(1f))
            }
        }
    }
}
