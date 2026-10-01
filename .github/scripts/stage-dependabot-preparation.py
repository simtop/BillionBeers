#!/usr/bin/env python3
"""Stage only the recorded tracked formatter output and preparation files."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

GENERATED = {"gradle/verification-metadata.xml", "app/dependencies/releaseRuntimeClasspath.txt", "README.md"}


def allowed_files(recorded: bytes, changed: bytes) -> list[str]:
    formats = {path.decode() for path in recorded.split(b"\0") if path}
    if any(not path.endswith((".kt", ".gradle.kts")) for path in formats):
        raise ValueError("Formatter output contains an unexpected file type")
    changes = [path.decode() for path in changed.split(b"\0") if path]
    unexpected = set(changes) - formats - GENERATED
    if unexpected:
        raise ValueError(f"Preparation changed files outside its allowlist: {sorted(unexpected)}")
    return changes


def main() -> int:
    changed = subprocess.check_output(["git", "diff", "--name-only", "-z"])
    files = allowed_files(Path(sys.argv[1]).read_bytes(), changed)
    if files:
        subprocess.run(["git", "add", "--", *files], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
