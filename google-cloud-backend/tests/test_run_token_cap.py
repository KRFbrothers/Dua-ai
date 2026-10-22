"""MAX_RUN_TOTAL_TOKENS: cumulative per-run token cap for the old tool loop and the ADK pipeline."""
import json
import os
import sys
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_adk  # noqa: E402
import agent_tools  # noqa: E402
import coding_agent  # noqa: E402


def usage(total=None, prompt=None, out=None):
    return SimpleNamespace(total_token_count=total, prompt_token_count=prompt, candidates_token_count=out)


# ------------------------------------------------------------------ helpers
def test_cap_off_by_default_and_bad_values(monkeypatch):
    monkeypatch.delenv("MAX_RUN_TOTAL_TOKENS", raising=False)
    assert coding_agent.run_token_cap() == 0
    for bad in ("", "abc", "-5"):
        monkeypatch.setenv("MAX_RUN_TOTAL_TOKENS", bad)
        assert coding_agent.run_token_cap() == 0
    budget = coding_agent.RunTokenBudget()
    budget.add(usage(total=10**9))
    assert not budget.enabled and not budget.exceeded


def test_usage_tokens_handles_missing_and_partial_metadata():
    assert coding_agent.usage_tokens(None) == 0
    assert coding_agent.usage_tokens(SimpleNamespace()) == 0
    assert coding_agent.usage_tokens(usage(prompt=7)) == 7
    assert coding_agent.usage_tokens(usage(prompt=7, out=3)) == 10
    assert coding_agent.usage_tokens(usage(total=50, prompt=7, out=3)) == 50
    assert coding_agent.usage_tokens({"prompt_token_count": 4, "candidates_token_count": True}) == 4
    assert coding_agent.usage_tokens({"prompt_token_count": -1, "candidates_token_count": "9"}) == 0


# ------------------------------------------------------------------ old tool loop
class Looping:
    """Always asks for another tool call; reports [tokens] per step (None = no usage metadata)."""

    def __init__(self, tokens):
        self.n, self.tokens = 0, tokens

    def generate_content(self, history, **kw):
        self.n += 1
        call = SimpleNamespace(name="current_time", args={})
        meta = usage(total=self.tokens) if self.tokens is not None else None
        return SimpleNamespace(function_calls=[call], candidates=[SimpleNamespace(content="c")], usage_metadata=meta,
                               text="")


def loop_env(model):
    return {"model": model, "progress": lambda *a: None, "thinking": lambda d: None, "github": None,
            "request_text": "req", "enqueue": lambda *a: True, "chunk_text": lambda r: r.text}


def run_loop(monkeypatch, tokens):
    monkeypatch.setattr(agent_tools.AgentRun, "run_tool_timed", lambda self, name, args: {"time": "now"})
    model = Looping(tokens)
    text, agent = agent_tools.run(loop_env(model), [], ["TOOLS"], lambda results: ("tool", results))
    return model, text, agent


def test_loop_unchanged_when_cap_off(monkeypatch):
    monkeypatch.delenv("MAX_RUN_TOTAL_TOKENS", raising=False)
    model, text, _ = run_loop(monkeypatch, 1000)
    assert model.n == agent_tools.MAX_STEPS
    assert coding_agent.TOKEN_CAP_NOTE not in text


def test_loop_stops_gracefully_when_cap_exceeded(monkeypatch):
    monkeypatch.setenv("MAX_RUN_TOTAL_TOKENS", "2500")
    model, text, agent = run_loop(monkeypatch, 1000)
    assert model.n == 3  # 1000, 2000, 3000 >= 2500 -> stop, no 4th model call
    assert agent.status == "token_cap"
    assert text.endswith(coding_agent.TOKEN_CAP_NOTE)
    assert "Steps completed so far" in text and "current_time" in text
    assert agent.token_budget.used == 3000


def test_loop_missing_usage_counts_zero(monkeypatch):
    monkeypatch.setenv("MAX_RUN_TOTAL_TOKENS", "10")
    model, text, agent = run_loop(monkeypatch, None)
    assert model.n == agent_tools.MAX_STEPS and agent.token_budget.used == 0
    assert coding_agent.TOKEN_CAP_NOTE not in text


