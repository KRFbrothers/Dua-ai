package com.dua.ai.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.MicPermission
import com.dua.ai.ui.voice.VoiceModeCallbacks
import com.dua.ai.ui.voice.VoiceModeContent
import com.dua.ai.ui.voice.VoiceModeUi
import com.dua.ai.voice.VoicePhase
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Voice mode: the composer's voice button (empty) and Send (typing), and the full-screen voice mode in idle,
 * listening, speaking with captions, captions off, light theme and the microphone rationale.
 * Record with: ./gradlew :app:recordRoborazziDebug -Pdua.screenshots --tests '*VoiceModeScreenshot*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VoiceModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val home = ChatUiState(greeting = "Good afternoon, Fareed", webSearchConfigured = true,
        codeConfig = CodeExecutionConfig(true, providers = setOf("Dua", "Gemini")))
    private val reply = "Here's a quick plan: start with 20 minutes of revision, take a short walk, then do two practice problems. Want me to turn it into a timetable?"

    private fun shot(name: String, theme: String = "dark", content: @Composable () -> Unit) {
        compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/voice/$name.png")
    }

    private fun voice(name: String, ui: VoiceModeUi, level: Float = 0f, theme: String = "dark") =
        shot(name, theme) { VoiceModeContent(ui, VoiceModeCallbacks(), level = { level }, motion = false) }

    @Test fun inputEmpty() = shot("1_input_empty") { ChatScreen(home, ChatActions()) }
    @Test fun inputTyping() = shot("2_input_typing") { ChatScreen(home.copy(draft = "What should I cook tonight?"), ChatActions()) }
    @Test fun idle() = voice("3_voice_idle", VoiceModeUi(firstName = "Fareed", notice = "Tap the orb when you're ready to talk."))
    @Test fun listening() = voice("4_voice_listening", VoiceModeUi(phase = VoicePhase.Listening, firstName = "Fareed", heard = "Help me plan my evening"), level = .7f)
    @Test fun thinking() = voice("5_voice_thinking", VoiceModeUi(phase = VoicePhase.Thinking, userText = "Help me plan my evening study session"))
    @Test fun speaking() = voice("6_voice_speaking_caption", VoiceModeUi(phase = VoicePhase.Speaking, userText = "Help me plan my evening study session", replyText = reply, feedback = 1))
    @Test fun captionsOff() = voice("7_voice_captions_off", VoiceModeUi(phase = VoicePhase.Speaking, captions = false, userText = "Help me plan my evening", replyText = reply))
    @Test fun mutedNoName() = voice("8_voice_muted_no_name", VoiceModeUi(muted = true))
    @Test fun lightIdle() = voice("9_voice_light_idle", VoiceModeUi(phase = VoicePhase.Listening, firstName = "Fareed"), level = .5f, theme = "light")
    @Test fun lightSpeaking() = voice("10_voice_light_speaking", VoiceModeUi(phase = VoicePhase.Speaking, userText = "Help me plan my evening study session", replyText = reply), theme = "light")
    @Test fun permission() = voice("11_voice_permission", VoiceModeUi(permission = MicPermission.Rationale, firstName = "Fareed"))
    @Test fun permissionBlocked() = voice("12_voice_permission_blocked", VoiceModeUi(permission = MicPermission.Blocked), theme = "light")
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun small() = voice("13_voice_small", VoiceModeUi(phase = VoicePhase.Speaking, userText = "Help me plan my evening study session", replyText = reply))
}
