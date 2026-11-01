"""Fixed runner profiles; operators configure immutable images, callers only IDs."""
import json
import re

import dua_engine.coding.coding_agent as coding

IMAGE = re.compile(r"[a-z0-9][a-z0-9._/:\-]*@sha256:[0-9a-f]{64}")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
PRESETS = {
    "python": {"label": "Python", "toolchains": ["Python 3.12"],
        "limits": {"cpus": 1, "memoryMiB": 512, "workspaceMiB": 256, "tempMiB": 64,
                   "cacheMiB": 0, "pids": 128, "fileMiB": 64, "maxSeconds": 120, "setupSeconds": 10}},
    "jvm": {"label": "Java / Gradle", "toolchains": ["JDK 17", "Gradle 8.11.1"],
        "limits": {"cpus": 2, "memoryMiB": 2048, "workspaceMiB": 512, "tempMiB": 128,
                   "cacheMiB": 1024, "pids": 256, "fileMiB": 128, "maxSeconds": 120, "setupSeconds": 20}},
    "android": {"label": "Android", "toolchains": ["JDK 17", "Gradle 8.11.1", "Android SDK 35 / Build Tools 35.0.0"],
        "limits": {"cpus": 2, "memoryMiB": 4096, "workspaceMiB": 2048, "tempMiB": 256,
                   "cacheMiB": 2048, "pids": 512, "fileMiB": 256, "maxSeconds": 120, "setupSeconds": 30}},
}


def name(value="python"):
    if not isinstance(value, str) or value not in PRESETS:
        raise coding.CodingError("profile must be python, jvm or android; use list_runner_profiles for configured images.")
    return value


def images(legacy, encoded):
    if not isinstance(encoded, str) or len(encoded) > 4096:
        raise coding.CodingError("Invalid runner profile image configuration.")
    try:
        # Reject duplicate JSON keys rather than silently changing an image.
        def pairs(items):
            result = {}
            for key, value in items:
                if key in result:
                    raise ValueError()
                result[key] = value
            return result
        configured = json.loads(encoded, object_pairs_hook=pairs) if encoded else {}
    except (ValueError, TypeError):
        raise coding.CodingError("Invalid runner profile image JSON.") from None
    if not isinstance(configured, dict) or len(configured) > len(PRESETS):
        raise coding.CodingError("Invalid runner profile image map.")
    if legacy:
        if "python" in configured and configured["python"] != legacy:
            raise coding.CodingError("Conflicting Python image configuration.")
        configured["python"] = legacy
    if not configured or any(not isinstance(key, str) or key not in PRESETS or not isinstance(value, str) or
                             len(value) > 512 or not IMAGE.fullmatch(value) for key, value in configured.items()):
        raise coding.CodingError("Configure only known profiles with installed digest-pinned images.")
    return configured


def catalog(configured):
    return {"version": 1, "liveVerified": False, "profiles": [
        {"id": key, **PRESETS[key], "imageDigest": value.rsplit("@", 1)[1], "liveVerified": False}
        for key, value in sorted(configured.items())]}


def validate_catalog(value):
    if (not isinstance(value, dict) or type(value.get("version")) is not int or value["version"] != 1 or
            value.get("liveVerified") is not False or not isinstance(value.get("profiles"), list) or
            not 1 <= len(value["profiles"]) <= len(PRESETS)):
        raise coding.CodingError("Runner profile discovery returned an unsupported response.")
    seen, profiles = set(), []
    for row in value["profiles"]:
        if not isinstance(row, dict):
            raise coding.CodingError("Invalid runner profile descriptor.")
        key = name(row.get("id"))
        preset = PRESETS[key]
        if (key in seen or row.get("label") != preset["label"] or row.get("toolchains") != preset["toolchains"] or
                not isinstance(row.get("limits"), dict) or row["limits"] != preset["limits"] or
                any(type(v) is not int for v in row["limits"].values()) or row.get("liveVerified") is not False or
                not isinstance(row.get("imageDigest"), str) or not DIGEST.fullmatch(row["imageDigest"])):
            raise coding.CodingError("Runner profile limits or identity did not match the supported contract.")
        seen.add(key)
        profiles.append({"id": key, **preset, "imageDigest": row["imageDigest"], "liveVerified": False})
    return {"version": 1, "liveVerified": False, "profiles": profiles}


def environment(profile):
    values = {"HOME": "/tmp/home", "XDG_CACHE_HOME": "/tmp/cache",
              "PATH": "/usr/local/bin:/usr/bin:/bin"}
    if profile in ("jvm", "android"):
        values.update(JAVA_HOME="/opt/java/openjdk", GRADLE_HOME="/opt/gradle", GRADLE_USER_HOME="/cache/gradle",
                      PATH="/opt/java/openjdk/bin:/opt/gradle/bin:/usr/local/bin:/usr/bin:/bin")
    if profile == "android":
        values.update(ANDROID_HOME="/opt/android-sdk", ANDROID_SDK_ROOT="/opt/android-sdk")
    return values


def bootstrap(profile):
    commands = ["mkdir -p /tmp/home /tmp/cache", "test -x /usr/local/bin/python"]
    if profile in ("jvm", "android"):
        commands += ["test -x /opt/java/openjdk/bin/java", "test -x /opt/java/openjdk/bin/javac",
                     "test -x /opt/gradle/bin/gradle", "test -x /usr/local/bin/dua-gradle",
                     "test -d /opt/dua-gradle-seed", "mkdir -p /cache/gradle",
                     "cp -R /opt/dua-gradle-seed/. /cache/gradle/"]
    if profile == "android":
        commands += ["test -f /opt/android-sdk/platforms/android-35/android.jar",
                     "test -x /opt/android-sdk/build-tools/35.0.0/aapt2"]
    commands += ["cp -R /input/. /workspace/", "touch /tmp/.dua-ready", "exec sleep 3600"]
    return " && ".join(commands)
