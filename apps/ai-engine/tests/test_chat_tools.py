"""Plain-chat hosted tools, using real SDK configs and an offline provider transport."""
import json
from pathlib import Path
import sys
from types import SimpleNamespace as NS

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app as engine


def response(text='answer', sources=(), parts=()):
    return NS(text=text, candidates=[NS(finish_reason=None, content=NS(parts=parts),
        grounding_metadata=NS(grounding_chunks=[NS(web=NS(uri=url, title=title, domain=''))
                                                for url, title in sources]))])


def events(reply):
    return [json.loads(line[6:]) for line in reply.get_data(as_text=True).splitlines()
            if line.startswith('data: ')]


@pytest.fixture
def client(monkeypatch):
    types = pytest.importorskip('google.genai.types')
    state = NS(calls=[], chunks=[response()], reply=response(), types=types)
    def call(stream, **kwargs):
        state.calls.append(kwargs)
        return iter(state.chunks) if stream else state.reply
    monkeypatch.setattr(engine, 'get_client', lambda: NS(models=NS(
        generate_content=lambda **kw: call(False, **kw),
        generate_content_stream=lambda **kw: call(True, **kw))))
    monkeypatch.setattr(engine, '_models', {})
    monkeypatch.setattr(engine, 'verify_owner', lambda _: 'owner')
    monkeypatch.setattr(engine, 'CHAT_WEB_SEARCH', '1')
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', '0')
    monkeypatch.delenv('DUA_APP_TOKEN', raising=False)
    monkeypatch.delenv('DUA_MAINTENANCE', raising=False)
    monkeypatch.delenv('DUA_DISABLE_AGENT', raising=False)
    state.client = engine.app.test_client()
    return state


def post(state, stream=True, **fields):
    return state.client.post('/api/chat', json={'stream': stream,
        'messages': [{'role': 'user', 'text': "What is today's news?"}], **fields})


@pytest.mark.parametrize('stream', [False, True])
def test_web_search_tool(client, stream):
    reply = post(client, stream, web=True)
    assert reply.status_code == 200
    reply.get_data()
    config = client.calls[0]['config']
    assert isinstance(config, client.types.GenerateContentConfig)
    assert len(config.tools) == 1 and config.tools[0].google_search is not None
    assert config.tools[0].code_execution is None
    if stream:
        assert not any(e['type'] == 'notice' for e in events(reply))
    else:
        assert 'notice' not in reply.get_json()


@pytest.mark.parametrize('stream', [False, True])
@pytest.mark.parametrize('flag', ['0', '1'])
def test_code_requires_flag_and_request(client, monkeypatch, stream, flag):
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', flag)
    reply = post(client, stream, code=True)
    reply.get_data()
    tools = client.calls[0]['config'].tools or []
    assert bool(tools) == (flag == '1')
    if flag == '1':
        assert tools[0].code_execution is not None
    notices = [e['message'] for e in events(reply) if e['type'] == 'notice'] if stream else [reply.get_json().get('notice', '')]
    assert any('Run code is not available' in note for note in notices) == (flag == '0')


def test_flags_alone_never_activate_tools(client, monkeypatch):
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', '1')
    post(client).get_data()
    assert not client.calls[0]['config'].tools


def test_both_tools_can_be_requested(client, monkeypatch):
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', '1')
    post(client, web=True, code=True).get_data()
    tools = client.calls[0]['config'].tools
    assert len(tools) == 2 and tools[0].google_search is not None and tools[1].code_execution is not None


def test_web_disabled_notice(client, monkeypatch):
    monkeypatch.setattr(engine, 'CHAT_WEB_SEARCH', '0')
    reply = post(client, web=True)
    assert any('Web search is not available' in e.get('message', '') for e in events(reply))
    assert not client.calls[0]['config'].tools


@pytest.mark.parametrize('fields', [{'web': True}, {'code': True}])
def test_tool_requests_never_use_free_model(client, monkeypatch, fields):
    import openrouter
    monkeypatch.setattr(openrouter, 'enabled', lambda: True)
    monkeypatch.setattr(openrouter, 'allowed', lambda _: True)
    monkeypatch.setattr(openrouter, 'stream', lambda *a, **kw: pytest.fail('Free model used for tool request'))
    reply = post(client, openrouter='curated:free', **fields)
    assert 'answer' in reply.get_data(as_text=True)
    assert client.calls[0]['model'] == engine.MODEL_NAME


