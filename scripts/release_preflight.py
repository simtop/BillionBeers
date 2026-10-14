#!/usr/bin/env python3
"""Validate Play release notes for the current Android version code."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


MAX_RELEASE_NOTES_CHARACTERS = 500
VERSION_CODE = re.compile(r"^billionbeers\.versionCode=(\d+)\s*$", re.MULTILINE)


def validate_release_notes(root: Path) -> str | None:
    properties = root / "gradle.properties"
    if not properties.is_file():
        return f"missing Gradle properties file: {properties}"

    matches = VERSION_CODE.findall(properties.read_text(encoding="utf-8"))
    if len(matches) != 1:
        return "gradle.properties must define billionbeers.versionCode exactly once"

    version_code = matches[0]
    changelog = root / "fastlane/metadata/android/en-US/changelogs" / f"{version_code}.txt"
    if not changelog.is_file():
        return f"missing Play release notes for versionCode {version_code}: {changelog}"

    text = changelog.read_text(encoding="utf-8").strip()
    if not text:
        return f"Play release notes for versionCode {version_code} are empty"
    if len(text) > MAX_RELEASE_NOTES_CHARACTERS:
        return (
            f"Play release notes for versionCode {version_code} contain {len(text)} characters; "
            f"the limit is {MAX_RELEASE_NOTES_CHARACTERS}"
        )
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()

    error = validate_release_notes(args.root)
    if error:
        print(f"Release preflight failed: {error}", file=sys.stderr)
        return 1
    print("Release preflight passed: current version has valid Play release notes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
