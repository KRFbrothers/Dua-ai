package com.dua.ai.ui.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dua.ai.R
import com.dua.ai.ui.theme.DuaColors
import com.dua.ai.ui.theme.DuaVoiceColors
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.VoiceGreetingStyle
import com.dua.ai.ui.theme.VoiceReplyCaptionStyle
import com.dua.ai.ui.theme.VoicePermissionHeadingStyle
import com.dua.ai.ui.theme.voiceTranscriptStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.sin

enum class MicPermission { Granted, Rationale, Blocked }

/** Everything the voice-mode screen shows; the stateful screen builds it, screenshots pass it directly. */
data class VoiceModeUi(
    val phase: VoicePhase = VoicePhase.Idle,
    val muted: Boolean = false,
    val captions: Boolean = true,
    val firstName: String? = null,
    /** Live partial transcript while listening. */
    val heard: String = "",
    /** The last thing the user said in voice mode. */
    val userText: String = "",
    /** The reply being (or last) spoken. */
    val replyText: String = "",
    val notice: String = "",
    val permission: MicPermission = MicPermission.Granted,
    /** 1 = thumbs up, -1 = thumbs down, 0 = none (local only). */
    val feedback: Int = 0,
)

class VoiceModeCallbacks(
    val onOrb: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onCaptions: (Boolean) -> Unit = {},
    val onCamera: () -> Unit = {},
    val onUpload: () -> Unit = {},
    val onFeedback: (Int) -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onAllowMic: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** Greeting with the Google first name, or a neutral one. */
fun voiceGreeting(firstName: String?): String =
    firstName?.trim()?.takeIf { it.isNotEmpty() }?.let { "Hi $it, what's on your mind?" } ?: "Hi, what's on your mind?"

/** First word of a display name ("Fareed Ahmad" → "Fareed"); null when there is nothing usable. */
fun firstNameOf(displayName: String?): String? =
    displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() && it.length <= 40 && !it.contains('@') }

private fun phaseLabel(phase: VoicePhase, muted: Boolean) = when {
    phase == VoicePhase.Listening -> "Listening"
    phase == VoicePhase.Thinking -> "Thinking"
    phase == VoicePhase.Speaking -> "Speaking"
    muted -> "Mic off"
    else -> "Paused"
}

/**
 * Stateless voice-mode screen: near-black (or soft white) canvas with a silver-gold glow rising from the bottom,
 * the DUA greeting or live captions, and the control row (camera · share · orb · mic · close).
 *
 * [level] is the microphone level 0..1, read only while drawing. With [motion] the glow and orb animate on frames
 * while the screen is resumed; without it (Remove animations, screenshots) they draw one static frame at [staticTime].
 */
@Composable
fun VoiceModeContent(
    ui: VoiceModeUi,
    callbacks: VoiceModeCallbacks,
    level: () -> Float,
    motion: Boolean,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    staticTime: Float = 1.25f,
) {
    val dark = DuaTheme.colors.isDark
    val time = remember { mutableFloatStateOf(staticTime) }
    val smooth = remember { mutableFloatStateOf(0f) }
    if (motion) {
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = 0L
                while (true) {
                    androidx.compose.runtime.withFrameNanos { now ->
                        val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
                        last = now
                        time.floatValue += dt
                        smooth.floatValue += (level().coerceIn(0f, 1f) - smooth.floatValue) * (dt * 9f).coerceAtMost(1f)
                    }
                }
            }
        }
    }
    val phase = ui.phase
    // Energy 0..1 that drives glow and orb: mic level while listening, a synthetic voice envelope while speaking.
    val energy: () -> Float = {
        val t = time.floatValue
        when (phase) {
            VoicePhase.Listening -> if (motion) smooth.floatValue else level().coerceIn(0f, 1f)
            VoicePhase.Speaking -> (.55f + .3f * sin(t * 6.1f) * sin(t * 2.3f + 1f) + .1f * sin(t * 13f)).coerceIn(.2f, 1f)
            VoicePhase.Thinking -> .32f + .08f * sin(t * 2.4f)
            VoicePhase.Idle -> if (ui.muted) .05f else .14f + .04f * sin(t * 1.2f)
        }
    }
    val colors = DuaTheme.colors
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.voice.background)
            .drawBehind { drawGlow(colors, energy(), ui.muted) },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopRow(ui, callbacks)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.xxl)) {
                val conversation = ui.userText.isNotBlank() || ui.replyText.isNotBlank()
                when {
                    ui.permission != MicPermission.Granted -> PermissionRationale(ui.permission, callbacks, Modifier.align(Alignment.Center))
                    ui.captions && conversation -> Captions(ui, callbacks, Modifier.fillMaxSize())
                    else -> Greeting(ui, conversation, Modifier.align(BiasAlignment(0f, -.18f)))
                }
            }
            BottomStatus(ui)
            SnackbarHost(snackbar, Modifier.fillMaxWidth()) { data ->
                Snackbar(data, modifier = Modifier.padding(horizontal = Spacing.lg), shape = RoundedCornerShape(16.dp),
                    containerColor = if (dark) scheme.surfaceContainerHighest else scheme.inverseSurface,
                    contentColor = if (dark) scheme.onSurface else scheme.inverseOnSurface)
            }
            Controls(ui, callbacks, energy, time, Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xl))
        }
    }
}

