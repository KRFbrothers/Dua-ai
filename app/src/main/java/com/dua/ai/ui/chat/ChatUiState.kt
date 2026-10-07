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
    val webSearchConfigured: Boolean = false,
    val pageReadingAvailable: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
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
) {
    val canSend: Boolean get() = (draft.isNotBlank() || attachments.isNotEmpty()) && !typing && !readingFiles
    val modelLabel: String get() = model
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
    val onSelectModel: (String) -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onOpenChat: (String) -> Unit = {},
    val onDeleteChat: (String) -> Unit = {},
    val onPickCamera: () -> Unit = {},
    val onPickPhotos: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    val onClearAttachments: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenVoice: () -> Unit = {},
    val onOpenSetup: () -> Unit = {},
)

/** Bottom sheets the chat screen can show. */
enum class ChatSheet { Model, Attach }
