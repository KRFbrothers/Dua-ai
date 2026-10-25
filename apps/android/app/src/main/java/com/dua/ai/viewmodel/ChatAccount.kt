package com.dua.ai.viewmodel

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.setup.AutomaticCloudSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dua.ai.viewmodel.ChatViewModel.CloudPreview
import com.dua.ai.viewmodel.ChatViewModel.EmailAuthState
import com.dua.ai.viewmodel.ChatViewModel.GoogleSignInState

/** The ChatViewModel state and actions the account, sign-in and cloud review flows read and drive. Implemented by ChatViewModel. */
internal interface AccountHost {
    val viewModelScope: CoroutineScope
    val account: com.dua.ai.account.AccountRepository
    val automaticSync: AutomaticCloudSync
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val localPreferences: com.dua.ai.settings.DevicePreferences
    val preferences: android.content.SharedPreferences
    val historyWriter: com.dua.ai.history.ChatHistoryWriter
    val draftSaver: com.dua.ai.history.Debouncer
    val activeId: String
    var emailAuthJob: Job?
    val _cloudPreview: MutableStateFlow<CloudPreview?>
    val _googleSignIn: MutableStateFlow<GoogleSignInState>
    val _emailAuth: MutableStateFlow<EmailAuthState>
    val _dataBusy: MutableStateFlow<Boolean>
    val _isTyping: MutableStateFlow<Boolean>
    val _buildPr: MutableStateFlow<Map<String, com.dua.ai.ui.chat.BuildPrUiState>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val _webMode: MutableStateFlow<String?>
    val _xSearch: MutableStateFlow<Boolean>
    fun refreshSyncBusy()
    fun syncBlocked(): Boolean
    fun liveProjectBusy(): Boolean
    fun flushHistory()
    fun currentDeletionIds(): List<String>
    fun bindCloudHistory(chats: List<SavedChat>, deleted: List<String>)
    fun googleSignInFailed(message: String?)
}

/** Account actions, Google/email sign-in, phone number and cloud review/download, moved out of ChatViewModel.kt unchanged. */
internal class ChatAccount(host: AccountHost) : AccountHost by host {

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
    suspend fun setAccountPhoneNumber(value: String, expectedAccountId: String) {
        check(!account.state.value.busy && !_googleSignIn.value.busy && !syncBlocked()) { "Finish the current account, reply or sync action, then try again." }
        account.busy(true); automaticSync.setBusy(true)
        try { account.setPhoneNumber(value, expectedAccountId) }
        finally { account.busy(false); refreshSyncBusy() }
    }
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
    internal suspend fun downloadCloudSnapshot(remote: com.dua.ai.account.CloudSnapshot) {
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
}
