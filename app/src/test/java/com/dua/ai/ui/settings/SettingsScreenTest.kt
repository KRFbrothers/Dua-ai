package com.dua.ai.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = SettingsUiState(email = "fareed@example.com", displayName = "Fareed", imagine = "Image generation", voice = "Device voice")

    private fun show(theme: String = "light", s: SettingsUiState = state, onOpen: (DuaDestination) -> Unit = {}) =
        compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { SettingsContent(s, {}, onOpen) } }

    @Test fun referenceThreeCardStructureWithDuaBranding() {
        show()
        for (t in listOf("Fareed", "F", "Account", "fareed@example.com", "Workspace", "Upgrade to Dua AI Pro",
            "Personalization", "Memory", "Plugins", "Accent color", "General", "Usage and limits", "Notifications", "Voice",
            "Safety", "Security and login", "Storage", "History and data"))
            compose.onNodeWithText(t, substring = false, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("OpenAI", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("ChatGPT", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithContentDescription("About Dua AI").assertExists()
        compose.onNodeWithContentDescription("Edit profile photo").assertExists()
    }

    @Test fun everyExistingDestinationIsReachable() {
        var dest: DuaDestination? = null
        show(onOpen = { dest = it })
        val expected = mapOf("Account" to DuaDestination.Account, "Workspace" to DuaDestination.Work, "Sync" to DuaDestination.CloudSync,
            "Upgrade to Dua AI Pro" to DuaDestination.UsageLimits, "Personalization" to DuaDestination.Appearance, "Memory" to DuaDestination.Memory,
            "Plugins" to DuaDestination.Plugins, "Imagine" to DuaDestination.Imagine, "Accent color" to DuaDestination.Accent,
            "General" to DuaDestination.General, "Feature setup" to DuaDestination.FeatureSetup, "Usage and limits" to DuaDestination.UsageLimits,
            "Notifications" to DuaDestination.Notifications, "Voice" to DuaDestination.VoiceSettings, "Safety" to DuaDestination.Safety,
            "Security and login" to DuaDestination.SecurityLogin, "Storage" to DuaDestination.Storage,
            "Search settings" to DuaDestination.SettingsSearch, "History and data" to DuaDestination.HistoryData)
        for ((label, d) in expected) {
            compose.onNodeWithText(label, substring = false).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(label, d, dest) }
        }
        compose.onNodeWithContentDescription("Edit profile photo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.AvatarEdit, dest) }
        compose.onNodeWithContentDescription("About Dua AI").performScrollTo().performClick()
        compose.onNodeWithText("About Dua AI").assertIsDisplayed()
    }

    @Test fun touchTargetsAre48dpInLight() = touchTargets("light")
    @Test fun touchTargetsAre48dpInDark() = touchTargets("dark")

    private fun touchTargets(theme: String) {
        show(theme)
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            assertTrue("touch target ${it.size}", it.size.height >= 48 * 2 - 1 && it.size.width >= 48 * 2 - 1)
        }
    }

    @Test fun signedOutHidesLogoutAndShowsNotSignedIn() {
        show(s = SettingsUiState())
        compose.onNodeWithText("Not signed in").assertExists()
        compose.onNodeWithText("Logout").assertDoesNotExist()
        compose.onNodeWithText("Imagine").assertDoesNotExist()
    }

    @Test fun workspaceAndSyncShowNoFakeState() {
        show()
        compose.onNodeWithText("Personal").assertDoesNotExist()
        compose.onNodeWithText("On account").assertDoesNotExist()
    }

    @Test fun syncShowsRealState() {
        show(s = state.copy(sync = "On"))
        assertTrue(compose.onAllNodesWithText("On", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
