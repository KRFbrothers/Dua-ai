package com.dua.ai.ui.chat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.dua.ai.ui.og.BlobDrawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Canvas adapter for the original Telegram blob geometry collected in Dua OG Assets.
 * Palette, position and opacity come from HomeBackdrop; the renderer does not own UI state.
 */
internal class OgGenerationBackdrop(colors: List<Color>) {
    private val blobs = colors.mapIndexed { index, _ ->
        BlobDrawable(6, 7401L + index).apply {
            // Keep the shader's near-transparent edge inside the original morphing outline.
            minRadius = 90f
            maxRadius = 115f
            generateBlob()
        }
    }
    private val paints = colors.map { color ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 100f,
                intArrayOf(color.copy(alpha = .78f).toArgb(), color.copy(alpha = .34f).toArgb(), Color.Transparent.toArgb()),
                floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        }
    }
    private var previousAngle = Float.NaN

    fun draw(canvas: Canvas, width: Float, height: Float, angle: Float, opacity: Float, animate: Boolean) {
        if (width <= 0f || height <= 0f || opacity <= 0f) return
        // Capture/repaint at the same animation frame must not advance upstream geometry.
        val advance = animate && angle != previousAngle
        previousAngle = angle
        val radius = maxOf(width, height) * .72f
        for (i in blobs.indices) {
            val phase = angle + i * (PI / 2).toFloat()
            val x = width * (.5f + .42f * cos(phase))
            val y = height * (.25f + .3f * sin(phase))
            val blob = blobs[i]
            if (advance) blob.update(.12f, 1f)
            val paint = paints[i]
            paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            val save = canvas.save()
            canvas.translate(x, y)
            canvas.scale(radius / 100f, radius / 100f)
            blob.draw(0f, 0f, canvas, paint)
            canvas.restoreToCount(save)
        }
    }
}
