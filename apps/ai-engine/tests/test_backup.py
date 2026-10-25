import datetime
import gzip
import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import backup  # noqa: E402

ENV = {"SUPABASE_URL": "https://p.supabase.co", "SUPABASE_SERVICE_ROLE_KEY": "srk", "BACKUP_BUCKET": "dua-backups"}
DAY = datetime.datetime(2026, 10, 17, 3, 0, tzinfo=backup.IST)


class Fake:
    def __init__(self, tables=None, files=None, gcs=None, missing=()):
        self.tables, self.files, self.gcs, self.missing = tables or {}, files or {}, dict(gcs or {}), missing
        self.requests = []

    def __call__(self, req, timeout=None):
        self.requests.append(req)
        url, method = req.full_url, req.get_method()
        if url.startswith(backup.METADATA_TOKEN):
            assert req.get_header("Metadata-flavor") == "Google"
            return 200, {}, b'{"access_token": "gtok"}'
        if "/rest/v1/" in url:
            assert req.get_header("Authorization") == "Bearer srk"
            table = url.split("/rest/v1/")[1].split("?")[0]
            if table in self.missing:
                return 404, {}, b"{}"
            offset = int(url.split("offset=")[1])
            return 200, {}, json.dumps(self.tables.get(table, [])[offset:offset + backup.PAGE]).encode()
        if "/storage/v1/object/list/" in url:
            prefix = json.loads(req.data)["prefix"]
            names = sorted({p[len(prefix):].split("/")[0] + ("/" if "/" in p[len(prefix):] else "")
                            for p in self.files if p.startswith(prefix)})
            items = [{"name": n.rstrip("/"), "id": None} if n.endswith("/") else
                     {"name": n, "id": "x", "metadata": {"size": len(self.files[prefix + n]), "mimetype": "image/png"}}
                     for n in names]
            return 200, {}, json.dumps(items).encode()
        if "/storage/v1/object/" in url:
            return 200, {}, self.files[url.split("/dua-files/")[1].replace("%40", "@")]
        assert req.get_header("Authorization") == "Bearer gtok"
        if "upload/storage" in url:
            name = url.split("name=")[1].replace("%2F", "/")
            self.gcs[name] = req.data
            return 200, {}, b"{}"
        name = url.split("/o/")[1].replace("%2F", "/")
        if name in self.gcs:
            return 200, {}, json.dumps({"size": str(len(self.gcs[name]))}).encode()
        return 404, {}, b"{}"


def run(fake, **env):
    logs = []
    result = backup.Backup(dict(ENV, **env), send=fake, today=DAY, log=logs.append).run()
    return result, logs


def test_requires_service_role_key():
    with pytest.raises(backup.BackupError):
        backup.Backup({"SUPABASE_URL": "https://p.supabase.co"}, send=Fake())


def test_tables_are_exported_dated_gzip_with_paging(monkeypatch):
    monkeypatch.setattr(backup, "PAGE", 2)
    rows = [{"id": i} for i in range(5)]
    fake = Fake(tables={"agent_notes": rows}, missing=("dua_voice_sessions",))
    result, logs = run(fake, BACKUP_FILES="0")
    data = json.loads(gzip.decompress(fake.gcs["db/2026-10-17/agent_notes.json.gz"]))
    assert data["rows"] == rows and data["date"] == "2026-10-17"
    assert result["tables"]["agent_notes"] == 5 and result["tables"]["dua_voice_sessions"] is None
    assert "db/2026-10-17/_summary.json" in fake.gcs and "files" not in result
    assert any("not found" in line for line in logs)


def test_files_are_mirrored_incrementally():
    files = {"u1/a.png": b"aaaa", "u1/sub/b.png": b"bb"}
    fake = Fake(files=files, gcs={"files/u1/a.png": b"aaaa"})
    result, _ = run(fake, BACKUP_TABLES="agent_notes")
    assert result["files"] == {"copied": 1, "skipped": 1}
    assert fake.gcs["files/u1/sub/b.png"] == b"bb"


def test_failure_returns_nonzero(monkeypatch, capsys):
    monkeypatch.setattr(backup.Backup, "__init__", lambda self, *a, **k: (_ for _ in ()).throw(backup.BackupError("x")))
    assert backup.main() == 1 and "FAILED" in capsys.readouterr().out


def test_server_error_stops_backup():
    class Broken(Fake):
        def __call__(self, req, timeout=None):
            if "/rest/v1/" in req.full_url:
                return 500, {}, b""
            return super().__call__(req, timeout)
    with pytest.raises(backup.BackupError):
        run(Broken(), BACKUP_FILES="0")
