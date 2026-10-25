package com.dua.ai.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The ChatViewModel state and actions the settings / appearance / writing feature reads and drives. Implemented by ChatViewModel. */
internal interface SettingsHost {
    val viewModelScope: CoroutineScope
    /** The one device-preferences store; ChatProjects, ChatDataControls and ChatAccount read the same instance. */
    val localPreferences: com.dua.ai.settings.DevicePreferences
    /** Appearance storage; ChatViewModel still owns the [appearance] stream that reads it. */
    val appearanceStore: com.dua.ai.settings.AppearanceStore
    /** The on-device model, shared with [ChatHistoryController] (automatic chat titles). */
    val onDeviceAi: com.dua.ai.ondevice.OnDeviceAi
    fun onDeviceAiEnabled(): Boolean
    /** The shared composer draft; polish and Summarize / Rewrite put their result here. */
    val _draft: MutableStateFlow<String>
    fun updateDraft(value: String)
}

/** On-device prompt polish, the Summarize / Rewrite sheet, appearance writes and the device-settings status, moved out of ChatViewModel.kt unchanged. */
internal class ChatSettings(host: SettingsHost) : SettingsHost by host {

    // ---- On-device prompt polish ----
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

    // ---- Summarize / Rewrite sheet ----
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

    // ---- Appearance writes and the device-settings status line ----
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

}
