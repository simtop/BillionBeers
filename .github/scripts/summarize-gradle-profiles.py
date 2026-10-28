#!/usr/bin/env python3
"""Summarize task work from Gradle HTML profiles without treating it as wall time."""

from __future__ import annotations

import argparse
from collections import defaultdict
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re


def seconds(value: str) -> float:
    match = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?", value)
    if not match or not any(value is not None for value in match.groups()):
        raise ValueError(f"Unrecognized Gradle duration: {value!r}")
    hours, minutes, remainder = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(remainder or 0)


class ProfileParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.cells = None
        self.cell = None
        self.task_row = False

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.cells = []
            self.task_row = False
        elif tag == "td" and self.cells is not None:
            self.cell = ""
            if "indentPath" in dict(attrs).get("class", "").split():
                self.task_row = True

    def handle_data(self, data):
        if self.cell is not None:
            self.cell += data

    def handle_endtag(self, tag):
        if tag == "td" and self.cell is not None:
            self.cells.append(self.cell.strip())
            self.cell = None
        elif tag == "tr" and self.cells is not None:
            self.rows.append((self.task_row, self.cells))
            self.cells = None


def category(task: str) -> str:
    name = task.rsplit(":", 1)[-1].lower()
    if name.startswith("link") and "test" in name and "ios" in name:
        return "Apple test linking"
    if name.startswith("link") and "framework" in name and "ios" in name:
        return "Apple framework linking"
    if "compile" in name:
        return "Compilation"
    if name.startswith("ksp") or name.startswith("generate"):
        return "Code and resource generation"
    if name.endswith("test") or name.startswith("verifypaparazzi"):
        return "Test tasks"
    return "Other tasks"


def parse_profile(path: Path) -> dict:
    parser = ProfileParser()
    parser.feed(path.read_text(encoding="utf-8"))
    tasks = []
    wall_seconds = None
    for task_row, cells in parser.rows:
        if len(cells) == 2 and cells[0] == "Total Build Time":
            wall_seconds = seconds(cells[1])
        if task_row:
            if len(cells) != 3 or not cells[0].startswith(":"):
                raise ValueError("Malformed Gradle task row")
            tasks.append({"task": cells[0], "seconds": seconds(cells[1]),
                          "outcome": cells[2] or "EXECUTED", "category": category(cells[0])})
    if wall_seconds is None or not tasks:
        raise ValueError("Gradle profile is missing build time or task rows")
    groups = defaultdict(float)
    for task in tasks:
        groups[task["category"]] += task["seconds"]
    return {
        "file": path.name,
        "build_wall_seconds": wall_seconds,
        "summed_task_seconds": round(sum(task["seconds"] for task in tasks), 3),
        "categories": {key: round(value, 3) for key, value in sorted(groups.items())},
        "tasks": sorted(tasks, key=lambda task: (-task["seconds"], task["task"])),
    }


def summarize(directory: Path) -> dict:
    profiles = []
    errors = []
    for path in sorted(directory.glob("profile-*.html")):
        try:
            profiles.append(parse_profile(path))
        except (OSError, ValueError):
            errors.append(f"Cannot parse {path.name}")
    return {"schema": 1, "available": bool(profiles), "profiles": profiles, "errors": errors,
            "provenance": {key: os.environ[key] for key in
                           ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "GITHUB_SHA")
                           if os.environ.get(key)}}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--outcome", choices=("unknown", "success", "failure", "cancelled"), default="unknown")
    parser.add_argument("--summary", type=Path,
                        default=Path(os.environ["GITHUB_STEP_SUMMARY"]) if os.environ.get("GITHUB_STEP_SUMMARY") else None)
    args = parser.parse_args(argv)
    record = summarize(args.directory)
    record["lane_outcome"] = args.outcome
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    lines = ["### Gradle task profiles", "", "Summed task durations include parallel work and are not wall-clock savings.",
             f"Lane outcome: `{args.outcome}`. A task recorded as executed is not proof that it passed.", ""]
    if not record["available"]:
        lines.append("No readable Gradle task profile was produced.")
    for profile in record["profiles"]:
        lines.extend([f"**{profile['file']}**, build wall time {profile['build_wall_seconds']:.1f}s", "",
                      "| Work | Summed task seconds |", "|---|---:|"])
        lines.extend(f"| {key} | {value:.1f} |" for key, value in profile["categories"].items())
        lines.extend(["", "Slowest tasks:", ""])
        lines.extend(f"- `{task['task']}`: {task['seconds']:.1f}s, {task['outcome']}"
                     for task in profile["tasks"][:8])
        lines.append("")
    lines.extend(record["errors"])
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")
    return 1 if record["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
