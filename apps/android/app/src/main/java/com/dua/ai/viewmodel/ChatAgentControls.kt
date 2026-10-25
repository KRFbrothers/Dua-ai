package com.dua.ai.viewmodel

import com.dua.ai.files.PendingUpload
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The ChatViewModel state and actions the Agent, Run code, Build, pull request, approval and X search controls read and drive. Implemented by ChatViewModel. */
internal interface AgentControlsHost {
    val viewModelScope: CoroutineScope
    val account: com.dua.ai.account.AccountRepository
    val api: com.dua.ai.network.DuaApiClient
    val _isTyping: MutableStateFlow<Boolean>
    val _switches: MutableStateFlow<com.dua.ai.setup.RemoteSwitches>
    val _error: MutableStateFlow<String?>
    val _agentAvailable: MutableStateFlow<Boolean>
    val _agent: MutableStateFlow<Boolean>
    val _reasoning: MutableStateFlow<Boolean>
    val _xSearch: MutableStateFlow<Boolean>
    val _imagine: MutableStateFlow<Boolean>
    val _runCode: MutableStateFlow<Boolean>
    val _webMode: MutableStateFlow<String?>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _online: MutableStateFlow<Boolean>
    val _readingFiles: MutableStateFlow<Boolean>
    val _preparingFiles: MutableStateFlow<Boolean>
    val _draft: MutableStateFlow<String>
    val _attachments: MutableStateFlow<List<com.dua.ai.attachments.ChatAttachment>>
    val _uploads: MutableStateFlow<List<PendingUpload>>
    val _editingId: MutableStateFlow<String?>
    val _selectedModel: MutableStateFlow<String>
    val _buildRepositories: MutableStateFlow<com.dua.ai.setup.BuildRepositories>
    val _buildRepo: MutableStateFlow<String?>
    val _buildPr: MutableStateFlow<Map<String, com.dua.ai.ui.chat.BuildPrUiState>>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    fun leaveBuild()
    fun selectAuto()
    fun sendMessage(userText: String)
    fun saveHistory()
    fun codeUsable(): Boolean
    fun buildUsable(): Boolean
    fun xSearchUsable(): Boolean
}

/** Agent mode, Run code, Build mode, Build pull requests, agent approvals and X search toggles, moved out of ChatViewModel.kt unchanged. */
internal class ChatAgentControls(host: AgentControlsHost) : AgentControlsHost by host {

    fun setAgent(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled && _switches.value.agent) { _error.value = com.dua.ai.setup.RemoteSwitches.AGENT_PAUSED; return }
        if (enabled && !_agentAvailable.value) { _error.value = "Agent mode needs the Google Cloud engine."; return }
        _agent.value = enabled
        if (enabled) { _reasoning.value = false; _xSearch.value = false; _imagine.value = false; _runCode.value = false; _webMode.value = null; leaveBuild() }
        _error.value = null
    }
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
    fun setBuildRepository(repository: String?) {
        if (_isTyping.value) return
        if (repository == null) { leaveBuild(); return }
        if (!com.dua.ai.model.BuildInfos.validRepository(repository)) return
        buildUnavailableReason()?.let { _error.value = it; return }
        if (!buildUsable()) {
            val integrations = _integrations.value
            val config = integrations.build ?: run {
                _error.value = "Build configuration changed. Refresh Feature setup and retry."
                return
            }
            val model = listOf("Dua", "Gemini").firstOrNull { name ->
                name in config.providers && integrations.providers.any { it.name == name && it.configured }
            } ?: run {
                _error.value = "Build mode needs a configured Dua or Gemini provider. Refresh Feature setup."
                return
            }
            _selectedModel.value = model
        }
        _reasoning.value = false; _webMode.value = null; _xSearch.value = false; _runCode.value = false; _imagine.value = false; _agent.value = false
        _buildRepo.value = repository
        _error.value = null
        saveHistory()
    }
    fun buildPrUnavailableReason(): String? {
        val pr = _integrations.value.build?.pr
        return when {
            pr == null -> "Creating pull requests needs the latest Dua backend. Deploy it, then refresh Feature setup."
            !pr.enabled -> pr.reason.ifBlank { "Creating pull requests is turned off on the server." }
            account.state.value.user == null -> "Sign in from Profile to create pull requests. Only the owner account can."
            else -> null
        }
    }
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
    internal fun updateApproval(messageId: String, approval: com.dua.ai.model.AgentApproval, outcome: Boolean = false) {
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
    fun setXSearch(enabled: Boolean) {
        if (_isTyping.value) return
        if (enabled && !xSearchUsable()) { _error.value = "X search works with Grok when the server offers it."; return }
        _xSearch.value = enabled
        if (enabled) _agent.value = false
        _error.value = null
    }
}
