"""Optional Dua AI engine for Google Cloud Run (Vertex AI Gemini).

Not used by the Android app by default (the app talks to the web/ backend).
Configuration comes only from environment variables; no secrets in code.
"""
import os

from flask import Flask, jsonify, request
import vertexai
from vertexai.generative_models import GenerativeModel

app = Flask(__name__)

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "")
LOCATION = os.environ.get("VERTEX_LOCATION", "us-central1")
MODEL_NAME = os.environ.get("VERTEX_MODEL", "gemini-1.5-flash")
MAX_MESSAGE_CHARS = 8000

SYSTEM_PROMPT = "You are Dua AI, a helpful, smart, and polite personal AI assistant."

_model = None


def get_model():
    global _model
    if _model is None:
        if not PROJECT_ID:
            raise RuntimeError("GOOGLE_CLOUD_PROJECT is not set")
        vertexai.init(project=PROJECT_ID, location=LOCATION)
        _model = GenerativeModel(MODEL_NAME, system_instruction=[SYSTEM_PROMPT])
    return _model


@app.route("/", methods=["GET"])
def home():
    return jsonify({"status": "Dua AI Engine is running!"})


@app.route("/chat", methods=["POST"])
def chat():
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


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
