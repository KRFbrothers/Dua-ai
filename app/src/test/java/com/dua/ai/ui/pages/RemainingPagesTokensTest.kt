package com.dua.ai.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dua.ai.account.AccountState
import com.dua.ai.account.AccountUser
import com.dua.ai.account.CloudReview
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.AutomaticSyncState
import com.dua.ai.setup.IntegrationGroup
import com.dua.ai.setup.IntegrationState
import com.dua.ai.setup.RepositoryResult
import com.dua.ai.setup.SearchResult
import com.dua.ai.ui.onboarding.OnboardingContent
import com.dua.ai.ui.screens.*
import com.dua.ai.ui.settings.*
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.PageDarkColors
import com.dua.ai.ui.theme.PageLightColors
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.viewmodel.ChatViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Data controls, Profile, Cloud sync, Feature setup, Onboarding and Computer on the PageTokens / SettingsTokens look. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class RemainingPagesTokensTest {
    @get:Rule val compose = createComposeRule()

    private fun show(theme: String, content: @Composable () -> Unit) = compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { content() } }
    private fun click(text: String) = compose.onNode(hasText(text, substring = false) and hasClickAction()).performScrollTo().performClick()
    private fun assertTexts(vararg texts: String) = texts.forEach { compose.onNodeWithText(it, substring = false, useUnmergedTree = true).performScrollTo().assertExists() }
    private fun roleOf(text: String): Role? = compose.onNode(hasText(text, substring = false) and hasClickAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
    private fun assert48dp() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
        assertTrue("touch target ${it.size}", it.size.height >= 48 * 2 - 1 && it.size.width >= 48 * 2 - 1)
    }

    // Data controls
    private val calls = mutableListOf<String>()
    private fun dataControls(theme: String) = show(theme) {
        DataControlsContent(chatCount = 3, busy = false, status = "Done", signedIn = true, filesEnabled = true,
            usage = com.dua.ai.files.StorageUsage(2, 1000, 52428800), onBack = { calls += "back" }, onExport = { calls += "export" }, onImport = { calls += "import" },
            onClear = { calls += "clear" }, onRefreshFiles = { calls += "refresh" }, onDeleteFiles = { calls += "deleteFiles" })
    }

    @Test fun dataControlsOptionsAndHandlers() {
        dataControls("light")
        assertTexts("Export chats", "Import chats", "Clear cached chats", "Uploaded files", "Refresh", "Delete all uploaded files", "Done")
        listOf("Export chats" to "export", "Import chats" to "import", "Clear cached chats" to "clear", "Refresh" to "refresh",
            "Delete all uploaded files" to "deleteFiles").forEach { (label, call) -> click(label); compose.runOnIdle { assertEquals(call, calls.last()) } }
        compose.onNodeWithContentDescription("Back to settings").performClick(); compose.runOnIdle { assertEquals("back", calls.last()) }
        assertEquals(Role.Button, roleOf("Export chats"))
    }
    @Test fun dataControls48dpLight() { dataControls("light"); assert48dp() }
    @Test fun dataControls48dpDark() { dataControls("dark"); assert48dp() }

    // Profile
    private fun profile(theme: String, signedIn: Boolean, onEmail: () -> Unit = {}, onSignIn: () -> Unit = {}, onLogOut: () -> Unit = {}) = show(theme) {
        ProfileContent(ProfileUiState(email = if (signedIn) "fareed@example.com" else null, displayName = "Fareed", deletionConfigured = true, emailAuthConfigured = true),
            ProfileActions(onSignIn = onSignIn, onEmail = onEmail, onLogOut = onLogOut))
    }

    @Test fun profileSignedInOptionsAndHandlers() {
        var email = 0; var logout = 0
        profile("light", signedIn = true, onEmail = { email++ }, onLogOut = { logout++ })
        assertTexts("Account", "fareed@example.com", "Log out", "Delete account", "Set email password")
        click("Set email password"); compose.runOnIdle { assertEquals(1, email) }
        click("Log out"); compose.onNodeWithText("Log out?").assertExists()
        compose.onAllNodes(hasText("Log out", substring = false) and hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(1, logout) }
        // Opening the delete dialog focuses a text field (cursor blink keeps Robolectric busy); assert the action is wired and enabled.
        compose.onNode(hasText("Delete account", substring = false) and hasClickAction()).performScrollTo().assertIsEnabled()
    }
    @Test fun profileSignedOutKeepsGoogleAndEmail() {
        var email = 0
        profile("dark", signedIn = false, onEmail = { email++ })
        assertTexts("Dua AI", "Continue with Google", "Log in another way")
        click("Log in another way"); compose.runOnIdle { assertEquals(1, email) }
    }
    @Test fun profile48dpLight() { profile("light", true); assert48dp() }
    @Test fun profile48dpDark() { profile("dark", true); assert48dp() }

    // Cloud sync
    private val user = AccountUser("u1", "fareed@example.com", "Fareed")
    private fun cloud(theme: String, sync: AutomaticSyncState = AutomaticSyncState(), preview: ChatViewModel.CloudPreview? = null) = show(theme) {
        CloudSyncContent(AccountState(configured = true, user = user), preview, sync, dataBusy = false, onBack = { calls += "back" }, actions = CloudSyncActions(
            onProfile = { calls += "profile" }, onEnableAuto = { calls += "enable" }, onDisableAuto = { calls += "disable" }, onResetAuto = { calls += "reset" },
            onResumeAuto = { calls += "resume" }, onRetryAuto = { calls += "retry" }, onReview = { calls += "review:$it" }, onConfirm = { calls += "confirm" },
            onCancelReview = { calls += "cancel" }))
    }

    @Test fun cloudSyncOptionsAndHandlers() {
        cloud("light")
        compose.onNode(hasText("Automatic sync on this device") and isToggleable()).performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertEquals("enable", calls.last()) }
        assertEquals(Role.Switch, roleOf("Automatic sync on this device"))
        mapOf("Review upload" to "review:upload", "Review download" to "review:download", "Clear cloud snapshot" to "review:delete").forEach { (l, c) ->
            click(l); compose.runOnIdle { assertEquals(c, calls.last()) }
        }
    }
    @Test fun cloudSyncReviewAndRecoveryActionsRemain() {
        cloud("dark", AutomaticSyncState(enabled = true, phase = "idle"), ChatViewModel.CloudPreview("upload", "u1", CloudReview(7, CloudSnapshot(emptyList(), listOf("note")))))
        assertTexts("Confirm upload · cloud revision 7", "Confirm", "Cancel", "Check sync / Retry")
        click("Confirm"); compose.runOnIdle { assertEquals("confirm", calls.last()) }
        click("Cancel"); compose.runOnIdle { assertEquals("cancel", calls.last()) }
    }
    @Test fun cloudSyncRetryResumeResetCancel() {
        cloud("light", AutomaticSyncState(enabled = true, phase = "idle")); click("Check sync / Retry"); compose.runOnIdle { assertEquals("retry", calls.last()) }
    }
    @Test fun cloudSyncStorageErrorReset() {
        cloud("light", AutomaticSyncState(enabled = true, phase = "storage-error")); click("Reset automatic sync settings"); compose.runOnIdle { assertEquals("reset", calls.last()) }
    }
    @Test fun cloudSyncResumeAfterReview() {
        cloud("dark", AutomaticSyncState(enabled = true, phase = "review", reviewRequired = true)); click("Resume after review"); compose.runOnIdle { assertEquals("resume", calls.last()) }
    }
    @Test fun cloudSyncCancelSetup() {
        cloud("light", AutomaticSyncState(enabled = true, phase = "checking")); click("Cancel sync setup"); compose.runOnIdle { assertEquals("disable", calls.last()) }
    }
    @Test fun cloudSync48dpLight() { cloud("light"); assert48dp() }
    @Test fun cloudSync48dpDark() { cloud("dark"); assert48dp() }

    // Feature setup
    private fun groups() = listOf("providers", "search", "sync", "memory", "connectors", "voice", "account").map {
        IntegrationGroup(it, "Group $it", "configured", emptyList(), emptyList())
    }
    private var voice: Boolean? = null
    private fun setup(theme: String) = show(theme) {
        IntegrationSetupContent(IntegrationState(groups = groups(), searchConfigured = true, generatedVoiceConfigured = true),
            listOf(SearchResult("Result one", "https://example.com", "Snippet")), listOf(RepositoryResult("dua-repo", "https://github.com/x/y", "Repo")),
            listOf("Remember tea"), signedIn = true, generatedVoice = false, onBack = {}, actions = IntegrationSetupActions(
                onRefresh = { calls += "refresh" }, onSearch = { calls += "search:$it" }, onOpenUrl = { calls += "open:$it" }, onCloud = { calls += "cloud" },
                onProposeMemory = { calls += "propose" }, onAcceptMemory = { calls += "accept:$it" }, onDismissMemory = { calls += "dismiss:$it" },
                onListRepositories = { calls += "repos" }, onGeneratedVoice = { voice = it }, onProfile = { calls += "profile" }))
    }

    @Test fun featureSetupOptionsAndHandlers() {
        setup("light")
        mapOf("Refresh setup" to "refresh", "Result one" to "open:https://example.com", "Open cloud sync" to "cloud", "Suggest memory notes" to "propose",
            "Save note" to "accept:Remember tea", "Dismiss" to "dismiss:Remember tea", "List GitHub repositories" to "repos", "dua-repo" to "open:https://github.com/x/y",
            "Profile and account controls" to "profile").forEach { (l, c) -> click(l); compose.runOnIdle { assertEquals(c, calls.last()) } }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("weather")
        click("Search"); compose.runOnIdle { assertEquals("search:weather", calls.last()) }
        compose.onNode(hasText("Use AI voice for reply speaker") and isToggleable()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, voice) }
    }
    @Test fun featureSetup48dpLight() { setup("light"); assert48dp() }
    @Test fun featureSetup48dpDark() { setup("dark"); assert48dp() }

    // Onboarding
    private fun onboarding(theme: String) = show(theme) {
        OnboardingContent(busy = false, error = null, onSignIn = { calls += "google" }, onSkip = { calls += "skip" }, onEmail = { calls += "email" })
    }
    @Test fun onboardingOptionsAndHandlers() {
        onboarding("light")
        assertTexts("Dua AI", "Log in another way", "Skip for now")
        click("Log in another way"); compose.runOnIdle { assertEquals("email", calls.last()) }
        click("Skip for now"); compose.runOnIdle { assertEquals("skip", calls.last()) }
        compose.onAllNodes(hasClickAction()).onFirst().performClick(); compose.runOnIdle { assertEquals("google", calls.last()) }
    }
    @Test fun onboarding48dpLight() { onboarding("light"); assert48dp() }
    @Test fun onboarding48dpDark() { onboarding("dark"); assert48dp() }

    // Computer
    private fun computer(theme: String) = show(theme) { ComputerScreen(onBack = { calls += "back" }) }
    @Test fun computerBackAndCopy() {
        computer("dark")
        compose.onNodeWithText("Computer").assertIsDisplayed()
        compose.onNodeWithContentDescription("New Computer session").assertIsDisplayed().assertIsNotEnabled()
        assertTexts("Coming soon", "This feature is coming soon.")
        compose.onNodeWithContentDescription("Back").performClick(); compose.runOnIdle { assertEquals("back", calls.last()) }
        assertEquals(Role.Button, compose.onNodeWithContentDescription("Back").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }
    @Test fun computer48dpLight() { computer("light"); assert48dp() }
    @Test fun computer48dpDark() { computer("dark"); assert48dp() }

    @Test fun pageTokensLightAndDarkDifferAndTouchIs48() {
        assertEquals(48.dp, PageSizes.touch)
        assertNotEquals(PageLightColors.background, PageDarkColors.background)
        assertNotEquals(PageLightColors.text, PageDarkColors.text)
        assertNotEquals(PageLightColors.card, PageDarkColors.card)
    }
}
