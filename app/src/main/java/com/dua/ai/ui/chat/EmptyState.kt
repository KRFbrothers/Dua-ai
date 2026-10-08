package com.dua.ai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dua.ai.R
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** New-chat screen: logo, greeting and suggestion chips that prefill the composer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(greeting: String, suggestions: List<PromptSuggestion>, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomy = maxHeight > 420.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (roomy) {
                Image(painterResource(R.drawable.dua_logo), contentDescription = "Dua logo", modifier = Modifier.height(120.dp))
                Spacer(Modifier.height(Spacing.xl))
            }
            if (greeting.isNotBlank()) Text(greeting, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text("What can I help with?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.xs).semantics { heading() })
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xxl))
                FlowRow(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    suggestions.take(4).forEach { suggestion -> SuggestionChip(suggestion) { onSuggestion(suggestion.prompt) } }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(suggestion: PromptSuggestion, onClick: () -> Unit) {
    val icon = when (suggestion.category) {
        "Writing" -> Icons.Outlined.EditNote
        "Learning" -> Icons.Outlined.School
        "Coding" -> Icons.Outlined.Code
        "Planning", "Productivity" -> Icons.Outlined.TaskAlt
        else -> Icons.Outlined.Lightbulb
    }
    Surface(onClick = onClick, shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny + 2.dp))
            Text(suggestion.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
