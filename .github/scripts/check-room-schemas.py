#!/usr/bin/env python3
"""Keep exported Room schemas reproducible and previously shipped versions immutable."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

SCHEMA_DIR = "beer-database/schemas/com.simtop.billionbeers.database.BeersDatabase"
LEGACY_SCHEMA_DIR = "beer_database/schemas/com.simtop.beer_database.database.BeersDatabase"
SCHEMA_FILE = re.compile(r"(\d+)\.json$")


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args], cwd=root, check=check, capture_output=True, text=True
    )


def ensure_base_available(root: Path, base: str) -> None:
    found = git(root, "cat-file", "-e", f"{base}^{{commit}}", check=False)
    if found.returncode == 0:
        return
    fetched = git(root, "fetch", "--no-tags", "--depth=1", "origin", base, check=False)
    if fetched.returncode:
        raise RuntimeError(
            f"could not fetch Room schema comparison base {base}: {fetched.stderr.strip()}"
        )


def schema_versions(paths: list[str]) -> dict[str, int]:
    versions = {}
    for path in paths:
        match = SCHEMA_FILE.fullmatch(Path(path).name)
        if match:
            versions[path] = int(match.group(1))
    return versions


def schema_entries(root: Path, revision: str, directory: str) -> dict[str, str]:
    entries = {}
    for entry in git(root, "ls-tree", "-r", "-z", revision, "--", directory).stdout.split("\0"):
        if entry:
            metadata, path = entry.split("\t", 1)
            entries[path.removeprefix(f"{directory}/")] = metadata
    return entries


def check_history(root: Path, base: str) -> list[str]:
    ensure_base_available(root, base)
    shipped = schema_entries(root, base, SCHEMA_DIR) or schema_entries(root, base, LEGACY_SCHEMA_DIR)
    current = schema_entries(root, "HEAD", SCHEMA_DIR)
    violations = [
        f"{SCHEMA_DIR}/{path}: shipped Room schema files are immutable"
        for path, metadata in shipped.items()
        if current.get(path) != metadata
    ]
    base_versions = schema_versions(list(shipped))
    added_versions = schema_versions(sorted(current.keys() - shipped.keys()))
    max_base_version = max(base_versions.values(), default=0)
    for path, version in added_versions.items():
        if version <= max_base_version:
            violations.append(
                f"{SCHEMA_DIR}/{path}: new schema version must be greater than the shipped maximum "
                f"({max_base_version})"
            )
    return violations


def check_generated_files(root: Path) -> list[str]:
    status = git(
        root,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
        "--",
        SCHEMA_DIR,
    ).stdout.splitlines()
    return [f"{line[3:]}: generated schema differs from the checked-in file" for line in status]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument(
        "--base",
        default=os.environ.get("ROOM_SCHEMA_BASE_SHA", ""),
        help="target-branch commit; existing schema files at this commit may not change",
    )
    args = parser.parse_args()
    root = args.repo_root.resolve()

    try:
        violations = check_history(root, args.base) if args.base else []
        violations.extend(check_generated_files(root))
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Room schema check error: {error}", file=sys.stderr)
        return 2

    if violations:
        print("Room schema safety check failed:", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        print(
            "Keep shipped schema versions unchanged, add a new version for schema changes, "
            "and commit the Room-generated schema.",
            file=sys.stderr,
        )
        return 1

    print("Room schema history is immutable and generated schemas are up to date")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
