"""Import and entrypoint compatibility without host devices or terminal processes."""
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

MODULES = ("agent_automation", "automation_companion", "automation_drivers", "automation_terminal")
FIRST_IMPORTS = tuple(path for name in MODULES for path in (name, "dua_engine.automation." + name)) + (
    "dua_engine.agent.agent_automation",
)


@pytest.mark.parametrize("name", MODULES)
def test_legacy_and_packaged_imports_share_module(name):
    legacy = importlib.import_module(name)
    canonical = importlib.import_module("dua_engine.automation." + name)
    assert legacy is canonical
    if name == "agent_automation":
        assert importlib.import_module("dua_engine.agent.agent_automation") is canonical


def test_prior_agent_package_monkeypatch_changes_client_globals(monkeypatch):
    from dua_engine.agent import agent_automation as previous
    from dua_engine.automation import agent_automation as canonical

    monkeypatch.setattr(previous, "configuration", lambda: ("synthetic", "configuration"))
    assert canonical.status()["configured"] is True


def test_legacy_driver_monkeypatch_changes_packaged_validation(monkeypatch):
    import automation_drivers
    from dua_engine.automation import automation_drivers as canonical

    monkeypatch.setattr(automation_drivers, "KEYS", {"synthetic-key"})
    assert canonical.valid_key("synthetic-key") == "synthetic-key"
    with pytest.raises(ValueError):
        canonical.valid_key("enter")


def test_legacy_terminal_monkeypatch_changes_packaged_expiry(monkeypatch):
    import automation_terminal
    from dua_engine.automation import automation_terminal as canonical

    monkeypatch.setattr(automation_terminal, "LIFETIME_SECONDS", 0)
    session = canonical.Session.__new__(canonical.Session)
    session.clock = lambda: 1
    session.created = session.touched = 0
    assert session.expired()


def test_companion_factory_patch_and_flask_identity_survive_move(monkeypatch):
    import automation_companion
    from dua_engine.automation import automation_companion as canonical

    control = SimpleNamespace(handle=lambda body: {"synthetic": True})
    monkeypatch.setattr(automation_companion, "Companion", lambda config: control)
    app = canonical.create_app({}, "x" * 40)
    assert app.extensions["companion"] is control
    assert app.import_name == "automation_companion"
    assert Path(app.root_path) == Path(__file__).resolve().parents[1]
    client = app.test_client()
    assert client.post("/v1/tool", json={}).status_code == 401
    response = client.post("/v1/tool", json={}, headers={"Authorization": "Bearer " + "x" * 40})
    assert response.json == {"synthetic": True}
    assert response.headers["Cache-Control"] == "no-store"


@pytest.fixture(scope="module")
def shipped_runtime(tmp_path_factory):
    source = Path(__file__).resolve().parents[1]
    runtime = tmp_path_factory.mktemp("automation-runtime")
    for file in source.glob("*.py"):
        shutil.copy2(file, runtime / file.name)
    for directory in ("dua_engine", "adk_examples", "evals"):
        shutil.copytree(source / directory, runtime / directory,
                        ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    return runtime


def run_isolated(runtime, script, *args):
    result = subprocess.run(
        [sys.executable, "-I", "-B", "-c", textwrap.dedent(script), str(runtime), *args],
        cwd=runtime, env={"PATH": os.defpath}, capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return result


@pytest.mark.parametrize("first", FIRST_IMPORTS)
def test_fresh_import_orders_reload_and_lazy_host_dependencies(shipped_runtime, first):
    run_isolated(shipped_runtime, """
        import importlib
        import sys

        sys.path.insert(0, sys.argv[1])
        import dua_engine.automation
        assert not any(key.startswith('automation_') or key.startswith('dua_engine.automation.')
                       for key in sys.modules)
        first = sys.argv[2]
        module = importlib.import_module(first)
        if first.endswith('agent_automation'):
            assert not any(key.endswith(('automation_companion', 'automation_drivers', 'automation_terminal'))
                           for key in sys.modules)
        for name in ('agent_automation', 'automation_companion', 'automation_drivers', 'automation_terminal'):
            canonical = importlib.import_module('dua_engine.automation.' + name)
            assert importlib.import_module(name) is canonical
            assert getattr(dua_engine.automation, name) is canonical
        assert importlib.import_module('dua_engine.agent.agent_automation') is importlib.import_module('agent_automation')
        assert importlib.reload(module) is module
        assert importlib.import_module(first) is module
        assert not any(key.split('.')[0] in ('pyautogui', 'adbutils', 'playwright', 'pexpect')
                       for key in sys.modules)
    """, first)


@pytest.mark.parametrize("entry", ("script", "legacy-module", "canonical-module"))
def test_companion_cli_still_reaches_argument_parser_without_host_actions(shipped_runtime, entry):
    result = run_isolated(shipped_runtime, """
        import runpy
        import sys
        from pathlib import Path
        from types import SimpleNamespace

        sys.path.insert(0, sys.argv[1])
        runtime, entry = Path(sys.argv[1]), sys.argv[2]
        def refuse_serve(*args, **kwargs):
            raise AssertionError('CLI help must not start a companion')
        sys.modules['waitress'] = SimpleNamespace(serve=refuse_serve)
        sys.argv = ['automation_companion', '--help']
        if entry == 'script':
            runpy.run_path(str(runtime / 'automation_companion.py'), run_name='__main__')
        else:
            name = 'automation_companion' if entry == 'legacy-module' else 'dua_engine.automation.automation_companion'
            runpy.run_module(name, run_name='__main__')
    """, entry)
    assert "--config" in result.stdout and "--port" in result.stdout
