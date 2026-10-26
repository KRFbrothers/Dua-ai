package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.settings.LocalPreferences
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
@Config(sdk = [34], qualifiers = "w393dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerRecoveryUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(360.dp, 700.dp), contentAlignment = Alignment.BottomCenter) { content() }
            }
        }
    }
    private fun capture(label: String) {
        val file = File("build/approved-ui/composer-recovery-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun controlledDraftSurvivesRejectedAndDeferredSendsUntilOwnerClearsIt() {
        val draft = mutableStateOf("Keep this request")
        var accept = false
        val submissions = mutableListOf<String>()
        val edits = mutableListOf<String>()
        show { Composer(homeLayout = true, controlled = true, showModelSelector = false, value = draft.value,
            onDraftChange = { edits += it; draft.value = it }, onSend = {
                submissions += it
                if (accept) draft.value = ""
            }) }
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Keep this request")
        assertEquals("Keep this request", draft.value)
        assertTrue("Submitting does not synthesize a draft edit", edits.isEmpty())
        compose.onNodeWithContentDescription("Message").performImeAction()
        assertEquals(listOf("Keep this request", "Keep this request"), submissions)
        assertEquals("Keep this request", draft.value)
        capture("retained-draft")
        // A deferred owner decision arrives after the callback has returned.
        compose.runOnIdle { draft.value = "" }
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        compose.onNodeWithContentDescription("Message").performTextInput("Accepted request")
        compose.runOnIdle { accept = true }
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals("Accepted request", submissions.last())
        assertEquals("", draft.value)
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    @Test fun uploadCancellationUsesCurrentItemsRetainsReadyFilesAndNeverSendsTheDraft() {
        val ready = PendingUpload("ready", "ready.pdf", "application/pdf", 10, state = PendingUpload.DONE,
            ref = FileRef("ready-path", "ready.pdf", "application/pdf", 10))
        val state = mutableStateOf(ChatUiState(chatId = "existing", draft = "Explain my files",
            uploads = listOf(PendingUpload("first", "first.pdf", "application/pdf", 100, sent = 20), ready)))
        var sends = 0
        val cancelled = mutableListOf<String>()
        val actions = ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) },
            onSend = { sends++ }, onCancelUpload = { id ->
                cancelled += id
                state.value = state.value.copy(uploads = state.value.uploads.filterNot { it.id == id })
            })
        show { ChatScreen(state.value, actions) }
        compose.onNodeWithContentDescription("Cancel upload").assertIsEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").performClick().performImeAction()
        assertEquals(0, sends)
        assertEquals("Explain my files", state.value.draft)
        // The cancel callback must use the latest list rather than the first composition.
        compose.runOnIdle { state.value = state.value.copy(uploads = listOf(ready,
            PendingUpload("second", "second.pdf", "application/pdf", 100, sent = 50),
            PendingUpload("third", "third.pdf", "application/pdf", 100))) }
        capture("uploading")
        compose.onNodeWithContentDescription("Cancel upload").performClick()
        assertEquals(listOf("second", "third"), cancelled)
        assertEquals(listOf(ready), state.value.uploads)
        assertEquals("Explain my files", state.value.draft)
        assertEquals(0, sends)
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    @Test fun stopOverridesUploadCancellationAndStandaloneDraftStillClearsAfterSend() {
        val typing = mutableStateOf(true)
        val uploads = mutableStateOf(listOf(PendingUpload("active", "active.pdf", "application/pdf", 100)))
        var stops = 0; var cancels = 0
        val sent = mutableListOf<String>()
        show { Composer(typing = typing.value, uploads = uploads.value, onStop = { stops++ },
            onCancelUpload = { cancels++ }, onSend = { sent += it }) }
        compose.onNodeWithContentDescription("Stop response").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Cancel upload").assertDoesNotExist()
        assertEquals(1, stops)
        assertEquals(0, cancels)
        compose.runOnIdle { typing.value = false; uploads.value = emptyList() }
        compose.onNodeWithContentDescription("Message").performTextInput("Standalone request")
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals(listOf("Standalone request"), sent)
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }
}
