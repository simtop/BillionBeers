#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("report-master-ci-failure.py")
SPEC = importlib.util.spec_from_file_location("master_ci_failure_report", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FakeGh:
    def __init__(self, issues: list[dict[str, object]]) -> None:
        self.issues = issues
        self.commands: list[list[str]] = []
        self.issue_body = ""

    def __call__(self, command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        self.commands.append(command)
        if command[1:3] == ["issue", "list"]:
            return subprocess.CompletedProcess(command, 0, json.dumps(self.issues), "")
        body_file = Path(command[command.index("--body-file") + 1])
        self.issue_body = body_file.read_text(encoding="utf-8")
        return subprocess.CompletedProcess(command, 0, "", "")


class MasterCiFailureReportTest(unittest.TestCase):
    def test_body_lists_failed_and_cancelled_lanes_with_latest_run(self) -> None:
        body = MODULE.render_body(
            "simtop/BillionBeers",
            "12345",
            "https://github.com/simtop/BillionBeers/actions/runs/12345",
            "abcdef1234567890",
            {
                "unit-tests": {"result": "failure"},
                "native-tests": {"result": "cancelled"},
                "changes": {"result": "success"},
                "ci-gate": {"result": "failure"},
            },
        )

        self.assertIn("[12345](https://github.com/simtop/BillionBeers/actions/runs/12345)", body)
        self.assertIn("`unit-tests` — failure", body)
        self.assertIn("`native-tests` — cancelled", body)
        self.assertNotIn("`changes`", body)
        self.assertNotIn("`ci-gate`", body)

    def test_creates_issue_when_no_matching_open_issue_exists(self) -> None:
        gh = FakeGh([{"number": 9, "title": "Unrelated issue"}])

        MODULE.upsert_issue("simtop/BillionBeers", "latest failed run", gh)

        self.assertEqual("create", gh.commands[-1][2])
        self.assertEqual(MODULE.ISSUE_TITLE, gh.commands[-1][gh.commands[-1].index("--title") + 1])
        self.assertEqual("latest failed run", gh.issue_body)

    def test_updates_the_existing_single_open_issue(self) -> None:
        gh = FakeGh(
            [
                {"number": 17, "title": MODULE.ISSUE_TITLE},
                {"number": 21, "title": MODULE.ISSUE_TITLE},
            ]
        )

        MODULE.upsert_issue("simtop/BillionBeers", "newer failed run", gh)

        self.assertEqual(["gh", "issue", "edit", "17"], gh.commands[-1][:4])
        self.assertEqual("newer failed run", gh.issue_body)


if __name__ == "__main__":
    unittest.main()
