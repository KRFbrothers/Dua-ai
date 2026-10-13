package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.dua.ai.ui.theme.DuaTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.ImagineCodec
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Callbacks for Imagine replies. Defaults are no-ops for previews and screenshots. */
class ImagineActions(
    val onOpen: (messageId: String, index: Int) -> Unit = { _, _ -> },
    val onSave: (List<GeneratedMedia>) -> Unit = {},
    val onShare: (List<GeneratedMedia>) -> Unit = {},
    val onEdit: (GeneratedMedia) -> Unit = {},
    val onAnimate: (GeneratedMedia) -> Unit = {},
    val onCancelVideo: (String) -> Unit = {},
    val onReuse: (ImagineInfo) -> Unit = {},
)

/** A generated-media reply: progress, image grid or video, failures, and Save · Share · Edit actions. */
@Composable
fun ImagineReply(message: ChatMessage, actions: ImagineActions, videoAvailable: Boolean, modifier: Modifier = Modifier, now: Long? = null,
    initiallyPlaying: Boolean = false) {
    val info = message.imagine
    val media = message.media
    val video = info?.isVideo ?: media.firstOrNull()?.isVideo ?: false
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            media.isNotEmpty() && video -> InlineVideo(message.id, media.first(), info?.aspectRatio, actions, initiallyPlaying)
            media.isNotEmpty() -> ImageGrid(media, info?.aspectRatio?.takeIf { media.size == 1 && it != "auto" } ?: info?.aspectRatio, onOpen = { actions.onOpen(message.id, it) })
            info != null && (message.streaming || info.pending) && !info.isVideo -> {
                ImagePlaceholderGrid(info.count, info.aspectRatio)
                Status("${if (info.edit) "Editing" else "Creating"} ${if (info.count == 1) "image" else "${info.count} images"} · ${ImagineCodec.aspectLabel(info.aspectRatio)} · ${info.provider ?: "Grok Imagine"}")
            }
            info != null && (message.streaming || info.pending) -> VideoProgress(message, info, actions, now)
            info != null -> Failure(info, actions)
        }
        if (media.isNotEmpty() && info != null) Text(caption(info, media), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message.notice?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (media.isNotEmpty()) MediaActionsRow(media, video, videoAvailable, actions)
    }
}

private fun caption(info: ImagineInfo, media: List<GeneratedMedia>): String = buildString {
    append(info.provider ?: "Grok Imagine")
    if (info.isVideo) { (media.firstOrNull()?.duration ?: info.seconds)?.let { append(" · $it s") }; info.resolution?.let { append(" · $it") } }
    else append(if (info.edit) " · edit" else " · ${ImagineCodec.aspectLabel(info.aspectRatio)}")
    if (media.any { it.path == null }) append(" · not saved (too large), link expires soon")
}

