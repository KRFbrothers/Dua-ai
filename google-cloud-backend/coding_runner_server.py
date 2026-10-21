"""Trusted control service for a dedicated Docker host, separate from Cloud Run.

Only this service accesses Docker. Source/commands run inside short-lived,
network-disabled, credential-free containers, never in this Python process.
Run one Gunicorn worker (threads are supported); job state is process-local.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import selectors
import subprocess
import tempfile
import threading
import time

from flask import Flask, jsonify, request

import coding_agent as coding
import coding_snapshot
from coding_terminal import FINAL_STATES, JOB_ID, MAX_OUTPUT_BYTES, validate_command

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 24 * 1024 * 1024
LABEL = "dev.dua.coding-sandbox"
MAX_JOBS = 64
MAX_CONCURRENT = 2
RESULT_TTL = 900


def settings():
    token = os.environ.get("DUA_CODING_RUNNER_TOKEN", "").strip()
    image = os.environ.get("DUA_SANDBOX_IMAGE", "").strip()
    runtime = os.environ.get("DUA_SANDBOX_RUNTIME", "").strip()
    if (len(token) < 32 or not token.isascii() or any(c.isspace() for c in token) or
            not re.fullmatch(r"[a-z0-9][a-z0-9._/:\-]*@sha256:[0-9a-f]{64}", image) or
            runtime not in ("", "runsc")):
        raise coding.CodingError("Runner requires a dedicated token, locally installed digest-pinned image and optional runsc runtime.")
    return {"token": token, "image": image, "runtime": runtime}


class Job:
    def __init__(self, job_id, scope, digest, base_sha):
        self.id, self.scope, self.digest, self.base_sha = job_id, scope, digest, base_sha
        self.state, self.output, self.exit_code = "queued", bytearray(), None
        self.truncated, self.finished = False, None
        self.cancel, self.lock = threading.Event(), threading.RLock()

    def append(self, data):
        with self.lock:
            available = max(0, MAX_OUTPUT_BYTES - len(self.output))
            self.output.extend(data[:available])
            self.truncated |= len(data) > available

    def finish(self, state, exit_code=None):
        with self.lock:
            self.state, self.exit_code, self.finished = state, exit_code, time.monotonic()

    def view(self):
        with self.lock:
            return {"id": self.id, "state": self.state, "snapshot_sha256": self.digest,
                    "base_sha": self.base_sha, "exit_code": self.exit_code,
                    "output": self.output.decode("utf-8", errors="replace"), "truncated": self.truncated}


def docker(config_dir, *args, timeout=15):
    # Explicit local socket/config prevents inherited contexts, TLS selectors,
    # credential helpers or Docker proxy defaults entering sandbox containers.
    return subprocess.run(["docker", "--host=unix:///var/run/docker.sock", "--config", str(config_dir), *args],
                          stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C.UTF-8"}, timeout=timeout, check=False)


def execute(job, files, argv, cwd, seconds, config):
    name = "dua-sandbox-" + job.id
    created, process = False, None
    # This directory must exist at the same absolute path on the Docker host.
    # The supplied systemd template runs this controller directly on that host.
    root_dir = os.environ.get("DUA_RUNNER_DATA_DIR") or None
    try:
        with tempfile.TemporaryDirectory(prefix="dua-command-", dir=root_dir) as temporary:
            root = Path(temporary)
            source, client_config = root / "source", root / "docker"
            source.mkdir(mode=0o755)
            source.chmod(0o755)
            client_config.mkdir(mode=0o700)
            (client_config / "config.json").write_text('{"auths":{},"proxies":{}}')
            for path, (raw, mode) in files.items():
                if job.cancel.is_set():
                    job.finish("cancelled")
                    return
                destination = source / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                for parent in destination.parents:
                    if parent == root:
                        break
                    parent.chmod(0o755)
                with destination.open("xb") as handle:
                    handle.write(raw)
                destination.chmod(0o755 if mode == "100755" else 0o644)
            options = ["create", "--name", name, "--label", LABEL + "=1", "--pull=never",
                       "--network=none", "--ipc=none", "--read-only", "--user=10001:10001", "--init",
                       "--cap-drop=ALL", "--security-opt=no-new-privileges:true", "--cgroupns=private",
                       "--cpus=1", "--memory=512m", "--memory-swap=512m", "--pids-limit=128",
                       "--ulimit=nofile=1024:1024", "--ulimit=fsize=67108864:67108864", "--ulimit=core=0:0",
                       "--log-driver=none", "--no-healthcheck", "--stop-timeout=2", "--hostname=dua-sandbox",
                       "--mount", f"type=bind,source={source},target=/input,readonly",
                       "--tmpfs", "/workspace:rw,exec,nosuid,nodev,size=268435456,uid=10001,gid=10001,mode=0700",
                       "--tmpfs", "/tmp:rw,exec,nosuid,nodev,size=67108864,uid=10001,gid=10001,mode=0700",
                       "--workdir=/workspace", "--env=HOME=/tmp/home", "--env=XDG_CACHE_HOME=/tmp/cache",
                       "--entrypoint=/usr/bin/timeout"]
            if config["runtime"]:
                options += ["--runtime", config["runtime"]]
            # argv/cwd are positional parameters, never interpolated shell text.
            # Even shell commands explicitly requested in argv stay in Docker.
            bootstrap = ('mkdir -p /tmp/home /tmp/cache && cp -R /input/. /workspace/ && '
                         'cd "/workspace/$1" && shift && exec "$@"')
            options += [config["image"], "--signal=KILL", str(seconds), "/bin/sh", "-c", bootstrap,
                        "dua-command", cwd, *argv]
            try:
                if job.cancel.is_set():
                    job.finish("cancelled")
                    return
                image_info = docker(client_config, "image", "inspect", "--format", "{{json .Config.Volumes}}", config["image"])
                if image_info.returncode or json.loads(image_info.stdout) not in (None, {}):
                    raise coding.CodingError("Sandbox image is unavailable or declares unsupported persistent volumes.")
                # Mark for name-scoped cleanup even if Docker creates the
                # container but its CLI response is lost or times out.
                created = True
                result = docker(client_config, *options)
                if result.returncode:
                    raise coding.CodingError("Sandbox could not be created. Check Docker, image and resource-limit support.")
                with job.lock:
                    job.state = "running"
                if job.cancel.is_set():
                    job.finish("cancelled")
                    return
                process = subprocess.Popen(["docker", "--host=unix:///var/run/docker.sock", "--config", str(client_config),
                    "start", "--attach", name], stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C.UTF-8"})
                started = time.monotonic()
                selector = selectors.DefaultSelector()
                try:
                    selector.register(process.stdout, selectors.EVENT_READ)
                    while process.poll() is None or selector.get_map():
                        if job.cancel.is_set():
                            job.finish("cancelled")
                            return
                        if time.monotonic() - started > seconds + 10:
                            job.finish("timed_out")
                            return
                        for key, _ in selector.select(timeout=0.2):
                            chunk = os.read(key.fileobj.fileno(), 4096)
                            if chunk:
                                job.append(chunk)
                            else:
                                selector.unregister(key.fileobj)
                    process.wait(timeout=3)
                finally:
                    selector.close()
                state = docker(client_config, "inspect", "--format", "{{json .State}}", name)
                if state.returncode:
                    raise coding.CodingError("Sandbox exit status is unavailable.")
                inspected = json.loads(state.stdout)
                code = inspected.get("ExitCode")
                if inspected.get("Running") or isinstance(code, bool) or not isinstance(code, int):
                    raise coding.CodingError("Sandbox did not return a completed exit status.")
                timed_out = code == 137 and time.monotonic() - started >= seconds - 0.5
                job.finish("timed_out" if timed_out else ("succeeded" if code == 0 else "failed"), code)
            finally:
                if created:
                    try:
                        docker(client_config, "rm", "--force", name, timeout=10)
                    except (OSError, subprocess.TimeoutExpired):
                        pass  # The in-container deadline remains independent.
                if process is not None:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=3)
                    process.stdout.close()
    except Exception as error:
        # No Docker diagnostics/source/command text in HTTP errors or logs.
        if job.state not in FINAL_STATES:
            message = str(error) if isinstance(error, coding.CodingError) else "Sandbox infrastructure failed; inspect the runner host configuration."
            job.append(message.encode("utf-8"))
            job.finish("error")


class Jobs:
    def __init__(self):
        self.items, self.lock = {}, threading.RLock()
        self.cancellations = {}
        self.slots = threading.BoundedSemaphore(MAX_CONCURRENT)

    def submit(self, payload, scope, config):
        if not self.slots.acquire(blocking=False):
            return None
        try:
            if not isinstance(payload, dict) or set(payload) != {
                    "id", "archive", "snapshot_sha256", "base_sha", "argv", "cwd", "timeout_seconds"}:
                raise coding.CodingError("Invalid runner request fields.")
            argv, cwd, seconds = validate_command(payload)
            job_id, digest, base = payload["id"], payload["snapshot_sha256"], payload["base_sha"]
            if (not isinstance(job_id, str) or not JOB_ID.fullmatch(job_id) or
                    not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest) or
                    not isinstance(base, str) or not re.fullmatch(r"[0-9a-f]{40}", base)):
                raise coding.CodingError("Invalid runner snapshot metadata.")
            encoded = payload["archive"]
            if not isinstance(encoded, str) or len(encoded) > (coding_snapshot.MAX_ARCHIVE_BYTES + 2) // 3 * 4:
                raise coding.CodingError("Source snapshot exceeds the runner limit.")
            try:
                archive = base64.b64decode(encoded, validate=True)
            except ValueError:
                raise coding.CodingError("Invalid source archive encoding.") from None
            if hashlib.sha256(archive).hexdigest() != digest:
                raise coding.CodingError("Source snapshot digest did not match.")
            files = coding_snapshot.unpack(archive)
            job = Job(job_id, scope, digest, base)
            with self.lock:
                now = time.monotonic()
                self.items = {key: old for key, old in self.items.items()
                              if old.finished is None or now - old.finished < RESULT_TTL}
                if job_id in self.items or len(self.items) >= MAX_JOBS:
                    raise coding.CodingError("Runner job ID already exists or result storage is full.")
                cancellation = self.cancellations.get(job_id)
                if cancellation and secrets.compare_digest(cancellation[0], scope):
                    job.cancel.set()
                self.items[job_id] = job
            def work():
                try:
                    execute(job, files, argv, cwd, seconds, config)
                finally:
                    self.slots.release()
            threading.Thread(target=work, name="dua-command-" + job_id, daemon=True).start()
            return job
        except Exception:
            self.slots.release()
            raise

    def get(self, job_id, scope):
        with self.lock:
            job = self.items.get(job_id)
            if job is None or not secrets.compare_digest(job.scope, scope):
                return None
            return job

    def cancel(self, job_id, scope):
        with self.lock:
            job = self.items.get(job_id)
            if job is not None:
                if not secrets.compare_digest(job.scope, scope):
                    return None
                job.cancel.set()
                return job
            # Remember a cancellation that reaches us while the POST is still
            # being uploaded/validated; its response may have been lost.
            now = time.monotonic()
            self.cancellations = {key: value for key, value in self.cancellations.items() if now - value[1] < RESULT_TTL}
            if len(self.cancellations) >= MAX_JOBS:
                oldest = min(self.cancellations, key=lambda key: self.cancellations[key][1])
                del self.cancellations[oldest]
            self.cancellations[job_id] = (scope, now)
            return None


JOBS = Jobs()


@app.before_request
def authenticate():
    if request.path == "/healthz" and request.method == "GET":
        return None
    try:
        config = settings()
    except coding.CodingError:
        return jsonify({"error": "Runner is not configured."}), 503
    supplied = request.headers.get("Authorization", "")
    scope = request.headers.get("X-Dua-Run-Scope", "")
    if (not supplied.isascii() or not secrets.compare_digest(supplied, "Bearer " + config["token"]) or
            not JOB_ID.fullmatch(scope)):
        return jsonify({"error": "Unauthorized."}), 401


@app.after_request
def private_response(response):
    response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/healthz")
def health():
    return jsonify({"service": "dua-coding-runner", "liveVerified": False})


@app.post("/v1/runs")
def create_run():
    try:
        job = JOBS.submit(request.get_json(silent=True), request.headers["X-Dua-Run-Scope"], settings())
        if job is None:
            return jsonify({"error": "Runner is busy."}), 429
        return jsonify(job.view()), 202
    except (coding.CodingError, ValueError, TypeError):
        return jsonify({"error": "Invalid or unsupported source snapshot/command."}), 400


@app.route("/v1/runs/<job_id>", methods=["GET", "DELETE"])
def run_status(job_id):
    if not JOB_ID.fullmatch(job_id):
        return jsonify({"error": "Run not found."}), 404
    if request.method == "DELETE":
        job = JOBS.cancel(job_id, request.headers["X-Dua-Run-Scope"])
        return jsonify(job.view() if job else {"id": job_id, "state": "cancel_requested"}), 202
    job = JOBS.get(job_id, request.headers["X-Dua-Run-Scope"])
    if job is None:
        return jsonify({"error": "Run not found."}), 404
    return jsonify(job.view())
