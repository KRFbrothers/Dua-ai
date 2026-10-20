package com.dua.ai.viewmodel

import android.app.Application
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Before the live voice session connects: busy while asking the server, or why live voice cannot start. */
data class LiveStartState(val busy: Boolean = false, val error: String = "", val fallback: Boolean = false)

/** The ChatViewModel state and actions the voice/TTS feature reads and drives. Implemented by ChatViewModel. */
internal interface VoiceHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val account: com.dua.ai.account.AccountRepository
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val replyAudioCache: com.dua.ai.voice.AudioChunkCache
    val _online: MutableStateFlow<Boolean>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val projectId: StateFlow<String>
    val _switches: MutableStateFlow<com.dua.ai.setup.RemoteSwitches>
    val _isTyping: MutableStateFlow<Boolean>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val activeId: String
    val historyWriter: com.dua.ai.history.ChatHistoryWriter
    fun saveHistory()
    fun projectMemory(): String
    fun projectUnavailable(): String?
}

/** AI reply voice/TTS and the live voice session, moved out of ChatViewModel.kt unchanged. */
internal class ChatVoice(host: VoiceHost) : VoiceHost by host {

    private val generatedAudioApi = com.dua.ai.voice.GeneratedAudioApi()
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
    internal val _liveStart = MutableStateFlow(LiveStartState())
    val liveStart = _liveStart.asStateFlow()
    internal val _liveUsage = MutableStateFlow<com.dua.ai.voice.live.LiveUsage?>(null)
    val liveUsage = _liveUsage.asStateFlow()
    /** Live voice is offered by this backend and the owner is signed in. */
    fun liveVoiceAvailable(): Boolean = _integrations.value.voice?.live?.enabled == true && account.state.value.user != null &&
        projectId.value == com.dua.ai.projects.ProjectCodec.PERSONAL && localSettings.value.projects.find { it.id == projectId.value }?.let { !it.archived && it.instructions.isBlank() } == true
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
            projectId.value != com.dua.ai.projects.ProjectCodec.PERSONAL || localSettings.value.projects.find { it.id == projectId.value }?.instructions?.isNotBlank() == true -> "Use Voice mode for this project so its instructions apply. Live voice is available in Personal without custom instructions."
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

}
