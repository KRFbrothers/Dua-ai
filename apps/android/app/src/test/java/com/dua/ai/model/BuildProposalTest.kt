package com.dua.ai.model

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import com.dua.ai.ui.chat.BuildPrUiState
import com.dua.ai.ui.chat.ProposalStage
import com.dua.ai.ui.chat.confirmationText
import com.dua.ai.ui.chat.proposalStage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Build pull requests (Phase 5C): proposal events, review card state, the create request, the PR link and local history. */
class BuildProposalTest {
    private val sha = "a".repeat(40)
    private val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
    private fun file(path: String, action: String, content: String? = "new\n", baseSha: String? = if (action == "create") null else sha) = JSONObject()
        .put("path", path).put("action", action).put("additions", 2).put("deletions", 1).put("diff", "--- a/$path\n+++ b/$path\n@@ -1 +1 @@\n-old\n+new")
        .apply { if (content != null && action != "delete") put("content", content); if (baseSha != null) put("baseSha", baseSha) }
    private fun proposalJson(vararg files: JSONObject = arrayOf(file("README.md", "update"), file("docs/new.md", "create"), file("old.txt", "delete"))) = JSONObject()
        .put("type", "proposal").put("id", id).put("repository", "KRFbrothers/Dua-ai").put("baseBranch", "main").put("branch", "dua/fix-readme-261008-134512")
        .put("title", "Fix README").put("summary", "Renames the title.").put("files", JSONArray().apply { files.forEach { put(it) } })
    private fun parse(json: JSONObject): List<ReplyEvent> {
        val parser = ChatStreamParser()
        return listOfNotNull(parser.line("event: proposal"), parser.line("data: $json"), parser.line(""))
    }

    @Test fun proposalEventsDecodeIntoAReviewableProposal() {
        val proposal = (parse(proposalJson()).single() as ReplyEvent.Proposal).proposal
        assertEquals("KRFbrothers/Dua-ai", proposal.repository)
        assertEquals(listOf("update", "create", "delete"), proposal.files.map { it.action })
        assertEquals(listOf("Changed", "Added", "Deleted"), proposal.files.map { it.label })
        assertEquals(sha, proposal.files[0].baseSha)
        assertNull(proposal.files[1].baseSha)
        assertNull(proposal.files[2].content)
        assertEquals(6, proposal.additions); assertEquals(3, proposal.deletions)
        assertEquals("3 files", proposal.filesLabel)
        assertTrue(proposal.complete)
    }

    @Test fun unsafeProposalsAreDropped() {
        val bad = listOf(
            proposalJson(file(".github/workflows/ci.yml", "create")), proposalJson(file("../x", "create")), proposalJson(file("/etc/passwd", "create")),
            proposalJson(file(".git/config", "update")), proposalJson(file("a", "rename")), proposalJson(file("a.md", "create"), file("a.md", "create")),
            proposalJson().put("branch", "main"), proposalJson().put("branch", "dua/../main"), proposalJson().put("id", "x"), proposalJson().put("repository", "a/b/c"),
            proposalJson().put("title", ""), proposalJson().put("files", JSONArray()))
        bad.forEach { assertTrue(it.toString(), parse(it).isEmpty()) }
        assertFalse("an update without its base sha cannot become a PR", BuildProposals.fromJson(proposalJson(file("a.md", "update", baseSha = null)))!!.complete)
    }

    @Test fun accumulatorKeepsTheProposalAndLargeEventsFit() {
        val accumulator = StreamAccumulator(0L) { 0L }
        val proposal = BuildProposals.fromJson(proposalJson())!!
        accumulator.apply(ReplyEvent.Proposal(proposal))
        accumulator.apply(ReplyEvent.Text("Review it and tap Create PR."))
        assertEquals(proposal, accumulator.snapshot().proposal)
        val big = proposalJson(file("big.txt", "create", content = "x".repeat(1_500_000)))
        assertEquals(1_500_000, (parse(big).single() as ReplyEvent.Proposal).proposal.files.single().content!!.length)
    }

    @Test fun createRequestCarriesTheApprovedProposalOnly() {
        val proposal = BuildProposals.fromJson(proposalJson())!!
        val body = BuildProposals.createRequest(proposal, "  Fix   README title ")
        assertEquals(setOf("repository", "proposalId", "title", "summary", "branch", "files"), body.keys().asSequence().toSet())
        assertEquals("Fix README title", body.getString("title"))
        assertEquals(id, body.getString("proposalId"))
        assertEquals("dua/fix-readme-261008-134512", body.getString("branch"))
        val files = body.getJSONArray("files")
        assertEquals(setOf("path", "action", "content", "baseSha"), files.getJSONObject(0).keys().asSequence().toSet())
        assertEquals(setOf("path", "action", "content"), files.getJSONObject(1).keys().asSequence().toSet())
        assertEquals(setOf("path", "action", "baseSha"), files.getJSONObject(2).keys().asSequence().toSet())
        assertFalse("diffs are display-only and never sent back", body.toString().contains("\"diff\""))
        assertThrows(IllegalArgumentException::class.java) { BuildProposals.createRequest(proposal, "   ") }
        assertThrows(IllegalArgumentException::class.java) { BuildProposals.createRequest(proposal, "x".repeat(121)) }
        val dropped = proposal.copy(files = proposal.files.map { it.copy(content = null) })
        assertThrows(IllegalArgumentException::class.java) { BuildProposals.createRequest(dropped, "Fix") }
    }

