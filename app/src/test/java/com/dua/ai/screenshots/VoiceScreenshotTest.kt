package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.settings.SettingsContent
import com.dua.ai.ui.settings.SettingsUiState
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.LiveVoiceContent
import com.dua.ai.ui.voice.PickerItem
import com.dua.ai.ui.voice.VoicePickerContent
import com.dua.ai.ui.voice.VoiceSettingsContent
import com.dua.ai.ui.voice.VoiceSettingsUiState
import com.dua.ai.voice.VoiceCodec
import com.dua.ai.voice.live.LiveCaption
import com.dua.ai.voice.live.LivePhase
import com.dua.ai.voice.live.LiveState
import com.dua.ai.voice.live.LiveUsage
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 4 Voice v2 surfaces. Record with ./gradlew :app:recordRoborazziDebug -Pdua.screenshots */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VoiceScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"
    private fun path(name: String) = "build/outputs/roborazzi/${name}_$mode.png"

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path(name))
    }

    @Test fun voiceSettings() = shot("30_voice_settings") {
        VoiceSettingsContent(VoiceData.settings, onBack = {}, onPicker = {}, onProvider = {}, onSpeed = {}, onPreview = {})
    }

    @Test fun voiceSettingsDevice() = shot("31_voice_settings_device") {
        VoiceSettingsContent(VoiceData.settings.copy(preferences = VoiceData.settings.preferences.copy(generatedVoice = false, replyLanguage = "ur-PK"),
            deviceVoices = listOf(com.dua.ai.voice.VoiceOption("ur-pk-x-urm-local", "Urdu (Pakistan) · urm", "On device · quality 400"))),
            onBack = {}, onPicker = {}, onProvider = {}, onSpeed = {}, onPreview = {})
    }

    @Test fun voicePicker() = shot("32_voice_picker") {
        val provider = VoiceData.settings.config!!.provider("xai")!!
        VoicePickerContent(title = "Grok voices", intro = "Tap a voice to choose it and hear a sample.",
            items = provider.voices.map { PickerItem(it.id, it.name, it.description) }, selected = "ara", previewing = "ara",
            onBack = {}, onSelect = {}, onPreview = {})
    }

    @Test fun replyLanguagePicker() = shot("33_reply_language") {
        val provider = VoiceData.settings.config!!.provider("xai")!!
        VoicePickerContent(title = "Reply language", intro = "Auto lets Grok detect the language from the reply.",
            items = provider.languages.map { PickerItem(it.id, it.name, it.note) }, selected = "hi", previewing = null, onBack = {}, onSelect = {}, onPreview = null)
    }

    private fun liveShot(name: String, content: @Composable () -> Unit) {
        // The orb animates forever: drive the clock by hand so the capture is deterministic.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage(path(name))
    }

    @Test fun liveListening() = liveShot("35_live_listening") {
        LiveVoiceContent(VoiceData.live(LivePhase.LISTENING, "Listening… just start talking.", level = 0.35f), starting = false, error = "", fallback = false,
            usage = VoiceData.usage, voiceName = "Ara", onStart = {}, onMute = {}, onEnd = {}, onClose = {}, onHandsFree = {})
    }

    @Test fun liveSpeaking() = liveShot("36_live_speaking") {
        LiveVoiceContent(VoiceData.live(LivePhase.SPEAKING, "Speaking… talk anytime to interrupt.", level = 0.7f, speaking = true), starting = false, error = "",
            fallback = false, usage = VoiceData.usage, voiceName = "Ara", onStart = {}, onMute = {}, onEnd = {}, onClose = {}, onHandsFree = {})
    }

    @Test fun liveIdle() = liveShot("37_live_start") {
        LiveVoiceContent(LiveState(), starting = false, error = "", fallback = false, usage = VoiceData.usage, voiceName = "Ara",
            onStart = {}, onMute = {}, onEnd = {}, onClose = {}, onHandsFree = {})
    }

    @Test fun liveUnavailable() = liveShot("38_live_fallback") {
        LiveVoiceContent(LiveState(), starting = false, error = "You've used today's 30 live voice minutes. They reset at midnight UTC.", fallback = true,
            usage = LiveUsage(1800, 0, 1800, 0), voiceName = "Ara", onStart = {}, onMute = {}, onEnd = {}, onClose = {}, onHandsFree = {})
    }

    /** Tall screen so the Live conversation section at the end of the page is in the capture. */
    @Config(qualifiers = "+h1300dp")
    @Test fun voiceSettingsLive() = shot("39_voice_settings_live") {
        VoiceSettingsContent(VoiceData.settings.copy(liveUsage = VoiceData.usage, preferences = VoiceData.settings.preferences.copy(liveVoice = "ara")),
            onBack = {}, onPicker = {}, onProvider = {}, onSpeed = {}, onPreview = {})
    }

    @Test fun settingsVoiceRow() = shot("34_settings_voice_row") {
        SettingsContent(SettingsUiState(email = "fareed@example.com", theme = mode, voice = "Grok voice (xAI) · Ara · Hindi"),
            onBack = {}, onOpen = {}, onHaptic = {}, onGeneratedVoice = {})
    }
}

