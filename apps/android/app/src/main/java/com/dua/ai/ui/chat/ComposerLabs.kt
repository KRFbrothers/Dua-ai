package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.imagine.ImagineUsage
import com.dua.ai.ui.theme.ComposerModeStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.SheetDisabledAlpha
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/** Honest status line inside the card. Absent unless a connected service reported a real limit. */
data class ComposerUsage(val message: String, val action: String = "See usage")

/** One media or workspace action. Disabled tools keep the reason in [unavailable]. */
data class ComposerTool(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val unavailable: String? = null,
    val onClick: () -> Unit,
)

/** Personal memory switch. Null hides the row when the caller cannot persist it. */
data class ComposerLabs(val checked: Boolean, val enabled: Boolean, val onCheckedChange: (Boolean) -> Unit)

internal fun composerResetLabel(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (epochSeconds <= 0L) return null
    val time = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalTime()
    val hour = time.hour % 12
    val shown = if (hour == 0) 12 else hour
    val minute = time.minute.toString().padStart(2, '0')
    val suffix = if (time.hour < 12) "am" else "pm"
    return "$shown:$minute $suffix"
}

/**
 * Real quota copy only. A non-quota error and missing Imagine usage return null
 * so the caller can choose a fallback that does not invent a reset time.
 */
internal fun composerUsage(error: String?, usage: ImagineUsage?, videoEnabled: Boolean, zone: ZoneId = ZoneId.systemDefault()): ComposerUsage? {
    val quota = error?.trim()?.takeIf { it.isNotEmpty() && (it.contains("limit", ignoreCase = true) || it.contains("quota", ignoreCase = true)) }
    if (quota != null) return ComposerUsage(quota.take(180))
    if (usage == null) return null
    val reset = composerResetLabel(usage.resetAt, zone)
    val imagesExhausted = usage.imageLimit > 0 && usage.images >= usage.imageLimit
    val videoExhausted = videoEnabled && usage.videoLimit > 0 && usage.videoSeconds >= usage.videoLimit
    val reached = when {
        imagesExhausted -> "today's image limit"
        videoExhausted -> "today's video limit"
        else -> null
    }
    if (reached != null) {
        val whenReset = if (reset != null) " It resets at $reset." else ""
        return ComposerUsage("You've reached $reached.$whenReset")
    }
    if (usage.imageLimit > 0 && reset != null) return ComposerUsage("Your image limit resets at $reset")
    if (videoEnabled && usage.videoLimit > 0 && reset != null) return ComposerUsage("Your video limit resets at $reset")
    return null
}

@Composable
internal fun ComposerUsageBanner(usage: ComposerUsage, onUsage: () -> Unit) {
    val colors = HomeTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag("composer-usage-banner"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(usage.message, style = ComposerModeStyle, color = colors.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Row(Modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
            .clip(HomeShapes.segmentTab)
            .clickable(role = Role.Button, onClick = onUsage)
            .testTag("composer-usage-action"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(usage.action, style = ComposerModeStyle, color = colors.segmentSelectedText,
                modifier = Modifier.clip(HomeShapes.segmentTab).background(colors.segmentSelected)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs))
        }
    }
}

@Composable
internal fun homeComposerTools(
    busy: Boolean,
    imagesEnabled: Boolean,
    imageUnavailable: String,
    videosEnabled: Boolean,
    videoUnavailable: String,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onAvatar: () -> Unit,
    onImages: () -> Unit,
    onVideos: () -> Unit,
): List<ComposerTool> {
    val paused = "Unavailable while a reply is in progress"
    fun reason(featureEnabled: Boolean, featureReason: String) = when {
        busy -> paused
        !featureEnabled -> featureReason
        else -> null
    }
    return listOf(
        ComposerTool("Photos", Icons.Outlined.Image, !busy, if (busy) paused else null, onPhotos),
        ComposerTool("Camera", Icons.Outlined.PhotoCamera, !busy, if (busy) paused else null, onCamera),
        ComposerTool("Avatar", Icons.Outlined.AccountCircle, !busy, if (busy) paused else null, onAvatar),
        ComposerTool("Create image", Icons.Outlined.Brush, !busy && imagesEnabled, reason(imagesEnabled, imageUnavailable), onImages),
        ComposerTool("Create video", Icons.Outlined.Movie, !busy && videosEnabled, reason(videosEnabled, videoUnavailable), onVideos),
        ComposerTool("Music", Icons.Outlined.MusicNote, false, "Music generation is not available", {}),
        ComposerTool("Canvas", Icons.Outlined.Dashboard, false, "Canvas opens from a reply that contains an artifact", {}),
        ComposerTool("Guided learning", Icons.Outlined.School, false, "Guided learning is not available", {}),
    )
}

@Composable
internal fun ComposerToolRail(tools: List<ComposerTool>) {
    val colors = HomeTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("composer-tool-rail"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        tools.forEach { tool ->
            val alpha = if (tool.enabled) 1f else SheetDisabledAlpha
            Row(Modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
                .clip(HomeShapes.promptRow).background(colors.promptRow)
                .clickable(enabled = tool.enabled, role = Role.Button, onClick = tool.onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = tool.label
                    tool.unavailable?.let { stateDescription = it }
                }
                .padding(horizontal = Spacing.md)
                .testTag("composer-tool-" + tool.label.lowercase().replace(' ', '-')),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(tool.icon, contentDescription = null, tint = colors.barIcon.copy(alpha = alpha), modifier = Modifier.size(Sizes.iconSmall))
                Text(tool.label, style = ComposerModeStyle, color = colors.input.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ComposerLabsSwitch(labs: ComposerLabs) {
    val colors = HomeTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
        .toggleable(value = labs.checked, enabled = labs.enabled, role = Role.Switch, onValueChange = labs.onCheckedChange)
        .semantics { contentDescription = "Personal memory. Uses saved notes in replies." }
        .testTag("composer-labs"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Personal memory", style = ComposerModeStyle, color = colors.input, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Text("Labs", style = ComposerModeStyle, color = colors.placeholder, maxLines = 1)
        Switch(checked = labs.checked, onCheckedChange = null, enabled = labs.enabled)
    }
}
