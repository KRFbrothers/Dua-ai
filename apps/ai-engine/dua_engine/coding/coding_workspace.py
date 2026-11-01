"""Request-local text workspace over one immutable GitHub commit.

Uses the existing allowlisted GitHub client and Python's difflib; no checkout,
shell, repository code execution or additional agent framework. External writes
still go through propose_changes/propose_pr_fix and the owner's approval.
"""
import base64
import difflib
import hashlib
import json
import re
import threading

import dua_engine.coding.coding_agent as coding

TOOLS = ("workspace_status", "edit_file", "workspace_diff")
MAX_TREE_FILES = 20_000
MAX_INSTRUCTION_BYTES = 32_000
MAX_DIFF_CHARS = 24_000
SHA = re.compile(r"[0-9a-f]{40}")


class Workspace:
    def __init__(self, github):
        self.github = github
        self.base_sha = None
        self.tree_sha, self.pr_target = None, None
        self.entries = {}
        self.originals = {}
        self.staged = {}
        self.instructions_seen = set()
        self.bytes_read = 0
        self.lock = threading.RLock()
        self.checkpoint_id, self.checkpoint_revision = None, 0
        self.saved_fingerprint = None
        self.command_results = {}
        self.active_commands = 0
        self.active_searches = 0

    def start(self, base_sha=None):
        if self.base_sha is not None:
            if base_sha is not None and base_sha != self.base_sha:
                raise coding.CodingError("Cannot change an active workspace base.")
            return
        if base_sha is None:
            ref = self.github.request("GET", f"/repos/{self.github.repo}/git/ref/heads/{coding.BASE_BRANCH}")
            base_sha = (ref.get("object") or {}).get("sha")
        sha = base_sha
        if not isinstance(sha, str) or not SHA.fullmatch(sha):
            raise coding.CodingError("GitHub did not return a valid workspace commit.")
        tree = self.github.request("GET", f"/repos/{self.github.repo}/git/trees/{sha}", query={"recursive": "1"})
        if tree.get("truncated") or not isinstance(tree.get("tree"), list):
            raise coding.CodingError("Repository tree is incomplete; use a smaller repository.")
        entries = {item["path"]: item for item in tree["tree"]
                   if isinstance(item, dict) and coding.valid_path(item.get("path"))}
        if len(entries) > MAX_TREE_FILES:
            raise coding.CodingError("Repository exceeds the workspace file limit.")
        self.entries, self.base_sha = entries, sha
        self.tree_sha = tree.get("sha")

    def fingerprint(self):
        return hashlib.sha256(json.dumps({"base_sha": self.base_sha, "staged": self.staged, "pr_target": self.pr_target},
                                        sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()

    @classmethod
    def restore(cls, github, record):
        workspace = cls(github)
        workspace.start(record["base_sha"])
        if record.get("pr_target") is not None:
            import dua_engine.coding.coding_pr as coding_pr
            workspace.pr_target = coding_pr.clean_target(record["pr_target"], workspace.base_sha)
        for item in record["files"]:
            workspace.check_path(item["path"], write=True)
            workspace.staged[item["path"]] = item["content"]
        workspace.checkpoint_id, workspace.checkpoint_revision = record["id"], record["revision"]
        workspace.saved_fingerprint = workspace.fingerprint()
        return workspace

    def check_path(self, path, write=False):
        if not coding.valid_path(path, write=write):
            raise coding.CodingError("Invalid or protected workspace path.")
        entry = self.entries.get(path)
        if entry and (entry.get("type") != "blob" or entry.get("mode") not in ("100644", "100755")):
            raise coding.CodingError("Workspace supports regular text files only, not links, folders or submodules.")
        for index in range(1, len(path.split("/"))):
            parent = "/".join(path.split("/")[:index])
            if parent in self.staged or (parent in self.entries and self.entries[parent].get("type") != "tree"):
                raise coding.CodingError("The file path conflicts with an existing file or link.")

    def original(self, path):
        self.check_path(path)
        if path in self.originals:
            return self.originals[path]
        entry = self.entries.get(path)
        if entry is None:
            return None
        size = entry.get("size")
        if not isinstance(size, int) or size < 0 or size > coding.MAX_FILE_BYTES:
            raise coding.CodingError("File is too large for the text workspace.")
        if len(self.originals) >= coding.MAX_FILES_READ or self.bytes_read + size > coding.MAX_TOTAL_READ_BYTES:
            raise coding.CodingError("Workspace read limit reached.")
        data = self.github.request("GET", f"/repos/{self.github.repo}/contents/{path}", query={"ref": self.base_sha})
        if data.get("type") != "file" or data.get("encoding") != "base64" or data.get("sha") != entry.get("sha"):
            raise coding.CodingError("File response did not match the pinned workspace.")
        try:
            raw = base64.b64decode(data["content"].replace("\n", ""), validate=True)
            text = raw.decode("utf-8")
        except (KeyError, ValueError, UnicodeError):
            raise coding.CodingError("Workspace accepts UTF-8 text files only.") from None
        if len(raw) != size or b"\x00" in raw:
            raise coding.CodingError("File size or text encoding did not match the workspace.")
        self.bytes_read += size
        self.originals[path] = text
        return text

    def instruction_paths(self, path):
        if path:
            self.check_path(path)
        parts = path.split("/")[:-1] if path else []
        candidates = ["AGENTS.md"] + ["/".join(parts[:index] + ["AGENTS.md"]) for index in range(1, len(parts) + 1)]
        return [p for p in candidates if p in self.entries]

    def instructions(self, path):
        result, size = [], 0
        for name in self.instruction_paths(path):
            text = self.original(name)
            size += len(text.encode("utf-8"))
            if size > MAX_INSTRUCTION_BYTES:
                raise coding.CodingError("Scoped AGENTS.md guidance exceeds the workspace limit; narrow the task.")
            result.append({"path": name, "content": text})
        return result

    def status(self, path=""):
        import dua_engine.coding.coding_terminal as coding_terminal
        import dua_engine.coding.coding_checkpoints as coding_checkpoints
        instructions = self.instructions(path)
        self.instructions_seen.update(item["path"] for item in instructions)
        return {"repository": self.github.repo, "base_sha": self.base_sha,
                "instructions": instructions, "changed_files": sorted(self.staged), "pr_target": self.pr_target,
                "files_read": len(self.originals), "bytes_read": self.bytes_read,
                "running_commands": self.active_commands,
                "storage": "private_sql_checkpoint" if self.checkpoint_id else "request_memory",
                "checkpoint": {"configured": coding_checkpoints.enabled(), "id": self.checkpoint_id, "revision": self.checkpoint_revision,
                               "saved": self.saved_fingerprint == self.fingerprint()},
                "command_changes": [{"command_id": key, "paths": [f["path"] for f in value["files"]],
                                     "current": value["fingerprint"] == self.fingerprint(), "imported": value["imported"],
                                     "profile": value.get("profile", "python"), "image_digest": value.get("image_digest")}
                                    for key, value in self.command_results.items()],
                "execution": coding_terminal.status(),
                "note": "Drafts only; opening a PR workspace does not update its branch. GitHub changes need a separate approval. Unsaved drafts and command exports are request-local."}

    def read(self, path):
        instructions = self.instructions(path)
        text = self.staged[path] if path in self.staged else self.original(path)
        if text is None:
            self.instructions_seen.update(item["path"] for item in instructions)
            return {"exists": False, "base_sha": self.base_sha, "instructions": instructions,
                    "note": "New path: use edit_file with empty old_text and full new_text to create a draft."}
        # Bound output in bytes without cutting a UTF-8 code point. Keep the full
        # original server-side so an edit never replaces a file with its preview.
        shown = text.encode("utf-8")[:coding.MAX_READ_BYTES].decode("utf-8", errors="ignore")
        self.instructions_seen.update(item["path"] for item in instructions)
        return {"content": shown, "truncated": shown != text, "base_sha": self.base_sha,
                "staged": path in self.staged, "instructions": instructions}

    def edit(self, path, old_text, new_text):
        self.check_path(path, write=True)
        if not isinstance(old_text, str) or not isinstance(new_text, str):
            raise coding.CodingError("old_text and new_text must be strings.")
        if any(name not in self.instructions_seen for name in self.instruction_paths(path)):
            raise coding.CodingError("Read scoped guidance first: call workspace_status with this file path.")
        original = self.original(path)
        text = self.staged.get(path, original)
        if text is None:
            if old_text:
                raise coding.CodingError("New files require empty old_text and full content in new_text.")
            changed = new_text
        else:
            if not old_text or text.count(old_text) != 1:
                raise coding.CodingError("old_text must match exactly once. Read the file and include more context.")
            changed = text.replace(old_text, new_text, 1)
        candidate = dict(self.staged)
        if changed == original:
            candidate.pop(path, None)
        else:
            candidate[path] = changed
        coding.validate_changes([{"path": p, "content": c} for p, c in candidate.items()])
        self.staged = candidate
        return {"path": path, "staged": path in self.staged, "changed_files": sorted(self.staged),
                "note": "Draft updated; nothing was written to GitHub. Persistence is reported separately."}

    def diff(self, files=None):
        # Explicit files are an immutable proposal, never the current mutable draft.
        staged = self.staged if files is None else {f["path"]: f["content"] for f in files}
        chunks, remaining = [], MAX_DIFF_CHARS
        truncated = False
        for path, content in sorted(staged.items()):
            original = self.original(path)
            lines = difflib.unified_diff((original or "").splitlines(keepends=True), content.splitlines(keepends=True),
                                         fromfile="a/" + path if original is not None else "/dev/null", tofile="b/" + path)
            # The generator is bounded while consuming it, not after joining a
            # potentially very large diff into another copy of the file.
            for line in lines:
                if not line.endswith("\n"):
                    line += "\n\\ No newline at end of file\n"
                if len(line) > remaining:
                    chunks.append(line[:remaining])
                    truncated = True
                    remaining = 0
                    break
                chunks.append(line)
                remaining -= len(line)
            if truncated:
                break
        return {"base_sha": self.base_sha, "paths": sorted(staged),
                "diff": "".join(chunks), "truncated": truncated, "draft_fingerprint": self.fingerprint()}

    def import_command(self, command_id):
        with self.lock:
            result = self.command_results.get(command_id) if isinstance(command_id, str) else None
            if result is None or result["imported"]:
                raise coding.CodingError("Command exports are unavailable or already imported in this task.")
            if result["fingerprint"] != self.fingerprint():
                raise coding.CodingError("Draft changed after this command ran. Rerun it before importing; no changes were applied.")
            candidate = dict(self.staged)
            for item in result["files"]:
                path, content = item["path"], item["content"]
                self.check_path(path, write=True)
                if any(p not in self.instructions_seen for p in self.instruction_paths(path)):
                    raise coding.CodingError("Read scoped AGENTS.md before importing this path.")
                original = self.original(path)
                if content == original:
                    candidate.pop(path, None)
                else:
                    candidate[path] = content
            coding.validate_changes([{"path": p, "content": c} for p, c in candidate.items()])
            self.staged = candidate
            result["imported"] = True
            return {"imported": [f["path"] for f in result["files"]], "changed_files": sorted(self.staged),
                    "note": "Command text copied into the draft. Review workspace_diff before proposing a PR."}

    def proposal(self, args, repair=False):
        with self.lock:
            self.start()
            if self.pr_target is not None and not repair:
                raise coding.CodingError("This is an existing PR workspace. Use propose_pr_fix for a separate owner-approved fix.")
            if repair and (self.pr_target is None or not self.staged):
                raise coding.CodingError("Open a PR workspace and stage its fix before proposing a repair.")
            result = dict(args)
            files = args.get("files")
            if self.staged:
                if files:
                    raise coding.CodingError("Use an empty files list to propose all staged workspace edits.")
                files = [{"path": p, "content": c} for p, c in sorted(self.staged.items())]
            files = coding.validate_changes(files or [])
            for item in files:
                self.check_path(item["path"], write=True)
                if item["path"] in self.entries and item["path"] not in self.originals:
                    raise coding.CodingError("Read the original file before proposing its replacement.")
                if any(p not in self.instructions_seen for p in self.instruction_paths(item["path"])):
                    raise coding.CodingError("Read scoped AGENTS.md before proposing changes to this file.")
            result.update(files=files, base_sha=self.base_sha, repository=self.github.repo)
            if repair:
                result["pr_target"] = dict(self.pr_target)
            return result

    def run(self, name, args):
        with self.lock:
            self.start()
            if name == "workspace_status":
                return self.status(args.get("path", ""))
            if name == "list_files":
                prefix = args.get("prefix", "")
                if not isinstance(prefix, str):
                    raise coding.CodingError("prefix must be a string.")
                paths = sorted({p for p, e in self.entries.items() if e.get("type") == "blob"} | set(self.staged))
                paths = [p for p in paths if p.startswith(prefix)]
                return {"paths": paths[:800], "truncated": len(paths) > 800, "base_sha": self.base_sha}
            if name == "read_file":
                return self.read(args.get("path", ""))
            if name == "edit_file":
                return self.edit(args.get("path", ""), args.get("old_text"), args.get("new_text"))
            if name == "workspace_diff":
                return self.diff()
            raise coding.CodingError("Unknown workspace tool.")
