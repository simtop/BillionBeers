#!/usr/bin/env python3
"""Reject unsafe changes made while Gradle writes verification metadata."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CHECKSUM_MISMATCH = 10
CONFIGURATION_CHANGED = 11
INVALID_METADATA = 12
NOT_EQUIVALENT = 13


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def canonical(element: ET.Element) -> tuple:
    return (
        local_name(element.tag),
        tuple(sorted(element.attrib.items())),
        (element.text or "").strip(),
        tuple(canonical(child) for child in element),
    )


def child_named(element: ET.Element, name: str) -> ET.Element | None:
    return next((child for child in element if local_name(child.tag) == name), None)


def artifact_checksums(root: ET.Element) -> dict[tuple[str, str, str, str], frozenset[str]]:
    components = child_named(root, "components")
    if components is None:
        raise ValueError("metadata has no <components> element")

    checksums: dict[tuple[str, str, str, str], set[str]] = {}
    for component in components:
        if local_name(component.tag) != "component":
            continue
        coordinate = (
            component.attrib.get("group", ""),
            component.attrib.get("name", ""),
            component.attrib.get("version", ""),
        )
        for artifact in component:
            if local_name(artifact.tag) != "artifact":
                continue
            key = (*coordinate, artifact.attrib.get("name", ""))
            values = checksums.setdefault(key, set())
            for checksum in artifact:
                if local_name(checksum.tag) != "sha256" or "value" not in checksum.attrib:
                    continue
                values.add(checksum.attrib["value"])
                values.update(
                    alternative.attrib["value"]
                    for alternative in checksum
                    if local_name(alternative.tag) == "also-trust"
                    and "value" in alternative.attrib
                )
    return {key: frozenset(values) for key, values in checksums.items()}


def load(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError(f"cannot read {path}: {error}") from error


def verify(before_path: Path, after_path: Path, require_equivalent: bool = False) -> int:
    try:
        before = load(before_path)
        after = load(after_path)
        before_configuration = child_named(before, "configuration")
        after_configuration = child_named(after, "configuration")
        if before_configuration is None or after_configuration is None:
            raise ValueError("metadata has no <configuration> element")

        if canonical(before_configuration) != canonical(after_configuration):
            print(
                "::error::Dependency-verification configuration changed during regeneration. "
                "Review trusted-artifact policy by hand.",
                file=sys.stderr,
            )
            return CONFIGURATION_CHANGED

        before_checksums = artifact_checksums(before)
        after_checksums = artifact_checksums(after)
    except ValueError as error:
        print(f"::error::{error}", file=sys.stderr)
        return INVALID_METADATA

    keys = set(before_checksums) | set(after_checksums) if require_equivalent else set(before_checksums)
    changed = [
        (key, before_checksums.get(key, frozenset()), after_checksums.get(key, frozenset()))
        for key in sorted(keys)
        if before_checksums.get(key, frozenset()) != after_checksums.get(key, frozenset())
    ]

    if changed:
        if require_equivalent:
            message = "Reference and candidate ledgers resolve different artifacts or checksums."
            failure = NOT_EQUIVALENT
            labels = ("reference", "candidate")
        else:
            message = "Regeneration changed checksums for artifacts already present in the ledger."
            failure = CHECKSUM_MISMATCH
            labels = ("before", "after")
        print(f"::error::{message}", file=sys.stderr)
        for (group, name, version, artifact), expected, actual in changed:
            coordinate = f"{group}:{name}:{version}:{artifact}"
            print(f"  {coordinate}", file=sys.stderr)
            print(f"    {labels[0]}: {', '.join(sorted(expected)) or '<none>'}", file=sys.stderr)
            print(f"    {labels[1]}: {', '.join(sorted(actual)) or '<none>'}", file=sys.stderr)
        if not require_equivalent:
            print(
                "Verify the artifacts independently; do not commit the generated alternatives.",
                file=sys.stderr,
            )
        return failure

    if require_equivalent:
        print(f"Verification metadata graphs are equivalent: {len(before_checksums)} artifacts.")
    else:
        new_artifacts = len(set(after_checksums) - set(before_checksums))
        print(
            f"Verification metadata update is safe: {new_artifacts} new artifact(s), "
            "no changed hashes."
        )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-equivalent",
        action="store_true",
        help="require both ledgers to contain exactly the same artifacts and checksums",
    )
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    args = parser.parse_args()
    return verify(args.before, args.after, require_equivalent=args.require_equivalent)


if __name__ == "__main__":
    raise SystemExit(main())
