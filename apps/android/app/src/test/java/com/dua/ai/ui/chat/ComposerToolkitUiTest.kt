package com.dua.ai.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.*
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerToolkitUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bannerToolsAndMemorySwitchKeepThePromptAndExistingActions() {
        var usage = 0
        var photos = 0
        var music = 0
        var memory = false
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "dark")) {
                Composer(
                    homeLayout = true,
                    controlled = true,
                    showModelSelector = false,
                    placeholder = "Ask Dua AI...",
                    usage = ComposerUsage("Your image limit resets at 11:48 pm"),
                    onUsage = { usage++ },
                    quickTools = listOf(
                        ComposerTool("Photos", Icons.Outlined.Image, true, onClick = { photos++ }),
                        ComposerTool("Music", Icons.Outlined.MusicNote, false, "Music generation is not available", { music++ }),
                    ),
                    labs = ComposerLabs(checked = memory, enabled = true, onCheckedChange = { memory = it }),
                )
            }
        }
        compose.onNodeWithText("Your image limit resets at 11:48 pm").assertExists()
        compose.onNodeWithText("Ask Dua AI...").assertExists()
        compose.onNodeWithText("See usage").performClick()
        compose.onNodeWithTag("composer-tool-photos").performClick()
        compose.onNodeWithTag("composer-tool-music").assertIsNotEnabled()
        compose.onNodeWithTag("composer-labs").performClick()
        compose.onNodeWithContentDescription("Add photos and files").assertExists()
        assertEquals(1, usage)
        assertEquals(1, photos)
        assertEquals(0, music)
        assertEquals(true, memory)
    }

    @Test fun homeKeepsOneRealLimitLineAndReachesPhotosMemoryAndUsage() {
        val message = "Daily request limit reached. It resets at midnight UTC."
        var opened: DuaDestination? = null
        var photos = 0
        var memory = false
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "dark")) {
                ChatScreen(ChatUiState(error = message), ChatActions(
                    onOpenDestination = { opened = it },
                    onPickPhotos = { photos++ },
                ), personalMemory = false, onPersonalMemory = { memory = it })
            }
        }
        compose.onAllNodesWithText(message).assertCountEquals(1)
        compose.onNodeWithText("See usage").performClick()
        compose.onNodeWithTag("composer-tool-photos").assertIsDisplayed().performClick()
        compose.onNodeWithTag("composer-tool-music").assertIsNotEnabled()
        compose.onNodeWithTag("composer-labs").performClick()
        compose.onNodeWithText("Ask Dua AI...").assertIsDisplayed()
        assertEquals(DuaDestination.UsageLimits, opened)
        assertEquals(1, photos)
        assertEquals(true, memory)
    }
}
