package com.dua.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.dua.ai.ui.theme.HomeModelLabelStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Uses the selected provider's live config, never a fixed model version. */
internal fun homeModelName(state: ChatUiState): String {
    state.freeModel?.let { return shortModelLabel(it.name) }
    val configured = state.providers.firstOrNull { it.name == state.model }?.model.orEmpty().ifBlank { state.model }
    return shortModelLabel(configured)
}

/** Shorten known model families, never guess a tier from the hosting transport. */
internal fun shortModelLabel(name: String): String {
    val words = name.lowercase().split(Regex("[^a-z0-9]+"))
    return when {
        "flash" in words -> if ("lite" in words) "Flash Lite" else "Flash"
        "pro" in words -> "Pro"
        else -> name.replace(Regex("\\s*\\(Cloud Run\\)", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Dua" }
    }
}

@Composable
internal fun ModelSelector(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, textStyle: TextStyle = HomeModelLabelStyle) {
    val c = HomeTheme.colors
    Row(modifier.heightIn(min = Sizes.touchTarget).widthIn(min = Sizes.touchTarget)
        .clip(HomeShapes.segmentTab).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Model, $name" }
        .padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = textStyle, color = c.modelLabel, maxLines = 1, modifier = Modifier.width(IntrinsicSize.Max))
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = c.modelLabel, modifier = Modifier.size(Sizes.iconTiny))
    }
}
