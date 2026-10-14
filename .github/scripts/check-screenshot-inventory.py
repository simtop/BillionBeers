#!/usr/bin/env python3
"""Compare the executed Paparazzi preview inventory with its reviewed baseline."""

from __future__ import annotations

import argparse
import collections
import sys
from pathlib import Path


def read_rows(path: Path) -> list[tuple[str, ...]]:
    rows = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line:
            continue
        fields = line.split("\t")
        if len(fields) > 10:
            raise ValueError(f"{path}:{line_number}: expected 10 TSV fields")
        row = tuple(fields + [""] * (10 - len(fields)))
        rows.append(row)
    return rows


def actual_rows(inventory_dir: Path) -> list[tuple[str, ...]]:
    files = sorted(inventory_dir.glob("*.tsv"))
    if not files:
        raise ValueError(f"no inventory TSV files found in {inventory_dir}")
    return [row for path in files for row in read_rows(path)]


def format_rows(rows: list[tuple[str, ...]]) -> str:
    # Empty trailing columns are implicit in the baseline to avoid Git's trailing-whitespace
    # check rejecting the tabs Paparazzi emits for unused metadata fields.
    return "".join("\t".join(row).rstrip("\t") + "\n" for row in sorted(rows))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path, help="directory written by make screenshot-verify")
    parser.add_argument("baseline", type=Path, help="reviewed TSV inventory baseline")
    parser.add_argument("--update", action="store_true", help="replace the baseline with this inventory")
    args = parser.parse_args()

    try:
        current = actual_rows(args.inventory)
        if args.update:
            args.baseline.parent.mkdir(parents=True, exist_ok=True)
            args.baseline.write_text(format_rows(current), encoding="utf-8")
            print(f"Updated {args.baseline} with {len(current)} executed preview configurations")
            return 0

        expected = read_rows(args.baseline)
    except (OSError, ValueError) as error:
        print(f"screenshot inventory error: {error}", file=sys.stderr)
        return 2

    expected_counts = collections.Counter(expected)
    current_counts = collections.Counter(current)
    missing = expected_counts - current_counts
    added = current_counts - expected_counts
    if not missing and not added:
        print(f"Screenshot inventory matches ({len(current)} preview configurations)")
        return 0

    print("Screenshot inventory differs from the reviewed baseline.", file=sys.stderr)
    for label, differences in (("Missing", missing), ("Added", added)):
        for row, count in sorted(differences.items()):
            print(f"{label} x{count}: {row[0]} / {row[1]}", file=sys.stderr)
    print(
        "After reviewing an intentional preview change, run `make screenshot-inventory-record`.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
