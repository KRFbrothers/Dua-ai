"""Dua AI engine for Google Cloud Run (Vertex AI Gemini).

Endpoints
- GET  /            health check
- POST /api/chat    drop-in replacement for the web/ backend's chat endpoint used by the Android app:
                    full conversation history, inline attachments, SSE streaming (`"stream": true`)
                    with the same wire format as web/src/core/sse.mjs, or one JSON reply `{"text": ...}`.
                    `"mode": "agent"` runs the Dua Agent: router (casual -> direct reply), otherwise the
                    tool-calling loop in agent_tools.py (search, pages, code, images, memory, GitHub).
- POST /chat        old single-turn endpoint: {"message"} -> {"reply"} (kept for backward compat).

Configuration comes only from environment variables; no secrets in code.
  GOOGLE_CLOUD_PROJECT (required), VERTEX_LOCATION (default global),
  VERTEX_MODEL (default gemini-3.8-flash), AGENT_MODEL (Agent tool loop only, default VERTEX_MODEL),
  AGENT_THINKING_LEVEL (low|medium|high; default medium when AGENT_MODEL differs), AGENT_NOTES (0 turns off
  the Agent's private Supabase notes; see agent_notes.py), VERTEX_THINKING_LEVEL (Gemini 3: low|medium|high, default low),
  SUPABASE_URL, SUPABASE_ANON_KEY (or SUPABASE_PUBLISHABLE_KEY), DUA_ALLOWED_EMAIL (required owner authentication).
  DUA_APP_TOKEN is an optional additional gate, never a replacement for owner login.
"""
import base64
import binascii
import hmac
import json
import os
import queue
import re
import threading

from flask import Flask, Response, g, jsonify, request, stream_with_context
from owner_auth import OwnerAuthError, verify_owner

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 5 * 1024 * 1024

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "")
LOCATION = os.environ.get("VERTEX_LOCATION", "global")
MODEL_NAME = os.environ.get("VERTEX_MODEL", "gemini-3.8-flash")
# Gemini 3 uses thinking_level (low/medium/high; "minimal" is rejected by 3.8 Flash), not thinking_budget.
THINKING_LEVEL = os.environ.get("VERTEX_THINKING_LEVEL", "low").strip().lower()
if THINKING_LEVEL not in ("low", "medium", "high"):
    THINKING_LEVEL = "low"
# Dua Agent tool loop only (router, casual replies, titles and normal chat stay on VERTEX_MODEL).
# Gemini 4 Argon example: AGENT_MODEL=gemini-4-argon (location global/us/eu; LOW|MEDIUM|HIGH, no MINIMAL).
AGENT_MODEL = os.environ.get("AGENT_MODEL", "").strip() or MODEL_NAME
AGENT_THINKING_LEVEL = os.environ.get("AGENT_THINKING_LEVEL", "").strip().lower()
if AGENT_THINKING_LEVEL not in ("low", "medium", "high"):
    AGENT_THINKING_LEVEL = "medium" if AGENT_MODEL != MODEL_NAME else THINKING_LEVEL
MAX_MESSAGE_CHARS = 8000
MAX_TURNS = 400
MAX_TURN_CHARS = 100_000
MAX_MEMORY_CHARS = 8192  # Bounded owner project instructions (4000) plus up to 10 project memories.
MAX_ATTACHMENTS = 10
MAX_ATTACHMENT_BYTES = 4 * 1024 * 1024
HEARTBEAT_SECONDS = 15
ATTACHMENT_TYPES = ("image/", "application/pdf", "text/", "audio/", "video/")
# Models the app may name; this engine always answers with Gemini on Vertex AI.
APP_MODELS = {"Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi"}

SYSTEM_PROMPT = "You are Dua AI, a helpful, smart, and polite personal AI assistant."
GENERIC_ERROR = "The Dua AI engine could not answer right now. Please try again."

# Dua Agent: casual messages get one direct reply; tasks run the tool loop in agent_tools.py
# (max AGENT_MAX_STEPS model calls, at most 3 grounded searches and 2 code runs per request).
# Output caps include thinking tokens on Gemini 2.5/3, so leave room for low-level thinking.
AGENT_ANSWER_TOKENS = int(os.environ.get("AGENT_ANSWER_TOKENS", "4096"))
AGENT_MAX_SOURCES = 12