@Composable
private fun Status(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun VideoProgress(message: ChatMessage, info: ImagineInfo, actions: ImagineActions, now: Long?) {
    var clock by remember { mutableLongStateOf(now ?: System.currentTimeMillis()) }
    LaunchedEffect(now) { if (now == null) while (true) { delay(1000); clock = System.currentTimeMillis() } }
    val elapsed = if (info.startedAt > 0) ((clock - info.startedAt) / 1000).coerceAtLeast(0) else 0
    val ratio = aspectValue(info.aspectRatio, 16 / 9f).coerceIn(.5f, 2f)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = DuaShape.card, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(if (ratio < 1f) 1.4f else ratio), contentAlignment = Alignment.Center) {
                ShimmerBox(Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Text(if (info.progress > 0) "${info.progress}%" else "Queued", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
            if (info.progress > 0) LinearProgressIndicator(progress = { info.progress / 100f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(if (message.streaming) "Starting video…" else "Creating video · ${info.seconds ?: 0} s · ${info.resolution ?: ""}".trimEnd(' ', '·'),
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${elapsed / 60}:%02d elapsed · keeps going if you leave".format(elapsed % 60), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!message.streaming) TextButton(onClick = { actions.onCancelVideo(message.id) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Failure(info: ImagineInfo, actions: ImagineActions) {
    val title = when (info.status) {
        ImagineInfo.CANCELLED -> "Video cancelled"
        ImagineInfo.EXPIRED -> if (info.isVideo) "Video expired" else "Images expired"
        else -> if (info.isVideo) "Video not created" else "Image not created"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = DuaShape.card, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs, end = Spacing.xs), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.xxs).size(Sizes.iconSmall))
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                info.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { actions.onReuse(info) }, modifier = Modifier.offset(x = -Spacing.md)) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(Sizes.iconTiny)); Text("Use prompt again", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun MediaActionsRow(media: List<GeneratedMedia>, video: Boolean, videoAvailable: Boolean, actions: ImagineActions) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.offset(x = -Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.onSave(media) }) { Icon(Icons.Outlined.Download, if (media.size > 1) "Save all to gallery" else "Save to gallery", tint = tint, modifier = Modifier.size(Sizes.iconSmall)) }
        IconButton(onClick = { actions.onShare(media) }) { Icon(Icons.Outlined.Share, "Share", tint = tint, modifier = Modifier.size(Sizes.iconSmall)) }
        val first = media.first()
        if (!video && first.path != null) {
            IconButton(onClick = { actions.onEdit(first) }) { Icon(Icons.Outlined.AutoFixHigh, if (media.size > 1) "Edit the first image" else "Edit this image", tint = tint, modifier = Modifier.size(Sizes.iconSmall)) }
            if (videoAvailable) IconButton(onClick = { actions.onAnimate(first) }) { Icon(Icons.Outlined.Movie, "Make a video from this image", tint = tint, modifier = Modifier.size(Sizes.iconSmall)) }
        }
    }
}

@Composable
private fun InlineVideo(messageId: String, media: GeneratedMedia, aspectRatio: String?, actions: ImagineActions, initiallyPlaying: Boolean) {
    var playing by remember(media.key) { mutableStateOf(initiallyPlaying) }
    val loader = LocalMediaLoader.current
    var url by remember(media.key) { mutableStateOf<String?>(null) }
    var failed by remember(media.key) { mutableStateOf<String?>(null) }
    val ratio = aspectValue(aspectRatio, 16 / 9f).coerceIn(.5f, 2f)
    if (playing) LaunchedEffect(media.key) {
        if (url == null) try { url = loader.playable(media) ?: run { failed = "Video unavailable."; playing = false; null } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (failure: Exception) { failed = failure.message ?: "Video unavailable."; playing = false }
    }
    val current = url
    if (playing && current != null) {
        Box(Modifier.widthIn(max = if (ratio < 1f) 280.dp else 420.dp).fillMaxWidth().aspectRatio(ratio).clip(DuaShape.card).background(DuaTheme.colors.mediaBackground)) {
            VideoPlayer(current, Modifier.fillMaxSize(), onFullscreen = { actions.onOpen(messageId, 0) })
        }
    } else VideoPoster(media, aspectRatio, onPlay = { failed = null; playing = true })
    failed?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
    if (playing && current == null) LinearProgressIndicator(Modifier.widthIn(max = 420.dp).fillMaxWidth())
}

/** Media3 player view. Released when it leaves composition; pauses when the app goes to the background. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier, autoPlay: Boolean = true, onFullscreen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = autoPlay
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(modifier = modifier, factory = { viewContext ->
        androidx.media3.ui.PlayerView(viewContext).apply {
            this.player = player
            setShowNextButton(false); setShowPreviousButton(false)
            if (onFullscreen != null) setFullscreenButtonClickListener { onFullscreen() }
        }
    })
}

/** Fullscreen viewer for images (swipe between them, pinch or double-tap to zoom) or one video. */
@Composable
fun MediaViewer(media: List<GeneratedMedia>, startIndex: Int, actions: ImagineActions, videoAvailable: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MediaViewerContent(media, startIndex, actions, videoAvailable, onDismiss)
    }
}

@Composable
fun MediaViewerContent(media: List<GeneratedMedia>, startIndex: Int, actions: ImagineActions, videoAvailable: Boolean, onDismiss: () -> Unit,
    playVideo: Boolean = true) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (media.size - 1).coerceAtLeast(0))) { media.size }
    val loader = LocalMediaLoader.current
    Box(Modifier.fillMaxSize().background(DuaTheme.colors.mediaBackground)) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize(), key = { media[it].key }) { page ->
            val item = media[page]
            if (item.isVideo) {
                var url by remember(item.key) { mutableStateOf<String?>(null) }
                if (playVideo) LaunchedEffect(item.key) { url = runCatching { loader.playable(item) }.getOrNull() }
                val current = url
                if (current != null) VideoPlayer(current, Modifier.fillMaxSize())
                else MediaBitmap(item, Modifier.fillMaxSize(), maxPx = 1600, poster = true, contentScale = ContentScale.Fit, background = DuaTheme.colors.mediaBackground)
            } else ZoomableImage(item)
        }
        Row(Modifier.fillMaxWidth().background(DuaTheme.colors.mediaOverlay).safeDrawingPadding().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close viewer", tint = DuaTheme.colors.onMedia) }
            if (media.size > 1) Text("${pager.currentPage + 1} / ${media.size}", color = DuaTheme.colors.onMedia, style = MaterialTheme.typography.titleMedium)
        }
        val current = media.getOrNull(pager.currentPage)
        if (current != null) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(DuaTheme.colors.mediaOverlay).safeDrawingPadding().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            ViewerButton(Icons.Outlined.Download, "Save") { actions.onSave(listOf(current)) }
            ViewerButton(Icons.Outlined.Share, "Share") { actions.onShare(listOf(current)) }
            if (!current.isVideo && current.path != null) {
                ViewerButton(Icons.Outlined.AutoFixHigh, "Edit") { onDismiss(); actions.onEdit(current) }
                if (videoAvailable) ViewerButton(Icons.Outlined.Movie, "Animate") { onDismiss(); actions.onAnimate(current) }
            }
        }
    }
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = DuaShape.card, color = Color.Transparent, contentColor = DuaTheme.colors.onMedia) {
        Column(Modifier.widthIn(min = 72.dp).padding(horizontal = Spacing.sm, vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = DuaTheme.colors.onMedia.copy(alpha = .14f), contentColor = DuaTheme.colors.onMedia) {
                Icon(icon, null, modifier = Modifier.padding(Spacing.md).size(Sizes.iconSmall))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** Pinch to zoom (1–5×), pan while zoomed, double-tap to toggle 2.5×. Swiping pages works at 1×. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(media: GeneratedMedia) {
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offset by remember(media.key) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    val px = with(LocalDensity.current) { 1200.dp.roundToPx() }.coerceAtMost(2048)
    Box(Modifier.fillMaxSize()
        .pointerInput(media.key) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
        .transformable(state, canPan = { scale > 1f })
        .semantics { contentDescription = "Generated image. Pinch or double-tap to zoom" }) {
        MediaBitmap(media, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            maxPx = px, contentScale = ContentScale.Fit, background = DuaTheme.colors.mediaBackground)
    }
}
