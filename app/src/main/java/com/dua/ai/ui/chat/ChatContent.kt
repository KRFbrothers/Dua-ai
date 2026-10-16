package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.HomePromptStyle
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

internal val HomeTaskPrompts = listOf("Nudge me to backup my photos", "Learn React by building an app", "Compare views on a topic I follow")

private data class HomeActionCard(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector,
    val gradient: List<Color>,
)

private val ChatActionCards = listOf(
    HomeActionCard(
        title = "Deep Research",
        subtitle = "Search web & sources",
        prompt = "Deep research with live web sources: ",
        icon = Icons.Outlined.TravelExplore,
        gradient = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
    ),
    HomeActionCard(
        title = "Code & Build",
        subtitle = "Write, debug & build",
        prompt = "Code likho: ",
        icon = Icons.Outlined.Code,
        gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    ),
    HomeActionCard(
        title = "Image Studio",
        subtitle = "Create visuals & art",
        prompt = "Create an image of ",
        icon = Icons.Outlined.Image,
        gradient = listOf(Color(0xFFF59E0B), Color(0xFFEA580C)),
    ),
    HomeActionCard(
        title = "Brainstorm",
        subtitle = "Ideas & strategies",
        prompt = "Mujhe ideas do for ",
        icon = Icons.Outlined.Lightbulb,
        gradient = listOf(Color(0xFF10B981), Color(0xFF047857)),
    ),
)

@Composable
internal fun ChatContent(task: Boolean, onTask: (String) -> Unit) {
    val c = HomeTheme.colors
    val isDark = DuaTheme.colors.isDark
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello there"
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        val contentHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = contentHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Hero section: Ambient Radial Glow + Dua Logo + Headlines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg, bottom = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!task) {
                    Box(contentAlignment = Alignment.Center) {
                        // Ambient radial glow behind the emblem
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (isDark) listOf(
                                            Color(0xFF8B5CF6).copy(alpha = 0.22f),
                                            Color(0xFF3B82F6).copy(alpha = 0.08f),
                                            Color.Transparent,
                                        ) else listOf(
                                            Color(0xFF8B5CF6).copy(alpha = 0.14f),
                                            Color(0xFF60A5FA).copy(alpha = 0.06f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                        DuaCalligraphyLogo(modifier = Modifier.width(156.dp))
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = if (task) "What can I take off\nyour plate?" else "Dua always with you",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChatTheme.colors.text,
                        textAlign = TextAlign.Center,
                    )
                    if (!task) {
                        Text(
                            text = "$greeting • What should we explore?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChatTheme.colors.mutedText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Interactive Bottom Prompts
            if (task) {
                // Task Mode prompts
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HomeSizes.promptSpacing),
                ) {
                    val icons = listOf(Icons.Outlined.AttachFile, Icons.Outlined.Code, Icons.Outlined.Public)
                    HomeTaskPrompts.forEachIndexed { i, prompt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touchTarget)
                                .clickable(role = Role.Button) { onTask(prompt) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap),
                        ) {
                            Icon(icons[i], contentDescription = null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
                            Text(prompt, style = HomePromptStyle, color = c.promptText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Chat Mode: 2x2 Glassmorphic Action Cards + Quick Starter Chips
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // 2x2 Grid of Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        HomeActionCardView(ChatActionCards[0], Modifier.weight(1f)) { onTask(ChatActionCards[0].prompt) }
                        HomeActionCardView(ChatActionCards[1], Modifier.weight(1f)) { onTask(ChatActionCards[1].prompt) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        HomeActionCardView(ChatActionCards[2], Modifier.weight(1f)) { onTask(ChatActionCards[2].prompt) }
                        HomeActionCardView(ChatActionCards[3], Modifier.weight(1f)) { onTask(ChatActionCards[3].prompt) }
                    }

                    Spacer(Modifier.height(2.dp))

                    // Starter Chips Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("starter-chips"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        StarterChips.forEach { (icon, label, fill) ->
                            StarterChip(icon, label) { onTask(fill) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCardView(
    card: HomeActionCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isDark = DuaTheme.colors.isDark
    val cardBg = if (isDark) Color(0xFF141822) else Color(0xFFF9FAFB)
    val cardBorder = if (isDark) Color(0xFF263042).copy(alpha = 0.7f) else Color(0xFFE5E7EB)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .semantics {
                contentDescription = "${card.title}: ${card.subtitle}"
                role = Role.Button
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(card.gradient),
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    card.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = ChatTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = ChatTheme.colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
