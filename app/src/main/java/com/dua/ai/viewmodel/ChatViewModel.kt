package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dua.ai.history.SavedChat
import com.dua.ai.history.ChatHistoryCodec
import java.util.UUID
import android.net.Uri
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.attachments.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.dua.ai.attachments.CameraCaptureStore
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
    private val _attachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val attachments: StateFlow<List<ChatAttachment>> = _attachments.asStateFlow()
    private val _attachmentError = MutableStateFlow("")
    val attachmentError: StateFlow<String> = _attachmentError.asStateFlow()
    private val _readingFiles = MutableStateFlow(false)
    val readingFiles: StateFlow<Boolean> = _readingFiles.asStateFlow()
    private var selectionVersion = 0

    fun clearAttachments() {
        selectionVersion++
        _attachments.value = emptyList()
        _attachmentError.value = ""
        _readingFiles.value = false
    }

    private fun loadFiles(cleanup: () -> Unit = {}, loader: () -> List<ChatAttachment>) {
        if (_isTyping.value) { cleanup(); _attachmentError.value = "Reply complete hone ke baad files select karein."; return }
        val version = ++selectionVersion
        _readingFiles.value = true
        _attachmentError.value = ""
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val result = loader()
                    require(result.size <= 3) { "Maximum 3 files select karein." }
                    require(result.sumOf { java.util.Base64.getDecoder().decode(it.data).size } <= AttachmentReader.MAX_BYTES) {
                        "Attachments total 2 MB se chhote rakhein."
                    }
                    result
                }
                if (version == selectionVersion) _attachments.value = files
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == selectionVersion) _attachmentError.value = error.message ?: "File read nahi hui. Dobara select karein."
            } finally { cleanup(); if (version == selectionVersion) _readingFiles.value = false }
        }
    }

    fun selectAttachments(uris: List<Uri>) = loadFiles {
        require(uris.size <= 3) { "Maximum 3 files select karein." }
        val resolver = getApplication<Application>().contentResolver
        uris.map { uri ->
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "Selected file"
            } ?: "Selected file"
            val type = resolver.getType(uri) ?: if (name.endsWith(".txt", true)) "text/plain" else ""
            val input = resolver.openInputStream(uri) ?: error("File read nahi hui.")
            input.use { AttachmentReader.read(name, type, it) }
        }
    }

    fun selectCameraFile(file: File) = loadFiles(cleanup = { file.delete() }) {
        listOf(CameraCaptureStore(getApplication<Application>().cacheDir).read(file))
    }

    private val _dataBusy = MutableStateFlow(false)
    val dataBusy: StateFlow<Boolean> = _dataBusy.asStateFlow()
    private val _dataStatus = MutableStateFlow("")
    val dataStatus: StateFlow<String> = _dataStatus.asStateFlow()
    fun dataControlNotice(text: String) { _dataStatus.value = text }

    private val _importPreview = MutableStateFlow<List<SavedChat>?>(null)
    val importPreview: StateFlow<List<SavedChat>?> = _importPreview.asStateFlow()
    fun cancelImport() { if (!_dataBusy.value) { _importPreview.value = null; _dataStatus.value = "Import cancelled. Your chats are unchanged." } }

    fun reviewImport(uri: Uri) {
        if (_dataBusy.value) return
        _importPreview.value = null
        _dataBusy.value = true
        _dataStatus.value = "Checking export…"
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Selected file cannot be read.")
                    input.use { ChatHistoryCodec.readImport(it) }
                }
                if (records.isEmpty()) _dataStatus.value = "This export has no chats. Nothing was changed."
                else { _importPreview.value = records; _dataStatus.value = "Export checked. Confirm to add chats." }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _dataStatus.value = error.message ?: "Export could not be read. No chats were changed." }
            finally { _dataBusy.value = false }
        }
    }

    fun confirmImport() {
        if (_dataBusy.value) return
        val imported = _importPreview.value ?: return
        _dataBusy.value = true
        _dataStatus.value = "Saving imported chats…"
        cancelReply()
        // Use the latest local records at confirmation, keeping the active draft/selection.
        val merged = ChatHistoryCodec.importCopies(_savedChats.value, imported)
        val encoded = ChatHistoryCodec.encode(merged, activeId)
        viewModelScope.launch {
            try {
                val committed = withContext(Dispatchers.IO) {
                    val previous = preferences.getString("history", null)
                    val success = preferences.edit().putString("history", encoded).commit()
                    if (!success) preferences.edit().putString("history", previous).apply()
                    success
                }
                if (committed) {
                    _savedChats.value = merged
                    _importPreview.value = null
                    _dataStatus.value = "${imported.size} chats imported. Existing chats and drafts were kept."
                } else _dataStatus.value = "Import could not be saved. No chats were changed. Try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _dataStatus.value = "Import could not be saved. No chats were changed. Try again." }
            finally { _dataBusy.value = false }
        }
    }

    fun exportChats(uri: Uri) {
        if (_dataBusy.value) return
        val records = _savedChats.value.toList()
        _dataBusy.value = true
        _dataStatus.value = "Creating export…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("Selected file cannot be written.")
                    output.use { it.write(ChatHistoryCodec.export(records).toByteArray(Charsets.UTF_8)) }
                }
                _dataStatus.value = "Export saved. Your chats are unchanged."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _dataStatus.value = "Export failed. The selected file may be incomplete; try again." }
            finally { _dataBusy.value = false }
        }
    }

    fun clearAllChats() {
        if (_dataBusy.value) return
        _dataBusy.value = true
        _dataStatus.value = "Clearing local history…"
        cancelReply()
        val nextId = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val committed = withContext(Dispatchers.IO) {
                    val previous = preferences.getString("history", null)
                    val backup = preferences.getString("unreadable_history_backup", null)
                    val success = preferences.edit().putString("history", ChatHistoryCodec.encode(emptyList(), nextId))
                        .remove("unreadable_history_backup").commit()
                    if (!success) preferences.edit().putString("history", previous)
                        .putString("unreadable_history_backup", backup).apply()
                    if (success) runCatching { File(getApplication<Application>().cacheDir, "camera-captures").listFiles()?.forEach { it.delete() } }
                    success
                }
                if (committed) {
                    activeId = nextId
                    _messages.value = emptyList()
                    _draft.value = ""
                    _savedChats.value = emptyList()
                    clearAttachments()
                    _dataStatus.value = "All local chats, drafts and pending files cleared."
                } else _dataStatus.value = "Delete could not be saved. Chats are kept in memory; try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _dataStatus.value = "Local history could not be cleared. Try again." }
            finally { _dataBusy.value = false }
        }
    }

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
        clearAttachments()
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
        clearAttachments()
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
        if ((userText.isBlank() && _attachments.value.isEmpty()) || _isTyping.value || _readingFiles.value) return
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
        val files = _attachments.value
        val prompt = userText.trim().ifBlank { "Describe these files." }
        val text = prompt + if (files.isEmpty()) "" else "\nFiles: " + files.joinToString(", ") { it.name }
        val userMessage = ChatMessage(text = text, isUser = true, model = responseModel)
        val requestHistory = _messages.value.takeLast(38) + userMessage
        val currentGeneration = generation
        _messages.value = _messages.value + userMessage
        _draft.value = ""
        saveHistory()
        _isTyping.value = true
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            try {
                val reply = api.reply(responseModel, requestHistory, files)
                if (generation == currentGeneration) {
                    clearAttachments()
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

