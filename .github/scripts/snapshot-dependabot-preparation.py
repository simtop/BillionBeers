#!/usr/bin/env python3
"""Capture exactly the shared baseline and formatter changes for lane comparison."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

ALLOWED_FORMAT_SUFFIXES = (".kt", ".gradle.kts")
BASELINE = "app/dependencies/releaseRuntimeClasspath.txt"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("formatting_files", type=Path)
    parser.add_argument("patch", type=Path)
    args = parser.parse_args()
    paths = [path.decode() for path in args.formatting_files.read_bytes().split(b"\0") if path]
    if any(not path.endswith(ALLOWED_FORMAT_SUFFIXES) for path in paths):
        raise SystemExit("::error::Formatter output contains an unexpected file type")
    result = subprocess.run(["git", "diff", "--binary", "--", BASELINE, *paths], check=True, capture_output=True)
    args.patch.write_bytes(result.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
