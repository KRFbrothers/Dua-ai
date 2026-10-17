package com.dua.ai.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dua.ai.backup.drive.DriveBackupFile
import com.dua.ai.backup.drive.DriveBackupListState
import com.dua.ai.backup.drive.DriveBackupRecord
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomaticDriveBackupUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val connected = DriveBackupRecord("11111111-2222-4333-8444-555555555555", "owner@example.test", connected = true,
        enabled = true, generation = "test", phase = "success", message = "Encrypted Drive backup verified.", lastSuccess = 1792150000000)
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) { content() }
        }
    }
    @Test fun signedOutPageDoesNotClaimConnectionOrEnableAutomaticBackup() {
        var connect = 0
        show { DuaPageScaffold("Storage", {}) {
            AutomaticDriveBackupSection(DriveBackupRecord(), false, false, { connect++ }, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Connect Google Drive").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, connect) }
        compose.onNodeWithText("Daily automatic backup").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back up now").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Browse Drive backups").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Automatic backup is off.", useUnmergedTree = true).assertExists()
    }
    @Test fun connectedPageShowsLastSuccessAndExposesBackupRestoreAndOffActions() {
        var backup = 0; var browse = 0; var off = 0
        show { DuaPageScaffold("Storage", {}) {
            AutomaticDriveBackupSection(connected, true, false, {}, {}, { off++ }, { backup++ }, { browse++ })
        } }
        compose.onNodeWithText("Last successful backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back up now").performScrollTo().performClick()
        compose.onNodeWithText("Browse Drive backups").performScrollTo().performClick()
        compose.onNodeWithText("Daily automatic backup").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle { assertEquals(1, backup); assertEquals(1, browse); assertEquals(1, off) }
        if (System.getProperty("roborazzi.test.record") == "true") {
            compose.onNodeWithText("Reconnect Google Drive").performScrollTo()
            val output = File("build/approved-ui/automatic-drive-$dark-$large.png")
            output.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(output.absolutePath)
        }
    }
    @Test fun activeUploadDisablesDuplicateRunButUserCanStillTurnItOff() {
        var off = 0
        show { DuaPageScaffold("Storage", {}) {
            AutomaticDriveBackupSection(connected.copy(phase = "running", progress = 40, message = "Uploading encrypted backup…"), true, false, {}, {}, { off++ }, {}, {})
        } }
        compose.onNodeWithText("Back up now").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Daily automatic backup").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, off) }
    }
    @Test fun connectionFailureIsVisibleBesideConnectWithoutScrollingToTheEndOfStorage() {
        val failure = "Enable Google Drive API in the project containing the existing Android OAuth client, then reconnect."
        show { DuaPageScaffold("Storage", {}) {
            AutomaticDriveBackupSection(DriveBackupRecord(), true, false, {}, {}, {}, {}, {}, connectionStatus = failure)
        } }
        compose.onNodeWithText("Connect Google Drive").assertIsDisplayed()
        compose.onNodeWithText(failure).assertIsDisplayed()
        compose.onNodeWithText("Automatic backup is off.").assertDoesNotExist()
    }
    @Test fun retryAndVerifiedConnectionReplaceTheOldFailureWithCurrentStatus() {
        val record = mutableStateOf(DriveBackupRecord())
        val status = mutableStateOf("Old access denied error")
        val connecting = mutableStateOf(false)
        show { DuaPageScaffold("Storage", {}) {
            AutomaticDriveBackupSection(record.value, true, connecting.value, {}, {}, {}, {}, {},
                connectionStatus = status.value, connecting = connecting.value)
        } }
        compose.onNodeWithText("Old access denied error").assertIsDisplayed()
        compose.runOnIdle { status.value = ""; connecting.value = true }
        compose.onNodeWithText("Connecting Google Drive…").assertIsDisplayed()
        compose.onNodeWithText("Old access denied error").assertDoesNotExist()
        compose.runOnIdle { record.value = connected; connecting.value = false }
        compose.onNodeWithText("Encrypted Drive backup verified.").assertIsDisplayed()
        compose.onNodeWithText("Connecting Google Drive…").assertDoesNotExist()
        compose.onNodeWithText("Old access denied error").assertDoesNotExist()
        compose.onNodeWithText("Daily automatic backup").performScrollTo().assertIsEnabled().assertIsOn()
    }
    @Test fun enableRequiresExplicitPasswordConfirmationAndDocumentsRetentionConsent() {
        var submitted: CharArray? = null
        show { BackupPasswordDialog(true, {}, { submitted = it }, title = "Enable daily Drive backup?", submitLabel = "Enable daily backup",
            description = "Keep 3 completed backups; remove older backups after success. Store the password protected by Android Keystore on this phone.") }
        compose.onNodeWithText("Enable daily backup").assertIsNotEnabled()
        compose.onNodeWithText("Backup password").performTextInput("private backup password")
        compose.onNodeWithText("Confirm password").performTextInput("private backup password")
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithText("Enable daily backup").assertIsEnabled().performClick()
        compose.runOnIdle { assertArrayEquals("private backup password".toCharArray(), submitted) }
    }
    @Test fun driveListContainsOnlyLoadedBackupsAndSelectionRequestsPasswordInsteadOfRestoringImmediately() {
        val file = DriveBackupFile("real-id", "real.duabackup", 1024, 1792150000000, "0".repeat(32))
        var selected: DriveBackupFile? = null
        show { DriveBackupListDialog(DriveBackupListState(files = listOf(file)), {}, {}) { selected = it } }
        compose.onNodeWithText("1 KB").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(file, selected) }
        compose.onNodeWithText("Your password and restore confirmation are still required.", substring = true).assertExists()
        compose.onNodeWithText("Restore backup", substring = false).assertDoesNotExist()
    }
}
