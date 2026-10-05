"""Run real ADK coordinator/reviewer orchestration with fake models and GitHub."""
import json
import unittest
from unittest.mock import patch
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from dua_coder.agent import root_agent
from dua_coder.reviewer import reviewer
from dua_coder import proposals as p
from dua_coder.budget import KEY as BUDGET_KEY


class CoordinatorModel(BaseLlm):
    turn: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.turn += 1
        if self.turn == 1:
            part = types.Part(function_call=types.FunctionCall(name="prepare_proposal", args={
                "base_sha": "a" * 40, "title": "Update README", "description": "One source change",
                "changes": [{"path": "README.md", "content": "new\n"}]}))
        elif self.turn == 2:
            results = [part.function_response.response for content in llm_request.contents
                       for part in content.parts or [] if part.function_response
                       and part.function_response.name == "prepare_proposal"]
            part = types.Part(function_call=types.FunctionCall(name="review_proposal", args={
                "proposal_id": results[-1]["proposal_id"]}))
        else:
            part = types.Part.from_text(text="Independent review complete. No write performed.")
        yield LlmResponse(content=types.Content(role="model", parts=[part]),
                          usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=10))


class ReviewerModel(BaseLlm):
    calls: int = 0

    async def generate_content_async(self, llm_request, stream=False):
        self.calls += 1
        report = {"verdict": "ready", "summary": "Reviewed exact original and proposed text",
                  "findings": [], "suggested_tests": ["Check Markdown diff"]}
        yield LlmResponse(content=types.Content(role="model", parts=[types.Part.from_text(text=json.dumps(report))]),
                          usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=20))


class OrchestrationTests(unittest.IsolatedAsyncioTestCase):
    async def test_independent_reviewer_runs_and_shared_usage_reaches_parent_state(self):
        sessions = InMemorySessionService()
        session = await sessions.create_session(app_name="dua-test", user_id="owner")
        coordinator_model = CoordinatorModel(model="fake-coordinator")
        review_model = ReviewerModel(model="fake-reviewer")
        with patch.object(root_agent, "model", coordinator_model), patch.object(reviewer, "model", review_model), \
             patch.object(p.github, "repository_info", return_value={"main_sha": "a" * 40}), \
             patch.object(p.github, "api", return_value={"tree": [
                 {"path": "README.md", "type": "blob", "mode": "100644"}]}), \
             patch.object(p.github, "read_file", return_value={"content": "old\n"}), \
             patch.object(p.github, "create_draft_pr") as write:
            runner = Runner(app_name="dua-test", agent=root_agent, session_service=sessions)
            try:
                events = [event async for event in runner.run_async(user_id="owner", session_id=session.id,
                    new_message=types.Content(role="user", parts=[types.Part.from_text(text="Prepare and review a change only.")]))]
            finally:
                await runner.close()
            write.assert_not_called()
        saved = await sessions.get_session(app_name="dua-test", user_id="owner", session_id=session.id)
        self.assertEqual(review_model.calls, 1)
        proposals = list(saved.state[p.KEY].values())
        self.assertEqual(proposals[0]["status"], "reviewed")
        self.assertEqual(saved.state[BUDGET_KEY]["model_calls"], 4)
        self.assertEqual(saved.state[BUDGET_KEY]["tokens"], 50)
        self.assertTrue(events[-1].is_final_response())


if __name__ == "__main__": unittest.main()
