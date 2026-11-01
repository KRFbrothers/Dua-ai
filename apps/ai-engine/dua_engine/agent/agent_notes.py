"""Dua Agent self-learning notes, stored in Supabase table agent_notes (migration 202610160001).

Separate from the app's user-approved Memory. Every call uses the signed-in owner's own token plus the anon key,
so Row Level Security keeps notes owner-only; the table trigger prunes to MAX_NOTES (oldest first).
Off when AGENT_NOTES=0 or Supabase is not configured. Secrets, tokens, keys and emails are never stored.
"""
import json
import os
import re
import uuid
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request

from owner_auth import auth_opener

MAX_NOTES = 200
NOTE_CHARS = 300
PROMPT_NOTES = 20
PROMPT_CHARS = 8000  # about 2k tokens
MAX_LESSONS = 3
EXTRACT_TOKENS = 1024
TIMEOUT = 5  # hard timeout for every Supabase notes call
KINDS = ("preference", "worked", "failed", "repo", "fact")
NOTE_TOOLS_LIMIT = {"save_note": 3, "search_notes": 3, "delete_note": 3}

SECRET_PATTERNS = [re.compile(p, re.IGNORECASE) for p in (
    r"[^\s@]+@[^\s@]+\.[^\s@]+",                          # email
    r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}",         # JWT
    r"\b(sk|pk|rk|xai|sb|ghp|gho|ghs|ghu|github_pat|glpat|AIza|AKIA|ya29)[-_]?[A-Za-z0-9_\-]{10,}",
    r"-----BEGIN [A-Z ]*PRIVATE KEY",
    r"\bbearer\s+\S{8,}",
    r"\b(password|passwd|secret|api[_ -]?key|token|private[_ -]?key)\b\s*[:=]\s*\S+",
    r"\b[A-Fa-f0-9]{32,}\b",                              # long hex
    r"\b[A-Za-z0-9+/_-]{40,}={0,2}",                      # long base64-like blob
)]

EXTRACT_PROMPT = (
    "You maintain short private notes that help an AI agent work better for this one user next time.\n"
    "From the task below, extract 0 to 3 DURABLE lessons: user preferences, approaches that worked or failed, "
    "facts about the user's repository/project. Skip anything one-off, obvious, already in the existing notes, "
    "or sensitive (never secrets, tokens, keys, passwords, emails, phone numbers). Each note max 200 characters.\n"
    "If a new lesson contradicts or updates an existing note, set \"replaces\" to that note's id so the old one is removed.\n"
    'Reply with JSON only: {{"lessons": [{{"note": "...", "kind": "preference|worked|failed|repo|fact", "replaces": "optional existing id"}}]}}\n\n'
    "Existing notes:\n{existing}\n\nUser request:\n{request}\n\nAgent steps:\n{steps}\n\nFinal answer:\n{answer}"
)


def enabled():
    return os.environ.get("AGENT_NOTES", "1").strip() != "0" and bool(_config())


def _config():
    url = os.environ.get("SUPABASE_URL", "").strip().rstrip("/")
    key = (os.environ.get("SUPABASE_PUBLISHABLE_KEY") or os.environ.get("SUPABASE_ANON_KEY", "")).strip()
    return (url, key) if url.startswith("https://") and key else None


def clean(text):
    return " ".join(str(text or "").split())[:NOTE_CHARS]


def unsafe(text):
    return any(p.search(text) for p in SECRET_PATTERNS)


def _words(text):
    return {w for w in re.findall(r"[a-z0-9]+", text.lower()) if len(w) > 2}


def duplicate(note, notes):
    words = _words(note)
    for existing in notes:
        other = _words(existing["note"])
        if existing["note"].strip().lower() == note.strip().lower():
            return True
        if words and other and len(words & other) / len(words | other) >= 0.7:
            return True
    return False


def relevant(notes, query, limit=PROMPT_NOTES, max_chars=PROMPT_CHARS):
    """Most relevant first (word overlap, then newest); preferences always score a little."""
    words = _words(query)
    ranked = sorted(enumerate(notes), key=lambda pair: (-(len(words & _words(pair[1]["note"])) +
                                                          (0.5 if pair[1].get("kind") == "preference" else 0)), pair[0]))
    chosen, used = [], 0
    for _, note in ranked:
        if len(chosen) >= limit or used + len(note["note"]) > max_chars:
            break
        chosen.append(note)
        used += len(note["note"])
    return chosen


def prompt_block(notes):
    if not notes:
        return ""
    lines = "\n".join(f"- [{n.get('kind', 'fact')}] {n['note']}" for n in notes)
    return ("\n\nYour own notes from earlier tasks for this user (hints, may be outdated; the user's request wins):\n"
            + lines)


