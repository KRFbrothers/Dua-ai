package com.dua.ai.ui.chat

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Light haptics in the chat: [Tap] for chip and switch toggles and the swipe-up, [Confirm] for send, [LongPress] for long presses. */
enum class ChatHaptic { Tap, Confirm, LongPress }

/** Android constant for each haptic. View.performHapticFeedback already respects the system "Touch feedback" setting. */
@SuppressLint("InlinedApi") // CONFIRM is only used on API 30+ (checked via sdk).
internal fun ChatHaptic.constant(sdk: Int = Build.VERSION.SDK_INT): Int = when (this) {
    ChatHaptic.Tap -> HapticFeedbackConstants.CLOCK_TICK
    ChatHaptic.Confirm -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    ChatHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
}

internal fun View.performChatHaptic(kind: ChatHaptic) { performHapticFeedback(kind.constant()) }

/**
 * The one chat haptic helper for composables that do not get [ChatActions] (copy buttons, retry).
 * ChatRoute provides `actions.haptic`, which checks the app's Haptics setting; the View call above
 * respects the system Touch feedback setting. Default: no haptics (previews, tests).
 */
val LocalChatHaptic = androidx.compose.runtime.staticCompositionLocalOf<(ChatHaptic) -> Unit> { {} }

/** Throttle streamed text feedback before dispatching through the app/system preference-aware callback. */
internal class StreamHapticThrottle(private val clock: () -> Long = android.os.SystemClock::uptimeMillis) {
    private var lastTick: Long? = null
    fun tick(haptic: () -> Unit) {
        val now = clock()
        if (lastTick?.let { now - it < 40 } == true) return
        lastTick = now
        haptic()
    }
}
