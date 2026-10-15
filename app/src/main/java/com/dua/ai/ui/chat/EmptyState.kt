package com.dua.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.theme.HomePromptStyle
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** The reference shell's two quick prompts: label shown, text put in the composer. */
val ReferencePrompts = listOf(
    Triple(Icons.Outlined.Image, "Create an image or sticker", "Create an image or sticker of "),
    Triple(Icons.Outlined.Edit, "Write or edit", "Write or edit a "),
)

/** ChatGPT-style quick starters under the prompt rows: label shown, text put in the composer. */
val StarterChips = listOf(
    Triple(Icons.Outlined.Newspaper, "AI news", "What's new in AI this week? Short summary with sources."),
    Triple(Icons.Outlined.Code, "Code likho", "Code likho: "),
    Triple(Icons.Outlined.Lightbulb, "Idea do", "Mujhe ideas do for "),
    Triple(Icons.Outlined.Summarize, "Summarize", "Summarize this: "),
)

/**
 * Empty Home, as the reference shell's code draws it: blank space, then the quick prompt rows just above the
 * "Ask Dua AI" bar. Server suggestions (if any) follow the two reference prompts.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    suggestions: List<PromptSuggestion> = emptyList(),
    showPrompts: Boolean = true,
    onSuggestion: (String) -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        Spacer(Modifier.weight(1f))
        if (showPrompts) Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(HomeSizes.promptSpacing),
        ) {
            ReferencePrompts.forEach { (icon, label, fill) -> PromptRow(icon, label) { onSuggestion(fill) } }
            suggestions.take(2).forEach { s -> PromptRow(iconFor(s.category), s.title) { onSuggestion(s.prompt.ifBlank { s.title }) } }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("starter-chips"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StarterChips.forEach { (icon, label, fill) -> StarterChip(icon, label) { onSuggestion(fill) } }
            }
        }
    }
}

@Composable
private fun StarterChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = ChatTheme.colors
    Surface(onClick = onClick, shape = DuaShape.chip, color = c.chipFill, border = BorderStroke(ChatSizes.border, c.chipBorder),
        modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = "Start with: $label"; role = Role.Button }) {
        Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.chipIcon, modifier = Modifier.size(Sizes.iconSmall))
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.chipText, maxLines = 1, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun PromptRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val c = HomeTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap),
    ) {
        Icon(icon, contentDescription = null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
        Text(title, style = HomePromptStyle, color = c.promptText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun iconFor(category: String) = when (category.lowercase()) {
    "coding", "code" -> Icons.Outlined.Code
    "idea", "ideas", "brainstorm" -> Icons.Outlined.Lightbulb
    "image", "images" -> Icons.Outlined.Image
    "writing", "business" -> Icons.Outlined.Edit
    else -> Icons.Outlined.ChatBubbleOutline
}
