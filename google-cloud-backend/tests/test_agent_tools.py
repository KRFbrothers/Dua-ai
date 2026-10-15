import datetime
import json
import os
import socket
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_tools as at  # noqa: E402
import app as engine  # noqa: E402


def call(name, args=None):
    return SimpleNamespace(function_calls=[SimpleNamespace(name=name, args=args or {})], candidates=[SimpleNamespace(content=f"turn-{name}")], text=None)


def final(text):
    return SimpleNamespace(function_calls=[], candidates=[], text=text)


class Script:
    """Main-loop model that plays a fixed list of responses; sub-calls (search/code) answered separately."""

    def __init__(self, steps, sub=None):
        self.steps, self.sub, self.calls = list(steps), sub, []

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls.append({"contents": list(contents), "tools": tools, "config": generation_config})
        if tools is None:  # router
            return SimpleNamespace(text=json.dumps({"route": "agent"}))
        if tools != ["TOOLS"]:
            return self.sub(tools)
        return self.steps.pop(0) if self.steps else final("done")


def env(model, **extra):
    events, progress, thinking = [], [], []
    base = {
        "model": model, "enqueue": lambda kind, ev=None: events.append(ev) or True,
        "chunk_text": lambda r: getattr(r, "text", "") or "", "grounding_sources": lambda r: getattr(r, "sources", []),
        "search_tool": lambda: "SEARCH", "code_tool": lambda: "CODE", "fetch": lambda url: {"url": url, "title": "T", "text": "page", "truncated": False},
        "contents_for": lambda text: [text], "imagine": None, "memory": "", "github": None, "request_text": "x",
        "progress": lambda step, label, state="active": progress.append((step, label)),
        "thinking": thinking.append,
    }
    base.update(extra)
    return base, events, progress


def run(model, **extra):
    e, events, progress = env(model, **extra)
    text, agent = at.run(e, ["user"], ["TOOLS"], lambda results: ("tool", results), clock=extra.pop("clock", None) or (lambda: 0))
    return text, agent, events, progress, model


def tool_results(model):
    return [c["contents"][-1][1] for c in model.calls if c["tools"] == ["TOOLS"] and c["contents"][-1][0] == "tool"]


# ---------------- calculator / time
def test_calculator_is_safe_and_exact():
    assert at.calculate("(12.5*3)/7") == pytest.approx(5.357142857)
    assert at.calculate("2^10") == 1024 and at.calculate("sqrt(16) + pi") == pytest.approx(4 + 3.14159265)
    for bad in ("__import__('os')", "open('x')", "9**9**9", "a+1", "factorial(1000)", "", "1/0"):
        with pytest.raises((ValueError, ZeroDivisionError, SyntaxError)):
            at.calculate(bad)


def test_current_time_is_ist():
    fixed = lambda: datetime.datetime(2026, 10, 15, 11, 53, tzinfo=datetime.timezone.utc)
    out = at.now_ist(fixed)
    assert out["iso"] == "2026-10-15T17:23:00+05:30" and "05:23 PM IST" in out["readable"]


def test_calculator_and_time_through_loop():
    text, agent, _, progress, model = run(Script([call("calculator", {"expression": "6*7"}), call("current_time"), final("42")]))
    assert text == "42" and [p[1] for p in progress] == ["Calculating\u2026", "Checking the time\u2026"]
    results = tool_results(model)
    assert results[0][0][1] == {"result": 42} and "IST" in results[1][0][1]["readable"]


def test_calculator_error_goes_back_to_model():
    run_model = Script([call("calculator", {"expression": "import os"}), final("ok")])
    run(run_model)
    assert "error" in tool_results(run_model)[0][0][1]


# ---------------- fetch_url / SSRF
def resolver(*ips):
    return lambda host, port, proto=0: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, 0)) for ip in ips]


@pytest.mark.parametrize("url", ["ftp://example.com/x", "file:///etc/passwd", "http://localhost/", "http://example.com:8080/",
                                 "http://user:pw@example.com/", "http://metadata.google.internal/computeMetadata/v1/", "javascript:alert(1)"])
def test_fetch_rejects_bad_urls(url):
    with pytest.raises(at.FetchError):
        at.fetch_page(url, connect=lambda *a: pytest.fail("must not connect"), resolver=resolver("93.184.216.34"))


