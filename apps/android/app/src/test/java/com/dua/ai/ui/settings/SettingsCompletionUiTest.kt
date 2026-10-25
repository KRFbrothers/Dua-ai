package com.dua.ai.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PersonalMemory
import com.dua.ai.setup.IntegrationState
import com.dua.ai.ui.navigation.DuaDestination
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
@Config(sdk = [34], qualifiers = "w320dp-h740dp-xhdpi")
class SettingsCompletionUiTest(private val theme: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun themes() = listOf(arrayOf("light"), arrayOf("dark")) }
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { content() } }

    @Test fun memoryCanBeAddedEditedDisabledAndDeletedOnlyAfterConfirmation() {
        var latest = LocalPreferences(memoryEnabled = true)
        show {
            var state by remember { mutableStateOf(latest) }
            MemoryContent(state, true, false, "", {}, {},
                onEnabled = { state = state.copy(memoryEnabled = it); latest = state },
                onSave = { index, original, text -> state = state.copy(memory = PersonalMemory.save(state.memory, index, original, text)); latest = state; true },
                onDelete = { index, original -> state = state.copy(memory = PersonalMemory.delete(state.memory, index, original)); latest = state; true })
        }
        compose.onNodeWithText("Add note").performScrollTo().performClick()
        compose.onNodeWithTag("personal-memory-input").performTextInput("Prefer Hinglish")
        compose.onNodeWithText("Save note").performClick()
        compose.onNodeWithText("Prefer Hinglish").assertExists()
        compose.onNodeWithText("Edit note 1").performScrollTo().performClick()
        compose.onNodeWithTag("personal-memory-input").performTextReplacement("Prefer concise Hinglish")
        compose.onNodeWithText("Save note").performClick()
        compose.onNode(hasText("Use Personal memory") and isToggleable()).performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(listOf("Prefer concise Hinglish"), latest.memory) }
        compose.onNodeWithText("Delete note 1").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, latest.memory.size) }
        compose.onNodeWithText("Delete note 1").performClick()
        compose.onNodeWithText("Delete note", substring = false).performClick()
        compose.runOnIdle { assertTrue(latest.memory.isEmpty()) }
    }

    @Test fun aSyncedReplacementBlocksAnOpenEditorAndKeepsTypedText() {
        val state = mutableStateOf(LocalPreferences(memory = listOf("Old note")))
        var saves = 0
        show { MemoryContent(state.value, true, true, "", {}, {}, {}, { _, _, _ -> saves++; true }, { _, _ -> true }) }
        compose.onNodeWithText("Edit note 1").performScrollTo().performClick()
        compose.onNodeWithTag("personal-memory-input").performTextReplacement("Unsent edit")
        compose.runOnIdle { state.value = state.value.copy(memory = listOf("Synced replacement")) }
        compose.onNodeWithText("Save note").assertIsNotEnabled()
        compose.onNodeWithTag("personal-memory-input").assertTextContains("Unsent edit")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun failedMemorySaveKeepsTheEditorOpenAndBusyStateDisablesSaving() {
        val enabled = mutableStateOf(true)
        show { MemoryContent(LocalPreferences(), enabled.value, false, "Could not save", {}, {}, {}, { _, _, _ -> false }, { _, _ -> false }) }
        compose.onNodeWithText("Add note").performScrollTo().performClick()
        compose.onNodeWithTag("personal-memory-input").performTextInput("Keep me")
        compose.onNodeWithText("Save note").performClick()
        compose.onNodeWithTag("personal-memory-input").assertTextContains("Keep me")
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("Save note").assertIsNotEnabled()
    }

    @Test fun notificationSwitchesHaveSeparateConsentAndExposeAndroidRecovery() {
        var replies = false; var reminders = false; var android = 0
        show {
            var r by remember { mutableStateOf(false) }; var t by remember { mutableStateOf(false) }
            NotificationsContent(r, t, DeviceNotificationState(false, false, t), true, "", {},
                { r = it; replies = it }, { t = it; reminders = it }, { android++ })
        }
        compose.onNode(hasText("Reply notifications") and isToggleable()).performClick().assertIsOn()
        compose.onNode(hasText("Task reminders") and isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Notifications are blocked by Android.", substring = true).assertExists()
        compose.onNodeWithText("Android notification settings", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(replies); assertTrue(reminders); assertEquals(1, android) }
    }

    @Test fun generalUsesTheExistingPreferenceAndOpensActualDestinations() {
        var haptic = false; var opened: DuaDestination? = null
        show { GeneralContent(LocalPreferences(), "", true, {}, { opened = it }, { haptic = it }) }
        compose.onNode(hasText("Haptic feedback") and isToggleable()).performClick()
        compose.runOnIdle { assertTrue(haptic) }
        mapOf("Appearance and font" to DuaDestination.Appearance, "Notifications" to DuaDestination.Notifications,
            "Voice and language" to DuaDestination.VoiceSettings).forEach { (label, dest) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(dest, opened) }
        }
    }

    @Test fun usageLinksToExistingMetersAndDoesNotInventTotalsOrAPlan() {
        var opened: DuaDestination? = null
        show { UsageLimitsContent(IntegrationState(), false, {}, { opened = it }) }
        mapOf("Images and video" to DuaDestination.Imagine, "Live voice" to DuaDestination.VoiceSettings,
            "Uploaded files" to DuaDestination.DataControls, "Sign in" to DuaDestination.Account).forEach { (label, dest) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(dest, opened) }
        }
        compose.onNodeWithText("The connected chat backend does not report", substring = true).assertExists()
        compose.onNodeWithText("0 tokens", substring = true).assertDoesNotExist()
    }

    @Test fun securityUsesTheRealAccountFlowAndPackageScopedPermissions() {
        val email = mutableStateOf<String?>(null)
        var opened: DuaDestination? = null; var permissions = 0
        show { SecurityLoginContent(email.value, {}, { opened = it }, { permissions++ }) }
        compose.onNodeWithText("Sign in", substring = false).performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Account, opened); email.value = "owner@example.com" }
        compose.onNodeWithText("Sign in", substring = false).assertDoesNotExist()
        compose.onNodeWithText("owner@example.com", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Manage account").performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Account, opened) }
        compose.onNodeWithText("Android app permissions", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, permissions) }
        compose.onNodeWithText("History and data", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.HistoryData, opened) }
    }

    @Test fun safetyLinksToExistingContextAndDataControlsWithoutInventingASafetyToggle() {
        var opened: DuaDestination? = null
        show { SafetyContent(false, {}, { opened = it }, {}) }
        mapOf("Memory" to DuaDestination.Memory, "Plugins" to DuaDestination.Plugins, "Data controls" to DuaDestination.DataControls).forEach { (label, dest) ->
            compose.onNodeWithText(label, substring = false).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(dest, opened) }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("Coming soon").assertDoesNotExist()
    }

    @Test fun narrowLargeTextSettingsShowFullSummariesWithoutEllipsisOrClipping() {
        val voice = "Device voice · Auto (device language)"
        val imagine = "Gemini (Nano Banana) · 16:9 · video on"
        val personalization = "Light · Inter · Senior Architect"
        show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                SettingsContent(SettingsUiState(theme = "light", personality = "Senior Architect", voice = voice, imagine = imagine), {}, {})
            }
        }
        for (label in listOf("Personalization", "Imagine", "Feature setup", "Voice", "Accent color", voice, imagine, personalization, "Providers & connectors")) {
            val node = compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertReadable(label, layouts)
        }
    }

    @Test fun hubKeepsAllActionsAndShowsLongSummariesAsReadableSubtitles() {
        val imagine = "Gemini (Nano Banana) · 16:9"
        var opened: DuaDestination? = null
        show { SettingsContent(SettingsUiState(theme = theme, accent = if (theme == "dark") "Orange" else "Purple",
            imagine = imagine, voice = "Device voice · Auto (device language)"), {}, { opened = it }) }
        compose.onNodeWithText("Personalization").performScrollTo()
        compose.onNodeWithText(imagine, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(imagine, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertReadable(imagine, layouts)
        compose.onNodeWithText("Memory").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Memory, opened) }
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/settings-hub-$theme.png")
            file.parentFile!!.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    private fun assertReadable(label: String, layouts: List<TextLayoutResult>) {
        assertTrue("$label has a layout", layouts.isNotEmpty())
        // A Text node can shrink to its intrinsic width while its paragraph retains the wider
        // input constraint. Check actual rendered line bounds, not didOverflowWidth in that case.
        layouts.forEach { result ->
            for (line in 0 until result.lineCount) {
                assertFalse("$label is ellipsized", result.isLineEllipsized(line))
                assertTrue("$label extends past its right edge", result.getLineRight(line) <= result.size.width + 1)
                assertTrue("$label extends past its bottom edge", result.getLineBottom(line) <= result.size.height + 1)
            }
        }
    }
}
