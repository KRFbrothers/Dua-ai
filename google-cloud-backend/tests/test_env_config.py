import importlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from env_config import env_int  # noqa: E402


def test_env_int_reads_valid_and_falls_back(monkeypatch, capsys):
    monkeypatch.delenv("DUA_TEST_INT", raising=False)
    assert env_int("DUA_TEST_INT", 7) == 7
    monkeypatch.setenv("DUA_TEST_INT", " 42 ")
    assert env_int("DUA_TEST_INT", 7) == 42
    monkeypatch.setenv("DUA_TEST_INT", "  ")
    assert env_int("DUA_TEST_INT", 7) == 7
    monkeypatch.setenv("DUA_TEST_INT", "abc")
    assert env_int("DUA_TEST_INT", 7) == 7
    assert "DUA_TEST_INT" in capsys.readouterr().err


def test_bad_values_do_not_break_engine_imports(monkeypatch):
    for name in ("AGENT_MAX_STEPS", "AGENT_STEP_TOKENS", "CODING_MAX_STEPS", "AGENT_ANSWER_TOKENS", "TITLE_TOKENS"):
        monkeypatch.setenv(name, "oops")
    monkeypatch.setenv("AGENT_MODEL", "")
    import agent_tools
    import coding_agent
    try:
        tools = importlib.reload(agent_tools)
        coding = importlib.reload(coding_agent)
        assert tools.MAX_STEPS == 12 and tools.STEP_TOKENS == 16384
        assert coding.MAX_TOOL_STEPS == 15
    finally:
        monkeypatch.undo()
        importlib.reload(agent_tools)
        importlib.reload(coding_agent)


def test_daily_limits_fall_back_to_zero(monkeypatch):
    import app
    import coding_agent
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "x")
    assert app.daily_token_limit() == 0
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "-5")
    assert app.daily_token_limit() == 0
