package com.dua.ai.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.account.AccountUser
import com.dua.ai.account.AccountProvider
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.account.OnboardingContent
import com.dua.ai.ui.theme.*
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
class AccountReferenceUiTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)) { content() }
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
    private fun click(label: String) = compose.onNode(hasText(label, substring = false) and hasClickAction()).performScrollTo().performClick()
    private fun targets() {
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            assertTrue("48 dp target ${it.size}", it.size.height >= 48 && it.size.width >= 48)
        }
    }
    @Test fun accountPasswordSyncBackAndConfirmedLogoutKeepTheirHandlers() {
        var password = 0; var sync = 0; var back = 0; var logout = 0
        show { ProfileContent(ProfileUiState(email = "fareed@example.com", emailAuthConfigured = true, sync = "On", deletionConfigured = true),
            ProfileActions(onEmail = { password++ }, onSync = { sync++ }, onLogOut = { logout++ }), onBack = { back++ }) }
        capture("account-reference")
        compose.onNodeWithText("On").assertExists()
        compose.onNodeWithText("Email", substring = false).assertExists()
        click("Set email password"); compose.runOnIdle { assertEquals(1, password) }
        click("Sync"); compose.runOnIdle { assertEquals(1, sync) }
        click("Log out"); compose.runOnIdle { assertEquals(0, logout) }
        compose.onNodeWithText("Cancel").performClick(); compose.runOnIdle { assertEquals(0, logout) }
        click("Log out")
        compose.onAllNodes(hasText("Log out", substring = false) and hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(1, logout) }
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, back) }
        targets()
    }
    @Test fun savedPhoneIsShownAndProRemainsUnavailable() {
        show { ProfileContent(ProfileUiState(email = "fareed@example.com", phoneNumber = "+919876543210"), ProfileActions()) }
        compose.onNodeWithText("+919876543210", substring = false).assertExists()
        click("Upgrade to Dua AI Pro"); compose.onNodeWithText("Coming soon. Pro subscriptions are not available.").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Set email password").assertIsNotEnabled()
    }
    @Test fun settingsKeepsThreeAccountRowsAndShowsRealProfileInitial() {
        var destination: DuaDestination? = null
        show { SettingsContent(SettingsUiState(email = "fareed@example.com", displayName = "Fareed Ahmad", sync = "On"), {}, { destination = it }) }
        capture("settings-reference")
        compose.onNodeWithText("Fareed Ahmad").assertExists()
        compose.onNodeWithText("F", substring = false).assertExists()
        compose.onNodeWithText("Personal", substring = false).assertExists()
        compose.onNodeWithText("Sync", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Log out", substring = false).assertDoesNotExist()
        click("Account"); compose.runOnIdle { assertEquals(DuaDestination.Account, destination) }
        targets()
    }
    @Test fun actualPhotoRendersInsteadOfTheInitial() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).asImageBitmap()
        show { AccountAvatar("fareed@example.com", "Fareed", "https://example.com/avatar", SettingsSizes.avatar, photo = bitmap) }
        compose.onNodeWithContentDescription("Account photo").assertIsDisplayed()
        compose.onNodeWithText("F", substring = false).assertDoesNotExist()
    }
    @Test fun phoneFormValidatesInputAndKeepsErrorsUntilRetrySucceeds() {
        var attempts = 0; var saved = ""; var dismissals = 0
        show { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            DuaPageTheme { PhoneNumberForm("", false, { dismissals++ }, { value ->
                attempts++; if (attempts == 1) error("Offline. Retry."); saved = value
            }) }
        } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        val field = compose.onNodeWithContentDescription("Contact phone number")
        field.performScrollTo().performTextReplacement("9876543210")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field.performTextReplacement("+91 (98765) 43210")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Offline. Retry.").assertExists()
        compose.runOnIdle { assertEquals(1, attempts); assertEquals("", saved); assertEquals(0, dismissals) }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("+919876543210", saved); assertEquals(1, dismissals) }
    }
    @Test fun welcomeUsesActualRememberedAccountAndExistingSignInHandlers() {
        var google = 0; var email = 0; var skip = 0
        show { OnboardingContent(false, null, { google++ }, { skip++ }, { email++ },
            rememberedAccount = AccountUser("id", "fareed@example.com", "Fareed"),
            rememberedProvider = AccountProvider.Google, onContinueGoogle = { google++ }) }
        capture("welcome-reference")
        click("Continue with this account"); compose.runOnIdle { assertEquals(1, google) }
        click("Continue with Google"); compose.runOnIdle { assertEquals(2, google) }
        click("Log in another way"); compose.runOnIdle { assertEquals(1, email) }
        click("Skip for now"); compose.runOnIdle { assertEquals(1, skip) }
        targets()
    }
    @Test fun emailQuickContinueUsesRememberedEmailWithoutGoogleBrandingOrGoogleCallback() {
        var address = ""; var google = 0
        show { OnboardingContent(false, null, { google++ }, {}, {},
            rememberedAccount = AccountUser("id", "owner@example.com", "Owner"), rememberedProvider = AccountProvider.Email,
            onContinueEmail = { address = it }) }
        compose.onNodeWithContentDescription("Google account").assertDoesNotExist()
        click("Continue with this account")
        compose.runOnIdle { assertEquals("owner@example.com", address); assertEquals(0, google) }
        click("Continue with Google"); compose.runOnIdle { assertEquals(1, google) }
    }
    @Test fun unknownProviderOrMissingProviderSpecificHandlerHidesQuickContinue() {
        show { OnboardingContent(false, null, {}, {}, {}, rememberedAccount = AccountUser("id", "owner@example.com", "Owner")) }
        compose.onNodeWithText("Continue with this account").assertDoesNotExist()
        compose.onNodeWithText("Continue with Google").assertExists()
    }
    @Test fun signedOutSettingsUsesAnonymousAvatarAndNeverInventsAnInitial() {
        show { SettingsContent(SettingsUiState(), {}, {}) }
        compose.onNodeWithContentDescription("Anonymous profile").assertExists()
        compose.onNodeWithText("F", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Not signed in", substring = false).assertExists()
    }
    @Test fun welcomeWithoutRememberedIdentityDoesNotInventAnAccount() {
        show { OnboardingContent(true, "Sign-in failed", {}, {}, {}) }
        compose.onNodeWithText("Continue with this account").assertDoesNotExist()
        compose.onNodeWithText("Signing in…").assertExists()
        compose.onNodeWithText("Log in another way").assertIsNotEnabled()
        compose.onNodeWithText("Skip for now").assertIsNotEnabled()
        compose.onNodeWithText("Sign-in failed").assertExists()
    }
}
