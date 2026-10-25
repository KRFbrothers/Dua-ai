package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.model.AgentApproval
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Agent write actions show an Approve / Reject card under the finished reply; nothing runs without a tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class AgentApprovalCardTest {
    @get:Rule val compose = createComposeRule()
    private val approval = AgentApproval("AbCdEfGhIjKlMnOpQrStUvWx", "create_issue", "Create a GitHub issue",
        listOf("Title: Crash on launch"), expiresAt = Long.MAX_VALUE)

    @Test fun approveAndRejectReportTheDecisionAndStatusesRender() {
        val decisions = mutableListOf<Pair<String, Boolean>>()
        val reply = mutableStateOf(ChatMessage(text = "**Approval needed.**", isUser = false, approvals = listOf(approval)))
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply.value, isLast = true, typing = false, speaking = false, speakEnabled = false, generatedVoice = false,
                    retryWebMode = null, onRetry = {}, onSpeak = {}, onApproval = { id, ok -> decisions += id to ok })
            }
        }
        compose.onNodeWithText("Create a GitHub issue").assertExists()
        compose.onNodeWithText("Title: Crash on launch").assertExists()
        compose.onNodeWithContentDescription("Approve: Create a GitHub issue").performClick()
        compose.onNodeWithContentDescription("Reject: Create a GitHub issue").performClick()
        assertEquals(listOf(approval.id to true, approval.id to false), decisions)

        compose.runOnIdle { reply.value = reply.value.copy(approvals = listOf(approval.copy(status = AgentApproval.Status.Done, result = "Issue created: #5"))) }
        compose.onNodeWithText("Issue created: #5").assertExists()
        compose.onNodeWithContentDescription("Approve: Create a GitHub issue").assertDoesNotExist()

        compose.runOnIdle { reply.value = reply.value.copy(approvals = listOf(approval.copy(status = AgentApproval.Status.Rejected))) }
        compose.onNodeWithText("You declined. Nothing was changed.").assertExists()
    }

    @Test fun cardIsHiddenWhileStreamingAndExpiredCardsHaveNoButtons() {
        val reply = mutableStateOf(ChatMessage(text = "x", isUser = false, state = ChatMessage.STREAMING, approvals = listOf(approval)))
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply.value, isLast = true, typing = true, speaking = false, speakEnabled = false, generatedVoice = false,
                    retryWebMode = null, onRetry = {}, onSpeak = {})
            }
        }
        compose.onNodeWithText("Create a GitHub issue").assertDoesNotExist()
        compose.runOnIdle { reply.value = reply.value.copy(state = "complete", approvals = listOf(approval.copy(expiresAt = 1L))) }
        compose.onNodeWithText("This approval expired. Ask the agent again.").assertExists()
        compose.onNodeWithContentDescription("Approve: Create a GitHub issue").assertDoesNotExist()
    }
}
