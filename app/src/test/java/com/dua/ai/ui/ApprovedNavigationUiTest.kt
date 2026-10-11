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
    @Test fun sidebarAndAuraKeepNavigationFixedWhileOnlyRecentChatsScroll() {
        val chats = (1..40).map { SavedChat("chat-$it", "Dua", "", listOf(ChatMessage(text = "Conversation $it", isUser = true)), it.toLong()) }
        var destination: DuaDestination? = null
        var projects = 0; var profile = 0; var newChat = 0
        content { HistoryDrawer(chats, true, { newChat++ }, {}, {}, { profile++ },
            onProjects = { projects++ }, onDestination = { destination = it }, profileName = "Fareed") }
        val footer = compose.onNodeWithTag("sidebar-footer").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("sidebar-menu").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("recent-chats").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.bottom <= list.top)
        assertTrue(list.bottom <= footer.top)
        for (label in listOf("Images", "Library", "Projects", "Computer", "AI Agent", "Work", "Aura"))
            compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Computer").performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Computer, destination) }
        compose.onNodeWithText("Projects").performClick()
        compose.runOnIdle { assertEquals(1, projects) }
        compose.onNodeWithContentDescription("Profile & Settings").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("New chat").performClick()
        compose.runOnIdle { assertEquals(1, profile); assertEquals(1, newChat) }
        capture("sidebar")
        compose.onNodeWithTag("recent-chats").performScrollToIndex(20)
        assertEquals(footer, compose.onNodeWithTag("sidebar-footer").fetchSemanticsNode().boundsInRoot)
        assertEquals(menu, compose.onNodeWithTag("sidebar-menu").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Aura").performClick()
        compose.onNodeWithText("Plugins").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to sidebar").assertHeightIsAtLeast(48.dp)
        val auraFooter = compose.onNodeWithTag("sidebar-footer").fetchSemanticsNode().boundsInRoot
        capture("aura")
        compose.onNodeWithTag("recent-chats").performScrollToIndex(30)
        assertEquals(auraFooter, compose.onNodeWithTag("sidebar-footer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Back to sidebar").performClick()
        compose.onNodeWithText("Images").assertIsDisplayed()
        compose.onNodeWithText("Library").assertIsDisplayed()
    }
    @Test fun profileKeepsExistingRoutesAndLogoutRequiresConfirmation() {
        var destination: DuaDestination? = null; var logouts = 0
        content { SettingsContent(SettingsUiState(email = "fareed@example.com", displayName = "Fareed", theme = preferences.theme, accent = com.dua.ai.settings.AccentPreset.defaultFor(dark).label,
            imagine = "Image generation", voice = "Device voice"), {}, { destination = it }, onGeneratedVoice = {}, onLogOut = { logouts++ }) }
        capture("profile")
        compose.onNodeWithText("Account").performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Account, destination) }
        compose.onNodeWithText("Accent color").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Accent, destination) }
        compose.onNodeWithText("Sync").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.CloudSync, destination) }
        compose.onNodeWithText("Imagine").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.Imagine, destination) }
        compose.onNodeWithText("Logout").performScrollTo().performClick()
        compose.onNodeWithText("Log out?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithText("Logout").performClick()
        compose.onNodeWithText("Log out", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
    }
    @Test fun accountRetainsExistingDeleteActionWithoutASecondLogoutEntry() {
        content {
            com.dua.ai.ui.screens.ProfileContent(com.dua.ai.ui.screens.ProfileUiState(email = "fareed@example.com", displayName = "Fareed", deletionConfigured = true),
                com.dua.ai.ui.screens.ProfileActions(), showLogout = false)
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
}
