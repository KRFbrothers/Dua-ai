import importlib
import json
import sys

import pytest

pytest.importorskip('pydantic')

from dua_engine.agent import agent_mcp

FAKE_SERVER = r'''
import json, sys
for line in sys.stdin:
    m = json.loads(line)
    if 'id' not in m:
        continue
    if m['method'] == 'initialize':
        r = {'protocolVersion': '2024-11-05', 'capabilities': {}, 'serverInfo': {'name': 'fake'}}
    elif m['method'] == 'tools/list':
        r = {'tools': [
            {'name': 'echo', 'description': 'Echo text', 'inputSchema': {'type': 'object',
             'properties': {'text': {'type': 'string'}, 'times': {'type': 'integer'}}, 'required': ['text']}},
            {'name': 'delete_all', 'inputSchema': {'type': 'object', 'properties': {}}}]}
    else:
        a = m['params']['arguments']
        r = {'content': [{'type': 'text', 'text': a['text'] * a.get('times', 1)}]}
        if m['params']['name'] == 'boom':
            r['isError'] = True
    sys.stdout.write(json.dumps({'jsonrpc': '2.0', 'id': m['id'], 'result': r}) + '\n')
    sys.stdout.flush()
'''


@pytest.fixture(autouse=True)
def fresh_modules():
    # Other tests purge dua_engine modules; reload so Tool and Registry come from the same module.
    importlib.reload(agent_mcp)


@pytest.fixture
def servers(tmp_path):
    script = tmp_path / 'fake_mcp.py'
    script.write_text(FAKE_SERVER)
    return {'fake': {'command': [sys.executable, str(script)], 'allow': ['echo']}}


def test_discovers_only_allowed_tools_and_runs_them(servers):
    tools = agent_mcp.discover(servers, timeout=10)
    assert [t.name for t in tools] == ['mcp_fake_echo']
    registry = sys.modules['dua_engine.agent.agent_toolkit'].Registry(tools)
    tool = registry.tools['mcp_fake_echo']
    assert tool.run({'text': 'ab', 'times': 2}) == {'result': 'abab'}
    assert tool.run({'text': 'x'}) == {'result': 'x'}


def test_arguments_are_validated_strictly(servers):
    tool = agent_mcp.discover(servers, timeout=10)[0]
    with pytest.raises(Exception):
        tool.run({'text': 5})
    with pytest.raises(Exception):
        tool.run({'text': 'a', 'extra': 1})


def test_missing_allowed_tool_fails_closed(servers):
    servers['fake']['allow'] = ['nope']
    with pytest.raises(ValueError, match='does not provide'):
        agent_mcp.discover(servers, timeout=10)


def test_config_requires_explicit_allow_list_and_command():
    with pytest.raises(ValueError):
        agent_mcp.parse_servers(json.dumps({'a': {'command': ['x']}}))
    with pytest.raises(ValueError):
        agent_mcp.parse_servers(json.dumps({'A': {'command': ['x'], 'allow': []}}))
    with pytest.raises(ValueError):
        agent_mcp.parse_servers(json.dumps({'a': {'command': 'x', 'allow': []}}))
    assert agent_mcp.parse_servers('') == {}


def test_dead_server_reports_error():
    with pytest.raises(agent_mcp.McpError):
        with agent_mcp.StdioClient([sys.executable, '-c', 'pass'], timeout=5):
            pass
