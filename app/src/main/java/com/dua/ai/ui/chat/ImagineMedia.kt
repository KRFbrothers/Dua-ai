package com.dua.ai.ui.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.MediaLoader
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Loads generated media; the app provides the real cache, previews and screenshot tests a fake. */
val LocalMediaLoader = compositionLocalOf<MediaLoader> { object : MediaLoader {} }

/** "16:9" → 16/9f; "auto" and unknown values → [fallback]. */
internal fun aspectValue(value: String?, fallback: Float = 1f): Float {
    val parts = value?.split(':')?.mapNotNull { it.toFloatOrNull() } ?: return fallback
    return if (parts.size == 2 && parts[0] > 0 && parts[1] > 0) parts[0] / parts[1] else fallback
}

/** An image (or a video poster when [poster]) loaded through [LocalMediaLoader], cropped to fill its box. */
@Composable
fun MediaBitmap(media: GeneratedMedia, modifier: Modifier = Modifier, maxPx: Int = 1024, poster: Boolean = false, contentScale: ContentScale = ContentScale.Crop,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh, onSize: (Int, Int) -> Unit = { _, _ -> }) {
    val loader = LocalMediaLoader.current
    var bitmap by remember(media.key, maxPx, poster) { mutableStateOf<Bitmap?>(if (poster) loader.cachedPoster(media, maxPx) else loader.cached(media, maxPx)) }
    var failed by remember(media.key, maxPx, poster) { mutableStateOf(false) }
    LaunchedEffect(media.key, maxPx, poster) {
        if (bitmap == null) try { bitmap = if (poster) loader.poster(media, maxPx) else loader.image(media, maxPx); failed = bitmap == null && !poster }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
    }
    LaunchedEffect(bitmap) { bitmap?.let { onSize(it.width, it.height) } }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        val current = bitmap
        when {
            current != null -> Image(current.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            failed -> Icon(Icons.Outlined.BrokenImage, contentDescription = "Image unavailable", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            poster -> Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> ShimmerBox(Modifier.fillMaxSize())
        }
    }
}

/** Soft pulsing placeholder while media loads or is being generated. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(.35f, .7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.alpha(alpha).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/**
 * 1–4 generated images. One image keeps its aspect ratio at full width; more use a two-column grid of
 * equal tiles. Tapping a tile opens the fullscreen viewer at that index.
 */
@Composable
fun ImageGrid(media: List<GeneratedMedia>, aspectRatio: String?, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ratio = aspectValue(aspectRatio)
    val px = with(LocalDensity.current) { 360.dp.roundToPx() }
    if (media.size == 1) {
        var natural by remember(media.first().key) { mutableStateOf<Float?>(null) }
        val shown = (natural ?: ratio).coerceIn(.5f, 2f)
        MediaBitmap(media.first(), Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(shown).clip(DuaShape.card)
            .clickable(role = Role.Image, onClickLabel = "Open image") { onOpen(0) }.semantics { contentDescription = "Generated image. Tap to open" },
            maxPx = px * 2, onSize = { w, h -> if (aspectRatio == null || aspectRatio == "auto") natural = w.toFloat() / h })
        return
    }
    val tile = if (ratio in .7f..1.45f) ratio else 1f
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        media.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pair.forEachIndexed { column, item ->
                    val index = row * 2 + column
                    MediaBitmap(item, Modifier.weight(1f).aspectRatio(tile).clip(DuaShape.codeBlock)
                        .clickable(role = Role.Image, onClickLabel = "Open image ${index + 1}") { onOpen(index) }
                        .semantics { contentDescription = "Generated image ${index + 1} of ${media.size}. Tap to open" }, maxPx = px)
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Placeholder tiles while images are generated. */
@Composable
fun ImagePlaceholderGrid(count: Int, aspectRatio: String?, modifier: Modifier = Modifier) {
    val ratio = aspectValue(aspectRatio)
    val tile = if (count == 1) ratio.coerceIn(.5f, 2f) else if (ratio in .7f..1.45f) ratio else 1f
    Column(modifier.widthIn(max = if (count == 1) 300.dp else 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (0 until count.coerceIn(1, 4)).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pair.forEach { _ ->
                    Box(Modifier.weight(1f).aspectRatio(tile).clip(if (count == 1) DuaShape.card else DuaShape.codeBlock), contentAlignment = Alignment.Center) {
                        ShimmerBox(Modifier.fillMaxSize())
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.icon))
                    }
                }
                if (pair.size == 1 && count > 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** A finished video: poster with a play button and duration badge. Playback is handled by the caller. */
@Composable
fun VideoPoster(media: GeneratedMedia, aspectRatio: String?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val ratio = aspectValue(aspectRatio, 16 / 9f).coerceIn(.5f, 2f)
    BoxWithConstraints(modifier.widthIn(max = if (ratio < 1f) 280.dp else 420.dp).fillMaxWidth().aspectRatio(ratio).clip(DuaShape.card)
        .clickable(role = Role.Button, onClickLabel = "Play video", onClick = onPlay)
        .semantics { contentDescription = "Generated video${media.duration?.let { ", $it seconds" } ?: ""}. Tap to play"; role = Role.Button }) {
        val px = with(LocalDensity.current) { maxWidth.roundToPx() }
        MediaBitmap(media, Modifier.fillMaxSize(), maxPx = px, poster = true)
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f), modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(Spacing.md).size(32.dp))
        }
        media.duration?.let { seconds ->
            Surface(shape = DuaShape.chip, color = Color.Black.copy(alpha = .55f), modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)) {
                Text("0:%02d".format(seconds), style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs))
            }
        }
    }
}
