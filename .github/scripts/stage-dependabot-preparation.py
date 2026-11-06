#!/usr/bin/env python3
"""Apply and stage bounded dependency preparation outputs."""

from __future__ import annotations

import argparse
import importlib.util
import subprocess
import sys
from pathlib import Path

BASELINE = "app/dependencies/releaseRuntimeClasspath.txt"
GENERATED = {"gradle/verification-metadata.xml", BASELINE, "README.md"}
VERSION_INPUTS = {"gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties"}
SPEC = importlib.util.spec_from_file_location(
    "ci_writeback", Path(__file__).with_name("ci-writeback.py")
)
WRITEBACK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WRITEBACK)


def formatting_files(recorded: bytes) -> set[str]:
    formats = {path.decode() for path in recorded.split(b"\0") if path}
    if any(not path.endswith((".kt", ".gradle.kts")) for path in formats):
        raise ValueError("Formatter output contains an unexpected file type")
    return formats


def allowed_files(recorded: bytes, changed: bytes) -> list[str]:
    changes = [path.decode() for path in changed.split(b"\0") if path]
    unexpected = set(changes) - formatting_files(recorded) - GENERATED
    if unexpected:
        raise ValueError(f"Preparation changed files outside its allowlist: {sorted(unexpected)}")
    return changes


def require_regular_source_file(repo: Path, name: str) -> None:
    path = repo / name
    if path.resolve() != path or not path.is_file():
        raise ValueError(f"Preparation source path is not a regular file inside the checkout: {name}")
    result = subprocess.run(
        ["git", "--literal-pathspecs", "ls-files", "--error-unmatch", "--", name],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
    )
    if result.returncode:
        raise ValueError(f"Preparation may only modify tracked files: {name}")


def apply_prepared_patch(recorded: bytes, patch: bytes) -> None:
    repo = Path.cwd().resolve()
    paths = WRITEBACK.patch_paths(repo, patch)
    unexpected = set(paths) - formatting_files(recorded) - {BASELINE}
    if unexpected:
        raise ValueError(f"Prepared patch contains paths outside its allowlist: {sorted(unexpected)}")
    for name in sorted(set(paths) | GENERATED | VERSION_INPUTS):
        require_regular_source_file(repo, name)
    if not paths:
        return
    if subprocess.check_output(["git", "apply", "--summary", "--binary"], input=patch):
        raise ValueError("Prepared patch adds, deletes or changes file modes")
    subprocess.run(["git", "apply", "--check", "--binary"], input=patch, check=True)
    subprocess.run(["git", "apply", "--binary"], input=patch, check=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("formatting_files", type=Path)
    parser.add_argument("--apply-prepared", type=Path)
    args = parser.parse_args()
    try:
        recorded = args.formatting_files.read_bytes()
        if args.apply_prepared:
            apply_prepared_patch(recorded, args.apply_prepared.read_bytes())
        else:
            changed = subprocess.check_output(["git", "diff", "--name-only", "-z", "--no-ext-diff"])
            files = allowed_files(recorded, changed)
            if files:
                subprocess.run(["git", "--literal-pathspecs", "add", "--", *files], check=True)
    except (OSError, ValueError, subprocess.CalledProcessError, WRITEBACK.WritebackError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
