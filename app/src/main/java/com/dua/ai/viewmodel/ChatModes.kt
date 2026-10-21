package com.dua.ai.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow

/** The ChatViewModel state and actions the composer modes read and drive. Implemented by ChatViewModel. */
internal interface ChatModesHost {
    val _isTyping: MutableStateFlow<Boolean>
    val _reasoning: MutableStateFlow<Boolean>
    val _agent: MutableStateFlow<Boolean>
    val _xSearch: MutableStateFlow<Boolean>
    val _imagine: MutableStateFlow<Boolean>
    val _runCode: MutableStateFlow<Boolean>
    val _webMode: MutableStateFlow<String?>
    val _error: MutableStateFlow<String?>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _selectedModel: MutableStateFlow<String>
    fun leaveBuild()
}

/** The per-session composer modes (Reasoning, Auto, web, Read link), moved out of ChatViewModel.kt unchanged. */
internal class ChatModes(host: ChatModesHost) : ChatModesHost by host {

    fun setReasoning(enabled: Boolean) { if (!_isTyping.value) { _reasoning.value = enabled; if (enabled) { _imagine.value = false; _agent.value = false; leaveBuild() } } }

    /** Auto chip: plain chat, no web and no Reasoning. */
    fun selectAuto() { if (_isTyping.value) return; _reasoning.value = false; _agent.value = false; _xSearch.value = false; _imagine.value = false; _runCode.value = false; leaveBuild(); setWebMode(null) }

    fun setWebMode(mode: String?) {
        if (_isTyping.value || mode !in listOf(null, "search", "page")) return
        webModeError(mode)?.let { _error.value = it; return }
        _webMode.value = mode
        if (mode != null) { _imagine.value = false; _agent.value = false; leaveBuild() }
        _error.value = null
    }
    fun webModeError(mode: String?, model: String = _selectedModel.value): String? = when {
        mode == "search" && !_integrations.value.searchAvailable(model) -> "Web search needs setup for $model. Open Feature setup to configure search, choose a model that searches natively, or choose Off or Read link."
        mode == "page" && !_integrations.value.pageReadingAvailable -> "Read link is unavailable. Refresh Feature setup, or choose Off."
        else -> null
    }
}
