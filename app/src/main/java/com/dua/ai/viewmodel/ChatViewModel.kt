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
    private val historyFeature: ChatHistory by lazy { ChatHistory(historyHost) }
    private val historyHost: HistoryHost by lazy { object : HistoryHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val account get() = this@ChatViewModel.account
        override val api get() = this@ChatViewModel.api
        override val localSettings get() = this@ChatViewModel.localSettings
        override val preferences get() = this@ChatViewModel.preferences
        override val historyWriter get() = this@ChatViewModel.historyWriter
        override val draftSaver get() = this@ChatViewModel.draftSaver
        override val mediaCache get() = this@ChatViewModel.mediaCache
        override val onDeviceAi get() = this@ChatViewModel.onDeviceAi
        override val replyAudioCache get() = this@ChatViewModel.replyAudioCache
        override val separateChatEngine get() = this@ChatViewModel.separateChatEngine
        override val pollJobs get() = this@ChatViewModel.pollJobs
        override val replyWebModes get() = this@ChatViewModel.replyWebModes
        override val replyReasoning get() = this@ChatViewModel.replyReasoning
        override val replyAgent get() = this@ChatViewModel.replyAgent
        override val replyCode get() = this@ChatViewModel.replyCode
        override val replyBuild get() = this@ChatViewModel.replyBuild
        override val replyXSearch get() = this@ChatViewModel.replyXSearch
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _dataStatus get() = this@ChatViewModel._dataStatus
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _messages get() = this@ChatViewModel._messages
        override val _selectedModel get() = this@ChatViewModel._selectedModel
        override val _draft get() = this@ChatViewModel._draft
        override val _summary get() = this@ChatViewModel._summary
        override val _webMode get() = this@ChatViewModel._webMode
        override val _xSearch get() = this@ChatViewModel._xSearch
        override val _buildRepo get() = this@ChatViewModel._buildRepo
        override val _projectId get() = this@ChatViewModel._projectId
        override val _integrations get() = this@ChatViewModel._integrations
        override val _deletedChatIds get() = this@ChatViewModel._deletedChatIds
        override val _chatDeletion get() = this@ChatViewModel._chatDeletion
        override var activeId: String
            get() = this@ChatViewModel.activeId
            set(value) { this@ChatViewModel.activeId = value }
        override var deletionOwnerId: String?
            get() = this@ChatViewModel.deletionOwnerId
            set(value) { this@ChatViewModel.deletionOwnerId = value }
        override var historyReadable: Boolean
            get() = this@ChatViewModel.historyReadable
            set(value) { this@ChatViewModel.historyReadable = value }
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override fun flushHistory() = this@ChatViewModel.flushHistory()
        override fun cancelReply() = this@ChatViewModel.cancelReply()
        override fun clearAttachments() = this@ChatViewModel.clearAttachments()
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun syncBlocked() = this@ChatViewModel.syncBlocked()
        override fun cacheBlocked() = this@ChatViewModel.cacheBlocked()
        override fun currentDeletionIds() = this@ChatViewModel.currentDeletionIds()
        override fun onDeviceAiEnabled() = this@ChatViewModel.onDeviceAiEnabled()
    } }
    private val modesFeature: ChatModes by lazy { ChatModes(modesHost) }
    private val modesHost: ChatModesHost by lazy { object : ChatModesHost {
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _reasoning get() = this@ChatViewModel._reasoning
        override val _agent get() = this@ChatViewModel._agent
        override val _xSearch get() = this@ChatViewModel._xSearch
        override val _imagine get() = this@ChatViewModel._imagine
        override val _runCode get() = this@ChatViewModel._runCode
        override val _webMode get() = this@ChatViewModel._webMode
        override val _error get() = this@ChatViewModel._error
        override val _integrations get() = this@ChatViewModel._integrations
        override val _selectedModel get() = this@ChatViewModel._selectedModel
        override fun leaveBuild() = this@ChatViewModel.leaveBuild()
    } }
    private val agentFeature: ChatAgentControls by lazy { ChatAgentControls(agentHost) }
    private val agentHost: AgentControlsHost by lazy { object : AgentControlsHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override val account get() = this@ChatViewModel.account
        override val api get() = this@ChatViewModel.api
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _switches get() = this@ChatViewModel._switches
        override val _error get() = this@ChatViewModel._error
        override val _agentAvailable get() = this@ChatViewModel._agentAvailable
        override val _agent get() = this@ChatViewModel._agent
        override val _reasoning get() = this@ChatViewModel._reasoning
        override val _xSearch get() = this@ChatViewModel._xSearch
        override val _imagine get() = this@ChatViewModel._imagine
        override val _runCode get() = this@ChatViewModel._runCode
        override val _webMode get() = this@ChatViewModel._webMode
        override val _integrations get() = this@ChatViewModel._integrations
        override val _online get() = this@ChatViewModel._online
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _preparingFiles get() = this@ChatViewModel._preparingFiles
        override val _draft get() = this@ChatViewModel._draft
        override val _attachments get() = this@ChatViewModel._attachments
        override val _uploads get() = this@ChatViewModel._uploads
        override val _editingId get() = this@ChatViewModel._editingId
        override val _selectedModel get() = this@ChatViewModel._selectedModel
        override val _buildRepositories get() = this@ChatViewModel._buildRepositories
        override val _buildRepo get() = this@ChatViewModel._buildRepo
        override val _buildPr get() = this@ChatViewModel._buildPr
        override val _messages get() = this@ChatViewModel._messages
        override val _savedChats get() = this@ChatViewModel._savedChats
        override fun leaveBuild() = this@ChatViewModel.leaveBuild()
        override fun selectAuto() = this@ChatViewModel.selectAuto()
        override fun sendMessage(userText: String) = this@ChatViewModel.sendMessage(userText)
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override fun codeUsable() = this@ChatViewModel.codeUsable()
        override fun buildUsable() = this@ChatViewModel.buildUsable()
        override fun xSearchUsable() = this@ChatViewModel.xSearchUsable()
    } }
    private val uploadsFeature: ChatUploads by lazy { ChatUploads(uploadsHost) }
    private val uploadsHost: UploadsHost by lazy { object : UploadsHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val account get() = this@ChatViewModel.account
        override val api get() = this@ChatViewModel.api
        override val fileClient get() = this@ChatViewModel.fileClient
        override val pendingDeletes get() = this@ChatViewModel.pendingDeletes
        override val recorder get() = this@ChatViewModel.recorder
        override val mediaCache get() = this@ChatViewModel.mediaCache
        override val uploadJobs get() = this@ChatViewModel.uploadJobs
        override val _cloudLibrary get() = this@ChatViewModel._cloudLibrary
        override val _integrations get() = this@ChatViewModel._integrations
        override val _uploads get() = this@ChatViewModel._uploads
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _attachmentError get() = this@ChatViewModel._attachmentError
        override val _recordingSince get() = this@ChatViewModel._recordingSince
        override val _imagine get() = this@ChatViewModel._imagine
        override val _storageUsage get() = this@ChatViewModel._storageUsage
        override val _storageUsageStatus get() = this@ChatViewModel._storageUsageStatus
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _dataStatus get() = this@ChatViewModel._dataStatus
        override val _messages get() = this@ChatViewModel._messages
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val _attachments get() = this@ChatViewModel._attachments
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _preparingFiles get() = this@ChatViewModel._preparingFiles
        override var libraryJob: Job?
            get() = this@ChatViewModel.libraryJob
            set(value) { this@ChatViewModel.libraryJob = value }
        override var flushingDeletes: Boolean
            get() = this@ChatViewModel.flushingDeletes
            set(value) { this@ChatViewModel.flushingDeletes = value }
        override var storageUsageGeneration: Long
            get() = this@ChatViewModel.storageUsageGeneration
            set(value) { this@ChatViewModel.storageUsageGeneration = value }
        override var selectionVersion: Int
            get() = this@ChatViewModel.selectionVersion
            set(value) { this@ChatViewModel.selectionVersion = value }
        override var preparingSelections: Int
            get() = this@ChatViewModel.preparingSelections
            set(value) { this@ChatViewModel.preparingSelections = value }
        override fun updateUpload(id: String, transform: (PendingUpload) -> PendingUpload) = this@ChatViewModel.updateUpload(id, transform)
        override fun deleteStoredPaths(paths: Collection<String>) = this@ChatViewModel.deleteStoredPaths(paths)
        override fun storedFilesAvailable() = this@ChatViewModel.storedFilesAvailable()
        override fun storedFilesHint() = this@ChatViewModel.storedFilesHint()
        override fun maxStoredBytes() = this@ChatViewModel.maxStoredBytes()
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun describe(uris: List<Uri>) = this@ChatViewModel.describe(uris)
        override suspend fun uploadPicked(files: List<PickedFile>) = this@ChatViewModel.uploadPicked(files)
    } }
    private val attachmentsFeature: ChatAttachments by lazy { ChatAttachments(attachmentsHost) }
    private val attachmentsHost: AttachmentsHost by lazy { object : AttachmentsHost {
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val uploadsFeature get() = this@ChatViewModel.uploadsFeature
        override var selectionVersion: Int
            get() = this@ChatViewModel.selectionVersion
            set(value) { this@ChatViewModel.selectionVersion = value }
        override val _attachments get() = this@ChatViewModel._attachments
        override val _attachmentError get() = this@ChatViewModel._attachmentError
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _preparingFiles get() = this@ChatViewModel._preparingFiles
        override val _uploads get() = this@ChatViewModel._uploads
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override fun copyCapped(uri: Uri, limit: Long): File? = this@ChatViewModel.copyCapped(uri, limit)
    } }
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
    // Reply lifecycle. Declared after the Imagine host so an init-time retry cannot reach it early.
    private val lifecycleFeature: ChatLifecycle by lazy { ChatLifecycle(lifecycleHost) }
    private val lifecycleHost: LifecycleHost by lazy { object : LifecycleHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override val api get() = this@ChatViewModel.api
        override val account get() = this@ChatViewModel.account
        override val separateChatEngine get() = this@ChatViewModel.separateChatEngine
        override val localSettings get() = this@ChatViewModel.localSettings
        override val appearance get() = this@ChatViewModel.appearance
        override val _agentAvailable get() = this@ChatViewModel._agentAvailable
        override val _integrations get() = this@ChatViewModel._integrations
        override val _messages get() = this@ChatViewModel._messages
        override val _error get() = this@ChatViewModel._error
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _preparingFiles get() = this@ChatViewModel._preparingFiles
        override val _readingFiles get() = this@ChatViewModel._readingFiles
        override val _dataBusy get() = this@ChatViewModel._dataBusy
        override val _projectId get() = this@ChatViewModel._projectId
        override val replyWebModes get() = this@ChatViewModel.replyWebModes
        override val replyAgent get() = this@ChatViewModel.replyAgent
        override val replyReasoning get() = this@ChatViewModel.replyReasoning
        override val replyXSearch get() = this@ChatViewModel.replyXSearch
        override val replyCode get() = this@ChatViewModel.replyCode
        override val replyBuild get() = this@ChatViewModel.replyBuild
        override val activeId get() = this@ChatViewModel.activeId
        override val generation get() = this@ChatViewModel.generation
        override var replyJob: Job?
            get() = this@ChatViewModel.replyJob
            set(value) { this@ChatViewModel.replyJob = value }
        override var retryOriginal: ChatMessage?
            get() = this@ChatViewModel.retryOriginal
            set(value) { this@ChatViewModel.retryOriginal = value }
        override fun projectUnavailable() = this@ChatViewModel.projectUnavailable()
        override fun personalityUnavailable() = this@ChatViewModel.personalityUnavailable()
        override fun xSearchUsable(model: String) = this@ChatViewModel.xSearchUsable(model)
        override fun codeUsable(model: String) = this@ChatViewModel.codeUsable(model)
        override fun buildUsable(model: String) = this@ChatViewModel.buildUsable(model)
        override fun buildUnavailableReason() = this@ChatViewModel.buildUnavailableReason()
        override fun webModeError(mode: String?, model: String) = this@ChatViewModel.webModeError(mode, model)
        override fun requestTurns(history: List<ChatMessage>, legacy: List<ChatMessage>, inlineFiles: Boolean) = this@ChatViewModel.requestTurns(history, legacy, inlineFiles)
        override fun requestFreeModel() = this@ChatViewModel.requestFreeModel()
        override fun refreshSyncBusy() { this@ChatViewModel.refreshSyncBusy() }
        override fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) = this@ChatViewModel.updateMessage(id, transform)
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override suspend fun engineProjectContext(projectId: String) = this@ChatViewModel.engineProjectContext(projectId)
        override suspend fun collectReply(id: String, accumulator: com.dua.ai.network.StreamAccumulator, events: kotlinx.coroutines.flow.Flow<com.dua.ai.network.ReplyEvent>, request: RequestTurns?) = this@ChatViewModel.collectReply(id, accumulator, events, request)
    } }
    val driveBackups = com.dua.ai.backup.drive.AutomaticDriveBackups.get(application)
    val account = driveBackups.account
    private val setupApi = com.dua.ai.account.AccountApi()
    private val _online = MutableStateFlow(false)
    val online = _online.asStateFlow()
    /** Generated reply audio for this process only (memory, ~12 MB); replays and voice previews are free. */
    private val replyAudioCache = com.dua.ai.voice.AudioChunkCache()
    private val voiceFeature: ChatVoice by lazy { ChatVoice(voiceHost) }
    private val voiceHost: VoiceHost by lazy { object : VoiceHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override fun <T : Application> getApplication(): T = this@ChatViewModel.getApplication()
        override val account get() = this@ChatViewModel.account
        override val localSettings get() = this@ChatViewModel.localSettings
        override val replyAudioCache get() = this@ChatViewModel.replyAudioCache
        override val _online get() = this@ChatViewModel._online
        override val _integrations get() = this@ChatViewModel._integrations
        override val _switches get() = this@ChatViewModel._switches
        override val projectId get() = this@ChatViewModel.projectId
        override val _isTyping get() = this@ChatViewModel._isTyping
        override val _messages get() = this@ChatViewModel._messages
        override val _savedChats get() = this@ChatViewModel._savedChats
        override val activeId get() = this@ChatViewModel.activeId
        override val historyWriter get() = this@ChatViewModel.historyWriter
        override fun saveHistory() = this@ChatViewModel.saveHistory()
        override fun projectMemory() = this@ChatViewModel.projectMemory()
        override fun projectUnavailable() = this@ChatViewModel.projectUnavailable()
    } }

    // Voice/TTS and live voice (moved to ChatVoice.kt; public API unchanged).
    fun replyTtsOptions(): com.dua.ai.voice.TtsOptions? = voiceFeature.replyTtsOptions()
    fun replyVoicePlan(text: String): List<String> = voiceFeature.replyVoicePlan(text)
    suspend fun generateReplyAudio(text: String): ByteArray = voiceFeature.generateReplyAudio(text)
    val liveStart: StateFlow<LiveStartState> get() = voiceFeature.liveStart
    val liveUsage: StateFlow<com.dua.ai.voice.live.LiveUsage?> get() = voiceFeature.liveUsage
    fun liveVoiceAvailable(): Boolean = voiceFeature.liveVoiceAvailable()
    fun liveOptions(): com.dua.ai.voice.live.LiveOptions? = voiceFeature.liveOptions()
    fun loadLiveUsage() = voiceFeature.loadLiveUsage()
    fun clearLiveError() = voiceFeature.clearLiveError()
    fun startLiveVoice() = voiceFeature.startLiveVoice()

    private val _integrations = MutableStateFlow(com.dua.ai.setup.IntegrationState())
    val integrations = _integrations.asStateFlow()
    private val _searchResults = MutableStateFlow<List<com.dua.ai.setup.SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _connectorRepositories = MutableStateFlow<List<com.dua.ai.setup.RepositoryResult>>(emptyList())
    val connectorRepositories = _connectorRepositories.asStateFlow()
    private val memoryFeature: ChatMemory by lazy { ChatMemory(memoryHost) }
    private val memoryHost: MemoryHost by lazy { object : MemoryHost {
        override val _projectId get() = this@ChatViewModel._projectId
        override val localPreferences get() = this@ChatViewModel.localPreferences
        override val localSettings get() = this@ChatViewModel.localSettings
        override val _messages get() = this@ChatViewModel._messages
        override val account get() = this@ChatViewModel.account
        override fun syncBlocked() = this@ChatViewModel.syncBlocked()
        override fun integrationNotice(text: String) { this@ChatViewModel.integrationNotice(text) }
    } }

    // Personal-memory note proposals (moved to ChatMemory.kt; public API unchanged).
    val memoryCandidates: StateFlow<List<String>> get() = memoryFeature.memoryCandidates
    fun proposeMemoryNotes() = memoryFeature.proposeMemoryNotes()
    fun acceptMemoryNote(note: String) = memoryFeature.acceptMemoryNote(note)
    private fun suggestAgentMemory(fact: String) = memoryFeature.suggestAgentMemory(fact)
    fun dismissMemoryNote(note: String) = memoryFeature.dismissMemoryNote(note)
    fun confirmSharedMemory(note: String, expectedOwnerId: String): String? = memoryFeature.confirmSharedMemory(note, expectedOwnerId)
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
    /** The account picker needs an Activity, so the UI runs it ([com.dua.ai.account.GoogleSignIn]) and reports here. */
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
    // Settings / appearance / writing (moved to ChatSettings.kt; public API unchanged).
    private val settingsFeature: ChatSettings by lazy { ChatSettings(settingsHost) }
    private val settingsHost: SettingsHost by lazy { object : SettingsHost {
        override val viewModelScope get() = this@ChatViewModel.viewModelScope
        override val localPreferences get() = this@ChatViewModel.localPreferences
        override val appearanceStore get() = this@ChatViewModel.appearanceStore
        override val onDeviceAi get() = this@ChatViewModel.onDeviceAi
        override fun onDeviceAiEnabled() = this@ChatViewModel.onDeviceAiEnabled()
        override val _draft get() = this@ChatViewModel._draft
        override fun updateDraft(value: String) = this@ChatViewModel.updateDraft(value)
    } }
    val polishing: StateFlow<Boolean> get() = settingsFeature.polishing
    val polishNotice: StateFlow<String> get() = settingsFeature.polishNotice
    fun polishImaginePrompt() = settingsFeature.polishImaginePrompt()
    val writing: StateFlow<com.dua.ai.ondevice.WritingSession?> get() = settingsFeature.writing
    fun openWriting(text: String, fromDraft: Boolean, task: com.dua.ai.ondevice.WritingTask?) = settingsFeature.openWriting(text, fromDraft, task)
    fun runWriting(task: com.dua.ai.ondevice.WritingTask) = settingsFeature.runWriting(task)
    fun useWritingResult() = settingsFeature.useWritingResult()
    fun askServerForWriting() = settingsFeature.askServerForWriting()
    fun closeWriting() = settingsFeature.closeWriting()
    val settingsStatus: StateFlow<String> get() = settingsFeature.settingsStatus
    fun setPersonality(value: com.dua.ai.settings.DuaPersonality) = settingsFeature.setPersonality(value)
    fun setAppearance(value: String) = settingsFeature.setAppearance(value)
    fun setAccent(value: com.dua.ai.settings.AccentPreset?) = settingsFeature.setAccent(value)
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch {
        try { appearanceStore.setDynamicColor(enabled); settingsNotice("Saved on this device.") }
        catch (_: java.io.IOException) { settingsNotice("Appearance could not be saved. Check device storage and retry.") }
    } }
    fun resetSettings() = settingsFeature.resetSettings()
    fun settingsNotice(text: String) = settingsFeature.settingsNotice(text)
    fun updateSettings(value: com.dua.ai.settings.LocalPreferences) = settingsFeature.updateSettings(value)
    private val backgroundRemover by lazy { com.dua.ai.ondevice.OnDeviceServices.backgroundRemover(application) }
    /** Google Play services is present, so the on-device background tool can run (or be downloaded once). */
    val backgroundRemovalAvailable: Boolean by lazy { backgroundRemover.available() }
    /** A transparent PNG of the photo's subject, made on the phone, in the private cache. Throws with a user-facing message. */
    suspend fun removeBackground(bytes: ByteArray): java.io.File =
        com.dua.ai.ondevice.Cutouts.create(getApplication(), backgroundRemover, bytes)
    /** Bytes of a draft image attachment, or null when it is not an image any more. */
    fun attachmentImageBytes(index: Int): ByteArray? = attachmentsFeature.attachmentImageBytes(index)
    private val appearanceStore = com.dua.ai.settings.AppearanceStore(application)
    private var appearanceLoaded = false
    val appearance = appearanceStore.data.onEach { appearanceLoaded = true }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, appearanceStore.initial)
    val appearanceNotice = appearanceStore.notice
    var foreground = false
        private set
    fun setForeground(value: Boolean) {
        foreground = value
        automaticSync.setForeground(value)
        setNetwork(networkMonitor.currentlyOnline())
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
                else -> message.copy(state = "stopped", coding = message.coding?.ended(), thinkingSeconds = message.thinkingSeconds ?: elapsedThinking(message))
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
                    proposal = latest.proposal ?: message.proposal, coding = latest.coding ?: message.coding, state = "complete") else message }
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
            updateMessage(id) { it.copy(progress = snapshot.progress, approvals = snapshot.approvals.ifEmpty { it.approvals }, coding = snapshot.coding ?: it.coding, media = snapshot.media.ifEmpty { it.media }, text = snapshot.text, thinking = snapshot.thinking, thinkingSeconds = snapshot.thinkingSeconds, notice = snapshot.notice, codeRuns = snapshot.codeRuns, build = snapshot.build ?: it.build, proposal = snapshot.proposal ?: it.proposal) }
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
    fun setReasoning(enabled: Boolean) = modesFeature.setReasoning(enabled)
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
    fun setAgent(enabled: Boolean) = agentFeature.setAgent(enabled)
    /** Auto chip: plain chat, no web and no Reasoning. */
    fun selectAuto() = modesFeature.selectAuto()

    // Run code (Phase 5): the provider runs Python in its own sandbox. Opt-in per session like Reasoning, never saved.
    private val _runCode = MutableStateFlow(false)
    val runCode = _runCode.asStateFlow()
    private val replyCode = mutableMapOf<String, Boolean>()
    private fun codeUsable(model: String = _selectedModel.value) = _integrations.value.codeExecution?.available(model) == true
    /** Only advertise the existing provider sandbox on the engine that actually supports it. */
    fun codeSnippetUnavailableReason(): String? = agentFeature.codeSnippetUnavailableReason()

    fun runCodeSnippet(code: String): Boolean = agentFeature.runCodeSnippet(code)
    fun setRunCode(enabled: Boolean) = agentFeature.setRunCode(enabled)

    // Build mode (Phase 5B): STRICTLY READ-ONLY chat about one GitHub repository. The repository is tied to the chat
    // (saved with it on this device); the server reads files with its own token and never writes to GitHub.
    private val _buildRepo = MutableStateFlow<String?>(null)
    val buildRepo = _buildRepo.asStateFlow()
    private val replyBuild = mutableMapOf<String, String>()
    private val _buildRepositories = MutableStateFlow(com.dua.ai.setup.BuildRepositories())
    val buildRepositories = _buildRepositories.asStateFlow()
    private fun buildUsable(model: String = _selectedModel.value) = _integrations.value.build?.available(model) == true
    /** Why Build mode cannot be used right now (shown in the Build sheet instead of hiding the mode); null when it can. */
    fun buildUnavailableReason(): String? = agentFeature.buildUnavailableReason()
    /** Loads the owner's repositories (read-only list) for the Build picker; kept until [force]d (connector calls share the chat burst limit). */
    fun loadBuildRepositories(force: Boolean = false) = agentFeature.loadBuildRepositories(force)
    /** Ties this chat to [repository] (owner/name), or leaves Build mode with null. Other modes turn off. */
    fun setBuildRepository(repository: String?) = agentFeature.setBuildRepository(repository)
    private fun leaveBuild() { if (_buildRepo.value != null) { _buildRepo.value = null; saveHistory() } }

    // Build pull requests (Phase 5C): the model only proposes; this is the single place that asks the server to write,
    // and it runs only from the review card's Create PR button after the owner confirms the dialog.
    private val _buildPr = MutableStateFlow<Map<String, com.dua.ai.ui.chat.BuildPrUiState>>(emptyMap())
    val buildPr = _buildPr.asStateFlow()
    /** Why the Create PR button is unavailable (shown on the review card), or null when it can be used. */
    fun buildPrUnavailableReason(): String? = agentFeature.buildPrUnavailableReason()
    /** Sends the approved proposal of [messageId] (with the confirmed [title]) to POST /api/build/pr and saves the PR link. */
    fun createPullRequest(messageId: String, title: String) = agentFeature.createPullRequest(messageId, title)

    // Dua Agent approvals: the engine never runs a write tool by itself. This is the single place that asks it to run (or
    // drop) one stored action, and only from the approval card's Approve / Reject buttons. Only the id is sent.

    fun decideAgentApproval(messageId: String, approvalId: String, approve: Boolean) = agentFeature.decideAgentApproval(messageId, approvalId, approve)

    // X search (Grok only, billed per post by xAI) is off by default and, like the web mode, never saved.
    private val _xSearch = MutableStateFlow(false)
    val xSearch = _xSearch.asStateFlow()
    private val replyXSearch = mutableMapOf<String, Boolean>()
    private fun xSearchUsable(model: String = _selectedModel.value) = model == "Grok" && _integrations.value.xSearchAvailable
    fun setXSearch(enabled: Boolean) = agentFeature.setXSearch(enabled)

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
    fun setWebMode(mode: String?) = modesFeature.setWebMode(mode)
    private fun webModeError(mode: String?, model: String = _selectedModel.value): String? = modesFeature.webModeError(mode, model)

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
    fun acceptSharedAttachments(uris: List<Uri>): Boolean = attachmentsFeature.acceptSharedAttachments(uris)

    /** Remove only the selected inline draft file; keep every other attachment intact. */
    fun removeAttachment(index: Int) = attachmentsFeature.removeAttachment(index)

    fun clearAttachments() = attachmentsFeature.clearAttachments()

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

    fun loadLibraryFiles() = uploadsFeature.loadLibraryFiles()

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

    private fun startUpload(name: String, type: String, size: Long, open: () -> java.io.InputStream, cleanup: () -> Unit = {}) =
        uploadsFeature.startUpload(name, type, size, open, cleanup)

    /** Removes one pending upload; an already uploaded (unsent) file is deleted from storage. */
    fun cancelUpload(id: String) = uploadsFeature.cancelUpload(id)

    private fun discardUploads() = uploadsFeature.discardUploads()


    private suspend fun uploadPicked(files: List<PickedFile>): List<Uri> = attachmentsFeature.uploadPicked(files)


    /** Deletes storage objects now (or later, if offline / signed out). */
    private fun deleteStoredPaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        pendingDeletes.add(paths)
        flushPendingFileDeletes()
    }
    private var flushingDeletes = false
    fun flushPendingFileDeletes() = uploadsFeature.flushPendingFileDeletes()

    /** Opens a stored file with a short-lived signed link (the server checks the owner). */
    suspend fun storedFileUrl(ref: FileRef): String = storedFileUrl(ref.path)

    fun startRecording() = uploadsFeature.startRecording()
    fun stopRecording() = uploadsFeature.stopRecording()
    fun cancelRecording() { recorder.cancel(); _recordingSince.value = null }

    fun loadStorageUsage() = uploadsFeature.loadStorageUsage()

    /** Deletes every uploaded file for this account. Chats keep the file names but can no longer open or re-read them. */
    fun deleteAllUploadedFiles() = uploadsFeature.deleteAllUploadedFiles()

    private fun applyTranscripts(transcripts: Map<String, String>) {
        _messages.value = _messages.value.map { message ->
            if (message.files.none { it.path in transcripts }) message
            else message.copy(files = message.files.map { ref -> transcripts[ref.path]?.let { ref.copy(transcript = it) } ?: ref })
        }
    }
    private fun historyBytes() = if (_integrations.value.filesEnabled) DuaApiClient.FILES_HISTORY_BYTES else DuaApiClient.MAX_HISTORY_BYTES


    fun selectAttachments(uris: List<Uri>) = uploadsFeature.selectAttachments(uris)



    fun selectCameraFile(file: File) = uploadsFeature.selectCameraFile(file)

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

    fun clearAllChats() = historyFeature.clearAllChats()

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
    private fun liveProjectBusy() = voiceFeature._liveStart.value.busy || com.dua.ai.voice.live.LiveVoiceController.active
    // File preparation/reading is a reply-shaped action: the picker that started it owns the draft
    // and the message list until it finishes, so anything gated by syncBlocked() must wait too.
    private fun syncBlocked() = cacheBlocked() || _isTyping.value
    /**
     * The half of [syncBlocked] that guards the *stored* state rather than a reply. A local delete
     * rewrites that same state, so it must honour these conditions too — but not `_isTyping`, because
     * deleting the open chat is exactly how a streaming reply is cancelled.
     */
    private fun cacheBlocked() = account.state.value.busy || _dataBusy.value || liveProjectBusy() ||
        _preparingFiles.value || _readingFiles.value ||
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
            combine(voiceFeature._liveStart, com.dua.ai.voice.live.LiveVoiceController.state) { starting, live -> starting.busy || live.active }
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

    fun openChat(id: String) = historyFeature.openChat(id)

    /** Returns an actionable error, or null after queuing the renamed chat for the existing sync flow. */
    fun renameChat(id: String, value: String): String? = historyFeature.renameChat(id, value)

    /**
     * Auto title (ChatGPT-style): after the first reply of a new chat, ask the Cloud Run engine for a 3–6 word
     * title. Only when chat goes to that engine and the backend accepts titles; any failure keeps the
     * default title (the first message). A title the user set themselves is never replaced.
     */
    private fun maybeAutoTitle(chatId: String) = historyFeature.maybeAutoTitle(chatId)

    /** Pin is chat metadata, queued for the existing reviewed/automatic sync flow. */
    fun setChatPinned(id: String, pinned: Boolean): String? = historyFeature.setChatPinned(id, pinned)

    /** Apply authoritative deletion IDs after persistence, including the active chat's draft buffers. */
    private fun bindCloudHistory(chats: List<SavedChat>, deleted: List<String>, selectedId: String = activeId) = historyFeature.bindCloudHistory(chats, deleted, selectedId)

    fun dismissChatDeletion() { if (!_chatDeletion.value.busy) _chatDeletion.value = ChatDeletionState() }
    fun deleteServerChat(id: String) = historyFeature.deleteServerChat(id)

    private suspend fun applyConfirmedChatDeletion(result: com.dua.ai.account.CloudChatDeletion) = historyFeature.applyConfirmedChatDeletion(result)

    fun deleteChat(id: String) = historyFeature.deleteChat(id)

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

    /** A checkpoint ID is a request to the existing owner-scoped tool, never direct restored state. */
    fun resumeCodingCheckpoint(id: String): Boolean {
        if (!Regex("[0-9a-f]{32}").matches(id) || syncBlocked() || !_online.value ||
            account.state.value.user == null || !_agentAvailable.value || _switches.value.agent ||
            _draft.value.isNotBlank() || _attachments.value.isNotEmpty() || _uploads.value.isNotEmpty() ||
            _recordingSince.value != null || _editingId.value != null || projectUnavailable() != null || personalityUnavailable() != null) return false
        startNewChat()
        setAgent(true)
        if (!_agent.value) return false
        val prompt = "Restore my saved coding workspace checkpoint $id using restore_workspace. " +
            "Show its saved draft and workspace_diff. Do not rerun commands or publish changes."
        updateDraft(prompt)
        val plan = _prepareSend(prompt) ?: return false
        _streamReply(plan, prompt)
        return true
    }

    fun startNewChat() = historyFeature.startNewChat()

    fun retryResponse(messageId: String) = lifecycleFeature.retryResponse(messageId)

    fun sendMessage(userText: String) {
        val plan = _prepareSend(userText) ?: return
        _streamReply(plan, userText)
    }

    /** One outgoing turn, frozen before any file work or project loading starts. */
    private data class SendPlan(
        val files: List<ChatAttachment>,
        val convert: (ChatAttachment) -> Boolean,
        val storedFiles: List<FileRef>,
        val edited: Boolean,
        val sentPaths: Set<String>,
        val userMessage: ChatMessage,
        val reply: ChatMessage,
        val request: RequestTurns,
        val persistenceBase: List<ChatMessage>,
        val persistenceOwner: String?,
        val currentGeneration: Int,
        val startedAt: Long,
        val requestProjectId: String,
        val responseModel: String,
        val requestWebMode: String?,
        val requestReasoning: Boolean,
        val requestXSearch: Boolean,
        val requestCode: Boolean,
        val requestBuild: String?,
        val requestAgent: Boolean,
        val requestPersonality: String?
    )

    /** Guards, request snapshot, and the user/reply pair. Null means the turn was rejected (or handled elsewhere). */
    private fun _prepareSend(userText: String): SendPlan? {
        if (_preparingFiles.value) return null
        if (!autoRetrying) offlineRetry.clear()
        val requestProjectId = _projectId.value
        projectUnavailable()?.let { _error.value = it; return null }
        if (_imagine.value && _editingId.value != null) { _error.value = "Turn off Imagine to resend an edited message."; return null }
        if (_imagine.value) { imagineFeature.sendImagine(userText); return null }
        val storedFiles = _uploads.value.filter { it.state == PendingUpload.DONE }.mapNotNull { it.ref }
        if (_editingId.value != null && (_attachments.value.isNotEmpty() || storedFiles.isNotEmpty())) { _error.value = "Edited messages are text only. Remove the attachments or cancel editing."; return null }
        if ((userText.isBlank() && _attachments.value.isEmpty() && storedFiles.isEmpty()) || _isTyping.value || _readingFiles.value) return null
        if (_uploads.value.any { it.uploading } || _recordingSince.value != null) { _error.value = "Wait for uploads to finish, or remove them."; return null }
        _error.value = null
        if (userText.length > 8000) {
            _error.value = "Message 8000 characters se chhota rakhein."
            return null
        }
        personalityUnavailable()?.let { _error.value = it; return null }
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
            (buildUnavailableReason() ?: if (!buildUsable(responseModel)) "Build mode works with Dua or Gemini. Choose one of them or turn Build off." else null)?.let { _error.value = it; return null }
            if (_attachments.value.isNotEmpty() || storedFiles.isNotEmpty()) { _error.value = "Build mode reads the repository, not attachments. Remove them or turn Build off."; return null }
        }
        webModeError(requestWebMode, responseModel)?.let { _error.value = it; return null }
        val provider = api.chatProvider(responseModel, _integrations.value.providers)
        if (provider == null) {
            _error.value = "This provider needs backend setup. Choose a configured model."
            return null
        }
        val files = _attachments.value
        val convert = { file: com.dua.ai.attachments.ChatAttachment -> com.dua.ai.attachments.AttachmentConversion.needsJpeg(provider.attachmentTypes, file) }
        // WebP photos are converted to JPEG for providers that only take JPEG/PNG (Grok).
        val attachmentError = com.dua.ai.setup.IntegrationCapabilities.attachmentError(provider, files.map { if (convert(it)) "image/jpeg" else it.mimeType })
        if (attachmentError != null) {
            _error.value = attachmentError
            return null
        }
        val prompt = userText.trim().ifBlank { "Describe these files." }
        val names = files.map { it.name } + storedFiles.map { it.name }
        val text = prompt + if (names.isEmpty()) "" else "\nFiles: " + names.joinToString(", ")
        val fileText = com.dua.ai.attachments.TextFiles.contextText(files, ChatMessage.MAX_ATTACHMENT_TEXT_CHARS)
        if (api.usesInlineChatFiles(false) && storedFiles.any { it.transcript.isNullOrBlank() }) {
            _error.value = "Cloud Run cannot read stored file references. Remove these files and select them again for inline chat (2 MB total); use CSV for spreadsheets."
            return null
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
        return SendPlan(files = files, convert = convert, storedFiles = storedFiles, edited = edited, sentPaths = sentPaths,
            userMessage = userMessage, reply = reply, request = request, persistenceBase = persistenceBase,
            persistenceOwner = persistenceOwner, currentGeneration = currentGeneration, startedAt = startedAt,
            requestProjectId = requestProjectId, responseModel = responseModel, requestWebMode = requestWebMode,
            requestReasoning = requestReasoning, requestXSearch = requestXSearch, requestCode = requestCode,
            requestBuild = requestBuild, requestAgent = requestAgent, requestPersonality = requestPersonality)
    }

    /** Opens the stream and hands every outcome back to [_persistResult], [_failedReply] and [_finishReply]. */
    private fun _streamReply(plan: SendPlan, userText: String) {
        replyJob = viewModelScope.launch {
            var accumulator: com.dua.ai.network.StreamAccumulator? = null
            try {
                val ownFiles = if (plan.files.any(plan.convert)) withContext(Dispatchers.Default) { PhotoAttachmentReader.convertToJpeg(plan.files, plan.convert) } else plan.files
                val projectContext = engineProjectContext(plan.requestProjectId)
                val prepared = ownFiles + projectContext.attachments
                check(prepared.size <= 3 || !separateChatEngine) { "Use at most 3 files across project sources and this message." }
                if (separateChatEngine) check(prepared.sumOf { java.util.Base64.getDecoder().decode(it.data).size.toLong() } <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Project sources and message attachments must fit within 2 MB total." }
                val persistence = plan.persistenceOwner?.let { owner -> withContext(Dispatchers.Default) {
                    com.dua.ai.account.ReplyPersistenceCodec.create(activeId, owner, plan.persistenceBase, plan.userMessage, plan.reply.id,
                        plan.request.turns.filter { it.state == "complete" }.takeLast(if (plan.request.context != null) 400 else 39), userText)
                } }
                account.withToken { token ->
                    val attempt = com.dua.ai.network.StreamAccumulator(plan.startedAt).also { accumulator = it }
                    check(projectContext.ownerId == null || account.state.value.user?.id == projectContext.ownerId) { "Account changed. Retry the project chat." }
                    collectReply(plan.reply.id, attempt, api.streamReply(plan.responseModel, plan.request.turns, prepared,
                        projectContext.memory, token, plan.requestWebMode.takeIf { plan.requestBuild == null }, plan.requestReasoning && plan.requestBuild == null,
                        plan.request.historyBytes, plan.request.context, plan.requestXSearch && plan.requestBuild == null, plan.requestCode && plan.requestBuild == null, plan.requestBuild, persistence, agent = plan.requestAgent && plan.requestBuild == null, freeModel = requestFreeModel(), personality = plan.requestPersonality), plan.request)
                }
                if (accumulator?.snapshot()?.text.isNullOrBlank()) throw DuaApiException(EMPTY_REPLY)
                if (generation == plan.currentGeneration) {
                    _persistResult(plan, "complete") { accumulator!!.snapshot(ended = true) }
                    offlineRetry.delivered()
                    maybeAutoTitle(activeId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                com.dua.ai.diagnostics.DuaLog.soft(com.dua.ai.diagnostics.SoftErrorArea.ChatReply, failure)
                if (generation == plan.currentGeneration) {
                    val partial = accumulator?.snapshot(ended = true)
                    if (partial != null && partial.text.isNotBlank()) {
                        // The request reached the model: keep the turn and its partial reply; Retry regenerates it.
                        _persistResult(plan, "stopped") { partial }
                        _error.value = "Reply interrupted: ${failureText(failure)} Partial reply kept; tap retry."
                    } else {
                        _failedReply(plan, userText, failure)
                    }
                }
            } finally {
                _finishReply(plan.currentGeneration)
            }
        }
    }

    /** Landing the reply: drop the delivered uploads, clear the composer files, and store the final text. */
    private fun _persistResult(plan: SendPlan, state: String, final: () -> com.dua.ai.network.StreamSnapshot) {
        _uploads.update { list -> list.filterNot { it.ref?.path in plan.sentPaths } }
        clearAttachments()
        val result = final()
        updateMessage(plan.reply.id) { it.copy(text = result.text, thinking = result.thinking, thinkingSeconds = result.thinkingSeconds, notice = result.notice, codeRuns = result.codeRuns, build = result.build ?: it.build, proposal = result.proposal ?: it.proposal, approvals = result.approvals.ifEmpty { it.approvals }, coding = result.coding ?: it.coding, state = state) }
    }

    /** Nothing reached the model: take the turn back out and, when it was a plain text send, queue it for reconnect. */
    private fun _failedReply(plan: SendPlan, userText: String, failure: Exception) {
        _messages.value = _messages.value.filterNot { it.id == plan.userMessage.id || it.id == plan.reply.id }
        replyWebModes.remove(plan.reply.id); replyReasoning.remove(plan.reply.id); replyAgent.remove(plan.reply.id); replyCode.remove(plan.reply.id); replyBuild.remove(plan.reply.id)
        _draft.value = userText
        _failedPrompt.value = userText
        // Offline, text-only, not an edit: send it again once the phone reconnects (at most once).
        val queued = offlineRetry.sendFailed(activeId, userText,
            offline = !plan.edited && plan.files.isEmpty() && plan.storedFiles.isEmpty() && (!_online.value || failure is java.io.IOException))
        _error.value = if (queued) "You're offline. Dua will send this message when you're back online." else failureText(failure)
    }

    /** The one tail that always runs: clear the typing flag, save, and notify if the user is elsewhere. */
    private fun _finishReply(currentGeneration: Int) {
        if (generation == currentGeneration) {
            _isTyping.value = false; saveHistory()
            if (!foreground && _error.value == null && localSettings.value.notifications && _messages.value.lastOrNull()?.isUser == false)
                com.dua.ai.settings.LocalReplyNotifications.show(getApplication(), activeId)
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
        /** Draft typing is saved this long after the last keystroke (and at once on stop or send). */
        const val DRAFT_SAVE_DELAY_MS = 500L
    }
}
