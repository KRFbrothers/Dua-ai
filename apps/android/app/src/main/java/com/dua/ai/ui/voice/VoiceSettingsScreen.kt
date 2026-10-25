package com.dua.ai.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.*
import com.dua.ai.ui.theme.SettingsRowStyle
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.LanguageOption
import com.dua.ai.voice.NativeReplySpeaker
import com.dua.ai.voice.SpeechLanguages
import com.dua.ai.voice.TtsProvider
import com.dua.ai.voice.VoiceCodec
import com.dua.ai.voice.VoiceConfig
import com.dua.ai.voice.VoiceOption
import java.util.Locale

/** What Voice settings shows; built from preferences, the server voice catalog and the device engine. */
@Immutable
data class VoiceSettingsUiState(
    val config: VoiceConfig?, val signedIn: Boolean, val preferences: LocalPreferences,
    val deviceVoices: List<VoiceOption> = emptyList(), val previewing: Boolean = false, val status: String = "",
    val liveUsage: com.dua.ai.voice.live.LiveUsage? = null,
    val legacyGeneratedVoiceConfigured: Boolean = false,
    val busy: Boolean = false,
) {
    /** The AI provider in use, or null for the device voice. */
    val provider: TtsProvider? get() = if (!preferences.generatedVoice || !signedIn) null
        else VoiceCodec.ttsOptions(config, preferences.voiceProvider, "", "auto", 1f)?.let { config?.provider(it.provider) }
    val aiAvailable: Boolean get() = config?.ttsEnabled == true && signedIn
    val voice: VoiceOption get() = provider?.voice(preferences.voiceName)
        ?: deviceVoices.firstOrNull { it.id == preferences.deviceVoice } ?: VoiceOption("", "Device default", "Your phone's speech engine")
    val languages: List<LanguageOption> get() = provider?.languages ?: SpeechLanguages.DEVICE_REPLY
    val language: LanguageOption get() = provider?.language(preferences.replyLanguage)
        ?: SpeechLanguages.DEVICE_REPLY.firstOrNull { it.id == preferences.replyLanguage } ?: LanguageOption(preferences.replyLanguage, SpeechLanguages.name(preferences.replyLanguage))
    val speedRange: ClosedFloatingPointRange<Float> get() = provider?.let { p -> p.speed?.let { it.min..it.max } } ?: (if (provider == null) NativeReplySpeaker.MIN_RATE..NativeReplySpeaker.MAX_RATE else 1f..1f)
    val speed: Float get() = preferences.voiceSpeed.coerceIn(speedRange)
}

/** A short line in the reply language, used for previews. */
object VoiceSamples {
    fun text(language: String): String = when (language.substringBefore('-').lowercase(Locale.ROOT)) {
        "hi" -> "नमस्ते! मैं दुआ हूँ। मैं आपके जवाब इसी आवाज़ में पढ़ूँगी।"
        "ur" -> "السلام علیکم! میں دعا ہوں۔ میں آپ کے جوابات اسی آواز میں پڑھوں گی۔"
        "ar" -> "السلام عليكم! أنا دعاء. هكذا سأقرأ لك ردودي."
        "bn" -> "নমস্কার! আমি দুআ। আমি এই কণ্ঠে আপনার উত্তর পড়ব।"
        "fr" -> "Bonjour ! Je suis Dua. Voici comment je lirai mes réponses."
        "de" -> "Hallo! Ich bin Dua. So klinge ich, wenn ich dir meine Antworten vorlese."
        "es" -> "¡Hola! Soy Dua. Así sonaré cuando lea mis respuestas."
        "tr" -> "Merhaba! Ben Dua. Yanıtlarımı sana böyle okuyacağım."
        else -> "Assalamu alaikum! I'm Dua. This is how I'll sound when I read my replies to you."
    }
}

const val VOICE_PREVIEW_PREFIX = "voice-preview:"

@Composable
fun VoiceSettingsScreen(viewModel: ChatViewModel, voice: ReplyVoice, onBack: () -> Unit, onPicker: (String) -> Unit,
    onOpen: (DuaDestination) -> Unit) {
    val state = rememberVoiceSettingsState(viewModel, voice)
    VoiceSettingsContent(state, onBack = onBack, onPicker = onPicker,
        onProvider = { id -> voice.stopAll(); viewModel.updateSettings(selectProvider(viewModel.localSettings.value, id)) },
        onSpeed = { value -> viewModel.updateSettings(viewModel.localSettings.value.copy(voiceSpeed = (Math.round(value * 20) / 20f).coerceIn(0.5f, 2f))) },
        onPreview = { previewSelected(viewModel, voice) }, onOpen = onOpen,
        onGeneratedVoice = { enabled -> voice.stopAll(); viewModel.updateSettings(viewModel.localSettings.value.copy(generatedVoice = enabled)) },
        onAutoSend = { enabled -> viewModel.updateSettings(viewModel.localSettings.value.copy(dictationAutoSend = enabled)) },
        offlineTyping = { OfflineVoiceTypingSection(viewModel) })
}

