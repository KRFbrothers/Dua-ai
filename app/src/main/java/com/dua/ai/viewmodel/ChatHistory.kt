package com.dua.ai.viewmodel

import android.app.Application
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.viewmodel.ChatViewModel.ChatDeletionState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ChatViewModel state and actions the chat history and lifecycle (open, new, rename, pin, delete, clear) read and drive. Implemented by ChatViewModel. */
internal interface HistoryHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val account: com.dua.ai.account.AccountRepository
    val api: com.dua.ai.network.DuaApiClient
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val preferences: android.content.SharedPreferences
    val historyWriter: com.dua.ai.history.ChatHistoryWriter
    val draftSaver: com.dua.ai.history.Debouncer
    val mediaCache: com.dua.ai.imagine.MediaCache
    val onDeviceAi: com.dua.ai.ondevice.OnDeviceAi
    val replyAudioCache: com.dua.ai.voice.AudioChunkCache
    val separateChatEngine: Boolean
    val pollJobs: MutableMap<String, Job>
    val replyWebModes: MutableMap<String, String?>
    val replyReasoning: MutableMap<String, Boolean>
    val replyAgent: MutableMap<String, Boolean>
    val replyCode: MutableMap<String, Boolean>
    val replyBuild: MutableMap<String, String>
    val replyXSearch: MutableMap<String, Boolean>
    var activeId: String
    var deletionOwnerId: String?
    var historyReadable: Boolean
    val _dataBusy: MutableStateFlow<Boolean>
    val _dataStatus: MutableStateFlow<String>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _selectedModel: MutableStateFlow<String>
    val _draft: MutableStateFlow<String>
    val _summary: MutableStateFlow<com.dua.ai.model.ChatSummary?>
    val _webMode: MutableStateFlow<String?>
    val _xSearch: MutableStateFlow<Boolean>
    val _buildRepo: MutableStateFlow<String?>
    val _projectId: MutableStateFlow<String>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _deletedChatIds: MutableStateFlow<List<String>>
    val _chatDeletion: MutableStateFlow<ChatDeletionState>
    fun saveHistory()
    fun flushHistory()
    fun cancelReply()
    fun clearAttachments()
    fun refreshSyncBusy()
    fun syncBlocked(): Boolean
    /** [syncBlocked] without `_isTyping`: the conditions that guard the stored history, not a reply. */
    fun cacheBlocked(): Boolean
    fun currentDeletionIds(): List<String>
    fun onDeviceAiEnabled(): Boolean
}

/** Chat history and lifecycle: open, new, rename, auto title, pin, cloud rebind, server/local delete and clear all, moved out of ChatViewModel.kt unchanged. */
internal class ChatHistory(host: HistoryHost) : HistoryHost by host {

