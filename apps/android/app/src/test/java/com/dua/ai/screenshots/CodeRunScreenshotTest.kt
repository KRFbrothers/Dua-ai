package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 5 Run code surfaces. Record with ./gradlew :app:recordRoborazziDebug -Pdua.screenshots */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CodeRunScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"

    private fun shot(name: String, scrollTo: String? = null, content: @Composable () -> Unit) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        // Bring a mode chip into view in the horizontally scrolling chip row.
        scrollTo?.let { compose.onNodeWithContentDescription(it, substring = true).performScrollTo(); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$mode.png")
    }

    @Test fun ranCodeCollapsed() = shot("40_ran_code_collapsed") { ChatScreen(CodeRunData.chat, ChatActions()) }

    @Test fun ranCodeExpanded() = shot("41_ran_code_expanded") { ChatScreen(CodeRunData.chat.copy(expandedCode = setOf("m1")), ChatActions()) }

    @Test fun runCodeComposer() = shot("42_run_code_composer", scrollTo = "Run code on") { ChatScreen(CodeRunData.composer, ChatActions()) }
}

internal object CodeRunData {
    private val code = """
        import pandas as pd
        df = pd.read_csv("sales.csv")
        monthly = df.groupby("month")["amount"].sum()
        print(monthly.round(2).to_string())
        print("Best month:", monthly.idxmax())
    """.trimIndent()
    private val output = "month\nApr    18450.00\nFeb    12210.50\nJan     9830.25\nMar    15102.75\nBest month: Apr"

    val base = ChatUiState(greeting = "Good evening, Fareed", webSearchConfigured = true, storedFiles = true,
        codeConfig = CodeExecutionConfig(true, providers = setOf("Dua", "Gemini")))

    val chat = base.copy(messages = listOf(
        ChatMessage(id = "u1", text = "Which month had the highest sales?\nFiles: sales.csv", isUser = true),
        ChatMessage(id = "m1", text = "**April** had the highest sales at **18,450**, about 22% above March.\n\n| Month | Sales |\n|---|---|\n| Jan | 9,830.25 |\n| Feb | 12,210.50 |\n| Mar | 15,102.75 |\n| Apr | 18,450.00 |",
            isUser = false, model = "Dua", codeRuns = listOf(CodeRun("python", code, output, "ok"))),
    ))

    val composer = base.copy(runCode = true, draft = "Plot my monthly sales and find the trend")
}
