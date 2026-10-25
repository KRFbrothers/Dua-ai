"""Daily Dua backup (Cloud Run job `dua-backup`, triggered by Cloud Scheduler at 03:00 IST).

1) Supabase tables -> gs://$BACKUP_BUCKET/db/YYYY-MM-DD/<table>.json.gz (dated; bucket lifecycle deletes after 30 days).
2) Supabase Storage bucket dua-files -> gs://$BACKUP_BUCKET/files/<path> (incremental mirror: only new or
   changed-size objects are copied; files deleted in the app are kept in the backup).

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (Secret Manager; needed because a scheduled job has no user login),
BACKUP_BUCKET (default dua-backups), BACKUP_TABLES (comma list), DUA_FILES_BUCKET (default dua-files),
BACKUP_FILES=0 to skip files. GCS auth uses the job's service account through the metadata server.
No third-party packages. Run locally: python backup.py (with the same env + GCS_TOKEN for a user token).
"""
import datetime
import gzip
import json
import os
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

TABLES = ("dua_cloud_snapshots", "agent_notes", "dua_daily_usage", "dua_imagine_usage", "dua_voice_usage",
          "dua_voice_sessions")
PAGE = 1000
MAX_ROWS = 200_000
MAX_FILE_BYTES = 60 * 1024 * 1024
TIMEOUT = 60
IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))
METADATA_TOKEN = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"


class BackupError(Exception):
    pass


def http(request, timeout=TIMEOUT):
    """(status, headers, body bytes). Replaced in tests."""
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, dict(response.headers), response.read()
    except HTTPError as error:
        body = error.read()
        error.close()
        return error.code, dict(error.headers or {}), body


