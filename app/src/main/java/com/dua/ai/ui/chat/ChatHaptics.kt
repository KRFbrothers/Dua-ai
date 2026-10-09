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
