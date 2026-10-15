#!/usr/bin/env python3
"""Validate frontmatter on repository-tracked Claude skills."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


FRONTMATTER_DELIMITER = "---"
SKILL_NAME = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")


def validate_skill_text(text: str, expected_name: str) -> list[str]:
    errors: list[str] = []
    lines = text.splitlines()
    if not lines or lines[0].strip() != FRONTMATTER_DELIMITER:
        return ["frontmatter must start with ---"]

    try:
        end = next(
            i
            for i, line in enumerate(lines[1:], start=1)
            if line.strip() == FRONTMATTER_DELIMITER
        )
    except StopIteration:
        return ["frontmatter closing --- is missing"]

    fields: dict[str, str] = {}
    current_field: str | None = None
    for line in lines[1:end]:
        if current_field == "description" and (line.startswith(" ") or not line.strip()):
            fields[current_field] += " " + line.strip()
            continue
        match = re.fullmatch(r"([A-Za-z][A-Za-z0-9_-]*):(?:\s*(.*))?", line)
        if not match:
            current_field = None
            continue
        key, raw_value = match.groups()
        current_field = key
        if key in fields:
            errors.append(f"frontmatter field {key!r} is duplicated")
            continue
        value = (raw_value or "").strip()
        if key == "description" and value in {">", ">-", ">+", "|", "|-", "|+"}:
            fields[key] = ""
        else:
            fields[key] = value

    name = fields.get("name", "").strip().strip("\"'")
    description = fields.get("description", "").strip().strip("\"'")
    if not name:
        errors.append("frontmatter name is required")
    elif len(name) > 64 or not SKILL_NAME.fullmatch(name):
        errors.append(
            "name must be lowercase letters, numbers, and single hyphens (max 64 chars)"
        )
    elif name != expected_name:
        errors.append(f"name {name!r} must match directory {expected_name!r}")
    if not description:
        errors.append("frontmatter description is required")
    elif len(description) > 1024:
        errors.append("description must be at most 1024 characters")
    elif "<" in description or ">" in description:
        errors.append("description must not contain angle brackets")
    if not any(line.strip() for line in lines[end + 1 :]):
        errors.append("skill body must not be empty")
    return errors


def tracked_skill_paths(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--", ".claude/skills/*/SKILL.md"],
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        raise RuntimeError("git ls-files could not list tracked skill files")
    paths = [root / value.decode("utf-8") for value in result.stdout.split(b"\0") if value]
    if not paths:
        raise RuntimeError("no tracked .claude/skills/*/SKILL.md files were found")
    return paths


def check_repository(root: Path) -> tuple[int, list[str]]:
    failures = []
    paths = tracked_skill_paths(root)
    for path in paths:
        errors = validate_skill_text(path.read_text(encoding="utf-8"), path.parent.name)
        failures.extend(f"{path.relative_to(root)}: {error}" for error in errors)
    return len(paths), failures


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    try:
        count, failures = check_repository(root)
    except (OSError, RuntimeError) as error:
        print(f"Skill format check could not run: {error}", file=sys.stderr)
        return 2

    if failures:
        print("Skill format check failed:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"Skill format check passed for {count} tracked skills.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
