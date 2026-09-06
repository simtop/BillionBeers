#!/usr/bin/env python3
"""Render a Markdown phase timing table for a GitHub Step Summary."""

from __future__ import annotations

import argparse
import os
from pathlib import Path


def duration(value: str) -> str:
    if not value.isdigit():
        return "—"
    seconds = int(value)
    return f"{seconds // 60}m {seconds % 60:02d}s"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", required=True)
    parser.add_argument("--phase", action="append", default=[], metavar="NAME|OUTCOME|SECONDS")
    parser.add_argument("--summary", type=Path, default=Path(os.environ["GITHUB_STEP_SUMMARY"]))
    args = parser.parse_args()
    rows = [item.split("|", 2) for item in args.phase]
    if any(len(row) != 3 for row in rows):
        parser.error("each --phase must be NAME|OUTCOME|SECONDS")
    total = sum(int(row[2]) for row in rows if row[2].isdigit())
    lines = [f"### {args.title}", "", "| Phase | Outcome | Elapsed |", "|---|---|---:|"]
    lines.extend(f"| {name} | `{outcome}` | {duration(seconds)} |" for name, outcome, seconds in rows)
    lines.extend(["", f"Measured phase total: **{duration(str(total))}**", ""])
    args.summary.open("a", encoding="utf-8").write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