class Backup:
    def __init__(self, env=None, send=None, today=None, log=print):
        env = os.environ if env is None else env
        self.url = env.get("SUPABASE_URL", "").strip().rstrip("/")
        self.key = env.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
        self.bucket = env.get("BACKUP_BUCKET", "dua-backups").strip()
        self.files_bucket = env.get("DUA_FILES_BUCKET", "dua-files").strip()
        self.tables = [t.strip() for t in env.get("BACKUP_TABLES", ",".join(TABLES)).split(",") if t.strip()]
        self.with_files = env.get("BACKUP_FILES", "1").strip() != "0"
        self.gcs_token = env.get("GCS_TOKEN", "").strip()
        self.send = send or http
        self.day = (today or datetime.datetime.now(IST)).strftime("%Y-%m-%d")
        self.log = log
        if not self.url.startswith("https://") or not self.key:
            raise BackupError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required.")
        if not self.bucket:
            raise BackupError("BACKUP_BUCKET is required.")

    # ---------------------------------------------------------------- helpers
    def _supabase(self, path, method="GET", body=None, accept="application/json"):
        headers = {"apikey": self.key, "Authorization": "Bearer " + self.key, "Accept": accept}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        status, _, raw = self.send(Request(self.url + path, data=data, method=method, headers=headers))
        if status != 200:
            raise BackupError(f"Supabase {method} {path.split('?')[0]} failed: HTTP {status}")
        return raw

    def _token(self):
        if not self.gcs_token:
            status, _, raw = self.send(Request(METADATA_TOKEN, headers={"Metadata-Flavor": "Google"}))
            if status != 200:
                raise BackupError("No Google credentials (metadata server).")
            self.gcs_token = json.loads(raw)["access_token"]
        return self.gcs_token

    def _gcs(self, url, method="GET", data=None, content_type=None):
        headers = {"Authorization": "Bearer " + self._token()}
        if content_type:
            headers["Content-Type"] = content_type
        return self.send(Request(url, data=data, method=method, headers=headers))

    def upload(self, name, data, content_type):
        url = (f"https://storage.googleapis.com/upload/storage/v1/b/{quote(self.bucket, safe='')}/o"
               f"?uploadType=media&name={quote(name, safe='')}")
        status, _, raw = self._gcs(url, "POST", data, content_type)
        if status != 200:
            raise BackupError(f"GCS upload {name} failed: HTTP {status}")

    def existing_size(self, name):
        url = f"https://storage.googleapis.com/storage/v1/b/{quote(self.bucket, safe='')}/o/{quote(name, safe='')}"
        status, _, raw = self._gcs(url)
        if status == 404:
            return None
        if status != 200:
            raise BackupError(f"GCS check {name} failed: HTTP {status}")
        return int(json.loads(raw).get("size", -1))

    # ---------------------------------------------------------------- tables
    def table_rows(self, table):
        rows = []
        while True:
            raw = self._supabase(f"/rest/v1/{quote(table)}?select=*&limit={PAGE}&offset={len(rows)}")
            page = json.loads(raw or b"[]")
            if not isinstance(page, list):
                raise BackupError(f"Unexpected response for {table}")
            rows.extend(page)
            if len(page) < PAGE:
                return rows
            if len(rows) >= MAX_ROWS:
                raise BackupError(f"{table} has more than {MAX_ROWS} rows; raise MAX_ROWS.")

    def backup_tables(self):
        summary = {}
        for table in self.tables:
            try:
                rows = self.table_rows(table)
            except BackupError as error:
                if "HTTP 404" in str(error):  # migration not applied: skip, keep going
                    self.log(f"[dua-backup] {table}: table not found, skipped")
                    summary[table] = None
                    continue
                raise
            payload = json.dumps({"table": table, "date": self.day, "rows": rows}, ensure_ascii=False).encode()
            self.upload(f"db/{self.day}/{table}.json.gz", gzip.compress(payload), "application/gzip")
            summary[table] = len(rows)
            self.log(f"[dua-backup] {table}: {len(rows)} rows")
        return summary

    # ---------------------------------------------------------------- files
    def list_files(self, prefix=""):
        """All objects under [prefix] in the Supabase Storage bucket (folders are walked)."""
        found, offset = [], 0
        while True:
            raw = self._supabase(f"/storage/v1/object/list/{quote(self.files_bucket)}", "POST",
                                 {"prefix": prefix, "limit": PAGE, "offset": offset,
                                  "sortBy": {"column": "name", "order": "asc"}})
            items = json.loads(raw or b"[]")
            for item in items:
                path = f"{prefix}{item['name']}"
                if item.get("id") is None:  # a folder
                    found.extend(self.list_files(path + "/"))
                else:
                    found.append((path, int((item.get("metadata") or {}).get("size") or -1),
                                  (item.get("metadata") or {}).get("mimetype") or "application/octet-stream"))
            if len(items) < PAGE:
                return found
            offset += PAGE

    def backup_files(self):
        copied = skipped = 0
        for path, size, mime in self.list_files():
            name = f"files/{path}"
            if size > MAX_FILE_BYTES:
                self.log(f"[dua-backup] skip large file {path} ({size} bytes)")
                skipped += 1
                continue
            if size >= 0 and self.existing_size(name) == size:
                skipped += 1
                continue
            data = self._supabase(f"/storage/v1/object/{quote(self.files_bucket)}/{quote(path)}", accept="*/*")
            self.upload(name, data, mime)
            copied += 1
        self.log(f"[dua-backup] files: {copied} copied, {skipped} unchanged/skipped")
        return {"copied": copied, "skipped": skipped}

    def run(self):
        result = {"date": self.day, "tables": self.backup_tables()}
        if self.with_files:
            result["files"] = self.backup_files()
        self.upload(f"db/{self.day}/_summary.json", json.dumps(result).encode(), "application/json")
        return result


def main():
    try:
        result = Backup().run()
    except BackupError as error:
        print(f"[dua-backup] FAILED: {error}", flush=True)
        return 1
    print(f"[dua-backup] OK {json.dumps(result)}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
