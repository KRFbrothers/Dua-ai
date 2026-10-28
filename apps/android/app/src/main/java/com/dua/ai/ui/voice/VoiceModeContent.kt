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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import com.dua.ai.R
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.ui.graphics.Color
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
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.VoiceGreetingStyle
import com.dua.ai.ui.theme.VoiceReplyCaptionStyle
import com.dua.ai.ui.theme.VoicePermissionHeadingStyle
import com.dua.ai.ui.theme.voiceTranscriptStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.VoiceShapes
import com.dua.ai.ui.theme.VoiceSizes
import com.dua.ai.ui.theme.VoiceTheme
import com.dua.ai.voice.VoicePhase
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
    val cameraAttached: Boolean = false,
    val cameraAttachmentName: String = "",
)

class VoiceModeCallbacks(
    val onOrb: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onCaptions: (Boolean) -> Unit = {},
    val onCamera: () -> Unit = {},
    val onClearCamera: () -> Unit = {},
    val onUpload: () -> Unit = {},
    val onFeedback: (Int) -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onAllowMic: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onPhotos: () -> Unit = {},
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
 * the DUA greeting or live captions, and the control row (attachments · camera · orb · mic · close).
 *
 * [level] is the microphone level 0..1, read only while drawing. With [motion] the glow and orb animate on frames
 * while the screen is resumed; without it (Remove animations, screenshots) they draw one static frame at [staticTime].
 */
@Composable
fun VoiceModeContent(
    ui: VoiceModeUi,
    callbacks: VoiceModeCallbacks,
    level: VoiceLevel,
    motion: Boolean,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    staticTime: Float = 1.25f,
    hapticEnabled: Boolean = true,
) {
    val time = remember { mutableFloatStateOf(staticTime) }
    val smooth = remember { mutableFloatStateOf(0f) }
    if (motion) {
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = 0L
                val advance: (Long) -> Unit = { now ->
                        val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
                        last = now
                        time.floatValue += dt
                        smooth.floatValue += (level.read().coerceIn(0f, 1f) - smooth.floatValue) * (dt * 9f).coerceAtMost(1f)
                }
                while (true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos(advance)
            }
        }
    }
    VoiceSpeakingFeedback(ui.phase == VoicePhase.Speaking, hapticEnabled)
    val phase = ui.phase
    // Energy 0..1 that drives glow and orb: mic level while listening, a synthetic voice envelope while speaking.
    val energy = VoiceLevel {
        val t = time.floatValue
        when (phase) {
            VoicePhase.Listening -> if (motion) smooth.floatValue else level.read().coerceIn(0f, 1f)
            VoicePhase.Speaking -> (.55f + .3f * sin(t * 6.1f) * sin(t * 2.3f + 1f) + .1f * sin(t * 13f)).coerceIn(.2f, 1f)
            VoicePhase.Thinking -> .32f + .08f * sin(t * 2.4f)
            VoicePhase.Idle -> if (ui.muted) .05f else .14f + .04f * sin(t * 1.2f)
        }
    }
    val colors = DuaTheme.colors
    val v = VoiceTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.voice.background),
    ) {
        VoiceFrequencyWaveform(energy, phase == VoicePhase.Speaking, ui.muted, Modifier.matchParentSize(), frame = time, motion = motion, glowOnly = true)
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
            if (ui.cameraAttached) {
                CameraVisionCard(
                    name = ui.cameraAttachmentName,
                    onClear = callbacks.onClearCamera,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xs)
                )
            }
            BottomStatus(ui)
            SnackbarHost(snackbar, Modifier.fillMaxWidth()) { data ->
                Snackbar(data, modifier = Modifier.padding(horizontal = Spacing.lg), shape = VoiceShapes.snackbar,
                    containerColor = v.snackbar, contentColor = v.onSnackbar)
            }
            Controls(ui, callbacks, energy, time, Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xl))
        }
    }
}

