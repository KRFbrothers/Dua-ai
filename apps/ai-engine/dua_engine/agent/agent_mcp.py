"""Opt-in MCP (Model Context Protocol) bridge on top of the custom-tool registry.

Enable with ADK_TOOL_MODULES=dua_engine.agent.agent_mcp and a server-side
ADK_MCP_SERVERS JSON object, for example:
  {"files": {"command": ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/data"],
             "allow": ["read_file"], "env": {"KEY": "value"}}}
Only stdio servers started by the operator are supported. Every exposed tool must be
named in that server's "allow" list, so nothing is exposed by default and no HTTP
request can add servers, commands or credentials. Tools run through the normal
research-tool limits, argument validation and response-size cap.
"""
import inspect
import json
import os
import re
import subprocess
import threading
import time

from dua_engine.agent.agent_toolkit import Tool

PROTOCOL_VERSION = '2024-11-05'
TIMEOUT_SECONDS = 30
MAX_LINE = 1_000_000
_TYPES = {'string': str, 'integer': int, 'number': float, 'boolean': bool, 'array': list, 'object': dict}


class McpError(Exception):
    pass


class StdioClient:
    """Short-lived JSON-RPC session: one subprocess per operation, always cleaned up."""

    def __init__(self, command, env=None, timeout=TIMEOUT_SECONDS):
        self.command, self.env, self.timeout = command, env or {}, timeout
        self.proc, self.next_id = None, 0

    def __enter__(self):
        env = {k: v for k, v in os.environ.items() if k in ('PATH', 'HOME', 'LANG', 'TMPDIR')}
        env.update(self.env)
        self.deadline = time.monotonic() + self.timeout
        self.proc = subprocess.Popen(self.command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.DEVNULL, env=env, text=True, bufsize=1)
        self.timer = threading.Timer(self.timeout, self.proc.kill)
        self.timer.start()
        self.request('initialize', {'protocolVersion': PROTOCOL_VERSION, 'capabilities': {},
                                    'clientInfo': {'name': 'dua-ai', 'version': '1'}})
        self.send({'jsonrpc': '2.0', 'method': 'notifications/initialized'})
        return self

    def __exit__(self, *exc):
        self.timer.cancel()
        try:
            self.proc.stdin.close()
        except OSError:
            pass
        try:
            self.proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            self.proc.wait()
        self.proc.stdout.close()

    def send(self, message):
        try:
            self.proc.stdin.write(json.dumps(message) + '\n')
            self.proc.stdin.flush()
        except (OSError, ValueError) as exc:
            raise McpError('MCP server closed the connection') from exc

    def request(self, method, params=None):
        self.next_id += 1
        ident = self.next_id
        self.send({'jsonrpc': '2.0', 'id': ident, 'method': method, 'params': params or {}})
        while True:
            line = self.proc.stdout.readline(MAX_LINE)
            if not line:
                raise McpError('MCP server closed the connection or timed out')
            try:
                message = json.loads(line)
            except ValueError:
                continue
            if not isinstance(message, dict) or message.get('id') != ident or 'method' in message:
                continue
            if 'error' in message:
                raise McpError('MCP error: ' + str((message['error'] or {}).get('message', 'unknown'))[:300])
            return message.get('result') or {}


def parse_servers(raw):
    if not raw:
        return {}
    config = json.loads(raw)
    if not isinstance(config, dict):
        raise ValueError('ADK_MCP_SERVERS must be a JSON object')
    for name, server in config.items():
        if not re.fullmatch(r'[a-z][a-z0-9]{0,15}', name or ''):
            raise ValueError('MCP server names must be lowercase letters and digits, max 16 characters')
        command = server.get('command') if isinstance(server, dict) else None
        allow = server.get('allow') if isinstance(server, dict) else None
        if not isinstance(command, list) or not command or not all(isinstance(c, str) and c for c in command):
            raise ValueError('MCP server command must be a non-empty list of strings')
        if not isinstance(allow, list) or not all(isinstance(a, str) for a in allow):
            raise ValueError('MCP server needs an explicit allow list of tool names')
        if not isinstance(server.get('env', {}), dict):
            raise ValueError('MCP server env must be an object')
    return config


def local_name(server, tool):
    return re.sub(r'[^a-z0-9_]', '_', ('mcp_' + server + '_' + tool).lower())[:64]


def build_tool(server, config, spec, timeout=TIMEOUT_SECONDS):
    remote = spec['name']
    schema = spec.get('inputSchema') or {}
    required = set(schema.get('required', []))
    parameters = []
    for key, prop in (schema.get('properties') or {}).items():
        if not re.fullmatch(r'[a-z][A-Za-z0-9_]*', key):
            raise ValueError('Unsupported MCP parameter name: ' + key)
        kind = _TYPES.get((prop or {}).get('type'), str)
        default = inspect.Parameter.empty if key in required else None
        parameters.append(inspect.Parameter(key, inspect.Parameter.KEYWORD_ONLY, default=default,
                                            annotation=kind if key in required else kind | None))

    def call(**args):
        with StdioClient(config['command'], config.get('env'), timeout) as client:
            result = client.request('tools/call', {'name': remote, 'arguments':
                                                   {k: v for k, v in args.items() if v is not None}})
        text = '\n'.join(part.get('text', '') for part in result.get('content', []) if part.get('type') == 'text')
        if result.get('isError'):
            raise McpError('MCP tool failed: ' + text[:300])
        return {'result': text[:15000]}

    call.__name__ = local_name(server, remote)
    call.__doc__ = (spec.get('description') or remote)[:500]
    call.__signature__ = inspect.Signature(sorted(parameters, key=lambda p: p.default is not inspect.Parameter.empty))
    return Tool(call, kind='research', limit=5, revision='mcp-' + server)


def discover(servers, timeout=TIMEOUT_SECONDS):
    tools = []
    for server, config in servers.items():
        with StdioClient(config['command'], config.get('env'), timeout) as client:
            listed = client.request('tools/list').get('tools', [])
        found = {spec.get('name'): spec for spec in listed if isinstance(spec, dict)}
        missing = set(config['allow']) - set(found)
        if missing:
            raise ValueError('MCP server %s does not provide allowed tools: %s' % (server, ', '.join(sorted(missing))))
        tools.extend(build_tool(server, config, found[name], timeout) for name in config['allow'])
    return tools


TOOLS = discover(parse_servers(os.environ.get('ADK_MCP_SERVERS', '')))
