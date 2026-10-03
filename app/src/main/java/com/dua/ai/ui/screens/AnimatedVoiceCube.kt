package com.dua.ai.ui.screens

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.cos

@Composable
internal fun AnimatedVoiceCube(listening: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun motionAllowed() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motion by remember { mutableStateOf(motionAllowed()) }
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    var resumed by remember { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = motionAllowed() }
        }
        val events = LifecycleEventObserver { _, _ -> resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle?.addObserver(events)
        onDispose { resolver.unregisterContentObserver(observer); lifecycle?.removeObserver(events) }
    }
    var progress by remember { mutableFloatStateOf(0f) }
    val active = listening && motion && resumed
    LaunchedEffect(active) {
        progress = 0f
        if (active) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now -> progress = ((now - start) % 4_000_000_000L) / 4_000_000_000f }
        }
    }
    Canvas(modifier.semantics { contentDescription = if (listening) "Dua cube · Voice preview" else "Dua cube · Muted" }) {
        val pulse = if (active) .96f + .08f * ((1f - cos(progress * Math.PI.toFloat() * 4f)) / 2f) else 1f
        val radius = size.minDimension * .28f * pulse
        val faces = VoiceCubeGeometry.faces(if (active) progress * 360f else 32f, if (active) 10f + 4f * kotlin.math.sin(progress * Math.PI.toFloat() * 2f) else -20f)
        faces.forEach { face ->
            val points = face.points.map { p -> VoiceCubeGeometry.project(p).let { Offset(size.width / 2 + it.first * radius, size.height / 2 + it.second * radius) } }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val alpha = if (active) .85f + .15f * ((1f - cos(progress * Math.PI.toFloat() * 4f)) / 2f) else 1f
            drawPath(path, Brush.linearGradient(listOf(Color(face.shade, face.shade, face.shade, alpha), Color(.88f, .88f, .90f, alpha)), start = points[0], end = points[2]))
            drawPath(path, Color(0xFFCAC4D0).copy(alpha = alpha), style = Stroke(1.dp.toPx()))
        }
    }
}