class NotesStore:
    """Owner-scoped REST access. [opener] is injectable for tests."""

    def __init__(self, token, opener=None):
        self.url, self.key = _config()
        self.token = token
        self.opener = opener or auth_opener
        self._cache = None
        self.unavailable = None
        self.semantic = None

    def _request(self, path, method="GET", body=None, prefer=None):
        headers = {"apikey": self.key, "Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if prefer:
            headers["Prefer"] = prefer
        request = Request(self.url + "/rest/v1/agent_notes" + path, method=method,
                          data=json.dumps(body).encode() if body is not None else None, headers=headers)
        with self.opener.open(request, timeout=TIMEOUT) as response:
            raw = response.read(512 * 1024)
        return json.loads(raw) if raw else None

    def list(self):
        if self._cache is None:
            rows = self._request(f"?select=id,note,kind,source,created_at&order=created_at.desc&limit={MAX_NOTES}")
            self._cache = [r for r in rows or [] if isinstance(r, dict) and isinstance(r.get("note"), str)]
        return self._cache

    def add(self, note, kind="fact", source="agent"):
        note = clean(note)
        if not note:
            return {"error": "Empty note."}
        if unsafe(note):
            return {"error": "Not saved: notes cannot contain secrets, tokens, keys or email addresses."}
        if duplicate(note, self.list()):
            return {"saved": False, "note": "Already noted."}
        kind = kind if kind in KINDS else "fact"
        rows = self._request("", "POST", {"note": note, "kind": kind, "source": source}, "return=representation")
        row = rows[0] if isinstance(rows, list) and rows else {"note": note, "kind": kind}
        self._cache.insert(0, row)
        semantic_status = {}
        if self.semantic is not None and row.get("id"):
            try:
                self.semantic.add("Agent note", note, row["id"], kind="note")
                semantic_status["semanticIndexed"] = True
            except Exception:
                semantic_status.update(semanticIndexed=False, note="Note saved; semantic indexing failed. Lexical retrieval remains available.")
        return {"saved": True, "id": row.get("id"), **semantic_status}

    def recall(self, query, limit=10, max_chars=4000):
        """Semantic matches first (when configured), then lexical ones; falls back to lexical alone."""
        notes = self.list()
        found = relevant(notes, query, limit=limit, max_chars=max_chars)
        if self.semantic is not None and query.strip():
            try:
                ids = [m["id"] for m in self.semantic.search(query, kind="note", limit=limit)]
                live = {n.get("id"): n for n in notes}
                semantic = [live[i] for i in ids if i in live]
                found = (semantic + [n for n in found if n.get("id") not in ids])[:limit]
            except Exception:
                pass
        return found

    def search(self, query):
        found = self.recall(query)
        return [{"id": n.get("id"), "note": n["note"], "kind": n.get("kind"), "created_at": n.get("created_at")}
                for n in found]

    def reindex(self, offset=0, limit=20):
        if self.semantic is None:
            raise ValueError("Semantic memory is not configured")
        if type(offset) is not int or type(limit) is not int or not 0 <= offset <= MAX_NOTES or not 1 <= limit <= 20:
            raise ValueError("Choose an offset within 200 notes and a batch of 1–20")
        rows=self.list()[offset:offset+limit]
        done=0
        for row in rows:
            self.semantic.add("Agent note",row["note"],row["id"],kind="note")
            done+=1
        return {"indexed":done,"nextOffset":offset+done,"more":len(self.list())>offset+done}

    def delete(self, note_id):
        try:
            note_id = str(uuid.UUID(str(note_id)))
        except ValueError:
            return {"error": "Unknown note id."}
        rows = self._request(f"?id=eq.{quote(note_id)}", "DELETE", prefer="return=representation")
        if self._cache is not None:
            self._cache = [n for n in self._cache if n.get("id") != note_id]
        semantic_status = {}
        if self.semantic is not None:
            try:
                self.semantic.delete(note_id)
                semantic_status["semanticIndexDeleted"] = True
            except Exception:
                semantic_status.update(semanticIndexDeleted=False, note="Note deleted; semantic index cleanup is incomplete.")
        return {"deleted": bool(rows), **semantic_status} if rows else {"error": "No such note."}


def for_owner(owner, opener=None):
    if not enabled() or not owner.get("id") or not owner.get("token"):
        return None
    return NotesStore(owner["token"], opener)


UNAVAILABLE_NOTE = ("\n\nYour private notes are UNAVAILABLE right now ({reason}). If the user asks about notes or "
                    "their preferences, say honestly that your notes could not be loaded right now (it may need setup); "
                    "never claim that you do not keep notes.")
OFF_NOTE = ("\n\nYour private notes are turned off on this server (AGENT_NOTES=0). If asked, say notes are turned off; "
            "the app's own Memory still works.")


def failure_reason(error):
    """Plain reason without data: missing_table (migration not applied), auth, or unreachable."""
    code = getattr(error, "code", None)
    body = ""
    try:
        body = error.read(2048).decode("utf-8", "replace") if isinstance(error, HTTPError) else ""
    except Exception:
        pass
    if code == 404 or "PGRST205" in body or "42P01" in body or "does not exist" in body:
        return "missing_table"
    if code in (401, 403) or "42501" in body:
        return "not_permitted"
    return "unreachable"


def probe(store, log=print):
    """(ready, reason). Reads at most one id; never returns note data."""
    if store is None:
        return False, "off" if os.environ.get("AGENT_NOTES", "1").strip() == "0" else "not_configured"
    try:
        store._request("?select=id&limit=1")
        return True, "ok"
    except Exception as error:
        reason = failure_reason(error)
        log(f"[dua-agent-notes] notes unavailable: {reason}")
        return False, reason


def load_prompt(store, query, log=print):
    """Notes block for the system prompt; never fails the task. Unavailable notes give an honest notice + log."""
    if store is None:
        return OFF_NOTE if os.environ.get("AGENT_NOTES", "1").strip() == "0" else ""
    try:
        if hasattr(store, "recall"):
            return prompt_block(store.recall(query, PROMPT_NOTES, PROMPT_CHARS))
        return prompt_block(relevant(store.list(), query))
    except Exception as error:
        reason = failure_reason(error)
        log(f"[dua-agent-notes] could not load notes: {reason}"
            + (" (apply infra/database/migrations/202610160001_dua_agent_notes.sql)" if reason == "missing_table" else ""))
        store.unavailable = reason
        return UNAVAILABLE_NOTE.format(reason="the notes table is not set up yet" if reason == "missing_table"
                                       else "notes storage could not be reached")


def learn(store, model, request_text, steps, answer, log=print):
    """After a task: one cheap Flash call -> 0-3 lessons -> filtered, deduped, saved. Returns saved count."""
    if store is None or store_unavailable(store) or not (request_text or "").strip():
        return 0
    try:
        existing = "\n".join(f"- [{n.get('id')}] {n['note']}" for n in store.list()[:40]) or "(none)"
        prompt = EXTRACT_PROMPT.format(existing=existing, request=request_text[:3000], steps=(steps or "")[:3000],
                                       answer=(answer or "")[:3000])
        response = model.generate_content(prompt, generation_config={
            "max_output_tokens": EXTRACT_TOKENS, "temperature": 0.0, "thinking_level": "low",
            "response_mime_type": "application/json"})
        data = json.loads(getattr(response, "text", "") or "{}")
        lessons = data.get("lessons") if isinstance(data, dict) else None
        saved = 0
        for lesson in (lessons if isinstance(lessons, list) else [])[:MAX_LESSONS]:
            if not isinstance(lesson, dict):
                continue
            old_id = lesson.get("replaces")
            known = {n.get("id") for n in store.list()}
            if store.add(lesson.get("note"), lesson.get("kind"), "auto").get("saved"):
                saved += 1
                if isinstance(old_id, str) and old_id in known:
                    store.delete(old_id)
        return saved
    except Exception as error:  # learning is best-effort; never surface details
        log(f"[dua-agent-notes] learning skipped ({type(error).__name__})")
        return 0


def store_unavailable(store):
    return bool(getattr(store, "unavailable", None))


TOOLS = [
    {"name": "save_note", "description": "Save one short durable lesson for future tasks (your private notes, not the "
     "user's Memory): a preference, what worked/failed, or a repo fact. Never secrets or emails. Max 300 characters.",
     "parameters": {"type": "OBJECT", "properties": {"note": {"type": "STRING"},
                                                     "kind": {"type": "STRING", "enum": list(KINDS)}},
                    "required": ["note"]}},
    {"name": "search_notes", "description": "Search your private notes from earlier tasks.",
     "parameters": {"type": "OBJECT", "properties": {"query": {"type": "STRING"}}, "required": ["query"]}},
    {"name": "delete_note", "description": "Delete one of your own notes by id (from search_notes) if it is wrong or outdated.",
     "parameters": {"type": "OBJECT", "properties": {"id": {"type": "STRING"}}, "required": ["id"]}},
]


def run_tool(store, name, args):
    if store is None:
        return {"error": "Agent notes are turned off."}
    if store_unavailable(store):
        return {"error": "Notes are unavailable right now (storage not set up or not reachable). Tell the user honestly."}
    if name == "save_note":
        return store.add(args.get("note"), args.get("kind", "fact"), "agent")
    if name == "search_notes":
        return {"notes": store.search(str(args.get("query", "")))}
    return store.delete(args.get("id"))
