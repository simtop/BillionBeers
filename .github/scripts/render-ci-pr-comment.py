#!/usr/bin/env python3
"""Render a compact PR diagnosis from a completed CI job list."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def render(data: dict, run_url: str, repository: str, run_id: str, diagnostics: list[str] | None = None) -> str:
    failed = [job for job in data.get("jobs", []) if job.get("conclusion") in {"failure", "cancelled"}]
    lines = ["<!-- billionbeers-ci:diagnosis -->", "## ❌ CI needs attention", ""]
    if failed:
        lines += ["### Failed jobs", "", *[f"- **{job.get('name', 'Unknown')}** — {job.get('conclusion')}" for job in failed], ""]
    lines += [f"[Open the CI run]({run_url})", ""]
    artifacts = [artifact for artifact in data.get("artifacts", []) if not artifact.get("expired")]
    if artifacts:
        lines += [
            "### Evidence",
            "",
            *[
                f"- [{artifact.get('name', 'artifact')}](https://github.com/{repository}/actions/runs/{run_id}/artifacts/{artifact.get('id', '')})"
                for artifact in artifacts
            ],
            "",
        ]
    commands = {
        "Code Style Formatting Check": "gh workflow run format_fix.yml --ref <branch>",
        "Screenshot Tests (Paparazzi)": "gh workflow run record_screenshots.yml --ref <branch> -f modules='<affected modules>'",
        "Unit Tests": "make test",
        "Instrumented Tests (Gradle Managed Device)": "make ui-test-managed-ci",
        "Static Analysis (Detekt)": "make lint",
    }
    if failed:
        lines += ["### Reproduce or fix", ""]
        for job in failed:
            command = commands.get(job.get("name"), "Open the failing job log and rerun its displayed command.")
            lines.append(f"- `{command}`")
        lines.append("")
    if diagnostics:
        lines += ["### Exact failures", ""]
        for diagnostic in diagnostics:
            text = diagnostic.strip()
            if text:
                lines += [text, ""]
    lines.append("The detailed Step Summary and uploaded artifacts contain the complete logs.")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jobs", type=Path)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--diagnostics", action="append", type=Path, default=[])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    diagnostics = [path.read_text(encoding="utf-8") for path in args.diagnostics if path.exists()]
    args.output.write_text(render(json.loads(args.jobs.read_text()), args.run_url, args.repository, args.run_id, diagnostics), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
