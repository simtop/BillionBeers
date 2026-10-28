#!/usr/bin/env python3
"""Identify a run-isolated Kotlin/Native cache experiment; never select tests."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess


def cache_identity(root: Path, environment: dict, xcode_version: str, home: Path) -> dict:
    run_id = environment.get("GITHUB_RUN_ID", "")
    if not run_id.isdigit():
        raise ValueError("a numeric workflow run id is required")
    architecture = environment.get("RUNNER_ARCH")
    host = {"ARM64": "aarch64", "X64": "x86_64"}.get(architecture)
    if environment.get("RUNNER_OS") != "macOS" or host is None:
        raise ValueError("the cache probe supports only macOS runners")
    if not xcode_version.strip() or not environment.get("ImageOS") or not environment.get("ImageVersion"):
        raise ValueError("the exact Xcode and runner image versions are required")
    catalog = (root / "gradle/libs.versions.toml").read_text()
    version = re.search(r'^org-jetbrains-kotlin\s*=\s*"([0-9A-Za-z.+_-]+)"\s*$', catalog, re.MULTILINE)
    if not version:
        raise ValueError("cannot identify the Kotlin compiler version")
    files = subprocess.check_output([
        "git", "ls-files", "-z", "--", "*.gradle.kts", "gradle.properties",
        "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    ], cwd=root).decode().split("\0")
    digest = hashlib.sha256()
    for value in (architecture, environment["ImageOS"], environment["ImageVersion"], xcode_version):
        digest.update(value.encode() + b"\0")
    for relative in sorted(path for path in files if path):
        path = root / relative
        if path.is_symlink() or not path.is_file():
            raise ValueError("cache inputs must be tracked regular files")
        digest.update(relative.encode() + b"\0" + path.read_bytes() + b"\0")
    directory = home / ".konan"
    if "\n" in str(directory) or "\r" in str(directory):
        raise ValueError("invalid compiler cache directory")
    paths = [str(directory / "dependencies"),
             str(directory / f"kotlin-native-prebuilt-macos-{host}-{version[1]}"),
             "!" + str(directory / "**/*.lock"), "!" + str(directory / "**/*.lck")]
    return {"key": f"native-cache-probe-v1-{architecture}-{run_id}-{digest.hexdigest()}",
            "paths": "\n".join(paths), "kotlin_version": version[1]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    xcode = subprocess.check_output(["xcodebuild", "-version"], text=True)
    record = cache_identity(args.root.resolve(), os.environ, xcode, Path.home())
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"key={record['key']}\n")
        output.write(f"paths<<NATIVE_CACHE_PROBE_PATHS\n{record['paths']}\nNATIVE_CACHE_PROBE_PATHS\n")
    print(f"Kotlin/Native {record['kotlin_version']} cache probe, isolated to this workflow run.")


if __name__ == "__main__":
    main()
