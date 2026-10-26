"""Owner-scoped GCS references; no cloud calls, uploads or credentials required."""
import base64
import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine

OWNER = "owner-123"
BUCKET = "dua-large-files-test"
URI = f"gs://{BUCKET}/{OWNER}/report.pdf"
MIB = 1024 * 1024


def attachment(**changes):
    return {"uri": URI, "mimeType": "application/pdf", "size": 20 * MIB, **changes}


def decode(item, owner=OWNER):
    with engine.app.test_request_context():
        engine.g.owner_id = owner
        return engine.decode_attachments([item])


@pytest.fixture(autouse=True)
def config(monkeypatch):
    monkeypatch.setattr(engine, "LARGE_FILES", "1")
    monkeypatch.setattr(engine, "LARGE_FILES_BUCKET", BUCKET)
    monkeypatch.setattr(engine, "LARGE_FILE_MAX_BYTES", 100 * MIB)
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)


@pytest.fixture
def client(monkeypatch):
    calls = []
    def generate(contents, stream=False, **kwargs):
        calls.append(contents)
        response = SimpleNamespace(text="Summary", candidates=[])
        return iter([response]) if stream else response
    monkeypatch.setattr(engine, "verify_owner", lambda _authorization: OWNER)
    monkeypatch.setattr(engine, "get_model", lambda *_args: SimpleNamespace(generate_content=generate))
    return engine.app.test_client(), calls


@pytest.mark.parametrize("flag", ["0", "", "true", "2"])
def test_flag_off_rejects_uri_with_400(client, monkeypatch, flag):
    monkeypatch.setattr(engine, "LARGE_FILES", flag)
    http, calls = client
    response = http.post("/api/chat", json={"messages": [{"role": "user", "text": "Summarize"}],
                                             "attachments": [attachment()]})
    assert response.status_code == 400
    assert calls == []


@pytest.mark.parametrize("uri", [
    "gs://other-bucket/owner-123/report.pdf", f"gs://{BUCKET}/other-owner/report.pdf",
    f"gs://{BUCKET}/{OWNER}-suffix/report.pdf", f"gs://{BUCKET}/{OWNER}",
    f"gs://{BUCKET}/{OWNER}/", f"gs://{BUCKET}/{OWNER}/../other-owner/report.pdf",
    f"gs://{BUCKET}/{OWNER}/a/../../report.pdf", f"gs://{BUCKET}/{OWNER}//report.pdf",
    f"gs://{BUCKET}/{OWNER}/%2e%2e/report.pdf", f"gs://{BUCKET}/{OWNER}/a%2fb.pdf",
    f"gs://{BUCKET}/{OWNER}/a\\b.pdf", f"gs://{BUCKET}/{OWNER}/a.pdf?alt=media",
    f"gs://{BUCKET}/{OWNER}/a.pdf#fragment", f"gs://{BUCKET}@evil/{OWNER}/a.pdf",
    f"https://storage.googleapis.com/{BUCKET}/{OWNER}/report.pdf", "file:///tmp/report.pdf",
    "gs://" + BUCKET + "/" + OWNER + "/bad\nfile.pdf", " " + URI, None, 42,
])
def test_only_canonical_owned_bucket_uris_are_accepted(uri):
    with pytest.raises(engine.BadRequest):
        decode(attachment(uri=uri))


@pytest.mark.parametrize("mime", ["application/zip", "application/pdf-extra", "image/", "text/plain; charset=utf-8", None, 42])
def test_bad_mime_is_rejected(mime):
    with pytest.raises(engine.BadRequest):
        decode(attachment(mimeType=mime))


@pytest.mark.parametrize("size", [None, True, False, "20971520", 20.5, 0, -1, 4 * MIB, 100 * MIB + 1])
def test_size_is_a_bounded_large_integer(size):
    with pytest.raises(engine.BadRequest):
        decode(attachment(size=size))


@pytest.mark.parametrize("size", [4 * MIB + 1, 100 * MIB])
def test_uri_size_boundaries(size):
    assert decode(attachment(size=size))[0][0].uri == URI


def test_missing_bucket_owner_and_ambiguous_inline_data_are_rejected(monkeypatch):
    with pytest.raises(engine.BadRequest):
        decode(attachment(), owner=None)
    with pytest.raises(engine.BadRequest):
        decode(attachment(data="AA=="))
    monkeypatch.setattr(engine, "LARGE_FILES_BUCKET", "")
    with pytest.raises(engine.BadRequest):
        decode(attachment())


@pytest.mark.parametrize("bucket", ["gs://bucket", "bucket/owner", "bucket:443", "UPPERCASE", "bucket@evil"])
def test_bucket_configuration_must_be_a_bucket_name(monkeypatch, bucket):
    monkeypatch.setattr(engine, "LARGE_FILES_BUCKET", bucket)
    with pytest.raises(engine.BadRequest):
        decode(attachment(uri=f"gs://{bucket}/{OWNER}/report.pdf"))


def test_valid_marker_becomes_sdk_uri_part():
    marker, mime = decode(attachment())[0]
    assert isinstance(marker, tuple)
    part = engine.make_part(data=marker, mime_type=mime)
    assert part.file_data.file_uri == URI
    assert part.file_data.mime_type == "application/pdf"
    assert part.inline_data is None