private fun DrawScope.drawGlow(colors: DuaColors, energy: Float, muted: Boolean) {
    val e = if (muted) energy * .4f else energy
    val gold = colors.sparkle
    val silver = colors.voice.glowSilver
    val center = Offset(size.width / 2f, size.height * 1.02f)
    val radius = size.width * (1.05f + .35f * e)
    val strength = if (colors.isDark) 1f else .65f
    drawRect(Brush.radialGradient(
        0f to colors.voice.highlight.copy(alpha = (.16f + .18f * e) * strength),
        .22f to gold.copy(alpha = (.26f + .24f * e) * strength),
        .5f to silver.copy(alpha = (.08f + .08f * e) * strength),
        1f to Color.Transparent,
        center = center, radius = radius,
    ))
    // A low, wide wash so the glow reads as light rising from the bottom edge, not a spot.
    drawRect(Brush.verticalGradient(
        0f to Color.Transparent, .72f to Color.Transparent,
        1f to gold.copy(alpha = (.10f + .10f * e) * strength)))
}

@Composable
private fun TopRow(ui: VoiceModeUi, callbacks: VoiceModeCallbacks) {
    Row(Modifier.fillMaxWidth().height(Sizes.topBarHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(ui.phase, ui.muted, ui.permission == MicPermission.Granted)
        Spacer(Modifier.weight(1f))
        IconToggleButton(
            checked = ui.captions,
            onCheckedChange = callbacks.onCaptions,
            modifier = Modifier.size(Sizes.touchTarget).semantics { stateDescription = if (ui.captions) "On" else "Off" },
        ) {
            Icon(if (ui.captions) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled, contentDescription = "Captions",
                tint = if (ui.captions) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(phase: VoicePhase, muted: Boolean, granted: Boolean) {
    val label = if (!granted) "Mic needed" else phaseLabel(phase, muted)
    val dot = when (phase) {
        VoicePhase.Listening -> DuaTheme.colors.sparkle
        VoicePhase.Speaking -> MaterialTheme.colorScheme.onSurface
        VoicePhase.Thinking -> DuaTheme.colors.sparkle.copy(alpha = .6f)
        VoicePhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(DuaTheme.colors.voice.statusSurface)
            .padding(horizontal = Spacing.md, vertical = 6.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = "Voice mode: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DuaLogo(height: Dp) {
    if (DuaTheme.colors.isDark) Image(painterResource(R.drawable.dua_logo_home), contentDescription = "Dua", modifier = Modifier.height(height))
    else Image(painterResource(R.drawable.dua_logo_home_light), contentDescription = "Dua", modifier = Modifier.height(height * 1.04f))
}

@Composable
private fun Greeting(ui: VoiceModeUi, conversation: Boolean, modifier: Modifier) {
    Column(modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DuaLogo(88.dp)
        Spacer(Modifier.height(Spacing.xxl))
        val text = if (!conversation) voiceGreeting(ui.firstName) else when (ui.phase) {
            VoicePhase.Listening -> "I'm listening…"
            VoicePhase.Thinking -> "Thinking…"
            VoicePhase.Speaking -> "Speaking…"
            VoicePhase.Idle -> "Tap the orb to keep talking"
        }
        Text(text, style = VoiceGreetingStyle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Captions(ui: VoiceModeUi, callbacks: VoiceModeCallbacks, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.replyText) { scroll.scrollTo(0) }
    Column(modifier.verticalScroll(scroll).padding(top = Spacing.sm, bottom = Spacing.lg)) {
        if (ui.userText.isNotBlank()) {
            Text(ui.userText, style = voiceTranscriptStyle(MaterialTheme.typography),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = "You said: ${ui.userText}" })
            Spacer(Modifier.height(Spacing.lg))
        }
        when {
            ui.phase == VoicePhase.Thinking -> Text("Thinking…", style = VoiceReplyCaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.replyText.isNotBlank() -> {
                Text(ui.replyText, style = VoiceReplyCaptionStyle, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(Spacing.md))
                ReplyActions(ui.feedback, callbacks)
            }
        }
    }
}

@Composable
private fun ReplyActions(feedback: Int, callbacks: VoiceModeCallbacks) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
        ActionIcon(if (feedback == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Good response", tint, selected = feedback == 1) { callbacks.onFeedback(if (feedback == 1) 0 else 1) }
        ActionIcon(if (feedback == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Bad response", tint, selected = feedback == -1) { callbacks.onFeedback(if (feedback == -1) 0 else -1) }
        ActionIcon(Icons.Outlined.Share, "Share reply", tint) { callbacks.onShare() }
        ActionIcon(Icons.Outlined.ContentCopy, "Copy reply", tint) { callbacks.onCopy() }
        Box {
            ActionIcon(Icons.Outlined.MoreVert, "More", tint) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open in chat") }, onClick = { menu = false; callbacks.onOpenChat() })
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, description: String, tint: Color, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Sizes.touchTarget).semantics { if (selected) stateDescription = "Selected" }) {
        Icon(icon, contentDescription = description, tint = if (selected) MaterialTheme.colorScheme.onSurface else tint, modifier = Modifier.size(Sizes.iconSmall))
    }
}

@Composable
private fun BottomStatus(ui: VoiceModeUi) {
    val heard = ui.heard.takeIf { ui.captions && ui.phase == VoicePhase.Listening && it.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (heard != null) Text(heard, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = Spacing.sm))
        if (ui.notice.isNotBlank()) Text(ui.notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun Controls(ui: VoiceModeUi, callbacks: VoiceModeCallbacks, energy: () -> Float, time: androidx.compose.runtime.FloatState, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        val circle = min(56.dp, width / 6.6f).coerceAtLeast(Sizes.touchTarget)
        val orbWidth = min(circle * 2.5f, width - circle * 4 - 6.dp * 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlCircle(Icons.Outlined.Videocam, "Camera, coming soon", circle, onClick = callbacks.onCamera)
            ControlCircle(Icons.AutoMirrored.Outlined.ScreenShare, "Share screen or upload, coming soon", circle, onClick = callbacks.onUpload)
            VoiceOrb(ui.phase, ui.muted, energy, time, Modifier.width(orbWidth).height(circle * 1.2f), callbacks.onOrb)
            ControlCircle(if (ui.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Microphone", circle, selected = ui.muted,
                state = if (ui.muted) "Muted" else "On", onClick = callbacks.onMute)
            ControlCircle(Icons.Outlined.Close, "Close voice mode", circle, onClick = callbacks.onClose)
        }
    }
}

@Composable
private fun ControlCircle(icon: ImageVector, description: String, size: Dp, selected: Boolean = false, state: String? = null, onClick: () -> Unit) {
    val dark = DuaTheme.colors.isDark
    val container = when {
        selected -> DuaTheme.colors.chipSelected
        dark -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }
    val content = if (selected) DuaTheme.colors.onChipSelected else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (!dark && !selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.size(size).semantics { contentDescription = description; role = Role.Button; if (state != null) stateDescription = state },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
    }
}

/**
 * The central orb pill: a dark capsule holding a glowing silver-gold liquid whose wave follows the voice energy.
 * Listening breathes with the mic, Thinking adds a moving shimmer, Speaking rolls a stronger wave; dim when muted.
 */
@Composable
fun VoiceOrb(phase: VoicePhase, muted: Boolean, energy: () -> Float, time: androidx.compose.runtime.FloatState, modifier: Modifier, onClick: () -> Unit) {
    val description = when (phase) {
        VoicePhase.Idle -> "Talk to Dua"
        VoicePhase.Listening -> "Listening. Tap to pause"
        VoicePhase.Thinking -> "Thinking. Tap to stop and talk"
        VoicePhase.Speaking -> "Speaking. Tap to interrupt"
    }
    val colors = DuaTheme.colors.voice
    Box(
        modifier
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = colors.highlight), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .drawBehind { drawOrb(phase, muted, energy(), time.floatValue, colors) },
    )
}

private fun DrawScope.drawOrb(phase: VoicePhase, muted: Boolean, energy: Float, t: Float, colors: DuaVoiceColors) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(colors.orbSurfaceTop, colors.orbSurfaceBottom)))
    val fill = when (phase) {
        VoicePhase.Idle -> .34f
        VoicePhase.Listening -> .36f + .28f * energy
        VoicePhase.Thinking -> .42f
        VoicePhase.Speaking -> .40f + .30f * energy
    }
    val amp = h * when (phase) {
        VoicePhase.Idle -> .035f
        VoicePhase.Listening -> .04f + .10f * energy
        VoicePhase.Thinking -> .05f
        VoicePhase.Speaking -> .07f + .13f * energy
    }
    val speed = when (phase) { VoicePhase.Speaking -> 3.2f; VoicePhase.Listening -> 2.2f; else -> 1.2f }
    val alpha = if (muted) .45f else 1f
    fun wave(shift: Float, scale: Float, level: Float): Path = Path().apply {
        moveTo(0f, h)
        val steps = 32
        for (i in 0..steps) {
            val x = w * i / steps
            val p = x / w
            val y = h * (1f - level) + amp * scale * (sin(2f * PI.toFloat() * (p * 1.15f) + t * speed + shift) * .7f + sin(2f * PI.toFloat() * (p * 2.3f) - t * speed * .8f + shift * 2f) * .3f)
            lineTo(x, y)
        }
        lineTo(w, h); close()
    }
    drawPath(wave(2.1f, .8f, fill + .07f), Brush.verticalGradient(
        listOf(colors.orbSilverLow.copy(alpha = .35f * alpha), colors.orbSilverHigh.copy(alpha = .55f * alpha)), startY = h * (1f - fill - .1f), endY = h))
    drawPath(wave(0f, 1f, fill), Brush.verticalGradient(
        0f to colors.orbGold.copy(alpha = .95f * alpha),
        .45f to colors.orbCream.copy(alpha = alpha),
        1f to colors.highlight.copy(alpha = alpha),
        startY = h * (1f - fill - .08f), endY = h))
    if (phase == VoicePhase.Thinking && !muted) {
        val p = (t * .55f) % 1f
        val cx = -w * .4f + p * w * 1.8f
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, colors.highlight.copy(alpha = .38f), Color.Transparent), startX = cx - w * .22f, endX = cx + w * .22f))
    }
    // Soft inner rim so the capsule reads on both themes.
    drawRoundRect(color = colors.orbRim,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f), style = Stroke(width = 1.dp.toPx()))
}

@Composable
private fun PermissionRationale(permission: MicPermission, callbacks: VoiceModeCallbacks, modifier: Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = DuaTheme.colors.composer,
        border = BorderStroke(1.dp, DuaTheme.colors.composerBorder),
    ) {
        Column(Modifier.padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, DuaTheme.colors.sparkle.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Let Dua hear you", style = VoicePermissionHeadingStyle, color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            val body = if (permission == MicPermission.Blocked)
                "Microphone access is turned off for Dua. Turn it on in Settings › Permissions › Microphone, then come back."
            else "Voice mode uses your microphone only while this screen is open. Your phone's speech service turns what you say into text, and Dua answers out loud."
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xl))
            Surface(
                onClick = if (permission == MicPermission.Blocked) callbacks.onOpenSettings else callbacks.onAllowMic,
                shape = CircleShape, color = DuaTheme.colors.action, contentColor = DuaTheme.colors.onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Spacing.md)) {
                    Text(if (permission == MicPermission.Blocked) "Open settings" else "Allow microphone", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = callbacks.onClose, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
