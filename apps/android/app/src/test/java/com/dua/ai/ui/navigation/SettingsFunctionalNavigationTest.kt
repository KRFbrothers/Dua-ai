package com.dua.ai.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import com.dua.ai.backup.FullBackupState
import com.dua.ai.files.CacheUsage
import com.dua.ai.files.StorageUsageStatus
import com.dua.ai.ui.settings.*
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFunctionalNavigationTest(private val theme: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun themes() = listOf(arrayOf("light"), arrayOf("dark")) }
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    @Test fun functionalRoutesReplacePlaceholdersAndBackReturnsToSettings() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = theme)) {
                nav = rememberNavController()
                val back = { nav.popBackStack(); Unit }
                val open: (DuaDestination) -> Unit = { nav.navigate(it.route) }
                NavHost(nav, startDestination = DuaDestination.Settings.route) {
                    composable(DuaDestination.Settings.route) { Text("Settings home") }
                    settingsFeaturePages(
                        memory = { MemoryContent(LocalPreferences(), true, false, "", back, open, {}, { _, _, _ -> true }, { _, _ -> true }) },
                        general = { GeneralContent(LocalPreferences(), "", true, back, open, {}) },
                        notifications = { NotificationsContent(false, false, DeviceNotificationState(true, true, false), true, "", back, {}, {}, {}) },
                        usage = { UsageLimitsContent(IntegrationState(), false, back, open) },
                        safety = { SafetyContent(false, back, open, {}) },
                        security = { SecurityLoginContent(null, back, open, {}) },
                    )
                    composable(DuaDestination.Storage.route) {
                        StorageContent(0, 0, CacheUsage(0, 0), "", false, false, null, StorageUsageStatus(), false,
                            false, FullBackupState(), back, open, {}, {}, {}, {})
                    }
                    comingSoonPages(back)
                }
            }
        }
        mapOf(DuaDestination.Memory to "Memory", DuaDestination.General to "General", DuaDestination.Notifications to "Notifications",
            DuaDestination.UsageLimits to "Usage and limits", DuaDestination.Safety to "Safety", DuaDestination.SecurityLogin to "Security and login",
            DuaDestination.Storage to "Chat backup").forEach { (destination, title) ->
            assertNull(destination.comingSoonTitle)
            compose.runOnIdle { nav.navigate(destination.route) }
            compose.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
            compose.onNodeWithText("Coming soon").assertDoesNotExist()
            compose.onNodeWithText("Settings home").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Settings home").assertExists()
            compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
        }
    }
}
