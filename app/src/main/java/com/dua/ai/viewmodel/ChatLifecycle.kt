package com.dua.ai.viewmodel

import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.DuaApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ChatViewModel state and actions the reply lifecycle (retry a reply) reads and drives.
 * Implemented by ChatViewModel, which keeps the single declaration of [generation], [replyJob]
 * and [retryOriginal] — this interface only forwards, so no state is copied.
 */
internal interface LifecycleHost {
    val viewModelScope: CoroutineScope
    val api: DuaApiClient
    val account: com.dua.ai.account.AccountRepository
    val separateChatEngine: Boolean
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val appearance: StateFlow<com.dua.ai.settings.AppearancePreferences>
    val _agentAvailable: MutableStateFlow<Boolean>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _error: MutableStateFlow<String?>
    val _isTyping: MutableStateFlow<Boolean>
    val _preparingFiles: MutableStateFlow<Boolean>
    val _readingFiles: MutableStateFlow<Boolean>
    val _dataBusy: MutableStateFlow<Boolean>
    val _projectId: MutableStateFlow<String>
    val replyWebModes: MutableMap<String, String?>
    val replyAgent: MutableMap<String, Boolean>
    val replyReasoning: MutableMap<String, Boolean>
    val replyXSearch: MutableMap<String, Boolean>
    val replyCode: MutableMap<String, Boolean>
    val replyBuild: MutableMap<String, String>
    val activeId: String
    val generation: Int
    var replyJob: Job?
    var retryOriginal: ChatMessage?
    fun projectUnavailable(): String?
    fun personalityUnavailable(): String?
    fun xSearchUsable(model: String): Boolean
    fun codeUsable(model: String): Boolean
    fun buildUsable(model: String): Boolean
    fun buildUnavailableReason(): String?
    fun webModeError(mode: String?, model: String): String?
    fun requestTurns(history: List<ChatMessage>, legacy: List<ChatMessage>, inlineFiles: Boolean): ChatViewModel.RequestTurns
    fun requestFreeModel(): String?
    fun refreshSyncBusy()
    fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage)
    fun saveHistory()
    suspend fun engineProjectContext(projectId: String): EngineProjectContext
    suspend fun collectReply(id: String, accumulator: com.dua.ai.network.StreamAccumulator, events: kotlinx.coroutines.flow.Flow<com.dua.ai.network.ReplyEvent>, request: ChatViewModel.RequestTurns?)
}

/** Reply lifecycle: regenerate the last reply in place. Moved verbatim out of ChatViewModel.kt. */
internal class ChatLifecycle(private val host: LifecycleHost) : LifecycleHost by host {
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
}
