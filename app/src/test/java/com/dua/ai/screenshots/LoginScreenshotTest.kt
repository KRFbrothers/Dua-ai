package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.onboarding.OnboardingContent
import com.dua.ai.ui.settings.ProfileActions
import com.dua.ai.ui.settings.ProfileContent
import com.dua.ai.ui.settings.ProfileDialog
import com.dua.ai.ui.settings.ProfileUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Welcome screen and Profile (Google sign-in only, Log out only in Profile) in dark and light.
 * Record with: ./gradlew :app:recordRoborazziDebug -Pdua.screenshots
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LoginScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))

        private const val ERROR = "Google sign-in failed. Check that this Google account is allowed and try again."
        private val signedIn = ProfileUiState(email = "fareed@example.com", displayName = "Fareed Ahmad")
    }

    @get:Rule val compose = createComposeRule()

    private val mode get() = if (dark) "dark" else "light"
    private fun path(name: String) = "build/outputs/roborazzi/login/${name}_$mode.png"

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        setContent(content)
        compose.onRoot().captureRoboImage(path(name))
    }

    @Test fun onboarding() = shot("01_onboarding") {
        OnboardingContent(busy = false, error = null, onSignIn = {}, onSkip = {})
    }

    @Test fun onboardingSigningIn() = shot("02_onboarding_signing_in") {
        OnboardingContent(busy = true, error = null, onSignIn = {}, onSkip = {})
    }

    @Test fun onboardingError() = shot("03_onboarding_error") {
        OnboardingContent(busy = false, error = ERROR, onSignIn = {}, onSkip = {})
    }

    @Test fun profileSignedOut() = shot("04_profile_signed_out") {
        ProfileContent(ProfileUiState(), ProfileActions())
    }

    @Test fun profileSignedIn() = shot("05_profile_signed_in") {
        ProfileContent(signedIn.copy(notice = "Signed in with Google. Local data was not uploaded."), ProfileActions())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun logoutConfirm() {
        setContent { ProfileContent(signedIn, ProfileActions(), initialDialog = ProfileDialog.LogOut) }
        captureScreenRoboImage(path("06_logout_confirm"))
    }
}
