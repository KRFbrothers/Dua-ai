/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java (startAnimation, startChildAnimation, dismiss) (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One row in the long-press popup. */
internal class PopupItem(val key: String, val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/** ActionBarPopupWindow timings. */
internal object TelegramPopupTimings {
    /** windowAnimatorSet.setDuration(150 + 16 * visibleCount) */
    fun openMillis(visibleCount: Int) = 150 + 16 * visibleCount
    const val ITEM_MILLIS = 180 // startChildAnimation
    const val DISMISS_MILLIS = 150 // dismissAnimationDuration
    /** Layout.setBackScaleY: a child starts once (its bottom - dp(24)) is revealed by backScaleY * height. */
    fun childStarted(childBottom: Float, revealed: Float, dp24: Float) = childBottom - dp24 <= revealed
}

/**
 * Telegram message popup: the background grows from the top (backScaleY 0 -> 1, alpha 0 -> 1, pivot at the
 * anchored edge); each item fades in and slides from -6dp with a decelerate curve as the background reaches it;
 * dismiss slides -5dp and fades out.
 */
@Composable
internal fun TelegramMessagePopup(alignEnd: Boolean, items: List<PopupItem>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val visible = items.size
    LaunchedEffect(Unit) { back.animateTo(1f, tween(TelegramPopupTimings.openMillis(visible))) }
    fun dismiss(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch { out.animateTo(1f, tween(TelegramPopupTimings.DISMISS_MILLIS)); onDismiss(); then() }
    }
    val rowHeight = with(density) { Sizes.touchTarget.toPx() }
    val dp24 = with(density) { 24.dp.toPx() }
    val dp6 = with(density) { 6.dp.toPx() }
    val dp5 = with(density) { 5.dp.toPx() }
    Popup(alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart, offset = IntOffset(0, 0),
        onDismissRequest = { dismiss() }, properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(12.dp), color = ChatTheme.colors.assistantBubble, shadowElevation = 6.dp,
            modifier = Modifier.testTag("message-popup").widthIn(min = 196.dp).graphicsLayer {
                transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
                scaleY = back.value
                alpha = back.value * (1f - out.value)
                translationY = -dp5 * out.value
            }) {
            Column(Modifier.padding(vertical = Spacing.xs)) {
                items.forEachIndexed { index, item ->
                    val itemAnim = remember { Animatable(0f) }
                    val totalHeight = rowHeight * visible
                    val started = TelegramPopupTimings.childStarted(rowHeight * (index + 1), back.value * totalHeight, dp24)
                    LaunchedEffect(started) { if (started) itemAnim.animateTo(1f, tween(TelegramPopupTimings.ITEM_MILLIS, easing = TelegramDecelerate)) }
                    Row(Modifier.heightIn(min = Sizes.touchTarget).testTag("popup-${item.key}")
                        .clickable(enabled = item.enabled && !closing, role = Role.Button) { dismiss(item.onClick) }
                        .graphicsLayer {
                            alpha = itemAnim.value * (if (item.enabled) 1f else 0.5f)
                            translationY = -dp6 * (1f - itemAnim.value)
                        }
                        .padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, null, tint = ChatTheme.colors.chipIcon, modifier = Modifier.size(Sizes.iconTiny))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = ChatTheme.colors.chipText,
                            modifier = Modifier.padding(start = Spacing.lg).width(140.dp))
                    }
                }
            }
        }
    }
}
