"""Enable with ADK_TOOL_MODULES=adk_examples.text_tools; no network or secrets."""
from agent_toolkit import Tool


def word_count(text: str) -> dict:
    """Count words in supplied text without a model call or local file access."""
    if len(text) > 100_000:
        raise ValueError('Text exceeds 100000 characters')
    return {'words':len(text.split()), 'characters':len(text)}


TOOLS = [Tool(word_count, limit=3)]
