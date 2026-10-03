package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dua.ai.history.SavedChat
import com.dua.ai.history.ChatHistoryCodec
import java.util.UUID
import androidx.lifecycle.viewModelScope
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSession
import com.dua.ai.model.MessageReaction
import com.dua.ai.model.PromptSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.DuaApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val api = DuaApiClient()
    private var generation = 0
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _failedPrompt = MutableStateFlow<String?>(null)
    val failedPrompt: StateFlow<String?> = _failedPrompt.asStateFlow()
    fun consumeFailedPrompt() { _failedPrompt.value = null }
    private var replyJob: Job? = null
    private fun cancelReply() {
        generation++
        _error.value = null
        _failedPrompt.value = null
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

    private val preferences = application.getSharedPreferences("dua_chat_history", 0)
    private var activeId = UUID.randomUUID().toString()
    private val _savedChats = MutableStateFlow<List<SavedChat>>(emptyList())
    val savedChats: StateFlow<List<SavedChat>> = _savedChats.asStateFlow()
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    init {
        preferences.getString("history", null)?.let { raw ->
            try {
                val (id, records) = ChatHistoryCodec.decode(raw)
                activeId = id
                _savedChats.value = records
                records.find { it.id == id }?.let {
                    _messages.value = it.messages
                    _selectedModel.value = it.model
                    _draft.value = it.draft
                }
            } catch (_: Exception) {
                preferences.edit().putString("unreadable_history_backup", raw).apply()
                _error.value = "Saved chats could not be loaded. Stored data has not been deleted."
            }
        }
    }

    private fun saveHistory() {
        val others = _savedChats.value.filterNot { it.id == activeId }
        _savedChats.value = if (_messages.value.isEmpty() && _draft.value.isBlank()) others else
            listOf(SavedChat(activeId, _selectedModel.value, _draft.value, _messages.value, System.currentTimeMillis())) + others
        preferences.edit().putString("history", ChatHistoryCodec.encode(_savedChats.value, activeId)).apply()
    }

    fun updateDraft(value: String) { _draft.value = value; saveHistory() }

    fun openChat(id: String) {
        val chat = _savedChats.value.find { it.id == id } ?: return
        cancelReply()
        activeId = chat.id
        _messages.value = chat.messages
        _selectedModel.value = chat.model
        _draft.value = chat.draft
        saveHistory()
    }

    fun deleteChat(id: String) {
        if (id == activeId) {
            cancelReply()
            activeId = UUID.randomUUID().toString()
            _messages.value = emptyList()
            _draft.value = ""
        }
        _savedChats.value = _savedChats.value.filterNot { it.id == id }
        saveHistory()
    }

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
        if (model !in listOf("Dua", "Gemini")) return
        _selectedModel.value = model
        saveHistory()
    }

    /** Cancels the current request without removing the user's sent message. */
    fun stopReply() {
        if (!_isTyping.value) return
        cancelReply()
        _error.value = "Response stopped."
        saveHistory()
    }

    fun startNewChat() {
        cancelReply()
        activeId = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _draft.value = ""
        saveHistory()
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
        _error.value = null
        if (userText.length > 8000) {
            _error.value = "Message 8000 characters se chhota rakhein."
            return
        }
        val responseModel = _selectedModel.value
        if (responseModel !in listOf("Dua", "Gemini")) {
            _error.value = "Dua ya Gemini select karein."
            return
        }
        val userMessage = ChatMessage(text = userText.trim(), isUser = true, model = responseModel)
        val requestHistory = _messages.value.takeLast(38) + userMessage
        val currentGeneration = generation
        _messages.value = _messages.value + userMessage
        _draft.value = ""
        saveHistory()
        _isTyping.value = true
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            try {
                val reply = api.reply(responseModel, requestHistory)
                if (generation == currentGeneration) {
                    _messages.value = _messages.value + ChatMessage(
                        text = reply, isUser = false, model = responseModel
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == currentGeneration) {
                    _messages.value = _messages.value.filterNot { it.id == userMessage.id }
                    _draft.value = userText
                    _failedPrompt.value = userText
                    _error.value = when (failure) {
                        is DuaApiException, is IllegalArgumentException -> failure.message
                        is java.net.SocketTimeoutException -> "Dua response timeout hua. Dobara try karein."
                        else -> "Connection fail hua. Dobara try karein."
                    }
                }
            } finally {
                if (generation == currentGeneration) { _isTyping.value = false; saveHistory() }
            }
        }
    }
}

