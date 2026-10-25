package com.dua.ai.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.backup.FullBackupState
import com.dua.ai.backup.drive.DriveBackupRecord
import com.dua.ai.files.CacheUsage
import com.dua.ai.files.StorageUsageStatus
import com.dua.ai.settings.LocalPreferences
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupReferenceLayoutUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val record = DriveBackupRecord("11111111-2222-4333-8444-555555555555", "owner@example.test", connected = true,
        enabled = true, generation = "test", phase = "success", message = "Encrypted Drive backup verified.",
        lastSuccess = 1792194060000, lastSize = 113664)

    @Test fun realSummaryAndFlatAccountControlsUseExistingActionsWithoutSampleReferenceData() {
        var backup = 0; var connect = 0; var manage = 0; var back = 0; var save = 0; var restore = 0
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                StorageContent(3, 2, CacheUsage(1024, 0), "", true, true, null, StorageUsageStatus(), false, true,
                    FullBackupState(), { back++ }, {}, {}, { save++ }, { restore++ }, {}, automaticBackup = {
                        AutomaticDriveBackupSection(record, true, false, { connect++ }, {}, {}, { backup++ }, {}, onManageStorage = { manage++ })
                    })
            }
        }
        compose.onNodeWithText("Chat backup").assertIsDisplayed()
        compose.onNodeWithText("Size: 111 KB").assertIsDisplayed()
        compose.onNodeWithText("Back up", substring = false).performClick()
        compose.onNodeWithText("Google Account").performScrollTo().performClick()
        compose.onNodeWithText("Manage Google storage").performScrollTo().performClick()
        compose.onNodeWithText("Automatic backups").performScrollTo().assertIsOn()
        compose.onNodeWithText("Daily", substring = false).assertExists()
        compose.onNodeWithText("9.1 GB", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Include videos in chats").assertDoesNotExist()
        compose.onNodeWithText("Cached chats").assertDoesNotExist()
        compose.onNodeWithText("Backup settings").performScrollTo()
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/backup-reference-$dark.png")
            file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
        compose.onNodeWithText("Save full backup").performScrollTo().performClick()
        compose.onNodeWithText("Restore full backup").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            assertEquals(1, backup); assertEquals(1, connect); assertEquals(1, manage)
            assertEquals(1, save); assertEquals(1, restore); assertEquals(1, back)
        }
    }

    @Test fun olderBackupSizeIsUnknownUntilVerifiedAndAnIncompleteUploadKeepsTheLastVerifiedSize() {
        val current = mutableStateOf(record.copy(lastSize = null))
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                BackupPageScaffold({}, true) {
                    AutomaticDriveBackupSection(current.value, true, false, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Size: Not recorded").assertIsDisplayed()
        compose.runOnIdle { current.value = record.copy(phase = "running", progress = 12, message = "Uploading encrypted backup…") }
        compose.onNodeWithText("Size: 111 KB").assertIsDisplayed()
        compose.onNodeWithText("Back up", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Uploading encrypted backup…").assertIsDisplayed()
        compose.runOnIdle { current.value = current.value.copy(phase = "error", message = "Upload interrupted") }
        compose.onNodeWithText("Size: 111 KB").assertIsDisplayed()
        compose.onNodeWithText("Retry backup").assertIsEnabled()
    }
}