    @Test fun onlyGitHubPullRequestLinksOfTheSameRepositoryAreAccepted() {
        val ok = BuildProposals.pullRequest(JSONObject("{\"url\":\"https://github.com/KRFbrothers/Dua-ai/pull/80\",\"number\":80,\"draft\":true,\"branch\":\"dua/fix-261008-134512\"}"), "KRFbrothers/Dua-ai")!!
        assertEquals("Draft PR #80", ok.label)
        assertEquals("dua/fix-261008-134512", ok.branch)
        assertNull(BuildProposals.pullRequest(JSONObject("{\"url\":\"https://evil.example/KRFbrothers/Dua-ai/pull/80\",\"number\":80}"), "KRFbrothers/Dua-ai"))
        assertNull(BuildProposals.pullRequest(JSONObject("{\"url\":\"https://github.com/other/repo/pull/80\",\"number\":80}"), "KRFbrothers/Dua-ai"))
        assertNull(BuildProposals.pullRequest(JSONObject("{\"url\":\"https://github.com/KRFbrothers/Dua-ai/pull/81\",\"number\":80}"), "KRFbrothers/Dua-ai"))
        assertEquals("PR #3", BuildPullRequest("u", 3, draft = false).label)
    }

    @Test fun proposalsAndPrLinksSurviveHistoryAndExport() {
        val proposal = BuildProposals.fromJson(proposalJson())!!
        val reply = ChatMessage(id = "r", text = "Review it.", isUser = false, model = "Dua", proposal = proposal)
        val chat = SavedChat("c", "Dua", "", listOf(ChatMessage(id = "u", text = "Fix it", isUser = true, model = "Dua"), reply), 5)
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "c")).second.single().messages[1].proposal!!
        assertEquals(proposal, restored)
        assertTrue("contents survive a restart so the PR can still be created", restored.complete)
        assertEquals(proposal, ChatHistoryCodec.parseImport(ChatHistoryCodec.export(listOf(chat))).single().messages[1].proposal)
        // After the PR exists the contents are dropped; the link and the diffs stay.
        val created = proposal.copy(pullRequest = BuildPullRequest("https://github.com/KRFbrothers/Dua-ai/pull/80", 80, true, "dua/fix-readme-261008-134512"))
        val saved = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat.copy(messages = listOf(chat.messages[0], reply.copy(proposal = created)))), "c")).second.single().messages[1].proposal!!
        assertEquals(created.pullRequest, saved.pullRequest)
        assertTrue(saved.files.all { it.content == null })
        assertEquals(proposal.files.map { it.diff }, saved.files.map { it.diff })
        // Very large proposals keep their diffs but not their contents.
        val large = BuildProposals.fromJson(proposalJson(file("big.txt", "create", content = "x".repeat(BuildProposals.SAVED_CONTENT_CHARS + 1))))!!
        val reloaded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat.copy(messages = listOf(chat.messages[0], reply.copy(proposal = large)))), "c")).second.single().messages[1].proposal!!
        assertFalse(reloaded.complete)
    }

    @Test fun reviewCardStagesAndConfirmationText() {
        val proposal = BuildProposals.fromJson(proposalJson())!!
        assertEquals(ProposalStage.Ready, proposalStage(proposal, null, ""))
        assertEquals(ProposalStage.Unavailable, proposalStage(proposal, null, "Pull requests are off."))
        assertEquals(ProposalStage.Creating, proposalStage(proposal, BuildPrUiState(creating = true), ""))
        assertEquals(ProposalStage.Failed, proposalStage(proposal, BuildPrUiState(error = "changed on main"), ""))
        assertEquals(ProposalStage.Incomplete, proposalStage(proposal.copy(files = proposal.files.map { it.copy(content = null) }), null, ""))
        assertEquals(ProposalStage.Created, proposalStage(proposal.copy(pullRequest = BuildPullRequest("u", 1, true)), BuildPrUiState(error = "old"), ""))
        val text = confirmationText(proposal, draft = true)
        assertTrue(text.contains("dua/fix-readme-261008-134512")); assertTrue(text.contains("KRFbrothers/Dua-ai")); assertTrue(text.contains("draft pull request"))
        assertTrue(text.contains("Nothing is merged"))
        assertFalse(confirmationText(proposal, draft = false).contains("draft"))
    }

    @Test fun configReportsWhetherPullRequestsAreAvailable() {
        val on = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"readOnly\":true,\"providers\":[\"Dua\"],\"pr\":{\"enabled\":true,\"approval\":\"owner\",\"draft\":false}}}"), IntegrationState())
        assertTrue(on.build!!.pr!!.enabled); assertFalse(on.build!!.pr!!.draft)
        val off = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"readOnly\":true,\"providers\":[\"Dua\"],\"pr\":{\"enabled\":false,\"reason\":\"Set DUA_BUILD_PR=1.\"}}}"), IntegrationState())
        assertEquals("Set DUA_BUILD_PR=1.", off.build!!.pr!!.reason); assertFalse(off.build!!.pr!!.enabled)
        val noApproval = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"readOnly\":true,\"providers\":[\"Dua\"],\"pr\":{\"enabled\":true}}}"), IntegrationState())
        assertFalse("the app only offers Create PR when the server requires owner approval", noApproval.build!!.pr!!.enabled)
        assertNull(IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"readOnly\":true,\"providers\":[\"Dua\"]}}"), IntegrationState()).build!!.pr)
        assertTrue(BuildProposals.safePath("web/src/core/build-pr.mjs"))
        listOf(".github/workflows/a.yml", ".GITHUB/WORKFLOWS/a.yml", "a/.git/x", ".gitmodules", "a//b", " a", "a\\b").forEach { assertFalse(it, BuildProposals.safePath(it)) }
    }
}
