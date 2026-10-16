import io
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import agent_notes  # noqa: E402
import agent_tools  # noqa: E402


class Resp(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class FakeSupabase:
    """Plays PostgREST for agent_notes and records every request."""

    def __init__(self, rows=None):
        self.rows, self.requests = list(rows or []), []

    def open(self, request, timeout=None):
        self.requests.append(request)
        method, url = request.get_method(), request.full_url
        if method == "GET":
            return Resp(json.dumps(self.rows).encode())
        if method == "POST":
            row = dict(json.loads(request.data), id=str(uuid.uuid4()), created_at="2026-10-16T07:00:00Z")
            self.rows.insert(0, row)
            return Resp(json.dumps([row]).encode())
        note_id = url.split("id=eq.")[1]
        gone = [r for r in self.rows if r["id"] == note_id]
        self.rows = [r for r in self.rows if r["id"] != note_id]
        return Resp(json.dumps(gone).encode())


def setup_env(monkeypatch, notes="1"):
    monkeypatch.setenv("SUPABASE_URL", "https://proj.supabase.co")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "anon")
    monkeypatch.setenv("AGENT_NOTES", notes)


def store(monkeypatch, rows=None):
    setup_env(monkeypatch)
    fake = FakeSupabase(rows)
    return agent_notes.for_owner({"id": "u1", "token": "owner-jwt"}, fake), fake


def test_kill_switch_and_owner_required(monkeypatch):
    setup_env(monkeypatch, "0")
    assert agent_notes.for_owner({"id": "u1", "token": "t"}) is None
    setup_env(monkeypatch)
    assert agent_notes.for_owner({"id": "u1", "token": ""}) is None
    assert agent_notes.for_owner({"id": None, "token": "t"}) is None
    assert agent_notes.run_tool(None, "save_note", {"note": "x"}) == {"error": "Agent notes are turned off."}


def test_requests_use_owner_token_and_anon_key(monkeypatch):
    s, fake = store(monkeypatch)
    assert s.add("User likes short Hinglish answers", "preference")["saved"]
    req = fake.requests[-1]
    assert req.get_header("Authorization") == "Bearer owner-jwt" and req.get_header("Apikey") == "anon"
    assert req.full_url == "https://proj.supabase.co/rest/v1/agent_notes"
    assert json.loads(req.data) == {"note": "User likes short Hinglish answers", "kind": "preference", "source": "agent"}


def test_secrets_and_emails_are_never_saved(monkeypatch):
    s, fake = store(monkeypatch)
    for bad in ["mail me at fareed@example.com", "key is AIzaSyA1234567890abcdefghijk", "ghp_abcdefghijklmnop1234",
                "token: abc123", "Bearer abcdefghijklmnop", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.sig",
                "-----BEGIN RSA PRIVATE KEY-----", "sk-proj-abcdefghijklmnopqrst", "a" * 8 + "0123456789abcdef" * 3]:
        assert "error" in s.add(bad), bad
    assert all(r.get_method() == "GET" for r in fake.requests)


def test_dedup_and_search_and_delete_own_note(monkeypatch):
    s, fake = store(monkeypatch)
    first = s.add("Repo uses git grep because rg hangs on the box", "repo")
    assert s.add("repo uses git grep because rg hangs on the box")["saved"] is False
    assert s.add("The repo uses git grep since rg hangs on the box", "repo")["saved"] is False
    found = s.search("rg hangs")
    assert found[0]["id"] == first["id"]
    assert s.delete(first["id"]) == {"deleted": True}
    assert s.delete(first["id"]) == {"error": "No such note."}
    assert s.delete("../all") == {"error": "Unknown note id."}


def test_relevant_caps_count_and_size():
    notes = [{"id": str(i), "note": f"note {i} about python tests " + "x" * 300, "kind": "fact"} for i in range(50)]
    chosen = agent_notes.relevant(notes, "python tests")
    assert len(chosen) <= agent_notes.PROMPT_NOTES
    assert sum(len(n["note"]) for n in chosen) <= agent_notes.PROMPT_CHARS
    many = [{"id": str(i), "note": f"short {i}", "kind": "fact"} for i in range(50)]
    assert len(agent_notes.relevant(many, "")) == 20


def test_relevant_prefers_matching_notes():
    notes = [{"id": "a", "note": "User prefers tables", "kind": "fact"},
             {"id": "b", "note": "Android build needs JDK 21", "kind": "repo"}]
    assert agent_notes.relevant(notes, "fix the android build", limit=1)[0]["id"] == "b"
    block = agent_notes.prompt_block(notes)
    assert "[repo] Android build needs JDK 21" in block and "may be outdated" in block


