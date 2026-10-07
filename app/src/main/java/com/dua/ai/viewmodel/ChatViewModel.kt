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
import kotlinx.coroutines.launch
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudReview
import com.dua.ai.setup.AutomaticCloudSync
import com.dua.ai.setup.AutomaticSyncAccount
import com.dua.ai.setup.AutomationSyncStore

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    val account = com.dua.ai.account.AccountRepository(application)
    private val setupApi = com.dua.ai.account.AccountApi()
    private val generatedAudioApi = com.dua.ai.voice.GeneratedAudioApi()
    private val _online = MutableStateFlow(false)
    val online = _online.asStateFlow()
    suspend fun generateReplyAudio(text: String): ByteArray {
        check(_online.value && _integrations.value.generatedVoiceConfigured) { "AI voice setup is unavailable. Try device voice." }
        var received: ByteArray? = null
        try {
            return account.withToken { token ->
                check(token != null) { "Sign in before using AI voice." }
                generatedAudioApi.generate(text, token).also { received = it }
            }
        } catch (failure: Exception) { received?.fill(0); throw failure }
    }
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
        _integrations.value = next.copy(busy = true, notice = "Setup status refreshed.",
            webSearchConfigured = _integrations.value.webSearchConfigured, pageReadingAvailable = _integrations.value.pageReadingAvailable)
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
    fun loadAccount() = accountAction { account.load(); account.loadFeatures() }
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

    // Web requests require a choice in this session; history and cloud snapshots never save it.
    private val _webMode = MutableStateFlow<String?>(null)
    val webMode = _webMode.asStateFlow()
    private val replyWebModes = mutableMapOf<String, String?>()
    fun replyWebMode(messageId: String): String? = replyWebModes[messageId]
    fun setWebMode(mode: String?) {
        if (_isTyping.value || mode !in listOf(null, "search", "page")) return
        webModeError(mode)?.let { _error.value = it; return }
        _webMode.value = mode
        _error.value = null
    }
    private fun webModeError(mode: String?): String? = when {
        mode == "search" && !_integrations.value.webSearchConfigured -> "Web search needs setup. Open Feature setup to configure search, or choose Off or Read link."
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
        val sources = uris.map { uri ->
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "Selected file"
            } ?: "Selected file"
            val type = resolver.getType(uri) ?: if (name.endsWith(".txt", true)) "text/plain" else ""
            val input = resolver.openInputStream(uri) ?: error("File read nahi hui.")
            input.use { PhotoPolicy.readSource(name, type, it) }
        }
        PhotoAttachmentReader.prepare(sources)
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
                    activeId = nextId
                    historyReadable = true
                    _messages.value = emptyList()
                    _draft.value = ""
                    _savedChats.value = emptyList()
                    _webMode.value = null
                    replyWebModes.clear()
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
                val encoded = ChatHistoryCodec.encode(snapshot.chats, activeId)
                localPreferences.update(settings.copy(memory = snapshot.memory))
                try {
                    if (!preferences.edit().putString("history", encoded).commit()) {
                        preferences.edit().putString("history", previous).commit()
                        error("Synced chats could not be saved. Local data was kept.")
                    }
                    _savedChats.value = snapshot.chats
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
        viewModelScope.launch { account.state.map { it.user?.id }.distinctUntilChanged().collect { automaticSync.accountChanged() } }
        viewModelScope.launch {
            combine(_savedChats, localSettings) { chats, settings -> CloudSnapshot(chats, settings.memory) }
                .distinctUntilChanged().collect { automaticSync.localChanged() }
        }
        automaticSync.restore()
    }

    override fun onCleared() {
        automaticSync.close()
        if (networkObserverRegistered) runCatching { connectivity.unregisterNetworkCallback(networkObserver) }
        super.onCleared()
    }

    private fun saveHistory() {
        val previous = _savedChats.value.find { it.id == activeId }
        val others = _savedChats.value.filterNot { it.id == activeId }
        _savedChats.value = if (_messages.value.isEmpty() && _draft.value.isBlank()) others else
            listOf(com.dua.ai.history.ChatSavePolicy.preserveUnchangedTime(previous,
                SavedChat(activeId, _selectedModel.value, _draft.value, _messages.value, System.currentTimeMillis()))) + others
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
        _webMode.value = null
        saveHistory()
    }

    fun deleteChat(id: String) {
        _savedChats.value.find { it.id == id }?.messages?.forEach { replyWebModes.remove(it.id) }
        if (id == activeId) {
            cancelReply()
            activeId = UUID.randomUUID().toString()
            _messages.value = emptyList()
            _draft.value = ""
            _webMode.value = null
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
        _webMode.value = null
        saveHistory()
    }

    fun retryResponse(messageId: String) {
        if (_isTyping.value || _readingFiles.value || _dataBusy.value) return
        val original = _messages.value.lastOrNull() ?: return
        if (original.id != messageId || original.isUser || original.state != "complete") return
        val plan = try { com.dua.ai.model.ReplyRetryPlan.create(_messages.value, messageId) }
            catch (failure: IllegalArgumentException) { _error.value = failure.message; return }
        // A restored reply has no saved mode and retries with web off. In this session,
        // retries keep the original request's choice even if the composer has changed.
        val requestWebMode = replyWebModes[messageId]
        webModeError(requestWebMode)?.let { _error.value = it; return }
        val currentGeneration = generation
        _error.value = null; _isTyping.value = true; refreshSyncBusy()
        replyJob = viewModelScope.launch {
            try {
                val reply = account.withToken { token -> api.reply(plan.model, plan.context, emptyList(), com.dua.ai.settings.PreferencesCodec.memoryText(localSettings.value), token, requestWebMode) }
                if (generation == currentGeneration) {
                    _messages.value = _messages.value.map { if (it.id == messageId) it.copy(text = reply, codeSnippet = null) else it }
                    saveHistory()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == currentGeneration) _error.value = "Retry failed. Original response kept."
            } finally {
                if (generation == currentGeneration) { _isTyping.value = false; saveHistory() }
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
        val requestWebMode = _webMode.value
        webModeError(requestWebMode)?.let { _error.value = it; return }
        val responseModel = _selectedModel.value
        val provider = _integrations.value.providers.firstOrNull { it.name == responseModel && it.configured }
        if (provider == null) {
            _error.value = "This provider needs backend setup. Choose a configured model."
            return
        }
        val files = _attachments.value
        val attachmentError = com.dua.ai.setup.IntegrationCapabilities.attachmentError(provider, files.map { it.mimeType })
        if (attachmentError != null) {
            _error.value = attachmentError
            return
        }
        val prompt = userText.trim().ifBlank { "Describe these files." }
        val text = prompt + if (files.isEmpty()) "" else "\nFiles: " + files.joinToString(", ") { it.name }
        val userMessage = ChatMessage(text = text, isUser = true, model = responseModel)
        val requestHistory = _messages.value.takeLast(38) + userMessage
        val currentGeneration = generation
        _messages.value = _messages.value + userMessage
        _draft.value = ""
        saveHistory()
        _isTyping.value = true; refreshSyncBusy()
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            try {
                val reply = account.withToken { token -> api.reply(responseModel, requestHistory, files, com.dua.ai.settings.PreferencesCodec.memoryText(localSettings.value), token, requestWebMode) }
                if (generation == currentGeneration) {
                    clearAttachments()
                    val response = ChatMessage(
                        text = reply, isUser = false, model = responseModel
                    )
                    replyWebModes[response.id] = requestWebMode
                    _messages.value = _messages.value + response
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
                if (generation == currentGeneration) {
                    _isTyping.value = false; saveHistory()
                    if (!foreground && _error.value == null && localSettings.value.notifications && _messages.value.lastOrNull()?.isUser == false)
                        com.dua.ai.settings.LocalReplyNotifications.show(getApplication(), activeId)
                }
            }
        }
    }
}