@Composable
private fun TopRow(ui: VoiceModeUi, callbacks: VoiceModeCallbacks) {
    Row(Modifier.fillMaxWidth().height(Sizes.topBarHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(ui.phase, ui.muted, ui.permission == MicPermission.Granted)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = callbacks.onOpenChat, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in chat", tint = VoiceTheme.colors.text)
        }
        IconToggleButton(
            checked = ui.captions,
            onCheckedChange = callbacks.onCaptions,
            modifier = Modifier.size(Sizes.touchTarget).semantics { stateDescription = if (ui.captions) "On" else "Off" },
        ) {
            Icon(if (ui.captions) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled, contentDescription = "Captions",
                tint = if (ui.captions) VoiceTheme.colors.text else VoiceTheme.colors.subText)
        }
    }
}

@Composable
private fun StatusPill(phase: VoicePhase, muted: Boolean, granted: Boolean) {
    val label = if (!granted) "Mic needed" else phaseLabel(phase, muted)
    val dot = when (phase) {
        VoicePhase.Listening -> DuaTheme.colors.sparkle
        VoicePhase.Speaking -> VoiceTheme.colors.text
        VoicePhase.Thinking -> DuaTheme.colors.sparkle.copy(alpha = .6f)
        VoicePhase.Idle -> VoiceTheme.colors.subText
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(DuaTheme.colors.voice.statusSurface)
            .padding(horizontal = Spacing.md, vertical = VoiceSizes.statusPaddingV)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = "Voice mode: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (phase == VoicePhase.Speaking) VoiceSpeakingIndicator()
        else Icon(painterResource(R.drawable.ls_gemini_live_wght320_vd_theme_28), null,
            tint = dot, modifier = Modifier.size(Sizes.iconSmall))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = VoiceTheme.colors.subText)
    }
}

@Composable
private fun DuaLogo(height: Dp) {
    if (DuaTheme.colors.isDark) Image(painterResource(R.drawable.dua_logo_home), contentDescription = "Dua", modifier = Modifier.height(height))
    else Image(painterResource(R.drawable.dua_logo_home_light), contentDescription = "Dua", modifier = Modifier.height(height * 1.04f))
}

