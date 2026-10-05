"""Exercise all five roles through the real Coordinator with fake model/API calls."""
import json
import unittest
from unittest.mock import patch
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from dua_coder.agent import root_agent
from dua_coder import specialists, proposals, validation
from dua_coder.reviewer import reviewer
from dua_coder.budget import KEY as BUDGET_KEY

SHA = "a" * 40
PATHS = ["app/src/main/A.kt", "web/api/a.js"]


class RoleModel(BaseLlm):
    role: str
    calls: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        if self.role in ("android", "web"):
            path = PATHS[0 if self.role == "android" else 1]
            report = {"status": "proposed", "summary": "Proposed scoped change",
                      "changes": [{"path": path, "content": "new\n"}], "suggested_tests": []}
        elif self.role == "reviewer":
            report = {"verdict": "ready", "summary": "Integrated diff checked",
                      "findings": [], "suggested_tests": []}
        else:
            report = {"suggested_tests": ["Review exact Project checks run"]}
        yield LlmResponse(content=types.Content(role="model", parts=[
            types.Part.from_text(text=json.dumps(report))]),
            usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=10))


class FlowModel(BaseLlm):
    calls: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        def result(name):
            return [p.function_response.response for c in llm_request.contents for p in c.parts or []
                    if p.function_response and p.function_response.name == name][-1]
        if self.calls == 1:
            name, args = "delegate_tasks", {"base_sha": SHA, "tasks": [
                {"role": role, "task": "Small independent source improvement", "paths": [path], "independent": True}
                for role, path in zip(("android", "web"), PATHS)]}
        elif self.calls == 2:
            changes = [c for r in result("delegate_tasks")["results"] for c in r["changes"]]
            name, args = "prepare_proposal", {"base_sha": SHA, "title": "Integrated proposal",
                                             "description": "Two independent changes", "changes": changes}
        elif self.calls == 3:
            name, args = "review_proposal", {"proposal_id": result("prepare_proposal")["proposal_id"]}
        elif self.calls == 4:
            name, args = "validate_commit", {"branch": "dua-agent/existing", "head_sha": "b" * 40}
        elif self.calls == 5:
            name, args = "task_status", {}
        else:
            yield LlmResponse(content=types.Content(role="model", parts=[
                types.Part.from_text(text="Proposal reviewed; no write or test dispatch.")]))
            return
        yield LlmResponse(content=types.Content(role="model", parts=[types.Part(
            function_call=types.FunctionCall(name=name, args=args))]))


class IntegratedFlowTests(unittest.IsolatedAsyncioTestCase):
    async def test_all_roles_integrate_without_bypassing_review_or_writing(self):
        models = {role: RoleModel(model="fake-" + role, role=role)
                  for role in ("android", "web", "reviewer", "validation")}
        sessions = InMemorySessionService()
        session = await sessions.create_session(app_name="five-roles", user_id="owner")
        with patch.object(root_agent, "model", FlowModel(model="fake-coordinator")), \
             patch.object(specialists.SPECIALISTS["android"], "model", models["android"]), \
             patch.object(specialists.SPECIALISTS["web"], "model", models["web"]), \
             patch.object(reviewer, "model", models["reviewer"]), \
             patch.object(validation.validator, "model", models["validation"]), \
             patch.object(proposals.github, "repository_info", return_value={"main_sha": SHA}), \
             patch.object(proposals.github, "read_file", return_value={"content": "old\n"}), \
             patch.object(proposals.github, "api", return_value={"tree": [
                 {"path": path, "type": "blob", "mode": "100644"} for path in PATHS]}), \
             patch.object(validation.github, "check_results", return_value={"runs": [
                 {"id": 3, "status": "completed", "conclusion": "success", "url": "check-url"}]}), \
             patch.object(proposals.github, "create_draft_pr") as write, \
             patch.object(proposals.github, "run_checks") as dispatch:
            runner = Runner(app_name="five-roles", agent=root_agent, session_service=sessions)
            try:
                events = [e async for e in runner.run_async(user_id="owner", session_id=session.id,
                    new_message=types.Content(role="user", parts=[types.Part.from_text(text="Prepare and review only")]))]
            finally:
                await runner.close()
            write.assert_not_called()
            dispatch.assert_not_called()
        saved = await sessions.get_session(app_name="five-roles", user_id="owner", session_id=session.id)
        proposal = next(iter(saved.state[proposals.KEY].values()))
        self.assertEqual(proposal["status"], "reviewed")
        self.assertEqual([c["path"] for c in proposal["changes"]], PATHS)
        self.assertTrue(all(m.calls == 1 for m in models.values()))
        self.assertEqual(saved.state[BUDGET_KEY]["model_calls"], 10)
        self.assertEqual(saved.state[BUDGET_KEY]["tokens"], 40)
        statuses = [p.function_response.response for e in events for p in (e.content.parts if e.content else [])
                    if p.function_response and p.function_response.name == "task_status"]
        self.assertEqual(statuses[0]["checks"]["status"], "not_verified")
        self.assertEqual(statuses[0]["workers"]["status"], "delegated")
