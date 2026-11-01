"""Engine-side client for the separately administered Docker coding runner.

Only server configuration supplies its HTTPS endpoint/token. No process or
Docker socket is opened in the chat engine; failures never use a local shell.
"""
import base64
import hashlib
import json
import os
import re
import secrets
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

import dua_engine.coding.coding_agent as coding
import dua_engine.coding.coding_snapshot as coding_snapshot
import dua_engine.coding.coding_profiles as coding_profiles

MAX_OUTPUT_BYTES = 32_000
MAX_COMMAND_SECONDS = 120
TERMINAL_TOOL_SECONDS = 240
MAX_RESPONSE_BYTES = 1_100_000
JOB_ID = re.compile(r"[0-9a-f]{32}")
FINAL_STATES = frozenset(("succeeded", "failed", "timed_out", "cancelled", "error"))


def configuration():
    endpoint = os.environ.get("DUA_CODING_RUNNER_URL", "").strip()
    token = os.environ.get("DUA_CODING_RUNNER_TOKEN", "").strip()
    if not endpoint and not token:
        return None
    try:
        parts = urlsplit(endpoint)
    except ValueError:
        raise coding.CodingError("Coding runner endpoint is invalid.") from None
    if (parts.scheme != "https" or not parts.hostname or parts.username is not None or parts.password is not None or
            parts.query or parts.fragment or parts.path not in ("", "/") or len(token) < 32 or
            not token.isascii() or any(ch.isspace() for ch in token) or
            any(ch.isspace() for ch in endpoint) or len(endpoint) > 2048):
        raise coding.CodingError("Coding runner configuration is invalid. Configure an HTTPS origin and dedicated token.")
    return endpoint.rstrip("/"), token


def status():
    try:
        ready = configuration() is not None
    except (ValueError, coding.CodingError):
        return {"configured": False, "reason": "invalid_configuration", "liveVerified": False}
    return {"configured": ready, "network": "none", "persistence": "ephemeral", "profileDiscoveryTool": "list_runner_profiles",
            "defaultProfile": "python", "liveVerified": False}


def validate_command(args):
    argv, cwd, seconds = args.get("argv"), args.get("cwd", ""), args.get("timeout_seconds", 30)
    if (not isinstance(argv, list) or not 1 <= len(argv) <= 64 or
            any(not isinstance(arg, str) or "\0" in arg or len(arg.encode("utf-8")) > 4096 for arg in argv) or
            not argv[0] or argv[0].startswith("-") or sum(len(arg.encode("utf-8")) for arg in argv) > 16_000):
        raise coding.CodingError("argv must contain 1-64 bounded command arguments.")
    if not isinstance(cwd, str) or (cwd and not coding.valid_path(cwd)):
        raise coding.CodingError("cwd must be an empty string or a relative repository directory.")
    if isinstance(seconds, bool) or not isinstance(seconds, int) or not 1 <= seconds <= MAX_COMMAND_SECONDS:
        raise coding.CodingError("timeout_seconds must be an integer from 1 to 120.")
    return argv, cwd, seconds


class Client:
    def __init__(self, endpoint, token):
        self.endpoint, self.token = endpoint, token
        self.scope = secrets.token_hex(16)
        self.opener = build_opener(type("NoRunnerRedirect", (HTTPRedirectHandler,),
                                   {"redirect_request": lambda *a, **k: None}))

    def request(self, method, path, payload=None):
        if not (path == "/v1/capabilities" and method == "GET") and not (path == "/v1/runs" and method == "POST") and not (
                method in ("GET", "DELETE") and re.fullmatch(r"/v1/runs/[0-9a-f]{32}", path)):
            raise coding.CodingError("Invalid runner operation.")
        data = json.dumps(payload, separators=(",", ":")).encode("utf-8") if payload is not None else None
        request = Request(self.endpoint + path, data=data, method=method, headers={
            "Authorization": "Bearer " + self.token, "X-Dua-Run-Scope": self.scope,
            "Content-Type": "application/json", "Accept": "application/json"})
        try:
            with self.opener.open(request, timeout=15) as response:
                raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                raise coding.CodingError("Runner returned an oversized response.")
            result = json.loads(raw)
            if not isinstance(result, dict):
                raise ValueError()
            return result
        except HTTPError as error:
            if error.code == 429:
                raise coding.CodingError("Coding runner is busy. Try the command again later.") from None
            raise coding.CodingError("Coding runner refused the request or is unavailable.") from None
        except (OSError, ValueError):
            raise coding.CodingError("Coding runner could not be reached or returned invalid data.") from None


def list_profiles():
    config = configuration()
    if config is None:
        return {"configured": False, "profiles": [], "liveVerified": False,
                "note": "Set up the separate runner first. No toolchain readiness has been verified."}
    result = coding_profiles.validate_catalog(Client(*config).request("GET", "/v1/capabilities"))
    return dict(result, configured=True, note="Configured image contracts only, not live verification. Dependencies must be preinstalled; networking stays disabled.")


