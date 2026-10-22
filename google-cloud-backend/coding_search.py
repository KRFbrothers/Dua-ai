"""Literal text search over the validated pinned archive plus request-local edits."""
import json
import re

from agent_policy import filter_text
import coding_agent as coding
import coding_snapshot

MAX_QUERY_CHARS = 200
MAX_TEXT_FILE_BYTES = 1_000_000
MAX_RESULT_BYTES = 16_000
SNIPPET_CHARS = 320


def validate(args):
    if not isinstance(args, dict):
        raise coding.CodingError("Search arguments must be an object.")
    query = args.get("query")
    path = args.get("path", "")
    case_sensitive = args.get("case_sensitive", True)
    limit = args.get("max_results", 30)
    if (not isinstance(query, str) or not query.strip() or len(query) > MAX_QUERY_CHARS or
            any(ord(c) < 32 for c in query)):
        raise coding.CodingError("query must be a non-empty single-line literal up to 200 characters.")
    if not isinstance(path, str) or (path and not coding.valid_path(path)):
        raise coding.CodingError("path must be empty or an exact relative file/directory path without a trailing slash.")
    if type(case_sensitive) is not bool or type(limit) is not int or not 1 <= limit <= 50:
        raise coding.CodingError("Use a boolean case_sensitive and max_results from 1 to 50.")
    return query, path, case_sensitive, limit


def search(workspace, args, cancelled):
    query, path, case_sensitive, limit = validate(args)
    # Escape the entire query: callers cannot supply a regex or glob program.
    pattern = re.compile(re.escape(query), 0 if case_sensitive else re.IGNORECASE)
    files, fingerprint = coding_snapshot.files_from_workspace(workspace, cancelled)
    paths = sorted(p for p in files if not path or p == path or p.startswith(path + "/"))
    if path and not paths:
        raise coding.CodingError("Search path has no files in the pinned/staged workspace.")
    results, used, examined, binary, large = [], 0, 0, 0, 0
    truncated = False
    for name in paths:
        if cancelled():
            raise coding.CodingError("Workspace search stopped or timed out; no complete result is available.")
        raw, _mode = files[name]
        examined += 1
        if len(raw) > MAX_TEXT_FILE_BYTES:
            large += 1
            continue
        if b"\0" in raw:
            binary += 1
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeError:
            binary += 1
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if number % 128 == 0 and cancelled():
                raise coding.CodingError("Workspace search stopped or timed out; no complete result is available.")
            match = pattern.search(line)
            if match is None:
                continue
            start = max(0, match.start() - 60)
            # Redact the complete line before cropping; otherwise a window
            # boundary could reveal part of a credential that should be hidden.
            redacted = filter_text(line)
            # Redaction can move the match or remove it entirely. In that case
            # show a bounded prefix rather than positioning in the original key.
            safe_match = pattern.search(redacted)
            if redacted != line:
                start = max(0, safe_match.start() - 60) if safe_match else 0
            snippet = redacted[start:start + SNIPPET_CHARS]
            row = {"path": name, "line": number, "text": snippet,
                   "clipped": start > 0 or start + len(snippet) < len(redacted)}
            size = len(json.dumps(row, ensure_ascii=False).encode("utf-8"))
            if len(results) >= limit or used + size > MAX_RESULT_BYTES:
                truncated = True
                break
            results.append(row)
            used += size
        if truncated:
            break
    if cancelled():
        raise coding.CodingError("Workspace search stopped or timed out; no complete result is available.")
    with workspace.lock:
        if workspace.fingerprint() != fingerprint:
            raise coding.CodingError("Draft changed during search. Search again, then read matching files before editing.")
        base = workspace.base_sha
    return {"base_sha": base, "draft_fingerprint": fingerprint, "matches": results,
            "scope_files": len(paths), "files_examined": examined, "skipped_binary_files": binary,
            "skipped_large_files": large, "truncated": truncated,
            "complete": not truncated and not binary and not large,
            "note": "Literal matching lines from the pinned/staged snapshot, not the live GitHub index. Skipped files are not searched. Matches do not replace read_file or scoped AGENTS.md review before editing."}
