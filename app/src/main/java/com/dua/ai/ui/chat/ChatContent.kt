package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.dua.ai.R
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomeTaskPromptStyle
import com.dua.ai.ui.theme.HomeTaglineCompactStyle
import com.dua.ai.ui.theme.HomeFontFamily
import com.dua.ai.ui.theme.HomeMotion
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Task starter labels (strings.xml) paired with their icons. */
internal val HomeTaskPromptIds = listOf(R.string.home_task_prompt_backup, R.string.home_task_prompt_react, R.string.home_task_prompt_compare)

/** A quiet, centered Home canvas. Task starters keep the existing draft/focus handler. */
@Composable
internal fun ChatContent(
    task: Boolean,
    composing: Boolean = false,
    onTask: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).testTag("home-empty-content")) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val compact = maxHeight < HomeSizes.heroCompactHeight
        val motion = rememberMotionAllowed()
        val enterDuration = if (motion) HomeMotion.fadeIn else 0
        val exitDuration = if (motion) HomeMotion.fadeOut else 0
        // Keep the empty canvas and bottom controls stationary while the draft hides decoration.
        AnimatedVisibility(visible = !composing,
            modifier = Modifier.widthIn(max = HomeSizes.composerMaxWidth).fillMaxSize().align(Alignment.Center),
            enter = fadeIn(tween(enterDuration)), exit = fadeOut(tween(exitDuration))) {
            // Animate decorative content only; the composer/focus and Agent callbacks stay outside.
            AnimatedContent(targetState = task, modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(enterDuration)) togetherWith fadeOut(tween(exitDuration)) }, label = "Home mode content") { selectedTask ->
                if (selectedTask && (compact || keyboardOpen)) {
                    // All three real starter handlers remain reachable in a short keyboard viewport.
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                        verticalArrangement = Arrangement.Bottom) {
                        AnimatedVisibility(visible = !keyboardOpen,
                            enter = fadeIn(tween(enterDuration)) + expandVertically(tween(if (motion) HomeMotion.resize else 0)),
                            exit = fadeOut(tween(exitDuration)) + shrinkVertically(tween(if (motion) HomeMotion.resize else 0))) {
                            HomeHero(true, small = true)
                        }
                        TaskStarters(onTask)
                    }
                } else {
                    val promptMaxHeight = maxHeight * 0.55f
                    // Both modes share one layout: Chat reserves the (invisible, inert) starter rows so the
                    // logo sits at exactly the same spot in Chat and Task and never jumps on toggle.
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center) { HomeHero(selectedTask, keyboardOpen || compact) }
                        // Short screens (landscape) give Chat the full height instead of reserving starter rows.
                        if (selectedTask || !compact) TaskStarters(onTask,
                            Modifier.heightIn(max = promptMaxHeight).verticalScroll(rememberScrollState()), visible = selectedTask)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(task: Boolean, small: Boolean) {
    val duration = if (rememberMotionAllowed()) HomeMotion.resize else 0
    val padding by animateDpAsState(if (small) Spacing.sm else Spacing.xxl, tween(duration), label = "Home hero padding")
    val gap by animateDpAsState(if (small) Spacing.sm else Spacing.lg, tween(duration), label = "Home hero gap")
    val logoWidth by animateDpAsState(if (small) HomeSizes.heroCompactLogo else HomeSizes.heroLogo, tween(duration), label = "Home logo width")
    Column(Modifier.fillMaxWidth().padding(vertical = padding).testTag("home-hero"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap)) {
        // Same Dua logo, size and placement in Chat and Task; no headline text.
        DuaCalligraphyLogo(Modifier.width(logoWidth))
        // Task keeps the tagline's space (hidden, no semantics) so the logo is centred identically.
        Text(stringResource(R.string.home_tagline), style = HomeTaglineCompactStyle.copy(fontFamily = HomeFontFamily),
            color = ChatTheme.colors.mutedText, textAlign = TextAlign.Center,
            modifier = if (task) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
    }
}

@Composable
private fun TaskStarters(onTask: (String) -> Unit, modifier: Modifier = Modifier, visible: Boolean = true) {
    val c = HomeTheme.colors
    val haptic = LocalChatHaptic.current
    val hidden = if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
    Column(modifier.then(hidden).fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val icons = listOf(R.drawable.home_task_attach_file, R.drawable.home_task_code, R.drawable.home_task_public)
        HomeTaskPromptIds.forEachIndexed { i, id ->
            val prompt = stringResource(id)
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
                .clip(HomeShapes.segmentTab)
                .clickable(enabled = visible, role = Role.Button) { haptic(ChatHaptic.Tap); onTask(prompt) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap)) {
                Icon(ImageVector.vectorResource(icons[i]), null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
                Text(prompt, style = HomeTaskPromptStyle, color = c.promptText, modifier = Modifier.weight(1f))
            }
        }
    }
}
