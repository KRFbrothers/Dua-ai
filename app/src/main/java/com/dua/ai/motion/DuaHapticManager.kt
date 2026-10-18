package com.dua.ai.motion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback manager providing tactile micro-interactions:
 * - Ultra-light tick for streaming LLM tokens (onTokenTick)
 * - Tactile click for drawer/model picker snaps (onStateChange)
 * - Quick rise for agentic workflow completions (onAgentStepComplete)
 */
class DuaHapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastTokenTickMs = 0L

    /**
     * Ultra-light tick fired during streaming token delivery.
     * Rate-limited to max ~20-30Hz (every 40ms) to prevent vibrator queue saturation.
     */
    fun onTokenTick() {
        val vib = vibrator ?: return
        val now = System.currentTimeMillis()
        if (now - lastTokenTickMs < 40) return
        lastTokenTickMs = now

        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f)
                    .compose()
                vib.vibrate(effect)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }
    }

    /**
     * Click confirmation for navigation snaps, drawer locks, or model picker switches.
     */
    fun onStateChange() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
                vib.vibrate(effect)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { vib.vibrate(10) }
        }
    }

    /**
     * Quick rise confirmation when an AI Agent step finishes (e.g. PR created, code inspected).
     */
    fun onAgentStepComplete() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.9f)
                    .compose()
                vib.vibrate(effect)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { vib.vibrate(25) }
        }
    }
}