# ------------------------------------------------------------------ ADK pipeline (main + sub-agents)
PLAN = json.dumps({"split": True, "subtasks": [{"kind": "research", "task": "a"}, {"kind": "notes", "task": "b"}]})


def fake_adk(tokens_per_call):
    """Calls each LlmAgent's before/after model callbacks the way ADK does; records which agents hit the model."""
    called = []

    def model_call(agent, text, meta):
        ctx = SimpleNamespace(agent_name=agent.name, state={})
        before = getattr(agent, "before_model_callback", None)
        short = before(callback_context=ctx, llm_request=SimpleNamespace(contents=[], config=SimpleNamespace(), model="m")) if before else None
        if short is not None:
            return "".join(p.get("text", "") for p in short.content["parts"])
        called.append(agent.name)
        response = SimpleNamespace(partial=False, usage_metadata=meta,
                                   content={"parts": [{"text": text}]})
        after = getattr(agent, "after_model_callback", None)
        if after:
            after(callback_context=ctx, llm_response=response)
        return text

    meta = usage(total=tokens_per_call) if tokens_per_call is not None else None

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
                text = model_call(agent, PLAN, meta)
                yield SimpleNamespace(author="controller", content=SimpleNamespace(parts=[SimpleNamespace(text=text)]))
                return
            parallel, merger = agent.sub_agents
            state = {}
            for sub in parallel.sub_agents:
                state[sub.output_key] = model_call(sub, "helper done", meta)
            merger.instruction(SimpleNamespace(state=state))
            text = model_call(merger, "MERGED", meta)
            yield SimpleNamespace(author="merger", content=SimpleNamespace(parts=[SimpleNamespace(text=text)]))

    class Agent:
        def __init__(self, **kw):
            self.__dict__.update(kw)

    adk = SimpleNamespace(LlmResponse=lambda **kwargs: SimpleNamespace(**kwargs), LlmAgent=Agent, ParallelAgent=Agent,
                          SequentialAgent=Agent, RunConfig=lambda **k: k, InMemoryRunner=Runner,
                          types=SimpleNamespace(Content=lambda **k: k, Part=lambda **k: k))
    return adk, called


class Notes:
    def search(self, query):
        return []


def adk_env():
    return {"progress": lambda *a, **k: None, "thinking": lambda d: None, "enqueue": lambda *a: True,
            "chunk_text": lambda r: r.text, "request_text": "q", "github": None, "notes": Notes(), "memory": "",
            "search_tool": lambda: "S", "contents_for": lambda t: [t], "grounding_sources": lambda r: [],
            "model": SimpleNamespace(generate_content=lambda *a, **k: SimpleNamespace(text="x"))}


def cfg(model, tokens, json_out=False):
    return {}


def run_adk(monkeypatch, cap, tokens):
    monkeypatch.delenv("DUA_AGENT_REVIEW", raising=False)
    if cap is None:
        monkeypatch.delenv("MAX_RUN_TOTAL_TOKENS", raising=False)
    else:
        monkeypatch.setenv("MAX_RUN_TOTAL_TOKENS", str(cap))
    adk, called = fake_adk(tokens)
    text, result = agent_adk.run(adk_env(), "q", "flash", "main", cfg, adk=adk, log=lambda m: None)
    return text, result, called


def test_adk_unchanged_when_cap_off(monkeypatch):
    text, result, called = run_adk(monkeypatch, None, 10**6)
    assert text == "MERGED" and called == ["controller", "research_agent", "notes_agent", "merger"]


def test_adk_cap_counts_main_and_sub_agents_and_stops(monkeypatch):
    # controller 100 + research helper 100 = 200 >= 150: the notes helper and merger never call the model.
    text, result, called = run_adk(monkeypatch, 150, 100)
    assert called == ["controller", "research_agent"]
    assert result.status == "token_cap"
    assert text.endswith(coding_agent.TOKEN_CAP_NOTE) and text.count(coding_agent.TOKEN_CAP_NOTE) == 1


def test_adk_missing_usage_counts_zero(monkeypatch):
    text, result, called = run_adk(monkeypatch, 1, None)
    assert text == "MERGED" and len(called) == 4 and result.status == "ok"
