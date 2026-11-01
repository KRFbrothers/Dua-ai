"""Canonical package imports across the shipped engine layout."""
import importlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import textwrap
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

AGENT_MODULES = (
    "agent_adk", "agent_context", "agent_documents", "agent_evaluation", "agent_models",
    "agent_notes", "agent_observability", "agent_policy", "agent_resilience", "agent_safety",
    "agent_store", "agent_toolkit", "agent_tools", "agent_verify",
)


@pytest.mark.parametrize("name", AGENT_MODULES)
def test_agent_modules_import_from_package_and_reload(name):
    module = importlib.import_module("dua_engine.agent." + name)
    assert importlib.reload(module) is module


def test_package_import_stays_lazy():
    runtime = Path(__file__).resolve().parents[1]
    result = subprocess.run(
        [sys.executable, "-I", "-c", "import sys; sys.path.insert(0, sys.argv[1]); import dua_engine.agent; assert not any(key.startswith('dua_engine.agent.agent_') for key in sys.modules)", str(runtime)],
        capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_approval_store_is_shared_between_agent_modules(monkeypatch):
    from dua_engine.agent import agent_safety as safety, agent_store as store

    monkeypatch.delenv("ADK_SESSION_DB_URL", raising=False)
    monkeypatch.setattr(safety, "STORE", safety.PendingStore())
    assert store.approvals() is safety.STORE
    action = safety.STORE.create("owner", "save_note", {"note": "Synthetic import test"})
    assert store.approvals().take(action["id"], "owner") is action
    with pytest.raises(safety.ApprovalError):
        safety.STORE.take(action["id"], "owner")


def test_model_client_cache_is_shared_without_creating_a_client(monkeypatch):
    from dua_engine.agent import agent_models

    cached = object()
    monkeypatch.setattr(agent_models, "_key_client", cached)
    assert agent_models.key_client() is cached


def test_tool_module_monkeypatch_changes_function_globals(monkeypatch):
    from dua_engine.agent import agent_tools

    monkeypatch.setattr(agent_tools, "MAX_SOURCES", 1)
    run = agent_tools.AgentRun({})
    found = run.add_sources([
        {"url": "https://example.test/one", "title": "One"},
        {"url": "https://example.test/two", "title": "Two"},
    ])
    assert [row["title"] for row in found] == ["One"]
    assert len(run.sources) == 1


def test_adk_lazy_fallback(monkeypatch):
    from dua_engine.agent import agent_adk

    calls, logs = [], []
    def unavailable():
        calls.append("load")
        raise ImportError("Synthetic SDK unavailable")
    monkeypatch.setattr(agent_adk, "load_adk", unavailable)
    assert agent_adk.run({}, "Synthetic request", "flash", "agent", None, log=logs.append) is None
    assert calls == ["load"]
    assert logs == ["[dua-agent] ADK not installed; using the normal loop"]


@pytest.fixture(scope="module")
def shipped_runtime(tmp_path_factory):
    """Stage the Docker Python inputs without relying on checkout/test imports."""
    source = Path(__file__).resolve().parents[1]
    runtime = tmp_path_factory.mktemp("agent-runtime")
    for file in source.glob("*.py"):
        shutil.copy2(file, runtime / file.name)
    for directory in ("dua_engine", "adk_examples", "evals"):
        shutil.copytree(source / directory, runtime / directory,
                        ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    return runtime


def run_isolated(runtime, probe, *args):
    result = subprocess.run(
        [sys.executable, "-I", "-B", "-c", textwrap.dedent(probe), str(runtime), *args],
        cwd=runtime, env={"PATH": os.defpath}, capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("name", AGENT_MODULES)
def test_fresh_runtime_imports_are_canonical_and_lazy(shipped_runtime, name):
    run_isolated(shipped_runtime, """
        import importlib
        from pathlib import Path
        import sys

        runtime = Path(sys.argv[1])
        sys.path.insert(0, str(runtime))
        import dua_engine.agent
        assert not any(key.startswith('dua_engine.agent.agent_') for key in sys.modules)
        name = sys.argv[2]
        module = importlib.import_module('dua_engine.agent.' + name)
        assert importlib.reload(module) is module
        assert not any(key == 'google.adk' or key.startswith('google.adk.') or
                       key == 'google.genai' or key.startswith('google.genai.')
                       for key in sys.modules)
        assert not (runtime / (name + '.py')).exists()
    """, name)


def test_shipped_runtime_entrypoints_and_compatibility_shim(shipped_runtime):
    run_isolated(shipped_runtime, """
        import os
        import sys

        sys.path.insert(0, sys.argv[1])
        from dua_engine.agent import agent_adk, agent_models, agent_store, agent_toolkit
        from dua_engine.coding import coding_agent, coding_runner_server
        import coding_runner_server as runner_entrypoint
        assert runner_entrypoint.JOBS is coding_runner_server.JOBS
        assert runner_entrypoint.app.import_name == 'coding_runner_server'
        assert runner_entrypoint.app.test_client().get('/healthz').get_json() == {
            'service': 'dua-coding-runner', 'liveVerified': False}
        assert agent_models.check_adapters() is True
        assert not agent_toolkit.configured().tools
        import wsgi
        import adk_server
        assert wsgi.app.test_client().get('/').status_code == 200
        launched = []
        os.execvp = lambda executable, argv: launched.append((executable, argv))
        adk_server.main()
        assert len(launched) == 1 and launched[0][0] == 'gunicorn'
        assert launched[0][1][-1] == 'wsgi:app'
        assert coding_agent.__name__ == 'dua_engine.coding.coding_agent'
        assert agent_adk.__name__ == 'dua_engine.agent.agent_adk'
    """)
