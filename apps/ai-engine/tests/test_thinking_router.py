"""Request thinking selection and actual SDK configs; model transport stays offline."""
import logging
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app as engine


@pytest.fixture(autouse=True)
def routing(monkeypatch):
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '1')
    monkeypatch.setattr(engine, 'THINKING_MAX_LEVEL', 'high')


def data(text='hi', mode='auto'):
    return {'mode': mode, 'messages': [{'role': 'user', 'text': text}]}


@pytest.mark.parametrize('body,route,expected', [
    (data(mode='reasoning'), None, 'high'),
    (data(), None, 'low'), (data(), 'code', 'high'), (data(), 'agent', 'medium'),
    (data('Please debug this code'), None, 'medium'),
    (data('prove this theorem'), None, 'medium'),
    (data('word ' * 401), None, 'medium'), (data('word ' * 400), None, 'low'),
])
def test_selection(body, route, expected):
    assert engine.pick_thinking_level(body, route) == expected


@pytest.mark.parametrize('route', [None, 'code', 'agent'])
def test_cost_ceiling(monkeypatch, route):
    monkeypatch.setattr(engine, 'THINKING_MAX_LEVEL', 'low')
    assert engine.pick_thinking_level(data(mode='reasoning'), route) == 'low'


def test_rollback_bypasses_ceiling(monkeypatch):
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '0')
    monkeypatch.setattr(engine, 'THINKING_MAX_LEVEL', 'low')
    monkeypatch.setattr(engine, 'THINKING_LEVEL', 'medium')
    monkeypatch.setattr(engine, 'AGENT_THINKING_LEVEL', 'high')
    assert engine.pick_thinking_level(data(mode='reasoning')) == 'medium'
    assert engine.pick_thinking_level(data(), 'agent') == 'high'
    assert engine.pick_thinking_level(data(), 'code') == 'high'


@pytest.mark.parametrize('stream', [False, True])
@pytest.mark.parametrize('model,expected', [('gemini-3.8-flash', 'HIGH'), ('gemini-4-argon', 'HIGH'),
                                           ('gemini-2.5-flash', None)])
def test_api_chat_sdk_config(monkeypatch, caplog, stream, model, expected):
    types = pytest.importorskip('google.genai.types')
    captured = []
    def generate(**kwargs):
        captured.append(kwargs['config'])
        response = SimpleNamespace(text='answer', candidates=[])
        return iter([response]) if stream else response
    monkeypatch.setattr(engine, 'verify_owner', lambda _: 'owner')
    monkeypatch.delenv('DUA_APP_TOKEN', raising=False)
    monkeypatch.setattr(engine, 'get_client', lambda: SimpleNamespace(models=SimpleNamespace(
        generate_content=generate, generate_content_stream=generate)))
    monkeypatch.setattr(engine, 'get_model', lambda system: engine.GenaiModel(system, model))
    caplog.set_level(logging.INFO, logger=engine.app.logger.name)
    response = engine.app.test_client().post('/api/chat', json={**data(mode='reasoning'), 'stream': stream})
    assert response.status_code == 200
    assert 'answer' in response.get_data(as_text=True)
    assert len(captured) == 1 and isinstance(captured[0], types.GenerateContentConfig)
    thinking = captured[0].thinking_config
    assert (thinking.thinking_level if thinking else None) == expected
    assert len([r for r in caplog.records if r.message.startswith('thinking level:')]) == 1


def test_request_does_not_mutate_cached_model(monkeypatch):
    pytest.importorskip('google.genai.types')
    monkeypatch.setattr(engine, '_models', {})
    cached = engine.get_model('system')
    assert cached.config({'thinking_level': 'high'}).thinking_config.thinking_level == 'HIGH'
    assert cached.thinking_level is None
    assert cached.config().thinking_config.thinking_level == engine.THINKING_LEVEL.upper()


@pytest.mark.parametrize('route,expected', [('agent', 'MEDIUM'), ('code', 'HIGH')])
def test_agent_factory_and_adk_config_are_request_local(monkeypatch, route, expected):
    pytest.importorskip('google.genai.types')
    monkeypatch.setattr(engine, 'AGENT_MODEL', 'gemini-4-argon')
    monkeypatch.setattr(engine, 'AGENT_THINKING_LEVEL', 'low')
    level = engine.pick_thinking_level(data(), route)
    token = engine._agent_request_level.set(level)
    try:
        routed = engine.get_agent_model('system')
    finally:
        engine._agent_request_level.reset(token)
    assert routed.config().thinking_config.thinking_level == expected
    assert engine.get_agent_model('system').config().thinking_config.thinking_level == 'LOW'
    assert engine.adk_config(engine.AGENT_MODEL, 256, thinking_level=level).thinking_config.thinking_level == expected
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '0')
    assert engine.adk_config(engine.AGENT_MODEL, 256, thinking_level=level).thinking_config.thinking_level == 'LOW'


