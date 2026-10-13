package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.screens.AnimatedVoiceCube
import com.dua.ai.ui.theme.HomePromptStyle
import com.dua.ai.ui.theme.HomeTaglineStyle
import com.dua.ai.ui.theme.HomeWordmarkStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/**
 * Empty Home, laid out like the "Dua AI Dabba" reference (ChatGPT layout): centred animated DUA logo,
 * the DUA wordmark and tagline, then quick prompt rows above the composer.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    shimmerPreview: Float? = null,
    suggestions: List<PromptSuggestion> = emptyList(),
    onSuggestion: (String) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Dua AI"; heading() }) {
            Box(Modifier.size(Sizes.homeLogo).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                AnimatedVoiceCube(listening = false)
            }
            Spacer(Modifier.height(Spacing.md))
            DuaWordmark()
            Spacer(Modifier.height(Spacing.sm))
            Text("Dua always with you ✨", style = HomeTaglineStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.weight(1f))
        if (suggestions.isNotEmpty()) Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            suggestions.take(4).forEach { s ->
                PromptRow(iconFor(s.category), s.title) { onSuggestion(s.prompt.ifBlank { s.title }) }
            }
        }
    }
}

@Composable
private fun DuaWordmark() {
    val ink = MaterialTheme.colorScheme.onSurface
    val rule = MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.clearAndSetSemantics {}) {
        Box(Modifier.size(Sizes.wordmarkSquare).background(ink, RoundedCornerShape(Spacing.xxs)))
        Box(Modifier.width(Sizes.wordmarkRule).height(Sizes.wordmarkRuleThickness).background(rule))
        Text("DUA", style = HomeWordmarkStyle, color = ink)
        Box(Modifier.width(Sizes.wordmarkRule).height(Sizes.wordmarkRuleThickness).background(rule))
        Box(Modifier.size(Sizes.wordmarkSquare).background(ink, RoundedCornerShape(Spacing.xxs)))
    }
}

@Composable
private fun PromptRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clip(DuaThemeShapeRow)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
        Text(title, style = HomePromptStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val DuaThemeShapeRow = RoundedCornerShape(Spacing.md)

private fun iconFor(category: String) = when (category.lowercase()) {
    "coding", "code" -> Icons.Outlined.Code
    "idea", "ideas", "brainstorm" -> Icons.Outlined.Lightbulb
    "image", "images" -> Icons.Outlined.Image
    "writing", "business" -> Icons.Outlined.Edit
    else -> Icons.Outlined.ChatBubbleOutline
}
