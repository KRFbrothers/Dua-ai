import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from google.genai import types
from google.adk.models.llm_response import LlmResponse
from dua_coder import budget as b
from dua_coder import github_tools as g

spec = importlib.util.spec_from_file_location("launcher", Path(__file__).parents[1] / "start_agent.py")
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class BudgetTests(unittest.TestCase):
    def test_model_budget_stops_scheduling(self):
        ctx = SimpleNamespace(state={}, invocation_id="root")
        for _ in range(15): self.assertIsNone(b.before_model(ctx, None))
        self.assertIsNotNone(b.before_model(ctx, None))
        self.assertEqual(ctx.state[b.KEY]["model_calls"], 15)

    def test_reviewer_shares_parent_budget_even_with_child_invocation(self):
        root = SimpleNamespace(state={}, invocation_id="root")
        child = SimpleNamespace(state={}, invocation_id="child")
        b.before_model(root, None)
        with b.shared_budget(root):
            b.before_model(child, None)
            b.after_model(child, LlmResponse(usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=5)))
        self.assertEqual(root.state[b.KEY]["model_calls"], 2)
        self.assertEqual(root.state[b.KEY]["tokens"], 5)

    def test_tool_budget_and_deadline_stop_writes(self):
        ctx = SimpleNamespace(state={}, invocation_id="root")
        for _ in range(40): self.assertIsNone(b.before_tool(None, {}, ctx))
        self.assertEqual(b.before_tool(None, {}, ctx)["status"], "stopped")
        next_ctx = SimpleNamespace(state={}, invocation_id="later")
        current = b.get_budget(next_ctx)
        current["started"] = 0
        next_ctx.state[b.KEY] = current
        self.assertEqual(b.before_tool(None, {}, next_ctx)["status"], "stopped")

    def test_token_limit_stops_next_model_and_tool_call(self):
        ctx = SimpleNamespace(state={}, invocation_id="root")
        b.before_model(ctx, None)
        b.after_model(ctx, LlmResponse(usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=60001)))
        self.assertIsNotNone(b.before_model(ctx, None))
        self.assertEqual(b.before_tool(None, {}, ctx)["status"], "stopped")

    def test_latest_failing_run_beats_older_success(self):
        runs = [{"id": 1, "head_sha": "a" * 40, "status": "completed", "conclusion": "success", "html_url": "old"},
                {"id": 2, "head_sha": "a" * 40, "status": "completed", "conclusion": "failure", "html_url": "new"}]
        with patch.object(g, "api", return_value={"workflow_runs": runs}):
            result = g.check_results("dua-agent/test", "a" * 40)
        self.assertFalse(result["passed"])
        self.assertEqual(result["latest"]["id"], 2)


class LauncherTests(unittest.TestCase):
    def test_only_exact_preview_origin_is_accepted(self):
        self.assertEqual(launcher.origin("https://8081-example.cloudshell.dev"), "https://8081-example.cloudshell.dev")
        for url in ("https://evil.example", "https://example.cloudshell.dev/dev-ui/", "https://*.cloudshell.dev",
                    "https://user:password@example.cloudshell.dev", "https://example.cloudshell.dev?token=x"):
            with self.assertRaises(Exception): launcher.origin(url)

    def test_saved_token_overrides_stale_export_without_printing(self):
        with TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / "dua_coder").mkdir()
            (directory / "dua_coder/.env").write_text("DUA_GITHUB_TOKEN=saved-value\n")
            with patch.dict("os.environ", {"DUA_GITHUB_TOKEN": "stale"}):
                launcher.load_settings(directory)
                import os
                self.assertEqual(os.environ["DUA_GITHUB_TOKEN"], "saved-value")

    def test_google_refresh_failure_returns_safe_recovery_instruction(self):
        with patch.dict("os.environ", {"DUA_GITHUB_TOKEN": "test", "GOOGLE_CLOUD_PROJECT": "test"}):
            with patch("google.auth.default", side_effect=RuntimeError("secret-token-in-error")):
                error = launcher.preflight()
        self.assertIn("application-default login", error)
        self.assertNotIn("secret-token", error)


if __name__ == "__main__": unittest.main()
