"""Bounded, validated source bundles for the separate coding runner.

Archives are read as data, never extracted by tarfile or executed on the engine.
Only regular files are supported; links and submodules fail closed.
"""
import gzip
import hashlib
import io
import tarfile

import coding_agent as coding

MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
MAX_TAR_BYTES = 64 * 1024 * 1024
MAX_SOURCE_BYTES = 32 * 1024 * 1024
MAX_SOURCE_FILE_BYTES = 8 * 1024 * 1024
MAX_MEMBERS = 25_000


def unpack(data, github_root=False, expected=None):
    if not isinstance(data, bytes) or not data or len(data) > MAX_ARCHIVE_BYTES:
        raise coding.CodingError("Source archive exceeds the runner limit.")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as compressed:
            expanded = compressed.read(MAX_TAR_BYTES + 1)
        if len(expanded) > MAX_TAR_BYTES:
            raise coding.CodingError("Expanded source archive exceeds the runner limit.")
        files, total, root = {}, 0, None
        with tarfile.open(fileobj=io.BytesIO(expanded), mode="r:") as archive:
            for index, item in enumerate(archive):
                if index >= MAX_MEMBERS:
                    raise coding.CodingError("Source archive contains too many entries.")
                name = item.name.rstrip("/")
                if github_root:
                    first, _, name = name.partition("/")
                    if root is None:
                        root = first
                    if first != root or first in ("", ".", ".."):
                        raise coding.CodingError("Invalid source archive root.")
                    if not name and item.isdir():
                        continue
                if not coding.valid_path(name):
                    raise coding.CodingError("Invalid source archive path.")
                if item.isdir():
                    continue
                if not item.isfile() or item.size > MAX_SOURCE_FILE_BYTES or item.size < 0 or name in files:
                    raise coding.CodingError("Source archive must contain bounded, unique regular files only.")
                total += item.size
                if total > MAX_SOURCE_BYTES:
                    raise coding.CodingError("Source tree exceeds the runner byte limit.")
                handle = archive.extractfile(item)
                if handle is None:
                    raise coding.CodingError("Invalid source archive member.")
                with handle:
                    raw = handle.read(item.size + 1)
                if len(raw) != item.size:
                    raise coding.CodingError("Source archive member is incomplete.")
                mode = "100755" if item.mode & 0o111 else "100644"
                if expected is not None:
                    entry = expected.get(name)
                    blob = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
                    if not entry or entry.get("type") != "blob" or entry.get("sha") != blob or entry.get("mode") != mode:
                        raise coding.CodingError("Source archive does not match the pinned Git tree.")
                files[name] = (raw, mode)
        for name in files:
            parts = name.split("/")
            if any("/".join(parts[:i]) in files for i in range(1, len(parts))):
                raise coding.CodingError("Source archive has conflicting file/directory paths.")
        if expected is not None:
            wanted = {p for p, entry in expected.items() if entry.get("type") != "tree"}
            if set(files) != wanted:
                raise coding.CodingError("Source archive is missing pinned files or contains unsupported submodules.")
        return files
    except (tarfile.TarError, OSError, EOFError, ValueError):
        raise coding.CodingError("Invalid compressed source archive.") from None


def pack(files):
    total = sum(len(raw) for raw, _mode in files.values())
    if total > MAX_SOURCE_BYTES:
        raise coding.CodingError("Source snapshot exceeds the runner byte limit.")
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as compressed:
        with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as archive:
            for name, (raw, mode) in sorted(files.items()):
                if not coding.valid_path(name) or len(raw) > MAX_SOURCE_FILE_BYTES:
                    raise coding.CodingError("Invalid or oversized source snapshot file.")
                member = tarfile.TarInfo(name)
                member.size, member.mode = len(raw), (0o755 if mode == "100755" else 0o644)
                member.uid = member.gid = 10001
                archive.addfile(member, io.BytesIO(raw))
    data = output.getvalue()
    if len(data) > MAX_ARCHIVE_BYTES:
        raise coding.CodingError("Compressed source snapshot exceeds the runner limit.")
    return data


def from_workspace(workspace, cancelled):
    with workspace.lock:
        workspace.start()
        if cancelled():
            raise coding.CodingError("Command cancelled before preparing its snapshot.")
        if getattr(workspace, "source_archive", None) is None:
            workspace.source_archive = workspace.github.archive(workspace.base_sha)
        files = unpack(workspace.source_archive, github_root=True, expected=workspace.entries)
        for name, text in workspace.staged.items():
            mode = workspace.entries.get(name, {}).get("mode", "100644")
            files[name] = (text.encode("utf-8"), mode)
        if cancelled():
            raise coding.CodingError("Command cancelled before sending its snapshot.")
        return pack(files)
