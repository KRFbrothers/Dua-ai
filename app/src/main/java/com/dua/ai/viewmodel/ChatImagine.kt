package com.dua.ai.viewmodel

import android.app.Application
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The ChatViewModel state and actions the Imagine feature reads and drives. Implemented by ChatViewModel. */
internal interface ImagineHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val account: com.dua.ai.account.AccountRepository
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val mediaCache: com.dua.ai.imagine.MediaCache
    val imagineApi: com.dua.ai.imagine.ImagineApi
    val cloudImagineApi: com.dua.ai.imagine.ImagineApi
    val pollJobs: MutableMap<String, Job>
    val foreground: Boolean
    val activeId: String
    val generation: Int
    var replyJob: Job?
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _cloudImagine: MutableStateFlow<com.dua.ai.imagine.CloudImagine?>
    val _switches: MutableStateFlow<com.dua.ai.setup.RemoteSwitches>
    val _isTyping: MutableStateFlow<Boolean>
    val _error: MutableStateFlow<String?>
    val _failedPrompt: MutableStateFlow<String?>
    val _imagine: MutableStateFlow<Boolean>
    val _imagineOptions: MutableStateFlow<com.dua.ai.imagine.ImagineOptions>
    val _imagineUsage: MutableStateFlow<com.dua.ai.imagine.ImagineUsage?>
    val _agent: MutableStateFlow<Boolean>
    val _reasoning: MutableStateFlow<Boolean>
    val _webMode: MutableStateFlow<String?>
    val _xSearch: MutableStateFlow<Boolean>
    val _runCode: MutableStateFlow<Boolean>
    val _dataBusy: MutableStateFlow<Boolean>
    val _preparingFiles: MutableStateFlow<Boolean>
    val _readingFiles: MutableStateFlow<Boolean>
    val _recordingSince: MutableStateFlow<Long?>
    val _editingId: MutableStateFlow<String?>
    val _draft: MutableStateFlow<String>
    val _attachments: MutableStateFlow<List<com.dua.ai.attachments.ChatAttachment>>
    val _uploads: MutableStateFlow<List<PendingUpload>>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val _selectedModel: MutableStateFlow<String>
    fun refreshCloudImagine()
    fun leaveBuild()
    fun saveHistory()
    fun refreshSyncBusy()
    fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage)
    fun deleteStoredPaths(paths: Collection<String>)
    fun discardUploads()
    fun startNewChat()
    fun sendMessage(userText: String)
}

/** Imagine image/video generation, video polling and usage, moved out of ChatViewModel.kt unchanged. */
internal class ChatImagine(host: ImagineHost) : ImagineHost by host {

    internal fun imagineConfig() = com.dua.ai.imagine.CloudImagine.merge(_integrations.value.imagine, _cloudImagine.value)
    /** Imagine chip: turns the composer into image/video generation (Reasoning, Search and X off). */
    fun setImagine(enabled: Boolean) {
        if (_isTyping.value) return
        if (!enabled) { _imagine.value = false; return }
        if (_switches.value.imagine) { _error.value = com.dua.ai.setup.RemoteSwitches.IMAGINE_PAUSED; return }
        refreshCloudImagine()
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

    /** Images destination reuses Imagine's auth, capability, upload and generation guards. Never falls back to text chat. */
    fun generateImageFromHub(prompt: String, aspect: String): Boolean {
        if (_dataBusy.value || _isTyping.value || _preparingFiles.value) return false
        if (prompt.isBlank() || prompt.length > com.dua.ai.imagine.ImagineCodec.MAX_PROMPT) { _error.value = "Describe an image in 1 to 4,000 characters."; return false }
        if (_editingId.value != null) { _error.value = "Finish or cancel the edited chat message before creating an image."; return false }
        setImagine(true)
        if (!_imagine.value) return false
        if (imagineConfig()?.imageEnabled != true) { _error.value = imagineConfig()?.imageReason ?: "Image generation needs setup."; return false }
        if (_draft.value.isNotBlank()) {
            if (_attachments.value.isNotEmpty() || _uploads.value.isNotEmpty() || _readingFiles.value || _recordingSince.value != null) {
                _error.value = "Send or remove the current draft attachments before starting a separate image request."; return false
            }
            // Save an unrelated text draft in its own conversation instead of overwriting it.
            startNewChat()
        }
        setImagineOptions(_imagineOptions.value.copy(kind = com.dua.ai.imagine.ImagineInfo.IMAGE, aspectRatio = aspect))
        sendMessage(prompt)
        return _isTyping.value
    }


    internal fun sendImagine(userText: String) {
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
                    val cloud = com.dua.ai.imagine.CloudImagine.routes(_cloudImagine.value, options, sources.size)
                    // Google Cloud: always one image. Node API providers never receive the Google Cloud provider id.
                    val sent = if (cloud) options.copy(count = 1, provider = null)
                        else options.copy(provider = options.provider?.takeIf { it != com.dua.ai.imagine.CloudImagine.PROVIDER })
                    val result = account.withToken { token ->
                        check(token != null) { "Sign in from Profile to use Imagine." }
                        (if (cloud) cloudImagineApi else imagineApi).image(prompt, sent, sources.map { it.path }, token)
                    }.let { if (cloud) it.copy(provider = com.dua.ai.imagine.CloudImagine.PROVIDER,
                        notice = it.notice ?: if (options.count > 1) "Google Cloud makes one image per request." else null) else it }
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

}