def test_load_prompt_never_fails(monkeypatch):
    class Broken:
        def list(self):
            raise OSError("down")
    assert agent_notes.load_prompt(Broken(), "q") == ""
    assert agent_notes.load_prompt(None, "q") == ""


class Flash:
    def __init__(self, text):
        self.text, self.calls = text, []

    def generate_content(self, contents, generation_config=None, **kw):
        self.calls.append((contents, generation_config))
        return type("R", (), {"text": self.text})()


def test_learn_saves_up_to_three_filtered_deduped_lessons(monkeypatch):
    s, fake = store(monkeypatch, [{"id": str(uuid.uuid4()), "note": "User wants free tools only", "kind": "preference"}])
    lessons = [{"note": "User wants free tools only", "kind": "preference"},          # duplicate
               {"note": "Secret is sk-abcdefghijklmnopqrstuvwx", "kind": "fact"},      # secret
               {"note": "CI Python tests run without google-genai installed", "kind": "repo"},
               {"note": "never reached (4th)", "kind": "fact"}]
    flash = Flash(json.dumps({"lessons": lessons}))
    assert agent_notes.learn(s, flash, "add a test", "- Step 1: read_file", "done") == 1
    assert fake.rows[0]["note"] == "CI Python tests run without google-genai installed"
    assert fake.rows[0]["source"] == "auto"
    config = flash.calls[0][1]
    assert config["thinking_level"] == "low" and config["max_output_tokens"] == agent_notes.EXTRACT_TOKENS
    assert "User wants free tools only" in flash.calls[0][0]


def test_learn_is_best_effort(monkeypatch):
    s, _ = store(monkeypatch)
    logs = []
    assert agent_notes.learn(s, Flash("not json"), "q", "", "a", log=logs.append) == 0
    assert logs and "learning skipped" in logs[0]
    assert agent_notes.learn(None, Flash("{}"), "q", "", "a") == 0


def test_agent_loop_note_tools_and_limits(monkeypatch):
    s, fake = store(monkeypatch)
    run = agent_tools.AgentRun({"notes": s, "enqueue": lambda *a: True})
    assert run.run_tool("save_note", {"note": "User likes dark theme screenshots", "kind": "preference"})["saved"]
    assert run.run_tool("search_notes", {"query": "dark theme"})["notes"][0]["note"].startswith("User likes")
    for _ in range(2):
        run.run_tool("save_note", {"note": f"note {_} {uuid.uuid4()}"})
    assert "limit reached" in run.run_tool("save_note", {"note": "fourth"})["error"]
    names = [t["name"] for t in agent_tools.declarations(False, notes=True)]
    assert {"save_note", "search_notes", "delete_note"} <= set(names)
    assert "save_note" not in [t["name"] for t in agent_tools.declarations(False)]


def test_tool_loop_loads_notes_and_learns_after_done(monkeypatch):
    import app as engine
    s, fake = store(monkeypatch, [{"id": str(uuid.uuid4()), "note": "User wants answers in Hinglish", "kind": "preference"}])
    monkeypatch.setattr(agent_notes, "for_owner", lambda owner, opener=None: s)
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    seen = {}

    def fake_run(env, history, tools, tool_response):
        seen["notes"], seen["tools"] = env["notes"], tools
        run = agent_tools.AgentRun(env)
        run.steps.append("- Step 1: google_search\n")
        return "answer", run
    monkeypatch.setattr(agent_tools, "run", fake_run)
    monkeypatch.setattr(agent_tools, "default_tools", lambda github, issues=False, notes=False: ["NOTES"] if notes else [])
    monkeypatch.setattr(engine, "get_agent_model", lambda system: seen.setdefault("system", system))
    learned = []
    monkeypatch.setattr(agent_notes, "learn", lambda *a: learned.append(a))
    monkeypatch.setattr(engine, "build_contents", lambda m, a: [])
    events = []
    engine.tool_loop([{"role": "user", "text": "plan a trip"}], [], "sys",
                     lambda kind, ev=None: events.append(kind) or True, {"id": "u1", "token": "t"})
    assert "User wants answers in Hinglish" in seen["system"] and seen["tools"] == ["NOTES"]
    assert events[-1] == "done" and learned and learned[0][2] == "plan a trip" and learned[0][4] == "answer"
