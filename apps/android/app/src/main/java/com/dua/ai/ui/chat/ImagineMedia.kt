package com.dua.ai.ui.chat

import com.dua.ai.ui.components.ShimmerBox

import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import android.graphics.Bitmap
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.dua.ai.ui.theme.DuaTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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

/**
 * TalkBack label for a loaded generated image. Grid tiles pass null because their clickable wrapper already
 * announces "Generated image N of M. Tap to open"; posters default to null because VideoPoster labels the video.
 */
internal const val GENERATED_IMAGE_DESCRIPTION = "Generated image"

/** An image (or a video poster when [poster]) loaded through [LocalMediaLoader], cropped to fill its box. */
@Composable
fun MediaBitmap(media: GeneratedMedia, modifier: Modifier = Modifier, maxPx: Int = 1024, poster: Boolean = false, contentScale: ContentScale = ContentScale.Crop, imageDescription: String? = if (poster) null else GENERATED_IMAGE_DESCRIPTION,
    background: Color = ChatTheme.colors.mediaFill, onSize: (Int, Int) -> Unit = { _, _ -> }) {
    val loader = LocalMediaLoader.current
    var bitmap by remember(media.key, maxPx, poster) { mutableStateOf<Bitmap?>(if (poster) loader.cachedPoster(media, maxPx) else loader.cached(media, maxPx)) }
    var failed by remember(media.key, maxPx, poster) { mutableStateOf(false) }
    // Telegram ImageReceiver: crossfade only when the bitmap was not already in the memory cache.
    val fromCache = remember(media.key, maxPx, poster) { bitmap != null }
    val reveal = TelegramImageCrossfade.shouldAnimate(fromCache, com.dua.ai.ui.theme.rememberMotionAllowed())
    LaunchedEffect(media.key, maxPx, poster) {
        if (bitmap == null) try { bitmap = if (poster) loader.poster(media, maxPx) else loader.image(media, maxPx); failed = bitmap == null && !poster }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
    }
    LaunchedEffect(bitmap) { bitmap?.let { onSize(it.width, it.height) } }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        val current = bitmap
        when {
            current != null -> Image(current.asImageBitmap(), contentDescription = imageDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize().imageReveal(reveal))
            failed -> Icon(Icons.Outlined.BrokenImage, contentDescription = "Image unavailable", tint = ChatTheme.colors.mutedText)
            poster -> Icon(Icons.Outlined.Movie, contentDescription = null, tint = ChatTheme.colors.mutedText)
            else -> ShimmerBox(Modifier.fillMaxSize())
        }
    }
}

/**
 * 1–4 generated images. One image keeps its aspect ratio at full width; more use a two-column grid of
 * equal tiles. Tapping a tile opens the fullscreen viewer at that index.
 */
@Composable
fun ImageGrid(media: List<GeneratedMedia>, aspectRatio: String?, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ratio = aspectValue(aspectRatio)
    val px = with(LocalDensity.current) { ChatSizes.imageDecodeWidth.roundToPx() }
    if (media.size == 1) {
        var natural by remember(media.first().key) { mutableStateOf<Float?>(null) }
        val shown = (natural ?: ratio).coerceIn(.5f, 2f)
        MediaBitmap(media.first(), Modifier.widthIn(max = ChatSizes.mediaMaxWidth).fillMaxWidth().aspectRatio(shown).clip(DuaShape.card)
            .clickable(role = Role.Image, onClickLabel = "Open image") { onOpen(0) }.semantics { contentDescription = "Generated image. Tap to open" },
            maxPx = px * 2, imageDescription = null, onSize = { w, h -> if (aspectRatio == null || aspectRatio == "auto") natural = w.toFloat() / h })
        return
    }
    val tile = if (ratio in .7f..1.45f) ratio else 1f
    Column(modifier.widthIn(max = ChatSizes.mediaMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        media.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pair.forEachIndexed { column, item ->
                    val index = row * 2 + column
                    MediaBitmap(item, Modifier.weight(1f).aspectRatio(tile).clip(DuaShape.codeBlock)
                        .clickable(role = Role.Image, onClickLabel = "Open image ${index + 1}") { onOpen(index) }
                        .semantics { contentDescription = "Generated image ${index + 1} of ${media.size}. Tap to open" }, maxPx = px, imageDescription = null)
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
    Column(modifier.widthIn(max = if (count == 1) ChatSizes.placeholderSingleMaxWidth else ChatSizes.mediaMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (0 until count.coerceIn(1, 4)).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pair.forEach { _ ->
                    Box(Modifier.weight(1f).aspectRatio(tile).clip(if (count == 1) DuaShape.card else DuaShape.codeBlock), contentAlignment = Alignment.Center) {
                        ShimmerBox(Modifier.fillMaxSize())
                        Icon(Icons.Outlined.AutoAwesome, null, tint = ChatTheme.colors.mutedText, modifier = Modifier.size(Sizes.icon))
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
    BoxWithConstraints(modifier.widthIn(max = if (ratio < 1f) ChatSizes.portraitVideoMaxWidth else ChatSizes.mediaMaxWidth).fillMaxWidth().aspectRatio(ratio).clip(DuaShape.card)
        .clickable(role = Role.Button, onClickLabel = "Play video", onClick = onPlay)
        .semantics { contentDescription = "Generated video${media.duration?.let { ", $it seconds" } ?: ""}. Tap to play"; role = Role.Button }) {
        val px = with(LocalDensity.current) { maxWidth.roundToPx() }
        MediaBitmap(media, Modifier.fillMaxSize(), maxPx = px, poster = true)
        Surface(shape = CircleShape, color = DuaTheme.colors.mediaOverlay, modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Filled.PlayArrow, null, tint = DuaTheme.colors.onMedia, modifier = Modifier.padding(Spacing.md).size(ChatSizes.mediaIcon))
        }
        media.duration?.let { seconds ->
            Surface(shape = DuaShape.chip, color = DuaTheme.colors.mediaOverlay, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)) {
                Text("0:%02d".format(seconds), style = MaterialTheme.typography.labelMedium, color = DuaTheme.colors.onMedia,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs))
            }
        }
    }
}
