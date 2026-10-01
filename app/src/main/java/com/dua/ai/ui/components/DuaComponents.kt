package com.dua.ai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.R
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.MessageReaction
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.theme.*

// ==========================================
// Top Bar: Clean Minimalist Gemini Style
// ==========================================
@Composable
fun ModernTopBar(
    selectedModel: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open History",
                    tint = extendedColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Image(
                painter = painterResource(id = R.drawable.dua_logo),
                contentDescription = "Dua AI Logo",
                modifier = Modifier
                    .height(28.dp)
                    .padding(end = 6.dp)
            )

            // Model Selector Pill (ChatGPT / Gemini style)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onModelClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedModel,
                        style = MaterialTheme.typography.labelLarge,
                        color = extendedColors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Model",
                        tint = extendedColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Theme Mode Toggle (Sun / Moon)
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = extendedColors.textSecondary
                )
            }

            // New Chat Button
            IconButton(onClick = onNewChatClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "New Chat",
                    tint = extendedColors.textPrimary
                )
            }
        }
    }
}

// ==========================================
// Empty State / Welcome Screen: Gemini Style
// ==========================================
@Composable
fun GeminiWelcomeSection(
    suggestions: List<PromptSuggestion>,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Dua Hero Metallic Logo
        Image(
            painter = painterResource(id = R.drawable.dua_logo),
            contentDescription = "Dua AI Logo",
            modifier = Modifier
                .height(115.dp)
                .padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Greeting Header
        Text(
            text = "Hello, there",
            style = MaterialTheme.typography.displayLarge.copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GeminiBlueDark,
                        GeminiPurple,
                        if (extendedColors.isDark) Color(0xFFFF80BF) else GeminiBlue
                    )
                )
            )
        )

        Text(
            text = "How can I help you today?",
            style = MaterialTheme.typography.headlineMedium,
            color = extendedColors.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )

        // Suggestion Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestions.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectSuggestion(item.prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (item.category) {
                                "Coding" -> Icons.Outlined.Code
                                "Idea" -> Icons.Outlined.Lightbulb
                                "Learning" -> Icons.Outlined.School
                                else -> Icons.Outlined.Explore
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = extendedColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = extendedColors.textSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = extendedColors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Chat Message Stream Item
// ==========================================
@Composable
fun ChatMessageCard(
    message: ChatMessage,
    onReaction: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isUser) {
            // User Message Pill
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = extendedColors.userBubbleBg,
                border = BorderStroke(1.dp, extendedColors.userBubbleBorder)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = extendedColors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            // Bot Response (Clean Minimalist ChatGPT / Gemini Style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Header with Dua Logo & Model Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dua_logo),
                        contentDescription = "Dua AI",
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Dua AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = extendedColors.textPrimary
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "• ${message.model}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extendedColors.textTertiary
                    )
                }

                // Bot Response Text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = extendedColors.textPrimary,
                    lineHeight = 24.sp
                )

                // Optional Code Block
                if (message.hasCode && message.codeSnippet != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CodeBlockCard(
                        code = message.codeSnippet,
                        language = message.codeLanguage
                    )
                }

                // Action Bar (Copy, Like, Dislike, Regenerate)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Dua AI Response", message.text))
                            isCopied = true
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (isCopied) SuccessGreen else extendedColors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Like Reaction
                    IconButton(
                        onClick = { onReaction(true) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (message.reaction == MessageReaction.LIKED) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (message.reaction == MessageReaction.LIKED) MaterialTheme.colorScheme.primary else extendedColors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Dislike Reaction
                    IconButton(
                        onClick = { onReaction(false) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (message.reaction == MessageReaction.DISLIKED) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (message.reaction == MessageReaction.DISLIKED) ErrorRed else extendedColors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Retry / Regenerate
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Regenerate",
                            tint = extendedColors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Code Block Component with Copy Action
// ==========================================
@Composable
fun CodeBlockCard(
    code: String,
    language: String
) {
    val extendedColors = LocalExtendedColors.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = extendedColors.codeBlockBg,
        border = BorderStroke(1.dp, extendedColors.codeBlockBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Code Block Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2024))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFADB5BD)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            isCopied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = if (isCopied) SuccessGreen else Color(0xFFADB5BD),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy code",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCopied) SuccessGreen else Color(0xFFADB5BD)
                    )
                }
            }

            // Code Content
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = Color(0xFFE2E8F0),
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

// ==========================================
// Thinking / Typing Pulse Animation
// ==========================================
@Composable
fun ThinkingIndicator() {
    val extendedColors = LocalExtendedColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(GeminiBlueDark, GeminiPurple))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size((6 * dot1Scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .size((6 * dot2Scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .size((6 * dot3Scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Dua AI is thinking...",
            style = MaterialTheme.typography.bodyMedium,
            color = extendedColors.textSecondary
        )
    }
}

// ==========================================
// Modern Floating Chat Input Bar (Capsule)
// ==========================================
@Composable
fun ModernChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val hasText = inputText.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach Action
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = extendedColors.textSecondary
                    )
                }

                // Text Input
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Ask Dua anything...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = extendedColors.textTertiary
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = extendedColors.textPrimary,
                        unfocusedTextColor = extendedColors.textPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )

                // Voice Mic
                if (!hasText) {
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = extendedColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Circular Send Button
                IconButton(
                    onClick = onSend,
                    enabled = hasText,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (hasText) Color.White else extendedColors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Disclaimer Notice (ChatGPT/Gemini Style)
        Text(
            text = "Dua can make mistakes. Verify important info.",
            style = MaterialTheme.typography.labelMedium,
            color = extendedColors.textTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 4.dp)
        )
    }
}

// ==========================================
// Model Selection Dialog (Gemini/ChatGPT Style)
// ==========================================
@Composable
fun ModelSelectorDialog(
    selectedModel: String,
    onModelSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current

    val models = listOf(
        Triple("Dua 2.0 Flash", "Fast, agile for everyday conversation and answers", Icons.Default.Bolt),
        Triple("Dua 1.5 Pro", "Advanced reasoning, logic, coding and deep analysis", Icons.Default.Psychology),
        Triple("Dua DeepThink", "Multi-step reasoning and mathematical synthesis", Icons.Default.AutoAwesome),
        Triple("Dua Code Expert", "Specialized in Android Jetpack Compose and modern Kotlin", Icons.Default.Code)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Intelligence Model",
                style = MaterialTheme.typography.titleLarge,
                color = extendedColors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { (name, desc, icon) ->
                    val isSelected = selectedModel == name
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModelSelect(name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else extendedColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else extendedColors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = extendedColors.textSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
