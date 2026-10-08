package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.dua.ai.model.BuildFile
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.BuildProposal
import com.dua.ai.model.BuildPullRequest
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ProposalFile
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildModeConfig
import com.dua.ai.setup.BuildPrConfig
import com.dua.ai.ui.chat.BuildPrUiState
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
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

/** Phase 5C Build pull requests: review card, confirmation, created and error states. Record with ./gradlew :app:recordRoborazziDebug -Pdua.screenshots */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BuildPrScreenshotTest(private val dark: Boolean) {
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

    /** Renders the chat and scrolls so that [anchor] (text inside the review card) is on screen. */
    private fun card(name: String, state: com.dua.ai.ui.chat.ChatUiState, anchor: String) {
        compose.setContent { Themed { ChatScreen(state, ChatActions()) } }
        compose.waitForIdle()
        compose.onNodeWithText(anchor, substring = true, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path(name))
    }

    @Test fun reviewCard() = card("50_build_pr_review_card", BuildPrData.chat(), "Nothing is written to GitHub")

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun confirmDialog() {
        compose.setContent { Themed { ChatScreen(BuildPrData.chat().copy(confirmPr = "m1"), ChatActions()) } }
        compose.waitForIdle()
        captureScreenRoboImage(path("51_build_pr_confirm_dialog"))
    }

    @Test fun created() = card("52_build_pr_created", BuildPrData.chat(created = true), "Open Draft PR")

    @Test fun conflictError() = card("53_build_pr_error", BuildPrData.chat().copy(buildPr = mapOf("m1" to BuildPrUiState(error = BuildPrData.CONFLICT))), "Nothing is written to GitHub")

    @Test fun unavailable() = card("54_build_pr_unavailable", BuildPrData.chat().copy(buildPrHint = BuildPrData.OFF,
        buildConfig = BuildModeConfig(true, pr = BuildPrConfig(false, BuildPrData.OFF))), "DUA_BUILD_PR=1")
}

internal object BuildPrData {
    const val CONFLICT = "web/src/server/vercel-handler.mjs changed on main since Dua read it. The proposal is out of date: ask Dua to regenerate it."
    const val OFF = "Pull requests from Build mode are off. To allow them, set DUA_BUILD_PR=1 and GITHUB_BUILD_WRITE_TOKEN in hosting settings."
    private const val REPO = "KRFbrothers/Dua-ai"
    private const val BRANCH = "dua/make-chat-burst-limit-configurable-261008-134512"
    private val handlerDiff = """
        --- a/web/src/server/vercel-handler.mjs
        +++ b/web/src/server/vercel-handler.mjs
        @@ -1,9 +1,11 @@
         import { createBurstGuard } from './vercel-burst.mjs';
         import { handleService } from './service-handler.mjs';
        +import { envInteger } from '../core/adapters.mjs';
         export function createVercelHandler({ env = process.env } = {}) {
        -   const wait = guard(request, group, group === 'chat' ? 5 : 20);
        +   // DUA_CHAT_BURST: chat requests per minute (default 5).
        +   const chat = envInteger(env, 'DUA_CHAT_BURST', 5, 1, 60);
        +   const wait = guard(request, group, group === 'chat' ? chat : 20);
    """.trimIndent()

    fun chat(created: Boolean = false): com.dua.ai.ui.chat.ChatUiState {
        val proposal = BuildProposal(
            id = "0f8fad5b-d9cb-469f-a165-70867728950e", repository = REPO, baseBranch = "main", branch = BRANCH,
            title = "Make the chat burst limit configurable",
            summary = "Adds DUA_CHAT_BURST (default 5 per minute) to the Vercel handler and documents it. Test: cd web && npm test.",
            files = listOf(
                ProposalFile("web/src/server/vercel-handler.mjs", "update", "…", "a".repeat(40), handlerDiff, 4, 1),
                ProposalFile("web/test/burst.test.mjs", "create", "…", null, "--- /dev/null\n+++ b/web/test/burst.test.mjs\n@@ -0,0 +1,3 @@\n+import test from 'node:test';", 24, 0),
                ProposalFile("docs/old-limits.md", "delete", null, "b".repeat(40), "--- a/docs/old-limits.md\n+++ /dev/null\n@@ -1,1 +0,0 @@\n-Old", 0, 12),
            ),
            pullRequest = if (created) BuildPullRequest("https://github.com/$REPO/pull/80", 80, true, BRANCH) else null,
        )
        return BuildData.base.copy(buildConfig = BuildModeConfig(true, pr = BuildPrConfig(true)), messages = listOf(
            ChatMessage(id = "u1", text = "Make the chat rate limit configurable with an env var.", isUser = true),
            ChatMessage(id = "m1", text = "I read the handler and its test. Here is a small change; review it below and tap **Create PR** if it looks right.",
                isUser = false, model = "Dua", build = BuildInfo(REPO, "main", listOf(BuildFile("web/src/server/vercel-handler.mjs", 940), BuildFile("web/src/server/vercel-burst.mjs", 610))),
                proposal = proposal),
        ))
    }
}
