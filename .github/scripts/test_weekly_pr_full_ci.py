#!/usr/bin/env python3
"""Tests for weekly full-PR CI dispatch and its trust boundaries."""

from __future__ import annotations

import importlib.util
import os
import subprocess
import tempfile
import textwrap
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

    def test_dispatch_targets_the_read_only_pr_workflow(self):
        api = MODULE.GitHub(REPOSITORY, "token")
        with patch.object(api, "request", return_value=204) as request:
            api.dispatch_full_ci(42)
        request.assert_called_once_with(
            "actions/workflows/weekly-pr-full-ci.yml/dispatches",
            {
                "ref": "master",
                "inputs": {"pull_request_number": "42"},
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
    def test_dispatch_and_validation_have_separate_permissions(self):
        workflow = (ROOT / ".github/workflows/weekly-pr-full-ci.yml").read_text()
        dispatch, resolve_and_validate = workflow.split("\n  resolve:", 1)
        self.assertIn("cron: '0 5 * * 1'", dispatch)
        self.assertIn("github.ref == 'refs/heads/master' && !inputs.pull_request_number", dispatch)
        self.assertIn("python3 -I .github/scripts/dispatch-weekly-pr-full-ci.py", dispatch)
        self.assertIn("actions: write", dispatch)
        self.assertNotIn("actions: write", resolve_and_validate)
        self.assertNotIn("secrets: inherit", resolve_and_validate)
        validation = resolve_and_validate.split("\n  validate:", 1)[1]
        self.assertIn("cache-mode: read", validation)
        self.assertIn("uses: ./.github/workflows/ci.yml", validation)
        self.assertIn("force_full: true", validation)
        self.assertIn("tested_sha: ${{ needs.resolve.outputs.tested_sha }}", validation)

    def test_called_ci_pins_checkouts_and_separates_pr_concurrency(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("Manual CI dispatch is supported only on master.", workflow)
        self.assertIn("FORCE_FULL: ${{ inputs.force_full }}", workflow)
        self.assertNotIn("refs/pull/", workflow)
        self.assertIn("format('full-pr-ci-{0}', inputs.pull_request_number)", workflow)
        self.assertIn("format('{0}-{1}', github.workflow, github.ref)", workflow)
        self.assertEqual(13, workflow.count("ref: ${{ inputs.tested_sha }}"))
        self.assertEqual(2, workflow.count("CI_TESTED_SHA: ${{ inputs.tested_sha }}"))
        self.assertIn("persist-credentials: ${{ inputs.tested_sha == '' }}", workflow)
        self.assertIn("inputs.tested_sha == '' && github.event_name == 'push'", workflow)

    def test_merge_resolver_pins_exact_pr_parents(self):
        workflow = (ROOT / ".github/workflows/weekly-pr-full-ci.yml").read_text()
        resolver = workflow.split("id: merge", 1)[1].split("  validate:", 1)[0]
        script = textwrap.dedent(resolver.split("run: |\n", 1)[1])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gh = root / "gh"
            gh.write_text("""#!/usr/bin/env bash
case "$2" in
  */pulls/42) printf '{"state":"open","base":{"ref":"master","repo":{"full_name":"%s"},"sha":"%s"},"head":{"sha":"%s"}}\n' "$REPOSITORY" "$BASE" "$HEAD" ;;
  */git/ref/pull/42/merge) printf '%s\n' "$MERGE" ;;
  */git/commits/"$MERGE") printf '{"parents":[{"sha":"%s"},{"sha":"%s"}]}\n' "$BASE" "$MERGE_HEAD" ;;
  *) exit 1 ;;
esac
""")
            gh.chmod(0o755)
            output = root / "output"
            env = dict(
                os.environ, PATH=f"{root}{os.pathsep}{os.environ['PATH']}",
                PR_NUMBER="42", REPOSITORY=REPOSITORY, BASE="a" * 40,
                HEAD="b" * 40, MERGE="c" * 40, MERGE_HEAD="b" * 40,
                GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(root / "summary"),
            )
            result = subprocess.run(["bash", "-c", script], env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(f"sha={'c' * 40}\n", output.read_text())
            self.assertEqual(f"Validating PR #42 at merge commit {'c' * 40}.\n", (root / "summary").read_text())
            output.unlink()
            result = subprocess.run(
                ["bash", "-c", script], env=env | {"MERGE_HEAD": "d" * 40},
                capture_output=True, text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("does not match the current PR base and head", result.stdout)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
