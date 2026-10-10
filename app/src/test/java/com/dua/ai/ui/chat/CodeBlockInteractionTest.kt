package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.model.CodeRun
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rendering changes must preserve the complete clipboard payload, including trailing newlines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class CodeBlockInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun codeCopyPreservesWhitespaceAndFinalNewlines() {
        val code = "  print('hello')\n\n"
        lateinit var clipboard: ClipboardManager
        compose.setContent {
            clipboard = LocalClipboardManager.current
            DuaTheme(LocalPreferences()) { CodeBlock(code, "python") }
        }
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle { assertEquals(code, clipboard.getText()?.text) }
    }

    @Test fun changedCodeImmediatelyReplacesDisplayedTextAndCopyFeedback() {
        val code = mutableStateOf("print('first')")
        compose.setContent { DuaTheme(LocalPreferences()) { CodeBlock(code.value, "python") } }
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle { code.value = "print('replacement')" }
        compose.onNodeWithText("print('first')").assertDoesNotExist()
        compose.onNodeWithText("print('replacement')").assertExists()
        compose.onNodeWithText("Copy").assertExists()
    }

    @Test fun codeRunCopiesOutputSeparatelyFromCode() {
        val run = CodeRun(code = "print('hello')\n", output = "hello\n\n", outcome = "ok")
        lateinit var clipboard: ClipboardManager
        compose.setContent {
            clipboard = LocalClipboardManager.current
            DuaTheme(LocalPreferences()) { CodeRunBlock("reply", 0, 1, run, initiallyExpanded = true) }
        }
        compose.onNodeWithText("Copy output").performClick()
        compose.runOnIdle { assertEquals(run.output, clipboard.getText()?.text) }
        compose.onNodeWithText("Copy code").performClick()
        compose.runOnIdle { assertEquals(run.code, clipboard.getText()?.text) }
    }
}
