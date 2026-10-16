package com.dua.ai.ui.chat

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtifactCanvasTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val table = "| Item | Status |\n| --- | --- |\n| Build | Ready |"
    private val message = ChatMessage(id = "artifact", text = "```markdown\n$table\n```", isUser = false)
    private val state = mutableStateOf(ChatUiState(chatId = "chat", messages = listOf(message)))
    private lateinit var clipboard: ClipboardManager
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    @After fun restoreFontConfiguration() { RuntimeEnvironment.setFontScale(1f) }

    private fun show(fontScale: Float = 1f) {
        // Dialogs use their own Android window density; exercise the real configuration too.
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            clipboard = LocalClipboardManager.current
            listState = rememberLazyListState(); scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        MessageList(state.value, ChatActions(), listState)
                    }
                }
            }
        }
    }
    private fun open() = compose.onNodeWithTag("open-canvas").performScrollTo().performClick()
    private fun webViews(): List<WebView> {
        fun find(view: View): List<WebView> = if (view is WebView) listOf(view)
            else if (view is ViewGroup) (0 until view.childCount).flatMap { find(view.getChildAt(it)) } else emptyList()
        return ShadowDialog.getShownDialogs().flatMap { it.window?.decorView?.let(::find).orEmpty() }
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = java.io.File("build/approved-ui/canvas-$name-${if (dark) "dark" else "light"}.png")
            file.parentFile?.mkdirs()
            compose.onNodeWithTag("artifact-canvas-sheet").captureRoboImage(file.absolutePath)
        }
    }

    @Test fun opensFormattedTableAndSwitchesToSelectableSourceWithFullCopyPayload() {
        show(); open()
        compose.onNodeWithTag("canvas-preview-tab").assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("canvas-source").assertDoesNotExist()
        compose.onNodeWithTag("code-preview").assertExists()
        capture("table")
        compose.onNode(hasText("Ready", substring = true) and hasAnyAncestor(hasTestTag("code-preview"))).assertExists()
        compose.onNodeWithTag("canvas-code-tab").performClick().assertIsSelected()
        compose.onNodeWithTag("code-preview").assertDoesNotExist()
        compose.onNodeWithTag("canvas-source").assert(hasText(table + "\n"))
        compose.onNodeWithContentDescription("Copy artifact source").performClick()
        compose.runOnIdle { assertEquals(table + "\n", clipboard.getText()?.text) }
        compose.onNodeWithContentDescription("Artifact copied").assertExists()
        capture("source")
        compose.onNodeWithContentDescription("Close canvas").performClick()
        compose.onNodeWithTag("artifact-canvas-sheet").assertDoesNotExist()
    }

    @Test fun completedAssistantOnlyNeverShowsTheEntryOnUserPlainOrStreamingMessages() {
        state.value = state.value.copy(messages = listOf(message.copy(id = "user", isUser = true), message.copy(state = ChatMessage.STREAMING)))
        compose.mainClock.autoAdvance = false
        show(); compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("open-canvas").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(message.copy(id = "plain", text = "Plain answer"))) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("open-canvas").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(message.copy(state = "stopped"))) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("open-canvas").assertExists()
    }

    @Test fun webViewIsDestroyedOnCodeSwitchAndOnDismissal() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```html\n<h1>Hello</h1>\n```")))
        show(); open()
        lateinit var first: WebView
        compose.runOnIdle {
            first = webViews().single()
            assertFalse(first.settings.javaScriptEnabled)
            assertTrue(first.settings.blockNetworkLoads)
        }
        compose.onNodeWithTag("canvas-code-tab").performClick()
        compose.runOnIdle { assertReleased(first) }
        compose.onNodeWithTag("canvas-preview-tab").performClick()
        lateinit var second: WebView
        compose.runOnIdle { second = webViews().single(); assertNotSame(first, second) }
        compose.onNodeWithContentDescription("Close canvas").performClick()
        compose.runOnIdle { assertReleased(second) }
    }

    @Test fun selectingAnotherArtifactReleasesMermaidAndKeepsHtmlJavascriptOff() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```mermaid\ngraph TD; A-->B\n```\n```html\n<h1>Hello</h1>\n```")))
        show(); open()
        lateinit var diagram: WebView
        compose.runOnIdle { diagram = webViews().single(); assertTrue(diagram.settings.javaScriptEnabled) }
        compose.onNodeWithTag("canvas-artifact-1").performClick()
        compose.runOnIdle {
            assertReleased(diagram)
            val html = webViews().single()
            assertFalse(html.settings.javaScriptEnabled)
            assertFalse(html.settings.allowContentAccess)
            assertFalse(html.settings.allowFileAccess)
        }
        compose.onNodeWithContentDescription("Close canvas").performClick()
    }

    @Test fun sheetSurvivesSourceRowDisposalButClosesWhenSwitchingChat() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```svg\n<svg/>\n```")) + (1..30).map {
            ChatMessage(id = "reply-$it", text = "Reply $it", isUser = false)
        })
        show(); open()
        lateinit var view: WebView
        compose.runOnIdle { view = webViews().single(); scope.launch { listState.scrollToItem(30) } }
        compose.onNodeWithTag("artifact-canvas-sheet").assertExists()
        compose.runOnIdle { assertFalse(shadowOf(view).wasDestroyCalled()); state.value = ChatUiState(chatId = "other") }
        compose.onNodeWithTag("artifact-canvas-sheet").assertDoesNotExist()
        compose.runOnIdle { assertReleased(view) }
    }

    @Test fun uiRuntimeCodeIsRecognizedWithoutInventingACompiledPreview() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```jsx\nfunction App() {\nreturn <div>Hello</div>;\n}\n```")))
        show(); open()
        compose.onNodeWithTag("canvas-code-tab").assertIsSelected()
        compose.onNodeWithTag("canvas-source").assertExists()
        compose.onNodeWithTag("canvas-preview-tab").performClick()
        compose.onNodeWithTag("canvas-runtime-required").assertExists()
        compose.onNodeWithTag("canvas-source").assertDoesNotExist()
        compose.runOnIdle { assertTrue(webViews().isEmpty()) }
        compose.onNodeWithContentDescription("Close canvas").performClick()
    }

    @Test fun systemBackDismissesCanvasAndReleasesItsRenderer() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```svg\n<svg/>\n```")))
        show(); open()
        lateinit var view: WebView
        compose.runOnIdle {
            view = webViews().single()
            (ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("artifact-canvas-sheet").assertDoesNotExist()
        compose.runOnIdle { assertReleased(view) }
    }

    @Test fun repeatedOpenCloseNeverReusesAnAlreadyDestroyedWebView() {
        state.value = state.value.copy(messages = listOf(message.copy(text = "```html\n<h1>Hello</h1>\n```")))
        show()
        val views = mutableListOf<WebView>()
        repeat(3) {
            open()
            compose.runOnIdle {
                val next = webViews().single()
                assertTrue(views.none { it === next })
                views += next
            }
            compose.onNodeWithContentDescription("Close canvas").performClick()
            compose.runOnIdle { views.forEach(::assertReleased) }
        }
    }

    @Test fun sheetControlsRemainAccessibleAtLargeFontScale() {
        show(fontScale = 1.5f); open()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Canvas").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1.5f, layouts.single().layoutInput.density.fontScale, .01f)
        compose.onNodeWithContentDescription("Close canvas").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Copy artifact source").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("canvas-preview-tab").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("canvas-code-tab").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        capture("large-font")
        compose.onNodeWithContentDescription("Close canvas").performClick()
    }

    private fun assertReleased(view: WebView) {
        assertTrue(shadowOf(view).wasDestroyCalled())
        assertTrue(shadowOf(view).wasClearHistoryCalled())
        assertEquals("about:blank", shadowOf(view).lastLoadedUrl)
        assertFalse(view.settings.javaScriptEnabled)
        assertNull(view.parent)
    }
}