def run(workspace, args, cancelled=None, progress=None):
    import dua_engine.coding.coding_artifacts as coding_artifacts
    argv, cwd, seconds = validate_command(args)
    profile = coding_profiles.name(args.get("profile", "python"))
    exports = coding_artifacts.export_paths(args)
    config = configuration()
    if config is None:
        raise coding.CodingError("Repository terminal is not configured. Set up the separate coding runner first.")
    started = time.monotonic()
    # Leave time for runner cancellation before the outer tool deadline expires.
    deadline = started + TERMINAL_TOOL_SECONDS - 40
    stop = lambda: (cancelled is not None and cancelled()) or time.monotonic() >= deadline
    client = Client(*config)
    expected_digest = None
    if profile != "python":
        catalog = coding_profiles.validate_catalog(client.request("GET", "/v1/capabilities"))
        selected = next((row for row in catalog["profiles"] if row["id"] == profile), None)
        if selected is None:
            raise coding.CodingError("Requested toolchain profile is not configured. Use list_runner_profiles; no command was submitted.")
        expected_digest = selected["imageDigest"]
    if stop():
        raise coding.CodingError("Command cancelled before source preparation.")
    with workspace.lock:
        workspace.start()
        target = (cwd + "/" if cwd else "") + "__command__"
        if any(p not in workspace.instructions_seen for p in workspace.instruction_paths(target)):
            raise coding.CodingError("Read scoped AGENTS.md first: workspace_status with a file path inside cwd.")
        for path in exports:
            workspace.check_path(path, write=True)
            if any(p not in workspace.instructions_seen for p in workspace.instruction_paths(path)):
                raise coding.CodingError("Read scoped AGENTS.md for every export path before running a modifying command.")
    source, fingerprint = coding_snapshot.from_workspace(workspace, stop)
    digest = hashlib.sha256(source).hexdigest()
    if stop():
        raise coding.CodingError("Command cancelled before runner submission.")
    # The client-generated ID lets cancellation target the job even if the POST
    # response is lost after the runner accepted it. POST is never retried.
    job_id, completed = secrets.token_hex(16), False
    path = "/v1/runs/" + job_id
    try:
        payload = {"id": job_id, "archive": base64.b64encode(source).decode("ascii"),
            "snapshot_sha256": digest, "base_sha": workspace.base_sha, "argv": argv, "cwd": cwd,
            "timeout_seconds": seconds, "export_paths": exports}
        # Keep default Python requests compatible with the original controller.
        # Non-Python requests always require explicit discovery/profile binding.
        if profile != "python":
            payload["profile"] = profile
        result = client.request("POST", "/v1/runs", payload)
        while True:
            if stop():
                raise coding.CodingError("Command stopped. Runner cancellation was requested.")
            if (result.get("id") != job_id or result.get("snapshot_sha256") != digest or
                    result.get("base_sha") != workspace.base_sha):
                raise coding.CodingError("Runner result did not match the submitted snapshot.")
            if result.get("profile", "python") != profile:
                raise coding.CodingError("Runner returned a different toolchain profile.")
            image_digest = result.get("image_digest")
            if (image_digest is not None and (not isinstance(image_digest, str) or not coding_profiles.DIGEST.fullmatch(image_digest))) or (
                    expected_digest is not None and image_digest != expected_digest):
                raise coding.CodingError("Runner image changed or its identity could not be verified. Rediscover profiles before trying again.")
            state = result.get("state")
            if state in FINAL_STATES:
                code = result.get("exit_code")
                if state in ("succeeded", "failed") and (isinstance(code, bool) or not isinstance(code, int) or
                        (state == "succeeded") != (code == 0)):
                    raise coding.CodingError("Runner returned an inconsistent command exit status.")
                from dua_engine.agent.agent_policy import filter_text
                output = result.get("output", "")
                if not isinstance(output, str):
                    raise coding.CodingError("Invalid runner output.")
                exported = []
                artifact_error = result.get("artifact_error")
                if exports and state == "succeeded" and not artifact_error:
                    exported = coding_artifacts.validate(result.get("artifacts"), exports, workspace)
                    with workspace.lock:
                        workspace.command_results[job_id] = {"fingerprint": fingerprint, "files": exported, "imported": False, "profile": profile, "image_digest": image_digest}
                completed = True
                raw = output.encode("utf-8")
                return {"state": state, "exit_code": result.get("exit_code"), "profile": profile, "image_digest": image_digest,
                        "output": filter_text(raw[:MAX_OUTPUT_BYTES].decode("utf-8", errors="ignore")),
                        "truncated": result.get("truncated") is True or len(raw) > MAX_OUTPUT_BYTES,
                        "base_sha": workspace.base_sha, "snapshot_sha256": digest,
                        "command_id": job_id, "exported_paths": [item["path"] for item in exported],
                        "artifact_error": str(artifact_error)[:300] if artifact_error else None,
                        "duration_ms": round((time.monotonic() - started) * 1000),
                        "note": "Container is ephemeral. Requested text exports are request-local until import_command_changes; review workspace_diff before PR approval."}
            if state not in ("queued", "running"):
                raise coding.CodingError("Invalid runner job state.")
            output = result.get("output", "")
            if not isinstance(output, str):
                raise coding.CodingError("Invalid runner output.")
            if progress is not None:
                raw = output.encode("utf-8")
                progress({"state": state, "profile": profile,
                          "output": raw[:MAX_OUTPUT_BYTES].decode("utf-8", errors="ignore"),
                          "truncated": result.get("truncated") is True or len(raw) > MAX_OUTPUT_BYTES})
            for _ in range(5):
                if stop():
                    break
                time.sleep(0.2)
            result = client.request("GET", path)
    finally:
        if not completed:
            try:
                client.request("DELETE", path)
            except coding.CodingError:
                # The independent in-container deadline still limits a job if
                # the control service cannot be reached for cancellation.
                pass
