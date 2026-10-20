package com.dua.ai.ui

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.AppearancePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.history.HistoryDrawer
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.settings.*
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class ApprovedNavigationUiTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val preferences get() = LocalPreferences(theme = if (dark) "dark" else "light")
    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
                DuaTheme(preferences) {
                    assertEquals(androidx.compose.material3.MaterialTheme.colorScheme.onSurface, androidx.compose.material3.LocalContentColor.current)
                    Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)) { block() }
                }
            }
        }
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/$name-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }
    @Test fun settingsKeepsRoutesWhileAccountOwnsSyncAndLogout() {
        var destination: DuaDestination? = null
        content { SettingsContent(SettingsUiState(email = "fareed@example.com", displayName = "Fareed", theme = preferences.theme, accent = com.dua.ai.settings.AccentPreset.defaultFor(dark).label,
            imagine = "Image generation", voice = "Device voice"), {}, { destination = it }) }
        capture("profile")
        compose.onAllNodesWithText("Voice", substring = false).assertCountEquals(1)
        for (label in listOf("Voice input", "Voice mode", "Reply voice & language", "Data controls", "Your data", "Sharing", "Permissions"))
            compose.onNodeWithText(label, substring = false).assertDoesNotExist()
        compose.onNodeWithText("Voice", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.VoiceSettings, destination) }
        compose.onNodeWithText("History and data").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.HistoryData, destination) }
        compose.onNodeWithText("Account").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Account, destination) }
        compose.onNodeWithText("Accent color").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Accent, destination) }
        compose.onNodeWithText("Sync").assertDoesNotExist()
        compose.onNodeWithText("Imagine").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Imagine, destination) }
        compose.onNodeWithText("Log out").assertDoesNotExist()

    }
    @Test fun accountRetainsExistingDeleteActionWithoutASecondLogoutEntry() {
        content {
            com.dua.ai.ui.settings.ProfileContent(com.dua.ai.ui.settings.ProfileUiState(email = "fareed@example.com", displayName = "Fareed", deletionConfigured = true),
                com.dua.ai.ui.settings.ProfileActions(), showLogout = false)
        }
        compose.onNodeWithText("Account", substring = false).assertExists()
        compose.onNodeWithText("Log out", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Delete account", substring = false).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
    @Test fun appearanceAndAccentControlsRemainSeparateAndAccessible() {
        val appearance = mutableStateOf(AppearancePreferences("system"))
        content { AccentContent(appearance.value, {}, { appearance.value = appearance.value.copy(accent = it) }) }
        capture("accent")
        compose.onNodeWithText("Teal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("system", appearance.value.theme); assertEquals(com.dua.ai.settings.AccentPreset.Teal, appearance.value.accent) }
        compose.onNodeWithText("Reset to default").performScrollTo().performClick()
        compose.runOnIdle { assertNull(appearance.value.accent); assertEquals("system", appearance.value.theme) }
    }
    @Test fun appearanceScreenShowsTheThreeIndependentThemeChoices() {
        val appearance = mutableStateOf(AppearancePreferences("system", com.dua.ai.settings.AccentPreset.Blue))
        content { AppearanceContent(appearance.value, preferences, {}, { appearance.value = appearance.value.copy(theme = it) }, {}, {}) }
        capture("appearance")
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("light", appearance.value.theme); assertEquals(com.dua.ai.settings.AccentPreset.Blue, appearance.value.accent) }
        compose.onNodeWithText("System default").performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("dark", appearance.value.theme); assertEquals(com.dua.ai.settings.AccentPreset.Blue, appearance.value.accent) }
    }

    @Test fun historyAndDataOpensEveryExistingDataDestination() {
        var destination: DuaDestination? = null
        content { HistoryDataScreen({}, { destination = it }) }
        capture("history-data")
        for ((label, expected) in listOf("Data controls" to DuaDestination.DataControls, "Your data" to DuaDestination.Privacy,
            "Sharing" to DuaDestination.Sharing, "Permissions" to DuaDestination.Permissions)) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(expected, destination) }
        }
        assertNull(DuaDestination.HistoryData.comingSoonTitle)
    }

    @Test fun voiceContainsMovedActionsAndPreservesLegacyAndLiveAvailability() {
        var destination: DuaDestination? = null
        val state = mutableStateOf(com.dua.ai.ui.voice.VoiceSettingsUiState(null, false, preferences))
        content { com.dua.ai.ui.voice.VoiceSettingsContent(state.value, {}, {}, {}, {}, {},
            onOpen = { destination = it }, onGeneratedVoice = { state.value = state.value.copy(preferences = state.value.preferences.copy(generatedVoice = it)) }) }
        capture("voice-settings")
        compose.onNodeWithText("Voice input").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Voice, destination) }
        compose.onNodeWithText("Voice mode").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.HandsFree, destination) }
        compose.onNodeWithText("AI reply voice").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(signedIn = true, legacyGeneratedVoiceConfigured = true) }
        compose.onNodeWithText("AI reply voice").assertIsEnabled().performClick().assertIsOn()
        compose.runOnIdle { state.value = state.value.copy(config = com.dua.ai.voice.VoiceConfig(live = com.dua.ai.voice.LiveVoiceConfig(enabled = true))) }
        compose.onNode(hasText("Live conversation") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.LiveVoice, destination) }
        compose.onNodeWithText("Voice mode").assertDoesNotExist()
        compose.onNodeWithText("AI reply voice").assertDoesNotExist()
    }
}
