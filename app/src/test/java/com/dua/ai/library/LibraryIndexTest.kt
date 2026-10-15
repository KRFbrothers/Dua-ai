package com.dua.ai.library

import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.SavedChat
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.ChatProject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class LibraryIndexTest {
    private val image = FileRef("owner/image.png", "Photo.png", "image/png", 123)
    private val pdf = FileRef("owner/report.pdf", "Report.pdf", "application/pdf", 99)
    private val reply = ChatMessage(id = "reply", text = "```kotlin\nval answer = 42\n```", isUser = false,
        media = listOf(GeneratedMedia(image.path, image.mimeType)), imagine = ImagineInfo("image", "Real prompt"))
    private val chat = SavedChat("chat", "Dua", "", listOf(reply, ChatMessage(text = "My files", isUser = true, files = listOf(image, pdf))), 1)
    @Test fun noDemoItemsAndRealSourcesAreDeduplicated() {
        assertTrue(LibraryIndex.build(emptyList(), "new", emptyList(), emptyList()).isEmpty())
        val result = LibraryIndex.build(listOf(chat), "chat", chat.messages, listOf(ChatProject("project", "P", sources = listOf(pdf))))
        assertEquals(3, result.size)
        assertEquals(1, result.count { it.kind == LibraryKind.Images })
        assertTrue(result.any { it.matches("answer") && it.kind == LibraryKind.Code && it.chatId == "chat" })
        assertTrue(result.any { it.matches("REPORT") })
        assertEquals("Real prompt", result.first { it.kind == LibraryKind.Images }.content)
        assertTrue(result.first { it.kind == LibraryKind.Images }.matches("Photo.png"))
    }
    @Test fun activeConversationReplacesStaleSnapshotIncludingClearedImages() {
        assertTrue(LibraryIndex.build(listOf(chat), "chat", emptyList(), emptyList()).isEmpty())
        assertTrue(LibraryIndex.build(emptyList(), "unsaved", listOf(reply), emptyList()).any { it.chatId == "unsaved" })
    }
    @Test fun inlineImagesTextAndPendingUploadsReflectActualDraft() {
        val result = LibraryIndex.build(emptyList(), "draft", emptyList(), emptyList(),
            listOf(PendingUpload("upload", "Report.pdf", "application/pdf", 99, state = PendingUpload.DONE, ref = pdf)),
            listOf(ChatAttachment("inline.png", "image/png", Base64.getEncoder().encodeToString(byteArrayOf(1, 2))),
                ChatAttachment("source.kt", "text/plain", Base64.getEncoder().encodeToString("class Original".toByteArray()))))
        assertEquals(3, result.size)
        assertTrue(result.any { it.kind == LibraryKind.Images && it.media != null && it.inline != null })
        assertTrue(result.any { it.matches("Original") })
        assertTrue(result.all { it.status == "Attached to current draft" })
    }
    @Test fun fencesRetainOriginalCodeAndSupportTildesAndLongBackticks() {
        assertEquals(listOf("python" to "print(1)", "md" to "```\ncode\n```"), LibraryIndex.codeBlocks("~~~python\nprint(1)\n~~~\n````md\n```\ncode\n```\n````"))
    }
}
