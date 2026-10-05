import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from google.adk.agents import Agent
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from dua_coder import validation as v
from dua_coder.budget import KEY

SHA = "a" * 40
BRANCH = "dua-agent/validation-test"


def context():
    return SimpleNamespace(state={}, invocation_id="first", actions=SimpleNamespace(skip_summarization=False))


def run(run_id=1, status="completed", conclusion="success"):
    return {"id": run_id, "status": status, "conclusion": conclusion, "url": "https://github.com/check"}


class ValidationTests(unittest.IsolatedAsyncioTestCase):
    async def test_latest_failure_or_pending_overrides_cached_old_success(self):
        for newest, expected in [(run(2, conclusion="failure"), "failed"),
                                 (run(2, status="in_progress", conclusion=None), "pending")]:
            with patch.object(v.github, "check_results", return_value={"runs": [run(), newest], "passed": True}), \
                 patch.object(v.VALIDATION_TOOL, "run_async", new=AsyncMock(return_value={"suggested_tests": []})):
                result = await v.validate_commit(BRANCH, SHA, context())
            self.assertEqual(result["status"], expected)
            self.assertFalse(result["passed"])
            self.assertEqual(result["latest"]["id"], 2)

    async def test_missing_or_skipped_is_not_success(self):
        for runs in [[], [run(conclusion="skipped")], [run(conclusion="neutral")]]:
            with patch.object(v.github, "check_results", return_value={"runs": runs, "passed": True}), \
                 patch.object(v.VALIDATION_TOOL, "run_async", new=AsyncMock(return_value={"suggested_tests": []})):
                result = await v.validate_commit(BRANCH, SHA, context())
            self.assertEqual(result["status"], "missing")
            self.assertFalse(result["passed"])

    async def test_model_cannot_change_verdict_and_failure_is_sanitized(self):
        with patch.object(v.github, "check_results", return_value={"runs": []}) as checks, \
             patch.object(v.VALIDATION_TOOL, "run_async", new=AsyncMock(return_value={"passed": True, "status": "passed"})):
            result = await v.validate_commit(BRANCH, SHA, context())
        checks.assert_called_once_with(BRANCH, SHA)
        self.assertEqual(result["status"], "missing")
        self.assertIsNone(result["report"])
        with patch.object(v.github, "check_results", side_effect=RuntimeError("secret token")):
            result = await v.validate_commit(BRANCH, SHA, context())
        self.assertEqual(result["status"], "unavailable")
        self.assertNotIn("secret token", str(result))

    async def test_attempts_bounded_and_reset_next_invocation(self):
        ctx = context()
        with patch.object(v.github, "check_results", return_value={"runs": []}) as checks, \
             patch.object(v.VALIDATION_TOOL, "run_async", new=AsyncMock(side_effect=TimeoutError("private error"))):
            for _ in range(2):
                self.assertEqual((await v.validate_commit(BRANCH, SHA, ctx))["status"], "missing")
            self.assertEqual((await v.validate_commit(BRANCH, SHA, ctx))["status"], "unavailable")
            self.assertEqual(checks.call_count, 2)
            ctx.invocation_id = "second"
            await v.validate_commit(BRANCH, SHA, ctx)
            self.assertEqual(checks.call_count, 3)

    async def test_invalid_branch_or_sha_rejected_before_network(self):
        with patch.object(v.github, "check_results") as checks:
            with self.assertRaises(ValueError): await v.validate_commit("main", SHA, context())
            with self.assertRaises(ValueError): await v.validate_commit(BRANCH, "bad", context())
            checks.assert_not_called()


class ParentModel(BaseLlm):
    calls: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        part = (types.Part(function_call=types.FunctionCall(name="validate_commit", args={"branch": BRANCH, "head_sha": SHA}))
                if self.calls == 1 else types.Part.from_text(text="Validation evidence read."))
        yield LlmResponse(content=types.Content(role="model", parts=[part]),
                          usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=10))


class ChildModel(BaseLlm):
    calls: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        yield LlmResponse(content=types.Content(role="model", parts=[types.Part.from_text(
            text=json.dumps({"suggested_tests": ["Review Project checks job details"]}))]),
            usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=20))


class ValidationOrchestrationTests(unittest.IsolatedAsyncioTestCase):
    async def test_actual_child_agent_runs_with_shared_budget_without_mutations(self):
        sessions = InMemorySessionService()
        session = await sessions.create_session(app_name="validation-test", user_id="owner")
        parent = Agent(name="validation_parent", model=ParentModel(model="fake"), tools=[v.validate_commit],
                       before_model_callback=v.before_model, after_model_callback=v.after_model,
                       before_tool_callback=v.before_tool)
        child = ChildModel(model="fake-child")
        with patch.object(v.validator, "model", child), \
             patch.object(v.github, "check_results", return_value={"runs": [run()]}) as checks, \
             patch.object(v.github, "run_checks") as dispatch, patch.object(v.github, "create_draft_pr") as write:
            runner = Runner(app_name="validation-test", agent=parent, session_service=sessions)
            try:
                events = [event async for event in runner.run_async(user_id="owner", session_id=session.id,
                    new_message=types.Content(role="user", parts=[types.Part.from_text(text="Read checks only")]))]
            finally:
                await runner.close()
        self.assertEqual(child.calls, 1)
        dispatch.assert_not_called()
        write.assert_not_called()
        checks.assert_called_once_with(BRANCH, SHA)
        saved = await sessions.get_session(app_name="validation-test", user_id="owner", session_id=session.id)
        self.assertEqual(saved.state[KEY]["model_calls"], 3)
        self.assertEqual(saved.state[KEY]["tokens"], 40)
        self.assertEqual(saved.state[v.RESULT_KEY]["result"]["status"], "passed")
        self.assertEqual(saved.state[v.RESULT_KEY]["result"]["head_sha"], SHA)
        responses = [part.function_response.response for event in events for part in event.content.parts
                     if part.function_response and part.function_response.name == "validate_commit"]
        self.assertEqual(responses[-1]["status"], "passed")
        self.assertIn(SHA[:12], responses[-1]["phone_status"])


if __name__ == "__main__": unittest.main()