internal object VoiceData {
    private fun voice(id: String, description: String) = JSONObject().put("id", id).put("name", id.replaceFirstChar(Char::uppercase)).put("description", description)
    private fun language(id: String, name: String, note: String = "") = JSONObject().put("id", id).put("name", name).apply { if (note.isNotEmpty()) put("note", note) }
    val config = VoiceCodec.config(JSONObject().put("version", 1).put("tts", JSONObject().put("enabled", true).put("defaultProvider", "xai").put("providers", JSONArray()
        .put(JSONObject().put("id", "xai").put("name", "Grok").put("defaultVoice", "eve").put("maxCharacters", 2000).put("price", "$15 per 1M characters")
            .put("speed", JSONObject().put("min", 0.7).put("max", 1.5).put("default", 1))
            .put("voices", JSONArray(listOf(voice("eve", "Energetic and upbeat · British accent"), voice("ara", "Warm and friendly"), voice("rex", "Confident and clear"),
                voice("sal", "Smooth and balanced"), voice("leo", "Authoritative and strong"), voice("una", "Calm and soothing"), voice("orion", "Deep and steady"))))
            .put("languages", JSONArray(listOf(language("auto", "Auto"), language("en", "English"), language("hi", "Hindi"),
                language("ur", "Urdu", "Auto-detected (not on the provider's list)"), language("ar-SA", "Arabic (Saudi Arabia)"), language("bn", "Bengali"), language("fr", "French")))))
        .put(JSONObject().put("id", "gemini").put("name", "Gemini").put("model", "gemini-3.8-flash-tts").put("defaultVoice", "Kore").put("maxCharacters", 1000)
            .put("price", "about $0.00225 per 10 s of audio").put("voices", JSONArray(listOf(voice("Kore", "Firm"), voice("Puck", "Upbeat"))))
            .put("languages", JSONArray(listOf(language("auto", "Auto (detected from the reply)")))))))
        .put("live", JSONObject().put("enabled", true).put("model", "grok-voice-latest").put("defaultVoice", "eve").put("dailyMinutes", 30).put("maxSessionMinutes", 10)
            .put("pricePerMinute", "$0.08").put("voices", JSONArray(listOf(voice("eve", "Energetic and upbeat"), voice("ara", "Warm and friendly"), voice("rex", "Confident and clear"))))
            .put("languages", JSONArray(listOf(language("auto", "Auto"), language("hi", "Hindi"))))))
    val usage = LiveUsage(usedSeconds = 7 * 60, activeSeconds = 0, limitSeconds = 30 * 60, resetAt = 0)
    fun live(phase: LivePhase, status: String, level: Float, speaking: Boolean = false) = LiveState(phase = phase, status = status, level = level,
        elapsedSeconds = 134, maxSeconds = 600, voice = "ara", captions = listOf(
            LiveCaption("user:1", true, "Kal Delhi mein mausam kaisa rahega?", true),
            LiveCaption("dua:1", false, "Kal Delhi mein dhoop rahegi, din mein lagbhag 31 degree aur shaam ko halki thandak.", true),
            LiveCaption("user:2", true, if (speaking) "Aur Lahore mein?" else "Aur Lahore mein", speaking),
        ) + if (speaking) listOf(LiveCaption("dua:2", false, "Lahore mein bhi mausam saaf rahega, lekin thoda zyada garam —", false)) else emptyList())
    val settings = VoiceSettingsUiState(config = config, signedIn = true,
        preferences = LocalPreferences(generatedVoice = true, voiceProvider = "xai", voiceName = "ara", voiceSpeed = 1.1f, replyLanguage = "hi", inputLanguage = "hi-IN"))
}
