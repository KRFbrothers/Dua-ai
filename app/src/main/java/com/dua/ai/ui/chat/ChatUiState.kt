package com.dua.ai.ui.chat

import androidx.compose.runtime.Immutable
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.ProviderCapability

/** Everything the chat surface renders. Built from ChatViewModel flows in [ChatRoute]. */
@Immutable
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val typing: Boolean = false,
    val error: String? = null,
    val model: String = "Dua",
    val providers: List<ProviderCapability> = IntegrationCapabilities.fallbackProviders,
    val webMode: String? = null,
    /** Reasoning mode for the next reply; Auto = no reasoning and no web mode. */
    val reasoning: Boolean = false,
    /** Thinking blocks shown expanded on first render (previews and screenshots). */
    val expandedThinking: Set<String> = emptySet(),
    /** Search works for the selected model (natively or through Brave). */
    val webSearchConfigured: Boolean = false,
    /** X search chip: Grok selected and offered by the server. */
    val xSearchAvailable: Boolean = false,
    val xSearch: Boolean = false,
    /** Messages up to this id are summarised for the model (long context); a divider marks the boundary. */
    val summarisedThroughId: String? = null,
    val pageReadingAvailable: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
    /** Stored-file uploads for the next message (progress, done or failed). */
    val uploads: List<com.dua.ai.files.PendingUpload> = emptyList(),
    /** Stored files available: Phase 2 backend + signed in. */
    val storedFiles: Boolean = false,
    /** Why stored files are unavailable (sign in / backend setup); empty when available. */
    val storedFilesHint: String = "",
    val storedFilesMaxBytes: Long = 0,
    /** Voice-note recording start time while recording. */
    val recordingSince: Long? = null,
    val readingFiles: Boolean = false,
    val attachmentStatus: String = "",
    val savedChats: List<SavedChat> = emptyList(),
    val speakingId: String? = null,
    val speechAvailable: Boolean = true,
    val generatedVoice: Boolean = false,
    val deviceVoiceStatus: String = "",
    val deviceVoiceActive: Boolean = false,
    val generatedVoiceStatus: String = "",
    val generatedVoiceActive: Boolean = false,
    val greeting: String = "",
    val suggestions: List<PromptSuggestion> = emptyList(),
    /** Imagine mode for the next message, its options, the server's Imagine config (null hides the chip) and today's usage. */
    val imagine: Boolean = false,
    val imagineOptions: com.dua.ai.imagine.ImagineOptions = com.dua.ai.imagine.ImagineOptions(),
    val imagineConfig: com.dua.ai.imagine.ImagineConfig? = null,
    val imagineUsage: com.dua.ai.imagine.ImagineUsage? = null,
    /** Run code for the next reply and the server's Run code capability (null hides the chip on older backends). */
    val runCode: Boolean = false,
    val codeConfig: com.dua.ai.setup.CodeExecutionConfig? = null,
    /** "Ran code" blocks shown expanded on first render (previews and screenshots). */
    val expandedCode: Set<String> = emptySet(),
) {
    val canSend: Boolean get() = (draft.isNotBlank() || attachments.isNotEmpty() || uploads.any { it.ref != null }) && !typing && !readingFiles &&
        uploads.none { it.uploading } && recordingSince == null
    val modelLabel: String get() = model
    val auto: Boolean get() = !reasoning && webMode == null && !xSearch && !imagine && !runCode
    /** Imagine has a photo to edit or animate. */
    val imagineSource: Boolean get() = uploads.any { it.ref != null && it.mimeType.startsWith("image/") }
    val videoAvailable: Boolean get() = imagineConfig?.videoEnabled == true
    val reasoningSupported: Boolean get() = providers.firstOrNull { it.name == model }?.supportsReasoning ?: true
}

/** User actions from the chat surface. Defaults are no-ops so previews and screenshot tests stay small. */
@Immutable
class ChatActions(
    val onDraftChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val retryWebMode: (String) -> String? = { null },
    val onSpeak: (ChatMessage) -> Unit = {},
    val onStopSpeech: () -> Unit = {},
    val onUseDeviceVoice: () -> Unit = {},
    val onWebMode: (String?) -> Unit = {},
    val onReasoning: (Boolean) -> Unit = {},
    val onXSearch: (Boolean) -> Unit = {},
    val onAuto: () -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onOpenChat: (String) -> Unit = {},
    val onDeleteChat: (String) -> Unit = {},
    val onPickCamera: () -> Unit = {},
    val onPickPhotos: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    val onClearAttachments: () -> Unit = {},
    val onCancelUpload: (String) -> Unit = {},
    val onPickAudio: () -> Unit = {},
    val onStartRecording: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onCancelRecording: () -> Unit = {},
    val onOpenFile: (com.dua.ai.files.FileRef) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenVoice: () -> Unit = {},
    val onOpenSetup: () -> Unit = {},
    val onImagine: (Boolean) -> Unit = {},
    val onImagineOptions: (com.dua.ai.imagine.ImagineOptions) -> Unit = {},
    val imagine: ImagineActions = ImagineActions(),
    val onRunCode: (Boolean) -> Unit = {},
)

/** Bottom sheets the chat screen can show. */
enum class ChatSheet { Model, Attach, Audio, Imagine }
