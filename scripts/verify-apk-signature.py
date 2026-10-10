#!/usr/bin/env python3
"""Verify the public owner certificate and write shareable APK checksums."""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess


def fingerprint(value: str, length: int) -> str:
    normalized = value.replace(":", "").upper()
    if not re.fullmatch(rf"[0-9A-F]{{{length}}}", normalized):
        raise ValueError("Invalid certificate fingerprint")
    return normalized


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--certificate", type=Path, default=Path("app/signing-certificate.properties"))
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    properties = {}
    for line in args.certificate.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            name, value = line.split("=", 1)
            properties[name.strip()] = value.strip()
    expected_sha256 = fingerprint(properties["sha256"], 64)
    expected_sha1 = fingerprint(properties["sha1"], 40)
    result = subprocess.run(
        [args.apksigner, "verify", "--print-certs", str(args.apk)],
        capture_output=True, text=True, check=True,
    )
    actual_sha256 = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)$", result.stdout, re.MULTILINE)
    actual_sha1 = re.findall(r"^Signer #\d+ certificate SHA-1 digest: ([a-fA-F0-9]+)$", result.stdout, re.MULTILINE)
    if [value.upper() for value in actual_sha256] != [expected_sha256] or [value.upper() for value in actual_sha1] != [expected_sha1]:
        raise SystemExit("APK signer does not match the permanent owner certificate. No download metadata written.")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    checksum = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    (args.output_dir / "SHA256SUMS").write_text(f"{checksum}  {args.apk.name}\n")
    (args.output_dir / "signing-certificate.txt").write_text(
        f"Package: com.dua.ai\nCertificate SHA-1: {properties['sha1']}\nCertificate SHA-256: {properties['sha256']}\n"
    )
    print("APK signature verified against the permanent owner certificate.")


if __name__ == "__main__":
    main()
