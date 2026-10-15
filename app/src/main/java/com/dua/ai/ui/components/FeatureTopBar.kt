package com.dua.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.ui.theme.*

/** A 44dp visual inside each 48dp accessible target; no Home tabs, model or search controls. */
@Composable
fun FeatureTopBar(title: String, onBack: () -> Unit, onNewSession: () -> Unit,
    newSessionEnabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = PageTheme.colors
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AgentSizes.padding, vertical = Spacing.sm)
        .testTag("feature-top-bar")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FeatureCircleButton("Back", onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(AgentSizes.icon), tint = c.text) }
            FeatureCircleButton("New $title session", onNewSession, newSessionEnabled) {
                Icon(Icons.Default.Add, null, Modifier.size(AgentSizes.icon), tint = c.text)
            }
        }
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = Sizes.touchTarget).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.merge(AgentTitleStyle), color = c.text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeatureCircleButton(label: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val c = PageTheme.colors
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget).semantics {
        contentDescription = label
        if (!enabled) stateDescription = "Unavailable"
    }) {
        Surface(Modifier.size(AgentSizes.circle), shape = AgentShapes.circle, color = c.card,
            border = BorderStroke(AgentSizes.border, c.cardBorder)) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}
