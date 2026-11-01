"""Opt-in private SQL draft checkpoints; never restores credentials or processes."""
import json
import os
import re
import secrets
import time

import dua_engine.agent.agent_store as agent_store
import dua_engine.coding.coding_agent as coding

MAX_WORKSPACES = 20
TTL_SECONDS = 7 * 86400
ID = re.compile(r"[0-9a-f]{32}")


def enabled():
    return os.environ.get("DUA_CODING_CHECKPOINTS", "0").strip() == "1" and bool(agent_store.database_url())


def require_owner(owner):
    if not isinstance(owner, str) or not owner or len(owner) > 128:
        raise coding.CodingError("Authenticated owner required for workspace checkpoints.")
    if not enabled():
        raise coding.CodingError("Workspace checkpoints are not enabled. Configure private ADK SQL storage and DUA_CODING_CHECKPOINTS=1.")


def _table():
    metadata, _ = agent_store.tables()
    return metadata.tables["dua_coding_workspaces"]


def _engine():
    try:
        return agent_store.engine()
    except Exception:
        raise coding.CodingError("Private workspace checkpoint database configuration is unavailable.") from None


def _bound_query(connection):
    if connection.dialect.name == "postgresql":
        connection.exec_driver_sql("SET LOCAL statement_timeout = '5000ms'")


def save(workspace, owner):
    require_owner(owner)
    from sqlalchemy import delete, insert, select, update, func
    files = coding.validate_changes([{"path": p, "content": c} for p, c in sorted(workspace.staged.items())])
    target = workspace.pr_target
    if target is not None:
        import dua_engine.coding.coding_pr as coding_pr
        target = coding_pr.clean_target(target, workspace.base_sha)
    payload = {"version": 2, "files": files, "pr_target": target}
    # JSON escaping can expand source. The file/byte caps are authoritative;
    # this independent cap also bounds the serialized database document.
    if len(json.dumps(payload).encode("utf-8")) > coding.MAX_TOTAL_CHANGE_BYTES * 6 + 16_000:
        raise coding.CodingError("Workspace checkpoint is too large.")
    table, db = _table(), _engine()
    now, checkpoint_id = time.time(), workspace.checkpoint_id or secrets.token_hex(16)
    try:
        with db.begin() as con:
            _bound_query(con)
            agent_store.lock_owner(con, owner, "coding-workspaces")
            con.execute(delete(table).where(table.c.owner == owner, table.c.expires <= now))
            if workspace.checkpoint_id:
                revision = workspace.checkpoint_revision + 1
                changed = con.execute(update(table).where(table.c.id == checkpoint_id, table.c.owner == owner,
                    table.c.repository == workspace.github.repo, table.c.base_sha == workspace.base_sha,
                    table.c.revision == workspace.checkpoint_revision, table.c.expires > now).values(
                    revision=revision, updated=now, expires=now + TTL_SECONDS, payload=payload))
                if changed.rowcount != 1:
                    raise coding.CodingError("Checkpoint changed elsewhere or expired. Restore the latest version in a fresh task; this draft was not overwritten.")
            else:
                count = con.execute(select(func.count()).select_from(table).where(table.c.owner == owner)).scalar_one()
                if count >= MAX_WORKSPACES:
                    raise coding.CodingError("Workspace checkpoint quota reached (20 per owner). Reuse a checkpoint or wait for expiry.")
                revision = 1
                con.execute(insert(table).values(id=checkpoint_id, owner=owner, repository=workspace.github.repo,
                    base_sha=workspace.base_sha, revision=revision, updated=now, expires=now + TTL_SECONDS, payload=payload))
        workspace.checkpoint_id, workspace.checkpoint_revision = checkpoint_id, revision
        workspace.saved_fingerprint = workspace.fingerprint()
        return {"id": checkpoint_id, "revision": revision, "saved": True, "expires": now + TTL_SECONDS}
    except coding.CodingError:
        raise
    except Exception:
        raise coding.CodingError("Workspace checkpoint storage is unavailable; check init-db and the private database configuration.") from None
    finally:
        db.dispose()


def load(owner, checkpoint_id, repository):
    require_owner(owner)
    if not isinstance(checkpoint_id, str) or not ID.fullmatch(checkpoint_id):
        raise coding.CodingError("Invalid workspace checkpoint ID.")
    from sqlalchemy import select
    table, db = _table(), _engine()
    try:
        with db.connect() as con:
            _bound_query(con)
            row = con.execute(select(table).where(table.c.id == checkpoint_id, table.c.owner == owner,
                table.c.repository == repository, table.c.expires > time.time())).mappings().first()
        if row is None:
            raise coding.CodingError("Workspace checkpoint not found or expired for this owner/repository.")
        payload = row["payload"]
        if (not isinstance(payload, dict) or type(payload.get("version")) is not int or
                payload["version"] not in (1, 2) or
                (payload["version"] == 2 and "pr_target" not in payload) or
                (payload["version"] == 1 and payload.get("pr_target") is not None)):
            raise coding.CodingError("Unsupported workspace checkpoint version.")
        files = coding.validate_changes(payload.get("files"))
        if not re.fullmatch(r"[0-9a-f]{40}", row["base_sha"]):
            raise coding.CodingError("Invalid workspace checkpoint base.")
        if isinstance(row["revision"], bool) or not isinstance(row["revision"], int) or row["revision"] < 1:
            raise coding.CodingError("Invalid workspace checkpoint revision.")
        target = payload.get("pr_target") if payload["version"] == 2 else None
        if target is not None:
            import dua_engine.coding.coding_pr as coding_pr
            target = coding_pr.clean_target(target, row["base_sha"])
        return {"id": row["id"], "revision": row["revision"], "base_sha": row["base_sha"], "files": files, "pr_target": target}
    except coding.CodingError:
        raise
    except Exception:
        raise coding.CodingError("Workspace checkpoint storage is unavailable.") from None
    finally:
        db.dispose()


def list_owned(owner, repository):
    require_owner(owner)
    from sqlalchemy import select
    table, db = _table(), _engine()
    try:
        with db.connect() as con:
            _bound_query(con)
            rows = con.execute(select(table.c.id, table.c.revision, table.c.base_sha, table.c.updated, table.c.expires).where(
                table.c.owner == owner, table.c.repository == repository, table.c.expires > time.time()).order_by(
                table.c.updated.desc()).limit(MAX_WORKSPACES)).mappings().all()
        return {"checkpoints": [dict(row) for row in rows]}
    except Exception:
        raise coding.CodingError("Workspace checkpoint storage is unavailable.") from None
    finally:
        db.dispose()