@pytest.mark.parametrize("flag", ["0", "1"])
@pytest.mark.parametrize("size", [1, 4 * MIB])
def test_small_inline_data_is_unchanged_at_the_exact_limit(monkeypatch, flag, size):
    monkeypatch.setattr(engine, "LARGE_FILES", flag)
    raw = b"x" * size
    decoded = decode({"mimeType": "text/plain", "data": base64.b64encode(raw).decode()})
    assert decoded == [(raw, "text/plain")]
    part = engine.make_part(data=decoded[0][0], mime_type=decoded[0][1])
    assert part.inline_data.data == raw and part.file_data is None


def test_inline_limit_and_attachment_count_are_not_raised():
    assert engine.MAX_ATTACHMENT_BYTES == 4 * MIB
    with pytest.raises(engine.BadRequest):
        decode({"mimeType": "text/plain", "data": base64.b64encode(b"x" * (4 * MIB + 1)).decode()})
    with pytest.raises(engine.BadRequest):
        engine.decode_attachments([attachment()] * (engine.MAX_ATTACHMENTS + 1))


@pytest.mark.parametrize("stream", [False, True])
def test_top_level_and_history_uri_parts_reach_gemini(client, stream):
    http, calls = client
    response = http.post("/api/chat", json={"messages": [
        {"role": "user", "text": "First", "attachments": [attachment()]},
        {"role": "model", "text": "Read it"}, {"role": "user", "text": "Compare"}],
        "attachments": [attachment(uri=URI.replace("report", "second"))], "stream": stream})
    assert response.status_code == 200
    assert "Summary" in response.get_data(as_text=True)
    assert calls[0][0].parts[1].file_data.file_uri == URI
    assert calls[0][-1].parts[1].file_data.file_uri == URI.replace("report", "second")


def test_history_is_revalidated_and_flag_off_rejects_it(client, monkeypatch):
    http, calls = client
    for item in [attachment(uri=f"gs://{BUCKET}/another-owner/a.pdf"), attachment()]:
        if item["uri"] == URI:
            monkeypatch.setattr(engine, "LARGE_FILES", "0")
        response = http.post("/api/chat", json={"messages": [
            {"role": "user", "text": "First", "attachments": [item]},
            {"role": "model", "text": "OK"}, {"role": "user", "text": "Again"}]})
        assert response.status_code == 400
    assert calls == []


@pytest.mark.parametrize("history", [False, True])
def test_free_model_falls_back_to_gemini_with_notice(client, monkeypatch, history):
    import openrouter
    monkeypatch.setattr(openrouter, "enabled", lambda: True)
    monkeypatch.setattr(openrouter, "allowed", lambda _name: True)
    monkeypatch.setattr(openrouter, "stream", lambda *args, **kwargs: pytest.fail("URI reached a free model"))
    http, calls = client
    body = {"messages": [{"role": "user", "text": "Summarize"}], "stream": True, "openrouter": "test/free"}
    if history:
        body["messages"][0]["attachments"] = [attachment()]
    else:
        body["attachments"] = [attachment()]
    response = http.post("/api/chat", json=body)
    events = [json.loads(line[6:]) for line in response.get_data(as_text=True).splitlines() if line.startswith("data: ")]
    assert response.status_code == 200 and calls
    assert "Gemini" in events[0]["message"] and events[0]["type"] == "notice"
    assert events[-1] == {"type": "done"}


def test_agent_worker_retains_owner_for_historical_uri_parts(client, monkeypatch):
    rebuilt = []
    def producer(messages, attachments, system, owner, **kwargs):
        def produce(enqueue):
            rebuilt.extend(engine.build_contents(messages, attachments))
            enqueue("chunk", SimpleNamespace(text="Agent summary", candidates=[]))
            enqueue("done")
        return produce
    monkeypatch.setattr(engine, "agent_producer", producer)
    http, _calls = client
    response = http.post("/api/chat", json={"mode": "agent", "stream": True, "messages": [
        {"role": "user", "text": "Summarize", "attachments": [attachment()]}]})
    assert "Agent summary" in response.get_data(as_text=True)
    assert rebuilt[0].parts[1].file_data.file_uri == URI


@pytest.mark.parametrize("flag", ["0", "1"])
def test_inline_free_model_behavior_is_unchanged(client, monkeypatch, flag):
    import openrouter
    monkeypatch.setattr(engine, "LARGE_FILES", flag)
    monkeypatch.setattr(openrouter, "enabled", lambda: True)
    monkeypatch.setattr(openrouter, "allowed", lambda _name: True)
    forwarded = []
    def stream(name, system, messages, enqueue, **kwargs):
        forwarded.append(name)
        enqueue("chunk", SimpleNamespace(text="Free reply", candidates=[]))
        enqueue("done")
    monkeypatch.setattr(openrouter, "stream", stream)
    http, calls = client
    response = http.post("/api/chat", json={"messages": [{"role": "user", "text": "Hi"}],
        "attachments": [{"mimeType": "text/plain", "data": "aGk="}], "stream": True, "openrouter": "test/free"})
    assert "Free models read text only" in response.get_data(as_text=True)
    assert forwarded == ["test/free"] and calls == []


def test_owner_auth_still_runs_before_uri_handling(client, monkeypatch):
    from owner_auth import OwnerAuthError
    def deny(_authorization):
        raise OwnerAuthError("Sign in", 401)
    monkeypatch.setattr(engine, "verify_owner", deny)
    http, calls = client
    assert http.post("/api/chat", json={"attachments": [attachment()]}).status_code == 401
    assert calls == []
