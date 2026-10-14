package com.dua.ai.ui.voice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.screens.ComputerScreen
import com.dua.ai.ui.settings.SettingsContent
import com.dua.ai.ui.settings.SettingsUiState
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.PageDarkColors
import com.dua.ai.ui.theme.PageLightColors
import com.dua.ai.ui.theme.SettingsDarkColors
import com.dua.ai.ui.theme.SettingsLightColors
import com.dua.ai.ui.theme.SheetDarkColors
import com.dua.ai.ui.theme.SheetLightColors
import com.dua.ai.ui.theme.VoiceDarkColors
import com.dua.ai.ui.theme.VoiceLightColors
import com.dua.ai.ui.theme.VoiceSizes
import com.dua.ai.voice.VoicePhase
import com.dua.ai.voice.live.LivePhase
import com.dua.ai.voice.live.LiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class VoiceTokensTest {
    @get:Rule val compose = createComposeRule()
    private val purple = Color(0xFF8B5CF6)

    @Test fun voiceAccentIsTheSharedPurpleInLightAndDark() {
        assertEquals(purple, VoiceLightColors.accent); assertEquals(purple, VoiceDarkColors.accent)
        assertEquals(SheetLightColors.accent, VoiceLightColors.accent); assertEquals(SheetDarkColors.accent, VoiceDarkColors.accent)
        assertEquals(PageLightColors.text, VoiceLightColors.text); assertEquals(PageDarkColors.text, VoiceDarkColors.text)
        assertEquals(PageLightColors.subText, VoiceLightColors.subText); assertEquals(PageDarkColors.subText, VoiceDarkColors.subText)
    }

    @Test fun settingsUpgradeUsesTheRequestedLightPurpleAndReadableDarkVariant() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF7C3AED), SettingsLightColors.upgrade); assertEquals(androidx.compose.ui.graphics.Color(0xFFA78BFA), SettingsDarkColors.upgrade)
    }

    @Test fun voiceTouchSlotsAreAtLeast48dp() {
        for (size in listOf(VoiceSizes.touch, VoiceSizes.liveMute, VoiceSizes.liveEnd, VoiceSizes.micButton, VoiceSizes.controlMax))
            assertTrue(size >= 48.dp)
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClickLabel=$label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun voiceModeTalkBack(theme: String) {
        val events = mutableListOf<String>()
        compose.setContent {
            DuaTheme(LocalPreferences(theme = theme)) {
                VoiceModeContent(VoiceModeUi(phase = VoicePhase.Idle), VoiceModeCallbacks(onCamera = { events += "camera" },
                    onUpload = { events += "upload" }, onMute = { events += "mute" }, onClose = { events += "close" }), level = { 0f }, motion = false)
            }
        }
        listOf("Camera, coming soon", "Share screen or upload, coming soon", "Microphone", "Close voice mode").forEach {
            compose.onNodeWithContentDescription(it).assert(isButton).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(listOf("camera", "upload", "mute", "close"), events)
    }
    @Test fun voiceModeControlCirclesAreLabelledButtonsLight() = voiceModeTalkBack("light")
    @Test fun voiceModeControlCirclesAreLabelledButtonsDark() = voiceModeTalkBack("dark")

    private fun permission(theme: String) {
        val events = mutableListOf<String>()
        compose.setContent {
            DuaTheme(LocalPreferences(theme = theme)) {
                VoiceModeContent(VoiceModeUi(permission = MicPermission.Rationale), VoiceModeCallbacks(onAllowMic = { events += "allow" }, onClose = { events += "close" }),
                    level = { 0f }, motion = false)
            }
        }
        compose.onNodeWithText("Allow microphone").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(listOf("allow", "close"), events)
    }
    @Test fun permissionActionsRemainLight() = permission("light")
    @Test fun permissionActionsRemainDark() = permission("dark")

    private fun live(theme: String, active: Boolean) {
        val events = mutableListOf<String>()
        compose.setContent {
            DuaTheme(LocalPreferences(theme = theme)) {
                LiveVoiceContent(state = LiveState(phase = if (active) LivePhase.LISTENING else LivePhase.IDLE), starting = false, error = "x", fallback = true,
                    usage = null, voiceName = "Ara", onStart = { events += "start" }, onMute = { events += "mute" }, onEnd = { events += "end" },
                    onClose = { events += "close" }, onHandsFree = { events += "handsfree" })
            }
        }
        if (active) {
            compose.onNodeWithContentDescription("Mute microphone").assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithContentDescription("End live voice").assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithContentDescription("End and close").performClick()
            assertEquals(listOf("mute", "end", "close"), events)
        } else {
            compose.onNodeWithText("Start talking").assertIsEnabled().performClick()
            compose.onNodeWithText("Use hands-free mode instead").performClick()
            compose.onNodeWithContentDescription("Close").performClick()
            assertEquals(listOf("start", "handsfree", "close"), events)
        }
    }
    @Test fun liveVoiceIdleActionsRemainLight() = live("light", false)
    @Test fun liveVoiceIdleActionsRemainDark() = live("dark", false)
    @Test fun liveVoiceActiveActionsRemainLight() = live("light", true)
    @Test fun liveVoiceActiveActionsRemainDark() = live("dark", true)

    @Test fun settingsUpgradeRowHasClickLabel() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) { SettingsContent(SettingsUiState(email = "a@b.c"), {}, {}) } }
        compose.onNode(hasClickLabel("Open plans and usage limits")).assert(isButton).assertHeightIsAtLeast(48.dp)
    }

    @Test fun computerBackIsALabelledButton() {
        var back = 0
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) { ComputerScreen(onBack = { back++ }) } }
        compose.onNodeWithContentDescription("Back").assert(isButton).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, back)
    }
}
