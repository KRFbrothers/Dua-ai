package com.dua.ai.ui.components

import com.dua.ai.ui.theme.DuaVectorStroke

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons drawn for Dua where Material has no close match. Tinted by the Icon that shows them. */
object DuaIcons {
    /** Four-point sparkle with a small plus (Auto pill and Home tagline). */
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder("DuaSparkle", 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(DuaVectorStroke), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10f, 4.5f)
            quadTo(11f, 12f, 18.5f, 13f)
            quadTo(11f, 14f, 10f, 21.5f)
            quadTo(9f, 14f, 1.5f, 13f)
            quadTo(9f, 12f, 10f, 4.5f)
            close()
            moveTo(19f, 2f); lineTo(19f, 8f)
            moveTo(16f, 5f); lineTo(22f, 5f)
        }.build()
    }
}
