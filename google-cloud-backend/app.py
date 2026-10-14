"""Dua AI engine for Google Cloud Run (Vertex AI Gemini).

Endpoints
- GET  /            health check
- POST /api/chat    drop-in replacement for the web/ backend's chat endpoint used by the Android app:
                    full conversation history, inline attachments, SSE streaming (`"stream": true`)
                    with the same wire format as web/src/core/sse.mjs, or one JSON reply `{"text": ...}`.
                    `"mode": "agent"` runs the Dua Agent (phase 1): plan -> one Google Search grounded
                    research step -> summary with sources -> prioritized Dua AI feature ideas.
- POST /chat        old single-turn endpoint: {"message"} -> {"reply"} (kept for backward compat).

Configuration comes only from environment variables; no secrets in code.
  GOOGLE_CLOUD_PROJECT (required), VERTEX_LOCATION (default global),
  VERTEX_MODEL (default gemini-3.8-flash), VERTEX_THINKING_LEVEL (Gemini 3: low|medium|high, default low),
  SUPABASE_URL, SUPABASE_ANON_KEY (or SUPABASE_PUBLISHABLE_KEY), DUA_ALLOWED_EMAIL (required owner authentication).
  DUA_APP_TOKEN is an optional additional gate, never a replacement for owner login.
"""
import base64
import binascii
import hmac
import json
import os
import queue
import threading

from flask import Flask, Response, jsonify, request, stream_with_context
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
MAX_MESSAGE_CHARS = 8000
MAX_TURNS = 400
MAX_TURN_CHARS = 100_000
MAX_MEMORY_CHARS = 3000
MAX_ATTACHMENTS = 10
MAX_ATTACHMENT_BYTES = 4 * 1024 * 1024
HEARTBEAT_SECONDS = 15
ATTACHMENT_TYPES = ("image/", "application/pdf", "text/", "audio/", "video/")
# Models the app may name; this engine always answers with Gemini on Vertex AI.
APP_MODELS = {"Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi"}

SYSTEM_PROMPT = "You are Dua AI, a helpful, smart, and polite personal AI assistant."
GENERIC_ERROR = "The Dua AI engine could not answer right now. Please try again."

# Dua Agent cost caps: exactly 3 model calls per agent request and only ONE of them uses
# Google Search grounding (billed per grounded prompt after the free daily quota).
# Output caps include thinking tokens on Gemini 2.5/3, so leave room for low-level thinking.
AGENT_PLAN_TOKENS = int(os.environ.get("AGENT_PLAN_TOKENS", "1024"))
AGENT_RESEARCH_TOKENS = int(os.environ.get("AGENT_RESEARCH_TOKENS", "3072"))
AGENT_ANSWER_TOKENS = int(os.environ.get("AGENT_ANSWER_TOKENS", "4096"))
AGENT_MAX_SOURCES = 12
AGENT_PLAN_PROMPT = (
    "You are the Dua Agent planning step. Do not answer yet. In at most 5 short bullet points, write a research "
    "plan for the user's request: which latest AI news, models, tools or app features to look up on the web. "
    "Keep it simple, Hinglish-friendly English."
)
AGENT_RESEARCH_PROMPT = (
    "You are the Dua Agent research step. Use Google Search to find the LATEST, real information for this plan. "
    "Write compact factual notes (dates, names, what is new). No feature ideas yet.\n\nPlan:\n"
)
AGENT_ANSWER_PROMPT = (
    "You are the Dua Agent. Dua AI is the user's own ChatGPT-like Android app (Kotlin, Jetpack Compose) with a "
    "Google Cloud Run + Vertex AI Gemini backend. The user is a student who cannot code and wants free tools. "
    "Using ONLY the research notes below, reply in simple, Hinglish-friendly English with these sections:\n"
    "1. **Kya naya hai (findings)** - short bullets, cite sources like [1], [2].\n"
    "2. **Dua AI ke liye ideas** - a numbered list ordered by priority. For each idea: what it is, why it helps, "
    "effort (Small / Medium / Large) and whether it can stay free.\n"
    "3. **Next step** - the one thing to do first.\n"
    "If the notes do not cover something, say so instead of guessing."
)

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


