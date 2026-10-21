"""Optional ADK reviewer step (DUA_AGENT_REVIEW). Mocked, no network: a fake ADK always runs; the real-SDK
test runs where google-adk is installed (CI engine job) with a scripted offline model."""
import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_adk  # noqa: E402
from test_agent_adk import PLAN, FakeAgent, cfg, make_env  # noqa: E402

SCRIPTS = {"research": [("google_search", {"query": "gemini news"}), "Gemini 4 launched [1]"],
           "notes": [("search_notes", {"query": "preference"}), "User likes short Hinglish answers"]}


def fake_adk(review_text, revised_text="REVISED [1]"):
    seen = {"model_calls": {}, "max_calls": None, "stages": None}

    class Runner:
        def __init__(self, agent, app_name):
            self.agent = agent
            self.session_service = SimpleNamespace(create_session=self.create_session)

        async def create_session(self, **kwargs):
            return SimpleNamespace(id="s")

        async def close(self):
            pass

        async def run_async(self, user_id, session_id, new_message, run_config):
            agent = self.agent
            if agent.name == "controller":
                yield SimpleNamespace(author="controller", content=SimpleNamespace(parts=[SimpleNamespace(text=json.dumps(PLAN))]))
                return
            seen["max_calls"], seen["stages"] = run_config["max_llm_calls"], [a.name for a in agent.sub_agents]
            state = {}
            parallel, merger, *rest = agent.sub_agents
            for sub in parallel.sub_agents:
                kind = sub.name.replace("_agent", "")
                tools = {t.__name__: t for t in sub.tools}
                for name, args in SCRIPTS[kind][:-1]:
                    await tools[name](**args)
                state[sub.output_key] = SCRIPTS[kind][-1]
            ctx = SimpleNamespace(state=state)
            merger.instruction(ctx)
            state["dua_answer"] = "MERGED [1]"
            yield SimpleNamespace(author="merger", content=SimpleNamespace(parts=[SimpleNamespace(text="MERGED [1]")]))
            outputs = {"reviewer": review_text, "reviser": revised_text}
            for stage in rest:
                for _ in range(3):  # a misbehaving model loop: the gate must still allow at most one revision
                    stage.instruction(ctx)
                    gate = getattr(stage, "before_model_callback", None)
                    blocked = gate(callback_context=SimpleNamespace(state=state, agent_name=stage.name), llm_request=None) if gate else None
                    if blocked is not None:
                        text = blocked.content["parts"][0]["text"]
                    else:
                        seen["model_calls"][stage.name] = seen["model_calls"].get(stage.name, 0) + 1
                        text = outputs[stage.name]
                    state[stage.output_key] = text
                    yield SimpleNamespace(author=stage.name, content=SimpleNamespace(parts=[SimpleNamespace(text=text)]))
                    if stage.name == "reviewer":
                        break

    adk = SimpleNamespace(LlmResponse=lambda **kwargs: SimpleNamespace(**kwargs),
                          LlmAgent=FakeAgent, ParallelAgent=FakeAgent, SequentialAgent=FakeAgent,
                          RunConfig=lambda **k: k, InMemoryRunner=Runner,
                          types=SimpleNamespace(Content=lambda **k: k, Part=lambda **k: k))
    return adk, seen


def run(monkeypatch, flag, review_text='{"verdict": "ok", "issues": []}', **kw):
    if flag is None:
        monkeypatch.delenv("DUA_AGENT_REVIEW", raising=False)
    else:
        monkeypatch.setenv("DUA_AGENT_REVIEW", flag)
    events = []
    adk, seen = fake_adk(review_text, **kw)
    text, _ = agent_adk.run(make_env(events), "news and my preference", "flash", "argon", cfg, adk=adk,
                            log=lambda m: None)
    return text, seen, events


def test_flag_defaults_off(monkeypatch):
    monkeypatch.delenv("DUA_AGENT_REVIEW", raising=False)
    assert not agent_adk.review_enabled()
    monkeypatch.setenv("DUA_AGENT_REVIEW", "1")
    assert agent_adk.review_enabled()


@pytest.mark.parametrize("flag", [None, "0", "", "true"])
def test_flag_off_pipeline_is_unchanged(monkeypatch, flag):
    text, seen, events = run(monkeypatch, flag, review_text='{"verdict": "needs_fix", "issues": ["x"]}')
    assert text == "MERGED [1]"
    assert seen["stages"] == ["helpers", "merger"]
    assert seen["max_calls"] == agent_adk.SUB_STEPS * 2 + 2
    assert seen["model_calls"] == {}
    assert not any(e[0] == "review" for e in events)