    fun clearAllChats() {
        replyAudioCache.clear()
        if (_dataBusy.value) return
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Clearing cached chats…"
        cancelReply()
        flushHistory()
        val nextId = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val committed = withContext(Dispatchers.IO) { historyWriter.writeDirectly {
                    val previous = preferences.getString("history", null)
                    val backup = preferences.getString("unreadable_history_backup", null)
                    val deleted = preferences.getString("deleted_chat_ids", null)
                    val deletionOwner = preferences.getString("deleted_chat_owner", null)
                    val success = preferences.edit().putString("history", ChatHistoryCodec.encode(emptyList(), nextId))
                        .remove("unreadable_history_backup").remove("deleted_chat_ids").remove("deleted_chat_owner").commit()
                    if (!success) preferences.edit().putString("history", previous)
                        .putString("unreadable_history_backup", backup).putString("deleted_chat_ids", deleted).putString("deleted_chat_owner", deletionOwner).apply()
                    if (success) runCatching { File(getApplication<Application>().cacheDir, "camera-captures").listFiles()?.forEach { it.delete() } }
                    success
                } }
                if (committed) {
                    // Cached references are disposable; their server-backed files must remain restorable.
                    pollJobs.values.forEach { it.cancel() }; pollJobs.clear()
                    runCatching { mediaCache.clear() }
                    activeId = nextId
                    historyReadable = true
                    _messages.value = emptyList()
                    _draft.value = ""
                    _savedChats.value = emptyList()
                    _deletedChatIds.value = emptyList()
                    deletionOwnerId = null
                    _summary.value = null
                    _buildRepo.value = null
                    _webMode.value = null
                    _xSearch.value = false
                    replyXSearch.clear()
                    replyWebModes.clear()
                    replyReasoning.clear(); replyAgent.clear()
                    clearAttachments()
                    _dataStatus.value = "Cached chats, drafts and pending attachments cleared. Server chats and their stored files remain. Discarded unsent uploads are cleaned up separately."
                } else _dataStatus.value = "Delete could not be saved. Chats are kept in memory; try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _dataStatus.value = "Cached chats could not be cleared. Try again." }
            finally { _dataBusy.value = false }
        }
    }
    fun openChat(id: String) {
        draftSaver.runNow() // Save the current chat's typed draft before switching away from it.
        val chat = _savedChats.value.find { it.id == id } ?: return
        cancelReply()
        clearAttachments()
        activeId = chat.id
        _messages.value = chat.messages
        _selectedModel.value = chat.model
        _draft.value = chat.draft
        _summary.value = chat.summary
        _webMode.value = null
        _xSearch.value = false
        _buildRepo.value = chat.buildRepository
        _projectId.value = chat.projectId
        saveHistory()
    }
    fun renameChat(id: String, value: String): String? {
        if (syncBlocked()) return "Finish the current reply or sync action, then try again."
        if (!_integrations.value.chatRenameEnabled) return "Refresh setup after updating the backend, then try again."
        val title = try { com.dua.ai.history.ChatTitles.normalize(value) }
            catch (_: IllegalArgumentException) { return com.dua.ai.history.ChatTitles.ERROR }
        draftSaver.runNow()
        val chat = _savedChats.value.find { it.id == id } ?: return "This chat is no longer available."
        if (chat.customTitle == title) return null
        _savedChats.value = _savedChats.value.map {
            if (it.id == id) it.copy(customTitle = title, updatedAt = System.currentTimeMillis()) else it
        }
        historyWriter.save(_savedChats.value, activeId)
        return null
    }
    internal fun maybeAutoTitle(chatId: String) {
        if (!separateChatEngine || !_integrations.value.chatRenameEnabled) return
        val messages = _messages.value
        if (!com.dua.ai.model.AutoTitles.shouldTitle(messages)) return
        if (_savedChats.value.find { it.id == chatId }?.customTitle != null) return
        viewModelScope.launch {
            // On-device first (supported phones with the setting on); otherwise, or if it gives nothing, the server as before.
            val local = if (onDeviceAiEnabled()) try { onDeviceAi.title(messages[0].text, messages[1].text) }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } else null
            val title = local ?: try { account.withToken { token -> api.chatTitle(messages[0].text, messages[1].text, token) } }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } ?: return@launch
            val chat = _savedChats.value.find { it.id == chatId } ?: return@launch
            if (chat.customTitle != null) return@launch
            _savedChats.value = _savedChats.value.map { if (it.id == chatId) it.copy(customTitle = title) else it }
            historyWriter.save(_savedChats.value, activeId)
        }
    }
    fun setChatPinned(id: String, pinned: Boolean): String? {
        if (syncBlocked()) return "Finish the current reply or sync action, then try again."
        if (!_integrations.value.chatPinEnabled) return "Refresh setup after updating the backend, then try again."
        draftSaver.runNow()
        val chat = _savedChats.value.find { it.id == id } ?: return "This chat is no longer available."
        if (chat.pinned == pinned) return null
        // Pinning does not change the conversation's last-message timestamp.
        _savedChats.value = _savedChats.value.map { if (it.id == id) it.copy(pinned = pinned) else it }
        historyWriter.save(_savedChats.value, activeId)
        return null
    }
    internal fun bindCloudHistory(chats: List<SavedChat>, deleted: List<String>, selectedId: String = activeId) {
        val removed = _savedChats.value.filter { it.id in deleted }.flatMap { it.messages }
        _deletedChatIds.value = deleted
        deletionOwnerId = account.state.value.user?.id
        _savedChats.value = chats
        removed.forEach { message ->
            pollJobs.remove(message.id)?.cancel()
            replyWebModes.remove(message.id); replyReasoning.remove(message.id); replyAgent.remove(message.id); replyCode.remove(message.id); replyBuild.remove(message.id)
        }
        val used = pathsOf(chats.flatMap { it.messages }).toSet()
        runCatching { mediaCache.forget(pathsOf(removed).filterNot { it in used }.distinct()) }
        activeId = selectedId
        if (activeId in deleted) {
            activeId = UUID.randomUUID().toString()
            _messages.value = emptyList(); _draft.value = ""; _summary.value = null
            _webMode.value = null; _xSearch.value = false; _buildRepo.value = null
            clearAttachments()
        } else chats.find { it.id == activeId }?.let { chat ->
            _messages.value = api.keepInlineContext(chat.messages, _messages.value); _selectedModel.value = chat.model; _draft.value = chat.draft
            _summary.value = chat.summary; _buildRepo.value = chat.buildRepository; _projectId.value = chat.projectId
        }
    }
    fun deleteServerChat(id: String) {
        if (_chatDeletion.value.busy) return
        fun fail(message: String) { _chatDeletion.value = ChatDeletionState(id = id, error = message) }
        if (syncBlocked()) { fail("Finish the current reply or sync action, then try again."); return }
        if (account.state.value.user == null) { fail("Sign in from Profile before deleting server history."); return }
        if (!_integrations.value.chatDeletionEnabled) { fail("Refresh setup after updating the backend, then try again."); return }
        if (_savedChats.value.none { it.id == id }) { fail("This chat is no longer available."); return }
        _chatDeletion.value = ChatDeletionState(id = id, busy = true)
        _dataBusy.value = true; refreshSyncBusy()
        viewModelScope.launch {
            var serverConfirmed = false
            try {
                val review = account.cloud()
                val result = account.deleteChat(review, id)
                serverConfirmed = true
                applyConfirmedChatDeletion(result)
                _chatDeletion.value = ChatDeletionState(id = id, completed = true)
            } catch (cancelled: CancellationException) {
                fail("Deletion was interrupted. Refresh cloud history before retrying."); throw cancelled
            } catch (failure: Exception) {
                fail(if (serverConfirmed) "Server deletion succeeded, but the phone cache could not be updated. Retry to finish."
                    else failure.message ?: "Chat deletion could not be confirmed. Retry; the phone cache was kept.")
            } finally { _dataBusy.value = false; refreshSyncBusy() }
        }
    }
    internal suspend fun applyConfirmedChatDeletion(result: com.dua.ai.account.CloudChatDeletion) {
        check(account.state.value.user?.id == result.ownerId) { "Account changed. Refresh cloud history." }
        check(result.chatId in result.deletedChatIds) { "Chat deletion was not confirmed." }
        flushHistory()
        val original = _savedChats.value.toList()
        val selectedId = activeId
        val deleted = (currentDeletionIds() + result.deletedChatIds).distinct().sorted()
        com.dua.ai.account.CloudDeletionIds.validate(deleted)
        val chats = original.filterNot { it.id in deleted }
        val nextId = if (selectedId in deleted) UUID.randomUUID().toString() else selectedId
        val encoded = withContext(Dispatchers.Default) { ChatHistoryCodec.encode(chats, nextId) }
        check(account.state.value.user?.id == result.ownerId && activeId == selectedId && _savedChats.value == original && !draftSaver.pending) {
            "Phone cache changed while applying deletion. Retry."
        }
        val previous = preferences.getString("history", null)
        val previousDeleted = preferences.getString("deleted_chat_ids", null)
        val previousOwner = preferences.getString("deleted_chat_owner", null)
        val saved = historyWriter.writeDirectly {
            preferences.edit().putString("history", encoded).putString("deleted_chat_ids", org.json.JSONArray(deleted).toString())
                .putString("deleted_chat_owner", result.ownerId).commit().also { ok ->
                if (!ok) preferences.edit().putString("history", previous).putString("deleted_chat_ids", previousDeleted).putString("deleted_chat_owner", previousOwner).apply()
            }
        }
        check(saved) { "Phone cache could not be saved." }
        bindCloudHistory(chats, deleted)
        if (selectedId in deleted) activeId = nextId
    }
    fun deleteChat(id: String) {
        // Same stored-state guard the server path uses: a pick, a cloud review, an import preview, a
        // build PR or a starting live session owns the draft and the chat list until it finishes.
        if (cacheBlocked()) return
        draftSaver.runNow()
        _savedChats.value.find { it.id == id }?.messages?.forEach { replyWebModes.remove(it.id); replyReasoning.remove(it.id); replyAgent.remove(it.id); replyCode.remove(it.id); replyBuild.remove(it.id) }
        // Remove cached references/media only. The server snapshot may still need the stored files.
        val chatMessages = if (id == activeId) _messages.value else _savedChats.value.find { it.id == id }?.messages.orEmpty()
        val stillUsed = pathsOf(_savedChats.value.filterNot { it.id == id }.flatMap { it.messages } + if (id == activeId) emptyList() else _messages.value).toSet()
        val removed = pathsOf(chatMessages).filterNot { it in stillUsed }.distinct()
        runCatching { mediaCache.forget(removed) }
        chatMessages.forEach { message -> pollJobs.remove(message.id)?.cancel() }
        if (id == activeId) {
            cancelReply()
            // The composer belongs to the chat being deleted: its draft files, pending uploads and
            // selection counter must go with it, or they leak into the next conversation.
            clearAttachments()
            activeId = UUID.randomUUID().toString()
            _messages.value = emptyList()
            _draft.value = ""
            _summary.value = null
            _webMode.value = null
            _xSearch.value = false
            _buildRepo.value = null
        }
        _savedChats.value = _savedChats.value.filterNot { it.id == id }
        saveHistory()
    }
    fun startNewChat() {
        draftSaver.runNow()
        cancelReply()
        clearAttachments()
        activeId = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _draft.value = ""
        _summary.value = null
        _webMode.value = null
        _xSearch.value = false
        _buildRepo.value = null
        if (localSettings.value.projects.none { it.id == _projectId.value && !it.archived })
            _projectId.value = localSettings.value.projects.first { !it.archived }.id
        saveHistory()
    }
}
