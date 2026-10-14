#!/usr/bin/env python3
"""Tests for weekly full-PR CI dispatch and its trust boundaries."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "weekly_pr_full_ci", ROOT / ".github/scripts/dispatch-weekly-pr-full-ci.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

REPOSITORY = "simtop/BillionBeers"


def pull_request(
    number: int, *, state: str = "open", base: str = "master",
    head_repository: str = REPOSITORY, branch: str = "feature/example",
) -> dict:
    return {
        "number": number,
        "state": state,
        "base": {"ref": base, "repo": {"full_name": REPOSITORY}},
        "head": {"ref": branch, "repo": {"full_name": head_repository}},
    }


class EligibilityTests(unittest.TestCase):
    def test_only_open_prs_for_supported_bases_are_selected(self):
        selected = MODULE.eligible_pull_requests(
            [
                pull_request(1),
                pull_request(2, base="main"),
                pull_request(3, head_repository="contributor/BillionBeers"),
                pull_request(4, state="closed"),
                pull_request(5, base="release"),
            ],
            REPOSITORY,
        )
        self.assertEqual([1, 2, 3], [pr["number"] for pr in selected])

    def test_open_pr_pages_are_fetched_until_short_page(self):
        api = MODULE.GitHub(REPOSITORY, "token")
        first_page = [pull_request(number) for number in range(100)]
        with patch.object(api, "request", side_effect=[first_page, [pull_request(100)]]) as request:
            result = api.open_pull_requests()
        self.assertEqual(101, len(result))
        self.assertEqual(
            [
                "pulls?state=open&per_page=100&page=1",
                "pulls?state=open&per_page=100&page=2",
            ],
            [call.args[0] for call in request.call_args_list],
        )

    def test_dispatch_targets_master_and_requests_full_validation_of_pr_merge_ref(self):
        api = MODULE.GitHub(REPOSITORY, "token")
        with patch.object(api, "request", return_value=204) as request:
            api.dispatch_full_ci(42)
        request.assert_called_once_with(
            "actions/workflows/ci.yml/dispatches",
            {
                "ref": "master",
                "inputs": {"force_full": "true", "pull_request_number": "42"},
            },
        )


class MainTests(unittest.TestCase):
    def test_dispatches_all_eligible_prs_including_forks(self):
        api = MODULE.GitHub(REPOSITORY, "token")
        api.open_pull_requests = lambda: [
            pull_request(4),
            pull_request(5, head_repository="outside/BillionBeers"),
        ]
        with patch.dict("os.environ", {"REPOSITORY": REPOSITORY, "GH_TOKEN": "token"}), patch.object(
            MODULE, "GitHub", return_value=api
        ), patch.object(api, "dispatch_full_ci") as dispatch:
            self.assertEqual(0, MODULE.main())
        self.assertEqual([4, 5], [call.args[0] for call in dispatch.call_args_list])

    def test_one_dispatch_failure_does_not_prevent_later_prs(self):
        api = MODULE.GitHub(REPOSITORY, "token")
        api.open_pull_requests = lambda: [pull_request(4), pull_request(5, branch="fix/other")]
        dispatch = patch.object(api, "dispatch_full_ci", side_effect=[RuntimeError(), None])
        with patch.dict("os.environ", {"REPOSITORY": REPOSITORY, "GH_TOKEN": "token"}), patch.object(
            MODULE, "GitHub", return_value=api
        ), dispatch as mocked_dispatch:
            self.assertEqual(1, MODULE.main())
        self.assertEqual([4, 5], [call.args[0] for call in mocked_dispatch.call_args_list])


class WorkflowTests(unittest.TestCase):
    def test_weekly_runner_uses_trusted_master_and_write_scope_only_for_dispatch(self):
        workflow = (ROOT / ".github/workflows/weekly-pr-full-ci.yml").read_text()
        self.assertIn("cron: '0 5 * * 1'", workflow)
        self.assertIn("if: github.ref == 'refs/heads/master'", workflow)
        self.assertIn("ref: master", workflow)
        self.assertIn("actions: write", workflow)
        self.assertIn("pull-requests: read", workflow)
        self.assertIn("python3 -I .github/scripts/dispatch-weekly-pr-full-ci.py", workflow)
        self.assertIn("Checkout trusted master workflow code", workflow)
        script = (ROOT / ".github/scripts/dispatch-weekly-pr-full-ci.py").read_text()
        self.assertIn('"pull_request_number": str(pull_request_number)', script)

    def test_weekly_dispatch_runs_trusted_master_workflow_on_pr_merge_ref(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("github.event_name == 'workflow_dispatch' && github.ref != 'refs/heads/master'", workflow)
        self.assertIn("Manual CI dispatch is supported only on master.", workflow)
        self.assertIn("run-name: CI validation for ${{ github.ref_name }}", workflow)
        self.assertIn("FORCE_FULL: ${{ inputs.force_full }}", workflow)
        self.assertIn("github.event.inputs.pull_request_number != ''", workflow)
        self.assertGreaterEqual(
            workflow.count("format('refs/pull/{0}/merge', github.event.inputs.pull_request_number)"), 12
        )


if __name__ == "__main__":
    unittest.main()
