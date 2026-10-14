package com.dua.ai.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.imagine.ImagineProvider
import com.dua.ai.settings.AppearancePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.screens.ImagineSettingsContent
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.VoiceSettingsContent
import com.dua.ai.ui.voice.VoiceSettingsUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Voice, Appearance, History and data and Imagine settings on the shared Settings card rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class InnerSettingsRowsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(theme: String, content: @Composable () -> Unit) = compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { content() } }

    private fun assertTexts(vararg texts: String) = texts.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }

    private fun assert48dp() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
        assertTrue("touch target ${it.size}", it.size.height >= 48 * 2 - 1 && it.size.width >= 48 * 2 - 1)
    }

    private fun roleOf(text: String): Role? = compose.onNode(hasText(text) and hasClickAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

    // History and data
    private fun history(theme: String, onOpen: (DuaDestination) -> Unit = {}) = show(theme) { HistoryDataScreen({}, onOpen) }

    @Test fun historyAllRowsNavigate() {
        var dest: DuaDestination? = null
        history("light") { dest = it }
        mapOf("Data controls" to DuaDestination.DataControls, "Your data" to DuaDestination.Privacy,
            "Sharing" to DuaDestination.Sharing, "Permissions" to DuaDestination.Permissions).forEach { (label, d) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(label, d, dest) }
            assertEquals(Role.Button, roleOf(label))
        }
    }
    @Test fun history48dpLight() { history("light"); assert48dp() }
    @Test fun history48dpDark() { history("dark"); assert48dp() }

    // Appearance
    private var theme = "system"; private var font = "default"; private var haptic = false
    private fun appearance(t: String) = show(t) {
        var th by remember { mutableStateOf(theme) }; var f by remember { mutableStateOf(font) }; var h by remember { mutableStateOf(haptic) }
        AppearanceContent(AppearancePreferences(theme = th), LocalPreferences(font = f, haptic = h), {},
            onTheme = { th = it; theme = it }, onFont = { f = it; font = it }, onHaptic = { h = it; haptic = it })
    }

    @Test fun appearanceAllOptionsAndHandlers() {
        appearance("light")
        assertTexts("System default", "Light", "Dark", "Font", "Söhne (default)", "Device font", "Serif", "Monospace", "Haptic feedback")
        compose.onNodeWithText("Dark").performClick(); compose.runOnIdle { assertEquals("dark", theme) }
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Serif").performScrollTo().performClick(); compose.runOnIdle { assertEquals("serif", font) }
        assertEquals(Role.RadioButton, roleOf("Serif"))
        val toggle = compose.onNode(hasText("Haptic feedback") and isToggleable())
        toggle.performScrollTo().assertIsOff().performClick(); compose.runOnIdle { assertTrue(haptic) }
        toggle.assertIsOn()
        assertEquals(Role.Switch, roleOf("Haptic feedback"))
    }
    @Test fun appearance48dpLight() { appearance("light"); assert48dp() }
    @Test fun appearance48dpDark() { appearance("dark"); assert48dp() }

    @Test fun accentPickerKeepsEveryPreset() {
        var picked: Any? = "unset"
        show("light") { AccentContent(AppearancePreferences(), {}, onAccent = { picked = it }) }
        com.dua.ai.settings.AccentPreset.entries.forEach { compose.onAllNodesWithText(it.label).onFirst().assertExists() }
        val first = com.dua.ai.settings.AccentPreset.entries.first()
        compose.onAllNodesWithText(first.label).onFirst().performClick(); compose.runOnIdle { assertEquals(first, picked) }
        compose.onNodeWithText("Reset to default").performScrollTo().performClick(); compose.runOnIdle { assertNull(picked) }
        assert48dp()
    }

    // Voice
    private var generated = false
    private fun voice(t: String, onOpen: (DuaDestination) -> Unit = {}, onPicker: (String) -> Unit = {}, onProvider: (String) -> Unit = {}) = show(t) {
        var g by remember { mutableStateOf(generated) }
        VoiceSettingsContent(VoiceSettingsUiState(config = null, signedIn = true, preferences = LocalPreferences(generatedVoice = g), legacyGeneratedVoiceConfigured = true),
            onBack = {}, onPicker = onPicker, onProvider = onProvider, onSpeed = {}, onPreview = {}, onOpen = onOpen,
            onGeneratedVoice = { g = it; generated = it })
    }

    @Test fun voiceAllOptionsAndHandlers() {
        var dest: DuaDestination? = null; var picker: String? = null; var provider: String? = null
        voice("light", onOpen = { dest = it }, onPicker = { picker = it }, onProvider = { provider = it })
        assertTexts("Talk with Dua", "Voice input", "Voice mode", "Reply voice", "Device voice", "AI reply voice", "Speed",
            "Play sample", "Language", "Reply language", "Speech recognition")
        compose.onNodeWithText("Voice input").performClick(); compose.runOnIdle { assertEquals(DuaDestination.Voice, dest) }
        compose.onNodeWithText("Voice mode").performClick(); compose.runOnIdle { assertEquals(DuaDestination.HandsFree, dest) }
        compose.onNodeWithText("Device voice").performClick(); compose.runOnIdle { assertEquals("device", provider) }
        compose.onNodeWithText("Reply language").performScrollTo().performClick(); compose.runOnIdle { assertEquals("reply-language", picker) }
        compose.onNodeWithText("Speech recognition").performScrollTo().performClick(); compose.runOnIdle { assertEquals("input-language", picker) }
        val toggle = compose.onNode(hasText("AI reply voice") and isToggleable())
        toggle.performScrollTo().assertIsOff().performClick(); compose.runOnIdle { assertTrue(generated) }
        assertEquals(Role.Switch, roleOf("AI reply voice"))
        compose.onNodeWithContentDescription("Speaking speed").assertExists()
        assertEquals(2, compose.onAllNodesWithText("Voice", useUnmergedTree = true).fetchSemanticsNodes().size) // page title + group
    }
    @Test fun voice48dpLight() { voice("light"); assert48dp() }
    @Test fun voice48dpDark() { voice("dark"); assert48dp() }

    // Imagine
    private val config = ImagineConfig(imageEnabled = true, dailyImages = 10, videoEnabled = true, dailyVideoSeconds = 60,
        providers = listOf(ImagineProvider("xai", "Grok", "grok-imagine"), ImagineProvider("gemini", "Gemini", "imagen")))
    private fun imagine(t: String, onProvider: (String) -> Unit = {}, onAspect: (String) -> Unit = {}) = show(t) {
        ImagineSettingsContent(config, null, true, "", "auto", "", {}, onProvider, onAspect)
    }

    @Test fun imagineAllOptionsAndHandlers() {
        var provider: String? = null; var aspect: String? = null
        imagine("light", { provider = it }, { aspect = it })
        assertTexts("Today", "Images", "Video seconds", "Default image model", "Grok · grok-imagine", "Gemini · imagen", "Default aspect ratio", "Auto (model decides)", "Video")
        compose.onNodeWithText("Gemini · imagen").performScrollTo().performClick(); compose.runOnIdle { assertEquals("gemini", provider) }
        assertEquals(Role.RadioButton, roleOf("Gemini · imagen"))
        val last = config.imageAspects.last()
        compose.onNodeWithText(com.dua.ai.imagine.ImagineCodec.aspectLabel(last)).performScrollTo().performClick(); compose.runOnIdle { assertEquals(last, aspect) }
    }
    @Test fun imagine48dpLight() { imagine("light"); assert48dp() }
    @Test fun imagine48dpDark() { imagine("dark"); assert48dp() }
}