@pytest.mark.parametrize("ip", ["127.0.0.1", "10.0.0.5", "192.168.1.1", "169.254.169.254", "172.16.0.1", "::1", "fd00::1", "0.0.0.0", "100.64.0.1"])
def test_fetch_blocks_private_and_metadata_ips(ip):
    with pytest.raises(at.FetchError):
        at.fetch_page("https://evil.test/", connect=lambda *a: pytest.fail("must not connect"), resolver=resolver(ip))


def test_fetch_blocks_host_if_any_address_is_private():
    with pytest.raises(at.FetchError):
        at.public_ips("mixed.test", resolver("93.184.216.34", "10.0.0.1"))


class FakeConn:
    def __init__(self, pages, log, scheme, host, ip, port):
        self.pages, self.log = pages, log
        self.log.append((scheme, host, ip, port))
        self.key = f"{scheme}://{host}"

    def request(self, method, path, headers=None):
        self.path = path

    def getresponse(self):
        status, headers, body = self.pages[self.key + self.path]
        return SimpleNamespace(status=status, getheader=lambda k, d=None: headers.get(k, d), read=lambda n: body[:n])

    def close(self):
        pass


def fake_connect(pages, log):
    return lambda scheme, host, ip, port: FakeConn(pages, log, scheme, host, ip, port)


def test_fetch_extracts_text_pins_ip_and_caps_size():
    html = b"<html><head><title>News</title><script>evil()</script></head><body><h1>Hello</h1><p>World &amp; more</p></body></html>"
    log = []
    page = at.fetch_page("https://site.test/a?b=1", connect=fake_connect({"https://site.test/a?b=1": (200, {"Content-Type": "text/html; charset=utf-8"}, html)}, log),
                         resolver=resolver("93.184.216.34"))
    assert page["title"] == "News" and "Hello" in page["text"] and "World & more" in page["text"] and "evil" not in page["text"]
    assert log == [("https", "site.test", "93.184.216.34", 443)]
    big = b"a" * (at.FETCH_MAX_BYTES + 10)
    page = at.fetch_page("https://site.test/big", connect=fake_connect({"https://site.test/big": (200, {"Content-Type": "text/plain"}, big)}, []),
                         resolver=resolver("93.184.216.34"))
    assert page["truncated"] and len(page["text"]) == at.FETCH_MAX_CHARS


def test_fetch_rechecks_redirects_and_rejects_binary():
    pages = {"https://site.test/r": (302, {"Location": "http://internal.test/admin"}, b"")}
    ips = {"site.test": "93.184.216.34", "internal.test": "10.1.2.3"}
    res = lambda host, port, proto=0: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ips[host], 0))]
    with pytest.raises(at.FetchError, match="Private"):
        at.fetch_page("https://site.test/r", connect=fake_connect(pages, []), resolver=res)
    pages = {"https://site.test/f": (200, {"Content-Type": "application/pdf"}, b"%PDF")}
    with pytest.raises(at.FetchError, match="Only web pages"):
        at.fetch_page("https://site.test/f", connect=fake_connect(pages, []), resolver=resolver("93.184.216.34"))


def test_fetch_tool_adds_https_source_and_progress():
    text, agent, _, progress, model = run(Script([call("fetch_url", {"url": "https://site.test/a"}), final("read it")]))
    assert progress == [("fetch", "Opening page\u2026")]
    assert agent.sources == [{"title": "T", "url": "https://site.test/a", "domain": "site.test", "id": 1}]


def test_fetch_failure_is_safe_message():
    def boom(url):
        raise at.FetchError("Private or internal addresses cannot be opened.")
    model = Script([call("fetch_url", {"url": "http://10.0.0.1"}), final("no")])
    run(model, fetch=boom)
    assert tool_results(model)[0][0][1] == {"error": "Private or internal addresses cannot be opened."}


# ---------------- google_search
def test_search_is_grounded_subcall_and_sources_renumber_across_calls():
    def sub(tools):
        assert tools == ["SEARCH"]
        return SimpleNamespace(text="notes", sources=[{"id": 1, "title": "A", "url": "https://a.test", "domain": "a.test"},
                                                      {"id": 2, "title": "B", "url": "https://b.test", "domain": "b.test"}])
    model = Script([call("google_search", {"query": "q1"}), call("google_search", {"query": "q2"}), final("ans")], sub=sub)
    text, agent, *_ = run(model)
    assert [s["url"] for s in agent.sources] == ["https://a.test", "https://b.test"]
    results = tool_results(model)
    assert results[1][0][1]["sources"] == [{"id": 1, "title": "A", "url": "https://a.test"}, {"id": 2, "title": "B", "url": "https://b.test"}]
    sub_call = next(c for c in model.calls if c["tools"] == ["SEARCH"])
    assert sub_call["config"]["max_output_tokens"] <= at.SUB_TOKENS


