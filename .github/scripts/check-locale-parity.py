#!/usr/bin/env python3
"""Check French and Spanish string resources against their default resource tables."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree

LOCALE_DIR = re.compile(r"^values-(fr|es)$")
FORMAT_ARGUMENT = re.compile(
    r"%(?:(?P<position>\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?(?P<type>[bBhHsScCdoxXeEfgGaA])"
)
ResourceKey = tuple[str, str]
PlaceholderPattern = tuple[tuple[str, str], ...]


def tracked_string_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--", "*strings.xml"],
        check=True,
        capture_output=True,
    )
    return [root / path.decode() for path in result.stdout.split(b"\0") if path]


def placeholder_patterns(element: ElementTree.Element) -> set[PlaceholderPattern]:
    """Return each leaf's positional printf argument signature, ignoring plural category count."""
    leaves = list(element) if element.tag in {"plurals", "string-array"} else [element]

    patterns: set[PlaceholderPattern] = set()
    for leaf in leaves:
        arguments: list[tuple[str, str]] = []
        unpositioned = 0
        text = "".join(leaf.itertext())
        offset = 0
        while offset < len(text):
            if text[offset] != "%":
                offset += 1
                continue
            if text.startswith("%%", offset) or text.startswith("%n", offset):
                offset += 2
                continue
            match = FORMAT_ARGUMENT.match(text, offset)
            if not match:
                offset += 1
                continue
            position = match.group("position")
            if position is None:
                unpositioned += 1
                position = f"implicit-{unpositioned}"
            arguments.append((position, match.group("type").lower()))
            offset = match.end()
        patterns.add(tuple(sorted(arguments)))
    return patterns


def read_resources(
    path: Path, *, default_table: bool
) -> tuple[dict[ResourceKey, set[PlaceholderPattern]], list[str]]:
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as error:
        return {}, [f"{path}: cannot parse string resources: {error}"]

    resources: dict[ResourceKey, set[PlaceholderPattern]] = {}
    errors: list[str] = []
    for element in root:
        if element.tag not in {"string", "plurals", "string-array"}:
            continue
        if default_table and element.get("translatable", "true").lower() == "false":
            continue
        name = element.get("name")
        if not name:
            errors.append(f"{path}: <{element.tag}> is missing its name")
            continue
        key = (element.tag, name)
        if key in resources:
            errors.append(f"{path}: duplicate <{element.tag} name=\"{name}\">")
            continue
        resources[key] = placeholder_patterns(element)
    return resources, errors


def check_locale_files(files: list[Path]) -> list[str]:
    grouped: dict[Path, dict[str, Path]] = {}
    for path in files:
        if path.name != "strings.xml":
            continue
        if path.parent.name == "values":
            grouped.setdefault(path.parent.parent, {})["default"] = path
            continue
        match = LOCALE_DIR.fullmatch(path.parent.name)
        if match:
            grouped.setdefault(path.parent.parent, {})[match.group(1)] = path

    errors = []
    for resource_dir, tables in sorted(grouped.items(), key=lambda item: str(item[0])):
        default_path = tables.get("default", resource_dir / "values" / "strings.xml")
        if "default" not in tables:
            errors.append(f"{resource_dir}: missing values/strings.xml")
            continue

        defaults, parse_errors = read_resources(default_path, default_table=True)
        errors.extend(parse_errors)
        if defaults:
            missing_locales = {"fr", "es"} - tables.keys()
            for locale in sorted(missing_locales):
                errors.append(f"{resource_dir}: missing values-{locale}/strings.xml")

        for locale, localized_path in sorted(tables.items()):
            if locale == "default":
                continue
            localized, parse_errors = read_resources(localized_path, default_table=False)
            errors.extend(parse_errors)
            missing = sorted(defaults.keys() - localized.keys())
            extra = sorted(localized.keys() - defaults.keys())
            for kind, name in missing:
                errors.append(f"{localized_path}: missing <{kind} name=\"{name}\">")
            for kind, name in extra:
                errors.append(f"{localized_path}: unknown <{kind} name=\"{name}\">")
            for key in sorted(defaults.keys() & localized.keys()):
                if defaults[key] != localized[key]:
                    kind, name = key
                    errors.append(
                        f"{localized_path}: placeholder mismatch for <{kind} name=\"{name}\">"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root", type=Path, default=Path(__file__).resolve().parents[2]
    )
    args = parser.parse_args()
    root = args.repo_root.resolve()
    try:
        errors = check_locale_files(tracked_string_files(root))
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Locale parity check error: {error}", file=sys.stderr)
        return 2

    if errors:
        print("Locale parity check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("French and Spanish string resources match their default keys and placeholders")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
