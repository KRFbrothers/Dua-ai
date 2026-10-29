package com.dua.ai.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeProfileUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(state: androidx.compose.runtime.State<ChatUiState>, actions: ChatActions = ChatActions()) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) { ChatScreen(state.value, actions) }
            }
        }
    }
    private fun open() {
        compose.onNodeWithContentDescription("Open profile and settings").performClick()
        compose.onNodeWithTag("home-profile-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close profile").assertIsDisplayed()
            .assertWidthIsAtLeast(com.dua.ai.ui.theme.Sizes.touchTarget)
            .assertHeightIsAtLeast(com.dua.ai.ui.theme.Sizes.touchTarget)
        compose.runOnIdle {
            val window = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(!dark, bars.isAppearanceLightStatusBars)
            assertEquals(!dark, bars.isAppearanceLightNavigationBars)
        }
    }
    private fun capture(label: String) {
        val file = File("build/approved-ui/home-profile-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onNodeWithTag("home-profile-dialog").captureRoboImage(file.absolutePath)
    }

    @Test fun profileUsesLiveIdentityAndClosingKeepsTheDraftWithoutReopeningKeyboard() {
        val state = mutableStateOf(ChatUiState(profileName = "Amina Khan", profileEmail = "owner@example.test"))
        var settings = 0
        show(state, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }, onOpenSettings = { settings++ }))
        compose.onNodeWithContentDescription("Message").performClick().performTextInput("Keep my text")
        open()
        compose.onNode(hasText("Amina Khan") and hasAnyAncestor(hasTestTag("home-profile-dialog"))).assertIsDisplayed()
        compose.onNodeWithText("owner@example.test").assertIsDisplayed()
        capture("owner")
        compose.runOnIdle { state.value = state.value.copy(profileName = "Updated account") }
        compose.onNode(hasText("Updated account") and hasAnyAncestor(hasTestTag("home-profile-dialog"))).assertExists()
        compose.onNodeWithText("Amina Khan").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close profile").performClick()
        compose.onNodeWithTag("home-profile-dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep my text").assertIsNotFocused()
        assertEquals(0, settings)
        open()
        compose.runOnUiThread {
            (org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog)
                .onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("home-profile-dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep my text").assertIsNotFocused()
        open()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithTag("home-profile-dialog").assertDoesNotExist()
        assertEquals(1, settings)
        assertEquals("Keep my text", state.value.draft)
    }

    @Test fun menuDestinationsUseExistingNavigationAndDismissTheOverlay() {
        val opened = mutableListOf<DuaDestination>()
        val state = mutableStateOf(ChatUiState(profileName = "Owner", profileEmail = "owner@example.test"))
        show(state, ChatActions(onOpenDestination = { opened += it }))
        for ((label, destination) in listOf("Manage account" to DuaDestination.Account,
            "Usage limits" to DuaDestination.UsageLimits, "Personal memory" to DuaDestination.Memory,
            "Notifications" to DuaDestination.Notifications)) {
            open()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithTag("home-profile-dialog").assertDoesNotExist()
            assertEquals(destination, opened.last())
        }
        assertEquals(4, opened.size)
    }

    @Test fun guestProfileOffersSignInWithoutInventingAnIdentityOrSubscription() {
        var destination: DuaDestination? = null
        show(mutableStateOf(ChatUiState()), ChatActions(onOpenDestination = { destination = it }))
        open()
        compose.onNodeWithText("Guest").assertIsDisplayed()
        val card = compose.onNodeWithTag("home-profile-dialog").getUnclippedBoundsInRoot()
        assertTrue("Profile card stays bounded", card.right - card.left <= com.dua.ai.ui.theme.SheetSizes.dialogMaxWidth)
        if (!large) assertTrue("Short profiles wrap their content instead of filling the screen", card.bottom - card.top < 700.dp)
        compose.onNodeWithText("PRO").assertDoesNotExist()
        compose.onNodeWithText("Fareed Ahmad").assertDoesNotExist()
        capture("guest")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        assertEquals(DuaDestination.Account, destination)
    }
}
