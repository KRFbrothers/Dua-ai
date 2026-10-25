"""Legacy/canonical import compatibility across the shipped engine layout."""
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

SATELLITES = (
    "agent_automation", "agent_context", "agent_documents", "agent_evaluation",
    "agent_models", "agent_notes", "agent_observability", "agent_policy",
    "agent_resilience", "agent_safety", "agent_store", "agent_toolkit", "agent_verify",
)
HUBS = ("agent_adk", "agent_tools")
AGENT_MODULES = SATELLITES + HUBS


@pytest.mark.parametrize("name", AGENT_MODULES)
def test_legacy_and_packaged_agent_imports_share_module(name):
    legacy = importlib.import_module(name)
    packaged = importlib.import_module("dua_engine.agent." + name)
    assert legacy is packaged
    assert sys.modules[name] is packaged


def test_legacy_monkeypatch_changes_packaged_function_globals(monkeypatch):
    import agent_context
    from dua_engine.agent import agent_context as packaged

    monkeypatch.setattr(agent_context, "MAX_TEXT_CHARS", 4)
    original = [SimpleNamespace(role="user", parts=[SimpleNamespace(text="abcdefgh")])]
    assert packaged.bounded(original)[0].parts[0].text == "efgh"
    assert original[0].parts[0].text == "abcdefgh"


def test_approval_store_is_shared_across_legacy_and_packaged_callers(monkeypatch):
    import agent_safety
    from dua_engine.agent import agent_safety as safety, agent_store as store

    monkeypatch.delenv("ADK_SESSION_DB_URL", raising=False)
    monkeypatch.setattr(agent_safety, "STORE", safety.PendingStore())
    assert store.approvals() is safety.STORE
    action = agent_safety.STORE.create("owner", "save_note", {"note": "Synthetic import test"})
    assert store.approvals().take(action["id"], "owner") is action
    with pytest.raises(agent_safety.ApprovalError):
        safety.STORE.take(action["id"], "owner")


def test_model_client_cache_is_shared_without_creating_a_client(monkeypatch):
    import agent_models
    from dua_engine.agent import agent_models as packaged

    cached = object()
    monkeypatch.setattr(agent_models, "_key_client", cached)
    assert packaged.key_client() is cached


def test_legacy_tools_monkeypatch_changes_packaged_class_globals(monkeypatch):
    import agent_tools
    from dua_engine.agent import agent_tools as packaged

    assert agent_tools.AgentRun is packaged.AgentRun
    assert agent_tools.Cancelled is packaged.Cancelled
    assert agent_tools.DeadlineExceeded is packaged.DeadlineExceeded
    monkeypatch.setattr(agent_tools, "MAX_SOURCES", 1)
    run = packaged.AgentRun({})
    found = run.add_sources([
        {"url": "https://example.test/one", "title": "One"},
        {"url": "https://example.test/two", "title": "Two"},
    ])
    assert [row["title"] for row in found] == ["One"]
    assert len(run.sources) == 1


def test_legacy_adk_monkeypatch_preserves_lazy_fallback(monkeypatch):
    import agent_adk
    from dua_engine.agent import agent_adk as packaged

    calls, logs = [], []
    def unavailable():
        calls.append("load")
        raise ImportError("Synthetic SDK unavailable")
    monkeypatch.setattr(agent_adk, "load_adk", unavailable)
    assert packaged.run({}, "Synthetic request", "flash", "agent", None, log=logs.append) is None
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
@pytest.mark.parametrize("legacy_first", (True, False))
def test_fresh_import_orders_and_reload(shipped_runtime, name, legacy_first):
    run_isolated(shipped_runtime, """
        import importlib
        from pathlib import Path
        import sys

        sys.path.insert(0, sys.argv[1])
        import dua_engine.agent
        assert not any(key.startswith('agent_') or key.startswith('dua_engine.agent.')
                       for key in sys.modules)
        name, legacy_first = sys.argv[2], sys.argv[3] == 'True'
        module = importlib.import_module(name if legacy_first else 'dua_engine.agent.' + name)
        for path in (Path(sys.argv[1]) / 'dua_engine/agent').glob('agent_*.py'):
            legacy = importlib.import_module(path.stem)
            canonical = importlib.import_module('dua_engine.agent.' + path.stem)
            assert legacy is canonical
            assert getattr(dua_engine.agent, path.stem) is canonical
        assert not any(key == 'google.adk' or key.startswith('google.adk.') or
                       key == 'google.genai' or key.startswith('google.genai.')
                       for key in sys.modules)
        assert importlib.reload(module) is module
        assert importlib.import_module(name) is module
        assert importlib.import_module('dua_engine.agent.' + name) is module
    """, name, str(legacy_first))


@pytest.mark.parametrize("legacy_first", (True, False))
def test_shipped_runtime_hubs_coding_and_cloud_run_entrypoints(shipped_runtime, legacy_first):
    run_isolated(shipped_runtime, """
        import importlib
        import os
        import sys

        sys.path.insert(0, sys.argv[1])
        prefix = '' if sys.argv[2] == 'True' else 'dua_engine.agent.'
        for name in ('agent_adk', 'agent_tools', 'agent_safety', 'agent_models', 'agent_store', 'agent_toolkit'):
            importlib.import_module(prefix + name)
        import agent_adk, agent_tools, agent_models, agent_store, agent_toolkit
        from dua_engine.agent import agent_safety
        from dua_engine.coding import coding_agent
        assert importlib.import_module('coding_agent') is coding_agent
        assert agent_store.approvals() is agent_safety.STORE
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
    """, str(legacy_first))
