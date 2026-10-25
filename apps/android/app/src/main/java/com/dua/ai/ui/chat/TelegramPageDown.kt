/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/Components/chat/layouts/ChatActivitySideControlsButtonsLayout.java, ui/Components/chat/buttons/ChatActivityBlurredRoundPageDownButton.java and ui/ChatActivity.java (page-down button, newUnreadMessageCount) (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
 * Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018, https://github.com/DrKLO/Telegram
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), licensed under GNU GPL v. 2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details
 * (LICENSE in the repository root, or <https://www.gnu.org/licenses/>).
 */
package com.dua.ai.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsSizes

/** ChatActivitySideControlsButtonsLayout numbers for BUTTON_PAGE_DOWN. */
internal object TelegramPageDown {
    const val VISIBILITY_MILLIS = 280 // BoolAnimator(..., DECELERATE_INTERPOLATOR, 280)
    const val SIZE_DP = 56
    const val HIDDEN_OFFSET_DP = 80 // setTranslationY(dp(80) * (1f - visibility))
    const val MIN_SCALE = 0.7f // lerp(0.7f, 1f, visibility)
    fun scale(visibility: Float) = MIN_SCALE + (1f - MIN_SCALE) * visibility
    /** CounterDrawable shows the exact count. */
    fun counterText(count: Int) = count.toString()
}

/**
 * Telegram's page-down button: round 56 dp button that fades in, scales 0.7 -> 1 and rises 80 dp (decelerate,
 * 280 ms), pivot just below centre; the unread counter sits on top and animates its own visibility.
 */
@Composable
fun TelegramPageDownButton(visible: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    contentDescription: String = "Go to latest reply", stateDescription: String? = null) {
    val motion = com.dua.ai.ui.theme.rememberMotionAllowed()
    val visibility = remember { Animatable(if (visible) 1f else 0f) }
    val counter = remember { Animatable(if (count > 0) 1f else 0f) }
    val millis = if (motion) TelegramPageDown.VISIBILITY_MILLIS else 0
    LaunchedEffect(visible, millis) { visibility.animateTo(if (visible) 1f else 0f, tween(millis, easing = TelegramDecelerate)) }
    LaunchedEffect(count > 0, millis) { counter.animateTo(if (count > 0) 1f else 0f, tween(millis, easing = TelegramDecelerate)) }
    val lastCount = remember { intArrayOf(count) }
    if (count > 0) lastCount[0] = count
    val click by rememberUpdatedState(onClick)
    if (visibility.value <= 0f && !visible) return
    val density = LocalDensity.current
    val hidden = with(density) { TelegramPageDown.HIDDEN_OFFSET_DP.dp.toPx() }
    val size = TelegramPageDown.SIZE_DP.dp
    Box(modifier.testTag("page-down").graphicsLayer {
        val v = visibility.value
        alpha = v
        scaleX = TelegramPageDown.scale(v); scaleY = scaleX
        translationY = hidden * (1f - v)
        transformOrigin = TransformOrigin(0.5f, (size.toPx() / 2f + 8.dp.toPx()) / size.toPx()) // pivotY = size / 2 + 8
    }) {
        Surface(onClick = { click() }, enabled = visible, shape = CircleShape, color = PageTheme.colors.card,
            contentColor = PageTheme.colors.text, border = BorderStroke(SettingsSizes.border, PageTheme.colors.cardBorder),
            shadowElevation = 2.dp,
            modifier = Modifier.padding(top = 14.dp).size(size).semantics {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
            }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(32.dp)) }
        }
        if (counter.value > 0f) Surface(shape = RoundedCornerShape(12.dp), color = ChatTheme.colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.TopCenter).heightIn(min = 24.dp).widthIn(min = 24.dp).testTag("page-down-counter")
                .graphicsLayer { alpha = counter.value; scaleX = counter.value; scaleY = counter.value }) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                Text(TelegramPageDown.counterText(lastCount[0]), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
