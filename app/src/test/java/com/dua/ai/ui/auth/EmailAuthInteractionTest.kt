package com.dua.ai.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
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
class EmailAuthInteractionTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun content(mode: EmailAuthMode = EmailAuthMode.SignIn, configured: Boolean = true, busy: Boolean = false,
        submit: (String, String, Boolean) -> Unit = { _, _, _ -> }) : @androidx.compose.runtime.Composable () -> Unit = {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)) {
                    EmailAuthContent(initialMode = mode, initialEmail = if (mode == EmailAuthMode.SetPassword) "owner@example.com" else "",
                        configured = configured, busy = busy, onSubmit = submit)
                }
            }
        }
    }
    private fun field(description: String) = compose.onNodeWithContentDescription(description)
    private fun password(value: String) = field("Account password").performScrollTo().performTextReplacement(value)
    @Test fun emailSignInRequiresValidInputsAndClearsPasswordAfterSubmission() {
        var sent = ""; var address = ""; var signup = true
        compose.setContent(content(submit = { e, p, s -> address = e; sent = p; signup = s }))
        compose.onNodeWithText("Sign in").performScrollTo().assertIsNotEnabled()
        field("Account email").performScrollTo().performTextReplacement(" owner@example.com ")
        password(" spaces are valid ")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        assertEquals("owner@example.com", address); assertEquals(" spaces are valid ", sent); assertFalse(signup)
        assertEquals("", field("Account password").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
    }
    @Test fun rememberedEmailPrefillIsEditableAndNeverPrefillsAPassword() {
        compose.setContent { DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
            EmailAuthContent(initialEmail = "remembered@example.com")
        } }
        assertEquals("remembered@example.com", field("Account email").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        assertEquals("", field("Account password").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        field("Account email").performTextReplacement("another@example.com")
        assertEquals("another@example.com", field("Account email").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
    }
    @Test fun signupRequiresMatchingPasswordsAndSwitchingModesClearsCredentials() {
        compose.setContent(content(mode = EmailAuthMode.SignUp))
        field("Account email").performScrollTo().performTextReplacement("owner@example.com")
        password("new-password")
        field("Confirm account password").performScrollTo().performTextReplacement("different")
        compose.onNodeWithText("Create account").performScrollTo().assertIsNotEnabled()
        field("Confirm account password").performScrollTo().performTextReplacement("new-password")
        compose.onNodeWithText("Create account").performScrollTo().assertIsEnabled()
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/email-signup-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
        compose.onNodeWithText("Use email sign-in").performScrollTo().performClick()
        compose.onNodeWithText("Sign in").performScrollTo().assertIsNotEnabled()
        field("Confirm account password").assertDoesNotExist()
    }
    @Test fun passwordsAreNotRestoredThroughAndroidSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        password("temporary-secret")
        restoration.emulateSavedInstanceStateRestore()
        assertEquals("", field("Account password").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
    }
    @Test fun unsupportedFlowCannotSubmitAPasswordChange() {
        var calls = 0
        compose.setContent(content(mode = EmailAuthMode.SetPassword, configured = false, submit = { _, _, _ -> calls++ }))
        password("new-password"); field("Confirm account password").performScrollTo().performTextReplacement("new-password")
        compose.onNodeWithText("Set password").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh account setup").performScrollTo().assertIsEnabled()
        assertEquals(0, calls)
    }
    @Test fun busyFormPreventsAnotherSubmission() {
        compose.setContent(content(busy = true))
        compose.onNodeWithText("Please wait…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Create owner account").performScrollTo().assertIsNotEnabled()
    }
}
