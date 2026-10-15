#!/usr/bin/env python3
"""Fail when too many baseline-profile classes are absent from the release-smoke R8 mapping."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROFILE = ROOT / "app/src/release/generated/baselineProfiles/baseline-prof.txt"
DEFAULT_MAPPING = ROOT / "app/build/outputs/mapping/releaseSmoke/mapping.txt"
DEFAULT_THRESHOLD = ROOT / "config/baseline-profile-max-missing-percent.txt"
PROFILE_CLASS = re.compile(r"^[A-Z]*L([^;]+);")


class ProfileMappingError(ValueError):
    """Raised when a profile, mapping or threshold is invalid."""


def read_threshold(path: Path) -> float:
    values = [
        line.split("#", 1)[0].strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.split("#", 1)[0].strip()
    ]
    if len(values) != 1:
        raise ProfileMappingError(f"{path}: expected one percentage from 0 to 100")
    try:
        threshold = float(values[0])
    except ValueError as error:
        raise ProfileMappingError(f"{path}: expected a percentage from 0 to 100") from error
    if not 0 <= threshold <= 100:
        raise ProfileMappingError(f"{path}: percentage must be between 0 and 100")
    return threshold


def read_profile_classes(path: Path) -> set[str]:
    classes: set[str] = set()
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = PROFILE_CLASS.match(line)
        if not match:
            raise ProfileMappingError(f"{path}:{line_number}: malformed baseline profile rule")
        classes.add(match.group(1).replace("/", "."))
    if not classes:
        raise ProfileMappingError(f"{path}: no baseline profile classes found")
    return classes


def read_mapping_classes(path: Path) -> set[str]:
    classes: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line or raw_line[0].isspace() or raw_line.startswith("#"):
            continue
        if " -> " in raw_line and raw_line.endswith(":"):
            classes.add(raw_line.split(" -> ", 1)[0])
    if not classes:
        raise ProfileMappingError(f"{path}: no R8 class mappings found")
    return classes


def check_profile_mapping(profile: Path, mapping: Path, max_missing_percent: float) -> tuple[int, int, list[str]]:
    profile_classes = read_profile_classes(profile)
    mapping_classes = read_mapping_classes(mapping)
    missing = sorted(profile_classes - mapping_classes)
    percentage = 100 * len(missing) / len(profile_classes)
    failures = []
    if percentage > max_missing_percent:
        sample = ", ".join(missing[:10])
        suffix = " …" if len(missing) > 10 else ""
        failures.append(
            f"{percentage:.1f}% of baseline-profile classes are missing from the R8 mapping "
            f"({len(missing)}/{len(profile_classes)}; limit {max_missing_percent:.1f}%). "
            f"Examples: {sample}{suffix}"
        )
    return len(profile_classes), len(missing), failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE, help="baseline-prof.txt")
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING, help="release-smoke mapping.txt")
    parser.add_argument("--threshold", type=Path, default=DEFAULT_THRESHOLD, help="maximum missing percentage")
    args = parser.parse_args()
    try:
        threshold = read_threshold(args.threshold)
        total, missing, failures = check_profile_mapping(args.profile, args.mapping, threshold)
    except (OSError, ProfileMappingError) as error:
        print(f"Baseline profile check failed: {error}", file=sys.stderr)
        return 1
    if failures:
        for failure in failures:
            print(f"❌ {failure}", file=sys.stderr)
        return 1
    percentage = 100 * missing / total
    print(f"✅ {missing}/{total} baseline-profile classes missing ({percentage:.1f}%; limit {threshold:.1f}%)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
