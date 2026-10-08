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
import com.dua.ai.model.BuildFile
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildModeConfig
import com.dua.ai.setup.BuildRepositories
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.setup.RepositoryResult
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatSheet
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 5B Build mode surfaces. Record with ./gradlew :app:recordRoborazziDebug -Pdua.screenshots */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BuildScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"
    private fun path(name: String) = "build/outputs/roborazzi/${name}_$mode.png"

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        DuaTheme(LocalPreferences(theme = mode)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path(name))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(content) }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    @Test fun repositoryPicker() = screen("43_build_repo_picker") { ChatScreen(BuildData.base, ChatActions(), initialSheet = ChatSheet.Build) }

    @Test fun unavailable() = screen("44_build_unavailable") {
        ChatScreen(BuildData.base.copy(buildRepo = null, buildConfig = BuildModeConfig(false, BuildData.REASON), buildHint = BuildData.REASON,
            buildRepositories = BuildRepositories()), ChatActions(), initialSheet = ChatSheet.Build)
    }

    @Test fun buildChat() = shot("45_build_chat_diff") { ChatScreen(BuildData.chat, ChatActions()) }

    @Test fun buildChip() {
        compose.setContent { Themed { ChatScreen(BuildData.base.copy(buildRepo = null, draft = "Explain how streaming works"), ChatActions()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Build mode:", substring = true).performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path("47_build_mode_chip"))
    }

    @Test fun buildChatAllFiles() = shot("46_build_chat_files_expanded") { ChatScreen(BuildData.chat.copy(expandedBuild = setOf("m1")), ChatActions()) }
}

internal object BuildData {
    const val REASON = "Build mode needs the GitHub connector: set ENABLE_GITHUB_CONNECTOR=1 and a read-only GITHUB_CONNECTOR_TOKEN in hosting settings."
    private const val REPO = "KRFbrothers/Dua-ai"
    private val repositories = listOf(
        RepositoryResult("Dua-ai", "https://github.com/$REPO", "Personal AI assistant: Android app and Vercel backend", REPO),
        RepositoryResult("dua-apk-private", "https://github.com/KRFbrothers/dua-apk-private", "Private debug builds", "KRFbrothers/dua-apk-private", private = true),
        RepositoryResult("notes", "https://github.com/KRFbrothers/notes", "", "KRFbrothers/notes"),
    )
    val base = ChatUiState(greeting = "Good evening, Fareed", webSearchConfigured = true, storedFiles = true,
        codeConfig = CodeExecutionConfig(true, providers = setOf("Dua", "Gemini")),
        buildRepo = REPO, buildConfig = BuildModeConfig(true), buildRepositories = BuildRepositories(repositories = repositories))

    private val diff = """
        Rate limiting already lives in `vercel-handler.mjs`; the chat group allows 5 requests a minute. To make it configurable:

        ```diff
        --- a/web/src/server/vercel-handler.mjs
        +++ b/web/src/server/vercel-handler.mjs
        @@ -12,7 +12,8 @@
         const GROUPS = {
        -  chat: { limit: 5, windowMs: 60_000 },
        +  // DUA_CHAT_BURST: requests per minute (default 5).
        +  chat: { limit: envInteger(env, 'DUA_CHAT_BURST', 5, 1, 60), windowMs: 60_000 },
           voice: { limit: 20, windowMs: 60_000 },
         };
        ```

        Then add a test in `web/test/chat.test.mjs` for the new variable.
    """.trimIndent()

    val chat = base.copy(messages = listOf(
        ChatMessage(id = "u1", text = "Where is the chat rate limit, and how do I make it configurable?", isUser = true),
        ChatMessage(id = "m1", text = diff, isUser = false, model = "Dua", build = BuildInfo(REPO, "main", listOf(
            BuildFile("web/src/server/vercel-handler.mjs", 4210), BuildFile("web/src/server/service-handler.mjs", 3120),
            BuildFile("web/src/core/access-policy.mjs", 2890), BuildFile("web/test/chat.test.mjs", 61440),
            BuildFile("web/vercel.json", 310), BuildFile("PROVIDERS.md", 102400, truncated = true)))),
    ))
}
