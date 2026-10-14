"""Dua AI engine for Google Cloud Run (Vertex AI Gemini).

Endpoints
- GET  /            health check
- POST /api/chat    drop-in replacement for the web/ backend's chat endpoint used by the Android app:
                    full conversation history, inline attachments, SSE streaming (`"stream": true`)
                    with the same wire format as web/src/core/sse.mjs, or one JSON reply `{"text": ...}`.
- POST /chat        old single-turn endpoint: {"message"} -> {"reply"} (kept for backward compat).

Configuration comes only from environment variables; no secrets in code.
  GOOGLE_CLOUD_PROJECT (required), VERTEX_LOCATION (default us-central1),
  VERTEX_MODEL (default gemini-2.5-flash), DUA_APP_TOKEN (optional shared secret; when set,
  requests must send the same value in the X-Dua-App-Token header).
"""
import base64
import binascii
import hmac
import json
import os
import queue
import threading

from flask import Flask, Response, jsonify, request, stream_with_context

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 5 * 1024 * 1024

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "")
LOCATION = os.environ.get("VERTEX_LOCATION", "us-central1")
MODEL_NAME = os.environ.get("VERTEX_MODEL", "gemini-2.5-flash")
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

SSE_HEADERS = {
    "Cache-Control": "no-store",
    "X-Accel-Buffering": "no",
    "X-Content-Type-Options": "nosniff",
}

_models = {}


class BadRequest(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.message = message
        self.status = status


def get_model(system_text=SYSTEM_PROMPT):
    """Returns a cached Vertex AI GenerativeModel for this system instruction."""
    model = _models.get(system_text)
    if model is None:
        if not PROJECT_ID:
            raise RuntimeError("GOOGLE_CLOUD_PROJECT is not set")
        import vertexai
        from vertexai.generative_models import GenerativeModel
        vertexai.init(project=PROJECT_ID, location=LOCATION)
        model = GenerativeModel(MODEL_NAME, system_instruction=[system_text])
        if len(_models) > 32:
            _models.clear()
        _models[system_text] = model
    return model


def make_part(text=None, data=None, mime_type=None):
    from vertexai.generative_models import Part
    return Part.from_text(text) if data is None else Part.from_data(data=data, mime_type=mime_type)


def make_content(role, parts):
    from vertexai.generative_models import Content
    return Content(role=role, parts=parts)


def authorized():
    expected = os.environ.get("DUA_APP_TOKEN", "")
    if not expected:
        return True
    sent = request.headers.get("X-Dua-App-Token", "")
    return hmac.compare_digest(sent.encode("utf-8"), expected.encode("utf-8"))


def unauthorized():
    return jsonify({"error": "This Dua AI engine is private."}), 401


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
    return jsonify({"status": "Dua AI Engine is running!"})


@app.route("/api/chat", methods=["POST"])
def api_chat():
    if not authorized():
        return unauthorized()
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
    if not authorized():
        return unauthorized()
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
