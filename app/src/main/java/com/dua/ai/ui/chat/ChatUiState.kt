package com.dua.ai.ui.chat

import androidx.compose.runtime.Immutable
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.ProviderCapability

enum class ChatFeature(val title: String) {
    Agent("AI Agent"), Computer("Computer"), Images("Images"), Library("Library"), Projects("Projects"), Plugins("Plugins")
}

/** Everything the chat surface renders. Built from ChatViewModel flows in [ChatRoute]. */
@Immutable
data class ChatUiState(
    /** Identity of the active conversation, independent of its message count. */
    val chatId: String = "",
    val profileName: String = "",
    val profilePhotoUrl: String = "",
    val profileEmail: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val typing: Boolean = false,
    val error: String? = null,
    val model: String = "Dua",
    val providers: List<ProviderCapability> = IntegrationCapabilities.fallbackProviders,
    val webMode: String? = null,
    /** Reasoning mode for the next reply; Auto = no reasoning and no web mode. */
    val reasoning: Boolean = false,
    /** User message being edited in the composer (send replaces it and later turns). */
    val editingId: String? = null,
    /** Thinking blocks shown expanded on first render (previews and screenshots). */
    val expandedThinking: Set<String> = emptySet(),
    /** Search works for the selected model (natively or through Brave). */
    val webSearchConfigured: Boolean = false,
    /** X search chip: Grok selected and offered by the server. */
    val xSearchAvailable: Boolean = false,
    val xSearch: Boolean = false,
    /** Dua Agent toggle: offered when chat goes to the Google Cloud engine. */
    val agentAvailable: Boolean = false,
    /** Free OpenRouter models from the Cloud Run engine, and the chosen one (answers plain chat instead of [model]). */
    val freeModels: List<com.dua.ai.model.FreeModel> = emptyList(),
    val freeModel: com.dua.ai.model.FreeModel? = null,
    val agent: Boolean = false,
    /** Messages up to this id are summarised for the model (long context); a divider marks the boundary. */
    val summarisedThroughId: String? = null,
    val pageReadingAvailable: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
    /** Stored-file uploads for the next message (progress, done or failed). */
    val uploads: List<com.dua.ai.files.PendingUpload> = emptyList(),
    /** Stored files available: Phase 2 backend + signed in. */
    val storedFiles: Boolean = false,
    val inlineChatFiles: Boolean = false,
    /** Why stored files are unavailable (sign in / backend setup); empty when available. */
    val storedFilesHint: String = "",
    val storedFilesMaxBytes: Long = 0,
    /** Voice-note recording start time while recording. */
    val recordingSince: Long? = null,
    val readingFiles: Boolean = false,
    val attachmentStatus: String = "",
    val savedChats: List<SavedChat> = emptyList(),
    val serverChatDeletionEnabled: Boolean = false,
    val chatPinEnabled: Boolean = false,
    val chatDeletion: com.dua.ai.viewmodel.ChatViewModel.ChatDeletionState = com.dua.ai.viewmodel.ChatViewModel.ChatDeletionState(),
    val projects: List<com.dua.ai.projects.ChatProject> = com.dua.ai.projects.ProjectCodec.defaults,
    val projectTasksEnabled: Boolean = false,
    val taskDueDatesEnabled: Boolean = false,
    val projectMemoryEnabled: Boolean = false,
    val projectId: String = com.dua.ai.projects.ProjectCodec.PERSONAL,
    val projectNotice: String = "",
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
    /** "Polish my prompt · On-device" in the Imagine sheet; null hides it (unsupported phone or setting off). */
    val promptPolish: PromptPolishState? = null,
    /** "Remove background" (on-device) for photo attachments and Imagine images; false hides it. */
    val removeBackground: Boolean = false,
    /** Run code for the next reply and the server's Run code capability (null hides the chip on older backends). */
    val runCode: Boolean = false,
    val codeConfig: com.dua.ai.setup.CodeExecutionConfig? = null,
    val codeSnippetUnavailableReason: String? = "Code execution is unavailable in this view.",
    /** "Ran code" blocks shown expanded on first render (previews and screenshots). */
    val expandedCode: Set<String> = emptySet(),
    /** Build mode (Phase 5B, read-only): the repository this chat is tied to (null = off), the server capability
     *  (null on older backends), why it is unavailable (empty when usable) and the picker's repository list. */
    val buildRepo: String? = null,
    val buildConfig: com.dua.ai.setup.BuildModeConfig? = null,
    val buildHint: String = "",
    val buildRepositories: com.dua.ai.setup.BuildRepositories = com.dua.ai.setup.BuildRepositories(),
    /** "Files read" lists shown expanded on first render (previews and screenshots). */
    val expandedBuild: Set<String> = emptySet(),
    /** Build pull requests (Phase 5C): per-message Create PR progress/error, why Create PR is unavailable (empty when usable),
     *  and a message whose confirmation dialog starts open (previews and screenshots). */
    val buildPr: Map<String, BuildPrUiState> = emptyMap(),
    val buildPrHint: String = "",
    val confirmPr: String? = null,
    val feature: ChatFeature? = null,
    val homeTab: HomeTab = HomeTab.Chat,
) {
    val activeFeature: ChatFeature? get() = when {
        agent -> ChatFeature.Agent
        imagine -> ChatFeature.Images
        else -> feature
    }
    val canSend: Boolean get() = (draft.isNotBlank() || attachments.isNotEmpty() || uploads.any { it.ref != null }) && !typing && !readingFiles &&
        uploads.none { it.uploading } && recordingSince == null
    val modelLabel: String get() = freeModel?.name ?: model
    val auto: Boolean get() = !agent && !reasoning && webMode == null && !xSearch && !imagine && !runCode && buildRepo == null
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
    val onEditMessage: (String) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val retryWebMode: (String) -> String? = { null },
    val onSpeak: (ChatMessage) -> Unit = {},
    val onStopSpeech: () -> Unit = {},
    val onUseDeviceVoice: () -> Unit = {},
    val onWebMode: (String?) -> Unit = {},
    val onReasoning: (Boolean) -> Unit = {},
    val onAgent: (Boolean) -> Unit = {},
    val onXSearch: (Boolean) -> Unit = {},
    val onAuto: () -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
    val onSelectFreeModel: (String) -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onExitFeature: () -> Unit = {},
    val onNewFeatureSession: () -> Unit = {},
    val onAgentTask: (com.dua.ai.ui.agent.AgentTask) -> Unit = {},
    val onRefreshAgent: () -> Unit = {},
    val onSelectProject: (String) -> Unit = {},
    val onSaveProject: (String?, String, String, Boolean) -> Boolean = { _, _, _, _ -> false },
    val onCreateProject: (String, String, String, String) -> Boolean = { name, instructions, _, _ -> onSaveProject(null, name, instructions, false) },
    val onSaveProjectMemory: (String, Boolean, List<String>) -> Boolean = { _, _, _ -> false },
    val onSaveTask: (String, String?, String, Long?) -> Boolean = { _, _, _, _ -> false },
    val onToggleTask: (String, String) -> Boolean = { _, _ -> false },
    val onDeleteTask: (String, String) -> Boolean = { _, _ -> false },
    val onOpenChat: (String) -> Unit = {},
    val onDeleteChat: (String) -> Unit = {},
    val onRenameChat: (String, String) -> String? = { _, _ -> "Rename is unavailable." },
    val onPinChat: (String, Boolean) -> String? = { _, _ -> "Pin is unavailable." },
    val onDeleteServerChat: (String) -> Unit = {},
    val onDismissChatDeletion: () -> Unit = {},
    val onPickCamera: () -> Unit = {},
    val onPickPhotos: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    /** Photos/files/text dragged onto the chat from another app (already copied into Dua's cache). */
    val onDrop: (ChatDrop) -> Unit = {},
    val onClearAttachments: () -> Unit = {},
    val onRemoveAttachment: (Int) -> Unit = {},
    val onRemoveAttachmentBackground: (Int) -> Unit = {},
    val onPolishPrompt: () -> Unit = {},
    val onCancelUpload: (String) -> Unit = {},
    val onPickAudio: () -> Unit = {},
    val onStartRecording: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onCancelRecording: () -> Unit = {},
    val onOpenFile: (com.dua.ai.files.FileRef) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDestination: (com.dua.ai.ui.navigation.DuaDestination) -> Unit = {},
    val onOpenVoice: () -> Unit = {},
    /** Top-bar "Computer" pill: opens the Computer (coming soon) screen. */
    val onOpenComputer: () -> Unit = {},
    /** The composer's voice-mode button: live voice when available, else the device-speech voice mode. */
    val onOpenVoiceMode: () -> Unit = {},
    /** Light haptic (only when Haptic feedback is on in Settings; the system setting applies too). */
    val haptic: (ChatHaptic) -> Unit = {},
    /** Inline composer dictation (chat). Off = the mic opens the Voice page via [onOpenVoice]. */
    val inlineDictation: Boolean = false,
    val dictationLanguage: () -> String = { "auto" },
    val dictationAutoSend: () -> Boolean = { false },
    val onDictationNotice: (String) -> Unit = {},
    val onOpenSetup: () -> Unit = {},
    val onImagine: (Boolean) -> Unit = {},
    val onImagineOptions: (com.dua.ai.imagine.ImagineOptions) -> Unit = {},
    val imagine: ImagineActions = ImagineActions(),
    val onRunCode: (Boolean) -> Unit = {},
    val onRunCodeSnippet: (String) -> Boolean = { false },
    /** Build sheet opened: load the repository list. */
    val onOpenBuild: () -> Unit = {},
    /** Tie the chat to a repository (owner/name), or null to leave Build mode. */
    val onBuildRepository: (String?) -> Unit = {},
    val onRetryBuildRepositories: () -> Unit = {},
    /** Owner confirmed Create PR for the proposal on message id, with the (possibly edited) title. */
    val onCreatePr: (String, String) -> Unit = { _, _ -> },
    /** Owner tapped Approve (true) or Reject (false) on an Agent approval card: (message id, approval id, approve). */
    val onAgentApproval: (String, String, Boolean) -> Unit = { _, _, _ -> },
)

/** Bottom sheets the chat screen can show. */
enum class ChatSheet { Model, Attach, Audio, Imagine, Build, Projects, Profile }