@pytest.mark.parametrize('stream', [False, True])
def test_sources_reuse_agent_shape_and_footer(client, stream):
    source = [('https://news.test/item', 'News'), ('http://unsafe.test', 'Unsafe'),
              ('https://news.test/item', 'Duplicate')]
    client.reply = response(sources=source)
    client.chunks = [response(), response('', sources=source), response('', sources=source)]
    reply = post(client, stream, web=True)
    expected = [{'id': 1, 'title': 'News', 'url': 'https://news.test/item', 'domain': 'news.test'}]
    if stream:
        data = events(reply)
        assert [e['sources'] for e in data if e['type'] == 'sources'] == [expected]
        assert data[-3]['type'] == 'sources' and data[-1]['type'] == 'done'
        assert 'Sources:' in data[-2]['delta']
    else:
        assert reply.get_json()['sources'] == expected
        assert 'Sources:' in reply.get_json()['text']


def test_sources_cap_is_across_chunks(client):
    source = [(f'https://news.test/{i}', str(i)) for i in range(30)]
    client.chunks = [response(sources=source[:8]), response('', sources=source[4:])]
    data = events(post(client, web=True))
    sources = next(e['sources'] for e in data if e['type'] == 'sources')
    assert len(sources) == engine.AGENT_MAX_SOURCES
    assert [s['id'] for s in sources] == list(range(1, engine.AGENT_MAX_SOURCES + 1))
    assert len({s['url'] for s in sources}) == len(sources)


@pytest.mark.parametrize('stream', [False, True])
def test_code_parts_render_in_order_without_repeating_text_or_thoughts(client, monkeypatch, stream):
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', '1')
    types = client.types
    parts = [types.Part(text='Explain first.'),
             types.Part(executable_code=types.ExecutableCode(language='PYTHON', code='print(2 + 2)')),
             types.Part(code_execution_result=types.CodeExecutionResult(outcome='OUTCOME_OK', output='4\n')),
             types.Part(text='Private thought', thought=True), types.Part(text='Done.')]
    client.reply = response('Explain first.Done.', parts=parts)
    client.chunks = [client.reply]
    reply = post(client, stream, code=True)
    text = ''.join(e['delta'] for e in events(reply) if e['type'] == 'text') if stream else reply.get_json()['text']
    assert '```python\nprint(2 + 2)' in text and '```text\n4' in text
    assert text.index('Explain first.') < text.index('print(2 + 2)') < text.index('4\n') < text.index('Done.')
    assert text.count('Explain first.') == 1 and 'Private thought' not in text


def test_maintenance_stops_before_tool_or_model_setup(client, monkeypatch):
    monkeypatch.setenv('DUA_MAINTENANCE', '1')
    monkeypatch.setattr(engine, 'plain_chat_tools', lambda _: pytest.fail('Tool setup during maintenance'))
    assert post(client, web=True).status_code == 503
    assert not client.calls


def test_notice_retains_x_search_note(monkeypatch):
    monkeypatch.setattr(engine, 'CHAT_WEB_SEARCH', '1')
    monkeypatch.setattr(engine, 'CHAT_CODE_EXECUTION', '1')
    assert engine.unsupported_notice({'web': True, 'code': True}) is None
    assert 'X search is not available' in engine.unsupported_notice({'xSearch': True})


@pytest.mark.parametrize('stream', [False, True])
def test_grounding_preserves_long_output_config_and_cutoff_hint(client, monkeypatch, stream):
    monkeypatch.setattr(engine, 'CHAT_MAX_OUTPUT_TOKENS', 64)
    client.reply = response('Partial answer.', [('https://news.test/item', 'News')])
    client.reply.candidates[0].finish_reason = 'MAX_TOKENS'
    client.chunks = [client.reply]
    reply = post(client, stream, web=True, mode='reasoning')
    data = events(reply) if stream else reply.get_json()
    config = client.calls[0]['config']
    assert config.max_output_tokens == 64 and config.tools[0].google_search is not None
    assert config.thinking_config.thinking_level == 'HIGH'
    if stream:
        assert any(e['type'] == 'sources' for e in data)
        assert data[-2]['hint'] == engine.CONTINUE_HINT
        assert data[-1] == {'type': 'done', 'truncated': True}
    else:
        assert data['sources'] and data['hint'] == engine.CONTINUE_HINT and data['truncated'] is True
