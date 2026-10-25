"""Preserve validated owner context without importing the SDK on the normal path."""
import copy

MAX_TURNS = 24
MAX_TEXT_CHARS = 96_000


def bounded(contents):
    """Keep newest complete multimodal turns; visibly mark omitted older text."""
    result = copy.deepcopy(list(contents or [])[-MAX_TURNS:])
    while result and result[0].role != "user":
        result.pop(0)
    remaining = MAX_TEXT_CHARS
    for content in reversed(result):
        for part in reversed(content.parts or []):
            if part.text:
                text = part.text
                part.text = text[-remaining:] if remaining else '[Earlier text omitted: context limit]'
                remaining = max(0, remaining - len(text))
    if result and len(contents or []) > MAX_TURNS:
        from google.genai import types
        result[0].parts.insert(0, types.Part(text='[Earlier conversation omitted: context limit]'))
    return result


def callback(contents, system, then=None, request_text="", observer=None):
    """Apply original multimodal context to every planner/helper/merger request.

    Each agent still has its own tool history; helpers do not inherit each other's
    concurrent intermediate events. Existing owner system instructions remain
    authoritative; notes remain fenced by the caller.
    """
    original = bounded(contents)
    def apply(callback_context=None, llm_request=None, **kwargs):
        response = then(callback_context=callback_context, llm_request=llm_request, **kwargs) if then else None
        if response is not None or llm_request is None:
            return response
        if not str(getattr(llm_request, "model", "")).startswith("gemini-"):
            llm_request.config.thinking_config = None
        if not getattr(llm_request, '_dua_context_applied', False):
            own = list(llm_request.contents or [])
            if own and own[0].role == "user" and request_text and all(
                    getattr(p,"text",None) is not None for p in own[0].parts or []):
                if "".join(p.text for p in own[0].parts or []).strip() == request_text.strip():
                    own = own[1:]
            llm_request.contents = copy.deepcopy(original) + own
            if system:
                existing = llm_request.config.system_instruction or ''
                if not isinstance(existing, str):
                    existing = str(existing)
                llm_request.config.system_instruction = system + '\n\nAgent role:\n' + existing
            object.__setattr__(llm_request, '_dua_context_applied', True)
        if observer is not None:
            observer.begin_model(getattr(callback_context,"agent_name","unknown"),llm_request.model)
        return None
    return apply