@Composable
internal fun rememberVoiceSettingsState(viewModel: ChatViewModel, voice: ReplyVoice): VoiceSettingsUiState {
    val preferences by viewModel.localSettings.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val deviceVoices by voice.speaker.voices.collectAsState()
    val generated by voice.generated.state.collectAsState()
    val device by voice.speaker.state.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    val liveUsage by viewModel.liveUsage.collectAsState()
    androidx.compose.runtime.LaunchedEffect(account.user?.id, integrations.voice?.live?.enabled) { viewModel.loadLiveUsage() }
    val previewing = generated.activeId?.startsWith(VOICE_PREVIEW_PREFIX) == true || device.activeId?.startsWith(VOICE_PREVIEW_PREFIX) == true
    return VoiceSettingsUiState(integrations.voice, account.user != null, preferences, deviceVoices, previewing,
        generated.status.takeIf { generated.phase == "error" || generated.status.contains("unavailable", true) || generated.status.contains("interrupted", true) }
            ?: device.status.ifBlank { status }, liveUsage,
        legacyGeneratedVoiceConfigured = integrations.generatedVoiceConfigured, busy = integrations.busy)
}

internal fun selectProvider(current: LocalPreferences, id: String): LocalPreferences =
    if (id == "device") current.copy(generatedVoice = false)
    else current.copy(generatedVoice = true, voiceProvider = id, voiceName = if (id == current.voiceProvider) current.voiceName else "")

internal fun previewSelected(viewModel: ChatViewModel, voice: ReplyVoice) {
    val settings = viewModel.localSettings.value
    val ai = settings.generatedVoice && viewModel.integrations.value.generatedVoiceConfigured
    val options = if (ai) viewModel.replyTtsOptions() else null
    val language = options?.language ?: settings.replyLanguage
    val id = VOICE_PREVIEW_PREFIX + listOf(options?.provider ?: "device", options?.voice ?: "", settings.deviceVoice).joinToString(":")
    voice.preview(id, VoiceSamples.text(language), ai)
}

