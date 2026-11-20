#!/usr/bin/env python3
"""Track CI failures and recovery for the current master commit."""

from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import tempfile
from typing import Callable

ISSUE_TITLE = "CI: master branch validation failed"
ISSUE_AUTHOR = "app/github-actions"
FAILURE_RESULTS = {"failure", "cancelled"}
Run = Callable[..., subprocess.CompletedProcess[str]]


def render_body(
    repository: str,
    run_id: str,
    run_url: str,
    commit: str,
    needs: dict[str, dict[str, str]],
) -> str:
    failed_jobs = [
        (name, result.get("result", "unknown"))
        for name, result in needs.items()
        if result.get("result") in FAILURE_RESULTS and name != "ci-gate"
    ]
    lines = [
        "## Latest failed master validation",
        "",
        f"Run: [{run_id}]({run_url})",
        f"Commit: [`{commit[:12]}`](https://github.com/{repository}/commit/{commit})",
        "",
        "Failed or cancelled CI jobs:",
        *(f"- `{name}` — {result}" for name, result in failed_jobs),
    ]
    if not failed_jobs:
        lines.append("- The aggregate CI gate failed without a failed or cancelled lane result.")
    lines.extend(
        [
            "",
            "This issue closes when CI Gate passes on the current master commit.",
            "",
            "Filed automatically by the BillionBeers CI workflow.",
        ]
    )
    return "\n".join(lines) + "\n"


def find_open_issue(repository: str, run: Run = subprocess.run) -> int | None:
    result = run(
        [
            "gh",
            "issue",
            "list",
            "--repo",
            repository,
            "--state",
            "open",
            "--json",
            "number,title,author",
            "--limit",
            "100",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    matches = [
        issue["number"]
        for issue in json.loads(result.stdout)
        if issue.get("title") == ISSUE_TITLE
        and (issue.get("author") or {}).get("login") == ISSUE_AUTHOR
    ]
    return min(matches) if matches else None


def upsert_issue(
    repository: str,
    issue_number: int | None,
    body: str,
    run: Run,
) -> None:
    with tempfile.TemporaryDirectory() as temporary_directory:
        body_file = Path(temporary_directory) / "master-ci-failure.md"
        body_file.write_text(body, encoding="utf-8")
        if issue_number is None:
            command = [
                "gh",
                "issue",
                "create",
                "--repo",
                repository,
                "--title",
                ISSUE_TITLE,
                "--body-file",
                str(body_file),
            ]
        else:
            command = [
                "gh",
                "issue",
                "edit",
                str(issue_number),
                "--repo",
                repository,
                "--body-file",
                str(body_file),
            ]
        run(command, check=True)


def reconcile_issue(
    repository: str,
    run_id: str,
    run_url: str,
    commit: str,
    needs: dict[str, dict[str, str]],
    run: Run = subprocess.run,
) -> None:
    gate = needs.get("ci-gate", {}).get("result")
    if gate not in {"success", "failure"}:
        return

    issue_number = find_open_issue(repository, run)
    if gate == "success" and issue_number is None:
        return

    # An older run or rerun can finish after master has advanced.
    head = run(
        ["gh", "api", f"repos/{repository}/git/ref/heads/master", "--jq", ".object.sha"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if head != commit:
        print("Master has advanced. Leaving the CI failure issue unchanged.")
        return

    if gate == "failure":
        body = render_body(repository, run_id, run_url, commit, needs)
        upsert_issue(repository, issue_number, body, run)
    else:
        run(
            [
                "gh", "issue", "close", str(issue_number), "--repo", repository,
                "--comment", f"CI Gate passed on master in [run {run_id}]({run_url}).",
            ],
            check=True,
        )


def main() -> int:
    reconcile_issue(
        repository=os.environ["REPOSITORY"],
        run_id=os.environ["RUN_ID"],
        run_url=os.environ["RUN_URL"],
        commit=os.environ["COMMIT_SHA"],
        needs=json.loads(os.environ["NEEDS_JSON"]),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
