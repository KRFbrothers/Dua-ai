package com.dua.ai.viewmodel

import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The ChatViewModel state and actions the memory-notes feature reads and drives. Implemented by ChatViewModel. */
internal interface MemoryHost {
    /** The active project id; memory notes are Personal-chat only. */
    val _projectId: MutableStateFlow<String>
    /** The one device-preferences store; ChatSettings, ChatProjects and ChatAccount read the same instance. */
    val localPreferences: com.dua.ai.settings.DevicePreferences
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    /** The open conversation, read to propose notes from recent user turns. */
    val _messages: MutableStateFlow<List<ChatMessage>>
    val account: com.dua.ai.account.AccountRepository
    /** Shared with ChatDataControls: a sync or reply in flight blocks a memory write. */
    fun syncBlocked(): Boolean
    /** Writes the shared integration notice line. */
    fun integrationNotice(text: String)
}

/** Personal-memory note proposals, their review/dismiss flow and the share-target write, moved out of ChatViewModel.kt unchanged. */
internal class ChatMemory(host: MemoryHost) : MemoryHost by host {

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
    internal fun suggestAgentMemory(fact: String) {
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

}
