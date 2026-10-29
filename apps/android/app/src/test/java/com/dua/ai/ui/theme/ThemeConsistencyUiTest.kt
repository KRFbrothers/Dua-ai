package com.dua.ai.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.AccentPreset
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatUiState
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeConsistencyUiTest(private val systemDark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "night={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun savedAppearanceAndAccentReachEveryExistingSurfaceAndKeepDraft() {
        val appearance = mutableStateOf("system")
        val accent = mutableStateOf(AccentPreset.Blue)
        val state = mutableStateOf(ChatUiState(draft = "A retained draft"))
        var actualDark = false
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = appearance.value), accent = accent.value) {
                    val scheme = MaterialTheme.colorScheme
                    actualDark = DuaTheme.colors.isDark
                    assertEquals(scheme.primary, HomeTheme.colors.voice)
                    assertEquals(scheme.primary, ChatTheme.colors.accent)
                    assertEquals(scheme.primaryContainer, DrawerTheme.colors.newChat)
                    assertEquals(scheme.primary, SheetTheme.colors.accent)
                    assertEquals(scheme.primary, SettingsTheme.colors.accentValue)
                    assertEquals(scheme.primary, PageTheme.colors.accent)
                    assertTrue(contrast(HomeTheme.colors.onVoice, HomeTheme.colors.voice) >= 4.5)
                    Box(Modifier.requiredSize(393.dp, 798.dp)) {
                        ChatScreen(state.value, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }))
                    }
                }
            }
        }
        for (mode in listOf("light", "dark", "system")) {
            compose.runOnIdle { appearance.value = mode }
            compose.onNodeWithContentDescription("Message").assertTextContains("A retained draft")
            compose.runOnIdle { assertEquals(mode == "dark" || mode == "system" && systemDark, actualDark) }
            val file = File("build/approved-ui/consistency-home-$mode-$systemDark-$large.png")
            file.parentFile!!.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
        compose.runOnIdle { accent.value = AccentPreset.Orange; state.value = state.value.copy(typing = true) }
        compose.onNodeWithTag("composer-container").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Responding"))
        compose.onNodeWithContentDescription("Stop response").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(typing = false) }
        compose.onNodeWithContentDescription("Message").assertTextContains("A retained draft")
    }
}
