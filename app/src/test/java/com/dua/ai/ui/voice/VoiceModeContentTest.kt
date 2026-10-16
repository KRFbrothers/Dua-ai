package com.dua.ai.ui.voice

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.voice.VoicePhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class VoiceModeContentTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<String>()
    private val callbacks = VoiceModeCallbacks(
        onOrb = { events += "orb" }, onMute = { events += "mute" }, onClose = { events += "close" },
        onCaptions = { events += "captions:$it" }, onCamera = { events += "camera" }, onUpload = { events += "upload" },
        onFeedback = { events += "feedback:$it" }, onCopy = { events += "copy" }, onShare = { events += "share" },
        onAllowMic = { events += "allow" }, onOpenSettings = { events += "settings" },
    )

    private fun show(ui: VoiceModeUi) = compose.setContent {
        DuaTheme(LocalPreferences(theme = "dark")) { VoiceModeContent(ui, callbacks, level = { .4f }, motion = false) }
    }

    @Test fun greetingUsesFirstNameOrFallback() {
        assertEquals("Hi Fareed, what's on your mind?", voiceGreeting(firstNameOf("Fareed Ahmad")))
        assertEquals("Hi, what's on your mind?", voiceGreeting(firstNameOf(null)))
        assertEquals("Hi, what's on your mind?", voiceGreeting(firstNameOf("  ")))
        assertEquals("Hi, what's on your mind?", voiceGreeting(firstNameOf("someone@example.com")))
    }

    @Test fun controlsCallBackAndKeepTouchTargets() {
        show(VoiceModeUi(phase = VoicePhase.Listening, firstName = "Fareed"))
        compose.onNodeWithText("Hi Fareed, what's on your mind?").assertExists()
        listOf("Camera, coming soon" to "camera", "Share screen or upload, coming soon" to "upload", "Listening. Tap to pause" to "orb",
            "Microphone" to "mute", "Close voice mode" to "close").forEach { (label, event) ->
            compose.onNodeWithContentDescription(label).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(event, events.last())
        }
        compose.onNodeWithContentDescription("Captions").performClick()
        assertEquals("captions:false", events.last())
    }

    @Test fun captionsShowReplyAndActions() {
        show(VoiceModeUi(phase = VoicePhase.Speaking, userText = "Plan my evening", replyText = "Here's a plan."))
        compose.onNodeWithText("Here's a plan.").assertExists()
        compose.onNodeWithContentDescription("Speaking. Tap to interrupt").assertExists()
        compose.onNodeWithContentDescription("Good response").performClick()
        compose.onNodeWithContentDescription("Copy reply").performClick()
        compose.onNodeWithContentDescription("Share reply").performClick()
        assertEquals(listOf("feedback:1", "copy", "share"), events)
    }

    @Test fun captionsOffHidesTheReplyText() {
        show(VoiceModeUi(phase = VoicePhase.Speaking, captions = false, userText = "Plan my evening", replyText = "Here's a plan."))
        compose.onNodeWithText("Here's a plan.").assertDoesNotExist()
        compose.onNodeWithText("Speaking…").assertExists()
    }

    @Test fun permissionRationaleAsksThenOpensSettings() {
        show(VoiceModeUi(permission = MicPermission.Rationale))
        compose.onNodeWithText("Allow microphone").performClick()
        assertEquals("allow", events.last())
    }

    @Test fun blockedPermissionOpensSettings() {
        show(VoiceModeUi(permission = MicPermission.Blocked))
        compose.onNodeWithText("Open settings").performClick()
        assertEquals("settings", events.last())
    }

    @Test fun recognizerErrorsMapToLoopActions() {
        assertEquals(com.dua.ai.voice.RecognizerError.NoSpeech, recognizerErrorOf(android.speech.SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(com.dua.ai.voice.RecognizerError.NoSpeech, recognizerErrorOf(null))
        assertEquals(com.dua.ai.voice.RecognizerError.Busy, recognizerErrorOf(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(com.dua.ai.voice.RecognizerError.Network, recognizerErrorOf(android.speech.SpeechRecognizer.ERROR_NETWORK))
        assertEquals(com.dua.ai.voice.RecognizerError.Permission, recognizerErrorOf(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(com.dua.ai.voice.RecognizerError.Unavailable, recognizerErrorOf(android.speech.SpeechRecognizer.ERROR_CLIENT))
    }

    @Test fun cameraVisionCardRendersWhenPhotoAttachedAndCanBeCleared() {
        var cleared = 0
        val cb = VoiceModeCallbacks(onClearCamera = { cleared++ })
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "dark")) {
                VoiceModeContent(
                    VoiceModeUi(phase = VoicePhase.Listening, cameraAttached = true, cameraAttachmentName = "Test photo.jpg"),
                    cb,
                    level = { .4f },
                    motion = false
                )
            }
        }
        compose.onNodeWithText("Vision Active").assertExists()
        compose.onNodeWithText("Test photo.jpg").assertExists()
        compose.onNodeWithContentDescription("Remove photo").performClick()
        assertEquals(1, cleared)
        compose.onNodeWithContentDescription("Camera, photo attached").assertExists()
    }
}