@pytest.mark.parametrize('stream', [False, True])
def test_api_rollback_keeps_old_level_and_omits_override(monkeypatch, stream):
    seen = []
    def generate(contents, stream=False, **kwargs):
        seen.append(kwargs)
        reply = SimpleNamespace(text='answer', candidates=[])
        return iter([reply]) if stream else reply
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '0')
    monkeypatch.setattr(engine, 'CHAT_THINKING_EVENTS', '0')
    monkeypatch.setattr(engine, 'THINKING_MAX_LEVEL', 'low')
    monkeypatch.setattr(engine, 'verify_owner', lambda _: 'owner')
    monkeypatch.delenv('DUA_APP_TOKEN', raising=False)
    monkeypatch.setattr(engine, 'make_part', lambda **kw: kw)
    monkeypatch.setattr(engine, 'make_content', lambda role, parts: (role, parts))
    monkeypatch.setattr(engine, 'get_model', lambda system: SimpleNamespace(generate_content=generate))
    response = engine.app.test_client().post('/api/chat', json={**data(mode='reasoning'), 'stream': stream})
    assert 'answer' in response.get_data(as_text=True)
    assert seen == [{}]


def test_agent_tool_loop_wires_routed_level_to_factory(monkeypatch):
    import dua_engine.agent.agent_adk as agent_adk, dua_engine.agent.agent_documents as agent_documents, dua_engine.agent.agent_notes as agent_notes, dua_engine.agent.agent_tools as agent_tools
    captured = []
    monkeypatch.delenv('GITHUB_TOKEN', raising=False)
    monkeypatch.delenv('ADK_SESSION_DB_URL', raising=False)
    monkeypatch.setattr(agent_adk, 'enabled', lambda: False)
    monkeypatch.setattr(agent_notes, 'for_owner', lambda owner: None)
    monkeypatch.setattr(agent_documents, 'enabled', lambda: False)
    monkeypatch.setattr(engine, 'get_agent_model', lambda system: captured.append(engine._agent_request_level.get()) or object())
    monkeypatch.setattr(engine, 'build_contents', lambda *a: [])
    monkeypatch.setattr(agent_tools, 'default_tools', lambda *a, **kw: [])
    monkeypatch.setattr(agent_tools, 'run', lambda *a: ('answer', None))
    engine.tool_loop(data()['messages'], [], 'system', lambda *a: True, {})
    assert captured == ['medium'] and engine._agent_request_level.get() is None


def test_routed_agent_fallback_retains_cost_ceiling(monkeypatch):
    pytest.importorskip('google.genai.types')
    seen = []
    class Missing(Exception):
        code = 404
    def generate(**kwargs):
        seen.append(kwargs['config'].thinking_config.thinking_level)
        if len(seen) == 1:
            raise Missing()
        return SimpleNamespace(text='answer')
    monkeypatch.setattr(engine, 'AGENT_MODEL', 'gemini-4-argon')
    monkeypatch.setattr(engine, 'MODEL_NAME', 'gemini-3.8-flash')
    monkeypatch.setattr(engine, 'THINKING_LEVEL', 'high')
    monkeypatch.setattr(engine, 'get_client', lambda: SimpleNamespace(models=SimpleNamespace(generate_content=generate)))
    token = engine._agent_request_level.set('low')
    try:
        model = engine.get_agent_model('system')
    finally:
        engine._agent_request_level.reset(token)
    assert model.generate_content(['hi']).text == 'answer'
    assert seen == ['LOW', 'LOW']


@pytest.mark.parametrize('enabled', ['0', '1'])
def test_self_check_revision_stays_low_with_routing(monkeypatch, enabled):
    import dua_engine.agent.agent_verify as agent_verify
    seen = []
    def revise(prompt, generation_config):
        seen.append(generation_config)
        return SimpleNamespace(text='fixed')
    monkeypatch.setenv('THINKING_ROUTER', enabled)
    critic = SimpleNamespace(generate_content=lambda *a, **kw: SimpleNamespace(text='{"ok":false,"issues":["fix"]}'))
    result = agent_verify.check('question', 'draft', [], [], critic,
        SimpleNamespace(generate_content=revise), lambda r: r.text, lambda *a: None, log=lambda *a: None)
    assert result == 'fixed'
    assert seen[0].get('thinking_level') == ('low' if enabled == '1' else None)
