package com.dua.ai.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.*
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DynamicComposerUiTest(private val dark: Boolean, private val largeFont: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},largeFont={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeFont) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                assertEquals(if (dark) HomeDarkColors else HomeLightColors, HomeTheme.colors)
                Box(Modifier.requiredSize(if (largeFont) 320.dp else 360.dp, 700.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) { content() }
            }
        }
    }
    private fun capture(state: String) {
        val file = File("build/approved-ui/composer-$state-${if (dark) "dark" else "light"}-${if (largeFont) "font2" else "regular"}.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
    private fun control(label: String) = compose.onNodeWithContentDescription(label)
        .assertIsDisplayed().assertWidthIsAtLeast(Sizes.touchTarget).assertHeightIsAtLeast(Sizes.touchTarget)

    @Test fun idleTypingStreamingTransitionsPreserveFocusAndExistingHandlers() {
        val draft = mutableStateOf("")
        val streaming = mutableStateOf(false)
        var sent = ""; var live = 0; var mic = 0; var attach = 0; var stopped = 0
        show { Composer(value = draft.value, controlled = true, onDraftChange = { draft.value = it }, typing = streaming.value,
            onSend = { sent = it; draft.value = "" }, onVoiceMode = { live++ }, onMic = { mic++ }, onAttach = { attach++ }, onStop = { stopped++ }) }
        compose.onNodeWithText("Ask Dua AI...").assertIsDisplayed()
        compose.onNodeWithTag("composer-container").assertHeightIsAtLeast(64.dp)
        control("Voice mode").performClick(); control("Dictate").performClick(); control("Add photos and files").performClick()
        assertEquals(1, live); assertEquals(1, mic); assertEquals(1, attach)
        capture("idle")
        compose.onNodeWithContentDescription("Message").performClick().performTextInput("First character")
        compose.onNodeWithContentDescription("Message").assertIsFocused()
        control("Send message")
        compose.onNodeWithContentDescription("Voice mode").assertDoesNotExist()
        val text = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        val plus = compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot
        assertTrue(text.bottom <= plus.top)
        capture("typing")
        control("Send message").performClick()
        assertEquals("First character", sent)
        compose.runOnIdle { streaming.value = true }
        control("Stop response").performClick()
        compose.onNodeWithContentDescription("Voice mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        assertEquals(1, stopped)
        capture("streaming")
        compose.runOnIdle { streaming.value = false }
        control("Voice mode")
        compose.onNodeWithContentDescription("Message").assertIsFocused()
    }

    @Test fun inlineAttachmentsCanSendWithoutTextAndRemoveIndividually() {
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val files = mutableStateOf(listOf(ChatAttachment("photo.png", "image/png", Base64.getEncoder().encodeToString(png.toByteArray())),
            ChatAttachment("notes.txt", "text/plain", "dGV4dA==")))
        var sent = 0
        show { Composer(controlled = true, attachments = files.value, onSend = { sent++ },
            onRemoveAttachment = { index -> files.value = files.value.filterIndexed { i, _ -> i != index } }) }
        compose.onNodeWithTag("composer-attachments").assertIsDisplayed()
        compose.onNodeWithContentDescription("photo.png").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice mode").assertDoesNotExist()
        val strip = compose.onNodeWithTag("composer-attachments").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        val container = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(strip.top >= container.top && strip.bottom <= field.top)
        control("Send message").performClick(); assertEquals(1, sent)
        capture("attachments")
        control("Remove attachment photo.png").performClick()
        compose.onNodeWithText("notes.txt").assertIsDisplayed()
        control("Remove attachment notes.txt").performClick()
        compose.onNodeWithTag("composer-attachments").assertDoesNotExist()
        control("Voice mode")
    }

    @Test fun uploadsShowProgressFailureAndKeepSendGuardAndCancellation() {
        val upload = mutableStateOf(listOf(PendingUpload("upload", "report.pdf", "application/pdf", 100, sent = 30)))
        var cancelled = ""
        show { Composer(controlled = true, uploads = upload.value, uploading = upload.value.any { it.uploading },
            sendEnabled = false, onRemoveUpload = { cancelled = it; upload.value = emptyList() }) }
        compose.onNodeWithTag("attachment-upload-progress").assertExists()
        control("Cancel upload").assertIsEnabled()
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        compose.runOnIdle { upload.value = upload.value.map { it.copy(state = PendingUpload.FAILED, error = "Upload failed. Remove and choose the file again.") } }
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithText("Upload failed.", substring = true).assertIsDisplayed()
        control("Remove attachment report.pdf").performClick()
        assertEquals("upload", cancelled)
        control("Voice mode")
    }

    @Test fun modeSwitcherUsesCompactPillsAndChangesPlaceholders() {
        val mode = mutableStateOf(HomeMode.Chat)
        show { Composer(controlled = true, placeholder = if (mode.value == HomeMode.Task) "Describe..." else "Ask Dua AI...",
            aboveInput = { HomeModeSwitcher(mode.value, { mode.value = it }, enabled = true) }) }
        compose.onNodeWithText("Ask Dua AI...").assertIsDisplayed()
        val chat = compose.onNode(hasText("Chat") and isSelectable()).assertIsSelected().assertHeightIsAtLeast(48.dp)
        val tab = chat.fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 12.dp.toPx() }, card.top - tab.bottom, 1f)
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        compose.onNodeWithText("Describe...").assertIsDisplayed()
        compose.onNodeWithText("Ask Dua AI...").assertDoesNotExist()
        capture("task")
    }

    @Test fun multilineFieldCapsAtSixLinesAndStreamingOverridesDraftSend() {
        val draft = mutableStateOf("one\ntwo\nthree\nfour\nfive\nsix")
        val streaming = mutableStateOf(false)
        show { Composer(controlled = true, value = draft.value, onDraftChange = { draft.value = it }, typing = streaming.value) }
        val six = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { draft.value += "\nseven\neight\nnine\nten" }
        assertEquals(six, compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { streaming.value = true }
        control("Stop response")
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertTextContains("ten", substring = true)
    }

    @Test fun shortWindowKeepsActionsInsideCardAndScrollsTheTextInstead() {
        show { Composer(modifier = Modifier.heightIn(max = 240.dp), controlled = true,
            value = (1..20).joinToString("\n") { "Line $it" }, showModelSelector = false) }
        control("Send message")
        control("Dictate")
        val card = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("Send message").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        assertTrue(send.bottom <= card.bottom)
        assertTrue(field.bottom <= send.top)
        capture("short-window")
    }

    @Test fun stopRemainsAvailableWhenDictatingDuringStreaming() {
        val dictation = ChatDictation(mutableStateOf(true), mutableFloatStateOf(0f), {}, {}, {})
        var stopped = 0
        show { Composer(controlled = true, typing = true, dictation = dictation, onStop = { stopped++ }) }
        control("Cancel dictation")
        control("Done, use dictation")
        control("Stop response").performClick()
        assertEquals(1, stopped)
    }
}