SSE_HEADERS = {
    "Cache-Control": "no-store",
    "X-Accel-Buffering": "no",
    "X-Content-Type-Options": "nosniff",
}

_client = None
_models = {}


class BadRequest(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.message = message
        self.status = status


def get_client():
    """google-genai client on Vertex AI. Location "global" uses https://aiplatform.googleapis.com."""
    global _client
    if _client is None:
        if not PROJECT_ID:
            raise RuntimeError("GOOGLE_CLOUD_PROJECT is not set")
        from google import genai
        _client = genai.Client(vertexai=True, project=PROJECT_ID, location=LOCATION)
    return _client


def thinking_config(model_name=None, level=None):
    """Gemini 3/4 models take thinking_level; older models keep their default thinking."""
    if not (model_name or MODEL_NAME).startswith(("gemini-3", "gemini-4")):
        return None
    from google.genai import types
    return types.ThinkingConfig(thinking_level=(level or THINKING_LEVEL).upper())


def unavailable_model_error(error):
    """True for 404 not-found / 403 permission errors (model missing in this project or location)."""
    code = getattr(error, "code", None) or getattr(error, "status_code", None)
    if code in (403, 404):
        return True
    text = str(error).upper()
    return "NOT_FOUND" in text or "PERMISSION_DENIED" in text


class GenaiModel:
    """Small adapter with the generate_content(contents, stream, generation_config, tools) shape used here."""

    def __init__(self, system, model_name=None, thinking_level=None, fallback=None):
        self.system = system
        self.model_name = model_name or MODEL_NAME
        self.thinking_level = thinking_level
        self.fallback = fallback if fallback and fallback != self.model_name else None

    def config(self, generation_config=None, tools=None):
        from google.genai import types
        options = {"system_instruction": self.system, **(generation_config or {})}
        level = options.pop("thinking_level", None)
        if tools:
            options["tools"] = tools
        thinking = thinking_config(self.model_name, self.thinking_level)
        if thinking is not None and level:
            thinking = types.ThinkingConfig(thinking_level=str(level).upper())
        if thinking is not None:
            options["thinking_config"] = thinking
        return types.GenerateContentConfig(**options)

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        models = get_client().models
        config = self.config(generation_config, tools)
        call = models.generate_content_stream if stream else models.generate_content
        try:
            return call(model=self.model_name, contents=contents, config=config)
        except Exception as error:
            if not self.fallback or not unavailable_model_error(error):
                raise
            print(f"[dua-agent] {self.model_name} unavailable ({type(error).__name__}); falling back to {self.fallback}",
                  flush=True)
            self.model_name, self.fallback, self.thinking_level = self.fallback, None, None
            return call(model=self.model_name, contents=contents, config=self.config(generation_config, tools))


def get_agent_model(system_text):
    """Agent tool-loop model: AGENT_MODEL with one fallback to VERTEX_MODEL if it is missing or not permitted."""
    if AGENT_MODEL == MODEL_NAME and AGENT_THINKING_LEVEL == THINKING_LEVEL:
        return get_model(system_text)
    return GenaiModel(system_text, AGENT_MODEL, AGENT_THINKING_LEVEL, fallback=MODEL_NAME)


def get_model(system_text=SYSTEM_PROMPT):
    """Returns a cached model adapter for this system instruction."""
    model = _models.get(system_text)
    if model is None:
        if len(_models) > 32:
            _models.clear()
        model = _models[system_text] = GenaiModel(system_text)
    return model


def make_part(text=None, data=None, mime_type=None):
    from google.genai import types
    return types.Part.from_text(text=text) if data is None else types.Part.from_bytes(data=data, mime_type=mime_type)


def google_search_tool():
    """Google Search grounding (`google_search` tool) for Gemini 2.x / 3.x on Vertex AI."""
    from google.genai import types
    return types.Tool(google_search=types.GoogleSearch())


def make_content(role, parts):
    from google.genai import types
    return types.Content(role=role, parts=parts)


def app_token_matches():
    expected = os.environ.get("DUA_APP_TOKEN", "")
    if not expected:
        return True
    sent = request.headers.get("X-Dua-App-Token", "")
    return hmac.compare_digest(sent.encode("utf-8"), expected.encode("utf-8"))


def unauthorized():
    return jsonify({"error": "This Dua AI engine is private."}), 401


def owner_access_error():
    if not app_token_matches():
        return unauthorized()
    try:
        g.owner_id = verify_owner(request.headers.get("Authorization", ""))
    except OwnerAuthError as error:
        response = jsonify({"error": error.message})
        response.headers["Cache-Control"] = "no-store"
        return response, error.status
    header = request.headers.get("Authorization", "")
    g.owner_token = header.split(" ", 1)[1].strip() if header.lower().startswith("bearer ") else ""
    return None


def decode_attachments(raw):
    if raw is None:
        return []
    if not isinstance(raw, list) or len(raw) > MAX_ATTACHMENTS:
        raise BadRequest("Too many attachments.")
    result = []
    for item in raw:
        if not isinstance(item, dict):
            raise BadRequest("Invalid attachment.")
        mime = str(item.get("mimeType", "")).lower()
        if not mime.startswith(ATTACHMENT_TYPES):
            raise BadRequest("This file type is not supported by the Dua AI engine.", 415)
        try:
            data = base64.b64decode(str(item.get("data", "")), validate=True)
        except (binascii.Error, ValueError):
            raise BadRequest("Invalid attachment.")
        if not data or len(data) > MAX_ATTACHMENT_BYTES:
            raise BadRequest("Attachment is too large.", 413)
        result.append((data, mime))
    return result


def file_context(files):
    """Use supplied text only: Cloud Run cannot dereference private base-backend storage paths."""
    if files is None:
        return ""
    if not isinstance(files, list) or len(files) > 20:
        raise BadRequest("Invalid file metadata.")
    texts = []
    for item in files:
        if not isinstance(item, dict):
            raise BadRequest("Invalid file metadata.")
        for key in ("transcript", "attachmentText", "extractedText", "text"):
            value = item.get(key)
            if isinstance(value, str) and value.strip():
                name = str(item.get("name", "file"))[:200]
                texts.append(f"Attached file ({name}):\n{value.strip()}")
                break
    return "\n\n".join(texts)


def build_contents(messages, attachments):
    """Preserve inline parts on their original user turns, plus any supplied file-text fallback."""
    if not isinstance(messages, list) or not messages or len(messages) > MAX_TURNS:
        raise BadRequest("Invalid message history.")
    turns = []
    for message in messages:
        if not isinstance(message, dict) or message.get("role") not in ("user", "model"):
            raise BadRequest("Invalid message history.")
        text = message.get("text", "")
        if not isinstance(text, str):
            raise BadRequest("Invalid message history.")
        extra = message.get("attachmentText")
        if message["role"] == "user" and isinstance(extra, str) and extra.strip():
            text = (text + "\n\n" + extra).strip()
        inline = []
        if message["role"] == "user":
            fallback = file_context(message.get("files"))
            if fallback:
                text = (text + "\n\n" + fallback).strip()
            inline = decode_attachments(message.get("attachments"))
        if len(text) > MAX_TURN_CHARS:
            raise BadRequest("Message is too long.", 413)
        turns.append([message["role"], text, inline])
    while turns and turns[0][0] != "user":  # Gemini conversations start with the user
        turns.pop(0)
    if not turns or turns[-1][0] != "user":
        raise BadRequest("The last message must be from the user.")
    if not turns[-1][1].strip() and not attachments and not turns[-1][2]:
        raise BadRequest("Message is empty")
    # Merge consecutive same-role turns (e.g. a failed reply was dropped) so roles alternate.
    merged = []
    for role, text, inline in turns:
        if merged and merged[-1][0] == role:
            merged[-1][1] = (merged[-1][1] + "\n\n" + text).strip()
            merged[-1][2].extend(inline)
        else:
            merged.append([role, text, inline])
    contents = []
    for index, (role, text, inline) in enumerate(merged):
        parts = [make_part(text=text or " ")]
        parts += [make_part(data=data, mime_type=mime) for data, mime in inline]
        if index == len(merged) - 1:
            parts += [make_part(data=data, mime_type=mime) for data, mime in attachments]
        contents.append(make_content(role, parts))
    return contents


def system_text(data):
    text = SYSTEM_PROMPT
    memory = data.get("memory")
    if isinstance(memory, str) and memory.strip():
        if len(memory) > MAX_MEMORY_CHARS:
            raise BadRequest("Memory notes are too large.")
        text += "\n\nThings the user asked you to remember:\n" + memory.strip()
    context = data.get("context")
    if isinstance(context, dict) and isinstance(context.get("summary"), str) and context["summary"].strip():
        text += "\n\nSummary of the earlier part of this conversation:\n" + context["summary"][:20000]
    return text


def unsupported_notice(data):
    notes = []
    model = data.get("model")
    if model not in (None, "Dua", "Gemini"):
        notes.append(f"{model} is not available on the Google Cloud engine; Gemini answered instead.")
    if data.get("web") or data.get("xSearch"):
        notes.append("Web search is not available on the Google Cloud engine.")
    if data.get("code"):
        notes.append("Run code is not available on the Google Cloud engine.")
    if data.get("mode") == "build":
        notes.append("Build mode is not available on the Google Cloud engine.")
    return " ".join(notes)[:300] or None


def with_last_user_suffix(messages, suffix):
    """Copy of the app history whose last user turn ends with an agent instruction."""
    copied = [dict(m) if isinstance(m, dict) else m for m in messages]
    if copied and isinstance(copied[-1], dict) and isinstance(copied[-1].get("text"), str):
        copied[-1]["text"] = (copied[-1]["text"] + "\n\n" + suffix).strip()
    return copied


def _domain(url):
    from urllib.parse import urlparse
    try:
        return urlparse(url).hostname or ""
    except ValueError:
        return ""


def sources_footer(sources):
    """Plain-text footer in the exact format of web/src/core/chat-web.mjs sourcesFooter, which the app
    (ReplySources.split) turns into clickable source chips and keeps in saved history."""
    if not sources:
        return ""
    clean = lambda value: " ".join(str(value).split())
    return "\n\nSources:\n" + "\n\n".join(f"[{item['id']}] {clean(item['title'])}\n{item['url']}" for item in sources)


def grounding_sources(response, limit=AGENT_MAX_SOURCES):
    """Web sources from Vertex grounding metadata in the app's `sources` event shape."""
    sources, seen = [], set()
    try:
        chunks = response.candidates[0].grounding_metadata.grounding_chunks
    except (AttributeError, IndexError, TypeError):
        return sources
    for chunk in chunks or []:
        web = getattr(chunk, "web", None)
        url = str(getattr(web, "uri", "") or "")
        if not url.startswith("https://") or url in seen:
            continue
        seen.add(url)
        title = str(getattr(web, "title", "") or "")[:200]
        domain = str(getattr(web, "domain", "") or "") or (title if "." in title and " " not in title else _domain(url))
        sources.append({"id": len(sources) + 1, "title": title or domain or "Source", "url": url, "domain": domain[:253]})
        if len(sources) >= limit:
            break
    return sources


AGENT_ROUTER_TOKENS = int(os.environ.get("AGENT_ROUTER_TOKENS", "256"))
AGENT_ROUTER_TURNS = 6
AGENT_ROUTER_PROMPT = (
    "You are a router for the Dua Agent. Decide if the user's LAST message needs web research. "
    "Answer \"agent\" for real tasks: research, latest news, new AI models/tools, trends, comparisons, "
    "feature or upgrade ideas, plans, reviews, anything that needs current web facts. Answer \"code\" when the user "
    "wants the Dua AI app's code changed (fix a bug, add or change a feature in the app, write code, open a PR). Answer \"chat\" for "
    "greetings, thanks, small talk, short follow-ups or simple questions you can answer without searching. "
    "Reply only with JSON like {\"route\": \"chat\"}."
)
_GREETING = re.compile(
    r"^(hi+|hii+|hello+|hey+|hola|yo|salam|assalam[ou]?\s*alaikum|namaste|good\s+(morning|afternoon|evening|night)|"
    r"thanks?|thank\s+you|thx|ok(ay)?|cool|nice|great|bye|kaise\s+ho|kya\s+haal\s+hai|how\s+are\s+you)\b[\s!.?,]*\w{0,12}[\s!.?]*$",
    re.IGNORECASE)
_TASK_WORDS = re.compile(
    r"\b(research|news|latest|trend\w*|compare|comparison|vs|ideas?|features?|upgrades?|roadmap|tools?|"
    r"search|find|look\s+up|launch\w*|release\w*|what'?s\s+new|kya\s+naya|suggest\w*)\b", re.IGNORECASE)


_CODING_WORDS = re.compile(
    r"\b(fix|bug|bugs|crash\w*|implement|refactor|code\s+likho|likho\s+code|pull\s+request|open\s+a\s+pr|"
    r"make\s+a\s+pr|kotlin|jetpack|change\s+the\s+code|add\s+.{1,60}\s+(to|in)\s+(the|my|dua)\s+(app|repo|code))\b",
    re.IGNORECASE)


def last_user_text(messages):
    for message in reversed(messages or []):
        if isinstance(message, dict) and message.get("role") == "user":
            return str(message.get("text", "")).strip()
    return ""


def route_agent(messages, attachments, system):
    """Returns "chat" (direct reply, no Search) or "agent" (full pipeline).

    Free checks first (greetings / clear task words), then one cheap model call (low thinking, small output,
    JSON). Any router failure falls back to a heuristic: long messages -> agent, short ones -> chat."""
    text = last_user_text(messages)
    if not text or _GREETING.match(text):
        return "chat"
    if _CODING_WORDS.search(text):
        return "code"
    if _TASK_WORDS.search(text):
        return "agent"
    fallback = "agent" if len(text.split()) > 12 else "chat"
    try:
        response = get_model(system).generate_content(
            build_contents(with_last_user_suffix(messages[-AGENT_ROUTER_TURNS:], AGENT_ROUTER_PROMPT), attachments),
            generation_config={"max_output_tokens": AGENT_ROUTER_TOKENS, "temperature": 0.0, "thinking_level": "low",
                               "response_mime_type": "application/json",
                               "response_schema": {"type": "OBJECT", "properties": {"route": {"type": "STRING", "enum": ["chat", "agent", "code"]}},
                                                   "required": ["route"]}})
        route = json.loads(chunk_text(response) or "{}").get("route")
        return route if route in ("chat", "agent", "code") else fallback
    except Exception:
        app.logger.warning("agent router failed; using fallback route")
        return fallback


CODING_NOT_SET_UP = (
    "Coding mode abhi set up nahi hai. To let the Dua Agent open pull requests, add a GitHub token "
    "to the Cloud Run service (see google-cloud-backend/README.md, \"Coding agent setup\"). Until then I can "
    "still chat and research."
)


class _Text:
    def __init__(self, text):
        self.text, self.candidates = text, []


def progress_event(step, label, state="active"):
    """Structured agent progress for the app's step UI. Older clients ignore unknown event types."""
    return {"type": "progress", "step": step, "label": label, "state": state}


def imagine_for_agent(owner):
    """Agent generate_image: reuses the Cloud Run Imagine path (owner storage + daily cap); None when off."""
    import imagine
    if not imagine.enabled() or not owner.get("id") or not owner.get("token"):
        return None
    return lambda body: imagine.handle(body, owner["token"], owner["id"], get_client())


NOTES_SYSTEM = "You extract short, durable, non-sensitive lessons for an AI agent's private notes. Reply with JSON only."


def tool_loop(messages, attachments, system, enqueue, owner, coding=False):
    import agent_notes
    import agent_tools
    import coding_agent
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if coding and not token:
        enqueue("chunk", _Text(CODING_NOT_SET_UP))
        enqueue("done")
        return
    github, repo = None, ""
    try:
        if token:
            repo = coding_agent.repo_name()
            github = coding_agent.GitHubClient(token, repo)
    except coding_agent.CodingError:
        github = None
    issues = github is not None and github.issues_enabled()
    hint = (agent_tools.GITHUB_HINT.format(repo=repo) if github else "") + (agent_tools.ISSUES_HINT.format(repo=repo) if issues else "")
    prompt = agent_tools.AGENT_PROMPT.format(github=hint)
    if coding and github:
        prompt += "\n\n" + coding_agent.CODING_PROMPT.format(repo=repo, max_files=coding_agent.MAX_FILES_CHANGED)
    last = last_user_text(messages)
    notes = agent_notes.for_owner(owner)
    if notes is not None:
        prompt += agent_tools.NOTES_HINT + agent_notes.load_prompt(notes, last)
    model = get_agent_model(system + "\n\n" + prompt)
    if not enqueue("event", progress_event("plan", "Planning\u2026")):
        return
    env = {
        "model": model, "enqueue": enqueue, "chunk_text": chunk_text, "grounding_sources": grounding_sources,
        "search_tool": google_search_tool, "code_tool": agent_tools.code_tool, "fetch": agent_tools.fetch_page,
        "contents_for": lambda text: [make_content("user", [make_part(text=text)])],
        "imagine": imagine_for_agent(owner), "memory": owner.get("memory", ""), "github": github, "request_text": last, "notes": notes,
        "progress": lambda step, label, state="active": enqueue("event", progress_event(step, label, state)),
        "thinking": lambda delta: enqueue("event", {"type": "thinking", "delta": delta}),
    }
    try:
        text, run = agent_tools.run(env, build_contents(messages, attachments), (agent_tools.default_tools(github is not None, issues, True) if notes is not None
                                     else agent_tools.default_tools(github is not None, issues)),
                                    coding_agent.default_tool_response)
    except coding_agent.CodingError as error:
        text, run = f"Stopped: {str(error)[:300]} No pull request was opened.", None
    sources = run.sources if run else []
    if sources and not enqueue("event", {"type": "sources", "sources": sources}):
        return
    if not enqueue("event", progress_event("answer", "Writing the answer\u2026")):
        return
    if not enqueue("chunk", _Text(text)):
        return
    footer = sources_footer(sources)
    if footer and not enqueue("event", {"type": "text", "delta": footer}):
        return
    enqueue("done")
    if notes is not None and run is not None:  # after the reply: one cheap Flash call on VERTEX_MODEL
        agent_notes.learn(notes, get_model(NOTES_SYSTEM), last, "".join(run.steps), text)


def agent_producer(messages, attachments, system, owner=None):
    """Dua Agent: route -> (direct reply) or one general tool-calling loop (agent_tools)."""
    def produce(enqueue):
        model = get_model(system)
        route = route_agent(messages, attachments, system)
        if route == "chat":
            # Casual message: one normal streamed reply with full history, no Search, no plan or sources.
            for chunk in model.generate_content(build_contents(messages, attachments),
                                                generation_config={"max_output_tokens": AGENT_ANSWER_TOKENS}, stream=True):
                if not enqueue("chunk", chunk):
                    return
            enqueue("done")
            return
        tool_loop(messages, attachments, system, enqueue, owner or {}, coding=route == "code")
    return produce


def sse(event):
    return f"event: {event['type']}\ndata: {json.dumps(event, ensure_ascii=False)}\n\n"


def is_truncated(chunk):
    try:
        reason = chunk.candidates[0].finish_reason
        return getattr(reason, "name", str(reason)) == "MAX_TOKENS"
    except (AttributeError, IndexError, TypeError):
        return False


def chunk_text(chunk):
    try:
        return chunk.text or ""
    except (AttributeError, ValueError):  # blocked or empty candidate
        return ""


@app.route("/", methods=["GET"])
def home():
    # Capabilities: the app shows Agent mode only when this says "agent": true.
    import openrouter
    info = {"status": "Dua AI Engine is running!", "engine": "google-cloud", "agent": True, "titles": True, "model": MODEL_NAME,
            "agentModel": AGENT_MODEL}
    # Free OpenRouter models appear only when OPENROUTER_API_KEY is set (Secret Manager).
    info["freeModels"] = openrouter.models()
    return jsonify(info)


@app.route("/api/chat", methods=["POST"])
def api_chat():
    denied = owner_access_error()
    if denied is not None:
        return denied
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify({"error": "Invalid request."}), 400
    if data.get("persistence") is not None:
        return jsonify({"error": "Server reply saving is not available on the Google Cloud engine."}), 400
    if data.get("model") is not None and data.get("model") not in APP_MODELS:
        return jsonify({"error": "Supported model select karein."}), 400
    try:
        attachments = decode_attachments(data.get("attachments"))
        contents = build_contents(data.get("messages"), attachments)
        system = system_text(data)
    except BadRequest as error:
        return jsonify({"error": error.message}), error.status

    agent = data.get("mode") == "agent"
    import openrouter
    free_model = data.get("openrouter")
    if free_model is not None and (not openrouter.enabled() or not openrouter.allowed(free_model)):
        return jsonify({"error": "This free model is not available. Choose another model."}), 400
    # Free models answer plain chat only; Agent, web, code and Build stay on Gemini.
    use_free = free_model is not None and data.get("stream") is True and not agent and data.get("mode") in (None, "auto", "reasoning") \
        and not data.get("web") and not data.get("code")
    if agent and data.get("stream") is not True:
        return jsonify({"error": "Agent mode needs streaming. Update the app."}), 400
    if data.get("stream") is not True:
        try:
            response = get_model(system).generate_content(contents)
            text = chunk_text(response)
        except Exception:
            app.logger.exception("api chat failed")
            return jsonify({"error": GENERIC_ERROR}), 502
        if not text.strip():
            return jsonify({"error": GENERIC_ERROR}), 502
        return jsonify({"text": text})

    notice = unsupported_notice(data)
    if use_free:
        has_files = bool(attachments) or any(isinstance(m, dict) and m.get("attachments") for m in data.get("messages") or [])
        notice = "Free models read text only; attached images and audio were not sent." if has_files else None

    owner = {"id": getattr(g, "owner_id", None), "token": getattr(g, "owner_token", ""),
             "memory": data.get("memory") if isinstance(data.get("memory"), str) else ""}

    def events():
        chunks = queue.Queue(maxsize=16)
        stopped = threading.Event()

        def enqueue(kind, chunk=None):
            while not stopped.is_set():
                try:
                    chunks.put((kind, chunk), timeout=0.5)
                    return True
                except queue.Full:
                    continue
            return False

        def vertex_stream():
            iterator = None
            try:
                if agent:
                    agent_producer(data.get("messages"), attachments, system, owner)(enqueue)
                    return
                if use_free:
                    openrouter.stream(free_model, system, data.get("messages"), enqueue, reasoning=data.get("mode") == "reasoning")
                    return
                iterator = iter(get_model(system).generate_content(contents, stream=True))
                for chunk in iterator:
                    if not enqueue("chunk", chunk):
                        break
                enqueue("done")
            except Exception:
                app.logger.exception("api chat stream failed")
                enqueue("error")
            finally:
                if iterator is not None and hasattr(iterator, "close"):
                    try:
                        iterator.close()
                    except Exception:
                        app.logger.exception("Vertex stream cleanup failed")

        threading.Thread(target=vertex_stream, name="dua-vertex-stream", daemon=True).start()
        truncated = False
        sent = False
        try:
            # Flush headers promptly; the worker also covers delays obtaining the iterator itself.
            yield ": heartbeat\n\n"
            if notice:
                yield sse({"type": "notice", "message": notice})
            while True:
                try:
                    kind, chunk = chunks.get(timeout=HEARTBEAT_SECONDS)
                except queue.Empty:
                    yield ": heartbeat\n\n"
                    continue
                if kind == "error":
                    yield sse({"type": "error", "message": GENERIC_ERROR, "status": 502})
                    return
                if kind == "fail":  # friendly, already-safe message (e.g. free model rate limit)
                    message, status = chunk
                    yield sse({"type": "error", "message": message, "status": status})
                    return
                if kind == "done":
                    truncated = truncated or chunk is True
                    break
                if kind == "event":
                    sent = sent or chunk.get("type") == "text"
                    yield sse(chunk)
                    continue
                truncated = truncated or is_truncated(chunk)
                text = chunk_text(chunk)
                if text:
                    sent = True
                    yield sse({"type": "text", "delta": text})
                else:
                    # Metadata-only chunks must not reset queue polling without emitting any bytes.
                    yield ": heartbeat\n\n"
            if not sent:
                yield sse({"type": "error", "message": GENERIC_ERROR, "status": 502})
                return
            yield sse({"type": "done", "truncated": truncated})
        finally:
            # On client disconnect, stop enqueuing and close the iterator on its owning worker.
            stopped.set()

    return Response(stream_with_context(events()), headers=SSE_HEADERS,
                    content_type="text/event-stream; charset=utf-8")


@app.after_request
def add_imagine_capability(response):
    """Adds `"imagine": {...}` to the GET / capabilities (kept next to the Imagine endpoint)."""
    if request.endpoint == "home" and request.method == "GET" and response.is_json:
        import imagine
        data = response.get_json()
        if isinstance(data, dict):
            data["imagine"] = imagine.capability()
            response.set_data(json.dumps(data))
    return response


@app.route("/api/imagine/image", methods=["POST"])
def api_imagine_image():
    """Imagine on Vertex AI (one image per request, owner only). Same response shape as the Node API endpoint."""
    import imagine
    if not app_token_matches():
        return unauthorized()
    try:
        user_id = verify_owner(request.headers.get("Authorization", ""))
    except OwnerAuthError as error:
        response = jsonify({"error": error.message})
        response.headers["Cache-Control"] = "no-store"
        return response, error.status
    if not imagine.enabled():
        return jsonify({"error": "Imagine is turned off on the Google Cloud engine."}), 503
    token = request.headers.get("Authorization", "").split(" ", 1)[1]
    try:
        result = imagine.handle(request.get_json(silent=True), token, user_id, get_client())
    except imagine.ImagineError as error:
        return jsonify({"error": error.message}), error.status
    except Exception:
        app.logger.exception("imagine failed")
        return jsonify({"error": "Image generation failed. Try again."}), 502
    response = jsonify(result)
    response.headers["Cache-Control"] = "no-store"
    return response


TITLE_TOKENS = int(os.environ.get("TITLE_TOKENS", "256"))
TITLE_PROMPT = ("Write a short title (3 to 6 words) for this chat, in the language of the user's message. "
                "No quotes, no emoji, no ending punctuation. Reply only with JSON like {\"title\": \"...\"}.")


def clean_title(value, limit=60):
    text = " ".join(str(value or "").replace("\u2028", " ").replace("\u2029", " ").split())
    text = text.strip(" \"'`*#.:;-")
    words = text.split()
    if len(words) > 8:
        text = " ".join(words[:8])
    if len(text) > limit:
        text = text[:limit].rsplit(" ", 1)[0] or text[:limit]
    return "".join(ch for ch in text if ch.isprintable())


@app.route("/api/title", methods=["POST"])
def api_title():
    """Short chat title for the drawer: one cheap call (low thinking, small cap, JSON), owner-only."""
    denied = owner_access_error()
    if denied is not None:
        return denied
    data = request.get_json(silent=True) or {}
    user = str(data.get("user", "")).strip()[:4000]
    reply = str(data.get("reply", "")).strip()[:4000]
    if not user:
        return jsonify({"error": "Message is empty"}), 400
    try:
        response = get_model(SYSTEM_PROMPT).generate_content(
            f"{TITLE_PROMPT}\n\nUser: {user}\n\nAssistant: {reply}",
            generation_config={"max_output_tokens": TITLE_TOKENS, "temperature": 0.2, "thinking_level": "low",
                               "response_mime_type": "application/json",
                               "response_schema": {"type": "OBJECT", "properties": {"title": {"type": "STRING"}}, "required": ["title"]}})
        title = clean_title(json.loads(chunk_text(response) or "{}").get("title"))
    except Exception:
        app.logger.warning("title generation failed")
        return jsonify({"error": GENERIC_ERROR}), 502
    if not title:
        return jsonify({"error": GENERIC_ERROR}), 502
    return jsonify({"title": title})


@app.route("/chat", methods=["POST"])
def chat():
    denied = owner_access_error()
    if denied is not None:
        return denied
    data = request.get_json(silent=True) or {}
    user_message = str(data.get("message", "")).strip()
    if not user_message:
        return jsonify({"error": "Message is empty"}), 400
    if len(user_message) > MAX_MESSAGE_CHARS:
        return jsonify({"error": "Message is too long"}), 413
    try:
        response = get_model().generate_content(user_message)
        return jsonify({"reply": response.text})
    except Exception:  # don't leak internal error details to clients
        app.logger.exception("chat failed")
        return jsonify({"error": "The Dua AI engine could not answer right now."}), 500


@app.errorhandler(413)
def too_large(_error):
    return jsonify({"error": "Message history is too large."}), 413


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
