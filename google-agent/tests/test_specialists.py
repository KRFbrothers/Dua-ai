import asyncio
import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.tools import FunctionTool
from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from dua_coder import specialists as s
from dua_coder.budget import KEY as BUDGET_KEY

SHA = "a" * 40

def task(role="android", paths=None):
    return {"role": role, "task": "Improve bounded source", "paths": paths or ["app/src/main/A.kt"], "independent": True}

def report(path="app/src/main/A.kt"):
    return {"status": "proposed", "summary": "Proposed edit; tests not run", "changes": [{"path": path, "content": "new"}], "suggested_tests": []}

class BoundaryTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.ctx = SimpleNamespace(state={}, invocation_id="turn")
        self.info = patch.object(s.github, "repository_info", return_value={"main_sha": SHA}).start()
        self.read = patch.object(s.github, "read_file", return_value={"content": "old"}).start()
        self.child = AsyncMock(return_value=report())
        self.childpatch = patch.object(s.SPECIALIST_TOOLS["android"], "run_async", self.child).start()
        self.addCleanup(patch.stopall)

    async def test_scope_rejected_before_any_read(self):
        for tasks in ([task(paths=["web/api/a.js"])], [task(paths=["app/src/main/../A.kt"])],
                      [task(), task()], [dict(task(), independent=False), task("web", ["web/api/a.js"])],
                      [dict(task(), extra="client context")]):
            with self.assertRaises(ValueError):
                await s.delegate_tasks(SHA, tasks, self.ctx)
        self.read.assert_not_called()
        self.child.assert_not_called()

    async def test_output_cannot_escape_assignment(self):
        self.child.return_value = report("app/src/main/B.kt")
        result = await s.delegate_tasks(SHA, [task()], self.ctx)
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(result["results"][0]["changes"], [])

    async def test_attempt_cap_and_invocation_reset(self):
        for _ in range(2):
            self.assertEqual((await s.delegate_tasks(SHA, [task()], self.ctx))["status"], "delegated")
        self.assertEqual((await s.delegate_tasks(SHA, [task()], self.ctx))["status"], "stopped")
        self.assertEqual(self.child.await_count, 2)
        self.ctx.invocation_id = "next"
        self.assertEqual((await s.delegate_tasks(SHA, [task()], self.ctx))["status"], "delegated")

    async def test_secret_input_and_stale_base_block_child(self):
        self.read.return_value = {"content": "github_pat_abcd"}
        with self.assertRaises(ValueError): await s.delegate_tasks(SHA, [task()], self.ctx)
        self.info.return_value = {"main_sha": "b" * 40}
        with self.assertRaises(ValueError): await s.delegate_tasks(SHA, [task()], self.ctx)
        self.child.assert_not_called()

    async def test_oversized_context_and_output_secrets(self):
        self.read.return_value = {"content": "x" * 60001}
        with self.assertRaises(ValueError): await s.delegate_tasks(SHA, [task()], self.ctx)
        self.read.return_value = {"content": "old"}
        self.child.return_value = report()
        self.child.return_value["changes"][0]["content"] = "ghp_abcd"
        result = await s.delegate_tasks(SHA, [task()], self.ctx)
        self.assertEqual(result["status"], "incomplete")

class WorkerModel(BaseLlm):
    calls: int = 0
    barrier: object = None
    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        if self.barrier is not None:
            self.barrier["started"].add(self.model)
            if len(self.barrier["started"]) == 2:
                self.barrier["ready"].set()
            await asyncio.wait_for(self.barrier["ready"].wait(), timeout=5)
        payload = json.loads(llm_request.contents[-1].parts[0].text)
        yield LlmResponse(content=types.Content(role="model", parts=[types.Part.from_text(text=json.dumps(report(payload["assigned_paths"][0])))]), usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=10))

class CoordinatorModel(BaseLlm):
    calls: int = 0
    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        if self.calls == 1:
            part = types.Part(function_call=types.FunctionCall(name="delegate_tasks", args={"base_sha": SHA, "tasks": [task(), task("web", ["web/api/a.js"])]}))
        else:
            part = types.Part.from_text(text="Delegation finished; proposals unreviewed.")
        yield LlmResponse(content=types.Content(role="model", parts=[part]))

class ActualADKTests(unittest.IsolatedAsyncioTestCase):
    async def test_two_tool_free_children_share_budget_and_keep_separate_results(self):
        from dua_coder.budget import before_model, after_model, before_tool
        coordinator = Agent(name="test_coordinator", model=CoordinatorModel(model="fake"), tools=[FunctionTool(s.delegate_tasks)], before_model_callback=before_model, after_model_callback=after_model, before_tool_callback=before_tool)
        sessions = InMemorySessionService()
        session = await sessions.create_session(app_name="specialists-test", user_id="owner")
        barrier = {"started": set(), "ready": asyncio.Event()}
        android, web = WorkerModel(model="fake-android", barrier=barrier), WorkerModel(model="fake-web", barrier=barrier)
        with patch.object(s.SPECIALISTS["android"], "model", android), patch.object(s.SPECIALISTS["web"], "model", web), patch.object(s.github, "repository_info", return_value={"main_sha": SHA}), patch.object(s.github, "read_file", return_value={"content": "old"}):
            runner = Runner(app_name="specialists-test", agent=coordinator, session_service=sessions)
            try:
                events = [e async for e in runner.run_async(user_id="owner", session_id=session.id, new_message=types.Content(role="user", parts=[types.Part.from_text(text="Delegate two independent tasks")]))]
            finally: await runner.close()
        saved = await sessions.get_session(app_name="specialists-test", user_id="owner", session_id=session.id)
        self.assertEqual((android.calls, web.calls), (1, 1))
        self.assertEqual(barrier["started"], {"fake-android", "fake-web"})
        self.assertEqual(saved.state[BUDGET_KEY]["model_calls"], 4)
        self.assertEqual(saved.state[BUDGET_KEY]["tokens"], 20)
        responses = [part.function_response.response for e in events for part in (e.content.parts if e.content else []) if part.function_response]
        result = next(r for r in responses if "results" in r)
        self.assertEqual([r["role"] for r in result["results"]], ["android", "web"])
        self.assertEqual(result["validation_status"], "not_run")
