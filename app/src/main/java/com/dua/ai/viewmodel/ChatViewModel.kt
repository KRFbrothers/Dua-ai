package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dua.ai.history.SavedChat
import com.dua.ai.history.ChatHistoryCodec
import java.util.UUID
import android.net.Uri
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.attachments.PhotoPolicy
import com.dua.ai.attachments.PhotoAttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.dua.ai.attachments.CameraCaptureStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSession
import com.dua.ai.model.PromptSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.DuaApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudReview
import com.dua.ai.setup.AutomaticCloudSync
import com.dua.ai.setup.AutomaticSyncAccount
import com.dua.ai.setup.AutomationSyncStore
import com.dua.ai.files.AudioRecorder
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingFileDeletes
import com.dua.ai.files.PendingUpload
import com.dua.ai.files.SignedUpload
import com.dua.ai.files.StorageUsage
import com.dua.ai.files.StoredFileClient
import com.dua.ai.files.StoredFileTypes
import kotlinx.coroutines.flow.update

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    val account = com.dua.ai.account.AccountRepository(application)
    private val setupApi = com.dua.ai.account.AccountApi()
    private val generatedAudioApi = com.dua.ai.voice.GeneratedAudioApi()
    private val _online = MutableStateFlow(false)
    val online = _online.asStateFlow()
    /** Generated reply audio for this process only (memory, ~12 MB); replays and voice previews are free. */
    private val replyAudioCache = com.dua.ai.voice.AudioChunkCache()
    /** Reply voice resolved from Voice settings and the server catalog; null = older backend (single legacy voice). */
    fun replyTtsOptions(): com.dua.ai.voice.TtsOptions? = localSettings.value.let { settings ->
        com.dua.ai.voice.VoiceCodec.ttsOptions(_integrations.value.voice, settings.voiceProvider, settings.voiceName, settings.replyLanguage, settings.voiceSpeed)
    }
    /** Splits a reply for AI voice: markdown is cleaned first; Phase 4 backends get chunked playback. */
    fun replyVoicePlan(text: String): List<String> {
        val spoken = com.dua.ai.voice.SpeakableText.clean(text).ifBlank { text.trim() }
        val options = replyTtsOptions() ?: return listOf(com.dua.ai.voice.GeneratedVoiceText.validate(spoken, true))
        val limit = _integrations.value.voice?.provider(options.provider)?.maxCharacters ?: 1000
        return com.dua.ai.voice.TtsChunker.split(spoken, limit)
    }
    suspend fun generateReplyAudio(text: String): ByteArray {
        check(_online.value && _integrations.value.generatedVoiceConfigured) { "AI voice setup is unavailable. Try device voice." }
        val options = replyTtsOptions()
        val cacheKey = options?.cacheKey(text)?.let { (account.state.value.user?.id ?: "") + "\u0000" + it }
        cacheKey?.let(replyAudioCache::get)?.let { return it }
        var received: ByteArray? = null
        try {
            return account.withToken { token ->
                check(token != null) { "Sign in before using AI voice." }
                if (options == null) generatedAudioApi.generate(text, token).also { received = it }
                else {
                    val limit = _integrations.value.voice?.provider(options.provider)?.maxCharacters ?: 1000
                    generatedAudioApi.synthesize(text, token, options, limit).bytes.also { received = it; replyAudioCache.put(cacheKey!!, it) }
                }
            }
        } catch (failure: Exception) { received?.fill(0); throw failure }
    }
    fun clearReplyAudioCache() = replyAudioCache.clear()
    private val _integrations = MutableStateFlow(com.dua.ai.setup.IntegrationState())
    val integrations = _integrations.asStateFlow()
    private val _searchResults = MutableStateFlow<List<com.dua.ai.setup.SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _connectorRepositories = MutableStateFlow<List<com.dua.ai.setup.RepositoryResult>>(emptyList())
    val connectorRepositories = _connectorRepositories.asStateFlow()
    private val _memoryCandidates = MutableStateFlow<List<String>>(emptyList())
    val memoryCandidates = _memoryCandidates.asStateFlow()
    fun proposeMemoryNotes() {
        val existing = localSettings.value.memory
        _memoryCandidates.value = _messages.value.filter { it.isUser && it.state == "complete" }.takeLast(20)
            .flatMap { com.dua.ai.setup.AutomationMemoryCandidates.propose(it.text, existing) }.distinct().take(4)
        integrationNotice(if (_memoryCandidates.value.isEmpty()) "No explicit memory requests found. Try saying ‘remember that I prefer Hindi’." else "Review each proposed note before saving. Nothing has been sent or saved automatically.")
    }
    fun acceptMemoryNote(note: String) {
        if (note !in _memoryCandidates.value) return
        try {
            val settings = localSettings.value
            val notes = com.dua.ai.setup.AutomationMemoryCandidates.confirm(note, settings.memory)
            localPreferences.update(settings.copy(memory = notes))
            _memoryCandidates.value = _memoryCandidates.value.filterNot { it == note }
            integrationNotice("Note saved locally. Notes are sent with chat only when Memory is enabled.")
        } catch (failure: Exception) { integrationNotice(failure.message ?: "Memory could not be saved.") }
    }
    fun dismissMemoryNote(note: String) { _memoryCandidates.value = _memoryCandidates.value.filterNot { it == note } }
    fun integrationNotice(text: String) { _integrations.value = _integrations.value.copy(notice = text) }
    fun loadProviderConfiguration() = viewModelScope.launch {
        try {
            val config = setupApi.request("/api/config")
            _integrations.value = com.dua.ai.setup.IntegrationCapabilities.configuration(config, _integrations.value)
            flushPendingFileDeletes()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { integrationNotice("Provider status could not be loaded. Dua and Gemini use the existing backend configuration; refresh Feature setup to retry.") }
    }
    private fun integrationAction(action: suspend () -> Unit) {
        if (_integrations.value.busy) return
        _integrations.value = _integrations.value.copy(busy = true, notice = "")
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { integrationNotice(failure.message ?: "Integration request failed. Local data was kept.") }
            finally { _integrations.value = _integrations.value.copy(busy = false) }
        }
    }
    fun loadIntegrations() = integrationAction {
        val next = com.dua.ai.setup.IntegrationCapabilities.decode(setupApi.request("/api/setup"))
        val current = _integrations.value
        // /api/setup has no per-feature config; keep what /api/config reported.
        _integrations.value = next.copy(busy = true, notice = "Setup status refreshed.",
            webSearchConfigured = current.webSearchConfigured, pageReadingAvailable = current.pageReadingAvailable,
            filesEnabled = current.filesEnabled, filesMaxBytes = current.filesMaxBytes, nativeSearch = current.nativeSearch,
            xSearchAvailable = current.xSearchAvailable, longContext = current.longContext, imagine = current.imagine,
            voice = current.voice, generatedVoiceConfigured = current.voice?.ttsEnabled ?: next.generatedVoiceConfigured)
    }
    fun searchWeb(query: String) = integrationAction {
        require(query.trim().length in 1..500) { "Enter a search up to 500 characters." }
        _searchResults.value = emptyList()
        val response = account.request("/api/search", "POST", org.json.JSONObject().put("query", query.trim()))
        _searchResults.value = com.dua.ai.setup.IntegrationCapabilities.search(response)
        integrationNotice(if (_searchResults.value.isEmpty()) "No search results." else "Search complete. Open a source to read it.")
    }
    fun listConnectorRepositories() = integrationAction {
        _connectorRepositories.value = emptyList()
        val response = account.request("/api/connectors/github", "POST", org.json.JSONObject().put("action", "list-repositories"), authenticated = true)
        _connectorRepositories.value = com.dua.ai.setup.IntegrationCapabilities.repositories(response)
        integrationNotice(if (_connectorRepositories.value.isEmpty()) "No repositories returned." else "Read-only repository list loaded.")
    }
    data class CloudPreview(val kind: String, val userId: String, val review: com.dua.ai.account.CloudReview)
    private val _cloudPreview = MutableStateFlow<CloudPreview?>(null)
    val cloudPreview = _cloudPreview.asStateFlow()
    fun accountAction(action: suspend () -> Unit) {
        if (account.state.value.busy || _dataBusy.value) return
        account.busy(true)
        automaticSync.setBusy(true)
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { account.notice(failure.message ?: "Account request failed. Local data was kept.") }
            finally { account.busy(false); refreshSyncBusy() }
        }
    }
    fun loadAccount() = accountAction { account.load(); account.loadFeatures(); flushPendingFileDeletes() }
    fun signIn(email: String, password: String, signup: Boolean) = accountAction { _cloudPreview.value = null; account.signIn(email, password, signup) }
    fun signInWithGoogle(idToken: String, nonce: String) = accountAction { _cloudPreview.value = null; account.signInWithGoogle(idToken, nonce) }
    fun logOut() { automaticSync.disable(); accountAction { _cloudPreview.value = null; account.logout() } }
    fun cancelCloudReview() { _cloudPreview.value = null; account.notice("Cancelled. Data was not changed.") }
    fun reviewCloud(kind: String) = accountAction {
        val remote = account.cloud()
        val snapshot = if (kind == "upload") com.dua.ai.account.CloudSnapshot(_savedChats.value.toList(), localSettings.value.memory.toList()) else remote.snapshot
        com.dua.ai.account.CloudSnapshotCodec.encode(snapshot)
        _cloudPreview.value = CloudPreview(kind, account.state.value.user?.id ?: error("Sign in again."), remote.copy(snapshot = snapshot))
    }
    fun confirmCloud() = accountAction {
        _dataBusy.value = true; refreshSyncBusy()
        try {
        val preview = _cloudPreview.value ?: return@accountAction
        account.load()
        check(account.state.value.user?.id == preview.userId) { "Account changed. Review again." }
        when (preview.kind) {
            "upload" -> account.notice("Uploaded cloud revision ${account.upload(preview.review)}. Local data stays unchanged.")
            "delete" -> { account.upload(preview.review, clear = true); account.notice("Cloud snapshot cleared. Local data stays unchanged.") }
            "download" -> {
                val oldSettings = localSettings.value
                val notes = (oldSettings.memory + preview.review.snapshot.memory).distinct()
                require(notes.size <= 10) { "Merged memories exceed 10 notes. Remove some local notes and review again." }
                val result = com.dua.ai.account.CloudSnapshotCodec.merge(_savedChats.value, preview.review.snapshot.chats)
                val encoded = ChatHistoryCodec.encode(result.chats, activeId)
                val previous = preferences.getString("history", null)
                localPreferences.update(oldSettings.copy(memory = notes))
                try {
                    val committed = withContext(Dispatchers.IO) { preferences.edit().putString("history", encoded).commit() }
                    if (!committed) { preferences.edit().putString("history", previous).apply(); error("Cloud chats could not be saved. Retry.") }
                    _savedChats.value = result.chats
                } catch (failure: Exception) {
                    try { localPreferences.update(oldSettings) } catch (_: Exception) { throw IllegalStateException("Import interrupted and memory rollback failed. Inspect local memories before retrying.", failure) }
                    throw failure
                }
                account.notice("${result.added} chats merged; ${result.conflicts} conflicts kept as copies.")
            }
        }
        _cloudPreview.value = null
        } finally { _dataBusy.value = false }
    }
    private val api = DuaApiClient()

    // ---- Imagine (Phase 3): image and video generation through /api/imagine ----
    private val imagineApi = com.dua.ai.imagine.ImagineApi()
    /** Generated media cache; signed links come from /api/files/url (owner checked on the server). */
    val mediaCache = com.dua.ai.imagine.MediaCache(application.cacheDir, { path -> storedFileUrl(path) })
    private val _imagine = MutableStateFlow(false)
    val imagine = _imagine.asStateFlow()
    private val _imagineOptions = MutableStateFlow(com.dua.ai.imagine.ImagineOptions())
    val imagineOptions = _imagineOptions.asStateFlow()
    private val _imagineUsage = MutableStateFlow<com.dua.ai.imagine.ImagineUsage?>(null)
    val imagineUsage = _imagineUsage.asStateFlow()
    /** Video status polling per reply message id; survives leaving the chat (not the process: jobs resume on start). */
    private val pollJobs = mutableMapOf<String, Job>()
    private val localPreferences = com.dua.ai.settings.DevicePreferences(application)
    val localSettings = localPreferences.state
    private val _settingsStatus = MutableStateFlow("")
    val settingsStatus = _settingsStatus.asStateFlow()
    var foreground = false
        private set
    fun setForeground(value: Boolean) {
        foreground = value
        automaticSync.setForeground(value)
        refreshSyncNetwork()
    }
    fun resetSettings() { try { localPreferences.reset(); _settingsStatus.value = "Settings and memories reset." } catch (error: Exception) { _settingsStatus.value = error.message ?: "Reset failed." } }
    fun settingsNotice(text: String) { _settingsStatus.value = text }
    fun updateSettings(value: com.dua.ai.settings.LocalPreferences) {
        try { localPreferences.update(value); _settingsStatus.value = "Saved on this device." }
        catch (error: Exception) { _settingsStatus.value = error.message ?: "Settings could not be saved." }
    }
    fun shareChatText(chat: SavedChat): String = "Dua conversation\n\n" + chat.messages.filter { it.state == "complete" }
        .joinToString("\n\n") { (if (it.isUser) "You" else "Dua") + ": " + it.text }

    private var generation = 0
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _failedPrompt = MutableStateFlow<String?>(null)
    val failedPrompt: StateFlow<String?> = _failedPrompt.asStateFlow()
    fun consumeFailedPrompt() { _failedPrompt.value = null }
    private var replyJob: Job? = null
    /** Reply being regenerated by Retry; restored if the retry is stopped before any text arrives. */
    private var retryOriginal: ChatMessage? = null
    private fun cancelReply() {
        generation++
        _error.value = null
        _failedPrompt.value = null
        replyJob?.cancel()
        replyJob = null
        val changed = finalizeStreaming()
        _isTyping.value = false
        // Save while activeId still points at the chat that was streaming (callers may switch chats next).
        if (changed) saveHistory()
    }

    /** A stopped stream keeps its partial text; an empty one disappears (or a retried reply comes back). */
    private fun finalizeStreaming(): Boolean {
        val original = retryOriginal
        retryOriginal = null
        if (_messages.value.none { it.streaming }) return false
        _messages.value = _messages.value.mapNotNull { message ->
            when {
                !message.streaming -> message
                message.text.isNotBlank() -> message.copy(state = "stopped", thinkingSeconds = message.thinkingSeconds ?: elapsedThinking(message))
                original != null && original.id == message.id -> original
                else -> null
            }
        }
        return true
    }
    private fun elapsedThinking(message: ChatMessage): Int? =
        if (message.thinking.isNullOrBlank() || message.startedAt <= 0) null
        else (((System.currentTimeMillis() - message.startedAt) + 500) / 1000).toInt().coerceIn(1, 86400)

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    /**
     * Streams into message [id], rendering at most ~30 times a second (latest state wins).
     * Events are applied to [accumulator] as they arrive, so a failure keeps everything received.
     */
    private suspend fun collectReply(id: String, accumulator: com.dua.ai.network.StreamAccumulator, events: kotlinx.coroutines.flow.Flow<com.dua.ai.network.ReplyEvent>,
        request: RequestTurns? = null) {
        val chatId = activeId
        events.onEach {
            if (it is com.dua.ai.network.ReplyEvent.Files) applyTranscripts(it.transcripts)
            if (it is com.dua.ai.network.ReplyEvent.Context && request?.context != null) applySummary(chatId, request.turns, it)
            accumulator.apply(it)
        }.conflate().collect {
            val snapshot = accumulator.snapshot()
            updateMessage(id) { it.copy(text = snapshot.text, thinking = snapshot.thinking, thinkingSeconds = snapshot.thinkingSeconds, notice = snapshot.notice) }
            delay(STREAM_FRAME_MS)
        }
    }

    private fun failureText(failure: Throwable): String = when (failure) {
        is DuaApiException, is IllegalArgumentException -> failure.message ?: "Reply nahi aa saka. Dobara try karein."
        is java.net.SocketTimeoutException -> "Dua response timeout hua. Dobara try karein."
        else -> "Connection fail hua. Dobara try karein."
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _selectedModel = MutableStateFlow("Dua")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Web requests require a choice in this session; history and cloud snapshots never save it.
    private val _webMode = MutableStateFlow<String?>(null)
    val webMode = _webMode.asStateFlow()
    private val replyWebModes = mutableMapOf<String, String?>()
    fun replyWebMode(messageId: String): String? = replyWebModes[messageId]
    // Reasoning is a per-session choice like the web mode; it combines with Search / Read link.
    private val _reasoning = MutableStateFlow(false)
    val reasoning = _reasoning.asStateFlow()
    private val replyReasoning = mutableMapOf<String, Boolean>()
    fun setReasoning(enabled: Boolean) { if (!_isTyping.value) { _reasoning.value = enabled; if (enabled) _imagine.value = false } }
    /** Auto chip: plain chat, no web and no Reasoning. */
    fun selectAuto() { if (_isTyping.value) return; _reasoning.value = false; _xSearch.value = false; _imagine.value = false; setWebMode(null) }

    // X search (Grok only, billed per post by xAI) is off by default and, like the web mode, never saved.
    private val _xSearch = MutableStateFlow(false)
    val xSearch = _xSearch.asStateFlow()
    private val replyXSearch = mutableMapOf<String, Boolean>()
    private fun xSearchUsable(model: String = _selectedModel.value) = model == "Grok" && _integrations.value.xSearchAvailable
    fun setXSearch(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled && !xSearchUsable()) { _error.value = "X search works with Grok when the server offers it."; return }
        _xSearch.value = enabled
        _error.value = null
    }

    // Long context: the server's summary of this chat's older turns (saved with the chat on this device).
    private val _summary = MutableStateFlow<com.dua.ai.model.ChatSummary?>(null)
    val summary = _summary.asStateFlow()
    internal data class RequestTurns(val turns: List<ChatMessage>, val context: com.dua.ai.model.ConversationContext?, val historyBytes: Int)

    /** Long-context servers get every turn after the cached summary; older servers the last 39 turns as before. */
    private fun requestTurns(history: List<ChatMessage>, legacy: List<ChatMessage>, inlineFiles: Boolean): RequestTurns {
        if (!_integrations.value.longContext) return RequestTurns(legacy, null, historyBytes())
        val budget = if (inlineFiles) DuaApiClient.LONG_HISTORY_BYTES_WITH_FILES else DuaApiClient.LONG_HISTORY_BYTES
        val selection = com.dua.ai.model.ContextWindow.select(history, _summary.value, budget, DuaApiClient::turnJsonBytes)
        return RequestTurns(selection.turns, com.dua.ai.model.ConversationContext(activeId, selection.summary?.text), budget + 1024)
    }

    private fun applySummary(chatId: String, sent: List<ChatMessage>, event: com.dua.ai.network.ReplyEvent.Context) {
        val next = com.dua.ai.model.ContextWindow.advance(sent, event.covered, event.summary) ?: return
        if (chatId != activeId) return
        _summary.value = next
        saveHistory()
    }
    fun setWebMode(mode: String?) {
        if (_isTyping.value || mode !in listOf(null, "search", "page")) return
        webModeError(mode)?.let { _error.value = it; return }
        _webMode.value = mode
        if (mode != null) _imagine.value = false
        _error.value = null
    }
    private fun webModeError(mode: String?, model: String = _selectedModel.value): String? = when {
        mode == "search" && !_integrations.value.searchAvailable(model) -> "Web search needs setup for $model. Open Feature setup to configure search, choose a model that searches natively, or choose Off or Read link."
        mode == "page" && !_integrations.value.pageReadingAvailable -> "Read link is unavailable. Refresh Feature setup, or choose Off."
        else -> null
    }

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

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
        discardUploads()
    }

    // ---- Stored files: direct uploads to the private Supabase bucket (owner sign-in required) ----
    private val fileClient = StoredFileClient()
    private val filePreferences = application.getSharedPreferences("dua_files", 0)
    private val pendingDeletes = PendingFileDeletes({ filePreferences.getString("pending_deletes", null) }, { filePreferences.edit().putString("pending_deletes", it).apply() })
    private val _uploads = MutableStateFlow<List<PendingUpload>>(emptyList())
    val uploads: StateFlow<List<PendingUpload>> = _uploads.asStateFlow()
    private val uploadJobs = mutableMapOf<String, Job>()
    private val recorder = AudioRecorder(application)
    private val _recordingSince = MutableStateFlow<Long?>(null)
    val recordingSince: StateFlow<Long?> = _recordingSince.asStateFlow()
    private val _storageUsage = MutableStateFlow<StorageUsage?>(null)
    val storageUsage: StateFlow<StorageUsage?> = _storageUsage.asStateFlow()

    /** Stored files need a Phase 2 backend and a signed-in account (the server also requires the owner email). */
    fun storedFilesAvailable() = _integrations.value.filesEnabled && account.state.value.user != null
    private fun maxStoredBytes() = _integrations.value.filesMaxBytes.takeIf { it > 0 } ?: (50L * 1024 * 1024)
    fun storedFilesHint(): String = when {
        !_integrations.value.filesEnabled -> "Audio, spreadsheets and files over 2 MB need stored files on the Dua backend (see SUPABASE.md). Small images, PDFs and text files still work."
        account.state.value.user == null -> "Sign in from Profile to attach audio, spreadsheets and large files (up to ${StoredFileTypes.sizeLabel(maxStoredBytes())} each). Without signing in, up to 3 small files (2 MB total) still work."
        else -> ""
    }
    private data class PickedFile(val uri: Uri, val name: String, val type: String, val size: Long?)

    private fun updateUpload(id: String, transform: (PendingUpload) -> PendingUpload) = _uploads.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun startUpload(name: String, type: String, size: Long, open: () -> java.io.InputStream, cleanup: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        _uploads.update { it + PendingUpload(id, name, type, size) }
        uploadJobs[id] = viewModelScope.launch {
            try {
                val signed = SignedUpload.parse(account.request("/api/files/sign", "POST",
                    org.json.JSONObject().put("name", name).put("mimeType", type).put("size", size), authenticated = true))
                withContext(Dispatchers.IO) { fileClient.upload(signed, size, open) { sent -> updateUpload(id) { it.copy(sent = sent) } } }
                val ref = FileRef(signed.path, name, signed.mimeType.ifBlank { type }, size)
                if (_uploads.value.none { it.id == id }) deleteStoredPaths(listOf(ref.path))
                else updateUpload(id) { it.copy(state = PendingUpload.DONE, sent = size, ref = ref) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateUpload(id) { it.copy(state = PendingUpload.FAILED, error = when (failure) {
                    is java.io.IOException -> "Upload interrupted. Check the connection and try again."
                    else -> failure.message ?: "Upload failed. Try again."
                }) }
            } finally { uploadJobs.remove(id); runCatching { cleanup() } }
        }
    }

    /** Removes one pending upload; an already uploaded (unsent) file is deleted from storage. */
    fun cancelUpload(id: String) {
        val upload = _uploads.value.find { it.id == id } ?: return
        if (_isTyping.value) return
        uploadJobs.remove(id)?.cancel()
        _uploads.update { list -> list.filterNot { it.id == id } }
        if (!upload.shared) upload.ref?.let { deleteStoredPaths(listOf(it.path)) }
    }

    private fun discardUploads() {
        val current = _uploads.value
        if (current.isEmpty()) return
        current.forEach { uploadJobs.remove(it.id)?.cancel() }
        _uploads.value = emptyList()
        deleteStoredPaths(current.filterNot { it.shared }.mapNotNull { it.ref?.path })
    }

    private fun describe(uris: List<Uri>): List<PickedFile> {
        val resolver = getApplication<Application>().contentResolver
        return uris.map { uri ->
            var name = "Selected file"; var size: Long? = null
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) { name = it.getString(0) ?: name; if (!it.isNull(1)) size = it.getLong(1) }
            }
            PickedFile(uri, name.take(200), resolver.getType(uri).orEmpty(), size?.takeIf { it >= 0 })
        }
    }

    /** Uploads picked files that storage accepts; returns the rest for the small inline path. */
    private suspend fun uploadPicked(files: List<PickedFile>): List<Uri> {
        val resolver = getApplication<Application>().contentResolver
        val inline = mutableListOf<Uri>()
        var errors = 0
        for (file in files) {
            val type = StoredFileTypes.storedType(file.name, file.type)
            if (type == null) { inline += file.uri; continue }
            if (_uploads.value.count { it.state != PendingUpload.FAILED } >= MAX_UPLOADS) { _attachmentError.value = "Up to $MAX_UPLOADS stored files per message."; break }
            var size = file.size
            var temp: File? = null
            if (size == null) {
                // Some providers do not report a size; copy to cache first (capped) so the upload has a length.
                temp = withContext(Dispatchers.IO) { copyCapped(file.uri, maxStoredBytes()) }
                size = temp?.length()
            }
            when {
                size == null -> { errors++; _attachmentError.value = "\"${file.name}\" is larger than ${StoredFileTypes.sizeLabel(maxStoredBytes())}." }
                size == 0L -> { temp?.delete(); errors++; _attachmentError.value = "\"${file.name}\" is empty." }
                size > maxStoredBytes() -> { temp?.delete(); errors++; _attachmentError.value = "\"${file.name}\" is larger than ${StoredFileTypes.sizeLabel(maxStoredBytes())}. Choose a smaller file." }
                else -> {
                    val copy = temp
                    startUpload(file.name, type, size, open = { copy?.inputStream() ?: resolver.openInputStream(file.uri) ?: throw java.io.IOException("File cannot be read.") },
                        cleanup = { copy?.delete() })
                }
            }
        }
        if (errors == 0 && inline.isEmpty()) _attachmentError.value = ""
        return inline
    }

    private fun copyCapped(uri: Uri, limit: Long): File? {
        val dir = File(getApplication<Application>().cacheDir, "upload-staging").apply { mkdirs() }
        val target = File(dir, UUID.randomUUID().toString())
        val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: return null
        var total = 0L
        input.use { source -> target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = source.read(buffer); if (count < 0) break; total += count; if (total > limit) { target.delete(); return null }; out.write(buffer, 0, count) }
        } }
        return target
    }

    /** Deletes storage objects now (or later, if offline / signed out). */
    private fun deleteStoredPaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        pendingDeletes.add(paths)
        flushPendingFileDeletes()
    }
    private var flushingDeletes = false
    fun flushPendingFileDeletes() {
        val user = account.state.value.user ?: return
        if (flushingDeletes) return
        val paths = pendingDeletes.forUser(user.id).take(500)
        if (paths.isEmpty()) return
        flushingDeletes = true
        viewModelScope.launch {
            var again = false
            try {
                account.request("/api/files/delete", "POST", org.json.JSONObject().put("paths", org.json.JSONArray(paths)), authenticated = true)
                pendingDeletes.remove(paths); again = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: com.dua.ai.account.AccountException) { if (failure.status == 400) pendingDeletes.remove(paths) }
            catch (_: Exception) { }
            finally { flushingDeletes = false }
            if (again) flushPendingFileDeletes()
        }
    }
    private fun pathsOf(messages: List<ChatMessage>) = messages.flatMap { message -> message.files.map { it.path } + message.media.mapNotNull { it.path } }

    /** Opens a stored file with a short-lived signed link (the server checks the owner). */
    suspend fun storedFileUrl(ref: FileRef): String = storedFileUrl(ref.path)

    fun startRecording() {
        if (_isTyping.value || recorder.recording) return
        if (!storedFilesAvailable()) { _attachmentError.value = storedFilesHint(); return }
        try { recorder.start(); _recordingSince.value = recorder.startedAt; _attachmentError.value = "" }
        catch (failure: Exception) { _attachmentError.value = failure.message ?: "Recording could not start." }
    }
    fun stopRecording() {
        val file = recorder.stop()
        _recordingSince.value = null
        if (file == null) { _attachmentError.value = "Recording was too short. Hold on a little longer and try again."; return }
        val name = "Voice note " + java.time.LocalTime.now().withNano(0).toString().take(5) + ".m4a"
        startUpload(name, AudioRecorder.MIME, file.length(), open = { file.inputStream() }, cleanup = { file.delete() })
    }
    fun cancelRecording() { recorder.cancel(); _recordingSince.value = null }

    fun loadStorageUsage() {
        if (account.state.value.user == null) { _storageUsage.value = null; return }
        viewModelScope.launch {
            try {
                val json = account.request("/api/files/usage", authenticated = true)
                _storageUsage.value = StorageUsage(json.optInt("files"), json.optLong("bytes"), json.optLong("maxBytes"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _storageUsage.value = null; _dataStatus.value = failure.message ?: "Storage usage could not be loaded." }
        }
    }

    /** Deletes every uploaded file for this account. Chats keep the file names but can no longer open or re-read them. */
    fun deleteAllUploadedFiles() {
        if (_dataBusy.value || _isTyping.value) return
        val user = account.state.value.user ?: run { _dataStatus.value = "Sign in to delete uploaded files."; return }
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Deleting uploaded files…"
        viewModelScope.launch {
            try {
                val json = account.request("/api/files/delete-all", "POST", org.json.JSONObject(), authenticated = true)
                discardUploads()
                pendingDeletes.remove(pendingDeletes.forUser(user.id))
                _messages.value = _messages.value.map { if (it.files.isEmpty() && it.media.isEmpty()) it else it.copy(files = emptyList(), media = emptyList()) }
                _savedChats.value = _savedChats.value.map { chat -> if (chat.messages.none { it.files.isNotEmpty() || it.media.isNotEmpty() }) chat else chat.copy(messages = chat.messages.map { it.copy(files = emptyList(), media = emptyList()) }) }
                runCatching { mediaCache.clear() }
                saveHistory()
                _storageUsage.value = StorageUsage(0, 0, _storageUsage.value?.maxFileBytes ?: maxStoredBytes())
                _dataStatus.value = "${json.optInt("deleted")} uploaded and generated files deleted. Chats keep file names only."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _dataStatus.value = failure.message ?: "Uploaded files could not be deleted. Try again." }
            finally { _dataBusy.value = false }
        }
    }

    private fun applyTranscripts(transcripts: Map<String, String>) {
        _messages.value = _messages.value.map { message ->
            if (message.files.none { it.path in transcripts }) message
            else message.copy(files = message.files.map { ref -> transcripts[ref.path]?.let { ref.copy(transcript = it) } ?: ref })
        }
    }
    private fun historyBytes() = if (_integrations.value.filesEnabled) DuaApiClient.FILES_HISTORY_BYTES else DuaApiClient.MAX_HISTORY_BYTES

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

    fun selectAttachments(uris: List<Uri>) {
        if (_isTyping.value) { _attachmentError.value = "Reply complete hone ke baad files select karein."; return }
        viewModelScope.launch {
            val picked = try { withContext(Dispatchers.IO) { describe(uris) } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _attachmentError.value = "File read nahi hui. Dobara select karein."; return@launch }
            val inline = if (storedFilesAvailable()) uploadPicked(picked) else {
                val blocked = picked.filter { StoredFileTypes.needsStorage(it.name, it.type) || (it.size ?: 0) > AttachmentReader.MAX_BYTES }
                if (blocked.isNotEmpty()) _attachmentError.value = storedFilesHint()
                picked.filterNot { it in blocked }.map { it.uri }
            }
            if (inline.isNotEmpty()) selectInlineAttachments(inline)
        }
    }

    private fun selectInlineAttachments(uris: List<Uri>) = loadFiles {
        require(uris.size <= 3) { "Maximum 3 files select karein." }
        val resolver = getApplication<Application>().contentResolver
        val sources = uris.map { uri ->
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "Selected file"
            } ?: "Selected file"
            // Text/code files are recognised by MIME type or extension (many apps report octet-stream).
            val type = resolver.getType(uri).orEmpty()
            val input = resolver.openInputStream(uri) ?: error("File read nahi hui.")
            input.use { PhotoPolicy.readSource(name, type, it) }
        }
        PhotoAttachmentReader.prepare(sources)
    }

    fun selectCameraFile(file: File) {
        // Imagine edits need the photo in storage (the server reads sources from the owner's folder).
        if (_imagine.value && storedFilesAvailable() && !_isTyping.value) {
            val name = "Camera photo " + java.time.LocalTime.now().withNano(0).toString().take(5) + ".jpg"
            startUpload(name, "image/jpeg", file.length(), open = { file.inputStream() }, cleanup = { file.delete() })
            return
        }
        selectInlineCamera(file)
    }
    private fun selectInlineCamera(file: File) = loadFiles(cleanup = { file.delete() }) {
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
        _dataBusy.value = true; refreshSyncBusy()
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
        _dataBusy.value = true; refreshSyncBusy()
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
        _dataBusy.value = true; refreshSyncBusy()
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
        replyAudioCache.clear()
        if (_dataBusy.value) return
        _dataBusy.value = true; refreshSyncBusy()
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
                    deleteStoredPaths(pathsOf(_messages.value + _savedChats.value.flatMap { it.messages }))
                    pollJobs.values.forEach { it.cancel() }; pollJobs.clear()
                    runCatching { mediaCache.clear() }
                    activeId = nextId
                    historyReadable = true
                    _messages.value = emptyList()
                    _draft.value = ""
                    _savedChats.value = emptyList()
                    _summary.value = null
                    _webMode.value = null
                    _xSearch.value = false
                    replyXSearch.clear()
                    replyWebModes.clear()
                    replyReasoning.clear()
                    clearAttachments()
                    _dataStatus.value = "All local chats, drafts and pending files cleared. Their uploaded files are being deleted."
                } else _dataStatus.value = "Delete could not be saved. Chats are kept in memory; try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _dataStatus.value = "Local history could not be cleared. Try again." }
            finally { _dataBusy.value = false }
        }
    }

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()
    private var historyReadable = true
    private val automaticSync = AutomaticCloudSync(
        viewModelScope,
        object : AutomaticCloudSync.AccountPort {
            override fun currentAccountId() = account.state.value.user?.id
            override suspend fun load(): AutomaticSyncAccount {
                account.load()
                return AutomaticSyncAccount(account.state.value.configured, account.state.value.user?.id)
            }
            override suspend fun cloud(expectedAccountId: String): CloudReview {
                check(account.state.value.user?.id == expectedAccountId) { "Account changed. Review cloud sync again." }
                return account.cloud()
            }
            override suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long =
                account.upload(CloudReview(revision, snapshot, expectedAccountId), expectedAccountId = expectedAccountId)
        },
        object : AutomaticCloudSync.LocalPort {
            override fun read(): CloudSnapshot {
                check(!syncBlocked()) { "Sync paused during a reply or manual review." }
                check(historyReadable) { "Saved chats could not be read. Review Data controls before enabling sync." }
                localPreferences.requireReadable()
                return CloudSnapshot(_savedChats.value.toList(), localSettings.value.memory.toList())
            }
            // Called without suspension on the main dispatcher. Active edit buffers and attachments
            // remain authoritative; the controller keeps changed cloud chats as separate copies.
            override fun commit(snapshot: CloudSnapshot) {
                check(!syncBlocked()) { "Sync paused during a reply or manual review." }
                com.dua.ai.account.CloudSnapshotCodec.encode(snapshot)
                val settings = localSettings.value
                val previous = preferences.getString("history", null)
                // Summaries are local only: keep them for synced chats that still contain their boundary message.
                val summaries = _savedChats.value.associate { it.id to it.summary }
                val chats = snapshot.chats.map { chat -> chat.copy(summary = com.dua.ai.model.ContextWindow.validSummary(chat.messages, summaries[chat.id])) }
                val encoded = ChatHistoryCodec.encode(chats, activeId)
                localPreferences.update(settings.copy(memory = snapshot.memory))
                try {
                    if (!preferences.edit().putString("history", encoded).commit()) {
                        preferences.edit().putString("history", previous).commit()
                        error("Synced chats could not be saved. Local data was kept.")
                    }
                    _savedChats.value = chats
                } catch (failure: Exception) {
                    try { localPreferences.update(settings) }
                    catch (_: Exception) { throw IllegalStateException("Sync interrupted. Review local memories before retrying.", failure) }
                    throw failure
                }
            }
        },
        AutomationSyncStore(application)
    )
    val automaticSyncState = automaticSync.state
    fun enableAutomaticSync() { refreshSyncBusy(); automaticSync.enable() }
    fun disableAutomaticSync() = automaticSync.disable()
    fun resetAutomaticSync() = automaticSync.reset()
    fun retryAutomaticSync() { refreshSyncBusy(); automaticSync.retry() }
    fun resumeAutomaticSyncAfterReview() { refreshSyncBusy(); automaticSync.resumeAfterReview() }
    private fun syncBlocked() = account.state.value.busy || _dataBusy.value || _isTyping.value ||
        _cloudPreview.value != null || _importPreview.value != null
    private fun refreshSyncBusy() = automaticSync.setBusy(syncBlocked())
    private val connectivity = application.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkObserver = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { viewModelScope.launch { refreshSyncNetwork() } }
        override fun onLost(network: android.net.Network) { viewModelScope.launch { refreshSyncNetwork() } }
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
            viewModelScope.launch { refreshSyncNetwork() }
        }
    }
    private var networkObserverRegistered = false
    private fun refreshSyncNetwork() {
        val online = runCatching {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }.getOrDefault(false)
        _online.value = online
        automaticSync.setOnline(online)
    }

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
                    _summary.value = it.summary
                }
            } catch (_: Exception) {
                historyReadable = false
                preferences.edit().putString("unreadable_history_backup", raw).apply()
                _error.value = "Saved chats could not be loaded. Stored data has not been deleted."
            }
        }
        refreshSyncNetwork()
        networkObserverRegistered = runCatching { connectivity.registerDefaultNetworkCallback(networkObserver); true }.getOrDefault(false)
        viewModelScope.launch {
            combine(account.state, _dataBusy, _isTyping, _cloudPreview, _importPreview) { state, dataBusy, typing, cloud, imported ->
                state.busy || dataBusy || typing || cloud != null || imported != null
            }.distinctUntilChanged().collect { refreshSyncBusy() }
        }
        viewModelScope.launch { account.state.map { it.user?.id }.distinctUntilChanged().collect { automaticSync.accountChanged(); resumeImagineJobs() } }
        viewModelScope.launch {
            combine(_savedChats, localSettings) { chats, settings -> CloudSnapshot(chats, settings.memory) }
                .distinctUntilChanged().collect { automaticSync.localChanged() }
        }
        automaticSync.restore()
    }

    override fun onCleared() {
        recorder.cancel()
        pollJobs.values.forEach { it.cancel() }
        automaticSync.close()
        if (networkObserverRegistered) runCatching { connectivity.unregisterNetworkCallback(networkObserver) }
        super.onCleared()
    }

    private fun saveHistory() {
        val previous = _savedChats.value.find { it.id == activeId }
        val others = _savedChats.value.filterNot { it.id == activeId }
        _savedChats.value = if (_messages.value.isEmpty() && _draft.value.isBlank()) others else
            listOf(com.dua.ai.history.ChatSavePolicy.preserveUnchangedTime(previous,
                SavedChat(activeId, _selectedModel.value, _draft.value, _messages.value, System.currentTimeMillis(),
                    com.dua.ai.model.ContextWindow.validSummary(_messages.value, _summary.value)))) + others
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
        _summary.value = chat.summary
        _webMode.value = null
        _xSearch.value = false
        saveHistory()
    }

    fun deleteChat(id: String) {
        _savedChats.value.find { it.id == id }?.messages?.forEach { replyWebModes.remove(it.id); replyReasoning.remove(it.id) }
        // Deleting a chat deletes its uploaded files (unless another chat copy still uses them).
        val chatMessages = if (id == activeId) _messages.value else _savedChats.value.find { it.id == id }?.messages.orEmpty()
        val stillUsed = pathsOf(_savedChats.value.filterNot { it.id == id }.flatMap { it.messages } + if (id == activeId) emptyList() else _messages.value).toSet()
        val removed = pathsOf(chatMessages).filterNot { it in stillUsed }.distinct()
        deleteStoredPaths(removed)
        runCatching { mediaCache.forget(removed) }
        chatMessages.forEach { message -> pollJobs.remove(message.id)?.cancel() }
        if (id == activeId) {
            cancelReply()
            activeId = UUID.randomUUID().toString()
            _messages.value = emptyList()
            _draft.value = ""
            _summary.value = null
            _webMode.value = null
            _xSearch.value = false
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

    fun setModel(model: String) {
        if (_integrations.value.providers.none { it.name == model && it.configured }) return
        _selectedModel.value = model
        if (model != "Grok") _xSearch.value = false
        saveHistory()
    }

    /** Cancels the current request without removing the user's sent message. */
    fun stopReply() {
        if (!_isTyping.value) return
        val partial = _messages.value.any { it.streaming && it.text.isNotBlank() }
        cancelReply()
        _error.value = if (partial) "Response stopped. Partial reply kept." else "Response stopped."
        saveHistory()
    }

    fun startNewChat() {
        cancelReply()
        clearAttachments()
        activeId = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _draft.value = ""
        _summary.value = null
        _webMode.value = null
        _xSearch.value = false
        saveHistory()
    }

    fun retryResponse(messageId: String) {
        if (_isTyping.value || _readingFiles.value || _dataBusy.value) return
        val original = _messages.value.lastOrNull() ?: return
        if (original.id != messageId || original.isUser || original.state !in listOf("complete", "stopped") || original.isImagine) return
        val plan = try { com.dua.ai.model.ReplyRetryPlan.create(_messages.value, messageId) }
            catch (failure: IllegalArgumentException) { _error.value = failure.message; return }
        // A restored reply has no saved mode and retries with web off. In this session,
        // retries keep the original request's choice even if the composer has changed.
        val requestWebMode = replyWebModes[messageId]
        val requestReasoning = replyReasoning[messageId] ?: (original.thinking != null)
        val requestXSearch = (replyXSearch[messageId] ?: false) && xSearchUsable(plan.model)
        webModeError(requestWebMode, plan.model)?.let { _error.value = it; return }
        val request = requestTurns(plan.full, plan.context, inlineFiles = false)
        val currentGeneration = generation
        val startedAt = System.currentTimeMillis()
        _error.value = null; _isTyping.value = true; refreshSyncBusy()
        retryOriginal = original
        updateMessage(messageId) { it.copy(text = "", thinking = null, thinkingSeconds = null, notice = null, codeSnippet = null, state = ChatMessage.STREAMING, startedAt = startedAt) }
        replyJob = viewModelScope.launch {
            var accumulator: com.dua.ai.network.StreamAccumulator? = null
            try {
                account.withToken { token ->
                    val attempt = com.dua.ai.network.StreamAccumulator(startedAt).also { accumulator = it }
                    collectReply(messageId, attempt, api.streamReply(plan.model, request.turns, emptyList(), com.dua.ai.settings.PreferencesCodec.memoryText(localSettings.value), token, requestWebMode, requestReasoning,
                        request.historyBytes, request.context, requestXSearch), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    val final = accumulator!!.snapshot(ended = true)
                    retryOriginal = null
                    updateMessage(messageId) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, state = "complete") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    retryOriginal = null
                    if (partial != null && partial.text.isNotBlank()) {
                        updateMessage(messageId) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, state = "stopped") }
                        _error.value = "Retry interrupted: ${failureText(failure)} Partial reply kept; tap retry."
                    } else {
                        updateMessage(messageId) { original }
                        _error.value = "Retry failed. Original response kept."
                    }
                }
            } finally {
                if (generation == currentGeneration) { _isTyping.value = false; saveHistory() }
            }
        }
    }

    fun sendMessage(userText: String) {
        if (_imagine.value) { sendImagine(userText); return }
        val storedFiles = _uploads.value.filter { it.state == PendingUpload.DONE }.mapNotNull { it.ref }
        if ((userText.isBlank() && _attachments.value.isEmpty() && storedFiles.isEmpty()) || _isTyping.value || _readingFiles.value) return
        if (_uploads.value.any { it.uploading } || _recordingSince.value != null) { _error.value = "Wait for uploads to finish, or remove them."; return }
        _error.value = null
        if (userText.length > 8000) {
            _error.value = "Message 8000 characters se chhota rakhein."
            return
        }
        val requestWebMode = _webMode.value
        val requestReasoning = _reasoning.value
        val responseModel = _selectedModel.value
        val requestXSearch = _xSearch.value && xSearchUsable(responseModel)
        webModeError(requestWebMode, responseModel)?.let { _error.value = it; return }
        val provider = _integrations.value.providers.firstOrNull { it.name == responseModel && it.configured }
        if (provider == null) {
            _error.value = "This provider needs backend setup. Choose a configured model."
            return
        }
        val files = _attachments.value
        val convert = { file: com.dua.ai.attachments.ChatAttachment -> com.dua.ai.attachments.AttachmentConversion.needsJpeg(provider.attachmentTypes, file) }
        // WebP photos are converted to JPEG for providers that only take JPEG/PNG (Grok).
        val attachmentError = com.dua.ai.setup.IntegrationCapabilities.attachmentError(provider, files.map { if (convert(it)) "image/jpeg" else it.mimeType })
        if (attachmentError != null) {
            _error.value = attachmentError
            return
        }
        val prompt = userText.trim().ifBlank { "Describe these files." }
        val names = files.map { it.name } + storedFiles.map { it.name }
        val text = prompt + if (names.isEmpty()) "" else "\nFiles: " + names.joinToString(", ")
        val fileText = com.dua.ai.attachments.TextFiles.contextText(files, ChatMessage.MAX_ATTACHMENT_TEXT_CHARS)
        val userMessage = ChatMessage(text = text, isUser = true, model = responseModel, attachmentText = fileText, files = storedFiles)
        val sentPaths = storedFiles.map { it.path }.toSet()
        val startedAt = System.currentTimeMillis()
        val reply = ChatMessage(text = "", isUser = false, model = responseModel, state = ChatMessage.STREAMING, startedAt = startedAt)
        val request = requestTurns(_messages.value + userMessage, _messages.value.takeLast(38) + userMessage, inlineFiles = files.isNotEmpty())
        val currentGeneration = generation
        replyWebModes[reply.id] = requestWebMode
        replyXSearch[reply.id] = requestXSearch
        replyReasoning[reply.id] = requestReasoning
        _messages.value = _messages.value + userMessage + reply
        _draft.value = ""
        saveHistory()
        _isTyping.value = true; refreshSyncBusy()
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            var accumulator: com.dua.ai.network.StreamAccumulator? = null
            try {
                val prepared = if (files.any(convert)) withContext(Dispatchers.Default) { PhotoAttachmentReader.convertToJpeg(files, convert) } else files
                account.withToken { token ->
                    val attempt = com.dua.ai.network.StreamAccumulator(startedAt).also { accumulator = it }
                    collectReply(reply.id, attempt, api.streamReply(responseModel, request.turns, prepared,
                        com.dua.ai.settings.PreferencesCodec.memoryText(localSettings.value), token, requestWebMode, requestReasoning,
                        request.historyBytes, request.context, requestXSearch), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    _uploads.update { list -> list.filterNot { it.ref?.path in sentPaths } }
                    clearAttachments()
                    val final = accumulator!!.snapshot(ended = true)
                    updateMessage(reply.id) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, state = "complete") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    if (partial != null && partial.text.isNotBlank()) {
                        // The request reached the model: keep the turn and its partial reply; Retry regenerates it.
                        _uploads.update { list -> list.filterNot { it.ref?.path in sentPaths } }
                        clearAttachments()
                        updateMessage(reply.id) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, state = "stopped") }
                        _error.value = "Reply interrupted: ${failureText(failure)} Partial reply kept; tap retry."
                    } else {
                        _messages.value = _messages.value.filterNot { it.id == userMessage.id || it.id == reply.id }
                        replyWebModes.remove(reply.id); replyReasoning.remove(reply.id)
                        _draft.value = userText
                        _failedPrompt.value = userText
                        _error.value = failureText(failure)
                    }
                }
            } finally {
                if (generation == currentGeneration) {
                    _isTyping.value = false; saveHistory()
                    if (!foreground && _error.value == null && localSettings.value.notifications && _messages.value.lastOrNull()?.isUser == false)
                        com.dua.ai.settings.LocalReplyNotifications.show(getApplication(), activeId)
                }
            }
        }
    }

    // ---- Imagine ----

    private fun imagineConfig() = _integrations.value.imagine

    /** Imagine chip: turns the composer into image/video generation (Reasoning, Search and X off). */
    fun setImagine(enabled: Boolean) {
        if (_isTyping.value) return
        if (!enabled) { _imagine.value = false; return }
        val config = imagineConfig()
        when {
            config == null -> { _error.value = "Imagine needs the latest Dua backend. Deploy it, then refresh Feature setup."; return }
            !config.enabled -> { _error.value = config.imageReason.ifBlank { config.videoReason }.ifBlank { "Imagine is turned off on the server." }; return }
            account.state.value.user == null -> { _error.value = "Sign in from Profile to use Imagine. Results are saved to your private storage."; return }
        }
        val settings = localSettings.value
        val base = if (_imagine.value) _imagineOptions.value else _imagineOptions.value.copy(aspectRatio = settings.imagineAspect, provider = settings.imagineProvider.ifBlank { null })
        _imagineOptions.value = config!!.normalize(base)
        _reasoning.value = false; _webMode.value = null; _xSearch.value = false
        _imagine.value = true
        _error.value = null
    }

    fun setImagineOptions(options: com.dua.ai.imagine.ImagineOptions) {
        if (_isTyping.value) return
        val config = imagineConfig() ?: return
        if (options.kind == com.dua.ai.imagine.ImagineInfo.VIDEO && !config.videoEnabled) { _error.value = config.videoReason.ifBlank { "Video is turned off on the server." }; return }
        if (options.kind == com.dua.ai.imagine.ImagineInfo.IMAGE && !config.imageEnabled) { _error.value = config.imageReason.ifBlank { "Images are turned off on the server." }; return }
        _imagineOptions.value = config.normalize(options)
    }

    private fun imagineFailure(failure: Throwable): String = when (failure) {
        is com.dua.ai.account.AccountException -> failure.message ?: "Imagine request failed."
        is IllegalArgumentException, is IllegalStateException -> failure.message ?: "Imagine request failed."
        is java.net.SocketTimeoutException -> "Imagine took too long to answer. Check your daily usage in Settings before trying again."
        else -> "Connection fail hua. Dobara try karein."
    }

    private fun sendImagine(userText: String) {
        if (_isTyping.value || _readingFiles.value) return
        if (_uploads.value.any { it.uploading } || _recordingSince.value != null) { _error.value = "Wait for uploads to finish, or remove them."; return }
        val config = imagineConfig() ?: run { _error.value = "Imagine needs the latest Dua backend."; return }
        val options = config.normalize(_imagineOptions.value)
        val video = options.kind == com.dua.ai.imagine.ImagineInfo.VIDEO
        if (account.state.value.user == null) { _error.value = "Sign in from Profile to use Imagine."; return }
        if (video && !config.videoEnabled) { _error.value = config.videoReason.ifBlank { "Video is turned off on the server." }; return }
        if (!video && !config.imageEnabled) { _error.value = config.imageReason.ifBlank { "Images are turned off on the server." }; return }
        if (_attachments.value.isNotEmpty()) { _error.value = "Imagine uses photos from your storage. Remove these files and add photos again."; return }
        val done = _uploads.value.filter { it.state == PendingUpload.DONE && it.ref != null }
        val sources = done.mapNotNull { it.ref }
        if (sources.any { it.mimeType !in com.dua.ai.imagine.ImagineCodec.IMAGE_TYPES }) { _error.value = "Imagine works with JPEG, PNG or WebP photos. Remove other files."; return }
        if (video && sources.size > 1) { _error.value = "A video starts from one photo. Remove the others."; return }
        if (!video && sources.size > config.maxSourceImages) { _error.value = "Edit up to ${config.maxSourceImages} photos at a time."; return }
        sources.firstOrNull { it.size > config.maxSourceBytes }?.let { _error.value = "\"${it.name}\" is larger than ${com.dua.ai.files.StoredFileTypes.sizeLabel(config.maxSourceBytes)}, the Imagine source limit."; return }
        val prompt = userText.trim()
        if (prompt.length > com.dua.ai.imagine.ImagineCodec.MAX_PROMPT) { _error.value = "Keep the description under 4,000 characters."; return }
        if (prompt.isEmpty() && (!video || sources.isEmpty())) { _error.value = if (video) "Describe the video, or add a photo to animate." else "Describe the image to create or the edit to make."; return }
        _error.value = null
        val responseModel = _selectedModel.value
        val startedAt = System.currentTimeMillis()
        val info = com.dua.ai.imagine.ImagineInfo(kind = options.kind, prompt = prompt.ifEmpty { "Animate this photo" }, aspectRatio = options.aspectRatio,
            count = if (video) 1 else options.count, provider = if (video) "Grok Imagine" else config.providerName(options.provider),
            model = if (video) config.videoModel else null, seconds = if (video) options.duration else null, resolution = if (video) options.resolution else null,
            edit = sources.isNotEmpty(), status = com.dua.ai.imagine.ImagineInfo.PENDING, startedAt = startedAt)
        val userMessage = ChatMessage(text = info.prompt + if (sources.isEmpty()) "" else "\nFiles: " + sources.joinToString(", ") { it.name },
            isUser = true, model = responseModel, files = sources)
        val reply = ChatMessage(text = "", isUser = false, model = responseModel, state = ChatMessage.STREAMING, startedAt = startedAt, imagine = info)
        val sentIds = done.map { it.id }.toSet()
        val currentGeneration = generation
        _messages.value = _messages.value + userMessage + reply
        _draft.value = ""
        saveHistory()
        _isTyping.value = true; refreshSyncBusy()
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            try {
                if (video) {
                    val job = account.withToken { token ->
                        check(token != null) { "Sign in from Profile to use Imagine." }
                        imagineApi.video(prompt, options, sources.firstOrNull()?.path, token)
                    }
                    if (generation == currentGeneration) {
                        _uploads.update { list -> list.filterNot { it.id in sentIds } }
                        val next = info.copy(jobId = job.jobId, seconds = job.seconds, resolution = job.resolution, aspectRatio = job.aspectRatio,
                            model = job.model.ifBlank { info.model })
                        updateMessage(reply.id) { it.copy(state = "complete", imagine = next, text = com.dua.ai.imagine.ImagineCodec.replyText(next, emptyList())) }
                        pollVideo(reply.id, firstDelayMs = job.pollAfterSeconds * 1000L)
                    }
                } else {
                    val result = account.withToken { token ->
                        check(token != null) { "Sign in from Profile to use Imagine." }
                        imagineApi.image(prompt, options, sources.map { it.path }, token)
                    }
                    result.links.forEach { (path, url) -> mediaCache.remember(path, url) }
                    if (generation == currentGeneration) {
                        _uploads.update { list -> list.filterNot { it.id in sentIds } }
                        val next = info.copy(status = com.dua.ai.imagine.ImagineInfo.DONE, provider = config.providerName(result.provider), model = result.model, edit = result.edit, count = result.media.size)
                        updateMessage(reply.id) { it.copy(state = "complete", media = result.media, imagine = next, notice = result.notice,
                            text = com.dua.ai.imagine.ImagineCodec.replyText(next, result.media)) }
                    } else {
                        // Stopped after the server finished: keep nothing, but remove the stored results.
                        deleteStoredPaths(result.media.mapNotNull { it.path })
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == currentGeneration) {
                    _messages.value = _messages.value.filterNot { it.id == userMessage.id || it.id == reply.id }
                    _draft.value = userText
                    _failedPrompt.value = userText
                    _error.value = imagineFailure(failure)
                }
            } finally {
                if (generation == currentGeneration) {
                    _isTyping.value = false; saveHistory()
                    loadImagineUsage()
                    if (!video && !foreground && _error.value == null && localSettings.value.notifications)
                        com.dua.ai.settings.LocalReplyNotifications.show(getApplication(), activeId)
                }
            }
        }
    }

    /** Updates a message in the open chat or in any saved chat (video jobs finish wherever they are). */
    private fun updateAnyMessage(id: String, transform: (ChatMessage) -> ChatMessage): Boolean {
        if (_messages.value.any { it.id == id }) { updateMessage(id, transform); saveHistory(); return true }
        val chat = _savedChats.value.firstOrNull { saved -> saved.messages.any { it.id == id } } ?: return false
        _savedChats.value = _savedChats.value.map { if (it.id == chat.id) it.copy(messages = it.messages.map { message -> if (message.id == id) transform(message) else message }) else it }
        saveHistory()
        return true
    }
    private fun findMessage(id: String): ChatMessage? = _messages.value.firstOrNull { it.id == id }
        ?: _savedChats.value.firstNotNullOfOrNull { chat -> chat.messages.firstOrNull { it.id == id } }
    private fun chatIdOf(messageId: String): String? = if (_messages.value.any { it.id == messageId }) activeId
        else _savedChats.value.firstOrNull { chat -> chat.messages.any { it.id == messageId } }?.id

    private fun finishImagine(id: String, transform: (com.dua.ai.imagine.ImagineInfo) -> com.dua.ai.imagine.ImagineInfo) {
        updateAnyMessage(id) { message -> val info = message.imagine ?: return@updateAnyMessage message
            val next = transform(info); message.copy(imagine = next, text = com.dua.ai.imagine.ImagineCodec.replyText(next, message.media)) }
    }

    /** Polls a video job until it finishes, fails or is cancelled. Network failures back off and keep the job pending. */
    private fun pollVideo(messageId: String, firstDelayMs: Long = 0) {
        if (pollJobs[messageId]?.isActive == true) return
        pollJobs[messageId] = viewModelScope.launch {
            var failures = 0
            var wait = firstDelayMs
            try {
                while (true) {
                    if (wait > 0) delay(wait)
                    val info = findMessage(messageId)?.imagine ?: break
                    val jobId = info.jobId
                    if (!info.pending || jobId == null) break
                    if (com.dua.ai.imagine.ImagineCodec.stale(info, System.currentTimeMillis())) {
                        finishImagine(messageId, { it.copy(status = com.dua.ai.imagine.ImagineInfo.EXPIRED, jobId = null, error = "This video job expired before it finished.") }); break
                    }
                    val status = try {
                        account.withToken { token -> if (token == null) null else imagineApi.status(jobId, token) } ?: break // Signed out: resume after sign-in.
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: com.dua.ai.account.AccountException) {
                        if (failure.status in listOf(400, 403, 404)) {
                            finishImagine(messageId, { it.copy(status = com.dua.ai.imagine.ImagineInfo.FAILED, jobId = null, error = failure.message) }); break
                        }
                        failures++; wait = com.dua.ai.imagine.ImagineCodec.pollDelayMs(5, failures); continue
                    } catch (_: Exception) { failures++; wait = com.dua.ai.imagine.ImagineCodec.pollDelayMs(5, failures); continue }
                    failures = 0
                    when (status.status) {
                        com.dua.ai.imagine.ImagineInfo.PENDING -> {
                            if (status.progress != info.progress) updateAnyMessage(messageId) { it.copy(imagine = it.imagine?.copy(progress = status.progress)) }
                            wait = com.dua.ai.imagine.ImagineCodec.pollDelayMs(status.pollAfterSeconds, 0)
                        }
                        com.dua.ai.imagine.ImagineInfo.DONE -> {
                            val media = listOfNotNull(status.media)
                            status.media?.path?.let { path -> status.link?.let { mediaCache.remember(path, it) } }
                            updateAnyMessage(messageId) { message -> val next = (message.imagine ?: info).copy(status = com.dua.ai.imagine.ImagineInfo.DONE, progress = 100, jobId = null)
                                message.copy(media = media, imagine = next, notice = status.notice, text = com.dua.ai.imagine.ImagineCodec.replyText(next, media)) }
                            if (!foreground && localSettings.value.notifications) chatIdOf(messageId)?.let { com.dua.ai.settings.LocalReplyNotifications.show(getApplication(), it) }
                            break
                        }
                        else -> { finishImagine(messageId, { it.copy(status = status.status, jobId = null, error = status.error) }); break }
                    }
                }
            } finally { if (pollJobs[messageId] == coroutineContext[Job]) pollJobs.remove(messageId) }
        }
    }

    /** Starts polling every pending video job found in saved chats (after a restart or sign-in). */
    fun resumeImagineJobs() {
        if (account.state.value.user == null) return
        (_messages.value + _savedChats.value.flatMap { it.messages }).distinctBy { it.id }
            .filter { it.imagine?.pending == true && it.imagine.jobId != null }
            .forEach { pollVideo(it.id) }
    }

    /** Stops waiting for a video. xAI has no cancel call, so the job may still finish (and be billed); it will not be saved. */
    fun cancelVideo(messageId: String) {
        pollJobs.remove(messageId)?.cancel()
        finishImagine(messageId, { it.copy(status = com.dua.ai.imagine.ImagineInfo.CANCELLED, jobId = null, error = "Cancelled. The provider may still finish the job and count it; it will not be saved.") })
    }

    /** "Edit" / "Animate": puts a generated image into the composer as the Imagine source. */
    fun useGeneratedImage(media: com.dua.ai.imagine.GeneratedMedia, kind: String = com.dua.ai.imagine.ImagineInfo.IMAGE) {
        if (_isTyping.value) return
        val path = media.path ?: return
        if (media.isVideo) return
        setImagine(true)
        if (!_imagine.value) return
        setImagineOptions(_imagineOptions.value.copy(kind = kind))
        if (_uploads.value.any { it.ref?.path == path }) return
        val name = "Generated image." + com.dua.ai.imagine.MediaExport.extension(media.mimeType)
        if (kind == com.dua.ai.imagine.ImagineInfo.VIDEO) discardUploads()
        _uploads.value = _uploads.value + PendingUpload(UUID.randomUUID().toString(), name, media.mimeType, media.size, sent = media.size,
            state = PendingUpload.DONE, ref = FileRef(path, name, media.mimeType, media.size), shared = true)
    }

    fun loadImagineUsage() {
        if (account.state.value.user == null || imagineConfig() == null) { _imagineUsage.value = null; return }
        viewModelScope.launch {
            try { _imagineUsage.value = account.withToken { token -> token?.let { imagineApi.usage(it) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }

    /** Short-lived signed link for a stored path (owner checked on the server). */
    suspend fun storedFileUrl(path: String): String {
        val json = account.request("/api/files/url", "POST", org.json.JSONObject().put("path", path), authenticated = true)
        return com.dua.ai.setup.IntegrationCapabilities.safeUrl(json.optString("url")) ?: error("File link is invalid. Try again.")
    }

    private companion object {
        const val STREAM_FRAME_MS = 33L
        const val EMPTY_REPLY = "Model ne text reply nahi diya. Dobara try karein."
        const val MAX_UPLOADS = 10
    }
}
