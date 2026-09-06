#!/usr/bin/env python3
"""Check checked-in Markdown links and generated README content without network access."""

from __future__ import annotations

import argparse
import difflib
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urldefrag, urlparse

MARKDOWN_SUFFIXES = {".md", ".markdown"}
LINK_RE = re.compile(r"!?(?<!\\)\[([^\]]*)\]\(([^)\s]+)(?:\s+[^)]*)?\)")
HEADING_RE = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.MULTILINE)
ANCHOR_RE = re.compile(r'<a\s+[^>]*id=["\']([^"\']+)["\']', re.IGNORECASE)
START_MARKER = "<!-- START_VERSIONS -->"
END_MARKER = "<!-- END_VERSIONS -->"


def tracked_markdown_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "--", "*.md", "*.markdown"],
        check=True,
        capture_output=True,
        text=True,
    )
    return [root / path for path in result.stdout.splitlines()]


def slug_heading(value: str) -> str:
    value = re.sub(r"<[^>]+>", "", value).lower()
    value = re.sub(r"[^\w\s-]", "", value)
    return re.sub(r"[\s-]+", "-", value)


def local_anchors(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    anchors = {slug_heading(match.group(1)) for match in HEADING_RE.finditer(text)}
    anchors.update(ANCHOR_RE.findall(text))
    return anchors


def check_links(root: Path, paths: list[Path]) -> list[str]:
    errors: list[str] = []
    anchors = {path: local_anchors(path) for path in paths}
    for path in paths:
        relative_source = path.relative_to(root)
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for match in LINK_RE.finditer(line):
                target = match.group(2).strip("<>")
                parsed = urlparse(target)
                if parsed.scheme or target.startswith("//"):
                    continue
                target_path, fragment = urldefrag(target)
                resolved = (path.parent / target_path).resolve() if target_path else path
                try:
                    resolved.relative_to(root.resolve())
                except ValueError:
                    errors.append(f"{relative_source}:{line_number}: link escapes repository: {target}")
                    continue
                if not resolved.exists():
                    errors.append(f"{relative_source}:{line_number}: missing link target: {target}")
                elif fragment and resolved.is_file() and resolved.suffix in MARKDOWN_SUFFIXES:
                    if slug_heading(fragment) not in anchors.get(resolved, local_anchors(resolved)):
                        errors.append(
                            f"{relative_source}:{line_number}: missing link fragment: {target}"
                        )
    return errors


def required_value(pattern: str, text: str, name: str) -> str:
    match = re.search(pattern, text, re.MULTILINE)
    if not match or not match.group(1):
        raise ValueError(f"could not read {name}")
    return match.group(1)


def expected_version_block(root: Path) -> str:
    catalog = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    kotlin = required_value(r'^org-jetbrains-kotlin\s*=\s*"([^"]+)"', catalog, "Kotlin version")
    compose = required_value(r'^composeBom\s*=\s*"([^"]+)"', catalog, "Compose BOM version")
    metro = required_value(r'^dev-zacsweers-metro\s*=\s*"([^"]+)"', catalog, "Metro version")
    room = required_value(r'^androidx-room\s*=\s*"([^"]+)"', catalog, "Room version")
    gradle = required_value(r"distributionUrl=.*gradle-([^-]+)-(?:all|bin)\.zip", wrapper, "Gradle version")
    return "\n".join(
        [
            "| Tech | Version |",
            "| :--- | :--- |",
            f"| **Kotlin** | {kotlin} |",
            f"| **Gradle** | {gradle} |",
            f"| **Compose BOM** | {compose} |",
            f"| **Metro DI** | {metro} |",
            f"| **Room DB** | {room} |",
        ]
    )


def check_generated_indexes(root: Path) -> list[str]:
    readme_path = root / "README.md"
    text = readme_path.read_text(encoding="utf-8")
    starts = [match.start() for match in re.finditer(re.escape(START_MARKER), text)]
    ends = [match.start() for match in re.finditer(re.escape(END_MARKER), text)]
    if len(starts) != 1 or len(ends) != 1:
        return ["README.md: expected exactly one START_VERSIONS and END_VERSIONS marker"]
    if starts[0] >= ends[0]:
        return ["README.md: START_VERSIONS must precede END_VERSIONS"]
    try:
        expected = expected_version_block(root)
    except ValueError as error:
        return [f"README.md: {error}"]
    actual = text[starts[0] + len(START_MARKER) : ends[0]].strip()
    if actual != expected:
        diff = "".join(
            difflib.unified_diff(
                (expected + "\n").splitlines(keepends=True),
                (actual + "\n").splitlines(keepends=True),
                fromfile="expected README version block",
                tofile="actual README version block",
            )
        )
        return ["README.md: generated version block is stale\n" + diff.rstrip()]
    return []


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args(argv)
    root = args.root.resolve()
    paths = tracked_markdown_files(root)
    errors = check_links(root, paths) + check_generated_indexes(root)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Markdown checks passed ({len(paths)} tracked files; no network access).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