def test_per_tool_limits():
    sub = lambda tools: SimpleNamespace(text="n", sources=[])
    model = Script([call("google_search", {"query": "q"})] * 5 + [final("ok")], sub=sub)
    run(model)
    errors = [r[0][1] for r in tool_results(model) if "error" in r[0][1]]
    assert len(errors) == 2 and "limit reached" in errors[0]["error"]
    assert sum(1 for c in model.calls if c["tools"] == ["SEARCH"]) == at.LIMITS["google_search"] == 3


# ---------------- run_python
def test_run_python_uses_code_execution_subcall():
    parts = [SimpleNamespace(executable_code=SimpleNamespace(code="print(2**100)"), code_execution_result=None),
             SimpleNamespace(executable_code=None, code_execution_result=SimpleNamespace(output="1267650600228229401496703205376\n"))]
    sub = lambda tools: SimpleNamespace(text="2^100 = 1267650600228229401496703205376", candidates=[SimpleNamespace(content=SimpleNamespace(parts=parts))]) \
        if tools == ["CODE"] else pytest.fail("wrong tool")
    model = Script([call("run_python", {"task": "2 to the 100"}), final("big")], sub=sub)
    _, _, _, progress, _ = run(model)
    out = tool_results(model)[0][0][1]
    assert out["code"] == "print(2**100)" and out["output"].startswith("1267650600228229401496703205376")
    assert progress == [("python", "Running code\u2026")]


def test_run_python_without_code_reports_error():
    sub = lambda tools: SimpleNamespace(text="cannot", candidates=[])
    model = Script([call("run_python", {"task": "x"}), final("k")], sub=sub)
    run(model)
    assert tool_results(model)[0][0][1]["error"]


# ---------------- generate_image
def test_generate_image_reuses_imagine_emits_media_and_caps_one():
    made = []
    imagine = lambda body: made.append(body) or {"images": [{"path": "u/1.png", "url": "https://signed", "mimeType": "image/png"}], "model": "m"}
    model = Script([call("generate_image", {"prompt": "a cat", "aspect_ratio": "16:9"}), call("generate_image", {"prompt": "dog"}), final("here")])
    text, agent, events, progress, _ = run(model, imagine=imagine)
    assert made == [{"prompt": "a cat", "aspectRatio": "16:9"}]
    assert events == [{"type": "media", "images": [{"path": "u/1.png", "url": "https://signed", "mimeType": "image/png"}], "model": "m"}]
    assert "limit reached" in tool_results(model)[1][0][1]["error"] and ("image", "Creating image\u2026") in progress


def test_generate_image_daily_cap_message_passes_through():
    import imagine as im

    def capped(body):
        raise im.ImagineError("Daily image limit reached.", 429)
    model = Script([call("generate_image", {"prompt": "a cat"}), final("sorry")])
    run(model, imagine=capped)
    assert "limit" in tool_results(model)[0][0][1]["error"].lower()


def test_generate_image_off():
    model = Script([call("generate_image", {"prompt": "a cat"}), final("no")])
    run(model)
    assert "not available" in tool_results(model)[0][0][1]["error"]


# ---------------- memory
def test_remember_emits_memory_event_and_recall_reads_request_memory():
    model = Script([call("remember", {"fact": "  Fareed  likes   cricket " + "x" * 300}), call("recall"), final("ok")])
    _, agent, events, _, _ = run(model, memory="- Name: Fareed")
    assert events[0]["type"] == "memory" and events[0]["fact"].startswith("Fareed likes cricket") and len(events[0]["fact"]) == 200
    assert tool_results(model)[1][0][1] == {"facts": "- Name: Fareed"}


# ---------------- github
class FakeGitHub:
    def __init__(self):
        self.opened = []

    def tree(self):
        return ["app/Main.kt", "README.md"]

    def read(self, path):
        return "fun main() {}"

    def search(self, text):
        return ["app/Main.kt"]

    def open_pull_request(self, files, title, description, branch):
        self.opened.append((files, title, branch))
        return 7, "https://github.com/o/r/pull/7"


