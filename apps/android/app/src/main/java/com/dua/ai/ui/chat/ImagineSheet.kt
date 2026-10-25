package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.imagine.ImagineCodec
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.imagine.ImagineOptions
import com.dua.ai.imagine.ImagineUsage
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.SheetTileStyle
import androidx.compose.material.icons.filled.Check
import com.dua.ai.ui.theme.Spacing

/** Imagine options: Image or Video, aspect ratio, count or duration/resolution, provider, and today's usage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagineSheet(options: ImagineOptions, config: ImagineConfig, usage: ImagineUsage?, hasSource: Boolean,
    onChange: (ImagineOptions) -> Unit, onDismiss: () -> Unit, polish: PromptPolishState? = null, onPolish: () -> Unit = {}) {
    DuaSheet(onDismiss = onDismiss) {
        ImagineOptionsContent(options, config, usage, hasSource, onChange, onDismiss, polish = polish, onPolish = onPolish)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImagineOptionsContent(options: ImagineOptions, config: ImagineConfig, usage: ImagineUsage?, hasSource: Boolean,
    onChange: (ImagineOptions) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    polish: PromptPolishState? = null, onPolish: () -> Unit = {}) {
    val video = options.kind == ImagineInfo.VIDEO
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SheetTitle("Imagine")
        SheetGroup {
            listOf(ImagineInfo.IMAGE to "Image", ImagineInfo.VIDEO to "Video").forEachIndexed { index, (kind, label) ->
                val enabled = if (kind == ImagineInfo.VIDEO) config.videoEnabled else config.imageEnabled
                val selected = options.kind == kind
                if (index > 0) SheetDivider()
                SheetActionRow(label, if (kind == ImagineInfo.VIDEO) Icons.Outlined.Movie else Icons.Outlined.Image,
                    onClick = { onChange(options.copy(kind = kind)) }, enabled = enabled,
                    trailing = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, tint = SheetTheme.colors.accent, modifier = Modifier.size(SheetSizes.rowIcon)) }) else null,
                    modifier = Modifier.semantics { this.selected = selected; role = Role.RadioButton; contentDescription = "Create $label" })
            }
        }
        val reason = if (video) config.videoReason else config.imageReason
        if (!config.videoEnabled && config.videoReason.isNotBlank() && !video) Hint("Video: ${config.videoReason}")
        if (reason.isNotBlank() && (if (video) !config.videoEnabled else !config.imageEnabled)) Hint(reason)
        if (polish != null) PromptPolishRow(polish, onPolish)
        if (hasSource) Hint(if (video) "Your photo becomes the first frame. Auto keeps its shape." else "Your photo will be edited. Describe the change.")

        Label("Aspect ratio")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (if (video) config.videoAspects else config.imageAspects).forEach { aspect ->
                OptionChip(ImagineCodec.aspectLabel(aspect), options.aspectRatio == aspect, "Aspect ratio ${ImagineCodec.aspectLabel(aspect)}",
                    leading = { AspectGlyph(aspect) }) { onChange(options.copy(aspectRatio = aspect)) }
            }
        }
        if (video) {
            Label("Length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                config.durations.forEach { seconds -> OptionChip("$seconds s", options.duration == seconds, "$seconds seconds") { onChange(options.copy(duration = seconds)) } }
            }
            Label("Resolution")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                config.resolutions.forEach { value -> OptionChip(value, options.resolution == value, "Resolution $value") { onChange(options.copy(resolution = value)) } }
            }
        } else {
            Label("Images")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (1..config.maxImages).forEach { count -> OptionChip("$count", options.count == count, "$count ${if (count == 1) "image" else "images"}") { onChange(options.copy(count = count)) } }
            }
            if (config.providers.size > 1) {
                Label("Model")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    config.providers.forEach { provider ->
                        val selected = (options.provider ?: config.defaultProvider) == provider.id
                        OptionChip(provider.name, selected, "Use ${provider.name}") { onChange(options.copy(provider = provider.id)) }
                    }
                }
            }
        }
        usage?.let {
            Text(if (video) "Today: ${it.videoSeconds} of ${it.videoLimit} video seconds used" else "Today: ${it.images} of ${it.imageLimit} images used",
                style = SheetNoteStyle, color = SheetTheme.colors.subText)
        }
        Text(if (video) "Videos take a minute or two. You can leave the chat; progress continues." else "Results are saved to your private storage. Deleting the chat deletes them.",
            style = SheetNoteStyle, color = SheetTheme.colors.subText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SheetTextButton("Done", onClick = onDismiss) }
    }
}

/** On-device prompt polish. [canPolish] is false while the draft is empty. Text label only; no brand art. */
data class PromptPolishState(val busy: Boolean = false, val notice: String = "", val canPolish: Boolean = true)

@Composable
private fun PromptPolishRow(polish: PromptPolishState, onPolish: () -> Unit) {
    SheetGroup {
        SheetActionRow(if (polish.busy) "Polishing your prompt…" else "Polish my prompt", Icons.Outlined.AutoFixHigh,
            onClick = onPolish, enabled = polish.canPolish && !polish.busy,
            trailing = { Text("On-device", style = SheetNoteStyle, color = SheetTheme.colors.subText) },
            modifier = Modifier.testTag("imagine-polish"))
    }
    Hint(polish.notice.ifBlank { if (polish.canPolish) "Rewrites your prompt with more detail on this phone. Nothing is sent." else "Type an idea in the message box first." })
}

@Composable
private fun Label(text: String) = Text(text, style = SheetTileStyle, color = SheetTheme.colors.subText, modifier = Modifier.semantics { heading() })

@Composable
private fun Hint(text: String) = Text(text, style = SheetNoteStyle, color = SheetTheme.colors.subText)

@Composable
private fun OptionChip(label: String, selected: Boolean, description: String, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = SheetTheme.colors
    Surface(onClick = onClick, shape = SheetShapes.badge,
        color = if (selected) c.selectedFill else c.fill,
        contentColor = if (selected) c.accent else c.text,
        border = if (selected) BorderStroke(SheetSizes.dividerThickness, c.accent) else null,
        modifier = Modifier.semantics { contentDescription = description; this.selected = selected; role = Role.RadioButton }) {
        Row(Modifier.heightIn(min = SheetSizes.touch).padding(horizontal = SheetSizes.rowPaddingH), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Box(Modifier.width(Spacing.sm)) }
            Text(label, style = SheetTileStyle)
        }
    }
}

/** A small outlined rectangle in the given ratio (a dashed-looking square for Auto). */
@Composable
private fun AspectGlyph(aspect: String) {
    val ratio = aspectValue(aspect, 1f)
    val (w, h) = if (ratio >= 1f) 16f to 16f / ratio else 16f * ratio to 16f
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(w.dp).height(h.dp).border(1.5.dp, androidx.compose.material3.LocalContentColor.current, if (aspect == "auto") DuaShape.chip else androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
    }
}
