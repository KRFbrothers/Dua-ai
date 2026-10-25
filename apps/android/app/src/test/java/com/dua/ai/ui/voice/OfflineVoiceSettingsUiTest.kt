package com.dua.ai.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.voice.offline.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-mdpi")
class OfflineVoiceSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggleShowsModelsWithDownloadProgressAndDelete() {
        var mode by mutableStateOf("off")
        val events = mutableListOf<String>()
        var status by mutableStateOf(mapOf<String, ModelStatus>("en-in" to ModelStatus.Installed))
        compose.setContent {
            DuaTheme {
                Column { OfflineVoiceTypingGroup(mode, "hi", status, onMode = { mode = it }, onModel = { events += "model:$it" },
                    onDownload = { events += "download:$it" }, onCancel = { events += "cancel:$it" }, onDelete = { events += "delete:$it" }) }
            }
        }
        compose.onNodeWithText("Download Hindi model", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("Offline voice typing").assertCountEquals(2)
        compose.onAllNodes(isToggleable())[0].performClick()
        assertEquals("fallback", mode)
        compose.onNodeWithContentDescription("Download Hindi model").performClick()
        assertEquals(listOf("model:hi", "download:hi"), events)
        compose.onNodeWithContentDescription("Delete English (India) model").performClick()
        assertEquals("delete:en-in", events.last())
        status = mapOf("hi" to ModelStatus.Downloading(22_000_000, 44_458_845))
        compose.onNodeWithText("Downloading… 49% of 44 MB").assertExists()
        compose.onNodeWithContentDescription("Cancel Hindi download").performClick()
        assertEquals("cancel:hi", events.last())
        compose.onNodeWithText("Always use offline").performClick()
        assertEquals("always", mode)
    }
}
