package com.dua.ai.entry

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
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
@Config(sdk = [34], qualifiers = "w320dp-h568dp-mdpi")
class RememberThisUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun receiptDoesNotSaveUntilUserEditsAndConfirms() {
        var saved: String? = null
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            RememberThisScreen(ExternalEntry(action = EntryAction.Remember, text = "Long note ".repeat(30)), "owner@example.com", true, { saved = it; null }, {}, {})
        } }
        compose.onNodeWithText("Save memory").assertIsNotEnabled(); assertNull(saved)
        compose.onNodeWithContentDescription("Memory note").performTextReplacement("I prefer Hindi")
        compose.onNodeWithText("Save memory").assertIsEnabled().performClick(); assertEquals("I prefer Hindi", saved)
    }
    @Test fun signedOutTargetOffersLoginAndCancelWithoutSaving() {
        var cancelled = false; var signIn = false
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) {
            RememberThisScreen(ExternalEntry(action = EntryAction.Remember, text = "A note"), null, true, { fail("must not save"); null }, { signIn = true }, { cancelled = true })
        } }
        compose.onNodeWithText("Save memory").assertDoesNotExist()
        compose.onNodeWithText("Sign in", substring = false).performClick(); assertTrue(signIn)
        compose.onNodeWithText("Cancel").performClick(); assertTrue(cancelled)
    }
}
