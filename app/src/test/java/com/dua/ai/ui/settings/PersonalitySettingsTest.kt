package com.dua.ai.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.AccentPreset
import com.dua.ai.settings.AppearancePreferences
import com.dua.ai.settings.DuaPersonality
import com.dua.ai.settings.LocalPreferences
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
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonalitySettingsTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val appearance = mutableStateOf(AppearancePreferences("dark", AccentPreset.Teal))
    private val settings = mutableStateOf(LocalPreferences(font = "serif", memoryEnabled = false))
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) { content() }
        }
    }
    private fun showPersonalization() = show {
        AppearanceContent(appearance.value, settings.value, {},
            onTheme = { appearance.value = appearance.value.copy(theme = it) },
            onFont = { settings.value = settings.value.copy(font = it) }, onHaptic = {},
            onPersonality = { appearance.value = appearance.value.copy(personality = it) })
    }

    @Test fun fourAccessiblePresetsSelectExactlyOneAndKeepOtherPreferences() {
        showPersonalization()
        compose.onNodeWithText("Dua AI Personality").assertExists()
        for (preset in DuaPersonality.entries) {
            val row = compose.onNode(hasText(preset.label) and isSelectable())
                .performScrollTo().assertIsNotSelected().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertEquals(Role.RadioButton, row.fetchSemanticsNode().config[SemanticsProperties.Role])
            compose.onNodeWithText(preset.description, useUnmergedTree = true).assertExists()
            row.performClick().assertIsSelected()
            assertEquals(preset, appearance.value.personality)
            compose.onAllNodes(isSelected()).assertCountEquals(3) // Theme, font and personality.
            assertEquals("dark", appearance.value.theme)
            assertEquals(AccentPreset.Teal, appearance.value.accent)
            assertEquals("serif", settings.value.font)
            assertFalse(settings.value.memoryEnabled)
        }
        val selected = compose.onNode(hasText(DuaPersonality.CreativeBrainstormer.label) and isSelectable()).fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithText(DuaPersonality.CreativeBrainstormer.description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(subtitle.bottom <= selected.bottom)
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/personality-${dark}-${large}.png")
            file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    @Test fun selectedPersonalitySurvivesThemeAndFontChangesInThePage() {
        appearance.value = appearance.value.copy(personality = DuaPersonality.SeniorArchitect)
        showPersonalization()
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.onNodeWithText("Device font").performScrollTo().performClick()
        compose.onNode(hasText(DuaPersonality.SeniorArchitect.label) and isSelectable()).performScrollTo().assertIsSelected()
        assertEquals("light", appearance.value.theme)
        assertEquals("system", settings.value.font)
    }

    @Test fun settingsPersonalizationEntryKeepsItsExistingNavigationAndShowsTheSelectedStyle() {
        var destination: DuaDestination? = null
        show { SettingsContent(SettingsUiState(personality = DuaPersonality.DirectConcise.label), {}, { destination = it }) }
        compose.onNodeWithText("Personalization").performScrollTo().performClick()
        assertEquals(DuaDestination.Appearance, destination)
        compose.onNodeWithText(DuaPersonality.DirectConcise.label, substring = true, useUnmergedTree = true).assertExists()
    }
}
