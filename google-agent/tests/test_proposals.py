import asyncio
import copy
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch
from google.adk.tools import FunctionTool
from google.adk.tools.tool_confirmation import ToolConfirmation
from dua_coder import proposals as p

BASE = "a" * 40


def context():
    return SimpleNamespace(state={}, invocation_id="test-invocation", tool_confirmation=None,
                           actions=SimpleNamespace(skip_summarization=False))


def prepare(ctx, content="new\n"):
    with patch.object(p.github, "repository_info", return_value={"main_sha": BASE}), \
         patch.object(p.github, "api", return_value={"tree": [
             {"path": "README.md", "type": "blob", "mode": "100644"}]}), \
         patch.object(p.github, "read_file", return_value={"content": "old\n"}):
        return p.prepare_proposal(BASE, "Update README", "One change", [{"path": "README.md", "content": content}], ctx)


def review(ctx, proposal_id, result=None):
    result = result or {"verdict": "ready", "summary": "Small valid documentation change",
                        "findings": [], "suggested_tests": []}
    with patch.object(p.REVIEW_TOOL, "run_async", new=AsyncMock(return_value=result)):
        return asyncio.run(p.review_proposal(proposal_id, ctx))


class ProposalTests(unittest.TestCase):
    def test_prepare_returns_diff_without_remote_write(self):
        ctx = context()
        result = prepare(ctx)
        self.assertIn("-old", result["diff"])
        self.assertIn("+new", result["diff"])
        self.assertEqual(len(result["proposal_id"]), 64)
        self.assertEqual(ctx.state[p.KEY][result["proposal_id"]]["status"], "prepared")

    def test_preview_marks_newline_only_change(self):
        ctx = context()
        result = prepare(ctx, "old")
        self.assertIn("before=True, after=False", result["diff"])

    def test_unreviewed_or_text_approved_patch_cannot_write(self):
        ctx = context()
        prepared = prepare(ctx)
        with patch.object(p.github, "create_draft_pr") as write:
            with self.assertRaises(ValueError):
                p.publish_proposal(prepared["proposal_id"], prepared["diff"], ctx)
            review(ctx, prepared["proposal_id"])
            with self.assertRaises(ValueError):
                p.publish_proposal(prepared["proposal_id"], prepared["diff"], ctx)
            write.assert_not_called()

    def test_diff_or_saved_content_tampering_blocks_write(self):
        ctx = context()
        prepared = prepare(ctx)
        review(ctx, prepared["proposal_id"])
        ctx.tool_confirmation = ToolConfirmation(confirmed=True)
        with patch.object(p.github, "create_draft_pr") as write:
            with self.assertRaises(ValueError):
                p.publish_proposal(prepared["proposal_id"], "different diff", ctx)
            ctx.state[p.KEY][prepared["proposal_id"]]["changes"][0]["content"] = "tampered"
            with self.assertRaises(ValueError):
                p.publish_proposal(prepared["proposal_id"], prepared["diff"], ctx)
            write.assert_not_called()

    def test_blocking_finding_overrides_ready_and_prevents_write(self):
        ctx = context()
        prepared = prepare(ctx)
        report = {"verdict": "ready", "summary": "problem", "suggested_tests": [],
                  "findings": [{"path": "README.md", "severity": "blocking", "explanation": "wrong claim"}]}
        result = review(ctx, prepared["proposal_id"], report)
        self.assertEqual(result["status"], "changes_requested")
        ctx.tool_confirmation = ToolConfirmation(confirmed=True)
        with self.assertRaises(ValueError): p.publish_proposal(prepared["proposal_id"], prepared["diff"], ctx)

    def test_reviewer_failure_never_allows_write_and_retry_is_bounded(self):
        ctx = context()
        prepared = prepare(ctx)
        mock = AsyncMock(side_effect=RuntimeError("sensitive remote error"))
        with patch.object(p.REVIEW_TOOL, "run_async", new=mock):
            for _ in range(3):
                result = asyncio.run(p.review_proposal(prepared["proposal_id"], ctx))
                self.assertEqual(result["status"], "review_failed")
        self.assertEqual(mock.call_count, 2)
        self.assertNotIn("sensitive", str(result))

    def test_review_schema_failure_fails_closed(self):
        ctx = context()
        prepared = prepare(ctx)
        self.assertEqual(review(ctx, prepared["proposal_id"], {"verdict": "invented"})["status"], "review_failed")

    def test_real_framework_reject_then_confirm_publishes_stored_patch_once(self):
        ctx = context()
        prepared = prepare(ctx)
        review(ctx, prepared["proposal_id"])
        ctx.request_confirmation = lambda **kwargs: None
        tool = FunctionTool(p.publish_proposal, require_confirmation=True)
        args = {"proposal_id": prepared["proposal_id"], "reviewed_diff": prepared["diff"]}
        with patch.object(p.github, "create_draft_pr", return_value={"pr_number": 42, "url": "pr"}) as write:
            asyncio.run(tool.run_async(args=args, tool_context=ctx))
            write.assert_not_called()
            ctx.tool_confirmation = ToolConfirmation(confirmed=False)
            asyncio.run(tool.run_async(args=args, tool_context=ctx))
            write.assert_not_called()
            ctx.tool_confirmation = ToolConfirmation(confirmed=True)
            result = asyncio.run(tool.run_async(args=args, tool_context=ctx))
            self.assertEqual(result["status"], "published")
            self.assertEqual(write.call_args.args[3], [{"path": "README.md", "content": "new\n"}])
            self.assertTrue(write.call_args.kwargs["branch"].startswith("dua-agent/"))
            asyncio.run(tool.run_async(args=args, tool_context=ctx))
            self.assertEqual(write.call_count, 1)

    def test_unknown_write_is_not_automatically_retried(self):
        ctx = context()
        prepared = prepare(ctx)
        review(ctx, prepared["proposal_id"])
        ctx.tool_confirmation = ToolConfirmation(confirmed=True)
        with patch.object(p.github, "create_draft_pr", side_effect=p.github.GitHubError("timeout")) as write:
            for _ in range(2):
                result = p.publish_proposal(prepared["proposal_id"], prepared["diff"], ctx)
                self.assertEqual(result["status"], "write_unknown")
            self.assertEqual(write.call_count, 1)

    def test_expired_proposal_rejected(self):
        ctx = context()
        prepared = prepare(ctx)
        ctx.state[p.KEY][prepared["proposal_id"]]["created"] = 0
        with self.assertRaises(ValueError): p.get_proposal(ctx, prepared["proposal_id"])

    def test_session_cannot_publish_another_sessions_patch(self):
        prepared = prepare(context())
        with self.assertRaises(ValueError): p.get_proposal(context(), prepared["proposal_id"])

    def test_mutating_saved_review_context_invalidates_proposal_hash(self):
        ctx = context()
        result = prepare(ctx)
        ctx.state[p.KEY][result["proposal_id"]]["originals"]["README.md"] = "different source\n"
        with self.assertRaises(ValueError): p.get_proposal(ctx, result["proposal_id"])


if __name__ == "__main__": unittest.main()
