import asyncio
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

# Load credential-free core independently of the agent/model import.
spec = importlib.util.spec_from_file_location("github_tools", Path(__file__).parents[1] / "dua_coder/github_tools.py")
tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tools)
BASE = "a" * 40
NEW = "b" * 40


class ToolTests(unittest.TestCase):
    def test_hidden_traversal_credentials_and_workflows_blocked(self):
        for path in ("../README.md", "web/.env", "web//foo.mjs", "app/secret.key", ".github/workflows/checks.yml"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                tools.validate_path(path, write=True)

    def test_bad_secret_reference_does_not_call_cloud(self):
        with patch.dict("os.environ", {"DUA_GITHUB_TOKEN_SECRET": "https://example.com/token"}):
            with self.assertRaises(tools.GitHubError): tools.secret_token()

    def test_missing_github_token_does_not_send_request(self):
        with patch.dict("os.environ", {"DUA_GITHUB_TOKEN": "", "DUA_GITHUB_TOKEN_SECRET": ""}):
            with patch.object(tools, "urlopen") as http:
                with self.assertRaises(tools.GitHubError): tools.api("GET", "/branches/main")
                http.assert_not_called()

    def test_write_scope_excludes_dependency_and_agent_config(self):
        for path in ("web/package.json", "app/build.gradle.kts", "web/vercel.json", "google-agent/dua_coder/agent.py"):
            with self.assertRaises(ValueError):
                tools.validate_path(path, write=True)
        self.assertEqual(tools.validate_path("app/src/main/java/com/dua/ai/ui/Test.kt", True),
                         "app/src/main/java/com/dua/ai/ui/Test.kt")

    def test_credentials_duplicates_and_large_patches_blocked(self):
        for changes in ([{"path": "README.md", "content": "github_pat_abc123"}],
                        [{"path": "README.md", "content": ""}] * 2,
                        [{"path": "README.md", "content": "x" * 100001}], []):
            with self.assertRaises(ValueError):
                tools.validate_changes(changes)

    def test_changed_main_prevents_all_writes(self):
        with patch.object(tools, "api", return_value={"commit": {"sha": NEW}}) as api:
            with self.assertRaises(ValueError):
                tools.create_draft_pr(BASE, "Fix", "Details", [{"path": "README.md", "content": "updated"}])
            self.assertEqual([c.args[0] for c in api.call_args_list], ["GET"])

    def test_read_symlink_rejected_before_contents_request(self):
        tree = {"tree": [{"path": "README.md", "type": "blob", "mode": "120000"}]}
        with patch.object(tools, "api", return_value=tree) as api:
            with self.assertRaises(ValueError):
                tools.read_file("README.md", BASE)
            self.assertEqual(api.call_count, 1)

    def test_atomic_changes_keep_file_mode_and_only_create_new_ref(self):
        calls = []
        def api(method, path, body=None):
            calls.append((method, path, body))
            if path == "/branches/main": return {"commit": {"sha": BASE}}
            if path == f"/git/commits/{BASE}": return {"tree": {"sha": BASE}}
            if path.startswith("/git/trees/"): return {"tree": [{"path": "README.md", "mode": "100755", "type": "blob"}]}
            if path == "/git/trees": return {"sha": NEW}
            if path == "/git/commits": return {"sha": NEW}
            if path == "/git/refs": return {}
            if path == "/pulls": return {"number": 1, "html_url": "https://github.com/pr"}
            raise AssertionError(path)
        with patch.object(tools, "api", side_effect=api):
            result = tools.create_draft_pr(BASE, "Fix", "Details", [{"path": "README.md", "content": "updated"}])
        self.assertTrue(result["branch"].startswith("dua-agent/"))
        tree_body = next(c[2] for c in calls if c[:2] == ("POST", "/git/trees"))
        self.assertEqual(tree_body["tree"][0]["mode"], "100755")
        self.assertFalse(any(c[0] in ("PATCH", "PUT", "DELETE") for c in calls))
        self.assertTrue(next(c[2] for c in calls if c[1] == "/pulls")["draft"])

    def test_pr_failure_returns_existing_branch_instead_of_repeating(self):
        def api(method, path, body=None):
            if path == "/branches/main": return {"commit": {"sha": BASE}}
            if path == f"/git/commits/{BASE}": return {"tree": {"sha": BASE}}
            if path.startswith("/git/trees/"): return {"tree": []}
            if path == "/pulls": raise tools.GitHubError("fail")
            return {"sha": NEW}
        with patch.object(tools, "api", side_effect=api):
            result = tools.create_draft_pr(BASE, "Fix", "Details", [{"path": "README.md", "content": "updated"}])
        self.assertIn("compare_url", result)

    def test_test_dispatch_cannot_target_main(self):
        with patch.object(tools, "api") as api:
            with self.assertRaises(ValueError): tools.run_checks("main")
            api.assert_not_called()

    def test_old_check_success_is_not_current_success(self):
        with patch.object(tools, "api", return_value={"workflow_runs": [
            {"head_sha": BASE, "id": 1, "status": "completed", "conclusion": "success", "html_url": "old"}]}):
            self.assertEqual(tools.check_results("dua-agent/test", NEW)["runs"], [])


class ConfirmationTests(unittest.TestCase):
    def test_adk_write_confirmation_prevents_invocation_until_approved(self):
        from google.adk.tools import FunctionTool
        called = []
        def write_action(value: str) -> dict:
            """A write action."""
            called.append(value)
            return {"value": value}
        class Context:
            tool_confirmation = None
            from types import SimpleNamespace
            actions = SimpleNamespace(skip_summarization=False)
            def request_confirmation(self, **kwargs): self.requested = kwargs
        context = Context()
        tool = FunctionTool(write_action, require_confirmation=True)
        asyncio.run(tool.run_async(args={"value": "review this"}, tool_context=context))
        self.assertEqual(called, [])
        self.assertTrue(context.requested)
        from google.adk.tools.tool_confirmation import ToolConfirmation
        context.tool_confirmation = ToolConfirmation(confirmed=False)
        asyncio.run(tool.run_async(args={"value": "review this"}, tool_context=context))
        self.assertEqual(called, [])
        context.tool_confirmation = ToolConfirmation(confirmed=True)
        asyncio.run(tool.run_async(args={"value": "review this"}, tool_context=context))
        self.assertEqual(called, ["review this"])

    def test_real_agent_tools_generate_schemas_without_model_credentials(self):
        from dua_coder.agent import root_agent
        self.assertEqual(len(root_agent.tools), 8)
        from google.adk.tools import FunctionTool
        for tool in root_agent.tools:
            wrapped = tool if isinstance(tool, FunctionTool) else FunctionTool(tool)
            self.assertIsNotNone(wrapped._get_declaration())
        for tool in root_agent.tools:
            if isinstance(tool, FunctionTool):
                self.assertTrue(tool._require_confirmation)

    def test_coordinator_cannot_call_raw_write_and_reviewer_has_no_tools(self):
        from dua_coder.agent import root_agent
        from dua_coder.reviewer import reviewer
        from google.adk.tools import FunctionTool
        names = [t.name if isinstance(t, FunctionTool) else t.__name__ for t in root_agent.tools]
        self.assertNotIn("create_draft_pr", names)
        self.assertIn("publish_proposal", names)
        self.assertEqual(reviewer.tools, [])


if __name__ == "__main__": unittest.main()
