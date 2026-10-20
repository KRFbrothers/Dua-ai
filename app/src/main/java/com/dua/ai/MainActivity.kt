package com.dua.ai // <-- Apna package name yahan rakhein

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// 1. Data Model for Chat Messages
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GeminiChatScreen()
            }
        }
    }
}

// 2. Main Complete Chat Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatScreen() {
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Initial Welcome Message
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hello Fareed! Main Dua AI hoon. Aaj hum kis project par kaam kar rahe hain?",
                isFromUser = false,
                isStreaming = false
            )
        )
    }

    // Jab bhi naya message aaye, smoothly niche scroll karo
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF131314), // Gemini Main Dark Background
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Dua AI ✨",
                        color = Color(0xFFE3E3E3),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF131314)
                )
            )
        },
        bottomBar = {
            GeminiBottomBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSendClick = {
                    if (inputText.isNotBlank()) {
                        val userText = inputText
                        inputText = ""

                        // Step 1: User ka message list mein daalo
                        messages.add(
                            ChatMessage(
                                text = userText,
                                isFromUser = true
                            )
                        )

                        // Step 2: AI ka Thinking (Shimmer) placeholder daalo
                        val aiMessageId = UUID.randomUUID().toString()
                        messages.add(
                            ChatMessage(
                                id = aiMessageId,
                                text = "",
                                isFromUser = false,
                                isLoading = true
                            )
                        )

                        // Step 3: 2 Second baad Shimmer hata kar Streaming Response dikhao (Fake API Call)
                        coroutineScope.launch {
                            delay(2000) // 2 sec thinking time
                            val index = messages.indexOfFirst { it.id == aiMessageId }
                            if (index != -1) {
                                messages[index] = ChatMessage(
                                    id = aiMessageId,
                                    text = "Yeh dekho! Pehle 2 second tak Gemini style blue-purple shimmer chala, aur ab yeh text ek-ek word karke smoothly stream ho raha hai. Bilkul makkhan UI!",
                                    isFromUser = false,
                                    isLoading = false,
                                    isStreaming = true
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(
                items = messages,
                key = { message -> message.id } // Lag-free scrolling ke liye unique key
            ) { message ->
                ChatMessageItem(
                    message = message,
                    onStreamingFinished = {
                        // Ek baar stream ho gaya toh flag false kar do taaki scroll karne par dobara type na ho
                        val index = messages.indexOfFirst { it.id == message.id }
                        if (index != -1) {
                            messages[index] = messages[index].copy(isStreaming = false)
                        }
                    }
                )
            }
        }
    }
}

// 3. User Bubble & AI Response Item
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onStreamingFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
    ) {
        if (message.isFromUser) {
            // USER BUBBLE
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 4.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .background(Color(0xFF282A2C))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color(0xFFE3E3E3),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
        } else {
            // AI RESPONSE (Borderless)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "✨",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                )

                if (message.isLoading) {
                    GeminiLoadingShimmer()
                } else {
                    StreamingAiText(
                        fullText = message.text,
                        isStreaming = message.isStreaming,
                        onStreamingComplete = onStreamingFinished
                    )
                }
            }
        }
    }
}

// 4. Smooth Typewriter Streaming Component
@Composable
fun StreamingAiText(
    fullText: String,
    isStreaming: Boolean,
    onStreamingComplete: () -> Unit
) {
    var displayedText by remember(fullText) {
        mutableStateOf(if (isStreaming) "" else fullText)
    }

    LaunchedEffect(fullText, isStreaming) {
        if (isStreaming) {
            val words = fullText.split(" ")
            val builder = StringBuilder()
            for (word in words) {
                builder.append(word).append(" ")
                displayedText = builder.toString()
                delay(35) // Word speed (35ms is super smooth)
            }
            onStreamingComplete()
        } else {
            displayedText = fullText
        }
    }

    Text(
        text = displayedText,
        color = Color(0xFFE3E3E3),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    )
}

// 5. Gemini Blue-Purple-Pink Shimmer Loader
@Composable
fun GeminiLoadingShimmer() {
    val shimmerColors = listOf(
        Color(0xFF1E1F20),
        Color(0xFF4285F4).copy(alpha = 0.6f),
        Color(0xFF9B72CB).copy(alpha = 0.6f),
        Color(0xFFD96570).copy(alpha = 0.6f),
        Color(0xFF1E1F20)
    )

    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
    }
}

// 6. Expanding Pill-Shaped Bottom Bar
@Composable
fun GeminiBottomBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    val surfaceColor = Color(0xFF1E1F20)
    val textColor = Color(0xFFE3E3E3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding() // Keyboard aane par smoothly upar uthega
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceColor)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = { /* Attach action */ }) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = textColor
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Ask Dua AI...",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFFA8C7FA)),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedContent(
            targetState = text.isNotBlank(),
            transitionSpec = {
                (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
            },
            label = "MicToSendButton"
        ) { hasText ->
            if (hasText) {
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFA8C7FA))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color(0xFF062E6F)
                    )
                }
            } else {
                IconButton(onClick = { /* Voice action */ }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = textColor
                    )
                }
            }
        }
    }
}
