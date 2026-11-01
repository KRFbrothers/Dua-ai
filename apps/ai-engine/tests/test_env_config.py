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
    for name in ("AGENT_MAX_STEPS", "AGENT_STEP_TOKENS", "CODING_MAX_STEPS", "AGENT_ANSWER_TOKENS", "CHAT_MAX_OUTPUT_TOKENS", "TITLE_TOKENS"):
        monkeypatch.setenv(name, "oops")
    monkeypatch.setenv("AGENT_MODEL", "")
    import dua_engine.agent.agent_tools as agent_tools
    import dua_engine.coding.coding_agent as coding_agent
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
    import dua_engine.coding.coding_agent as coding_agent
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "x")
    assert app.daily_token_limit() == 0
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "-5")
    assert app.daily_token_limit() == 0


def test_bad_values_do_not_break_agent_adk_import(monkeypatch):
    monkeypatch.setenv("AGENT_SUB_STEPS", "oops")
    monkeypatch.setenv("AGENT_ADK_SECONDS", "")
    import dua_engine.agent.agent_adk as agent_adk
    try:
        adk = importlib.reload(agent_adk)
        assert adk.SUB_STEPS == 5 and adk.TOTAL_SECONDS == 300
    finally:
        monkeypatch.undo()
        importlib.reload(agent_adk)


def test_output_caps_defaults_and_clamps(monkeypatch):
    # Separate imports keep the rest of the suite's app globals untouched.
    import json
    from pathlib import Path
    import subprocess
    root = Path(__file__).resolve().parents[1]
    cases = [(None, [0, 8192]), ("", [0, 8192]), ("oops", [0, 8192]),
             ("-5", [0, 1]), ("0", [0, 1]), ("64", [64, 64]),
             ("65536", [65536, 65536]), ("999999", [65536, 65536])]
    for raw, expected in cases:
        for name in ("CHAT_MAX_OUTPUT_TOKENS", "AGENT_ANSWER_TOKENS"):
            if raw is None:
                monkeypatch.delenv(name, raising=False)
            else:
                monkeypatch.setenv(name, raw)
        result = subprocess.run([sys.executable, "-c",
            "import app,json; print(json.dumps([app.CHAT_MAX_OUTPUT_TOKENS,app.AGENT_ANSWER_TOKENS]))"],
            cwd=root, capture_output=True, text=True, check=True)
        assert json.loads(result.stdout) == expected
