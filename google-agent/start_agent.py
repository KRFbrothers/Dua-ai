"""Phone-friendly private ADK launcher with explicit preview origin and safe preflight."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse
from dotenv import dotenv_values


def origin(value):
    parsed = urlparse(value)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.port not in (None, 443) or parsed.path not in ("", "/")
            or parsed.query or parsed.fragment or "*" in value
            or not parsed.hostname.endswith(".cloudshell.dev")):
        raise argparse.ArgumentTypeError("Use your exact HTTPS Cloud Shell preview origin, without /dev-ui/ or query.")
    return "https://" + parsed.netloc


def load_settings(directory):
    # Saved values win over stale exports; empty placeholders do not erase a
    # deliberately exported session token. Never print either source.
    for key, value in dotenv_values(directory / "dua_coder" / ".env").items():
        if value and key in {"GOOGLE_GENAI_USE_VERTEXAI", "GOOGLE_CLOUD_PROJECT", "GOOGLE_CLOUD_LOCATION",
                            "DUA_AGENT_MODEL", "DUA_GITHUB_TOKEN", "DUA_GITHUB_TOKEN_SECRET"}:
            os.environ[key] = value


def preflight():
    if not (os.environ.get("DUA_GITHUB_TOKEN") or os.environ.get("DUA_GITHUB_TOKEN_SECRET")):
        return "GitHub token missing. Save it privately in dua_coder/.env; do not paste it into chat."
    if not os.environ.get("GOOGLE_CLOUD_PROJECT"):
        return "Google project missing. Set GOOGLE_CLOUD_PROJECT in the private .env file."
    try:
        import google.auth
        from google.auth.transport.requests import Request
        credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
        credentials.refresh(Request())
    except Exception:
        return ("Google credential refresh failed. In Cloud Shell run gcloud auth application-default login, "
                "choose Y if prompted to proceed despite Cloud Shell credentials, then set the quota project. "
                "No service-account key file is needed. Existing explicit credential configuration may need review.")
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview-origin", required=True, type=origin)
    parser.add_argument("--port", type=int, default=8081)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("Port must be between 1024 and 65535.")
    directory = Path(__file__).resolve().parent
    load_settings(directory)
    error = preflight()
    if error:
        print(error, file=sys.stderr)
        return 1
    print(f"Starting private ADK preview on port {args.port}. Model/runtime usage can be billed.")
    try:
        return subprocess.call([sys.executable, "-m", "google.adk.cli", "web", "--host", "127.0.0.1",
            "--port", str(args.port), "--max_llm_calls", "15", "--allow_origins", args.preview_origin, "."], cwd=directory)
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
