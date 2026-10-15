#!/usr/bin/env python3
"""Ensure every Gradle version-catalog dependency matches exactly one Dependabot group."""

from __future__ import annotations

import ast
import fnmatch
import re
import sys
from pathlib import Path


class CatalogCoverageError(ValueError):
    """Raised when the version catalog or Dependabot groups cannot be checked safely."""


def _toml_string(value: str, source: str) -> str:
    try:
        parsed = ast.literal_eval(value)
    except (SyntaxError, ValueError) as error:
        raise CatalogCoverageError(f"unsupported string value in {source}: {value}") from error
    if not isinstance(parsed, str):
        raise CatalogCoverageError(f"expected a string value in {source}: {value}")
    return parsed


def _inline_table_fields(value: str, source: str) -> dict[str, str]:
    value = value.strip()
    if not value.startswith("{") or not value.endswith("}"):
        raise CatalogCoverageError(f"unsupported catalog entry in {source}: {value}")

    fields: dict[str, str] = {}
    pattern = re.compile(r"(?:^|,)\s*([A-Za-z0-9_-]+)\s*=\s*(\"(?:\\.|[^\"])*\"|'[^']*')")
    for match in pattern.finditer(value[1:-1]):
        fields[match.group(1)] = _toml_string(match.group(2), source)
    if not fields:
        raise CatalogCoverageError(f"catalog entry has no supported fields in {source}: {value}")
    return fields


def parse_catalog_entries(text: str) -> list[tuple[str, str, str]]:
    """Read library coordinates and plugin IDs from the version-catalog table subset."""
    entries: list[tuple[str, str, str]] = []
    section = ""
    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        header = re.fullmatch(r"\[([A-Za-z0-9_-]+)\]", line)
        if header:
            section = header.group(1)
            continue
        if section not in {"libraries", "plugins"}:
            continue

        entry = re.fullmatch(r"([A-Za-z0-9_.-]+)\s*=\s*(.+)", line)
        if not entry:
            raise CatalogCoverageError(
                f"unsupported {section} catalog syntax on line {line_number}: {raw_line}"
            )
        alias, value = entry.groups()
        source = f"{section}.{alias} (line {line_number})"
        if value.startswith(('"', "'")):
            if section == "plugins":
                raise CatalogCoverageError(f"plugin {source} must declare an id")
            coordinate = _toml_string(value, source)
            parts = coordinate.split(":")
            if len(parts) < 3 or not parts[0] or not parts[1]:
                raise CatalogCoverageError(f"expected group:artifact:version in {source}")
            dependency = ":".join(parts[:2])
        else:
            fields = _inline_table_fields(value, source)
            if section == "libraries":
                dependency = fields.get("module", "")
                if not dependency and fields.get("group") and fields.get("name"):
                    dependency = f"{fields['group']}:{fields['name']}"
                if not dependency or ":" not in dependency:
                    raise CatalogCoverageError(
                        f"library {source} must declare module or group and name"
                    )
            else:
                dependency = fields.get("id", "")
                if not dependency:
                    raise CatalogCoverageError(f"plugin {source} must declare an id")
        kind = "library" if section == "libraries" else "plugin"
        entries.append((kind, alias, dependency))

    if not entries:
        raise CatalogCoverageError("version catalog contains no libraries or plugins")
    return entries


def parse_gradle_groups(text: str) -> dict[str, dict[str, list[str]]]:
    """Read group patterns from the Gradle update block without a YAML dependency."""
    lines = text.splitlines()
    gradle_start = next(
        (i for i, line in enumerate(lines) if re.fullmatch(r'  - package-ecosystem:\s*["\']gradle["\']\s*', line)),
        None,
    )
    if gradle_start is None:
        raise CatalogCoverageError("Dependabot config has no Gradle update block")

    groups_start = next(
        (i for i in range(gradle_start + 1, len(lines)) if lines[i].startswith("    groups:")),
        None,
    )
    if groups_start is None:
        raise CatalogCoverageError("Gradle Dependabot update has no groups section")

    groups: dict[str, dict[str, list[str]]] = {}
    current_group: str | None = None
    current_patterns: str | None = None
    for line_number, raw_line in enumerate(lines[groups_start + 1 :], start=groups_start + 2):
        if re.match(r"^  - package-ecosystem:", raw_line):
            break
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        group_match = re.fullmatch(r"      ([A-Za-z0-9_-]+):\s*", raw_line)
        if group_match:
            current_group = group_match.group(1)
            groups[current_group] = {"patterns": [], "exclude-patterns": []}
            current_patterns = None
            continue
        field_match = re.fullmatch(r"        (patterns|exclude-patterns):\s*", raw_line)
        if field_match and current_group:
            current_patterns = field_match.group(1)
            continue
        pattern_match = re.fullmatch(r"          -\s*([\"'])(.*?)\1\s*", raw_line)
        if pattern_match and current_group and current_patterns:
            groups[current_group][current_patterns].append(pattern_match.group(2))
            continue
        if raw_line.startswith("      "):
            current_patterns = None
            continue
        if raw_line.strip():
            raise CatalogCoverageError(
                f"unsupported Dependabot group syntax on line {line_number}: {raw_line}"
            )

    if not groups:
        raise CatalogCoverageError("Gradle Dependabot update has no groups")
    for name, group in groups.items():
        if not group["patterns"]:
            raise CatalogCoverageError(f"Dependabot group {name} has no patterns")
    return groups


def find_group_matches(
    dependency: str, groups: dict[str, dict[str, list[str]]]
) -> list[str]:
    matches = []
    for name, group in groups.items():
        included = any(fnmatch.fnmatchcase(dependency, pattern) for pattern in group["patterns"])
        excluded = any(
            fnmatch.fnmatchcase(dependency, pattern)
            for pattern in group["exclude-patterns"]
        )
        if included and not excluded:
            matches.append(name)
    return matches


def check_coverage(
    catalog_text: str, dependabot_text: str
) -> tuple[int, list[str]]:
    entries = parse_catalog_entries(catalog_text)
    groups = parse_gradle_groups(dependabot_text)
    failures = []
    for kind, alias, dependency in entries:
        matches = find_group_matches(dependency, groups)
        if len(matches) != 1:
            found = ", ".join(matches) if matches else "none"
            failures.append(
                f"{kind} {alias} ({dependency}) matches {found}; expected exactly one group"
            )
    return len(entries), failures


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    try:
        catalog = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        dependabot = (root / ".github/dependabot.yml").read_text(encoding="utf-8")
        total, failures = check_coverage(catalog, dependabot)
    except (OSError, CatalogCoverageError) as error:
        print(f"Dependabot catalog coverage could not be checked: {error}", file=sys.stderr)
        return 2

    if failures:
        print("Gradle catalog entries must match exactly one Dependabot group:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"Dependabot group coverage passed for {total} Gradle catalog entries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