def test_flag_on_ok_runs_one_review_and_no_revision(monkeypatch):
    text, seen, events = run(monkeypatch, "1")
    assert text == "MERGED [1]"
    assert seen["stages"] == ["helpers", "merger", "reviewer", "reviser"]
    assert seen["model_calls"] == {"reviewer": 1}
    assert seen["max_calls"] == agent_adk.SUB_STEPS * 2 + 4
    assert ("review", "Answer reviewed", "done") in events


def test_flag_on_needs_fix_revises_at_most_once(monkeypatch):
    text, seen, _ = run(monkeypatch, "1", review_text='{"verdict": "needs_fix", "issues": ["missing part"]}')
    assert text == "REVISED [1]"
    assert seen["model_calls"] == {"reviewer": 1, "reviser": 1}


@pytest.mark.parametrize("review", ['{"verdict": "needs_approval", "issues": ["pending PR"]}', "not json",
                                    '{"verdict": "needs_fix", "issues": []}', '{"verdict": "maybe"}'])
def test_flag_on_other_verdicts_keep_the_draft(monkeypatch, review):
    text, seen, _ = run(monkeypatch, "1", review_text=review)
    assert text == "MERGED [1]"
    assert "reviser" not in seen["model_calls"]


def test_revision_that_drops_a_pr_link_is_rejected(monkeypatch):
    monkeypatch.setenv("DUA_AGENT_REVIEW", "1")
    adk, _ = fake_adk('{"verdict": "needs_fix", "issues": ["x"]}', revised_text="no link")
    draft = "PR https://github.com/o/r/pull/7"
    orig = adk.InMemoryRunner.run_async

    async def run_async(self, **kw):
        async for e in orig(self, **kw):
            if e.author == "merger":
                e.content.parts[0].text = draft
            yield e
    adk.InMemoryRunner.run_async = run_async
    text, _ = agent_adk.run(make_env([]), "news and my preference", "flash", "argon", cfg, adk=adk, log=lambda m: None)
    assert text == draft


def test_parse_review():
    assert agent_adk.parse_review('```json\n{"verdict": "needs_fix", "issues": ["a"]}\n```') == {
        "verdict": "needs_fix", "issues": ["a"]}
    assert agent_adk.parse_review('{"verdict": "needs_fix"}')["verdict"] == "ok"
    assert agent_adk.parse_review("") is None


@pytest.mark.parametrize("verdict,expected,revise_calls", [("ok", "FINAL [1]", 0), ("needs_fix", "FIXED [1]", 1)])
def test_real_adk_reviewer_offline(monkeypatch, verdict, expected, revise_calls):
    pytest.importorskip("google.adk")
    from google.adk.models import BaseLlm, LlmResponse
    from google.genai import types
    monkeypatch.setenv("DUA_AGENT_REVIEW", "1")
    calls = {"review": 0, "revise": 0}

    class Scripted(BaseLlm):
        async def generate_content_async(self, llm_request, stream=False):
            system = str(llm_request.config.system_instruction or "")
            responded = any(p.function_response for c in llm_request.contents for p in (c.parts or []))
            if "controller of the Dua Agent" in system:
                text = json.dumps(PLAN)
            elif "You review the Dua Agent" in system:
                calls["review"] += 1
                assert "FINAL [1]" in system
                text = json.dumps({"verdict": verdict, "issues": ["missing part"] if verdict == "needs_fix" else []})
            elif "Revise the Dua Agent" in system:
                calls["revise"] += 1
                text = "FIXED [1]"
            elif "Helper results" in system:
                text = "FINAL [1]"
            elif "You research" in system and not responded:
                yield LlmResponse(content=types.Content(role="model", parts=[types.Part(
                    function_call=types.FunctionCall(name="google_search", args={"query": "gemini"}))]))
                return
            elif "private notes" in system and "Your part" in system and not responded:
                yield LlmResponse(content=types.Content(role="model", parts=[types.Part(
                    function_call=types.FunctionCall(name="search_notes", args={"query": "pref"}))]))
                return
            else:
                text = "found [1]"
            yield LlmResponse(content=types.Content(role="model", parts=[types.Part(text=text)]))

    model = Scripted(model="scripted")
    text, _ = agent_adk.run(make_env([]), "news and my preference", model, model,
                            lambda m, t, json_out=False: types.GenerateContentConfig(max_output_tokens=t),
                            log=lambda m: None)
    assert text == expected
    assert calls == {"review": 1, "revise": revise_calls}
