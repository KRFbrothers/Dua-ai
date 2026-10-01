package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.components.*
import com.dua.ai.ui.theme.DuaAiTheme
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    var inputQuery by remember { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Smooth scroll to bottom on new messages
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    DuaAiTheme(darkTheme = isDarkTheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    HistoryDrawerContent(
                        sessions = sessions,
                        onNewChatClick = { viewModel.startNewChat() },
                        onSelectChat = { session -> viewModel.selectSession(session) },
                        onDeleteChat = { id -> viewModel.deleteSession(id) },
                        onCloseDrawer = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    ModernTopBar(
                        selectedModel = selectedModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNewChatClick = { viewModel.startNewChat() },
                        onModelClick = { showModelDialog = true }
                    )
                },
                bottomBar = {
                    ModernChatInputBar(
                        inputText = inputQuery,
                        onInputChange = { inputQuery = it },
                        onSend = {
                            val prompt = inputQuery
                            inputQuery = ""
                            viewModel.sendMessage(prompt)
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (messages.isEmpty()) {
                        GeminiWelcomeSection(
                            suggestions = viewModel.promptSuggestions,
                            onSelectSuggestion = { suggestion ->
                                viewModel.sendMessage(suggestion)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatMessageCard(
                                    message = msg,
                                    onReaction = { isLike ->
                                        viewModel.toggleReaction(msg.id, isLike)
                                    },
                                    onRetry = {
                                        // Resend user prompt
                                        val lastUserMsg = messages.findLast { it.isUser }
                                        if (lastUserMsg != null) {
                                            viewModel.sendMessage(lastUserMsg.text)
                                        }
                                    }
                                )
                            }

                            if (isTyping) {
                                item {
                                    ThinkingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Model Selector Dialog
        if (showModelDialog) {
            ModelSelectorDialog(
                selectedModel = selectedModel,
                onModelSelect = { model ->
                    viewModel.setModel(model)
                    showModelDialog = false
                },
                onDismiss = { showModelDialog = false }
            )
        }
    }
}
