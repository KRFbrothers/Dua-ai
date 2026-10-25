package com.dua.ai.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dua.ai.backup.FullBackupPreview
import com.dua.ai.backup.FullBackupState
import com.dua.ai.files.CacheUsage
import com.dua.ai.files.StorageUsage
import com.dua.ai.files.StorageUsageStatus
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
@Config(sdk = [34], qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageScreenTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) { content() }
        }
    }
    private fun page(signedIn: Boolean = true, enabled: Boolean = true, busy: Boolean = false, state: FullBackupState = FullBackupState(),
        onSave: () -> Unit = {}, onRestore: () -> Unit = {}, onCancel: () -> Unit = {}, onOpen: (DuaDestination) -> Unit = {}) = show {
        StorageContent(3, 2, CacheUsage(2048, 1024), "", signedIn, enabled, if (signedIn && enabled) StorageUsage(4, 4096, 52428800) else null,
            StorageUsageStatus(), busy, true, state, {}, onOpen, {}, onSave, onRestore, onCancel)
    }

    @Test fun storageUsesRealCountsAndBackupControlsWithExistingThemeAndRoutes() {
        var save = 0; var restore = 0; var opened: DuaDestination? = null
        page(onSave = { save++ }, onRestore = { restore++ }, onOpen = { opened = it })
        compose.onNodeWithText("Cached chats").assertDoesNotExist()
        compose.onNodeWithText("Storage details").performScrollTo().performClick()
        for (label in listOf("Chat backup", "Cached chats", "Saved projects", "App cache", "Downloaded media", "Storage used", "Per-file limit"))
            compose.onNodeWithText(label, substring = false, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Save full backup").performScrollTo().performClick()
        compose.onNodeWithText("Restore full backup").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, save); assertEquals(1, restore) }
        compose.onNodeWithText("Manage chats and files").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.DataControls, opened) }
        compose.onNodeWithText("Account cloud sync").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DuaDestination.CloudSync, opened) }
        compose.onNodeWithText("Coming soon").assertDoesNotExist()
        compose.onNodeWithText("Login tokens and API keys are excluded.", substring = true, useUnmergedTree = true).assertExists()
        if (System.getProperty("roborazzi.test.record") == "true") {
            compose.onNodeWithText("Save full backup").performScrollTo()
            val file = File("build/approved-ui/storage-$dark-$large.png")
            file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    @Test fun signedOutAndUnconfiguredStatesDoNotPretendDriveOrCloudStorageIsConnected() {
        page(signedIn = false)
        compose.onNodeWithText("Storage details").performScrollTo().performClick()
        compose.onNodeWithText("Sign in to view cloud files").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Storage used", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Connected", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Saving a copy is manual", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun activeOperationsDisableNewBackupsButExposeCancellation() {
        var cancel = 0
        page(busy = true, state = FullBackupState(busy = true, status = "Backing up file 1 of 4…"), onCancel = { cancel++ })
        compose.onNodeWithText("Save full backup").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Restore full backup").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel backup operation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancel) }
    }

    @Test fun passwordDialogRequiresMatchingLongPasswordsAndPassesOnlyOnExplicitSubmission() {
        var submitted: CharArray? = null
        show { BackupPasswordDialog(true, {}, { submitted = it }) }
        compose.onNodeWithText("Choose destination").assertIsNotEnabled()
        compose.onNodeWithText("Backup password").performTextInput("private backup password")
        compose.onNodeWithText("Confirm password").performTextInput("does not match")
        compose.onNodeWithText("Choose destination").assertIsNotEnabled()
        compose.onNodeWithText("Confirm password").performTextReplacement("private backup password")
        compose.onNodeWithText("Choose destination").assertIsEnabled().performClick()
        compose.runOnIdle { assertArrayEquals("private backup password".toCharArray(), submitted) }
    }

    @Test fun verifiedRestoreNeedsExplicitConfirmationAndRawFileBackendSupport() {
        var restored = 0; var cancelled = 0
        val available = mutableStateOf(false)
        show { FullBackupReviewDialog(FullBackupPreview(3, 2, 4, 5, 4096), false, available.value, { cancelled++ }, { restored++ }) }
        compose.onNodeWithText("Restore backup").assertIsNotEnabled()
        compose.onNodeWithText("Sign in and update Feature setup", substring = true).assertExists()
        compose.runOnIdle { available.value = true }
        compose.onNodeWithText("Restore backup").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, restored); assertEquals(0, cancelled) }
    }
}
