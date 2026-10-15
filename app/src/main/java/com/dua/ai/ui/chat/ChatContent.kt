package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomePromptStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

internal val HomeTaskPrompts = listOf("Nudge me to backup my photos", "Learn React by building an app", "Compare views on a topic I follow")

@Composable
internal fun ChatContent(task: Boolean, onFocusComposer: () -> Unit, onTask: (String) -> Unit) {
    val c = HomeTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        val contentHeight = maxHeight
        val promptSpace = (Sizes.touchTarget + HomeSizes.promptSpacing) * HomeTaskPrompts.size + Spacing.sm
        val heroHeight = if (task) (contentHeight - promptSpace).coerceAtLeast(0.dp) else contentHeight
        // One scroll region lets the full hero and wrapped prompts flow naturally at large font sizes.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = contentHeight),
            verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth().heightIn(min = heroHeight), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    DuaCalligraphyLogo(modifier = Modifier.width(156.dp))
                    Text(if (task) "I Know Everything" else "Dua always with you",
                        style = MaterialTheme.typography.headlineMedium, color = ChatTheme.colors.text, textAlign = TextAlign.Center)
                    if (!task) Surface(onClick = onFocusComposer, color = c.bar, contentColor = c.promptText,
                        shape = HomeShapes.segmentTrack, border = BorderStroke(HomeSizes.barBorder, c.barBorder),
                        modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Box(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm), contentAlignment = Alignment.Center) {
                            Text("Let's go", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (task) Column(Modifier.fillMaxWidth()
                .padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(HomeSizes.promptSpacing)) {
                val icons = listOf(Icons.Outlined.AttachFile, Icons.Outlined.Code, Icons.Outlined.Public)
                HomeTaskPrompts.forEachIndexed { i, prompt ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(role = Role.Button) { onTask(prompt) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap)) {
                        Icon(icons[i], contentDescription = null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
                        Text(prompt, style = HomePromptStyle, color = c.promptText, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
