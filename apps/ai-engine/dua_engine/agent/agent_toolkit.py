"""Typed custom tools on official ADK FunctionTool, sharing Dua safety boundaries.

Only server-configured Python modules are loaded; no HTTP request may supply code,
module names, credentials or endpoints. Write tools always use owner approval.
"""
from dataclasses import dataclass
import functools
import importlib
import inspect
import json
import os
import re


@dataclass(frozen=True)
class Tool:
    function: object
    kind: str = 'research'
    write: bool = False
    limit: int = 3
    revision: str = '1'

    @property
    def name(self):
        return self.function.__name__

    @property
    def identity(self):
        import hashlib
        try:
            source=inspect.getsource(inspect.getmodule(self.function))
        except (OSError,TypeError):
            source=inspect.getsource(self.function)
        return hashlib.sha256((self.revision+'\n'+source).encode()).hexdigest()

    def validate(self, args):
        from pydantic import ConfigDict, create_model
        fields = {}
        signature = inspect.signature(self.function)
        for name, parameter in signature.parameters.items():
            if parameter.kind not in (parameter.POSITIONAL_OR_KEYWORD, parameter.KEYWORD_ONLY) or parameter.annotation is inspect.Parameter.empty or name.startswith('_'):
                raise ValueError('Custom tools require explicitly typed JSON parameters')
            fields[name] = (parameter.annotation, ... if parameter.default is inspect.Parameter.empty else parameter.default)
        schema = create_model('DuaToolArguments', __config__=ConfigDict(extra='forbid', strict=True), **fields)
        return schema.model_validate(args).model_dump()

    def run(self, args):
        if inspect.iscoroutinefunction(self.function):
            raise ValueError('Custom tool functions must be synchronous; Dua supplies bounded worker threads')
        result = self.function(**self.validate(args))
        encoded = json.dumps(result, allow_nan=False)
        if len(encoded) > 16000:
            raise ValueError('Custom tool response exceeds 16000 characters')
        return result if isinstance(result, dict) else {'result': result}


class Registry:
    def __init__(self, tools=()):
        self.tools = {}
        for tool in tools:
            if not isinstance(tool, Tool) or not callable(tool.function) or inspect.iscoroutinefunction(tool.function) or not re.fullmatch(r'[a-z][a-z0-9_]{0,63}', tool.name) or \
                    tool.kind not in ('research','code','notes') or not 1 <= tool.limit <= 20 or \
                    not isinstance(tool.revision, str) or not 1 <= len(tool.revision) <= 64:
                raise ValueError('Invalid custom tool registration')
            if tool.name in self.tools:
                raise ValueError('Duplicate custom tool name')
            tool.validate({}) if not inspect.signature(tool.function).parameters else None
            self.tools[tool.name] = tool

    def wrappers(self, kind, call):
        result = []
        for spec in self.tools.values():
            if spec.kind != kind:
                continue
            def make(tool):
                @functools.wraps(tool.function)
                async def wrapped(**args):
                    clean = tool.validate(args)
                    return await call(tool.name, clean)
                wrapped.__signature__ = inspect.signature(tool.function)
                return wrapped
            result.append(make(spec))
        return result


def configured():
    import dua_engine.automation.agent_automation as agent_automation
    import dua_engine.agent.agent_tools as agent_tools
    import dua_engine.agent.agent_notes as agent_notes
    import dua_engine.coding.coding_agent as coding_agent
    reserved = {d['name'] for d in agent_tools.BASE_TOOLS + agent_tools.ISSUE_TOOLS + agent_notes.TOOLS + coding_agent.TOOLS}
    reserved |= {'search_documents', 'check_pr_ci'}
    reserved.update(agent_automation.NAMES)
    specs = []
    for path in filter(None, os.environ.get('ADK_TOOL_MODULES', '').split(',')):
        if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.]*', path):
            raise ValueError('ADK_TOOL_MODULES accepts installed module names only')
        module = importlib.import_module(path)
        specs.extend(module.TOOLS)
    registry = Registry(specs)
    if reserved.intersection(registry.tools):
        raise ValueError('Custom tools cannot replace built-in tools')
    return registry
