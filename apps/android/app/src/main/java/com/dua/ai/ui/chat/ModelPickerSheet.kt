package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.SheetDisabledAlpha
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetSubStyle
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.rememberMotionAllowed

private val MODEL_ORDER = listOf("Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi")

/** Model picker. Only providers the backend reports as configured can be chosen. Letter badges only — no provider logos. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(current: String, providers: List<ProviderCapability>, onSelect: (String) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit,
    freeModels: List<com.dua.ai.model.FreeModel> = emptyList(), currentFree: String? = null, onSelectFree: (String) -> Unit = {}) {
    val c = SheetTheme.colors
    val haptic = LocalChatHaptic.current
    val motion = rememberMotionAllowed()
    DuaSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom)
            .testTag("model-picker-sheet")) {
            SheetTitle("Choose a model")
            Text("Pick who responds in this chat", style = SheetSubStyle, color = c.subText, modifier = Modifier.padding(bottom = SheetSizes.sectionGap))
            SheetGroup {
                MODEL_ORDER.forEachIndexed { index, name ->
                    if (index > 0) SheetDivider()
                    val capability = providers.firstOrNull { it.name == name }
                    val available = capability?.configured == true
                    val provider = when (name) { "ChatGPT" -> "OpenAI"; "Claude" -> "Anthropic"; "Grok" -> "xAI"; "Kimi" -> "Moonshot"; else -> "Gemini" }
                    val selected = current == name && currentFree == null
                    val alpha = if (available) 1f else SheetDisabledAlpha
                    Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin).testTag("model-row-$name")
                        .selectable(selected = selected, enabled = available, role = Role.RadioButton) {
                            haptic(ChatHaptic.Tap)
                            onSelect(name)
                        }
                        .semantics { stateDescription = if (selected) "Selected" else if (available) "Available" else "Setup required" }
                        .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
                        ModelSelectionBadge(name.take(1), selected, motion, alpha)
                        Column(Modifier.weight(1f).padding(start = SheetSizes.rowGap)) {
                            Text(if (name == AUTO_MODEL) "$name · Auto" else name, style = SheetRowStyle, color = c.text.copy(alpha = alpha))
                            Text(if (available) "$provider · ${capability?.model.orEmpty()}" else "$provider · Setup required",
                                style = SheetSubStyle, color = c.subText.copy(alpha = alpha))
                        }
                        Box(Modifier.size(SheetSizes.rowIcon)) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = c.accent)
                        }
                    }
                }
            }
            if (freeModels.isNotEmpty()) {
                Text("Free models", style = SheetRowStyle, color = c.text,
                    modifier = Modifier.padding(top = SheetSizes.sectionGap, bottom = SheetSizes.rowGap).semantics { heading() })
                SheetGroup {
                    freeModels.forEachIndexed { index, model ->
                        if (index > 0) SheetDivider()
                        val selected = currentFree == model.id
                        Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin).testTag("free-model-${model.id}")
                            .selectable(selected = selected, role = Role.RadioButton) {
                                haptic(ChatHaptic.Tap)
                                onSelectFree(model.id)
                                onDismiss()
                            }
                            .semantics { stateDescription = if (selected) "Selected" else "Available" }
                            .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
                            ModelSelectionBadge(model.name.take(1), selected, motion)
                            Column(Modifier.weight(1f).padding(start = SheetSizes.rowGap)) {
                                Text(model.name, style = SheetRowStyle, color = c.text)
                                Text("Free · OpenRouter · chat only", style = SheetSubStyle, color = c.subText)
                            }
                            Box(Modifier.size(SheetSizes.rowIcon)) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = c.accent)
                            }
                        }
                    }
                }
                Text("Free models have daily limits (about 50 replies a day) and read text only. Agent, web and code use Dua.",
                    style = SheetNoteStyle, color = c.subText, modifier = Modifier.padding(top = SheetSizes.rowGap))
            }
            Text("Available models follow backend configuration. Provider keys stay on the server.", style = SheetNoteStyle,
                color = c.subText, modifier = Modifier.padding(top = SheetSizes.sectionGap))
            Row(Modifier.fillMaxWidth().padding(top = SheetSizes.sectionGap), horizontalArrangement = Arrangement.SpaceBetween) {
                SheetTextButton("Feature setup", onOpenSetup, modifier = Modifier.heightIn(min = SheetSizes.touch))
                SheetTextButton("Done", onDismiss, modifier = Modifier.heightIn(min = SheetSizes.touch))
            }
        }
    }
}

@Composable
private fun ModelSelectionBadge(letter: String, selected: Boolean, motion: Boolean, alpha: Float = 1f) {
    val c = SheetTheme.colors
    val fill by animateColorAsState(if (selected) c.accent else c.container,
        if (motion) DuaMotion.gentle() else snap(), label = "Model badge fill")
    val foreground by animateColorAsState((if (selected) c.onAccent else c.text).copy(alpha = alpha),
        if (motion) DuaMotion.gentle() else snap(), label = "Model badge text")
    val corner by animateDpAsState(if (selected) 20.dp else 12.dp,
        if (motion) DuaMotion.gentle() else snap(), label = "Model badge shape")
    Surface(color = fill, shape = RoundedCornerShape(corner)) {
        Box(Modifier.size(SheetSizes.badge), contentAlignment = Alignment.Center) {
            Text(letter, style = SheetRowStyle, color = foreground)
        }
    }
}
