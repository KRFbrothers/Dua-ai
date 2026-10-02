package com.dua.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSession
import com.dua.ai.model.MessageReaction
import com.dua.ai.model.PromptSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private var replyJob: Job? = null
    private fun cancelReply() {
        replyJob?.cancel()
        replyJob = null
        _isTyping.value = false
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _selectedModel = MutableStateFlow("Dua")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(
        listOf(
            ChatSession("1", "Building Compose UI for Android", "Coding", "Today"),
            ChatSession("2", "5 High-Growth Startup Strategies", "Strategy", "Today"),
            ChatSession("3", "Quantum Computing in 5 Minutes", "Science", "Yesterday"),
            ChatSession("4", "Kotlin Coroutines vs Flow", "Coding", "Previous 7 Days")
        )
    )
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    val promptSuggestions = listOf(
        PromptSuggestion(
            title = "Write code snippet",
            description = "Clean Jetpack Compose UI component",
            prompt = "How do I build an elegant, modern Card component in Jetpack Compose?",
            category = "Coding"
        ),
        PromptSuggestion(
            title = "Brainstorm concepts",
            description = "Innovative product ideas for developers",
            prompt = "Give me 4 unique mobile app ideas leveraging modern AI and edge computing.",
            category = "Idea"
        ),
        PromptSuggestion(
            title = "Explain simply",
            description = "Deep concepts made straightforward",
            prompt = "Explain how transformer neural networks generate text in simple terms.",
            category = "Learning"
        ),
        PromptSuggestion(
            title = "Draft a plan",
            description = "Structured step-by-step roadmap",
            prompt = "Draft a 4-week learning roadmap to master Kotlin and Jetpack Compose.",
            category = "Productivity"
        )
    )

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun setModel(model: String) {
        _selectedModel.value = model
    }

    fun startNewChat() {
        cancelReply()
        _messages.value = emptyList()
    }

    fun selectSession(session: ChatSession) {
        cancelReply()
        _messages.value = listOf(
            ChatMessage(
                text = "Show me details about: ${session.title}",
                isUser = true,
                model = _selectedModel.value
            ),
            ChatMessage(
                text = "Here is the summary of your previous session on **${session.title}**.\n\nYou explored best architectural practices, modular structures, and performant state hoisting.",
                isUser = false,
                model = _selectedModel.value
            )
        )
    }

    fun deleteSession(sessionId: String) {
        _sessions.value = _sessions.value.filter { it.id != sessionId }
    }

    fun toggleReaction(messageId: String, isLike: Boolean) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) {
                val newReaction = when {
                    isLike && msg.reaction == MessageReaction.LIKED -> MessageReaction.NONE
                    isLike -> MessageReaction.LIKED
                    !isLike && msg.reaction == MessageReaction.DISLIKED -> MessageReaction.NONE
                    else -> MessageReaction.DISLIKED
                }
                msg.copy(reaction = newReaction)
            } else {
                msg
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isTyping.value) return
        val responseModel = _selectedModel.value

        val userMessage = ChatMessage(
            text = userText.trim(),
            isUser = true,
            model = _selectedModel.value
        )

        _messages.value = _messages.value + userMessage
        _isTyping.value = true

        replyJob = viewModelScope.launch {
            delay(850) // Realistic low-latency response simulation

            val (replyText, codeSnippet, codeLang) = generateResponse(userText, responseModel)

            val botMessage = ChatMessage(
                text = replyText,
                isUser = false,
                model = responseModel,
                hasCode = codeSnippet != null,
                codeSnippet = codeSnippet,
                codeLanguage = codeLang
            )

            _messages.value = _messages.value + botMessage
            _isTyping.value = false
        }
    }

    private fun generateResponse(
        prompt: String,
        model: String
    ): Triple<String, String?, String> {
        val lower = prompt.lowercase()

        return when {
            lower.contains("compose") || lower.contains("card") || lower.contains("code") -> {
                Triple(
                    "Here is a clean, modern Jetpack Compose Card component following Material 3 guidelines and smooth elevation aesthetics:",
                    """
                    @Composable
                    fun ModernDuaCard(
                        title: String,
                        subtitle: String,
                        onClick: () -> Unit
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    """.trimIndent(),
                    "Kotlin"
                )
            }
            lower.contains("roadmap") || lower.contains("plan") || lower.contains("week") -> {
                Triple(
                    """
                    Here is a structured 4-Week Roadmap for mastering **Modern Android & Compose**:

                    ### Week 1: Kotlin & Coroutines Foundation
                    - Kotlin syntax, extension functions, sealed interfaces
                    - Coroutines, `CoroutineScope`, `Dispatchers.IO` & Flow primitives

                    ### Week 2: Jetpack Compose Fundamentals
                    - State hoisting, `remember`, `mutableStateOf`, `derivedStateOf`
                    - Layouts (`Column`, `Row`, `LazyColumn`, `Box`, `Scaffold`)
                    - Modifiers and Material 3 theming

                    ### Week 3: Architecture & Navigation
                    - Clean Architecture with MVVM + UI Layer (`StateFlow`)
                    - Jetpack Navigation Compose with type-safe arguments

                    ### Week 4: Production Polish
                    - Animations (`AnimatedVisibility`, `animateColorAsState`)
                    - Performance profiling and edge-to-edge support
                    """.trimIndent(),
                    null,
                    ""
                )
            }
            lower.contains("transformer") || lower.contains("neural") || lower.contains("ai") || lower.contains("explain") -> {
                Triple(
                    """
                    **Transformer neural networks** process language through three core mechanisms:

                    1. **Tokens & Embeddings**: Words and subwords are converted into high-dimensional numerical vectors representing semantic meaning.
                    2. **Self-Attention Mechanism**: Unlike older sequential models (RNNs), transformers look at every word in context simultaneously. This allows the model to understand what *"it"* refers to in a complex sentence.
                    3. **Next-Token Prediction**: The model calculates probability distributions over the entire vocabulary to predict the most coherent next token, one by one.
                    """.trimIndent(),
                    null,
                    ""
                )
            }
            lower.contains("idea") || lower.contains("startup") -> {
                Triple(
                    """
                    Here are 3 high-impact AI product ideas:

                    1. **Contextual Code Reviewer**: An IDE companion that watches git commits and drafts bullet-proof unit tests before you create a PR.
                    2. **Personal Audio Digest**: An on-device assistant that aggregates your newsletters, GitHub alerts, and Slack summaries into a 3-minute morning audio brief.
                    3. **Instant API Mock Server**: Paste any OpenAPI schema or JSON payload, and it spins up a live mock REST/GraphQL endpoint with intelligent synthetic data.
                    """.trimIndent(),
                    null,
                    ""
                )
            }
            else -> {
                Triple(
                    """
                    Regarding **"$prompt"**:

                    Here are key points to consider:

                    - **Efficiency**: Prioritize lightweight solutions with minimal overhead.
                    - **Modularity**: Break requirements down into reusable, isolated components.
                    - **Scalability**: Ensure your architecture supports smooth future extensions.

                    Would you like me to elaborate on any specific aspect or generate code examples?
                    """.trimIndent(),
                    null,
                    ""
                )
            }
        }
    }
}
