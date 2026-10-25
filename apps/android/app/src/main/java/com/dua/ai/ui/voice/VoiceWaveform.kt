package com.dua.ai.ui.voice

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.size
import com.dua.ai.ui.theme.Sizes
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dua.ai.ui.theme.VoiceTheme
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.rememberMotionAllowed
import kotlin.math.sin

/** Primitive audio level avoids boxing a Float on every draw. */
fun interface VoiceLevel { fun read(): Float }

/** RMS/state-driven spectrum-style animation; RMS alone cannot expose real FFT frequency bins. */
internal class VoiceWaveformBuffer(val bins: FloatArray = FloatArray(48)) {
    val path = Path()
    var energy = 0f; private set
    fun update(level: Float, time: Float, elapsed: Float) {
        val safe = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        val blend = (elapsed.coerceIn(0f, .05f) * 12f).coerceIn(0f, 1f)
        energy += (safe - energy) * blend
        for (i in bins.indices) {
            val envelope = sin(Math.PI.toFloat() * (i + 1) / (bins.size + 1))
            val target = (.08f + energy * (.5f + .5f * sin(time * 5f + i * .43f))) * envelope
            bins[i] += (target - bins[i]) * blend
        }
    }
}

/** Paints, paths, shader and sample array are cached; draw() creates no frame-side objects. */
private class WaveformPainter(val width: Float, val height: Float, glow: Color, stroke: Color, val buffer: VoiceWaveformBuffer) {
    private val cx = width / 2f
    private val cy = height / 2f
    private val radius = maxOf(width, height).coerceAtLeast(1f) * .6f
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, radius, intArrayOf(glow.toArgb(), android.graphics.Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke.toArgb(); style = Paint.Style.STROKE; strokeWidth = (height * .025f).coerceIn(1.5f, 3f)
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    fun draw(canvas: Canvas, time: Float, level: Float, dt: Float, muted: Boolean, glowOnly: Boolean) {
        buffer.update(if (muted) 0f else level, time, dt)
        glowPaint.alpha = (35 + buffer.energy * 100).toInt()
        canvas.drawCircle(cx, cy, radius, glowPaint)
        if (glowOnly) return
        val path = buffer.path
        path.rewind()
        val n = buffer.bins.size
        for (i in 0 until n) {
            val x = width * (.08f + .84f * i / (n - 1))
            val y = cy + sin(time * 4f + i * .38f) * buffer.bins[i] * height * .34f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        strokePaint.alpha = if (muted) 90 else 230
        canvas.drawPath(path, strokePaint)
    }
}

@Composable
internal fun rememberVoiceFrame(motion: Boolean): FloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, motion) {
        if (motion) owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            val advance: (Long) -> Unit = { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(.05f)
                last = now
            }
            while (true) withInfiniteAnimationFrameNanos(advance)
        }
    }
    return time
}

@Composable
internal fun VoiceFrequencyWaveform(level: VoiceLevel, speaking: Boolean, muted: Boolean, modifier: Modifier = Modifier,
    frame: FloatState? = null, motion: Boolean = rememberMotionAllowed(), glowOnly: Boolean = false, waveColor: Color? = null) {
    val clock = frame ?: rememberVoiceFrame(motion)
    val buffer = remember { VoiceWaveformBuffer() }
    val voice = DuaTheme.colors.voice
    val glow = if (speaking) voice.speakingGlow else voice.glowSilver
    val stroke = waveColor ?: VoiceTheme.colors.text
    val currentLevel = rememberUpdatedState(level)
    val drawing = remember(buffer, glow, stroke, clock, motion, muted, speaking, glowOnly) { Modifier.drawWithCache {
        val painter = WaveformPainter(size.width, size.height, glow, stroke, buffer)
        var previous = Float.NaN
        onDrawBehind {
            val now = clock.floatValue
            val elapsed = if (motion && previous.isFinite()) (now - previous).coerceAtLeast(.001f) else .05f
            previous = now
            val energy = if (speaking) maxOf(currentLevel.value.read(), .45f + .15f * sin(now * 5f)) else currentLevel.value.read()
            painter.draw(drawContext.canvas.nativeCanvas, now, energy, elapsed, muted, glowOnly)
        }
    } }
    Box(modifier.testTag(if (glowOnly) "voice-ambient-glow" else "voice-frequency-waveform").then(drawing))
}

@Composable
internal fun VoiceSpeakingIndicator() {
    val voice = DuaTheme.colors.voice
    Box(Modifier.size(Sizes.icon).drawWithCache {
        val glow = Brush.radialGradient(listOf(voice.speakingGlow.copy(alpha = .55f), Color.Transparent), radius = size.minDimension / 2)
        onDrawBehind { drawCircle(glow) }
    }, contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Dua is speaking", tint = VoiceTheme.colors.text, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** One pulse per speaking transition; never vibrate on waveform frames. */
@Composable
internal fun VoiceSpeakingFeedback(speaking: Boolean, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(speaking) {
        if (speaking && enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}
