#!/usr/bin/env python3
"""Render a Markdown phase timing table for a GitHub Step Summary."""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path


def duration(value: str) -> str:
    if not value.isdigit():
        return "—"
    seconds = int(value)
    return f"{seconds // 60}m {seconds % 60:02d}s"


def parse_phases(values: list[str]) -> list[list[str]]:
    rows = [item.split("|", 2) for item in values]
    if any(len(row) != 3 for row in rows):
        raise ValueError("each --phase must be NAME|OUTCOME|SECONDS")
    return rows


def timing_record(title: str, rows: list[list[str]]) -> dict[str, object]:
    return {
        "schema": 1,
        "title": title,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "provenance": {
            key: os.environ[key]
            for key in ("GITHUB_REPOSITORY", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "GITHUB_SHA")
            if os.environ.get(key)
        },
        "phases": [
            {
                "name": name,
                "outcome": outcome,
                "elapsed_seconds": int(seconds) if seconds.isdigit() else None,
            }
            for name, outcome, seconds in rows
        ],
        "measured_phase_total_seconds": sum(int(row[2]) for row in rows if row[2].isdigit()),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", required=True)
    parser.add_argument("--phase", action="append", default=[], metavar="NAME|OUTCOME|SECONDS")
    parser.add_argument(
        "--summary",
        type=Path,
        default=Path(os.environ["GITHUB_STEP_SUMMARY"]) if os.environ.get("GITHUB_STEP_SUMMARY") else None,
    )
    parser.add_argument("--json", type=Path)
    args = parser.parse_args(argv)
    if args.summary is None:
        parser.error("--summary or GITHUB_STEP_SUMMARY is required")
    try:
        rows = parse_phases(args.phase)
    except ValueError as error:
        parser.error(str(error))
    record = timing_record(args.title, rows)
    total = record["measured_phase_total_seconds"]
    lines = [f"### {args.title}", "", "| Phase | Outcome | Elapsed |", "|---|---|---:|"]
    lines.extend(
        f"| {name} | `{outcome}` | {duration(seconds)} |"
        for name, outcome, seconds in rows
    )
    lines.extend(["", f"Measured phase total: **{duration(str(total))}**", ""])
    with args.summary.open("a", encoding="utf-8") as summary:
        summary.write("\n".join(lines) + "\n")
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
