#!/usr/bin/env python3
"""Enforce committed APK size limits against a release-smoke artifact manifest."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BUDGET = ROOT / "config/apk-size-budget.txt"
APK_SUFFIX = ".apk"
ARTIFACT_LINE = re.compile(r"^artifact=(\S+) sha256=(\S+) bytes=(\d+)$")


class BudgetError(ValueError):
    """Raised when a budget or manifest is malformed or violates the limits."""


def read_budget(path: Path) -> tuple[dict[str, int], int]:
    limits: dict[str, int] = {}
    total_limit: int | None = None
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.split("#", 1)[0].strip()
        if not line:
            continue
        fields = line.split()
        if len(fields) != 2 or not fields[1].isdigit():
            raise BudgetError(f"{path}:{line_number}: expected '<artifact path> <max bytes>'")
        name, raw_limit = fields
        limit = int(raw_limit)
        if limit <= 0:
            raise BudgetError(f"{path}:{line_number}: size limit must be positive")
        if name == "total":
            if total_limit is not None:
                raise BudgetError(f"{path}:{line_number}: duplicate total limit")
            total_limit = limit
        elif not name.endswith(APK_SUFFIX):
            raise BudgetError(f"{path}:{line_number}: only APK artifact paths are supported")
        elif name in limits:
            raise BudgetError(f"{path}:{line_number}: duplicate budget for {name}")
        else:
            limits[name] = limit
    if not limits:
        raise BudgetError(f"{path}: no APK budgets configured")
    if total_limit is None:
        raise BudgetError(f"{path}: missing total limit")
    return limits, total_limit


def check_manifest(manifest: Path, budget: Path) -> list[str]:
    limits, total_limit = read_budget(budget)
    actual: dict[str, int] = {}
    for line_number, line in enumerate(manifest.read_text(encoding="utf-8").splitlines(), 1):
        if not line.startswith("artifact="):
            continue
        match = ARTIFACT_LINE.fullmatch(line)
        if not match:
            raise BudgetError(f"{manifest}:{line_number}: malformed artifact record")
        artifact, _digest, raw_size = match.groups()
        if artifact.endswith(APK_SUFFIX):
            if artifact in actual:
                raise BudgetError(f"{manifest}:{line_number}: duplicate APK artifact {artifact}")
            actual[artifact] = int(raw_size)

    missing = sorted(limits.keys() - actual.keys())
    unbudgeted = sorted(actual.keys() - limits.keys())
    if missing:
        raise BudgetError(f"manifest is missing budgeted APKs: {', '.join(missing)}")
    if unbudgeted:
        raise BudgetError(f"manifest contains unbudgeted APKs: {', '.join(unbudgeted)}")

    failures: list[str] = []
    total = sum(actual.values())
    for artifact, size in actual.items():
        limit = limits[artifact]
        if size > limit:
            failures.append(f"{artifact}: {size} bytes exceeds {limit} byte limit")
    if total > total_limit:
        failures.append(f"total: {total} bytes exceeds {total_limit} byte limit")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="release-smoke manifest.txt")
    parser.add_argument("--budget", type=Path, default=DEFAULT_BUDGET, help="budget file")
    args = parser.parse_args()
    try:
        failures = check_manifest(args.manifest, args.budget)
    except (OSError, BudgetError) as error:
        print(f"APK size check failed: {error}", file=sys.stderr)
        return 1
    if failures:
        for failure in failures:
            print(f"❌ {failure}", file=sys.stderr)
        return 1
    print(f"✅ APK sizes are within the budget in {args.budget}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