@Composable
fun VoiceSettingsContent(state: VoiceSettingsUiState, onBack: () -> Unit, onPicker: (String) -> Unit, onProvider: (String) -> Unit,
    onSpeed: (Float) -> Unit, onPreview: () -> Unit, onOpen: (DuaDestination) -> Unit = {}, onGeneratedVoice: (Boolean) -> Unit = {},
    onAutoSend: (Boolean) -> Unit = {}, offlineTyping: (@Composable () -> Unit)? = null) {
    val config = state.config
    val provider = state.provider
    DuaPageScaffold(title = "Voice", onBack = onBack) {
        SettingsGroup("Talk with Dua") {
            SettingsNavRow(Icons.Outlined.Mic, "Voice input", "Dictate, review, then send") { onOpen(DuaDestination.Voice) }
            SettingsRowDivider()
            com.dua.ai.ui.components.SettingsToggleRow(null, "Auto-send dictation", "Send when you stop speaking in chat",
                checked = state.preferences.dictationAutoSend, onCheckedChange = onAutoSend)
            SettingsRowDivider()
            if (config?.live?.enabled == true && state.signedIn)
                SettingsNavRow(Icons.Outlined.GraphicEq, "Live conversation", "Talk with Grok in real time · interrupt any time") { onOpen(DuaDestination.LiveVoice) }
            else SettingsNavRow(Icons.Outlined.GraphicEq, "Voice mode", "Talk with Dua and hear the reply out loud") { onOpen(DuaDestination.HandsFree) }
        }
        Text(when {
            config == null -> "Update the Dua backend to pick Grok or Gemini voices. The device voice works now."
            !state.signedIn -> "Sign in from Profile to use AI voices. The device voice works without an account."
            !config.ttsEnabled -> config.ttsReason.ifBlank { "AI voices are off on the server. The device voice is available." }
            else -> "Choose how Dua reads replies aloud. AI voices make audio only for the reply you tap."
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("Reply voice") {
            Choice("Device voice", "Free · works offline when the language is installed",
                provider == null && !(config == null && state.signedIn && state.preferences.generatedVoice)) { onProvider("device") }
            if (config == null) {
                SettingsRowDivider()
                SettingsToggleRow(Icons.AutoMirrored.Outlined.VolumeUp, "AI reply voice",
                    subtitle = when {
                        !state.legacyGeneratedVoiceConfigured && !state.preferences.generatedVoice -> "Needs backend setup · device voice stays available"
                        !state.signedIn && !state.preferences.generatedVoice -> "Sign in to use a generated voice"
                        else -> "Generates audio only for the reply you tap"
                    }, checked = state.preferences.generatedVoice,
                    enabled = !state.busy && (state.preferences.generatedVoice || (state.legacyGeneratedVoiceConfigured && state.signedIn)),
                    onCheckedChange = onGeneratedVoice)
            }
            config?.providers?.forEach { item ->
                SettingsRowDivider()
                Choice(providerLabel(item), listOfNotNull(item.model.ifBlank { null }, item.price.ifBlank { null }).joinToString(" · ").ifBlank { null },
                    provider?.id == item.id, enabled = state.aiAvailable) { onProvider(item.id) }
            }
        }
        SettingsGroup("Voice") {
            SettingsNavRow(Icons.Outlined.RecordVoiceOver, state.voice.name, state.voice.description.ifBlank { null },
                enabled = provider == null || provider.voices.size > 1) { onPicker(VoicePickerKind.VOICE) }
            SettingsRowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", style = SettingsRowStyle, color = SettingsTheme.colors.rowText, modifier = Modifier.weight(1f))
                Text(if (state.speedRange.start == state.speedRange.endInclusive) "Set by the voice" else "%.2f×".format(Locale.ROOT, state.speed).replace("0×", "×"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.speedRange.start < state.speedRange.endInclusive) {
                var dragging by remember(state.speed) { mutableFloatStateOf(state.speed) }
                Slider(value = dragging, colors = pageSliderColors(), onValueChange = { dragging = it }, onValueChangeFinished = { onSpeed(dragging) },
                    valueRange = state.speedRange, steps = (((state.speedRange.endInclusive - state.speedRange.start) / 0.1f + 0.5f).toInt() - 1).coerceAtLeast(0),
                    modifier = Modifier.padding(horizontal = Spacing.lg).semantics { contentDescription = "Speaking speed" })
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPreview, enabled = provider == null || state.aiAvailable, modifier = Modifier.heightIn(min = SettingsSizes.touch)) {
                    Icon(if (state.previewing) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle, null)
                    Text(if (state.previewing) "Stop sample" else "Play sample", modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
        SettingsGroup("Language") {
            SettingsNavRow(Icons.Outlined.Language, "Reply language", subtitle = state.language.note.ifBlank { null }, value = state.language.name) {
                onPicker(VoicePickerKind.REPLY_LANGUAGE)
            }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Mic, "Speech recognition", subtitle = "Dictation and voice mode", value = SpeechLanguages.name(state.preferences.inputLanguage)) {
                onPicker(VoicePickerKind.INPUT_LANGUAGE)
            }
        }
        offlineTyping?.invoke()
        Text(if (provider != null) "${provider.name} receives only the text of the reply you play. Audio is kept in memory for replays and never saved."
            else "The device voice runs on your phone. Install more voices in Android Settings › Text-to-speech.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        config?.live?.let { live ->
            SettingsGroup("Live conversation") {
                if (!live.enabled || !state.signedIn) Text(if (!state.signedIn) "Sign in to talk with Grok in real time." else live.reason.ifBlank { "Live voice is off on the server." },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
                else {
                    val liveVoice = live.voices.firstOrNull { it.id == state.preferences.liveVoice } ?: live.voices.firstOrNull { it.id == live.defaultVoice }
                    SettingsNavRow(Icons.Outlined.RecordVoiceOver, "Live voice", subtitle = liveVoice?.description?.ifBlank { null }, value = liveVoice?.name ?: "Default") { onPicker(VoicePickerKind.LIVE_VOICE) }
                    SettingsRowDivider()
                    val usage = state.liveUsage
                    Text((if (usage != null && usage.limitSeconds > 0) "${usage.leftSeconds / 60} of ${usage.limitSeconds / 60} min left today" else "${live.dailyMinutes} min a day") +
                        " · up to ${live.maxSessionMinutes} min per session · about ${live.pricePerMinute.ifBlank { "$0.08" }}/min. Uses the reply language and speed above.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
                }
            }
        }
        if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun providerLabel(item: TtsProvider) = when (item.id) { "xai" -> "Grok voice (xAI)"; "gemini" -> "Gemini voice (Google)"; "elevenlabs" -> "ElevenLabs"; else -> item.name }

object VoicePickerKind {
    const val VOICE = "voice"
    const val REPLY_LANGUAGE = "reply-language"
    const val INPUT_LANGUAGE = "input-language"
    const val LIVE_VOICE = "live-voice"
    /** Not a picker: opens the live conversation screen. */
    const val LIVE = "live"
}

/** One row on a picker page. */
@Immutable
data class PickerItem(val id: String, val title: String, val subtitle: String = "")

/** Picker page for a voice or language. [previewing] marks the voice whose sample is playing. */
@Composable
fun VoicePickerContent(title: String, intro: String, items: List<PickerItem>, selected: String, previewing: String?, onBack: () -> Unit,
    onSelect: (String) -> Unit, onPreview: ((String) -> Unit)?) {
    DuaPageScaffold(title = title, onBack = onBack) {
        if (intro.isNotBlank()) Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup(null, Modifier.selectableGroup()) {
            items.forEachIndexed { index, item ->
                if (index > 0) SettingsRowDivider()
                SettingsChoiceRow(item.title, item.id == selected, item.subtitle.ifBlank { null },
                    trailing = onPreview?.let { preview -> {
                        IconButton(onClick = { preview(item.id) }) {
                            Icon(if (previewing == item.id) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = (if (previewing == item.id) "Stop sample of " else "Play sample of ") + item.title)
                        }
                    } }) { onSelect(item.id) }
            }
        }
    }
}

@Composable
fun VoicePickerScreen(viewModel: ChatViewModel, voice: ReplyVoice, kind: String, onBack: () -> Unit) {
    val state = rememberVoiceSettingsState(viewModel, voice)
    val generated by voice.generated.state.collectAsState()
    val device by voice.speaker.state.collectAsState()
    val provider = state.provider
    val playing = (generated.activeId ?: device.activeId)?.takeIf { it.startsWith(VOICE_PREVIEW_PREFIX) }
    fun update(transform: (LocalPreferences) -> LocalPreferences) = viewModel.updateSettings(transform(viewModel.localSettings.value))
    when (kind) {
        VoicePickerKind.VOICE -> {
            val items = provider?.voices?.map { PickerItem(it.id, it.name, it.description) }
                ?: (listOf(PickerItem("", "Device default", "Your speech engine's default voice")) + state.deviceVoices.map { PickerItem(it.id, it.name, it.description) })
            val selected = provider?.voice(state.preferences.voiceName)?.id ?: state.preferences.deviceVoice
            VoicePickerContent(title = if (provider != null) "${provider.name} voices" else "Device voices",
                intro = if (provider != null) "Tap a voice to choose it and hear a sample." else "Voices installed for ${state.language.name}. Tap one to choose it and hear a sample.",
                items = items, selected = selected, previewing = playing?.removePrefix(VOICE_PREVIEW_PREFIX)?.split(":")?.getOrNull(if (provider != null) 1 else 2),
                onBack = onBack,
                onSelect = { id ->
                    voice.stopAll()
                    update { if (provider != null) it.copy(voiceName = id) else it.copy(deviceVoice = id) }
                    if (provider == null) voice.speaker.configure(viewModel.localSettings.value.replyLanguage, id, viewModel.localSettings.value.voiceSpeed)
                    previewSelected(viewModel, voice)
                },
                onPreview = { id ->
                    val current = if (provider != null) state.preferences.voiceName else state.preferences.deviceVoice
                    if (id != current) {
                        voice.stopAll()
                        update { if (provider != null) it.copy(voiceName = id) else it.copy(deviceVoice = id) }
                        if (provider == null) voice.speaker.configure(viewModel.localSettings.value.replyLanguage, id, viewModel.localSettings.value.voiceSpeed)
                    }
                    previewSelected(viewModel, voice)
                })
        }
        VoicePickerKind.REPLY_LANGUAGE -> VoicePickerContent(title = "Reply language",
            intro = when {
                provider == null -> "Language for the device voice. Auto uses your phone's language."
                provider.languages.size <= 1 -> "${provider.name} detects the language from each reply."
                else -> "Auto lets ${provider.name} detect the language from the reply."
            },
            items = state.languages.map { PickerItem(it.id, it.name, it.note) }, selected = state.language.id, previewing = null, onBack = onBack,
            onSelect = { id -> update { it.copy(replyLanguage = id) } }, onPreview = null)
        VoicePickerKind.LIVE_VOICE -> {
            val live = state.config?.live
            val voices = live?.voices.orEmpty()
            VoicePickerContent(title = "Live voice", intro = "The voice Grok uses in live conversations.",
                items = voices.map { PickerItem(it.id, it.name, it.description) }, selected = voices.firstOrNull { it.id == state.preferences.liveVoice }?.id ?: live?.defaultVoice.orEmpty(),
                previewing = null, onBack = onBack, onSelect = { id -> update { it.copy(liveVoice = id) } }, onPreview = null)
        }
        else -> VoicePickerContent(title = "Speech recognition",
            intro = "Language your phone listens for in dictation and voice mode. Your phone's speech service must support it.",
            items = SpeechLanguages.INPUT.map { PickerItem(it.id, it.name) }, selected = state.preferences.inputLanguage, previewing = null, onBack = onBack,
            onSelect = { id -> update { it.copy(inputLanguage = id) } }, onPreview = null)
    }
}

@Composable
private fun Choice(label: String, subtitle: String?, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) =
    SettingsChoiceRow(label, selected, subtitle, enabled, onClick = onSelect)
