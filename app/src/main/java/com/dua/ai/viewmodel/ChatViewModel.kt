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
import com.dua.ai.model.PromptSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.DuaApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudReview
import com.dua.ai.setup.AutomaticCloudSync
import com.dua.ai.setup.AutomaticSyncAccount
import com.dua.ai.setup.AutomationMergePlan
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
    // Declared first: init-time collectors (e.g. resumeImagineJobs) may reach the Imagine feature during construction.
    private val accountFeature: ChatAccount by lazy { ChatAccount(accountHost) }
    private val accountHost: AccountHost by lazy { object : AccountHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override val account get() = this@ChatViewModel.account
        override val automaticSync get() = this@ChatViewModel.automaticSync
        override val localSettings get() = this@ChatViewModel.localSettings
        override val localPreferences get() = this@ChatViewModel.localPreferences
        override val preferences get() = this@ChatViewModel.preferences
        override val historyWriter get() = this@ChatViewModel.historyWriter
        override val draftSaver get() = this@ChatViewModel.draftSaver
        override val activeId get() = this@ChatViewModel.activeId
        override var emailAuthJob: Job?
            get() = this@ChatViewModel.emailAuthJob
            set(value) { this@ChatViewModel.emailAuthJob = value }
        override val _cloudPreview get() = this@ChatViewModel._cloudPreview
        override val _googleSignIn get() = this@ChatViewModel._googleSignIn
        override val _emailAuth get() = this@ChatViewModel._emailAuth
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _buildPr get() = this@ChatViewModel._buildPr
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _webMode get() = this@ChatViewModel._webMode
        override val _xSearch get() = this@ChatViewModel._xSearch
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun syncBlocked() = this@ChatViewModel.syncBlocked()
        override fun liveProjectBusy() = this@ChatViewModel.liveProjectBusy()
        override fun flushHistory() = this@ChatViewModel.flushHistory()
        override fun currentDeletionIds() = this@ChatViewModel.currentDeletionIds()
        override fun bindCloudHistory(chats: List<SavedChat>, deleted: List<String>) = this@ChatViewModel.bindCloudHistory(chats, deleted)
        override fun googleSignInFailed(message: String?) = this@ChatViewModel.googleSignInFailed(message)
    } }
    private val dataFeature: ChatDataControls by lazy { ChatDataControls(dataHost) }
    private val dataHost: DataControlsHost by lazy { object : DataControlsHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val localSettings get() = this@ChatViewModel.localSettings
        override val localPreferences get() = this@ChatViewModel.localPreferences
        override val driveBackups get() = this@ChatViewModel.driveBackups
        override val fullBackup get() = this@ChatViewModel.fullBackup
        override val preferences get() = this@ChatViewModel.preferences
        override val historyWriter get() = this@ChatViewModel.historyWriter
        override val draftSaver get() = this@ChatViewModel.draftSaver
        override val activeId get() = this@ChatViewModel.activeId
        override var driveRestoreJob: Job?
            get() = this@ChatViewModel.driveRestoreJob
            set(value) { this@ChatViewModel.driveRestoreJob = value }
        override var importProjects: List<com.dua.ai.projects.ChatProject>
            get() = this@ChatViewModel.importProjects
            set(value) { this@ChatViewModel.importProjects = value }
        override val _driveRestoreBusy get() = this@ChatViewModel._driveRestoreBusy
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _dataStatus get() = this@ChatViewModel._dataStatus
        override val _importPreview get() = this@ChatViewModel._importPreview
        override val _buildPr get() = this@ChatViewModel._buildPr
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _isTyping get() = this@ChatViewModel._isTyping
        override fun syncBlocked() = this@ChatViewModel.syncBlocked()
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun liveProjectBusy() = this@ChatViewModel.liveProjectBusy()
        override fun cancelReply() = this@ChatViewModel.cancelReply()
        override fun flushHistory() = this@ChatViewModel.flushHistory()
        override fun saveHistory() = this@ChatViewModel.saveHistory()
    } }
    private val projectsFeature: ChatProjects by lazy { ChatProjects(projectsHost) }
    private val projectsHost: ProjectsHost by lazy { object : ProjectsHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val account get() = this@ChatViewModel.account
        override val localSettings get() = this@ChatViewModel.localSettings
        override val localPreferences get() = this@ChatViewModel.localPreferences
        override val fileClient get() = this@ChatViewModel.fileClient
        override val separateChatEngine get() = this@ChatViewModel.separateChatEngine
        override val sourceRequests get() = this@ChatViewModel.sourceRequests
        override val _projectSourceUploads get() = this@ChatViewModel._projectSourceUploads
        override val _projectId get() = this@ChatViewModel._projectId
        override val _projectNotice get() = this@ChatViewModel._projectNotice
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _integrations get() = this@ChatViewModel._integrations
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _uploads get() = this@ChatViewModel._uploads
        override val _attachments get() = this@ChatViewModel._attachments
        override val _recordingSince get() = this@ChatViewModel._recordingSince
        override fun projectMemory() = this@ChatViewModel.projectMemory()
        override fun syncBlocked() = this@ChatViewModel.syncBlocked()
        override fun startNewChat() = this@ChatViewModel.startNewChat()
        override fun selectAuto() = this@ChatViewModel.selectAuto()
        override fun maxStoredBytes() = this@ChatViewModel.maxStoredBytes()
        override fun copyCapped(uri: Uri, limit: Long) = this@ChatViewModel.copyCapped(uri, limit)
        override fun deleteStoredPaths(paths: Collection<String>) = this@ChatViewModel.deleteStoredPaths(paths)
        override suspend fun storedFileUrl(ref: FileRef) = this@ChatViewModel.storedFileUrl(ref)
    } }
    private val imagineFeature: ChatImagine by lazy { ChatImagine(imagineHost) }
    private val imagineHost: ImagineHost by lazy { object : ImagineHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val account get() = this@ChatViewModel.account
        override val localSettings get() = this@ChatViewModel.localSettings
        override val mediaCache get() = this@ChatViewModel.mediaCache
        override val imagineApi get() = this@ChatViewModel.imagineApi
        override val cloudImagineApi get() = this@ChatViewModel.cloudImagineApi
        override val pollJobs get() = this@ChatViewModel.pollJobs
        override val foreground get() = this@ChatViewModel.foreground
        override val activeId get() = this@ChatViewModel.activeId
        override val generation get() = this@ChatViewModel.generation
        override var replyJob: Job?
            get() = this@ChatViewModel.replyJob
            set(value) { this@ChatViewModel.replyJob = value }
        override val _integrations get() = this@ChatViewModel._integrations
        override val _cloudImagine get() = this@ChatViewModel._cloudImagine
        override val _switches get() = this@ChatViewModel._switches
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _error get() = this@ChatViewModel._error
        override val _failedPrompt get() = this@ChatViewModel._failedPrompt
        override val _imagine get() = this@ChatViewModel._imagine
        override val _imagineOptions get() = this@ChatViewModel._imagineOptions
        override val _imagineUsage get() = this@ChatViewModel._imagineUsage
        override val _agent get() = this@ChatViewModel._agent
        override val _reasoning get() = this@ChatViewModel._reasoning
        override val _webMode get() = this@ChatViewModel._webMode
        override val _xSearch get() = this@ChatViewModel._xSearch
        override val _runCode get() = this@ChatViewModel._runCode
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _preparingFiles get() = this@ChatViewModel._preparingFiles
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _recordingSince get() = this@ChatViewModel._recordingSince
        override val _editingId get() = this@ChatViewModel._editingId
        override val _draft get() = this@ChatViewModel._draft
        override val _attachments get() = this@ChatViewModel._attachments
        override val _uploads get() = this@ChatViewModel._uploads
        override val _messages get() = this@ChatViewModel._messages
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _selectedModel get() = this@ChatViewModel._selectedModel
        override fun refreshCloudImagine() = this@ChatViewModel.refreshCloudImagine()
        override fun leaveBuild() = this@ChatViewModel.leaveBuild()
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) = this@ChatViewModel.updateMessage(id, transform)
        override fun deleteStoredPaths(paths: Collection<String>) = this@ChatViewModel.deleteStoredPaths(paths)
        override fun discardUploads() = this@ChatViewModel.discardUploads()
        override fun startNewChat() = this@ChatViewModel.startNewChat()
        override fun sendMessage(userText: String) = this@ChatViewModel.sendMessage(userText)
    } }
    val driveBackups = com.dua.ai.backup.drive.AutomaticDriveBackups.get(application)
    val account = driveBackups.account
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

    // ---- Live voice (Phase 4B): xAI realtime session with a server-minted token ----
    /** Before the session connects: busy while asking the server, or why live voice cannot start. */
    data class LiveStartState(val busy: Boolean = false, val error: String = "", val fallback: Boolean = false)
    private val _liveStart = MutableStateFlow(LiveStartState())
    val liveStart = _liveStart.asStateFlow()
    private val _liveUsage = MutableStateFlow<com.dua.ai.voice.live.LiveUsage?>(null)
    val liveUsage = _liveUsage.asStateFlow()
    /** Live voice is offered by this backend and the owner is signed in. */
    fun liveVoiceAvailable(): Boolean = _integrations.value.voice?.live?.enabled == true && account.state.value.user != null &&
        _projectId.value == com.dua.ai.projects.ProjectCodec.PERSONAL && localSettings.value.projects.find { it.id == _projectId.value }?.let { !it.archived && it.instructions.isBlank() } == true
    fun liveOptions(): com.dua.ai.voice.live.LiveOptions? = _integrations.value.voice?.live?.takeIf { it.enabled }?.let { config ->
        val settings = localSettings.value
        com.dua.ai.voice.live.LiveVoiceCodec.options(config, settings.liveVoice, settings.replyLanguage, settings.voiceSpeed)
    }
    fun loadLiveUsage() = viewModelScope.launch {
        if (!liveVoiceAvailable()) { _liveUsage.value = null; return@launch }
        try { _liveUsage.value = com.dua.ai.voice.live.LiveVoiceCodec.usage(account.request("/api/voice/usage", authenticated = true)) }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {}
    }
    fun clearLiveError() { _liveStart.value = LiveStartState() }
    fun startLiveVoice() {
        if (_liveStart.value.busy || com.dua.ai.voice.live.LiveVoiceController.active) return
        val options = liveOptions()
        val reason = when {
            _switches.value.voice -> com.dua.ai.setup.RemoteSwitches.VOICE_PAUSED
            projectUnavailable() != null -> projectUnavailable()
            _projectId.value != com.dua.ai.projects.ProjectCodec.PERSONAL || localSettings.value.projects.find { it.id == _projectId.value }?.instructions?.isNotBlank() == true -> "Use Voice mode for this project so its instructions apply. Live voice is available in Personal without custom instructions."
            options == null -> _integrations.value.voice?.live?.reason?.ifBlank { null } ?: "Live voice needs the latest Dua backend. Voice mode works now."
            account.state.value.user == null -> "Sign in from Profile to use live voice."
            !_online.value -> "You're offline. Connect to the internet to start live voice."
            _isTyping.value -> "Wait for the current reply to finish, or stop it."
            else -> null
        }
        if (reason != null) { _liveStart.value = LiveStartState(error = reason, fallback = true); return }
        _liveStart.value = LiveStartState(busy = true)
        val chatId = activeId
        viewModelScope.launch {
            try {
                val body = com.dua.ai.voice.live.LiveVoiceCodec.sessionRequest(options!!, projectMemory(), _messages.value)
                val grant = com.dua.ai.voice.live.LiveVoiceCodec.grant(account.request("/api/voice/session", "POST", body, authenticated = true))
                _liveUsage.value = grant.usage
                _liveStart.value = LiveStartState()
                com.dua.ai.voice.live.LiveVoiceController.begin(getApplication(), grant, options.voice) { end -> finishLiveVoice(chatId, end) }
            } catch (cancelled: CancellationException) { _liveStart.value = LiveStartState(); throw cancelled }
            catch (failure: com.dua.ai.account.AccountException) {
                _liveStart.value = LiveStartState(error = failure.message ?: "Live voice could not start.", fallback = failure.status in setOf(429, 503))
            } catch (failure: Exception) { _liveStart.value = LiveStartState(error = failure.message ?: "Live voice could not start.", fallback = true) }
        }
    }
    /** Saves the spoken conversation into the chat it started from, then settles the minutes on the server. */
    private suspend fun finishLiveVoice(chatId: String, end: com.dua.ai.voice.live.LiveEnd) {
        val messages = com.dua.ai.voice.live.LiveVoiceCodec.messages(end.captions)
        if (messages.isNotEmpty()) {
            if (chatId == activeId) { _messages.value = _messages.value + messages; saveHistory() }
            else _savedChats.value.find { it.id == chatId }?.let { chat ->
                _savedChats.value = _savedChats.value.map { if (it.id == chatId) it.copy(messages = it.messages + messages, updatedAt = System.currentTimeMillis()) else it }
                historyWriter.save(_savedChats.value, activeId)
            } ?: run { _messages.value = _messages.value + messages; saveHistory() }
        }
        try {
            val settled = account.request("/api/voice/session-end", "POST", org.json.JSONObject().put("sessionId", end.sessionId), authenticated = true)
            _liveUsage.value = _liveUsage.value?.copy(usedSeconds = settled.optInt("usedSeconds"), activeSeconds = 0)
        } catch (_: Exception) {
            // The server charges an unfinished session its reservation once it is overdue; nothing else to do.
        }
        loadLiveUsage()
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
        if (_projectId.value != com.dua.ai.projects.ProjectCodec.PERSONAL) {
            _memoryCandidates.value = emptyList()
            integrationNotice("Personal memory suggestions are available in Personal chats. Use this project's instructions for its context.")
            return
        }
        val existing = localSettings.value.memory
        _memoryCandidates.value = _messages.value.filter { it.isUser && it.state == "complete" }.takeLast(20)
            .flatMap { com.dua.ai.setup.AutomationMemoryCandidates.propose(it.text, existing) }.distinct().take(4)
        integrationNotice(if (_memoryCandidates.value.isEmpty()) "No explicit memory requests found. Try saying ‘remember that I prefer Hindi’." else "Review each proposed note before saving. Nothing has been sent or saved automatically.")
    }
    fun acceptMemoryNote(note: String) {
        if (_projectId.value != com.dua.ai.projects.ProjectCodec.PERSONAL) return
        if (note !in _memoryCandidates.value) return
        try {
            val settings = localSettings.value
            val notes = com.dua.ai.setup.AutomationMemoryCandidates.confirm(note, settings.memory)
            localPreferences.update(settings.copy(memory = notes))
            _memoryCandidates.value = _memoryCandidates.value.filterNot { it == note }
            integrationNotice("Note saved and cached on this phone; the server copy wins on sync. Notes are sent with chat only when Memory is enabled.")
        } catch (failure: Exception) { integrationNotice(failure.message ?: "Memory could not be saved.") }
    }
    /** Dua Agent `remember`: queued as a reviewable suggestion (Personal chats only); nothing is saved without a tap. */
    private fun suggestAgentMemory(fact: String) {
        if (_projectId.value != com.dua.ai.projects.ProjectCodec.PERSONAL) return
        if (fact in localSettings.value.memory) return
        _memoryCandidates.value = (_memoryCandidates.value + fact).distinct().takeLast(4)
    }
    fun dismissMemoryNote(note: String) { _memoryCandidates.value = _memoryCandidates.value.filterNot { it == note } }
    /** Share target writes only after explicit review, using the existing personal-memory cache/sync protocol. */
    fun confirmSharedMemory(note: String, expectedOwnerId: String): String? = try {
        check(account.state.value.user?.id == expectedOwnerId && expectedOwnerId.isNotBlank()) { "Sign in to the account shown before saving memory." }
        check(!syncBlocked()) { "Finish the current reply or sync action, then save this note." }
        localPreferences.requireReadable()
        val settings = localSettings.value
        val notes = com.dua.ai.setup.AutomationMemoryCandidates.confirm(note, settings.memory)
        localPreferences.update(settings.copy(memory = notes))
        null
    } catch (failure: Exception) { failure.message ?: "Memory could not be saved. Try again." }
    fun integrationNotice(text: String) { _integrations.value = _integrations.value.copy(notice = text) }
    // Remote kill switches (/api/config › switches). Cached for the first frame; any failure falls back to "all off".
    private val switchPrefs = application.getSharedPreferences("dua_remote_switches", 0)
    private val _switches = MutableStateFlow(com.dua.ai.setup.RemoteSwitches.fromCache(
        switchPrefs.getString("value", null), switchPrefs.getLong("savedAt", 0L), System.currentTimeMillis()))
    val switches: StateFlow<com.dua.ai.setup.RemoteSwitches> = _switches.asStateFlow()
    private val _switchesChecking = MutableStateFlow(false)
    val switchesChecking: StateFlow<Boolean> = _switchesChecking.asStateFlow()
    private var switchesCheckedAt = 0L
    private fun applySwitches(next: com.dua.ai.setup.RemoteSwitches) {
        _switches.value = next
        switchPrefs.edit().putString("value", next.toJson()).putLong("savedAt", System.currentTimeMillis()).apply()
        if (next.agent && _agent.value && !_isTyping.value) _agent.value = false
        if (next.agent) _agentAvailable.value = false
        if (next.imagine && _imagine.value && !_isTyping.value) _imagine.value = false
    }
    /** Start and resume: re-reads /api/config (5 s timeout, at most once a minute unless [force]). */
    fun refreshRemoteSwitches(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_switchesChecking.value || (!force && now - switchesCheckedAt < 60_000L)) return
        switchesCheckedAt = now
        _switchesChecking.value = true
        viewModelScope.launch {
            try {
                val config = kotlinx.coroutines.withTimeout(5_000L) { setupApi.request("/api/config") }
                applySwitches(com.dua.ai.setup.RemoteSwitches.fromConfig(config))
            } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) { failOpenSwitches() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failOpenSwitches() }
            finally { _switchesChecking.value = false }
        }
    }
    /** Config unreachable: never trap the user on the paused screen or hide features because of a stale value. */
    private fun failOpenSwitches() { _switches.value = com.dua.ai.setup.RemoteSwitches() }
    fun loadProviderConfiguration() = viewModelScope.launch {
        refreshEngineCapabilities(force = true)
        try {
            val config = setupApi.request("/api/config")
            applySwitches(com.dua.ai.setup.RemoteSwitches.fromConfig(config))
            _integrations.value = api.chatConfiguration(com.dua.ai.setup.IntegrationCapabilities.configuration(config, _integrations.value))
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
        refreshEngineCapabilities()
        val next = com.dua.ai.setup.IntegrationCapabilities.decode(setupApi.request("/api/setup"))
        val current = _integrations.value
        // /api/setup has no per-feature config; keep what /api/config reported.
        _integrations.value = api.chatConfiguration(next.copy(busy = true, notice = "Setup status refreshed.",
            webSearchConfigured = current.webSearchConfigured, pageReadingAvailable = current.pageReadingAvailable, projectsEnabled = current.projectsEnabled, projectTasksEnabled = current.projectTasksEnabled, taskDueDatesEnabled = current.taskDueDatesEnabled, projectMemoryEnabled = current.projectMemoryEnabled,
            chatRenameEnabled = current.chatRenameEnabled, chatDeletionEnabled = current.chatDeletionEnabled, chatPinEnabled = current.chatPinEnabled,
            personalityEnabled = current.personalityEnabled,
            filesEnabled = current.filesEnabled, filesRestoreEnabled = current.filesRestoreEnabled, filesMaxBytes = current.filesMaxBytes, nativeSearch = current.nativeSearch,
            xSearchAvailable = current.xSearchAvailable, longContext = current.longContext, imagine = current.imagine, codeExecution = current.codeExecution, build = current.build,
            voice = current.voice, generatedVoiceConfigured = current.voice?.ttsEnabled ?: next.generatedVoiceConfigured))
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
    fun accountAction(action: suspend () -> Unit) = accountFeature.accountAction(action)
    fun loadAccount() = accountAction { account.load(); account.loadFeatures(); flushPendingFileDeletes() }
    /** Google sign-in progress for the welcome screen and Profile: busy while the account picker or token exchange runs, plus the last error. */
    data class GoogleSignInState(val busy: Boolean = false, val error: String? = null)
    private val _googleSignIn = MutableStateFlow(GoogleSignInState())
    val googleSignIn = _googleSignIn.asStateFlow()
    private val onboarding = com.dua.ai.settings.OnboardingStore(application)
    /** The welcome screen shows at launch only while signed out and not skipped. */
    fun shouldShowOnboarding(): Boolean = account.state.value.user == null && !onboarding.skipped
    fun skipOnboarding() { onboarding.skipped = true }
    /** The account picker needs an Activity, so the UI runs it ([com.dua.ai.auth.GoogleSignIn]) and reports here. */
    fun googleSignInStarted() { _googleSignIn.value = GoogleSignInState(busy = true) }
    fun googleSignInFailed(message: String?) { _googleSignIn.value = GoogleSignInState(error = message) }
    fun signInWithGoogle(idToken: String, nonce: String, photoUrl: String = "", expectedEmail: String? = null) =
        accountFeature.signInWithGoogle(idToken, nonce, photoUrl, expectedEmail)
    /** Email-only navigation prefill. No password and no disk persistence; generic email entry clears it. */
    var emailSignInHint: String = ""
        private set
    fun prepareEmailSignIn(email: String = "") {
        emailSignInHint = email.trim().takeIf { com.dua.ai.account.EmailCredentials.validEmail(it) }.orEmpty()
    }
    data class EmailAuthState(val busy: Boolean = false, val error: String? = null, val confirmation: String = "", val completed: Boolean = false)
    private val _emailAuth = MutableStateFlow(EmailAuthState())
    val emailAuth = _emailAuth.asStateFlow()
    private var emailAuthJob: Job? = null
    fun clearEmailAuth() {
        if (emailAuthJob?.isActive == true) { account.cancelAuthentication(); emailAuthJob?.cancel() }
        _emailAuth.value = EmailAuthState()
    }
    fun submitEmailAuth(email: String, password: String, signup: Boolean, expectedAccountId: String? = null) =
        accountFeature.submitEmailAuth(email, password, signup, expectedAccountId)
    /** Runs in the Account dialog's scope; cancellation cancels the request and leaves the last confirmed profile intact. */
    suspend fun setAccountPhoneNumber(value: String, expectedAccountId: String) = accountFeature.setAccountPhoneNumber(value, expectedAccountId)
    fun logOut() { driveBackups.accountChanged(null); automaticSync.disable(); accountAction { _cloudPreview.value = null; account.logout() } }
    fun cancelCloudReview() { _cloudPreview.value = null; account.notice("Cancelled. Data was not changed.") }
    fun reviewCloud(kind: String) = accountFeature.reviewCloud(kind)
    fun confirmCloud() = accountFeature.confirmCloud()

    /** Prepare off Main, then commit without suspension after checking that the cache is still current. */
    private suspend fun downloadCloudSnapshot(remote: com.dua.ai.account.CloudSnapshot) = accountFeature.downloadCloudSnapshot(remote)
    /** Chat goes to DUA_CHAT_BASE_URL with the owner session; separate-engine server saving remains disabled. */
    private val separateChatEngine = com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL.trimEnd('/') != com.dua.ai.BuildConfig.DUA_API_BASE_URL.trimEnd('/')
    private val api = DuaApiClient(com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL, appToken = com.dua.ai.BuildConfig.DUA_APP_TOKEN,
        apiBaseUrl = com.dua.ai.BuildConfig.DUA_API_BASE_URL)

    // ---- Imagine (Phase 3): image and video generation through /api/imagine ----
    private val imagineApi = com.dua.ai.imagine.ImagineApi()
    /** Google Cloud Imagine (Vertex AI), used for new images when the engine advertises it; the Node API stays for edits and video. */
    private val cloudImagineApi = com.dua.ai.imagine.ImagineApi(com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL, appToken = com.dua.ai.BuildConfig.DUA_APP_TOKEN)
    private val _cloudImagine = MutableStateFlow<com.dua.ai.imagine.CloudImagine?>(null)
    private var cloudImagineCheck: kotlinx.coroutines.Job? = null
    fun refreshCloudImagine() {
        if (!separateChatEngine || _cloudImagine.value != null || cloudImagineCheck?.isActive == true) return
        cloudImagineCheck = viewModelScope.launch {
            _cloudImagine.value = com.dua.ai.imagine.CloudImagine.fetch(com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL, com.dua.ai.BuildConfig.DUA_APP_TOKEN)
        }
    }
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
    /** Small on-device AI (titles, Imagine prompt polish). Unsupported phones always use the server path. */
    val onDeviceAi = com.dua.ai.ondevice.OnDeviceAi(com.dua.ai.ondevice.OnDeviceServices.textModel(application), viewModelScope)
    init { onDeviceAi.refresh() }
    /** True only when this phone supports it and the "On-device AI" setting is on. */
    fun onDeviceAiEnabled(): Boolean = onDeviceAi.supported && localSettings.value.onDeviceAi
    private val _polishing = MutableStateFlow(false)
    val polishing = _polishing.asStateFlow()
    private val _polishNotice = MutableStateFlow("")
    val polishNotice = _polishNotice.asStateFlow()
    /** Rewrites the composer draft as a richer Imagine prompt, on the phone. The draft is left alone if it changed meanwhile. */
    fun polishImaginePrompt() {
        val original = _draft.value
        if (_polishing.value || original.isBlank() || !onDeviceAiEnabled()) return
        _polishing.value = true; _polishNotice.value = ""
        viewModelScope.launch {
            try {
                val polished = onDeviceAi.polish(original)
                _polishNotice.value = when {
                    polished == null -> "On-device polish is not ready on this phone right now. Your prompt was not changed."
                    _draft.value != original -> "Your prompt changed while polishing, so it was kept as is."
                    else -> { updateDraft(polished); "Prompt polished on this phone." }
                }
            } finally { _polishing.value = false }
        }
    }
    private val _writing = MutableStateFlow<com.dua.ai.ondevice.WritingSession?>(null)
    /** The open on-device Summarize / Rewrite sheet, if any. */
    val writing = _writing.asStateFlow()
    private var writingJob: Job? = null
    /** Opens Summarize / Rewrite for [text]. A null [task] means "Rewrite" and the user picks a style first. */
    fun openWriting(text: String, fromDraft: Boolean, task: com.dua.ai.ondevice.WritingTask?) {
        if (text.isBlank()) return
        writingJob?.cancel()
        _writing.value = com.dua.ai.ondevice.WritingSession(text, fromDraft, task)
        if (task != null) runWriting(task)
    }
    fun runWriting(task: com.dua.ai.ondevice.WritingTask) {
        val session = _writing.value ?: return
        writingJob?.cancel()
        if (!onDeviceAiEnabled()) { _writing.value = session.copy(task = task, phase = com.dua.ai.ondevice.WritingSession.Phase.Unavailable); return }
        _writing.value = session.copy(task = task, phase = com.dua.ai.ondevice.WritingSession.Phase.Working, result = "")
        writingJob = viewModelScope.launch {
            val result = onDeviceAi.write(task, session.text)
            _writing.value = _writing.value?.takeIf { it.text == session.text && it.task == task }?.let {
                if (result == null) it.copy(phase = com.dua.ai.ondevice.WritingSession.Phase.Failed)
                else it.copy(phase = com.dua.ai.ondevice.WritingSession.Phase.Result, result = result)
            }
        }
    }
    /** Puts the on-device result in the message box (nothing is sent). */
    fun useWritingResult() {
        val session = _writing.value ?: return
        if (session.phase == com.dua.ai.ondevice.WritingSession.Phase.Result) updateDraft(session.result)
        closeWriting()
    }
    /** Server path: puts a "Summarize / Rewrite this" request in the message box for the normal Dua chat. Nothing is sent. */
    fun askServerForWriting() {
        val session = _writing.value ?: return
        val task = session.task ?: return
        updateDraft(com.dua.ai.ondevice.WritingPrompts.server(task, session.text))
        closeWriting()
    }
    fun closeWriting() { writingJob?.cancel(); writingJob = null; _writing.value = null }
    private val backgroundRemover by lazy { com.dua.ai.ondevice.OnDeviceServices.backgroundRemover(application) }
    /** Google Play services is present, so the on-device background tool can run (or be downloaded once). */
    val backgroundRemovalAvailable: Boolean by lazy { backgroundRemover.available() }
    /** A transparent PNG of the photo's subject, made on the phone, in the private cache. Throws with a user-facing message. */
    suspend fun removeBackground(bytes: ByteArray): java.io.File =
        com.dua.ai.ondevice.Cutouts.create(getApplication(), backgroundRemover, bytes)
    /** Bytes of a draft image attachment, or null when it is not an image any more. */
    fun attachmentImageBytes(index: Int): ByteArray? = _attachments.value.getOrNull(index)
        ?.takeIf { it.mimeType.startsWith("image/") && it.data.length <= (com.dua.ai.attachments.AttachmentReader.MAX_BYTES + 2) / 3 * 4 }
        ?.let { runCatching { java.util.Base64.getDecoder().decode(it.data) }.getOrNull() }
    private val appearanceStore = com.dua.ai.settings.AppearanceStore(application)
    private var appearanceLoaded = false
    val appearance = appearanceStore.data.onEach { appearanceLoaded = true }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, appearanceStore.initial)
    val appearanceNotice = appearanceStore.notice
    fun setPersonality(value: com.dua.ai.settings.DuaPersonality) = saveAppearance { appearanceStore.setPersonality(value) }
    fun setAppearance(value: String) = saveAppearance { appearanceStore.setTheme(value) }
    fun setAccent(value: com.dua.ai.settings.AccentPreset?) = saveAppearance { appearanceStore.setAccent(value) }
    private fun saveAppearance(write: suspend () -> Unit) = viewModelScope.launch {
        try { write(); _settingsStatus.value = "Saved on this device." }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { _settingsStatus.value = "Appearance could not be saved. Check device storage and retry." }
    }
    private val _settingsStatus = MutableStateFlow("")
    val settingsStatus = _settingsStatus.asStateFlow()
    var foreground = false
        private set
    fun setForeground(value: Boolean) {
        foreground = value
        automaticSync.setForeground(value)
        setNetwork(networkMonitor.currentlyOnline())
    }
    fun resetSettings() {
        try { localPreferences.reset() }
        catch (failure: Exception) { _settingsStatus.value = failure.message ?: "Reset failed."; return }
        viewModelScope.launch {
            try {
                appearanceStore.reset()
                _settingsStatus.value = "Device settings and Personal memory reset. Project settings and memory kept."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { _settingsStatus.value = "Other device settings reset; appearance could not be reset. Check storage and retry." }
        }
    }
    fun settingsNotice(text: String) { _settingsStatus.value = text }
    fun updateSettings(value: com.dua.ai.settings.LocalPreferences) {
        try { localPreferences.update(value); _settingsStatus.value = "Saved on this device." }
        catch (error: Exception) { _settingsStatus.value = error.message ?: "Settings could not be saved." }
    }

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

    /** Keep chat turns for Retry; unfinished Imagine placeholders still disappear on Stop. */
    private fun finalizeStreaming(): Boolean {
        val original = retryOriginal
        retryOriginal = null
        if (_messages.value.none { it.streaming }) return false
        _messages.value = _messages.value.mapNotNull { message ->
            when {
                !message.streaming -> message
                original != null && original.id == message.id && message.text.isBlank() -> original
                message.isImagine && message.text.isBlank() -> null
                else -> message.copy(state = "stopped", thinkingSeconds = message.thinkingSeconds ?: elapsedThinking(message))
            }
        }
        return true
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    /**
     * Streams into message [id], emitting at most one UI snapshot per 24 ms plus processing time (latest state wins).
     * This batches text updates independently of the display's frame rate.
     * Events are applied to [accumulator] as they arrive, so a failure keeps everything received.
     */
    private suspend fun collectReply(id: String, accumulator: com.dua.ai.network.StreamAccumulator, events: kotlinx.coroutines.flow.Flow<com.dua.ai.network.ReplyEvent>,
        request: RequestTurns? = null) {
        val chatId = activeId
        events.onEach {
            if (it is com.dua.ai.network.ReplyEvent.Files) applyTranscripts(it.transcripts)
            if (it is com.dua.ai.network.ReplyEvent.Media) it.links.forEach { (path, url) -> mediaCache.remember(path, url) }
            if (it is com.dua.ai.network.ReplyEvent.Memory) suggestAgentMemory(it.fact)
            if (it is com.dua.ai.network.ReplyEvent.Context && request?.context != null) applySummary(chatId, request.turns, it)
            if (it is com.dua.ai.network.ReplyEvent.Saved) {
                val receipt = it.receipt
                check(activeId == chatId && receipt.chatId == chatId && receipt.replyId == id && account.state.value.user?.id == receipt.ownerId) {
                    "Account or chat changed before server save acknowledgement. Review cloud history."
                }
                val latest = accumulator.snapshot(ended = true)
                val completed = _messages.value.map { message -> if (message.id == id) message.copy(
                    text = latest.text, thinking = latest.thinking, thinkingSeconds = receipt.thinkingSeconds,
                    notice = latest.notice, codeRuns = latest.codeRuns, build = latest.build ?: message.build,
                    proposal = latest.proposal ?: message.proposal, state = "complete") else message }
                val confirmed = withContext(Dispatchers.Default) { com.dua.ai.account.ReplyPersistenceCodec.confirmedChat(receipt, completed) }
                check(activeId == chatId && account.state.value.user?.id == receipt.ownerId && _messages.value.lastOrNull()?.id == id)
                val prior = _savedChats.value.find { chat -> chat.id == chatId }
                // Retain unsynced local metadata edits; adopt concurrent server metadata only where unchanged.
                val cached = com.dua.ai.account.ReplyPersistenceCodec.cachedMetadata(confirmed, prior, _draft.value, _buildRepo.value, receipt)
                _messages.value = completed; _draft.value = cached.draft; _summary.value = cached.summary
                _selectedModel.value = cached.model; _projectId.value = cached.projectId; _buildRepo.value = cached.buildRepository
                _savedChats.value = listOf(cached) + _savedChats.value.filterNot { chat -> chat.id == chatId }
                historyWriter.save(_savedChats.value, activeId)
            }
            accumulator.apply(it)
        }.conflate().collect {
            val snapshot = accumulator.snapshot()
            updateMessage(id) { it.copy(progress = snapshot.progress, approvals = snapshot.approvals.ifEmpty { it.approvals }, media = snapshot.media.ifEmpty { it.media }, text = snapshot.text, thinking = snapshot.thinking, thinkingSeconds = snapshot.thinkingSeconds, notice = snapshot.notice, codeRuns = snapshot.codeRuns, build = snapshot.build ?: it.build, proposal = snapshot.proposal ?: it.proposal) }
            delay(STREAM_FRAME_MS)
        }
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
    fun setReasoning(enabled: Boolean) { if (!_isTyping.value) { _reasoning.value = enabled; if (enabled) { _imagine.value = false; _agent.value = false; leaveBuild() } } }
    // Dua Agent (Cloud Run engine only): router, then a server-side tool-calling loop (search, pages, code, images, GitHub).
    // Per-session like Reasoning and never saved; it replaces the other modes for the next reply.
    private val _agent = MutableStateFlow(false)
    val agent = _agent.asStateFlow()
    private val replyAgent = mutableMapOf<String, Boolean>()
    /** Agent mode is offered only when the chat engine advertises `"agent": true` on GET / (unknown or failure = hidden). */
    private val _agentAvailable = MutableStateFlow(false)
    val agentAvailable = _agentAvailable.asStateFlow()
    private val _enginePersonalityAvailable = MutableStateFlow(false)
    private fun personalityUnavailable(): String? =
        if (!appearanceLoaded) "Personalization settings are still loading. Try again shortly."
        else if (appearance.value.personality != null && !(if (separateChatEngine) _enginePersonalityAvailable.value else _integrations.value.personalityEnabled))
            "Update the chat backend and refresh Feature setup to use Dua AI Personality." else null
    private var engineCheck: kotlinx.coroutines.Job? = null
    /** Free OpenRouter models the engine offers, and the chosen one (only while still offered). */
    private val _freeModels = MutableStateFlow<List<com.dua.ai.model.FreeModel>>(emptyList())
    val freeModels = _freeModels.asStateFlow()
    val freeModel: kotlinx.coroutines.flow.StateFlow<com.dua.ai.model.FreeModel?> = combine(_freeModels, localSettings) { models, settings ->
        models.firstOrNull { it.id == settings.freeModel } }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    fun selectFreeModel(id: String) {
        if (_isTyping.value || _freeModels.value.none { it.id == id }) return
        _selectedModel.value = "Dua"; _xSearch.value = false
        updateSettings(localSettings.value.copy(freeModel = id))
        saveHistory()
    }
    private fun requestFreeModel(): String? = freeModel.value?.id
    fun refreshEngineCapabilities() = refreshEngineCapabilities(force = false)
    private fun refreshEngineCapabilities(force: Boolean) {
        if ((!force && _agentAvailable.value) || engineCheck?.isActive == true) return
        engineCheck = viewModelScope.launch {
            val engine = try { api.engineCapabilities() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { com.dua.ai.network.EngineCapabilities() }
            val available = engine.agent && !_switches.value.agent
            _enginePersonalityAvailable.value = engine.personality
            _freeModels.value = engine.freeModels
            _agentAvailable.value = available
            if (!available) _agent.value = false
        }
    }
    fun setAgent(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled && _switches.value.agent) { _error.value = com.dua.ai.setup.RemoteSwitches.AGENT_PAUSED; return }
        if (enabled && !_agentAvailable.value) { _error.value = "Agent mode needs the Google Cloud engine."; return }
        _agent.value = enabled
        if (enabled) { _reasoning.value = false; _xSearch.value = false; _imagine.value = false; _runCode.value = false; _webMode.value = null; leaveBuild() }
        _error.value = null
    }
    /** Auto chip: plain chat, no web and no Reasoning. */
    fun selectAuto() { if (_isTyping.value) return; _reasoning.value = false; _agent.value = false; _xSearch.value = false; _imagine.value = false; _runCode.value = false; leaveBuild(); setWebMode(null) }

    // Run code (Phase 5): the provider runs Python in its own sandbox. Opt-in per session like Reasoning, never saved.
    private val _runCode = MutableStateFlow(false)
    val runCode = _runCode.asStateFlow()
    private val replyCode = mutableMapOf<String, Boolean>()
    private fun codeUsable(model: String = _selectedModel.value) = _integrations.value.codeExecution?.available(model) == true
    /** Only advertise the existing provider sandbox on the engine that actually supports it. */
    fun codeSnippetUnavailableReason(): String? = when {
        api.separateChatEngine -> "Run code is not supported by the active Cloud Run chat engine. Local artifact previews are available."
        !codeUsable() -> _integrations.value.codeExecution?.reason?.takeIf { it.isNotBlank() }
            ?: "The selected model has no configured code sandbox. Check Plugins or choose a supported model."
        !_online.value -> "Reconnect before running code."
        _isTyping.value || _readingFiles.value || _preparingFiles.value -> "Wait for the current reply or file preparation to finish."
        _draft.value.isNotBlank() || _attachments.value.isNotEmpty() || _uploads.value.isNotEmpty() || _editingId.value != null -> "Send or clear the current draft and attachments before running a snippet."
        else -> null
    }

    fun runCodeSnippet(code: String): Boolean {
        codeSnippetUnavailableReason()?.let { _error.value = it; return false }
        val prompt = com.dua.ai.model.CodeSnippet.prompt(code)
        if (code.isBlank() || prompt.length > 8000) { _error.value = "Use a non-empty Python snippet shorter than 7,800 characters."; return false }
        selectAuto()
        setRunCode(true)
        sendMessage(prompt)
        return _isTyping.value
    }
    fun setRunCode(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled) _agent.value = false
        if (enabled) {
            val config = _integrations.value.codeExecution
            val reason = when {
                config == null -> "Run code needs the latest Dua backend. Deploy it, then refresh Feature setup."
                !config.enabled -> config.reason.ifBlank { "Run code is turned off on the server." }
                !config.available(_selectedModel.value) -> "Run code works with ${config.providers.sorted().joinToString(" or ")}. Choose one of them first."
                else -> null
            }
            if (reason != null) { _error.value = reason; return }
            _imagine.value = false
            leaveBuild()
        }
        _runCode.value = enabled
        _error.value = null
    }

    // Build mode (Phase 5B): STRICTLY READ-ONLY chat about one GitHub repository. The repository is tied to the chat
    // (saved with it on this device); the server reads files with its own token and never writes to GitHub.
    private val _buildRepo = MutableStateFlow<String?>(null)
    val buildRepo = _buildRepo.asStateFlow()
    private val replyBuild = mutableMapOf<String, String>()
    private val _buildRepositories = MutableStateFlow(com.dua.ai.setup.BuildRepositories())
    val buildRepositories = _buildRepositories.asStateFlow()
    private fun buildUsable(model: String = _selectedModel.value) = _integrations.value.build?.available(model) == true
    /** Why Build mode cannot be used right now (shown in the Build sheet instead of hiding the mode); null when it can. */
    fun buildUnavailableReason(): String? {
        val config = _integrations.value.build
        return when {
            config == null -> "Build mode needs the latest Dua backend. Deploy it, then refresh Feature setup."
            !config.enabled -> config.reason.ifBlank { "Build mode is turned off on the server." }
            account.state.value.user == null -> "Sign in from Profile to use Build mode. Only the owner account can read repositories."
            _integrations.value.providers.none { it.configured && it.name in config.providers } -> "Build mode works with ${config.providers.sorted().joinToString(" or ")}, and none is configured."
            else -> null
        }
    }
    /** Loads the owner's repositories (read-only list) for the Build picker; kept until [force]d (connector calls share the chat burst limit). */
    fun loadBuildRepositories(force: Boolean = false) {
        if (_buildRepositories.value.loading || (!force && _buildRepositories.value.repositories.isNotEmpty())) return
        buildUnavailableReason()?.let { _buildRepositories.value = com.dua.ai.setup.BuildRepositories(error = it); return }
        _buildRepositories.value = _buildRepositories.value.copy(loading = true, error = "")
        viewModelScope.launch {
            try {
                val response = account.request("/api/connectors/github", "POST", org.json.JSONObject().put("action", "list-repositories"), authenticated = true)
                val repositories = com.dua.ai.setup.IntegrationCapabilities.repositories(response).filter { com.dua.ai.model.BuildInfos.validRepository(it.fullName) }
                _buildRepositories.value = com.dua.ai.setup.BuildRepositories(repositories = repositories,
                    error = if (repositories.isEmpty()) "No repositories returned. Check which repositories the connector token can read." else "")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _buildRepositories.value = com.dua.ai.setup.BuildRepositories(error = failure.message ?: "Repositories could not be loaded. Retry.") }
        }
    }
    /** Ties this chat to [repository] (owner/name), or leaves Build mode with null. Other modes turn off. */
    fun setBuildRepository(repository: String?) {
        if (_isTyping.value) return
        if (repository == null) { leaveBuild(); return }
        if (!com.dua.ai.model.BuildInfos.validRepository(repository)) return
        buildUnavailableReason()?.let { _error.value = it; return }
        if (!buildUsable()) {
            val config = _integrations.value.build!!
            _selectedModel.value = listOf("Dua", "Gemini").first { name -> name in config.providers && _integrations.value.providers.any { it.name == name && it.configured } }
        }
        _reasoning.value = false; _webMode.value = null; _xSearch.value = false; _runCode.value = false; _imagine.value = false; _agent.value = false
        _buildRepo.value = repository
        _error.value = null
        saveHistory()
    }
    private fun leaveBuild() { if (_buildRepo.value != null) { _buildRepo.value = null; saveHistory() } }

    // Build pull requests (Phase 5C): the model only proposes; this is the single place that asks the server to write,
    // and it runs only from the review card's Create PR button after the owner confirms the dialog.
    private val _buildPr = MutableStateFlow<Map<String, com.dua.ai.ui.chat.BuildPrUiState>>(emptyMap())
    val buildPr = _buildPr.asStateFlow()
    /** Why the Create PR button is unavailable (shown on the review card), or null when it can be used. */
    fun buildPrUnavailableReason(): String? {
        val pr = _integrations.value.build?.pr
        return when {
            pr == null -> "Creating pull requests needs the latest Dua backend. Deploy it, then refresh Feature setup."
            !pr.enabled -> pr.reason.ifBlank { "Creating pull requests is turned off on the server." }
            account.state.value.user == null -> "Sign in from Profile to create pull requests. Only the owner account can."
            else -> null
        }
    }
    /** Sends the approved proposal of [messageId] (with the confirmed [title]) to POST /api/build/pr and saves the PR link. */
    fun createPullRequest(messageId: String, title: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val proposal = message.proposal ?: return
        if (proposal.pullRequest != null || _buildPr.value[messageId]?.creating == true) return
        val setState = { state: com.dua.ai.ui.chat.BuildPrUiState? -> _buildPr.update { if (state == null) it - messageId else it + (messageId to state) } }
        buildPrUnavailableReason()?.let { setState(com.dua.ai.ui.chat.BuildPrUiState(error = it)); return }
        val body = try { com.dua.ai.model.BuildProposals.createRequest(proposal, title) }
            catch (invalid: IllegalArgumentException) { setState(com.dua.ai.ui.chat.BuildPrUiState(error = invalid.message ?: "This proposal cannot be sent.")); return }
        setState(com.dua.ai.ui.chat.BuildPrUiState(creating = true))
        viewModelScope.launch {
            try {
                val response = account.request("/api/build/pr", "POST", body, authenticated = true)
                val pr = com.dua.ai.model.BuildProposals.pullRequest(response, proposal.repository)
                    ?: throw IllegalStateException("The server did not return a pull request link. Check GitHub before retrying.")
                val withPr = { item: ChatMessage -> if (item.id == messageId) item.copy(proposal = item.proposal?.copy(title = body.getString("title"), pullRequest = pr)) else item }
                // The owner may have switched chats while GitHub was working: store the link wherever the message is now.
                if (_messages.value.any { it.id == messageId }) _messages.value = _messages.value.map(withPr)
                else _savedChats.value = _savedChats.value.map { chat -> if (chat.messages.any { it.id == messageId }) chat.copy(messages = chat.messages.map(withPr)) else chat }
                setState(null)
                saveHistory()
            } catch (cancelled: CancellationException) { setState(null); throw cancelled }
            catch (failure: Exception) {
                setState(com.dua.ai.ui.chat.BuildPrUiState(error = failure.message?.take(300)?.takeIf { it.isNotBlank() } ?: "Creating the pull request failed. Retry."))
            }
        }
    }

    // Dua Agent approvals: the engine never runs a write tool by itself. This is the single place that asks it to run (or
    // drop) one stored action, and only from the approval card's Approve / Reject buttons. Only the id is sent.
    private fun updateApproval(messageId: String, approval: com.dua.ai.model.AgentApproval, outcome: Boolean = false) {
        val change = { item: ChatMessage ->
            if (item.id != messageId) item else item.copy(approvals = item.approvals.map { if (it.id == approval.id) approval else it },
                text = if (outcome) com.dua.ai.model.AgentApprovals.appendOutcome(item.text, com.dua.ai.model.AgentApprovals.transcriptLine(approval)) else item.text)
        }
        // The owner may have switched chats meanwhile: update the message wherever it is now.
        if (_messages.value.any { it.id == messageId }) _messages.value = _messages.value.map(change)
        else _savedChats.value = _savedChats.value.map { chat -> if (chat.messages.any { it.id == messageId }) chat.copy(messages = chat.messages.map(change)) else chat }
        if (outcome) saveHistory()
    }

    fun decideAgentApproval(messageId: String, approvalId: String, approve: Boolean) {
        val approval = _messages.value.firstOrNull { it.id == messageId }?.approvals?.firstOrNull { it.id == approvalId } ?: return
        if (approval.status != com.dua.ai.model.AgentApproval.Status.Pending) return
        if (approval.expired(System.currentTimeMillis())) {
            updateApproval(messageId, approval.copy(status = com.dua.ai.model.AgentApproval.Status.Expired), outcome = true); return
        }
        updateApproval(messageId, approval.copy(status = com.dua.ai.model.AgentApproval.Status.Working))
        viewModelScope.launch {
            try {
                val (status, text) = account.withToken { token -> api.agentAction(approvalId, approve, token) }
                updateApproval(messageId, approval.copy(status = status, result = text), outcome = true)
            } catch (cancelled: CancellationException) { updateApproval(messageId, approval); throw cancelled }
            catch (failure: DuaApiException) {
                // 404/410: the server no longer has it (used, expired or restarted); nothing can run twice.
                if (failure.status == 404 || failure.status == 410)
                    updateApproval(messageId, approval.copy(status = com.dua.ai.model.AgentApproval.Status.Expired, result = failure.message), outcome = true)
                else { updateApproval(messageId, approval); _error.value = failure.message ?: "Action nahi ho saka. Dobara try karein." }
            } catch (failure: Exception) {
                // Safe to try again: the server runs each approval at most once.
                updateApproval(messageId, approval)
                _error.value = "${failureText(failure)} The action was not confirmed; tap Approve or Reject again."
            }
        }
    }

    // X search (Grok only, billed per post by xAI) is off by default and, like the web mode, never saved.
    private val _xSearch = MutableStateFlow(false)
    val xSearch = _xSearch.asStateFlow()
    private val replyXSearch = mutableMapOf<String, Boolean>()
    private fun xSearchUsable(model: String = _selectedModel.value) = model == "Grok" && _integrations.value.xSearchAvailable
    fun setXSearch(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled && !xSearchUsable()) { _error.value = "X search works with Grok when the server offers it."; return }
        _xSearch.value = enabled
        if (enabled) _agent.value = false
        _error.value = null
    }

    // Long context: the server's summary of this chat's older turns (saved with the chat on this device).
    private val _summary = MutableStateFlow<com.dua.ai.model.ChatSummary?>(null)
    val summary = _summary.asStateFlow()
    internal data class RequestTurns(val turns: List<ChatMessage>, val context: com.dua.ai.model.ConversationContext?, val historyBytes: Int)

    /** Long-context servers get every turn after the cached summary; older servers the last 39 turns as before. */
    private fun requestTurns(history: List<ChatMessage>, legacy: List<ChatMessage>, inlineFiles: Boolean): RequestTurns {
        if (!_integrations.value.longContext) return RequestTurns(legacy,
            if (_integrations.value.projectsEnabled) com.dua.ai.model.ConversationContext(activeId, null, _projectId.value) else null, historyBytes())
        val budget = if (inlineFiles) DuaApiClient.LONG_HISTORY_BYTES_WITH_FILES else DuaApiClient.LONG_HISTORY_BYTES
        val selection = com.dua.ai.model.ContextWindow.select(history, _summary.value, budget, DuaApiClient::turnJsonBytes)
        return RequestTurns(selection.turns, com.dua.ai.model.ConversationContext(activeId, selection.summary?.text, _projectId.value.takeIf { _integrations.value.projectsEnabled }), budget + 1024)
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
        if (mode != null) { _imagine.value = false; _agent.value = false; leaveBuild() }
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
    // Encodes and writes history off the main thread; see ChatHistoryWriter for ordering guarantees.
    private val historyWriter = com.dua.ai.history.ChatHistoryWriter(viewModelScope, Dispatchers.Default) { encoded ->
        preferences.edit().putString("history", encoded).apply()
    }
    // Debounced save of the draft being typed (see updateDraft).
    private val draftSaver = com.dua.ai.history.Debouncer(viewModelScope, DRAFT_SAVE_DELAY_MS)
    private val _activeChatId = MutableStateFlow(UUID.randomUUID().toString())
    val activeChatId: StateFlow<String> = _activeChatId.asStateFlow()
    private var activeId: String
        get() = _activeChatId.value
        set(value) { _activeChatId.value = value }
    private val _savedChats = MutableStateFlow<List<SavedChat>>(emptyList())
    val savedChats: StateFlow<List<SavedChat>> = _savedChats.asStateFlow()
    private val _deletedChatIds = MutableStateFlow<List<String>>(emptyList())
    private var deletionOwnerId: String? = null
    private fun currentDeletionIds(): List<String> {
        val signedIn = account.state.value.user?.id
        return if (signedIn != null && deletionOwnerId != null && signedIn != deletionOwnerId) emptyList() else _deletedChatIds.value
    }
    data class ChatDeletionState(val id: String? = null, val busy: Boolean = false, val error: String? = null, val completed: Boolean = false)
    private val _chatDeletion = MutableStateFlow(ChatDeletionState())
    val chatDeletion = _chatDeletion.asStateFlow()
    private val _attachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val attachments: StateFlow<List<ChatAttachment>> = _attachments.asStateFlow()
    private val _attachmentError = MutableStateFlow("")
    val attachmentError: StateFlow<String> = _attachmentError.asStateFlow()
    private val _readingFiles = MutableStateFlow(false)
    val readingFiles: StateFlow<Boolean> = _readingFiles.asStateFlow()
    private var selectionVersion = 0
    private var preparingSelections = 0
    private val _preparingFiles = MutableStateFlow(false)
    val preparingFiles: StateFlow<Boolean> = _preparingFiles.asStateFlow()

    /** Reserve shared-file intake synchronously, before the next queued entry can dispatch. */
    fun acceptSharedAttachments(uris: List<Uri>): Boolean {
        if (_isTyping.value || _dataBusy.value || _preparingFiles.value || _readingFiles.value ||
            _attachments.value.isNotEmpty() || _uploads.value.isNotEmpty()) return false
        selectAttachments(uris)
        return true
    }

    /** Remove only the selected inline draft file; keep every other attachment intact. */
    fun removeAttachment(index: Int) {
        if (_isTyping.value) return
        _attachments.update { files -> files.filterIndexed { i, _ -> i != index } }
    }

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
    private val _storageUsageStatus = MutableStateFlow(com.dua.ai.files.StorageUsageStatus())
    val storageUsageStatus = _storageUsageStatus.asStateFlow()
    private var storageUsageGeneration = 0L
    private val _cloudLibrary = MutableStateFlow(com.dua.ai.library.CloudLibraryState())
    val cloudLibrary = _cloudLibrary.asStateFlow()
    private var libraryJob: Job? = null

    fun loadLibraryFiles() {
        libraryJob?.cancel()
        val owner = account.state.value.user?.id
        _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(ownerId = owner)
        if (owner == null || !_integrations.value.filesEnabled) return
        _cloudLibrary.value = _cloudLibrary.value.copy(loading = true)
        libraryJob = viewModelScope.launch {
            try {
                val json = account.request("/api/files/list", authenticated = true)
                val files = com.dua.ai.library.CloudLibrary.files(json, owner)
                if (account.state.value.user?.id == owner) _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(owner, files,
                    notice = if (json.optBoolean("truncated")) "Cloud inventory is limited to 10,000 files." else "")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (account.state.value.user?.id == owner) _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(owner,
                    notice = "Cloud inventory could not be loaded. Refresh after updating the backend; chat and project files remain available.")
            }
        }
    }

    /** Stored files need a Phase 2 backend and a signed-in account (the server also requires the owner email). */
    fun storedFilesAvailable() = _integrations.value.filesEnabled && account.state.value.user != null
    fun inlineChatFilesAvailable() = api.usesInlineChatFiles(_imagine.value)
    private fun maxStoredBytes() = _integrations.value.filesMaxBytes.takeIf { it > 0 } ?: (50L * 1024 * 1024)
    fun storedFilesHint(): String = when {
        inlineChatFilesAvailable() -> "Cloud Run chat sends up to 3 files inline (2 MB total). Use CSV or UTF-8 text for spreadsheets; reselect media after restoring a chat."
        !_integrations.value.filesEnabled -> "Audio, spreadsheets and files over 2 MB need stored files on the Dua backend (see SUPABASE.md). Small images, PDFs and text files still work."
        account.state.value.user == null -> "Sign in from Profile to attach audio, spreadsheets and large files (up to ${StoredFileTypes.sizeLabel(maxStoredBytes())} each). Without signing in, up to 3 small files (2 MB total) still work."
        else -> ""
    }

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

    /** Opens a stored file with a short-lived signed link (the server checks the owner). */
    suspend fun storedFileUrl(ref: FileRef): String = storedFileUrl(ref.path)

    fun startRecording() {
        if (_isTyping.value || recorder.recording) return
        if (!api.usesInlineChatFiles(_imagine.value) && !storedFilesAvailable()) { _attachmentError.value = storedFilesHint(); return }
        try { recorder.start(); _recordingSince.value = recorder.startedAt; _attachmentError.value = "" }
        catch (failure: Exception) { _attachmentError.value = failure.message ?: "Recording could not start." }
    }
    fun stopRecording() {
        val file = recorder.stop()
        _recordingSince.value = null
        if (file == null) { _attachmentError.value = "Recording was too short. Hold on a little longer and try again."; return }
        val name = "Voice note " + java.time.LocalTime.now().withNano(0).toString().take(5) + ".m4a"
        if (api.usesInlineChatFiles(_imagine.value)) loadFiles(cleanup = { file.delete() }) {
            file.inputStream().use { PhotoAttachmentReader.prepare(listOf(PhotoPolicy.readSource(name, AudioRecorder.MIME, it, allowAudio = true))) }
        } else startUpload(name, AudioRecorder.MIME, file.length(), open = { file.inputStream() }, cleanup = { file.delete() })
    }
    fun cancelRecording() { recorder.cancel(); _recordingSince.value = null }

    fun loadStorageUsage() {
        val owner = account.state.value.user?.id
        val generation = ++storageUsageGeneration
        _storageUsage.value = null
        _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(ownerId = owner, loading = owner != null)
        if (owner == null) return
        fun current() = generation == storageUsageGeneration && account.state.value.user?.id == owner
        viewModelScope.launch {
            try {
                val json = account.request("/api/files/usage", authenticated = true)
                val usage = com.dua.ai.files.StorageOverview.usage(json)
                if (current()) _storageUsage.value = usage
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (current()) {
                    val message = failure.message ?: "Storage usage could not be loaded. Refresh to retry."
                    _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(owner, error = message)
                    if (!_dataBusy.value) _dataStatus.value = message
                }
            } finally {
                if (current()) _storageUsageStatus.value = _storageUsageStatus.value.copy(loading = false)
            }
        }
    }

    /** Deletes every uploaded file for this account. Chats keep the file names but can no longer open or re-read them. */
    fun deleteAllUploadedFiles() {
        if (_dataBusy.value || _isTyping.value) return
        val user = account.state.value.user ?: run { _dataStatus.value = "Sign in to delete uploaded files."; return }
        ++storageUsageGeneration
        _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(user.id)
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
        preparingSelections++
        _preparingFiles.value = true
        viewModelScope.launch {
            try { prepareAttachments(uris) }
            finally { preparingSelections--; _preparingFiles.value = preparingSelections > 0 }
        }
    }

    private suspend fun prepareAttachments(uris: List<Uri>) {
        val picked = try { withContext(Dispatchers.IO) { describe(uris) } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _attachmentError.value = "File read nahi hui. Dobara select karein."; return }
        val inline = if (api.usesInlineChatFiles(_imagine.value)) picked.map { it.uri }
        else if (storedFilesAvailable()) uploadPicked(picked) else {
            val blocked = picked.filter { StoredFileTypes.needsStorage(it.name, it.type) || (it.size ?: 0) > AttachmentReader.MAX_BYTES }
            if (blocked.isNotEmpty()) _attachmentError.value = storedFilesHint()
            picked.filterNot { it in blocked }.map { it.uri }
        }
        if (inline.isNotEmpty()) selectInlineAttachments(inline)
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
            input.use { PhotoPolicy.readSource(name, type, it, allowAudio = api.usesInlineChatFiles(_imagine.value)) }
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

    private val fullBackupPort = object : com.dua.ai.backup.FullBackupPort {
                override fun begin(): com.dua.ai.backup.FullBackupSnapshot {
                    check(!syncBlocked() && !_readingFiles.value && !_preparingFiles.value && _uploads.value.isEmpty() && _attachments.value.isEmpty() && _recordingSince.value == null) {
                        "Finish the current reply or data action, and send or remove pending files before making a backup."
                    }
                    check(historyReadable) { "Saved chats could not be read. Recover the cache before making a full backup." }
                    localPreferences.requireReadable()
                    draftSaver.runNow()
                    val settings = localSettings.value
                    val snapshot = com.dua.ai.backup.FullBackupSnapshot(com.dua.ai.history.ChatBackup(_savedChats.value.toList(), settings.projects.toList()),
                        settings.memory.toList(), account.state.value.user?.id, _integrations.value.filesEnabled, currentDeletionIds())
                    _dataBusy.value = true; refreshSyncBusy()
                    return snapshot
                }
                override fun end() { _dataBusy.value = false; refreshSyncBusy() }
                override fun owner() = account.state.value.user?.id
                override fun restoreFilesAvailable() = _integrations.value.filesEnabled && _integrations.value.filesRestoreEnabled
                override fun preflight(data: com.dua.ai.backup.FullBackupData) {
                    com.dua.ai.backup.FullBackupArchive.mergeMemory(localSettings.value.memory, data.memory)
                    val adjusted = com.dua.ai.projects.ProjectImports.prepare(localSettings.value.projects, data.backup)
                    ChatHistoryCodec.importCopies(_savedChats.value, adjusted.chats, projects = adjusted.projects)
                }
                override suspend fun importData(backup: com.dua.ai.history.ChatBackup, memory: List<String>): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    // Same Main turn: no user action or automatic sync can interleave between these flags.
                    _dataBusy.value = false
                    importProjects = backup.projects
                    _importPreview.value = backup.chats
                    val operation = commitReviewedImport(memory) { success ->
                        _importPreview.value = null
                        if (continuation.isActive) continuation.resumeWith(Result.success(success))
                    }
                    if (operation == null && continuation.isActive) { _importPreview.value = null; continuation.resumeWith(Result.success(false)) }
                    continuation.invokeOnCancellation { operation?.cancel() }
                }
    }
    private val fullBackupDelegate = lazy {
        com.dua.ai.backup.FullBackupController(viewModelScope, getApplication<Application>().contentResolver,
            File(getApplication<Application>().cacheDir, "full-backups"), com.dua.ai.backup.BackupFileTransfer(account), fullBackupPort)
    }
    val fullBackup by fullBackupDelegate
    private var driveRestoreJob: Job? = null
    private val _driveRestoreBusy = MutableStateFlow(false)
    val driveRestoreBusy = _driveRestoreBusy.asStateFlow()
    fun cancelBackupOperation() { driveRestoreJob?.cancel(); fullBackup.cancel() }

    fun reviewDriveBackup(id: String, password: CharArray) = dataFeature.reviewDriveBackup(id, password)

    private val _importPreview = MutableStateFlow<List<SavedChat>?>(null)
    private var importProjects = com.dua.ai.projects.ProjectCodec.defaults
    val importPreview: StateFlow<List<SavedChat>?> = _importPreview.asStateFlow()
    fun cancelImport() { if (!_dataBusy.value) { _importPreview.value = null; _dataStatus.value = "Import cancelled. Your chats are unchanged." } }

    fun reviewImport(uri: Uri) = dataFeature.reviewImport(uri)

    fun confirmImport() { commitReviewedImport() }

    private fun commitReviewedImport(importedMemory: List<String>? = null, completion: (Boolean) -> Unit = {}): Job? =
        dataFeature.commitReviewedImport(importedMemory, completion)

    fun exportChats(uri: Uri) = dataFeature.exportChats(uri)

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

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()
    // Edit a sent message (ChatGPT-style): the composer holds the edited text; sending replaces that message and every later turn.
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()
    fun startEdit(messageId: String) {
        if (_isTyping.value) return
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (!com.dua.ai.model.MessageEdits.editable(message)) { _error.value = "Messages with files cannot be edited. Send a new message instead."; return }
        _editingId.value = messageId
        _draft.value = message.text
        _error.value = null
    }
    fun cancelEdit() { if (_editingId.value != null) { _editingId.value = null; _draft.value = "" } }
    /** Applies a pending edit: drops the edited message and later turns. Returns true when an edit was applied. */
    private fun applyEdit(): Boolean {
        val id = _editingId.value ?: return false
        _editingId.value = null
        val kept = com.dua.ai.model.MessageEdits.keptBefore(_messages.value, id) ?: return false
        com.dua.ai.model.MessageEdits.removedIds(_messages.value, id).forEach { removed ->
            replyWebModes.remove(removed); replyReasoning.remove(removed); replyAgent.remove(removed); replyCode.remove(removed); replyBuild.remove(removed); replyXSearch.remove(removed)
        }
        _messages.value = kept
        _summary.value = com.dua.ai.model.ContextWindow.validSummary(kept, _summary.value)
        return true
    }
    private var historyReadable = true
    private val _projectId = MutableStateFlow(com.dua.ai.projects.ProjectCodec.PERSONAL)
    val projectId = _projectId.asStateFlow()
    private val _projectNotice = MutableStateFlow("")
    val projectNotice = _projectNotice.asStateFlow()
    private fun projectMemory(): String = if (_projectId.value == com.dua.ai.projects.ProjectCodec.PERSONAL)
        com.dua.ai.settings.PreferencesCodec.memoryText(localSettings.value) else ""

    private val sourceRequests = mutableMapOf<String, Pair<String, Uri>>()
    private val _projectSourceUploads = MutableStateFlow<List<com.dua.ai.projects.ProjectSourceUpload>>(emptyList())
    val projectSourceUploads = _projectSourceUploads.asStateFlow()
    fun selectProject(id: String) = projectsFeature.selectProject(id)
    fun saveProject(id: String?, name: String, instructions: String, archived: Boolean): Boolean = projectsFeature.saveProject(id, name, instructions, archived)
    fun createProject(name: String, instructions: String, icon: String, color: String): String? = projectsFeature.createProject(name, instructions, icon, color)
    fun updateProjectPresentation(id: String, name: String, instructions: String, icon: String, color: String): Boolean = projectsFeature.updateProjectPresentation(id, name, instructions, icon, color)
    fun pinProject(id: String): Boolean = projectsFeature.pinProject(id)
    /** Retain the authoritative definition and chats/files; hidden tombstones cannot be resurrected by stale clients. */
    fun deleteProject(id: String): Boolean = projectsFeature.deleteProject(id)
    fun removeProjectSource(projectId: String, path: String): Boolean = projectsFeature.removeProjectSource(projectId, path)
    fun retryProjectSourceUpload(id: String) = projectsFeature.retryProjectSourceUpload(id)
    fun dismissProjectSourceUpload(id: String) = projectsFeature.dismissProjectSourceUpload(id)
    fun uploadProjectSources(projectId: String, uris: List<Uri>) = projectsFeature.uploadProjectSources(projectId, uris)
    fun saveTask(projectId: String, taskId: String?, title: String, dueAt: Long? = null): Boolean = projectsFeature.saveTask(projectId, taskId, title, dueAt)
    fun toggleTask(projectId: String, taskId: String): Boolean = projectsFeature.toggleTask(projectId, taskId)
    fun deleteTask(projectId: String, taskId: String): Boolean = projectsFeature.deleteTask(projectId, taskId)
    fun saveProjectMemory(projectId: String, enabled: Boolean, notes: List<String>): Boolean = projectsFeature.saveProjectMemory(projectId, enabled, notes)
    private suspend fun engineProjectContext(projectId: String): EngineProjectContext = projectsFeature.engineProjectContext(projectId)
    private fun projectUnavailable(): String? = projectsFeature.projectUnavailable()

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
                // Include keystrokes still waiting for the draft debounce in the merge/baseline.
                flushHistory()
                return CloudSnapshot(_savedChats.value.toList(), localSettings.value.memory.toList(), localSettings.value.projects.toList(), currentDeletionIds())
            }
            // Read, merge and commit run without suspension on Main. Rebind the open chat only
            // after persistence; otherwise its next draft save would write the stale cache back.
            override fun commit(snapshot: CloudSnapshot) = commit(AutomationMergePlan(snapshot, false))
            override fun commit(plan: AutomationMergePlan) {
                val snapshot = plan.snapshot
                check(!syncBlocked()) { "Sync paused during a reply or manual review." }
                check(!draftSaver.pending) { "The draft changed after reading the cache. Retry sync before applying server data." }
                historyWriter.flush()
                com.dua.ai.account.CloudSnapshotCodec.encode(snapshot)
                val settings = localSettings.value
                val previous = preferences.getString("history", null)
                val previousDeleted = preferences.getString("deleted_chat_ids", null)
                val previousDeletionOwner = preferences.getString("deleted_chat_owner", null)
                // The server snapshot is authoritative, including long-context summaries.
                val chats = snapshot.chats
                val selectedId = plan.recoveredChatIds[activeId]?.takeIf { id -> chats.any { it.id == id } } ?: activeId
                val encoded = ChatHistoryCodec.encode(chats, selectedId)
                localPreferences.update(settings.copy(memory = snapshot.memory, projects = snapshot.projects))
                try {
                    // Kept synchronous: the sync controller expects the commit to finish before it records the revision.
                    val saved = historyWriter.writeDirectly {
                        preferences.edit().putString("history", encoded).putString("deleted_chat_ids", org.json.JSONArray(snapshot.deletedChatIds).toString())
                            .putString("deleted_chat_owner", account.state.value.user?.id).commit().also { ok ->
                            if (!ok) preferences.edit().putString("history", previous).putString("deleted_chat_ids", previousDeleted).putString("deleted_chat_owner", previousDeletionOwner).apply()
                        }
                    }
                    if (!saved) error("Synced chats could not be saved. Local data was kept.")
                    bindCloudHistory(chats, snapshot.deletedChatIds, selectedId)
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
    private fun liveProjectBusy() = _liveStart.value.busy || com.dua.ai.voice.live.LiveVoiceController.active
    private fun syncBlocked() = account.state.value.busy || _dataBusy.value || _isTyping.value || liveProjectBusy() ||
        _cloudPreview.value != null || _importPreview.value != null || _buildPr.value.values.any { it.creating }
    private fun refreshSyncBusy() = automaticSync.setBusy(syncBlocked())
    private val networkMonitor: com.dua.ai.network.NetworkMonitor = com.dua.ai.network.ConnectivityManagerNetworkMonitor(application)
    private val offlineRetry = com.dua.ai.network.OfflineRetry()
    private var autoRetrying = false
    private fun setNetwork(online: Boolean) {
        _online.value = online
        automaticSync.setOnline(online)
        val resend = offlineRetry.onConnectivity(online, activeId, _draft.value, busy = _isTyping.value || _preparingFiles.value || _readingFiles.value)
        if (resend != null) {
            autoRetrying = true
            try { sendMessage(resend) } finally { autoRetrying = false }
        }
    }

    init {
        driveBackups.bind(this) {
            val snapshot = try { fullBackupPort.begin() }
            catch (failure: IllegalStateException) { throw com.dua.ai.backup.drive.DriveFailure(failure.message ?: "Finish the current app action before backing up.", retryable = true) }
            try { snapshot } finally { fullBackupPort.end() }
        }
        preferences.getString("history", null)?.let { raw ->
            try {
                val (id, records) = com.dua.ai.history.ChatHistoryPreload.take(raw)
                _deletedChatIds.value = com.dua.ai.account.CloudDeletionIds.decode(preferences.getString("deleted_chat_ids", null)?.let { org.json.JSONArray(it) })
                deletionOwnerId = preferences.getString("deleted_chat_owner", null)
                activeId = if (id in currentDeletionIds()) UUID.randomUUID().toString() else id
                val visible = records.filterNot { it.id in currentDeletionIds() }
                _savedChats.value = visible
                visible.find { it.id == activeId }?.let {
                    _messages.value = it.messages
                    _selectedModel.value = it.model
                    _draft.value = it.draft
                    _summary.value = it.summary
                    _buildRepo.value = it.buildRepository
                    _projectId.value = it.projectId
                }
            } catch (_: Exception) {
                historyReadable = false
                preferences.edit().putString("unreadable_history_backup", raw).apply()
                _error.value = "Saved chats could not be loaded. Stored data has not been deleted."
            }
        }
        refreshCloudImagine()
        setNetwork(networkMonitor.currentlyOnline())
        viewModelScope.launch { networkMonitor.isOnline.collect { setNetwork(it) } }
        viewModelScope.launch {
            combine(account.state, _dataBusy, _isTyping, _cloudPreview, _importPreview) { state, dataBusy, typing, cloud, imported ->
                state.busy || dataBusy || typing || cloud != null || imported != null
            }.distinctUntilChanged().collect { refreshSyncBusy() }
        }
        viewModelScope.launch { account.state.map { it.user?.id }.distinctUntilChanged().collect { driveBackups.accountChanged(it); automaticSync.accountChanged(); resumeImagineJobs() } }
        viewModelScope.launch {
            _buildPr.map { states -> states.values.any { it.creating } }.distinctUntilChanged().collect { refreshSyncBusy() }
        }
        viewModelScope.launch {
            combine(_liveStart, com.dua.ai.voice.live.LiveVoiceController.state) { starting, live -> starting.busy || live.active }
                .distinctUntilChanged().collect { refreshSyncBusy() }
        }
        viewModelScope.launch {
            combine(_savedChats, localSettings, _deletedChatIds) { chats, settings, deleted -> CloudSnapshot(chats, settings.memory, settings.projects, deleted) }
                .distinctUntilChanged().collect { automaticSync.localChanged() }
        }
        automaticSync.restore()
    }

    override fun onCleared() {
        driveBackups.unbind(this)
        if (fullBackupDelegate.isInitialized()) fullBackup.close()
        flushHistory()
        recorder.cancel()
        pollJobs.values.forEach { it.cancel() }
        automaticSync.close()
        super.onCleared()
    }

    private fun saveHistory() {
        draftSaver.cancel()
        val previous = _savedChats.value.find { it.id == activeId }
        val others = _savedChats.value.filterNot { it.id == activeId }
        _savedChats.value = if (_messages.value.isEmpty() && _draft.value.isBlank() && previous?.customTitle == null && previous?.pinned != true) others else
            listOf(com.dua.ai.history.ChatSavePolicy.preserveUnchangedTime(previous,
                SavedChat(activeId, _selectedModel.value, _draft.value, _messages.value, System.currentTimeMillis(),
                    com.dua.ai.model.ContextWindow.validSummary(_messages.value, _summary.value), _buildRepo.value, _projectId.value,
                    previous?.customTitle, previous?.pinned ?: false))) + others
        historyWriter.save(_savedChats.value, activeId)
    }

    /** Typing updates the draft at once; saving it (list update + disk) is debounced so each keystroke stays cheap. */
    fun updateDraft(value: String) {
        _draft.value = value
        draftSaver.schedule(::saveHistory)
    }

    /** Saves a pending draft and writes queued history now (app stopped, ViewModel cleared, before transactional writes). */
    fun flushHistory() {
        draftSaver.runNow()
        historyWriter.flush()
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

    /** Returns an actionable error, or null after queuing the renamed chat for the existing sync flow. */
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

    /**
     * Auto title (ChatGPT-style): after the first reply of a new chat, ask the Cloud Run engine for a 3–6 word
     * title. Only when chat goes to that engine and the backend accepts titles; any failure keeps the
     * default title (the first message). A title the user set themselves is never replaced.
     */
    private fun maybeAutoTitle(chatId: String) {
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

    /** Pin is chat metadata, queued for the existing reviewed/automatic sync flow. */
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

    /** Apply authoritative deletion IDs after persistence, including the active chat's draft buffers. */
    private fun bindCloudHistory(chats: List<SavedChat>, deleted: List<String>, selectedId: String = activeId) {
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

    fun dismissChatDeletion() { if (!_chatDeletion.value.busy) _chatDeletion.value = ChatDeletionState() }
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

    private suspend fun applyConfirmedChatDeletion(result: com.dua.ai.account.CloudChatDeletion) {
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
        if (_dataBusy.value) return
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

    val promptSuggestions = listOf(
        PromptSuggestion(
            title = "Write code snippet",
            prompt = "How do I build an elegant, modern Card component in Jetpack Compose?",
            category = "Coding"
        ),
        PromptSuggestion(
            title = "Brainstorm concepts",
            prompt = "Give me 4 unique mobile app ideas leveraging modern AI and edge computing.",
            category = "Idea"
        ),
        PromptSuggestion(
            title = "Explain simply",
            prompt = "Explain how transformer neural networks generate text in simple terms.",
            category = "Learning"
        ),
        PromptSuggestion(
            title = "Draft a plan",
            prompt = "Draft a 4-week learning roadmap to master Kotlin and Jetpack Compose.",
            category = "Productivity"
        )
    )

    fun setModel(model: String) {
        if (api.chatProvider(model, _integrations.value.providers) == null) return
        if (localSettings.value.freeModel.isNotEmpty()) updateSettings(localSettings.value.copy(freeModel = ""))
        _selectedModel.value = model
        if (model != "Grok") _xSearch.value = false
        if (!codeUsable(model)) _runCode.value = false
        if (!buildUsable(model)) _buildRepo.value = null
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

    fun retryResponse(messageId: String) {
        val requestProjectId = _projectId.value
        projectUnavailable()?.let { _error.value = it; return }
        if (_isTyping.value || _preparingFiles.value || _readingFiles.value || _dataBusy.value) return
        val original = _messages.value.lastOrNull() ?: return
        if (original.id != messageId || original.isUser || original.state !in listOf("complete", "stopped") || original.isImagine) return
        val plan = try { com.dua.ai.model.ReplyRetryPlan.create(_messages.value, messageId) }
            catch (failure: IllegalArgumentException) { _error.value = failure.message; return }
        personalityUnavailable()?.let { _error.value = it; return }
        // Freeze this request's style before file preparation or asynchronous project loading.
        val requestPersonality = appearance.value.personality?.instruction
        // A restored reply has no saved mode and retries with web off. In this session,
        // retries keep the original request's choice even if the composer has changed.
        val requestWebMode = replyWebModes[messageId]
        val requestAgent = (replyAgent[messageId] ?: false) && _agentAvailable.value
        val requestReasoning = !requestAgent && (replyReasoning[messageId] ?: (original.thinking != null))
        val requestXSearch = (replyXSearch[messageId] ?: false) && xSearchUsable(plan.model)
        val requestCode = (replyCode[messageId] ?: original.codeRuns.isNotEmpty()) && codeUsable(plan.model)
        // A Build reply retries in Build mode against the same repository.
        val requestBuild = replyBuild[messageId] ?: original.build?.repository
        if (requestBuild != null) (buildUnavailableReason() ?: if (!buildUsable(plan.model)) "Build mode works with Dua or Gemini." else null)?.let { _error.value = it; return }
        webModeError(requestWebMode, plan.model)?.let { _error.value = it; return }
        val request = requestTurns(plan.full, plan.context, inlineFiles = separateChatEngine && (plan.full.last().inlineAttachments.isNotEmpty() || localSettings.value.projects.find { it.id == _projectId.value }?.sources?.isNotEmpty() == true))
        val persistenceBase = _messages.value.toList()
        val persistenceOwner = account.state.value.user?.id.takeIf { _integrations.value.serverRepliesEnabled && !separateChatEngine }
        val currentGeneration = generation
        val startedAt = System.currentTimeMillis()
        _error.value = null; _isTyping.value = true; refreshSyncBusy()
        retryOriginal = original
        updateMessage(messageId) { it.copy(text = "", thinking = null, thinkingSeconds = null, notice = null, codeRuns = emptyList(), build = requestBuild?.let { com.dua.ai.model.BuildInfo(it) }, proposal = null, approvals = emptyList(), state = ChatMessage.STREAMING, startedAt = startedAt) }
        replyJob = viewModelScope.launch {
            var accumulator: com.dua.ai.network.StreamAccumulator? = null
            try {
                val persistence = persistenceOwner?.let { owner -> withContext(Dispatchers.Default) {
                    com.dua.ai.account.ReplyPersistenceCodec.create(activeId, owner, persistenceBase, plan.full.last(), messageId,
                        request.turns.filter { it.state == "complete" }.takeLast(if (request.context != null) 400 else 39), plan.full.last().text, replacing = true)
                } }
                val projectContext = engineProjectContext(requestProjectId)
                account.withToken { token ->
                    check(projectContext.ownerId == null || account.state.value.user?.id == projectContext.ownerId) { "Account changed. Retry the project chat." }
                    val attempt = com.dua.ai.network.StreamAccumulator(startedAt).also { accumulator = it }
                    collectReply(messageId, attempt, api.streamReply(plan.model, request.turns, (plan.full.last().inlineAttachments + projectContext.attachments), projectContext.memory, token, requestWebMode.takeIf { requestBuild == null }, requestReasoning && requestBuild == null,
                        request.historyBytes, request.context, requestXSearch && requestBuild == null, requestCode && requestBuild == null, requestBuild, persistence, agent = requestAgent && requestBuild == null, freeModel = requestFreeModel(), personality = requestPersonality), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    val final = accumulator!!.snapshot(ended = true)
                    retryOriginal = null
                    updateMessage(messageId) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, codeRuns = final.codeRuns, build = final.build ?: it.build, proposal = final.proposal ?: it.proposal, approvals = final.approvals.ifEmpty { it.approvals }, state = "complete") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                com.dua.ai.diagnostics.DuaLog.soft(com.dua.ai.diagnostics.SoftErrorArea.ChatRetry, failure)
                if (generation == currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    retryOriginal = null
                    if (partial != null && partial.text.isNotBlank()) {
                        updateMessage(messageId) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, codeRuns = partial.codeRuns, build = partial.build ?: it.build, proposal = partial.proposal ?: it.proposal, approvals = partial.approvals.ifEmpty { it.approvals }, state = "stopped") }
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
        if (_preparingFiles.value) return
        if (!autoRetrying) offlineRetry.clear()
        val requestProjectId = _projectId.value
        projectUnavailable()?.let { _error.value = it; return }
        if (_imagine.value && _editingId.value != null) { _error.value = "Turn off Imagine to resend an edited message."; return }
        if (_imagine.value) { imagineFeature.sendImagine(userText); return }
        val storedFiles = _uploads.value.filter { it.state == PendingUpload.DONE }.mapNotNull { it.ref }
        if (_editingId.value != null && (_attachments.value.isNotEmpty() || storedFiles.isNotEmpty())) { _error.value = "Edited messages are text only. Remove the attachments or cancel editing."; return }
        if ((userText.isBlank() && _attachments.value.isEmpty() && storedFiles.isEmpty()) || _isTyping.value || _readingFiles.value) return
        if (_uploads.value.any { it.uploading } || _recordingSince.value != null) { _error.value = "Wait for uploads to finish, or remove them."; return }
        _error.value = null
        if (userText.length > 8000) {
            _error.value = "Message 8000 characters se chhota rakhein."
            return
        }
        personalityUnavailable()?.let { _error.value = it; return }
        // Freeze this request's style before file preparation or asynchronous project loading.
        val requestPersonality = appearance.value.personality?.instruction
        val requestWebMode = _webMode.value
        val requestAgent = _agent.value && _agentAvailable.value
        val requestReasoning = _reasoning.value && !requestAgent
        val responseModel = _selectedModel.value
        val requestXSearch = _xSearch.value && xSearchUsable(responseModel)
        val requestCode = _runCode.value && codeUsable(responseModel)
        val requestBuild = _buildRepo.value
        if (requestBuild != null) {
            (buildUnavailableReason() ?: if (!buildUsable(responseModel)) "Build mode works with Dua or Gemini. Choose one of them or turn Build off." else null)?.let { _error.value = it; return }
            if (_attachments.value.isNotEmpty() || storedFiles.isNotEmpty()) { _error.value = "Build mode reads the repository, not attachments. Remove them or turn Build off."; return }
        }
        webModeError(requestWebMode, responseModel)?.let { _error.value = it; return }
        val provider = api.chatProvider(responseModel, _integrations.value.providers)
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
        if (api.usesInlineChatFiles(false) && storedFiles.any { it.transcript.isNullOrBlank() }) {
            _error.value = "Cloud Run cannot read stored file references. Remove these files and select them again for inline chat (2 MB total); use CSV for spreadsheets."
            return
        }
        // An edit replaces the edited message and later turns; the server copy is refreshed by the normal chat sync.
        val edited = applyEdit()
        val userMessage = ChatMessage(text = text, isUser = true, model = responseModel, attachmentText = fileText, files = storedFiles,
            inlineAttachments = if (separateChatEngine) files else emptyList())
        val sentPaths = storedFiles.map { it.path }.toSet()
        val startedAt = System.currentTimeMillis()
        val reply = ChatMessage(text = "", isUser = false, model = responseModel, state = ChatMessage.STREAMING, startedAt = startedAt,
            build = requestBuild?.let { com.dua.ai.model.BuildInfo(it) })
        val request = requestTurns(_messages.value + userMessage, _messages.value.takeLast(38) + userMessage, inlineFiles = files.isNotEmpty() || (separateChatEngine && localSettings.value.projects.find { it.id == _projectId.value }?.sources?.isNotEmpty() == true))
        val persistenceBase = _messages.value.toList()
        // Server reply saving appends to the stored conversation, so an edited (shortened) conversation skips it and syncs as a snapshot.
        val persistenceOwner = account.state.value.user?.id.takeIf { _integrations.value.serverRepliesEnabled && !separateChatEngine && !edited }
        val currentGeneration = generation
        replyWebModes[reply.id] = requestWebMode
        replyXSearch[reply.id] = requestXSearch
        replyReasoning[reply.id] = requestReasoning
        replyAgent[reply.id] = requestAgent
        replyCode[reply.id] = requestCode
        requestBuild?.let { replyBuild[reply.id] = it }
        _messages.value = _messages.value + userMessage + reply
        _draft.value = ""
        saveHistory()
        _isTyping.value = true; refreshSyncBusy()
        _failedPrompt.value = null
        replyJob = viewModelScope.launch {
            var accumulator: com.dua.ai.network.StreamAccumulator? = null
            try {
                val ownFiles = if (files.any(convert)) withContext(Dispatchers.Default) { PhotoAttachmentReader.convertToJpeg(files, convert) } else files
                val projectContext = engineProjectContext(requestProjectId)
                val prepared = ownFiles + projectContext.attachments
                check(prepared.size <= 3 || !separateChatEngine) { "Use at most 3 files across project sources and this message." }
                if (separateChatEngine) check(prepared.sumOf { java.util.Base64.getDecoder().decode(it.data).size.toLong() } <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Project sources and message attachments must fit within 2 MB total." }
                val persistence = persistenceOwner?.let { owner -> withContext(Dispatchers.Default) {
                    com.dua.ai.account.ReplyPersistenceCodec.create(activeId, owner, persistenceBase, userMessage, reply.id,
                        request.turns.filter { it.state == "complete" }.takeLast(if (request.context != null) 400 else 39), userText)
                } }
                account.withToken { token ->
                    val attempt = com.dua.ai.network.StreamAccumulator(startedAt).also { accumulator = it }
                    check(projectContext.ownerId == null || account.state.value.user?.id == projectContext.ownerId) { "Account changed. Retry the project chat." }
                    collectReply(reply.id, attempt, api.streamReply(responseModel, request.turns, prepared,
                        projectContext.memory, token, requestWebMode.takeIf { requestBuild == null }, requestReasoning && requestBuild == null,
                        request.historyBytes, request.context, requestXSearch && requestBuild == null, requestCode && requestBuild == null, requestBuild, persistence, agent = requestAgent && requestBuild == null, freeModel = requestFreeModel(), personality = requestPersonality), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    _uploads.update { list -> list.filterNot { it.ref?.path in sentPaths } }
                    clearAttachments()
                    val final = accumulator!!.snapshot(ended = true)
                    updateMessage(reply.id) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, codeRuns = final.codeRuns, build = final.build ?: it.build, proposal = final.proposal ?: it.proposal, approvals = final.approvals.ifEmpty { it.approvals }, state = "complete") }
                    offlineRetry.delivered()
                    maybeAutoTitle(activeId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                com.dua.ai.diagnostics.DuaLog.soft(com.dua.ai.diagnostics.SoftErrorArea.ChatReply, failure)
                if (generation == currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    if (partial != null && partial.text.isNotBlank()) {
                        // The request reached the model: keep the turn and its partial reply; Retry regenerates it.
                        _uploads.update { list -> list.filterNot { it.ref?.path in sentPaths } }
                        clearAttachments()
                        updateMessage(reply.id) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, codeRuns = partial.codeRuns, build = partial.build ?: it.build, proposal = partial.proposal ?: it.proposal, approvals = partial.approvals.ifEmpty { it.approvals }, state = "stopped") }
                        _error.value = "Reply interrupted: ${failureText(failure)} Partial reply kept; tap retry."
                    } else {
                        _messages.value = _messages.value.filterNot { it.id == userMessage.id || it.id == reply.id }
                        replyWebModes.remove(reply.id); replyReasoning.remove(reply.id); replyAgent.remove(reply.id); replyCode.remove(reply.id); replyBuild.remove(reply.id)
                        _draft.value = userText
                        _failedPrompt.value = userText
                        // Offline, text-only, not an edit: send it again once the phone reconnects (at most once).
                        val queued = offlineRetry.sendFailed(activeId, userText,
                            offline = !edited && files.isEmpty() && storedFiles.isEmpty() && (!_online.value || failure is java.io.IOException))
                        _error.value = if (queued) "You're offline. Dua will send this message when you're back online." else failureText(failure)
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

    /** Imagine settings the chat shows: the backend's, plus Google Cloud when the engine offers it. */
    val imagineSettings: StateFlow<com.dua.ai.imagine.ImagineConfig?> by lazy {
        combine(_integrations, _cloudImagine) { state, cloud -> com.dua.ai.imagine.CloudImagine.merge(state.imagine, cloud) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, imagineFeature.imagineConfig())
    }



    /** Imagine chip: turns the composer into image/video generation (Reasoning, Search and X off). */
    fun setImagine(enabled: Boolean) = imagineFeature.setImagine(enabled)
    fun setImagineOptions(options: com.dua.ai.imagine.ImagineOptions) = imagineFeature.setImagineOptions(options)
    /** Images destination reuses Imagine's auth, capability, upload and generation guards. Never falls back to text chat. */
    fun generateImageFromHub(prompt: String, aspect: String): Boolean = imagineFeature.generateImageFromHub(prompt, aspect)
    /** Starts polling every pending video job found in saved chats (after a restart or sign-in). */
    fun resumeImagineJobs() = imagineFeature.resumeImagineJobs()
    /** Stops waiting for a video. xAI has no cancel call, so the job may still finish (and be billed); it will not be saved. */
    fun cancelVideo(messageId: String) = imagineFeature.cancelVideo(messageId)
    /** "Edit" / "Animate": puts a generated image into the composer as the Imagine source. */
    fun useGeneratedImage(media: com.dua.ai.imagine.GeneratedMedia, kind: String = com.dua.ai.imagine.ImagineInfo.IMAGE) = imagineFeature.useGeneratedImage(media, kind)
    fun loadImagineUsage() = imagineFeature.loadImagineUsage()

    /** Short-lived signed link for a stored path (owner checked on the server). */
    suspend fun storedFileUrl(path: String): String {
        val json = account.request("/api/files/url", "POST", org.json.JSONObject().put("path", path), authenticated = true)
        return com.dua.ai.setup.IntegrationCapabilities.safeUrl(json.optString("url")) ?: error("File link is invalid. Try again.")
    }

    private companion object {
        const val STREAM_FRAME_MS = 24L
        const val EMPTY_REPLY = "Model ne text reply nahi diya. Dobara try karein."
        const val MAX_UPLOADS = 10
        /** Draft typing is saved this long after the last keystroke (and at once on stop or send). */
        const val DRAFT_SAVE_DELAY_MS = 500L
    }
}