def thinking_config(model_name=None):
    """Gemini 3 models take thinking_level; older models keep their default thinking."""
    if not (model_name or MODEL_NAME).startswith("gemini-3"):
        return None
    from google.genai import types
    return types.ThinkingConfig(thinking_level=THINKING_LEVEL.upper())


class GenaiModel:
    """Small adapter with the generate_content(contents, stream, generation_config, tools) shape used here."""

    def __init__(self, system):
        self.system = system

    def config(self, generation_config=None, tools=None):
        from google.genai import types
        options = {"system_instruction": self.system, **(generation_config or {})}
        if tools:
            options["tools"] = tools
        thinking = thinking_config()
        if thinking is not None:
            options["thinking_config"] = thinking
        return types.GenerateContentConfig(**options)

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        models = get_client().models
        config = self.config(generation_config, tools)
        if stream:
            return models.generate_content_stream(model=MODEL_NAME, contents=contents, config=config)
        return models.generate_content(model=MODEL_NAME, contents=contents, config=config)


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
        verify_owner(request.headers.get("Authorization", ""))
    except OwnerAuthError as error:
        response = jsonify({"error": error.message})
        response.headers["Cache-Control"] = "no-store"
        return response, error.status
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


def agent_producer(messages, attachments, system):
    """Dua Agent: plan -> grounded research -> streamed answer. Emits progress as `thinking`."""
    def produce(enqueue):
        model = get_model(system)
        if not enqueue("event", {"type": "thinking", "delta": "**Step 1 - Plan**\n"}):
            return
        plan = chunk_text(model.generate_content(
            build_contents(with_last_user_suffix(messages, AGENT_PLAN_PROMPT), attachments),
            generation_config={"max_output_tokens": AGENT_PLAN_TOKENS, "temperature": 0.3}))[:4000]
        if not enqueue("event", {"type": "thinking", "delta": (plan or "- Research latest AI news and tools") + "\n\n**Step 2 - Research (Google Search)**\n"}):
            return
        research = model.generate_content(
            build_contents(with_last_user_suffix(messages, AGENT_RESEARCH_PROMPT + plan), attachments),
            generation_config={"max_output_tokens": AGENT_RESEARCH_TOKENS, "temperature": 0.2},
            tools=[google_search_tool()])
        notes = chunk_text(research)[:20000]
        sources = grounding_sources(research)
        if not enqueue("event", {"type": "thinking", "delta": f"Found {len(sources)} sources.\n\n**Step 3 - Summary and ideas**\n"}):
            return
        if sources and not enqueue("event", {"type": "sources", "sources": sources}):
            return
        listed = "\n".join(f"[{item['id']}] {item['title']} - {item['url']}" for item in sources) or "(no web sources found)"
        answer_prompt = f"{AGENT_ANSWER_PROMPT}\n\nResearch notes:\n{notes or '(empty)'}\n\nSources:\n{listed}"
        for chunk in model.generate_content(
                build_contents(with_last_user_suffix(messages, answer_prompt), attachments),
                generation_config={"max_output_tokens": AGENT_ANSWER_TOKENS, "temperature": 0.4}, stream=True):
            if not enqueue("chunk", chunk):
                return
        footer = sources_footer(sources)
        if footer and not enqueue("event", {"type": "text", "delta": footer}):
            return
        enqueue("done")
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
    return jsonify({"status": "Dua AI Engine is running!", "engine": "google-cloud", "agent": True, "model": MODEL_NAME})


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
                    agent_producer(data.get("messages"), attachments, system)(enqueue)
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
                if kind == "done":
                    break
                if kind == "event":
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
