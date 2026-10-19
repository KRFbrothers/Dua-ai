package com.dua.ai.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DrawerTheme

/** Isometric cube from the supplied Compose reference, tinted by the existing drawer theme. */
@Composable
internal fun DrawerBrandMark(modifier: Modifier) {
    val tint = DrawerTheme.colors.subText
    Box(modifier.drawWithCache {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.44f
        val side = r * 0.866f
        val outline = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + side, cy - r * 0.5f)
            lineTo(cx + side, cy + r * 0.5f)
            lineTo(cx, cy + r)
            lineTo(cx - side, cy + r * 0.5f)
            lineTo(cx - side, cy - r * 0.5f)
            close()
        }
        val edges = Path().apply {
            moveTo(cx - side, cy - r * 0.5f); lineTo(cx, cy)
            moveTo(cx + side, cy - r * 0.5f); lineTo(cx, cy)
            moveTo(cx, cy + r); lineTo(cx, cy)
        }
        val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            drawPath(outline, tint, style = stroke)
            drawPath(edges, tint, style = stroke)
        }
    })
}
