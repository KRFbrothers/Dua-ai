package com.dua.ai.ui.chat

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dua.ai.attachments.DropItem
import com.dua.ai.attachments.DroppedFileStore
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChatDropTargetTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun onlyOtherAppsContentAndTextAreAccepted() {
        val clip = ClipData(ClipDescription("drop", arrayOf("*/*")), ClipData.Item(Uri.parse("content://com.android.providers.media/photo/1")))
        clip.addItem(ClipData.Item(Uri.parse("content://com.dua.ai.camera/generated_media/secret.png")))
        clip.addItem(ClipData.Item(Uri.parse("file:///data/data/com.dua.ai/files/history.json")))
        clip.addItem(ClipData.Item("Meeting at 5"))
        clip.addItem(ClipData.Item("   "))
        val items = clipItems(clip, "com.dua.ai")
        assertEquals(listOf(DropItem(uri = Uri.parse("content://com.android.providers.media/photo/1")), DropItem(text = "Meeting at 5")), items)
        assertTrue(clipItems(null, "com.dua.ai").isEmpty())
        assertFalse(droppable(Uri.parse("content://1@com.dua.ai.camera/x"), "com.dua.ai"))
        assertFalse(droppable(Uri.parse("content://com.example/" + "a".repeat(3000)), "com.dua.ai"))
    }

    @Test fun copiedFilesReachTheComposerAsDuaContentUrisWithTheirNames() {
        org.robolectric.Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf", "application/pdf")
        val store = DroppedFileStore(context.cacheDir)
        val file = store.copy("Bill March.pdf", 1_000) { ByteArrayInputStream(ByteArray(42)) }
        val drop = com.dua.ai.attachments.DropResult(listOf(file), "hi", "").toChatDrop(context)
        val uri = drop.uris.single()
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.camera", uri.authority)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals("Bill March.pdf", it.getString(0)); assertEquals(42L, it.getLong(1))
        }
        assertEquals("application/pdf", context.contentResolver.getType(uri))
        assertEquals("hi", drop.text)
        file.parentFile!!.deleteRecursively()
    }

    @Test fun overlayShowsOnlyWhileDragging() {
        var hovering by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                Box(Modifier.size(300.dp).chatDropTarget(enabled = true, maxBytes = 0, onHover = {}, onDrop = {})) {
                    ChatDropOverlay(hovering, motion = false)
                }
            }
        }
        compose.onNodeWithTag("chat-drop-overlay").assertDoesNotExist()
        hovering = true
        compose.onNodeWithTag("chat-drop-overlay").assertExists()
        compose.onNodeWithText("Drop to attach").assertExists()
        hovering = false
        compose.onNodeWithTag("chat-drop-overlay").assertDoesNotExist()
    }
}
