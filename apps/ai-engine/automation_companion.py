"""Personal device companion, separate from the engine. Linux host, one owner.

Run behind a private/authenticated HTTPS reverse proxy, with one worker. The
service token authenticates the engine and the owner UUID scopes every request.
All driver work uses one thread, so observations/actions cannot race each other.
"""
import atexit
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import threading
import time

from flask import Flask, jsonify, request

import agent_automation
import automation_drivers
from automation_terminal import Terminals, cleanup_orphans


class Companion:
    def __init__(self, config, driver_factory=automation_drivers.create, terminals=None, clock=time.monotonic):
        self.owner = agent_automation.owner(config.get("owner"))
        self.targets = config.get("targets", {})
        if not isinstance(self.targets, dict) or len(self.targets) > 8:
            raise ValueError("Configure at most eight targets")
        for key, target in self.targets.items():
            if not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", key) or not isinstance(target, dict) or target.get("kind") not in ("desktop", "browser", "android"):
                raise ValueError("Invalid configured target")
        # A companion owns one desktop/phone. Never share a desktop between unrelated users.
        self.terminals = terminals or Terminals(config.get("terminalProfiles", {}))
        self.factory, self.clock = driver_factory, clock
        self.drivers, self.views, self.used = {}, {}, {}
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="dua-device-owner")
        self.pending = threading.BoundedSemaphore(4)
        self.closed = threading.Event()
        self.reaper = threading.Thread(target=self.reap, name="dua-terminal-expiry", daemon=True)
        self.reaper.start()

    def reap(self):
        while not self.closed.wait(5):
            self.terminals.reap()

    def close(self):
        self.closed.set()
        self.terminals.close_all()
        def close_drivers():
            for driver in self.drivers.values():
                driver.close()
        self.executor.submit(close_drivers)
        self.executor.shutdown(wait=False, cancel_futures=False)

    def handle(self, body):
        if not isinstance(body, dict) or set(body) - {"owner", "tool", "args", "request_id"}:
            raise ValueError("Invalid companion request")
        if agent_automation.owner(body.get("owner")) != self.owner:
            raise ValueError("Owner is not paired with this companion")
        name = body.get("tool")
        args = agent_automation.validate(name, body.get("args", {}))
        request_id = body.get("request_id")
        if name in agent_automation.WRITE_TOOLS and (not isinstance(request_id, str) or not re.fullmatch(r"[a-zA-Z0-9_-]{16,80}", request_id)):
            raise ValueError("Missing action approval id")
        if not self.pending.acquire(blocking=False):
            raise ValueError("Companion is busy; observe again later")
        try:
            future = self.executor.submit(self.invoke, name, args, request_id)
            # Release on actual completion, even when HTTP times out. Never replay a queued action.
            future.add_done_callback(lambda _: self.pending.release())
        except BaseException:
            self.pending.release()
            raise
        try:
            return future.result(timeout=20)
        except FutureTimeout:
            future.cancel()  # if already running, outcome remains unknown; duplicate ID is still refused
            return {"error": "Device operation timed out; outcome may be unknown. Observe/read again, do not automatically retry."}

    def driver(self, target):
        if target not in self.targets:
            raise ValueError("Target is not paired")
        if target not in self.drivers:
            self.drivers[target] = self.factory(self.targets[target])
        return self.drivers[target]

    @staticmethod
    def fingerprint(view):
        return hashlib.sha256(json.dumps(view, sort_keys=True).encode()).hexdigest()

    def invoke(self, name, args, request_id):
        now = self.clock()
        self.used = {key: expiry for key, expiry in self.used.items() if expiry > now}
        if name in agent_automation.WRITE_TOOLS:
            if request_id in self.used:
                raise ValueError("Action already consumed; inspect its outcome instead of replaying")
            if len(self.used) >= 512:
                raise ValueError("Companion action budget reached")
            self.used[request_id] = now + 3600
        if name == "automation_targets":
            self.terminals.reap()
            with self.terminals.lock:
                sessions = [{"session_id": s.id, "profile": s.profile, "closed": s.closed}
                            for s in self.terminals.sessions.values() if s.owner == self.owner]
            return {"targets": [{"id": key, "kind": config["kind"], "apps": sorted(config.get("apps", {}))}
                                for key, config in self.targets.items()],
                    "terminalProfiles": sorted(self.terminals.profiles), "terminals": sessions,
                    "writesRequireApproval": True, "liveVerified": False}
        if name == "observe_device":
            target = args["target"]
            view = self.driver(target).observe()
            key = secrets.token_urlsafe(24)
            self.views[target] = {"id": key, "expires": now + 120, "fingerprint": self.fingerprint(view),
                                  "width": view["width"], "height": view["height"]}
            return dict(view, target=target, view_id=key, validForSeconds=120)
        if name == "control_device":
            target = args["target"]
            saved = self.views.pop(target, None)  # one attempted action per observation, including failures
            if saved is None or saved["id"] != args["view_id"] or saved["expires"] <= now:
                raise ValueError("View is missing, stale or consumed; observe again")
            driver = self.driver(target)
            current = driver.observe()
            if self.fingerprint(current) != saved["fingerprint"]:
                raise ValueError("Device changed since observation; observe again before approval")
            driver.act(args, saved)
            return {"target": target, "action": args["action"], "completed": True,
                    "note": "Input action completed; observe again to verify the app outcome."}
        if name == "open_terminal":
            return self.terminals.open(self.owner, args["profile"])
        session = self.terminals.get(self.owner, args["session_id"])
        if name == "read_terminal":
            return session.read(args.get("cursor", 0))
        if name == "write_terminal":
            return session.write(args["text"])
        session.close()
        return {"session_id": session.id, "closed": True}


def create_app(config, token, companion=None):
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", token):
        raise ValueError("Use a dedicated 32–256 character URL-safe companion token")
    control = companion or Companion(config)
    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = 16_000
    app.extensions["companion"] = control

    @app.after_request
    def no_store(response):
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.post("/v1/tool")
    def tool():
        supplied = request.headers.get("Authorization", "")
        if not supplied.isascii() or not secrets.compare_digest(supplied, "Bearer " + token):
            return jsonify({"error": "Unauthorized"}), 401
        try:
            return jsonify(control.handle(request.get_json(silent=True)))
        except ValueError as error:
            # Only static messages: driver errors may include URLs, entered text or private data.
            return jsonify({"error": "Operation refused (check owner, target, fresh view and session expiry). Observe/read again; do not automatically retry."})
        except Exception:
            return jsonify({"error": "Device unavailable or outcome unknown. Observe/read again; do not automatically retry."})

    return app


def main():
    import argparse
    import logging
    from waitress import serve
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--port", type=int, default=8791)
    args = parser.parse_args()
    path = Path(args.config)
    if path.stat().st_mode & 0o077:
        raise ValueError("Companion configuration must be private (chmod 600)")
    config = json.loads(path.read_text())
    if config.get("terminalProfiles"):
        cleanup_orphans()
    # Keep the service credential out of browser/ADB/app child environments.
    token = os.environ.pop("DUA_AUTOMATION_TOKEN", "")
    app = create_app(config, token)
    atexit.register(app.extensions["companion"].close)
    logging.getLogger("werkzeug").setLevel(logging.ERROR)
    # Bind locally. TLS/authenticated ingress belongs to the operator; never expose the debug server.
    serve(app, host="127.0.0.1", port=args.port, threads=4, max_request_body_size=16_000,
          channel_timeout=30, clear_untrusted_proxy_headers=True)


if __name__ == "__main__":
    main()
