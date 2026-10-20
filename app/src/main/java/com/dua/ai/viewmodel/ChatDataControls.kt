package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ChatViewModel state and actions the data controls (import, export, Drive restore) read and drive. Implemented by ChatViewModel. */
internal interface DataControlsHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val localPreferences: com.dua.ai.settings.DevicePreferences
    val driveBackups: com.dua.ai.backup.drive.AutomaticDriveBackups
    val fullBackup: com.dua.ai.backup.FullBackupController
    val preferences: android.content.SharedPreferences
    val historyWriter: com.dua.ai.history.ChatHistoryWriter
    val draftSaver: com.dua.ai.history.Debouncer
    val activeId: String
    var driveRestoreJob: Job?
    var importProjects: List<com.dua.ai.projects.ChatProject>
    val _driveRestoreBusy: MutableStateFlow<Boolean>
    val _dataBusy: MutableStateFlow<Boolean>
    val _dataStatus: MutableStateFlow<String>
    val _importPreview: MutableStateFlow<List<SavedChat>?>
    val _buildPr: MutableStateFlow<Map<String, com.dua.ai.ui.chat.BuildPrUiState>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val _isTyping: MutableStateFlow<Boolean>
    fun syncBlocked(): Boolean
    fun refreshSyncBusy()
    fun liveProjectBusy(): Boolean
    fun cancelReply()
    fun flushHistory()
    fun saveHistory()
}

/** Drive restore, chat import review/commit and chat export, moved out of ChatViewModel.kt unchanged. */
internal class ChatDataControls(host: DataControlsHost) : DataControlsHost by host {

    fun reviewDriveBackup(id: String, password: CharArray) {
        if (syncBlocked()) { password.fill('\u0000'); fullBackup.notice("Finish the current app action before restoring a Drive backup."); return }
        _dataBusy.value = true; refreshSyncBusy()
        _driveRestoreBusy.value = true
        driveRestoreJob = viewModelScope.launch {
            var file: File? = null
            var handedOff = false
            try {
                fullBackup.notice("Downloading encrypted Drive backup…")
                file = driveBackups.downloadBackup(id)
                _dataBusy.value = false
                _driveRestoreBusy.value = false
                fullBackup.review(Uri.fromFile(file), password)
                handedOff = true
                fullBackup.state.first { !it.busy }
            } catch (cancelled: CancellationException) {
                if (!handedOff) fullBackup.notice("Drive restore cancelled. Nothing was imported.")
                throw cancelled
            }
            catch (failure: Exception) { fullBackup.notice(if (failure is com.dua.ai.backup.drive.DriveFailure) failure.message.orEmpty() else "Drive restore could not be prepared. Nothing was imported; reconnect Drive and retry.") }
            finally {
                if (!handedOff) password.fill('\u0000')
                withContext(kotlinx.coroutines.NonCancellable) {
                    withContext(Dispatchers.IO) { file?.delete() }
                    _driveRestoreBusy.value = false
                    _dataBusy.value = false; refreshSyncBusy()
                    driveRestoreJob = null
                }
            }
        }
    }

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

    internal fun commitReviewedImport(importedMemory: List<String>? = null, completion: (Boolean) -> Unit = {}): Job? {
        if (_dataBusy.value) return null
        if (liveProjectBusy()) { _dataStatus.value = "End live voice before importing chats."; return null }
        val imported = _importPreview.value ?: return null
        if (_buildPr.value.values.any { it.creating }) { _dataStatus.value = "Wait for the pull request to finish before importing."; return null }
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Saving imported chats…"
        cancelReply()
        flushHistory()
        // Use the latest local records at confirmation, keeping the active draft/selection.
        val original = _savedChats.value.toList()
        val originalProjects = localSettings.value.projects.toList()
        val originalMemory = localSettings.value.memory.toList()
        val importedProjects = importProjects.toList()
        val selectedId = activeId
        return viewModelScope.launch {
            var projectsWritten = false
            var mergedMemory = originalMemory
            var success = false
            try {
                if (importedMemory != null) mergedMemory = com.dua.ai.backup.FullBackupArchive.mergeMemory(originalMemory, importedMemory)
                val (merged, encoded, mergedProjects) = withContext(Dispatchers.Default) {
                    val adjusted = com.dua.ai.projects.ProjectImports.prepare(originalProjects, com.dua.ai.history.ChatBackup(imported, importedProjects))
                    val chats = ChatHistoryCodec.importCopies(original, adjusted.chats, projects = adjusted.projects)
                    Triple(chats, ChatHistoryCodec.encode(chats, selectedId), adjusted.projects)
                }
                fun cacheUnchanged() = activeId == selectedId && _savedChats.value == original && !draftSaver.pending &&
                    (importedMemory == null || localSettings.value.memory == if (projectsWritten) mergedMemory else originalMemory) &&
                    localSettings.value.projects == (if (projectsWritten) mergedProjects else originalProjects) && !_isTyping.value && _buildPr.value.values.none { it.creating }
                val changed = "The phone cache changed while preparing the import. Review and retry. No chats were imported."
                check(cacheUnchanged()) { changed }
                localPreferences.update(localSettings.value.copy(projects = mergedProjects, memory = if (importedMemory == null) localSettings.value.memory else mergedMemory))
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
                    success = true
                } else _dataStatus.value = "Import could not be saved. No chats were changed. Try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _dataStatus.value = failure.message ?: "Import could not be saved. No chats were changed. Try again." }
            finally {
                if (projectsWritten) runCatching { localPreferences.update(localSettings.value.copy(projects = originalProjects,
                    memory = if (importedMemory != null && localSettings.value.memory == mergedMemory) originalMemory else localSettings.value.memory)) }
                _dataBusy.value = false
                completion(success)
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
}
