"""Bounded text exports from an untrusted command container.

The collector runs via the image's isolated Python, never on the controller
host. Its output remains untrusted and is validated again in the chat engine.
"""
import base64
import hashlib

import coding_agent as coding

MAX_ARTIFACT_OUTPUT = 900_000

COLLECTOR = r'''
import base64, hashlib, json, os, stat, sys
files, total = [], 0
try:
    for path in json.loads(sys.argv[1]):
        descriptor = os.open('/workspace', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            parts = path.split('/')
            for parent in parts[:-1]:
                child = os.open(parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=descriptor)
                os.close(descriptor)
                descriptor = child
            file_fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=descriptor)
            with os.fdopen(file_fd, 'rb') as handle:
                info = os.fstat(handle.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 200000:
                    raise ValueError()
                raw = handle.read(200001)
            if len(raw) > 200000 or b'\0' in raw:
                raise ValueError()
            raw.decode('utf-8')
            total += len(raw)
            if total > 600000:
                raise ValueError()
            files.append({'path': path, 'content_b64': base64.b64encode(raw).decode('ascii'),
                          'sha256': hashlib.sha256(raw).hexdigest(),
                          'mode': '100755' if info.st_mode & 0o111 else '100644'})
        finally:
            os.close(descriptor)
    print(json.dumps({'files': files}, separators=(',', ':')))
except Exception:
    print(json.dumps({'error': 'Only existing bounded UTF-8 regular files without links can be exported.'}))
    sys.exit(1)
'''


def export_paths(args):
    paths = args.get("export_paths", [])
    if (not isinstance(paths, list) or len(paths) > coding.MAX_FILES_CHANGED or
            any(not coding.valid_path(path, write=True) for path in paths) or len(set(paths)) != len(paths) or
            coding.conflicting_change_paths(paths)):
        raise coding.CodingError("export_paths must contain at most 10 unique, unprotected file paths.")
    return paths


def validate(files, paths, workspace):
    if not isinstance(files, list) or len(files) != len(paths):
        raise coding.CodingError("Runner did not export every requested file. Nothing was imported.")
    clean, seen = [], set()
    for item in files:
        if not isinstance(item, dict) or set(item) != {"path", "content_b64", "sha256", "mode"}:
            raise coding.CodingError("Invalid command file export.")
        path, encoded = item["path"], item["content_b64"]
        if not isinstance(path, str) or path not in paths or path in seen or not coding.valid_path(path, write=True):
            raise coding.CodingError("Command export contains an unexpected path.")
        if not isinstance(encoded, str) or len(encoded) > (coding.MAX_FILE_BYTES + 2) // 3 * 4:
            raise coding.CodingError("Command file export is too large.")
        try:
            raw = base64.b64decode(encoded, validate=True)
            text = raw.decode("utf-8")
        except (ValueError, UnicodeError):
            raise coding.CodingError("Command exports must be UTF-8 text.") from None
        if b"\0" in raw or hashlib.sha256(raw).hexdigest() != item["sha256"]:
            raise coding.CodingError("Command export bytes did not match their digest.")
        expected_mode = workspace.entries.get(path, {}).get("mode", "100644")
        if item["mode"] != expected_mode:
            raise coding.CodingError("Executable mode changes are not supported by draft import.")
        seen.add(path)
        clean.append({"path": path, "content": text})
    return coding.validate_changes(clean)
