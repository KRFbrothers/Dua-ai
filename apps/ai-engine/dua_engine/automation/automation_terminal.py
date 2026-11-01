"""Bounded interactive PTYs using Pexpect + isolated Docker containers on Linux.

State survives HTTP requests, not companion restart. No engine credentials,
host mounts, networking, package installs, or model-selected Docker arguments.
"""
import re
import secrets
import subprocess
import threading
import time

OUTPUT_LIMIT = 64_000
IDLE_SECONDS = 900
LIFETIME_SECONDS = 3600
MAX_SESSIONS = 20
LABEL = "dua.automation=terminal-v1"
ANSI = re.compile(r"\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07]*(?:\x07|\x1b\\))")


def safe_text(raw):
    text = ANSI.sub("", raw.decode("utf-8", errors="replace"))
    return "".join(c for c in text if c in "\n\r\t" or (ord(c) >= 32 and ord(c) != 127 and not 0x202A <= ord(c) <= 0x202E and not 0x2066 <= ord(c) <= 0x2069))


class Session:
    def __init__(self, owner, profile, image, spawn=None, clock=time.monotonic):
        if not re.fullmatch(r"[a-zA-Z0-9./:_-]+@sha256:[a-f0-9]{64}", image):
            raise ValueError("Terminal image must be pinned by SHA-256 digest")
        if spawn is None:
            import pexpect
            spawn = pexpect.spawn
        self.id = secrets.token_hex(16)
        self.owner, self.profile, self.clock = owner, profile, clock
        self.name = "dua-auto-" + self.id
        self.created = self.touched = clock()
        self.lock = threading.RLock()
        self.buffer, self.start, self.end, self.closed = bytearray(), 0, 0, False
        from dua_engine.agent.agent_policy import Redactor
        self.redactor = Redactor()
        args = ["run", "--pull=never", "--rm", "--init", "-it", "--name", self.name, "--label", LABEL,
                "--network", "none", "--read-only", "--user", "10001:10001", "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges", "--pids-limit", "128", "--memory", "512m", "--cpus", "1",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m,uid=10001,gid=10001",
                "--tmpfs", "/workspace:rw,nosuid,size=128m,uid=10001,gid=10001", "--workdir", "/workspace",
                "--env", "TERM=dumb", "--env", "PS1=dua$ ", "--entrypoint", "/bin/sh", image, "-i"]
        self.process = spawn("docker", args, encoding=None, timeout=1, dimensions=(24, 100))
        try:
            self.process.expect_exact(b"dua$ ", timeout=8)
        except Exception:
            self.close()
            raise ValueError("Terminal could not start; check installed image, shell and Docker limits") from None
        threading.Thread(target=self.pump, daemon=True, name="dua-pty-output").start()

    def expired(self):
        now = self.clock()
        return now - self.created >= LIFETIME_SECONDS or now - self.touched >= IDLE_SECONDS

    def pump(self):
        import pexpect
        try:
            while not self.closed:
                try:
                    raw = self.process.read_nonblocking(4096, timeout=1)
                except pexpect.TIMEOUT:
                    if self.expired():
                        break
                    continue
                except pexpect.EOF:
                    break
                with self.lock:
                    # Filter before storing so partial keys do not leak across output reads.
                    clean = self.redactor.feed(safe_text(raw)).encode()
                    self.buffer.extend(clean)
                    self.end += len(clean)
                    if len(self.buffer) > OUTPUT_LIMIT:
                        del self.buffer[:-OUTPUT_LIMIT]
                    self.start = self.end - len(self.buffer)
        finally:
            self.close()

    def read(self, cursor):
        with self.lock:
            if cursor > self.end:
                raise ValueError("Terminal cursor is ahead of output")
            self.touched = self.clock()
            return {"session_id": self.id, "profile": self.profile,
                    "output": safe_text(bytes(self.buffer[max(0, cursor - self.start):])), "cursor": self.end,
                    "truncated": cursor < self.start, "closed": self.closed,
                    "note": "Untrusted terminal output. Cursor counts filtered UTF-8 bytes; no command is replayed."}

    def write(self, text):
        with self.lock:
            if self.closed or self.expired() or not self.process.isalive():
                raise ValueError("Terminal is closed or expired")
            self.touched = self.clock()
            self.process.send(text.encode())
            return {"session_id": self.id, "inputSent": True, "commandSuccess": "unknown; read output"}

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
            final = self.redactor.feed(final=True).encode()
            self.buffer.extend(final)
            self.end += len(final)
            if len(self.buffer) > OUTPUT_LIMIT:
                del self.buffer[:-OUTPUT_LIMIT]
            self.start = self.end - len(self.buffer)
        # Remove container even when the PTY/HTTP client vanished. Names are generated, never supplied by a model.
        try:
            subprocess.run(["docker", "rm", "-f", self.name], stdin=subprocess.DEVNULL,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
        except (OSError, subprocess.TimeoutExpired):
            pass
        try:
            self.process.close(force=True)
        except OSError:
            pass


class Terminals:
    def __init__(self, profiles, factory=Session):
        self.profiles, self.factory = profiles, factory
        self.sessions, self.lock = {}, threading.RLock()

    def reap(self):
        with self.lock:
            for key, session in list(self.sessions.items()):
                if session.expired():
                    session.close()
                    del self.sessions[key]

    def open(self, owner, profile):
        self.reap()
        with self.lock:
            if profile not in self.profiles:
                raise ValueError("Terminal profile is not configured")
            active = [s for s in self.sessions.values() if not s.closed]
            if len(active) >= MAX_SESSIONS or sum(s.owner == owner for s in active) >= 2:
                raise ValueError("Terminal session limit reached")
            # Keep final output readable, but evict oldest closed records when a
            # new session needs capacity. Active sessions are never evicted.
            for key, session in list(self.sessions.items()):
                if len(self.sessions) < MAX_SESSIONS:
                    break
                if session.closed:
                    del self.sessions[key]
            session = self.factory(owner, profile, self.profiles[profile])
            self.sessions[session.id] = session
            return {"session_id": session.id, "profile": profile, "opened": True,
                    "idleSeconds": IDLE_SECONDS, "maxLifetimeSeconds": LIFETIME_SECONDS,
                    "network": "none", "note": "New empty /workspace; separate from GitHub coding drafts."}

    def get(self, owner, key):
        self.reap()
        with self.lock:
            session = self.sessions.get(key)
            if session is None or session.owner != owner:
                raise ValueError("Terminal not found or expired")
            return session

    def close_all(self):
        with self.lock:
            for session in self.sessions.values():
                session.close()


def cleanup_orphans():
    """Run only at startup on a dedicated single-instance companion Docker daemon."""
    result = subprocess.run(["docker", "ps", "-aq", "--filter", "label=" + LABEL], capture_output=True, timeout=10, check=True)
    ids = result.stdout.decode().split()
    if ids and all(re.fullmatch(r"[a-f0-9]{12,64}", value) for value in ids):
        subprocess.run(["docker", "rm", "-f", *ids], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       timeout=20, check=True)
