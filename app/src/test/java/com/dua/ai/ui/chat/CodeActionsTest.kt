package com.dua.ai.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.DuaLightColors
import com.dua.ai.ui.theme.DuaDarkColors
import com.dua.ai.ui.theme.KrfBrothersCodeFont
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodeActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharePreservesCodeWhitespaceAndUsesAndroidChooser() {
        lateinit var context: Context
        val code = "  print(42)\n\n"
        compose.setContent { context = LocalContext.current; DuaTheme(LocalPreferences()) { CodeBlock(code, "python") } }
        compose.onNodeWithContentDescription("Share code").performClick()
        compose.runOnIdle {
            var base = context
            while (base is ContextWrapper && base !is Activity) base = base.baseContext
            val chooser = shadowOf(base as Activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val sent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, sent.action)
            assertEquals("text/plain", sent.type)
            assertEquals(code, sent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    @Test fun previewIsExplicitAndUsesTheNativeMarkdownRenderer() {
        compose.setContent { DuaTheme(LocalPreferences()) { CodeBlock("# Artifact heading", "markdown") } }
        compose.onNodeWithTag("code-preview").assertDoesNotExist()
        compose.onNodeWithContentDescription("Preview code").performClick()
        compose.onNodeWithTag("code-preview").assertExists()
        compose.onNodeWithText("Artifact heading").assertExists()
        compose.onNodeWithText("Hide preview").performClick()
        compose.onNodeWithTag("code-preview").assertDoesNotExist()
    }

    @Test fun runDelegatesOnlyToAnAvailablePythonProvider() {
        var sent: String? = null
        compose.setContent { DuaTheme(LocalPreferences()) {
            CompositionLocalProvider(LocalCodeExecutionUi provides CodeExecutionUi(null) { sent = it; true }) { CodeBlock("print(42)", "py") }
        } }
        compose.onNodeWithContentDescription("Run code").performClick()
        compose.runOnIdle { assertEquals("print(42)", sent) }
        compose.onNodeWithText("Code run requested.", substring = true).assertExists()
    }

    @Test fun unavailableExecutionNeverPretendsToRun() {
        var calls = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            CompositionLocalProvider(LocalCodeExecutionUi provides CodeExecutionUi("Sandbox unavailable") { calls++; true }) { CodeBlock("print(42)", "python") }
        } }
        compose.onNodeWithContentDescription("Run code").performClick()
        compose.onNodeWithText("Sandbox unavailable").assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun chatScreenConnectsCodeBlocksToTheExistingExecutionHandler() {
        var sent: String? = null
        compose.setContent { DuaTheme(LocalPreferences()) {
            ChatScreen(ChatUiState(messages = listOf(com.dua.ai.model.ChatMessage(text = "```python\nprint(42)\n```", isUser = false)),
                codeSnippetUnavailableReason = null), ChatActions(onRunCodeSnippet = { sent = it; true }))
        } }
        compose.onNodeWithContentDescription("Run code").performClick()
        // The existing Markdown parser supplies fence contents without the closing line's newline.
        compose.runOnIdle { assertEquals("print(42)", sent) }
    }

    @Test fun headerActionsWrapOnSmallScreensAndKeepAccessibleTouchTargets() {
        compose.setContent { DuaTheme(LocalPreferences()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f)) {
                Box(Modifier.width(280.dp)) { CodeBlock("# Hello", "markdown") }
            }
        } }
        val nodes = listOf("Copy code", "Share code", "Preview code").map { description ->
            compose.onNodeWithContentDescription(description).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .fetchSemanticsNode().boundsInRoot
        }
        for (i in nodes.indices) for (j in i + 1 until nodes.size) assertFalse(nodes[i].overlaps(nodes[j]))
    }

    @Test fun codeUsesTheExportedFontAndOriginalSyntaxPaletteInBothThemes() {
        compose.setContent { DuaTheme(LocalPreferences()) { CodeBlock("print(42)", "python") } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("print(42)").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(KrfBrothersCodeFont, layouts.first().layoutInput.style.fontFamily)
        val code = "const value = \"hello\"; // comment\nconst n = 42;"
        for (colors in listOf(DuaLightColors, DuaDarkColors)) {
            val result = highlightCode(code, "javascript", colors)
            for (token in listOf("const", "hello", "comment", "42")) {
                val start = code.indexOf(token)
                assertTrue("$token has a syntax color", result.spanStyles.any { it.start <= start && it.end > start && it.item.color != Color.Unspecified })
            }
        }
    }

    @Test fun unsupportedLanguagesAndOversizeSnippetsCannotReachTheRunner() {
        val runner = CodeExecutionUi(null) { error("must not run") }
        assertNotNull(codeRunUnavailableReason("javascript", "alert(1)", runner))
        assertNotNull(codeRunUnavailableReason("python", "x".repeat(8000), runner))
        assertNotNull(codeRunUnavailableReason("py", " ", runner))
        assertNull(codeRunUnavailableReason("python", "print(1)", runner))
    }
}
