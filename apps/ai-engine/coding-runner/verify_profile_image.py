"""Image-build contract checks; not run by the controller or on project sources.

Versions and seed shape are checked, not arbitrary dependency correctness or the
absence of all secrets. Operators must review the upstream images/public seed.
"""
from pathlib import Path
import os
import re
import stat
import subprocess
import sys
import tempfile


def verify(profile, images):
    if profile not in ("jvm", "android") or len(images) != (1 if profile == "jvm" else 2):
        raise ValueError("Provide the profile and its immutable upstream image references.")
    if any(not re.fullmatch(r"[a-z0-9][a-z0-9._/:\-]*@sha256:[0-9a-f]{64}", value) for value in images):
        raise ValueError("Upstream image arguments must be digest-pinned references.")
    def output(argv):
        with tempfile.TemporaryDirectory(prefix="dua-image-contract-") as temporary:
            env = {"PATH": "/opt/java/openjdk/bin:/opt/gradle/bin:/usr/local/bin:/usr/bin:/bin",
                   "JAVA_HOME": "/opt/java/openjdk", "HOME": temporary, "GRADLE_USER_HOME": temporary + "/gradle"}
            result = subprocess.run(argv, cwd=temporary, env=env, stdin=subprocess.DEVNULL,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30, check=True)
            return result.stdout.decode("utf-8", errors="replace")
    if not re.match(r"(?:openjdk|java) 17(?:[.\s]|$)", output(["/opt/java/openjdk/bin/java", "--version"])):
        raise ValueError("The reference profiles require JDK 17.")
    if not re.match(r"javac 17(?:[.\s]|$)", output(["/opt/java/openjdk/bin/javac", "-version"])):
        raise ValueError("The compiler must be JDK 17.")
    if not re.search(r"(?m)^Gradle 8\.11\.1\s*$", output(["/opt/gradle/bin/gradle", "--offline", "--no-daemon", "--version"])):
        raise ValueError("The reference profiles require Gradle 8.11.1.")
    seed = Path("/opt/dua-gradle-seed")
    if not seed.is_dir() or seed.is_symlink():
        raise ValueError("A public dependency seed directory is required (it may be empty).")
    total, count = 0, 0
    for root, directories, files in os.walk(seed, followlinks=False):
        for name in directories + files:
            path = Path(root) / name
            relative, info = path.relative_to(seed), path.lstat()
            parts = relative.parts
            if stat.S_ISLNK(info.st_mode) or not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)):
                raise ValueError("Dependency seeds cannot contain links or special files.")
            allowed = (parts == (".keep",) or parts == ("caches",) or parts == ("caches", "modules-2") or
                       parts == ("wrapper",) or parts[:2] == ("wrapper", "dists") or
                       (len(parts) >= 3 and parts[:2] == ("caches", "modules-2") and
                        (parts[2] == "files-2.1" or re.fullmatch(r"metadata-2\.[0-9]+", parts[2]))))
            if not allowed or name in ("gradle.properties", "init.gradle", "init.gradle.kts", "credentials", ".npmrc"):
                raise ValueError("Only reviewed public artifact metadata/files and wrapper distributions may be seeded.")
            if parts in (("caches",), ("caches", "modules-2"), ("wrapper",), ("wrapper", "dists")) and not stat.S_ISDIR(info.st_mode):
                raise ValueError("Seed cache parents must be directories.")
            if parts == (".keep",) and (not stat.S_ISREG(info.st_mode) or info.st_size != 0):
                raise ValueError("The seed placeholder must be an empty regular file.")
            count += 1
            if stat.S_ISREG(info.st_mode):
                if info.st_nlink != 1:
                    raise ValueError("Dependency seeds cannot contain hard-linked files.")
                total += info.st_size
                path.chmod(0o644)
            else:
                path.chmod(0o755)
            if count > 100_000 or total > (768 if profile == "jvm" else 1536) * 1048576:
                raise ValueError("Dependency seed exceeds this profile's image budget.")
    if profile == "android":
        sdk = Path("/opt/android-sdk")
        if not (sdk / "platforms/android-35/android.jar").is_file():
            raise ValueError("Android platform 35 is required.")
        properties = (sdk / "build-tools/35.0.0/source.properties").read_text()
        if not re.search(r"(?m)^Pkg\.Revision\s*=\s*35\.0\.0\s*$", properties):
            raise ValueError("Android Build Tools 35.0.0 is required.")
        output([str(sdk / "build-tools/35.0.0/aapt2"), "version"])
    print("Image contract checked; project dependencies and live runner isolation still need separate verification.")


if __name__ == "__main__":
    verify(sys.argv[1], sys.argv[2:])
