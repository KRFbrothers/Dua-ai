"""Offline output-cap and truncation contract checks through the real Flask handler."""
from enum import Enum
import json
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app as engine


class FinishReason(Enum):
    MAX_TOKENS = 'MAX_TOKENS'
    STOP = 'STOP'


def chunk(text='answer', reason=None):
    return SimpleNamespace(text=text, candidates=[SimpleNamespace(finish_reason=reason)])


def events(response):
    return [json.loads(line[6:]) for line in response.get_data(as_text=True).splitlines()
            if line.startswith('data: ')]


@pytest.fixture
def model(monkeypatch):
    calls = []
    state = SimpleNamespace(reason=FinishReason.STOP, calls=calls, text="answer")
    def generate(contents, stream=False, generation_config=None):
        calls.append(generation_config or {})
        if stream:
            # SDKs can put finish metadata in a final chunk with no visible text.
            return iter([chunk(state.text), chunk('', state.reason)])
        return chunk(state.text, reason=state.reason)
    monkeypatch.setattr(engine, 'get_model', lambda system: SimpleNamespace(generate_content=generate))
    monkeypatch.setattr(engine, 'verify_owner', lambda _: 'owner')
    monkeypatch.setattr(engine, 'make_part', lambda **kwargs: kwargs)
    monkeypatch.setattr(engine, 'make_content', lambda role, parts: (role, parts))
    monkeypatch.setattr(engine, 'CHAT_MAX_OUTPUT_TOKENS', 0)
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '1')
    monkeypatch.setattr(engine, 'THINKING_MAX_LEVEL', 'high')
    monkeypatch.delenv('DUA_APP_TOKEN', raising=False)
    return state


def post(stream=False, **kwargs):
    return engine.app.test_client().post('/api/chat', json={
        'messages': [{'role': 'user', 'text': 'Write a long essay about the oceans.'}],
        'stream': stream, **kwargs})


@pytest.mark.parametrize('reason,expected', [(FinishReason.MAX_TOKENS, True),
    ('MAX_TOKENS', True), (FinishReason.STOP, False), ('STOP', False), (None, False)])
def test_finish_reason_by_name(reason, expected):
    assert engine.finish_truncated(chunk(reason=reason)) is expected


def test_missing_finish_metadata():
    for response in (None, SimpleNamespace(), SimpleNamespace(candidates=[])):
        assert not engine.finish_truncated(response)


@pytest.mark.parametrize('stream', [False, True])
@pytest.mark.parametrize('cap', [0, 64])
def test_plain_chat_cap_preserves_thinking_and_omits_default(model, monkeypatch, stream, cap):
    monkeypatch.setattr(engine, 'CHAT_MAX_OUTPUT_TOKENS', cap)
    response = post(stream, mode='reasoning')
    assert response.status_code == 200
    response.get_data()
    assert model.calls == [{'thinking_level': 'high', **({'max_output_tokens': cap} if cap else {})}]
    if stream:
        assert events(response)[-1] == {'type': 'done'}
        assert all('truncated' not in event and 'hint' not in event for event in events(response))
    else:
        assert response.get_json() == {'text': 'answer'}


@pytest.mark.parametrize('stream', [False, True])
def test_cut_off_reply_has_continue_hint(model, stream):
    model.reason = FinishReason.MAX_TOKENS
    response = post(stream)
    if stream:
        result = events(response)
        assert result[-2] == {'type': 'notice', 'message': engine.CONTINUE_HINT,
                              'truncated': True, 'hint': engine.CONTINUE_HINT}
        assert result[-1] == {'type': 'done', 'truncated': True}
    else:
        assert response.get_json() == {'text': 'answer', 'truncated': True, 'hint': engine.CONTINUE_HINT}


@pytest.mark.parametrize('cut_off', [False, True])
def test_agent_direct_answer_cap_and_finish(model, monkeypatch, cut_off):
    import agent_notes
    monkeypatch.setattr(engine, 'route_agent', lambda *a, **kw: 'chat')
    monkeypatch.setattr(agent_notes, 'for_owner', lambda *a: None)
    monkeypatch.setattr(engine, 'AGENT_ANSWER_TOKENS', 12345)
    monkeypatch.setattr(engine, 'CHAT_MAX_OUTPUT_TOKENS', 64)
    model.reason = FinishReason.MAX_TOKENS if cut_off else FinishReason.STOP
    result = events(post(True, mode='agent'))
    assert model.calls[0]['max_output_tokens'] == 12345
    if cut_off:
        assert result[-2]['hint'] == engine.CONTINUE_HINT
        assert result[-1] == {'type': 'done', 'truncated': True}
    else:
        assert result[-1] == {'type': 'done'}
        assert not any('hint' in event for event in result)


@pytest.mark.parametrize('stream', [False, True])
def test_output_cap_independent_of_thinking_router_rollback(model, monkeypatch, stream):
    monkeypatch.setattr(engine, 'THINKING_ROUTER', '0')
    monkeypatch.setattr(engine, 'CHAT_MAX_OUTPUT_TOKENS', 64)
    response = post(stream)
    response.get_data()
    assert model.calls == [{'max_output_tokens': 64}]


@pytest.mark.parametrize('stream', [False, True])
def test_thinking_consumes_cap_without_visible_text_still_gets_hint(model, stream):
    model.text = ''
    model.reason = FinishReason.MAX_TOKENS
    response = post(stream)
    assert response.status_code == 200
    result = events(response)[-2] if stream else response.get_json()
    assert result['truncated'] is True and result['hint'] == engine.CONTINUE_HINT
