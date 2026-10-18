package com.dua.ai.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
internal fun ChatContent(
    task: Boolean,
    profileName: String = "",
    onTask: (String) -> Unit,
) {
    val c = HomeTheme.colors
    val isDark = DuaTheme.colors.isDark
    val haptic = LocalChatHaptic.current
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val firstName = remember(profileName) {
        profileName.trim().split(Regex("\\s+")).firstOrNull()?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() }
    }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello there"
        }
    }

    // Dynamic breathing ambient aura animation
    val infiniteTransition = rememberInfiniteTransition(label = "AuraBreathing")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = if (isDark) 0.16f else 0.10f,
        targetValue = if (isDark) 0.28f else 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "AuraAlpha",
    )
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "AuraScale",
    )

    // Smooth entrance scale
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val heroScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "HeroScale",
    )

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        val contentHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = contentHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Hero section: Living Ambient Aura + Dua Logo + Headlines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg, bottom = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!task) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = heroScale
                            scaleY = heroScale
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Ambient radial glow behind the emblem with breathing pulse
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .graphicsLayer {
                                    scaleX = auraScale
                                    scaleY = auraScale
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (isDark) listOf(
                                            Color(0xFF8B5CF6).copy(alpha = auraAlpha),
                                            Color(0xFF3B82F6).copy(alpha = auraAlpha * 0.45f),
                                            Color.Transparent,
                                        ) else listOf(
                                            Color(0xFF8B5CF6).copy(alpha = auraAlpha),
                                            Color(0xFF60A5FA).copy(alpha = auraAlpha * 0.45f),
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
                        val greetingPrefix = if (firstName != null) "Hello, $firstName" else greeting
                        Text(
                            text = "$greetingPrefix • How can I help you today?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChatTheme.colors.mutedText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Interactive Bottom Prompts
            if (task) {
                // Task Mode prompts with haptic feedback
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
                                .clickable(role = Role.Button) {
                                    haptic(ChatHaptic.Tap)
                                    onTask(prompt)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HomeSizes.promptGap),
                        ) {
                            Icon(icons[i], contentDescription = null, tint = c.promptIcon, modifier = Modifier.size(HomeSizes.promptIcon))
                            Text(prompt, style = HomePromptStyle, color = c.promptText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Gemini Android Chat Mode: Spacious clean canvas + sleek horizontal starter pills
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // Starter Chips Carousel with Haptic Feedback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("starter-chips"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        StarterChips.forEach { (icon, label, fill) ->
                            StarterChip(icon, label) {
                                haptic(ChatHaptic.Tap)
                                onTask(fill)
                            }
                        }
                    }
                }
            }
        }
    }
}
