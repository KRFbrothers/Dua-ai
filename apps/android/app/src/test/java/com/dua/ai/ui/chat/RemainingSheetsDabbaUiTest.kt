package com.dua.ai.ui.chat

import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildRepositories
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetDarkColors
import com.dua.ai.ui.theme.SheetLightColors
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Audio, Build sheet, dialogs and shared action rows keep every action in the Dabba sheet style, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemainingSheetsDabbaUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val prefs get() = LocalPreferences(theme = if (dark) "dark" else "light")
    private val expected get() = if (dark) SheetDarkColors else SheetLightColors

    @Test fun audioSheetKeepsRecordPickStopDiscard() {
        val hits = mutableListOf<String>()
        val since = androidx.compose.runtime.mutableStateOf<Long?>(null)
        var accent = Color.Unspecified
        compose.setContent {
            DuaTheme(prefs) {
                accent = SheetTheme.colors.accent
                AudioSheetContent(since.value, true, "", onRecord = { hits += "record"; since.value = 0L }, onStop = { hits += "stop" },
                    onCancel = { hits += "discard" }, onPick = { hits += "pick" }, now = { 0L })
            }
        }
        assertEquals(expected.accent, accent)
        compose.onNodeWithText("Audio").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithText("Choose file").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Record").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Stop and attach").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Discard").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(listOf("pick", "record", "stop", "discard"), hits)
    }

    @Test fun buildSheetKeepsSelectRetryTurnOffDone() {
        val hits = mutableListOf<String>()
        compose.setContent {
            DuaTheme(prefs) {
                BuildSheetContent(null, "", BuildRepositories(error = "Network down"), true, { hits += "select:$it" }, { hits += "off" },
                    { hits += "retry" }, { hits += "setup" }, { hits += "done" })
            }
        }
        compose.onNodeWithText("Build mode").assertIsDisplayed()
        compose.onNodeWithText("Retry").performScrollTo().assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(listOf("retry", "done"), hits)
    }

    @Test fun dialogAndActionRowsUseSheetTokens() {
        val hits = mutableListOf<String>()
        var container = Color.Unspecified
        compose.setContent {
            DuaTheme(prefs) {
                container = SheetTheme.colors.container
                androidx.compose.foundation.layout.Column {
                    SheetGroup {
                        SheetActionRow("Rename", androidx.compose.material.icons.Icons.Outlined.Edit, onClick = { hits += "rename" })
                        SheetActionRow("Delete chat", androidx.compose.material.icons.Icons.Outlined.Delete, onClick = { hits += "delete" }, destructive = true)
                    }
                }
            }
        }
        assertEquals(expected.container, container)
        compose.onNodeWithText("Rename").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNode(hasClickAction() and hasText("Delete chat")).assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(listOf("rename", "delete"), hits)
    }

    @Test fun deleteDialogKeepsConfirmAndCancel() {
        val hits = mutableListOf<String>()
        compose.setContent {
            DuaTheme(prefs) {
                DuaDialog(onDismissRequest = { hits += "dismiss" }, title = "Delete task?",
                    confirmButton = { SheetTextButton("Delete task", onClick = { hits += "delete" }, destructive = true) },
                    dismissButton = { SheetTextButton("Cancel", onClick = { hits += "cancel" }) })
            }
        }
        compose.onNodeWithText("Delete task?").assertIsDisplayed()
        compose.onNodeWithText("Delete task").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Cancel").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(listOf("delete", "cancel"), hits)
    }
}
