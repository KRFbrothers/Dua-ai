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
    fun loadProviderConfiguration() = viewModelScope.launch {
        refreshEngineCapabilities()
        try {
            val config = setupApi.request("/api/config")
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
            filesEnabled = current.filesEnabled, filesMaxBytes = current.filesMaxBytes, nativeSearch = current.nativeSearch,
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
    fun signInWithGoogle(idToken: String, nonce: String, photoUrl: String = "", expectedEmail: String? = null) {
        if (_dataBusy.value) { googleSignInFailed("Wait for the current data operation to finish, then try again."); return }
        _googleSignIn.value = GoogleSignInState(busy = true)
        account.busy(true)
        automaticSync.setBusy(true)
        viewModelScope.launch {
            try {
                _cloudPreview.value = null
                account.signInWithGoogle(idToken, nonce, photoUrl, expectedEmail)
                _googleSignIn.value = GoogleSignInState()
            } catch (cancelled: CancellationException) { _googleSignIn.value = GoogleSignInState(); throw cancelled }
            catch (failure: Exception) { googleSignInFailed(failure.message ?: "Google sign-in failed. Try again.") }
            finally { account.busy(false); refreshSyncBusy() }
        }
    }
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
    fun submitEmailAuth(email: String, password: String, signup: Boolean, expectedAccountId: String? = null) {
        if (_emailAuth.value.busy) return
        if (account.state.value.busy || _googleSignIn.value.busy || syncBlocked()) {
            _emailAuth.value = EmailAuthState(error = "Finish the current account, reply or sync action, then try again."); return
        }
        if (!account.state.value.emailAuthConfigured) {
            _emailAuth.value = EmailAuthState(error = "Refresh Account after updating the owner backend to enable email authentication."); return
        }
        _emailAuth.value = EmailAuthState(busy = true); account.busy(true); automaticSync.setBusy(true)
        emailAuthJob = viewModelScope.launch {
            try {
                _cloudPreview.value = null
                val completed = if (expectedAccountId != null) { account.setEmailPassword(password, expectedAccountId); true }
                    else account.signInWithEmail(email, password, signup)
                _emailAuth.value = EmailAuthState(completed = completed, confirmation = if (completed) "" else account.state.value.notice)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _emailAuth.value = EmailAuthState(error = failure.message ?: "Email authentication failed. Try again.") }
            finally { account.busy(false); refreshSyncBusy() }
        }
    }
    /** Runs in the Account dialog's scope; cancellation cancels the request and leaves the last confirmed profile intact. */
    suspend fun setAccountPhoneNumber(value: String, expectedAccountId: String) {
        check(!account.state.value.busy && !_googleSignIn.value.busy && !syncBlocked()) { "Finish the current account, reply or sync action, then try again." }
        account.busy(true); automaticSync.setBusy(true)
        try { account.setPhoneNumber(value, expectedAccountId) }
        finally { account.busy(false); refreshSyncBusy() }
    }
    fun logOut() { automaticSync.disable(); accountAction { _cloudPreview.value = null; account.logout() } }
    fun cancelCloudReview() { _cloudPreview.value = null; account.notice("Cancelled. Data was not changed.") }
    fun reviewCloud(kind: String) = accountAction {
        val remote = account.cloud()
        draftSaver.runNow()
        val snapshot = if (kind == "upload") com.dua.ai.account.CloudSnapshot(_savedChats.value.toList(), localSettings.value.memory.toList(), localSettings.value.projects.toList(), currentDeletionIds()) else remote.snapshot
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
            "delete" -> { account.upload(preview.review, clear = true); account.notice("Cloud chat and note contents cleared. Deletion IDs are kept to block stale uploads; the phone cache stays unchanged.") }
            "download" -> downloadCloudSnapshot(preview.review.snapshot)
        }
        _cloudPreview.value = null
        } finally { _dataBusy.value = false }
    }

    /** Prepare off Main, then commit without suspension after checking that the cache is still current. */
    private suspend fun downloadCloudSnapshot(remote: com.dua.ai.account.CloudSnapshot) {
        check(!_isTyping.value && !liveProjectBusy() && _buildPr.value.values.none { it.creating }) { "Wait for the reply, live voice or pull request to finish before downloading." }
        flushHistory()
        val oldSettings = localSettings.value
        val local = com.dua.ai.account.CloudSnapshot(_savedChats.value.toList(), oldSettings.memory.toList(), oldSettings.projects.toList(), currentDeletionIds())
        val selectedId = activeId
        val (result, encoded) = withContext(Dispatchers.Default) {
            val merged = com.dua.ai.account.CloudSnapshotCodec.mergeDownload(local, remote)
            merged to ChatHistoryCodec.encode(merged.snapshot.chats, selectedId)
        }
        check(activeId == selectedId && _savedChats.value == local.chats && localSettings.value == oldSettings &&
            !_isTyping.value && _buildPr.value.values.none { it.creating } && !draftSaver.pending) {
            "The phone cache changed while preparing the download. Review and retry. No downloaded data was applied."
        }
        val chats = result.snapshot.chats
        val previous = preferences.getString("history", null)
        localPreferences.update(oldSettings.copy(memory = result.snapshot.memory, projects = result.snapshot.projects))
        try {
            val committed = historyWriter.writeDirectly {
                val previousDeleted = preferences.getString("deleted_chat_ids", null)
                val previousOwner = preferences.getString("deleted_chat_owner", null)
                preferences.edit().putString("history", encoded).putString("deleted_chat_ids", org.json.JSONArray(result.snapshot.deletedChatIds).toString())
                    .putString("deleted_chat_owner", account.state.value.user?.id).commit().also { ok ->
                    if (!ok) preferences.edit().putString("history", previous).putString("deleted_chat_ids", previousDeleted).putString("deleted_chat_owner", previousOwner).apply()
                }
            }
            check(committed) { "Cloud chats could not be saved. Retry." }
        } catch (failure: Exception) {
            try { localPreferences.update(oldSettings) } catch (_: Exception) {
                throw IllegalStateException("Download interrupted and settings rollback failed. Inspect cached memories and projects before retrying.", failure)
            }
            throw failure
        }
        bindCloudHistory(chats, result.snapshot.deletedChatIds)
        _webMode.value = null
        _xSearch.value = false
        account.notice("${result.added} server chats added; ${result.conflicts} phone versions kept as unsynced copies. Server memories and project settings restored.")
    }
    /** Chat goes to DUA_CHAT_BASE_URL with the owner session; separate-engine server saving remains disabled. */
    private val separateChatEngine = com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL.trimEnd('/') != com.dua.ai.BuildConfig.DUA_API_BASE_URL.trimEnd('/')
    private val api = DuaApiClient(com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL, appToken = com.dua.ai.BuildConfig.DUA_APP_TOKEN,
        apiBaseUrl = com.dua.ai.BuildConfig.DUA_API_BASE_URL)

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
    private val appearanceStore = com.dua.ai.settings.AppearanceStore(application)
    val appearance = appearanceStore.data.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, appearanceStore.initial)
    val appearanceNotice = appearanceStore.notice
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
        refreshSyncNetwork()
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
            updateMessage(id) { it.copy(text = snapshot.text, thinking = snapshot.thinking, thinkingSeconds = snapshot.thinkingSeconds, notice = snapshot.notice, codeRuns = snapshot.codeRuns, build = snapshot.build ?: it.build, proposal = snapshot.proposal ?: it.proposal) }
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
    fun setReasoning(enabled: Boolean) { if (!_isTyping.value) { _reasoning.value = enabled; if (enabled) { _imagine.value = false; _agent.value = false; leaveBuild() } } }
    // Dua Agent (phase 1, Cloud Run engine only): plan -> Google Search research -> summary with sources -> feature ideas.
    // Per-session like Reasoning and never saved; it replaces the other modes for the next reply.
    private val _agent = MutableStateFlow(false)
    val agent = _agent.asStateFlow()
    private val replyAgent = mutableMapOf<String, Boolean>()
    /** Agent mode is offered only when the chat engine advertises `"agent": true` on GET / (unknown or failure = hidden). */
    private val _agentAvailable = MutableStateFlow(false)
    val agentAvailable = _agentAvailable.asStateFlow()
    private var engineCheck: kotlinx.coroutines.Job? = null
    fun refreshEngineCapabilities() {
        if (_agentAvailable.value || engineCheck?.isActive == true) return
        engineCheck = viewModelScope.launch {
            val available = try { api.engineAgentAvailable() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            _agentAvailable.value = available
            if (!available) _agent.value = false
        }
    }
    fun setAgent(enabled: Boolean) {
        if (_isTyping.value) return
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
    fun inlineChatFilesAvailable() = api.usesInlineChatFiles(_imagine.value)
    private fun maxStoredBytes() = _integrations.value.filesMaxBytes.takeIf { it > 0 } ?: (50L * 1024 * 1024)
    fun storedFilesHint(): String = when {
        inlineChatFilesAvailable() -> "Cloud Run chat sends up to 3 files inline (2 MB total). Use CSV or UTF-8 text for spreadsheets; reselect media after restoring a chat."
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

    private val _importPreview = MutableStateFlow<List<SavedChat>?>(null)
    private var importProjects = com.dua.ai.projects.ProjectCodec.defaults
    val importPreview: StateFlow<List<SavedChat>?> = _importPreview.asStateFlow()
    fun cancelImport() { if (!_dataBusy.value) { _importPreview.value = null; _dataStatus.value = "Import cancelled. Your chats are unchanged." } }

    fun reviewImport(uri: Uri) {
        if (_dataBusy.value) return
        _importPreview.value = null
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Checking export…"
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Selected file cannot be read.")
                    input.use { ChatHistoryCodec.readBackup(it) }
                }
                importProjects = backup.projects
                if (backup.chats.isEmpty() && backup.projects == com.dua.ai.projects.ProjectCodec.defaults) _dataStatus.value = "This export has no chats. Nothing was changed."
                else { _importPreview.value = backup.chats; _dataStatus.value = "Export checked. Confirm to add chats and their project settings." }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _dataStatus.value = error.message ?: "Export could not be read. No chats were changed." }
            finally { _dataBusy.value = false }
        }
    }

    fun confirmImport() {
        if (_dataBusy.value) return
        if (liveProjectBusy()) { _dataStatus.value = "End live voice before importing chats."; return }
        val imported = _importPreview.value ?: return
        if (_buildPr.value.values.any { it.creating }) { _dataStatus.value = "Wait for the pull request to finish before importing."; return }
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Saving imported chats…"
        cancelReply()
        flushHistory()
        // Use the latest local records at confirmation, keeping the active draft/selection.
        val original = _savedChats.value.toList()
        val originalProjects = localSettings.value.projects.toList()
        val importedProjects = importProjects.toList()
        val selectedId = activeId
        viewModelScope.launch {
            var projectsWritten = false
            try {
                val (merged, encoded, mergedProjects) = withContext(Dispatchers.Default) {
                    val adjusted = com.dua.ai.projects.ProjectImports.prepare(originalProjects, com.dua.ai.history.ChatBackup(imported, importedProjects))
                    val chats = ChatHistoryCodec.importCopies(original, adjusted.chats, projects = adjusted.projects)
                    Triple(chats, ChatHistoryCodec.encode(chats, selectedId), adjusted.projects)
                }
                fun cacheUnchanged() = activeId == selectedId && _savedChats.value == original && !draftSaver.pending &&
                    localSettings.value.projects == (if (projectsWritten) mergedProjects else originalProjects) && !_isTyping.value && _buildPr.value.values.none { it.creating }
                val changed = "The phone cache changed while preparing the import. Review and retry. No chats were imported."
                check(cacheUnchanged()) { changed }
                localPreferences.update(localSettings.value.copy(projects = mergedProjects))
                projectsWritten = true
                val committed = withContext(Dispatchers.IO) { historyWriter.writeDirectly {
                    val previous = preferences.getString("history", null)
                    val success = preferences.edit().putString("history", encoded).commit()
                    if (!success) preferences.edit().putString("history", previous).apply()
                    success
                } }
                if (!cacheUnchanged()) {
                    // Main remained responsive during the write. Retire the stale disk import by
                    // queuing the latest live buffers (including pending typing), then flush off Main.
                    saveHistory()
                    withContext(Dispatchers.IO) { historyWriter.flush() }
                    error(changed)
                }
                if (committed) {
                    _savedChats.value = merged
                    projectsWritten = false
                    _importPreview.value = null
                    _dataStatus.value = "${imported.size} chats imported into the phone cache. Sign in and sync to make them server-backed. Existing chats and drafts were kept."
                } else _dataStatus.value = "Import could not be saved. No chats were changed. Try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _dataStatus.value = failure.message ?: "Import could not be saved. No chats were changed. Try again." }
            finally {
                if (projectsWritten) runCatching { localPreferences.update(localSettings.value.copy(projects = originalProjects)) }
                _dataBusy.value = false
            }
        }
    }

    fun exportChats(uri: Uri) {
        if (_dataBusy.value) return
        draftSaver.runNow()
        val records = _savedChats.value.toList()
        val projects = localSettings.value.projects.toList()
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Creating export…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val encoded = ChatHistoryCodec.exportBackup(records, projects = projects).toByteArray(Charsets.UTF_8)
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("Selected file cannot be written.")
                    output.use { it.write(encoded) }
                }
                _dataStatus.value = "Export saved. Your chats are unchanged."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IllegalArgumentException) { _dataStatus.value = "Export exceeds backup limits or contains invalid metadata. No backup was written. ${failure.message.orEmpty()}" }
            catch (_: Exception) { _dataStatus.value = "Export failed. The selected file may be incomplete; try again." }
            finally { _dataBusy.value = false }
        }
    }

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

    fun selectProject(id: String) {
        if (syncBlocked() || _readingFiles.value || _uploads.value.isNotEmpty() || _attachments.value.isNotEmpty() || _recordingSince.value != null) {
            _projectNotice.value = "Finish the current action and send or remove pending attachments before switching projects."; return
        }
        if (localSettings.value.projects.none { it.id == id && !it.archived }) return
        if (id == _projectId.value) return
        startNewChat()
        _projectId.value = id
        _projectNotice.value = ""
    }

    private fun commitProjects(projects: List<com.dua.ai.projects.ChatProject>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing projects."; return false }
        return try {
            localPreferences.requireReadable()
            com.dua.ai.projects.ProjectCodec.validate(projects)
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, localSettings.value.memory, projects))
            localPreferences.update(localSettings.value.copy(projects = projects))
            _projectNotice.value = "Project saved. Changes follow your account through Cloud Sync."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Project could not be saved."; false }
    }

    fun saveProject(id: String?, name: String, instructions: String, archived: Boolean): Boolean {
        val existing = localSettings.value.projects.find { it.id == id }
        if (id != null && existing == null) { _projectNotice.value = "Project changed. Reopen the editor."; return false }
        val now = System.currentTimeMillis()
        val original = existing ?: com.dua.ai.projects.ChatProject(UUID.randomUUID().toString(), name.trim(),
            details = com.dua.ai.projects.ProjectDetails(now, now))
        val updated = original.copy(name = name.trim(), instructions = instructions.trim(), archived = archived,
            details = original.details?.copy(updatedAt = now))
        return commitProjects(if (existing == null) localSettings.value.projects + updated else localSettings.value.projects.map { if (it.id == id) updated else it })
    }

    fun createProject(name: String, instructions: String, icon: String, color: String): String? {
        val now = System.currentTimeMillis()
        val project = com.dua.ai.projects.ChatProject(UUID.randomUUID().toString(), name.trim(), instructions.trim(),
            details = com.dua.ai.projects.ProjectDetails(now, now, icon, color))
        return project.id.takeIf { commitProjects(localSettings.value.projects + project) }
    }
    fun updateProjectPresentation(id: String, name: String, instructions: String, icon: String, color: String): Boolean =
        changeProject(id) { p -> p.copy(name = name.trim(), instructions = instructions.trim(),
            details = projectDetails(p).copy(icon = icon, color = color, updatedAt = System.currentTimeMillis())) }
    private fun projectDetails(project: com.dua.ai.projects.ChatProject) = project.details ?:
        com.dua.ai.projects.ProjectDetails(_savedChats.value.filter { it.projectId == project.id }.minOfOrNull { it.updatedAt } ?: System.currentTimeMillis(), System.currentTimeMillis())
    private fun changeProject(id: String, change: (com.dua.ai.projects.ChatProject) -> com.dua.ai.projects.ChatProject): Boolean {
        val project = localSettings.value.projects.find { it.id == id && it.details?.deleted != true }
            ?: run { _projectNotice.value = "Project is no longer available."; return false }
        return try { commitProjects(localSettings.value.projects.map { if (it.id == id) change(project) else it }) }
        catch (failure: Exception) { _projectNotice.value = failure.message ?: "Project could not be saved."; false }
    }
    fun pinProject(id: String): Boolean = changeProject(id) { p ->
        val details = projectDetails(p); p.copy(details = details.copy(pinned = !details.pinned, updatedAt = System.currentTimeMillis()))
    }
    /** Retain the authoritative definition and chats/files; hidden tombstones cannot be resurrected by stale clients. */
    fun deleteProject(id: String): Boolean {
        if (id == com.dua.ai.projects.ProjectCodec.PERSONAL) return false
        val saved = changeProject(id) { p -> p.copy(archived = true, details = projectDetails(p).copy(deleted = true, pinned = false, updatedAt = System.currentTimeMillis())) }
        if (saved && _projectId.value == id) { startNewChat(); selectAuto() }
        return saved
    }
    fun removeProjectSource(projectId: String, path: String): Boolean = changeProject(projectId) { p ->
        p.copy(sources = p.sources.filterNot { it.path == path }, details = projectDetails(p).copy(updatedAt = System.currentTimeMillis()))
    }
    private val sourceRequests = mutableMapOf<String, Pair<String, Uri>>()
    private val _projectSourceUploads = MutableStateFlow<List<com.dua.ai.projects.ProjectSourceUpload>>(emptyList())
    val projectSourceUploads = _projectSourceUploads.asStateFlow()
    fun retryProjectSourceUpload(id: String) { sourceRequests[id]?.let { (projectId, uri) -> dismissProjectSourceUpload(id); uploadProjectSources(projectId, listOf(uri)) } }
    fun dismissProjectSourceUpload(id: String) { sourceRequests.remove(id); _projectSourceUploads.update { it.filterNot { upload -> upload.id == id } } }
    fun uploadProjectSources(projectId: String, uris: List<Uri>) {
        if (syncBlocked() || account.state.value.user == null || !_integrations.value.filesEnabled) {
            _projectNotice.value = "Sign in and finish private file storage setup to add project sources."; return
        }
        val project = localSettings.value.projects.find { it.id == projectId && !it.archived } ?: return
        if (_projectSourceUploads.value.any { it.projectId == projectId && !it.complete && it.error == null }) return
        val count = com.dua.ai.projects.ProjectCodec.MAX_SOURCES - project.sources.size
        if (count <= 0) { _projectNotice.value = "Keep at most 3 project sources. Remove one before adding another."; return }
        val ownerId = account.state.value.user!!.id
        val resolver = getApplication<Application>().contentResolver
        uris.take(count).forEach { uri ->
            val uploadId = UUID.randomUUID().toString()
            sourceRequests[uploadId] = projectId to uri
            _projectSourceUploads.update { it + com.dua.ai.projects.ProjectSourceUpload(uploadId, projectId, "Source") }
            viewModelScope.launch {
                var uploadedPath: String? = null
                fun update(change: (com.dua.ai.projects.ProjectSourceUpload) -> com.dua.ai.projects.ProjectSourceUpload) {
                    _projectSourceUploads.update { list -> list.map { if (it.id == uploadId) change(it) else it } }
                }
                var temp: File? = null
                try {
                    val info = withContext(Dispatchers.IO) {
                        var name = "Source"; var size: Long? = null
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) { name = cursor.getString(0).orEmpty().take(200); if (!cursor.isNull(1)) size = cursor.getLong(1) }
                        }
                        Triple(name, resolver.getType(uri).orEmpty(), size)
                    }
                    val type = StoredFileTypes.storedType(info.first, info.second) ?: if (com.dua.ai.attachments.TextFiles.isText(info.first, info.second)) "text/plain" else error("Choose a supported image, PDF, text or document file.")
                    val limit = if (separateChatEngine) com.dua.ai.attachments.AttachmentReader.MAX_BYTES.toLong() else maxStoredBytes()
                    temp = withContext(Dispatchers.IO) { copyCapped(uri, limit) } ?: error("Source exceeds the file size limit.")
                    val size = temp!!.length()
                    if (separateChatEngine && com.dua.ai.attachments.TextFiles.isText(info.first, type) && size > com.dua.ai.attachments.TextFiles.MAX_BYTES) error("Cloud Run text sources must be 256 KB or smaller.")
                    if (separateChatEngine && type !in setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain", "text/markdown", "application/json", "text/csv"))
                        error("Use PDF, images, CSV or UTF-8 text with the Cloud Run engine.")
                    if (separateChatEngine && project.sources.sumOf { it.size } + size > com.dua.ai.attachments.AttachmentReader.MAX_BYTES) error("Cloud Run project sources must fit within 2 MB total.")
                    update { it.copy(name = info.first, total = size) }
                    val signed = SignedUpload.parse(account.request("/api/files/sign", "POST", org.json.JSONObject().put("name", info.first).put("mimeType", type).put("size", size), authenticated = true))
                    check(signed.path.substringBefore('/') == ownerId.lowercase()) { "Source upload belongs to another account." }
                    uploadedPath = signed.path
                    withContext(Dispatchers.IO) { fileClient.upload(signed, size, { temp!!.inputStream() }) { sent -> update { it.copy(sent = sent) } } }
                    check(account.state.value.user?.id == ownerId) { "Account changed. Source was not added." }
                    val ref = FileRef(signed.path, info.first, signed.mimeType.ifBlank { type }, size)
                    check(changeProject(projectId) { p -> check(!p.archived); if (separateChatEngine) check(p.sources.sumOf { it.size } + ref.size <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Cloud Run project sources must fit within 2 MB total." }; p.copy(sources = p.sources + ref, details = projectDetails(p).copy(updatedAt = System.currentTimeMillis())) }) { _projectNotice.value }
                    uploadedPath = null
                    sourceRequests.remove(uploadId)
                    update { it.copy(sent = size, complete = true) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { update { it.copy(error = failure.message ?: "Source upload failed. Try again.") } }
                finally { temp?.delete(); uploadedPath?.let { if (account.state.value.user?.id == ownerId) deleteStoredPaths(listOf(it)) } }
            }
        }
    }

    private data class EngineProjectContext(val memory: String, val attachments: List<com.dua.ai.attachments.ChatAttachment>, val ownerId: String? = null)
    private suspend fun engineProjectContext(projectId: String): EngineProjectContext {
        if (!separateChatEngine) return EngineProjectContext(projectMemory(), emptyList())
        val settings = localSettings.value
        val id = projectId
        val cached = settings.projects.find { it.id == id } ?: error("Restore project settings from cloud history before chatting.")
        // Plain Home chat does not acquire an unrelated base-backend provider/configuration gate.
        if (id == com.dua.ai.projects.ProjectCodec.PERSONAL && !settings.memoryEnabled && cached.instructions.isBlank() && cached.sources.isEmpty())
            return EngineProjectContext("", emptyList())
        val ownerId = account.state.value.user?.id ?: error("Sign in to load this project's server context.")
        val remote = account.cloud()
        check(remote.ownerId == ownerId && account.state.value.user?.id == ownerId) { "Account changed. Reopen the project and retry." }
        val context = com.dua.ai.projects.ProjectRequestContextPolicy.resolve(id, cached, remote.snapshot, settings.memoryEnabled)
        val result = mutableListOf<com.dua.ai.attachments.ChatAttachment>()
        var bytes = 0L
        for (source in context.sources) {
            check(source.path.substringBefore('/') == ownerId.lowercase()) { "A project source belongs to another account." }
            bytes += source.size
            check(bytes <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Project sources exceed Cloud Run's 2 MB context limit. Remove a source and retry." }
            val url = storedFileUrl(source)
            result += fileClient.readAttachment(url, source.name, source.mimeType)
        }
        check(account.state.value.user?.id == ownerId) { "Account changed. Reopen the project and retry." }
        return EngineProjectContext(context.memory, result, ownerId)
    }

    private fun changeTasks(projectId: String, change: (List<com.dua.ai.projects.ProjectTask>) -> List<com.dua.ai.projects.ProjectTask>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing tasks."; return false }
        if (!_integrations.value.projectTasksEnabled) { _projectNotice.value = "Refresh setup after deploying the Tasks backend before editing tasks."; return false }
        return try {
            localPreferences.requireReadable()
            val settings = localSettings.value
            val project = settings.projects.find { it.id == projectId } ?: error("Project changed. Reopen Tasks.")
            check(!project.archived) { "Restore this project before editing tasks." }
            val updated = project.copy(tasks = change(project.tasks))
            val next = settings.projects.map { if (it.id == projectId) updated else it }
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, settings.memory, next))
            localPreferences.update(settings.copy(projects = next))
            _projectNotice.value = "Task saved to the cache. Sync to save it on the server."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Task could not be saved."; false }
    }

    fun saveTask(projectId: String, taskId: String?, title: String, dueAt: Long? = null): Boolean = changeTasks(projectId) { tasks ->
        check(dueAt == null || _integrations.value.taskDueDatesEnabled) { "Refresh setup after updating the backend before saving due dates." }
        val existing = taskId?.let { id -> tasks.find { it.id == id } ?: error("Task changed. Reopen the editor.") }
        val task = existing?.copy(title = title.trim(), dueAt = dueAt) ?: com.dua.ai.projects.ProjectTask(UUID.randomUUID().toString(), title.trim(), dueAt = dueAt)
        if (existing == null) tasks + task else tasks.map { if (it.id == taskId) task else it }
    }
    fun toggleTask(projectId: String, taskId: String): Boolean = changeTasks(projectId) { tasks ->
        check(tasks.any { it.id == taskId }) { "Task changed. Reopen Tasks." }
        tasks.map { if (it.id == taskId) it.copy(completed = !it.completed) else it }
    }
    fun deleteTask(projectId: String, taskId: String): Boolean = changeTasks(projectId) { tasks ->
        check(tasks.any { it.id == taskId }) { "Task changed. Reopen Tasks." }
        tasks.filterNot { it.id == taskId }
    }

    fun saveProjectMemory(projectId: String, enabled: Boolean, notes: List<String>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing memory."; return false }
        if (!_integrations.value.projectMemoryEnabled) { _projectNotice.value = "Refresh setup after deploying the project memory backend."; return false }
        return try {
            localPreferences.requireReadable()
            val settings = localSettings.value
            val project = settings.projects.find { it.id == projectId } ?: error("Project changed. Reopen Memory.")
            check(projectId != com.dua.ai.projects.ProjectCodec.PERSONAL) { "Use Settings → Memory for Personal notes." }
            check(!project.archived) { "Restore this project before editing memory." }
            val memory = com.dua.ai.projects.ProjectMemory(enabled, notes.map { it.trim() })
            val next = settings.projects.map { if (it.id == projectId) it.copy(memory = memory) else it }
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, settings.memory, next))
            localPreferences.update(settings.copy(projects = next))
            _projectNotice.value = "Memory saved to the cache. Sync to apply it on the server."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Memory could not be saved."; false }
    }

    private fun projectUnavailable(): String? {
        val project = localSettings.value.projects.find { it.id == _projectId.value }
            ?: return "This project's settings are missing. Restore them from the server before chatting."
        if (project.archived) return "This project is archived. Restore it in Projects before chatting."
        if (project.memory != com.dua.ai.projects.ProjectMemory() && !_integrations.value.projectMemoryEnabled) return "Refresh Feature setup after deploying the project memory backend before chatting here."
        if (!_integrations.value.projectsEnabled && (project.id != "personal" || project.instructions.isNotBlank())) return "Deploy the Projects backend and refresh setup before chatting in this project."
        return null
    }

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
        refreshSyncNetwork()
        networkObserverRegistered = runCatching { connectivity.registerDefaultNetworkCallback(networkObserver); true }.getOrDefault(false)
        viewModelScope.launch {
            combine(account.state, _dataBusy, _isTyping, _cloudPreview, _importPreview) { state, dataBusy, typing, cloud, imported ->
                state.busy || dataBusy || typing || cloud != null || imported != null
            }.distinctUntilChanged().collect { refreshSyncBusy() }
        }
        viewModelScope.launch { account.state.map { it.user?.id }.distinctUntilChanged().collect { automaticSync.accountChanged(); resumeImagineJobs() } }
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
        flushHistory()
        recorder.cancel()
        pollJobs.values.forEach { it.cancel() }
        automaticSync.close()
        if (networkObserverRegistered) runCatching { connectivity.unregisterNetworkCallback(networkObserver) }
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
        updateMessage(messageId) { it.copy(text = "", thinking = null, thinkingSeconds = null, notice = null, codeRuns = emptyList(), build = requestBuild?.let { com.dua.ai.model.BuildInfo(it) }, proposal = null, state = ChatMessage.STREAMING, startedAt = startedAt) }
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
                        request.historyBytes, request.context, requestXSearch && requestBuild == null, requestCode && requestBuild == null, requestBuild, persistence, agent = requestAgent && requestBuild == null), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    val final = accumulator!!.snapshot(ended = true)
                    retryOriginal = null
                    updateMessage(messageId) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, codeRuns = final.codeRuns, build = final.build ?: it.build, proposal = final.proposal ?: it.proposal, state = "complete") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    retryOriginal = null
                    if (partial != null && partial.text.isNotBlank()) {
                        updateMessage(messageId) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, codeRuns = partial.codeRuns, build = partial.build ?: it.build, proposal = partial.proposal ?: it.proposal, state = "stopped") }
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
        val requestProjectId = _projectId.value
        projectUnavailable()?.let { _error.value = it; return }
        if (_imagine.value && _editingId.value != null) { _error.value = "Turn off Imagine to resend an edited message."; return }
        if (_imagine.value) { sendImagine(userText); return }
        val storedFiles = _uploads.value.filter { it.state == PendingUpload.DONE }.mapNotNull { it.ref }
        if (_editingId.value != null && (_attachments.value.isNotEmpty() || storedFiles.isNotEmpty())) { _error.value = "Edited messages are text only. Remove the attachments or cancel editing."; return }
        if ((userText.isBlank() && _attachments.value.isEmpty() && storedFiles.isEmpty()) || _isTyping.value || _readingFiles.value) return
        if (_uploads.value.any { it.uploading } || _recordingSince.value != null) { _error.value = "Wait for uploads to finish, or remove them."; return }
        _error.value = null
        if (userText.length > 8000) {
            _error.value = "Message 8000 characters se chhota rakhein."
            return
        }
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
                        request.historyBytes, request.context, requestXSearch && requestBuild == null, requestCode && requestBuild == null, requestBuild, persistence, agent = requestAgent && requestBuild == null), request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == currentGeneration) {
                    _uploads.update { list -> list.filterNot { it.ref?.path in sentPaths } }
                    clearAttachments()
                    val final = accumulator!!.snapshot(ended = true)
                    updateMessage(reply.id) { it.copy(text = final.text, thinking = final.thinking, thinkingSeconds = final.thinkingSeconds, notice = final.notice, codeRuns = final.codeRuns, build = final.build ?: it.build, proposal = final.proposal ?: it.proposal, state = "complete") }
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
                        updateMessage(reply.id) { it.copy(text = partial.text, thinking = partial.thinking, thinkingSeconds = partial.thinkingSeconds, notice = partial.notice, codeRuns = partial.codeRuns, build = partial.build ?: it.build, proposal = partial.proposal ?: it.proposal, state = "stopped") }
                        _error.value = "Reply interrupted: ${failureText(failure)} Partial reply kept; tap retry."
                    } else {
                        _messages.value = _messages.value.filterNot { it.id == userMessage.id || it.id == reply.id }
                        replyWebModes.remove(reply.id); replyReasoning.remove(reply.id); replyAgent.remove(reply.id); replyCode.remove(reply.id); replyBuild.remove(reply.id)
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
        _agent.value = false
        val config = imagineConfig()
        when {
            config == null -> { _error.value = "Imagine needs the latest Dua backend. Deploy it, then refresh Feature setup."; return }
            !config.enabled -> { _error.value = config.imageReason.ifBlank { config.videoReason }.ifBlank { "Imagine is turned off on the server." }; return }
            account.state.value.user == null -> { _error.value = "Sign in from Profile to use Imagine. Results are saved to your private storage."; return }
        }
        val settings = localSettings.value
        val base = if (_imagine.value) _imagineOptions.value else _imagineOptions.value.copy(aspectRatio = settings.imagineAspect, provider = settings.imagineProvider.ifBlank { null })
        _imagineOptions.value = config!!.normalize(base)
        _reasoning.value = false; _webMode.value = null; _xSearch.value = false; _runCode.value = false
        leaveBuild()
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
        /** Draft typing is saved this long after the last keystroke (and at once on stop or send). */
        const val DRAFT_SAVE_DELAY_MS = 500L
    }
}
