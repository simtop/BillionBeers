#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("report-master-ci-failure.py")
ROOT = SCRIPT.parents[2]
SPEC = importlib.util.spec_from_file_location("master_ci_failure_report", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

REPOSITORY = "simtop/BillionBeers"
COMMIT = "a" * 40
RUN_URL = "https://github.com/simtop/BillionBeers/actions/runs/12345"
TRACKER = {"number": 17, "title": MODULE.ISSUE_TITLE, "author": {"login": "app/github-actions"}}


class FakeGh:
    def __init__(self, issues: list[dict[str, object]], head: str = COMMIT) -> None:
        self.issues = issues
        self.head = head
        self.commands: list[list[str]] = []
        self.issue_body = ""

    def __call__(self, command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        self.commands.append(command)
        if command[1:3] == ["issue", "list"]:
            return subprocess.CompletedProcess(command, 0, json.dumps(self.issues), "")
        if command[1] == "api":
            return subprocess.CompletedProcess(command, 0, self.head + "\n", "")
        if "--body-file" in command:
            body_file = Path(command[command.index("--body-file") + 1])
            self.issue_body = body_file.read_text(encoding="utf-8")
        return subprocess.CompletedProcess(command, 0, "", "")

    def reconcile(self, gate: str) -> None:
        MODULE.reconcile_issue(REPOSITORY, "12345", RUN_URL, COMMIT, {"ci-gate": {"result": gate}}, self)


class MasterCiFailureReportTest(unittest.TestCase):
    def test_workflow_tracks_master_failures_and_recovery_with_minimal_permissions(self) -> None:
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        job_match = re.search(
            r"(?ms)^  master-failure-issue:\n(?P<job>.*?)(?=^  [\w-]+:|\Z)",
            workflow,
        )
        self.assertIsNotNone(job_match, "master-failure-issue job is missing")
        assert job_match is not None
        job = job_match.group("job")
        self.assertIn("github.ref == 'refs/heads/master'", job)
        self.assertIn("inputs.tested_sha == ''", job)
        self.assertIn("needs.ci-gate.result == 'failure' || needs.ci-gate.result == 'success'", job)

        permissions_match = re.search(r"(?ms)^    permissions:\n(?P<permissions>.*?)(?=^    \w|\Z)", job)
        self.assertIsNotNone(permissions_match, "job permissions are missing")
        assert permissions_match is not None
        self.assertRegex(permissions_match.group("permissions"), r"(?m)^      contents: read$")
        self.assertRegex(permissions_match.group("permissions"), r"(?m)^      issues: write$")

        checkout = re.search(
            r"(?ms)^      - name: Checkout code\n"
            r"        uses: actions/checkout@([0-9a-f]{40})(?:\s+# .*?)?\n"
            r"        with:\n"
            r"          ref: \$\{\{ github\.sha \}\}\n"
            r"          persist-credentials: false\n",
            job,
        )
        self.assertIsNotNone(checkout, "failure reporter must check out the triggering commit safely")
        self.assertLess(
            job.index("- name: Checkout code"),
            job.index("run: python3 .github/scripts/report-master-ci-failure.py"),
        )

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

        gh.reconcile("failure")

        self.assertEqual("create", gh.commands[-1][2])
        self.assertEqual(MODULE.ISSUE_TITLE, gh.commands[-1][gh.commands[-1].index("--title") + 1])
        self.assertIn(RUN_URL, gh.issue_body)

    def test_updates_the_existing_single_open_issue(self) -> None:
        gh = FakeGh(
            [
                TRACKER,
                TRACKER | {"number": 21},
            ]
        )

        gh.reconcile("failure")

        self.assertEqual(["gh", "issue", "edit", "17"], gh.commands[-1][:4])
        self.assertIn(RUN_URL, gh.issue_body)

    def test_current_master_success_closes_the_tracker_with_the_recovery_run(self) -> None:
        gh = FakeGh([TRACKER])

        gh.reconcile("success")

        self.assertEqual(["gh", "issue", "close", "17"], gh.commands[-1][:4])
        self.assertIn(RUN_URL, gh.commands[-1][-1])

    def test_success_leaves_other_issues_alone(self) -> None:
        gh = FakeGh([
            TRACKER | {"author": {"login": "maintainer"}},
            TRACKER | {"title": "Other tracker"},
        ])

        gh.reconcile("success")

        self.assertEqual([["issue", "list"]], [command[1:3] for command in gh.commands])

    def test_stale_master_results_cannot_change_the_tracker(self) -> None:
        for gate in ("failure", "success"):
            with self.subTest(gate=gate):
                gh = FakeGh([TRACKER], head="b" * 40)

                gh.reconcile(gate)

                self.assertEqual(["issue", "api"], [command[1] for command in gh.commands])

    def test_cancelled_gate_does_not_change_the_tracker(self) -> None:
        gh = FakeGh([TRACKER])

        gh.reconcile("cancelled")

        self.assertEqual([], gh.commands)


if __name__ == "__main__":
    unittest.main()