@Composable
private fun Greeting(ui: VoiceModeUi, conversation: Boolean, modifier: Modifier) {
    Column(modifier.widthIn(max = VoiceSizes.contentMaxWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        DuaLogo(VoiceSizes.logo)
        Spacer(Modifier.height(Spacing.xxl))
        val text = if (!conversation) voiceGreeting(ui.firstName) else when (ui.phase) {
            VoicePhase.Listening -> "I'm listening…"
            VoicePhase.Thinking -> "Thinking…"
            VoicePhase.Speaking -> "Speaking…"
            VoicePhase.Idle -> "Tap the orb to keep talking"
        }
        Text(text, style = VoiceGreetingStyle, textAlign = TextAlign.Center, color = VoiceTheme.colors.text,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Captions(ui: VoiceModeUi, callbacks: VoiceModeCallbacks, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.replyText) { scroll.scrollTo(0) }
    Column(modifier.verticalScroll(scroll).padding(top = Spacing.sm, bottom = Spacing.lg)) {
        if (ui.userText.isNotBlank()) {
            Surface(shape = com.dua.ai.ui.theme.DuaShape.userBubble, color = com.dua.ai.ui.theme.ChatTheme.colors.userBubble,
                modifier = Modifier.align(Alignment.End).widthIn(max = VoiceSizes.contentMaxWidth)) {
                Text(ui.userText, style = voiceTranscriptStyle(MaterialTheme.typography), color = VoiceTheme.colors.text,
                    modifier = Modifier.padding(Spacing.md).semantics { contentDescription = "You said: ${ui.userText}" })
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        when {
            ui.phase == VoicePhase.Thinking -> Text("Thinking…", style = VoiceReplyCaptionStyle, color = VoiceTheme.colors.subText)
            ui.replyText.isNotBlank() -> {
                Text(ui.replyText, style = VoiceReplyCaptionStyle, color = VoiceTheme.colors.text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(Spacing.md))
                ReplyActions(ui.feedback, callbacks)
            }
        }
    }
}

@Composable
private fun ReplyActions(feedback: Int, callbacks: VoiceModeCallbacks) {
    val tint = VoiceTheme.colors.subText
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.offset(x = VoiceSizes.replyActionsOffset), verticalAlignment = Alignment.CenterVertically) {
        ActionIcon(if (feedback == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Good response", tint, selected = feedback == 1) { callbacks.onFeedback(if (feedback == 1) 0 else 1) }
        ActionIcon(if (feedback == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Bad response", tint, selected = feedback == -1) { callbacks.onFeedback(if (feedback == -1) 0 else -1) }
        ActionIcon(Icons.Outlined.Share, "Share reply", tint) { callbacks.onShare() }
        ActionIcon(Icons.Outlined.ContentCopy, "Copy reply", tint) { callbacks.onCopy() }
        Box {
            ActionIcon(Icons.Outlined.MoreVert, "More", tint) { menu = true }
            com.dua.ai.ui.chat.DuaDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                com.dua.ai.ui.chat.DuaDropdownItem("Open in chat", icon = Icons.AutoMirrored.Outlined.OpenInNew, onClick = { menu = false; callbacks.onOpenChat() })
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, description: String, tint: Color, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Sizes.touchTarget).semantics { if (selected) stateDescription = "Selected" }) {
        Icon(icon, contentDescription = description, tint = if (selected) VoiceTheme.colors.text else tint, modifier = Modifier.size(Sizes.iconSmall))
    }
}

@Composable
private fun BottomStatus(ui: VoiceModeUi) {
    val heard = ui.heard.takeIf { ui.captions && ui.phase == VoicePhase.Listening && it.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (heard != null) Text(heard, style = MaterialTheme.typography.bodyLarge, color = VoiceTheme.colors.subText,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = Spacing.sm))
        if (ui.notice.isNotBlank()) Text(ui.notice, style = MaterialTheme.typography.bodyMedium, color = VoiceTheme.colors.subText,
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Controls(ui: VoiceModeUi, callbacks: VoiceModeCallbacks, energy: VoiceLevel, time: androidx.compose.runtime.FloatState, modifier: Modifier) {
    var attachments by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        val circle = min(VoiceSizes.controlMax, width / 6.6f).coerceAtLeast(Sizes.touchTarget)
        val orbWidth = min(circle * 2.5f, width - circle * 4 - VoiceSizes.controlGap * 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlCircle(Icons.Outlined.Add, "Add to voice conversation", circle, onClick = { attachments = true })
            ControlCircle(Icons.Outlined.PhotoCamera, if (ui.cameraAttached) "Camera, photo attached" else "Take a photo", circle, selected = ui.cameraAttached, onClick = callbacks.onCamera)
            VoiceOrb(ui.phase, ui.muted, energy, time, Modifier.width(orbWidth).height(circle * 1.2f), callbacks.onOrb)
            ControlCircle(if (ui.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Microphone", circle, selected = ui.muted,
                state = if (ui.muted) "Muted" else "On", onClick = callbacks.onMute)
            ControlCircle(Icons.Outlined.Close, "Close voice mode", circle, onClick = callbacks.onClose)
        }
    }
    if (attachments) com.dua.ai.ui.chat.DuaSheet(onDismiss = { attachments = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            com.dua.ai.ui.chat.SheetTitle("Add to voice conversation")
            com.dua.ai.ui.chat.SheetActionRow("Photos", Icons.Outlined.PhotoLibrary, { attachments = false; callbacks.onPhotos() })
            com.dua.ai.ui.chat.SheetActionRow("Camera", Icons.Outlined.PhotoCamera, { attachments = false; callbacks.onCamera() })
            com.dua.ai.ui.chat.SheetActionRow("Files", Icons.Outlined.Folder, { attachments = false; callbacks.onOpenChat() }, detail = "Attach documents in chat")
            com.dua.ai.ui.chat.SheetActionRow("Share screen", Icons.AutoMirrored.Outlined.ScreenShare, {}, enabled = false, detail = "Screen sharing is not available in this voice mode")
        }
    }

}

@Composable
private fun ControlCircle(icon: ImageVector, description: String, size: Dp, selected: Boolean = false, state: String? = null, onClick: () -> Unit) {
    val v = VoiceTheme.colors
    val container = if (selected) v.selected else v.control
    val content = if (selected) v.onSelected else v.text
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (!selected) BorderStroke(VoiceSizes.border, v.controlBorder) else null,
        modifier = Modifier.size(size).semantics { contentDescription = description; role = Role.Button; if (state != null) stateDescription = state },
    ) {
        Box(contentAlignment = Alignment.Center) { // Decorative: the circle itself carries the label, role and state for TalkBack.
        Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
    }
}

/** Floating card indicating that camera vision is active for the current voice interaction. */
@Composable
private fun CameraVisionCard(name: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DuaTheme.colors
    val v = VoiceTheme.colors
    Surface(
        shape = VoiceShapes.snackbar,
        color = colors.voice.statusSurface,
        border = BorderStroke(1.dp, colors.sparkle.copy(alpha = .45f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                Modifier.size(Sizes.icon).clip(CircleShape).background(colors.sparkle.copy(alpha = .18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = colors.sparkle, modifier = Modifier.size(Sizes.iconSmall))
            }
            Column(Modifier.weight(1f)) {
                Text("Vision Active", style = MaterialTheme.typography.labelMedium, color = colors.sparkle)
                Text(
                    name.ifBlank { "Camera photo ready" },
                    style = MaterialTheme.typography.bodySmall,
                    color = v.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove photo", tint = v.subText, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
    }
}

/**
 * The central orb pill: a dark capsule holding a glowing silver-gold liquid whose wave follows the voice energy.
 * Listening breathes with the mic, Thinking adds a moving shimmer, Speaking rolls a stronger wave; dim when muted.
 */
@Composable
fun VoiceOrb(phase: VoicePhase, muted: Boolean, energy: VoiceLevel, time: androidx.compose.runtime.FloatState, modifier: Modifier, onClick: () -> Unit) {
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
            .background(colors.orbSurfaceBottom),
    ) {
        VoiceFrequencyWaveform(energy, phase == VoicePhase.Speaking, muted, Modifier.matchParentSize(), frame = time, waveColor = colors.highlight)
    }
}

@Composable
private fun PermissionRationale(permission: MicPermission, callbacks: VoiceModeCallbacks, modifier: Modifier) {
    Surface(
        modifier = modifier.widthIn(max = VoiceSizes.contentMaxWidth).fillMaxWidth(),
        shape = VoiceShapes.permissionCard,
        color = DuaTheme.colors.composer,
        border = BorderStroke(VoiceSizes.border, DuaTheme.colors.composerBorder),
    ) {
        Column(Modifier.padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(VoiceSizes.permissionBadge).clip(CircleShape).background(VoiceTheme.colors.iconFill)
                .border(VoiceSizes.border, DuaTheme.colors.sparkle.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = VoiceTheme.colors.text, modifier = Modifier.size(VoiceSizes.permissionIcon))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Let Dua hear you", style = VoicePermissionHeadingStyle, color = VoiceTheme.colors.text,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            val body = if (permission == MicPermission.Blocked)
                "Microphone access is turned off for Dua. Turn it on in Settings › Permissions › Microphone, then come back."
            else "Voice mode uses your microphone only while this screen is open. Your phone's speech service turns what you say into text, and Dua answers out loud."
            Text(body, style = MaterialTheme.typography.bodyMedium, color = VoiceTheme.colors.subText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xl))
            Surface(
                onClick = if (permission == MicPermission.Blocked) callbacks.onOpenSettings else callbacks.onAllowMic,
                shape = CircleShape, color = VoiceTheme.colors.accent, contentColor = VoiceTheme.colors.onAccent,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Spacing.md)) {
                    Text(if (permission == MicPermission.Blocked) "Open settings" else "Allow microphone", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = callbacks.onClose, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Not now", color = VoiceTheme.colors.subText) }
        }
    }
}