def test_github_read_tools_and_propose_changes_open_pr():
    gh = FakeGitHub()
    model = Script([call("list_files", {"prefix": "app/"}), call("read_file", {"path": "app/Main.kt"}), call("search_code", {"query": "main"}),
                    call("propose_changes", {"title": "Hi", "description": "d", "files": [{"path": "app/Main.kt", "content": "x"}]})])
    text, _, _, progress, _ = run(model, github=gh)
    results = tool_results(model)
    assert results[0][0][1]["paths"] == ["app/Main.kt"] and results[1][0][1]["content"] == "fun main() {}"
    assert "#7" in text and gh.opened[0][2].startswith("dua-agent/")
    assert [p[0] for p in progress] == ["read", "read", "search_code", "pr"]


def test_github_tools_absent_without_token():
    model = Script([call("read_file", {"path": "a"}), call("propose_changes", {"title": "t", "description": "d", "files": []}), final("no")])
    run(model)
    assert all("not set up" in r[0][1]["error"] for r in tool_results(model))
    assert [t["name"] for t in at.declarations(False)][-1] == "recall"
    assert "propose_changes" in [t["name"] for t in at.declarations(True)]


# ---------------- loop caps
def test_step_cap_stops_loop():
    model = Script([call("current_time")] * 50)
    text, *_ = run(model)
    assert f"after {at.MAX_STEPS} steps" in text
    assert sum(1 for c in model.calls if c["tools"] == ["TOOLS"]) == at.MAX_STEPS == 12


def test_time_cap_stops_loop():
    ticks = iter([0, 0, at.MAX_SECONDS + 1, at.MAX_SECONDS + 2])
    model = Script([call("current_time")] * 5)
    e, _, _ = env(model)
    text, _ = at.run(e, ["u"], ["TOOLS"], lambda r: r, clock=lambda: next(ticks))
    assert "time limit" in text


def test_every_main_step_is_token_capped():
    model = Script([call("current_time"), final("x")])
    run(model)
    assert all(c["config"]["max_output_tokens"] == at.STEP_TOKENS for c in model.calls)


# ---------------- end to end through /api/chat
def test_agent_endpoint_requires_owner(monkeypatch):
    calls = []
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: calls.append(1))
    monkeypatch.setattr(engine, "owner_access_error", lambda: (engine.jsonify({"error": "Sign in"}), 401))
    r = engine.app.test_client().post("/api/chat", json={"model": "Dua", "stream": True, "mode": "agent", "messages": [{"role": "user", "text": "latest news"}]})
    assert r.status_code == 401 and calls == []


def test_agent_image_streams_media_event_with_owner_identity(monkeypatch):
    import imagine as im
    seen = {}
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "owner-id")
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.setattr(engine, "get_client", lambda: "client")
    monkeypatch.setattr(im, "enabled", lambda: True)
    monkeypatch.setattr(im, "handle", lambda body, token, user_id, client: seen.update(token=token, user=user_id) or {"images": [{"path": "p.png", "url": "https://s"}], "model": "m"})
    monkeypatch.setattr(at, "default_tools", lambda github, issues=False: ["TOOLS"])
    import coding_agent
    monkeypatch.setattr(coding_agent, "default_tool_response", lambda results: ("tool", results))
    model = Script([call("generate_image", {"prompt": "sunset"}), final("Here is your image.")])
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: model)
    r = engine.app.test_client().post("/api/chat", headers={"Authorization": "Bearer tok"},
                                      json={"model": "Dua", "stream": True, "mode": "agent", "messages": [{"role": "user", "text": "make an image of a sunset please"}]})
    evs = [json.loads(l[6:]) for l in r.get_data(as_text=True).splitlines() if l.startswith("data: ")]
    assert seen == {"token": "tok", "user": "owner-id"}
    assert any(e["type"] == "media" and e["images"][0]["path"] == "p.png" for e in evs) and evs[-1]["type"] == "done"
    assert any(e["type"] == "progress" and e["label"] == "Creating image\u2026" for e in evs)


def test_real_genai_declarations():
    pytest.importorskip("google.genai")
    names = [f.name for f in at.default_tools(True)[0].function_declarations]
    assert names[:3] == ["google_search", "fetch_url", "run_python"] and "propose_changes" in names
    assert at.code_tool().code_execution is not None and at.search_tool().google_search is not None
