#!/usr/bin/env python3
"""Compare the executed Paparazzi preview inventory with its reviewed baseline."""

from __future__ import annotations

import argparse
import collections
import os
import re
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


def generated_test_class(namespace: str) -> str:
    safe_name = namespace.replace(".", "_")
    return (
        "com.simtop.billionbeers.screenshot_"
        + safe_name[0].upper()
        + safe_name[1:]
        + "ScreenshotTest"
    )


def orphaned_goldens(
    root: Path,
    rows: list[tuple[str, ...]],
    scoped_namespaces: set[str] | None = None,
) -> list[tuple[str, str]]:
    previews_by_namespace: dict[str, set[str]] = collections.defaultdict(set)
    for row in rows:
        previews_by_namespace[row[0]].add(row[1])

    orphans = []
    for current_directory, directories, _ in os.walk(root):
        directories[:] = [
            directory
            for directory in directories
            if directory not in {"build", ".git", ".gradle"}
            and not directory.startswith(".")
        ]
        images = Path(current_directory)
        if (
            images.name != "images"
            or images.parent.name != "snapshots"
            or images.parent.parent.name != "test"
        ):
            continue

        module_root = images.parents[3]
        build_file = module_root / "build.gradle.kts"
        if not build_file.is_file():
            continue
        namespace_match = re.search(
            r"namespace\s*=\s*[\"']([^\"']+)[\"']",
            build_file.read_text(encoding="utf-8"),
        )
        if not namespace_match:
            continue
        namespace = namespace_match.group(1)
        if scoped_namespaces is not None and namespace not in scoped_namespaces:
            continue
        previews = previews_by_namespace.get(namespace, set())

        generated_prefix = generated_test_class(namespace) + "_snapshot["
        for golden in images.glob("*.png"):
            if not golden.name.startswith(generated_prefix):
                continue
            preview, separator, _ = golden.name[len(generated_prefix) :].partition("]_")
            if not separator or preview not in previews:
                orphans.append((namespace, golden.relative_to(root).as_posix()))
    return sorted(orphans)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path, help="directory written by make screenshot-verify")
    parser.add_argument("baseline", type=Path, help="reviewed TSV inventory baseline")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="repository root used to find Paparazzi goldens",
    )
    parser.add_argument("--update", action="store_true", help="replace the baseline with this inventory")
    parser.add_argument(
        "--scoped",
        action="store_true",
        help="limit comparison/update to modules represented by inventory files",
    )
    args = parser.parse_args()

    try:
        if not args.root.is_dir():
            raise ValueError(f"repository root does not exist: {args.root}")
        current = actual_rows(args.inventory)
        expected = read_rows(args.baseline)
        if args.scoped:
            inventory_files = {path.name for path in args.inventory.glob("*.tsv")}
            scoped_modules = {row[0] for row in current}
            scoped_modules.update(
                row[0] for row in expected
                if f"{row[0].replace('.', '_')}.tsv" in inventory_files
            )
            scoped_namespaces = scoped_modules
            current = [row for row in current if row[0] in scoped_modules]
            expected_in_scope = [row for row in expected if row[0] in scoped_modules]
        else:
            scoped_namespaces = None
            expected_in_scope = expected
        orphans = orphaned_goldens(args.root, current, scoped_namespaces)
        if args.update:
            updated = current
            if args.scoped:
                updated.extend(row for row in expected if row not in expected_in_scope)
            args.baseline.parent.mkdir(parents=True, exist_ok=True)
            args.baseline.write_text(format_rows(updated), encoding="utf-8")
            print(f"Updated {args.baseline} with {len(current)} executed preview configurations")
            return 0
    except (OSError, ValueError) as error:
        print(f"screenshot inventory error: {error}", file=sys.stderr)
        return 2

    if orphans:
        print("Screenshot goldens without an executed preview were found.", file=sys.stderr)
        for namespace, path in orphans:
            print(f"Orphan: {namespace} / {path}", file=sys.stderr)
        print(
            "Remove the stale Paparazzi golden, then rerun `make screenshot-verify`.",
            file=sys.stderr,
        )
        return 1

    expected_counts = collections.Counter(expected_in_scope)
    current_counts = collections.Counter(current)
    missing = expected_counts - current_counts
    added = current_counts - expected_counts
    if not missing and not added:
        print(
            f"Screenshot inventory matches ({len(current)} preview configurations; "
            "no orphaned generated goldens)"
        )
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
