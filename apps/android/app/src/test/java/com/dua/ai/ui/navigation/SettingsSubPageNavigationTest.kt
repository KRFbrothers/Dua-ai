package com.dua.ai.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSubPageNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private val pages = listOf(DuaDestination.Privacy to "Privacy",
        DuaDestination.Sharing to "Sharing", DuaDestination.Permissions to "Permissions")

    private fun show(theme: String) = compose.setContent {
        DuaTheme(LocalPreferences(theme = theme)) {
            nav = rememberNavController()
            NavHost(nav, startDestination = DuaDestination.Settings.route) {
                composable(DuaDestination.Settings.route) { Text("Settings home") }
                comingSoonPages(onBack = { nav.popBackStack() })
            }
        }
    }

    private fun checkAll() {
        for ((destination, title) in pages) {
            compose.runOnIdle { nav.navigate(destination.route) }
            compose.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
            compose.onNodeWithText("Coming soon").assertExists()
            compose.onNodeWithText("Settings home").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
            compose.onNodeWithText("Settings home").assertExists()
            compose.runOnIdle { assertEquals(DuaDestination.Settings.route, nav.currentDestination?.route) }
            compose.runOnIdle { assertEquals(null, nav.previousBackStackEntry) }
        }
    }

    @Test fun eachSubPageOpensTitledPageAndBackReturnsToSettingsLight() { show("light"); checkAll() }
    @Test fun eachSubPageOpensTitledPageAndBackReturnsToSettingsDark() { show("dark"); checkAll() }

    @Test fun everyComingSoonDestinationHasTitle() {
        for ((destination, title) in pages) assertEquals(title, destination.comingSoonTitle)
    }
}
