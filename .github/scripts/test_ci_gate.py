#!/usr/bin/env python3
"""Exercise the same inline gate used by GitHub Actions."""

import json
import os
from pathlib import Path
import subprocess
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows/ci.yml"
STEP = WORKFLOW.read_text().split("- name: Require the expected CI jobs to succeed", 1)[1]
GATE = textwrap.dedent(STEP.split("run: |\n", 1)[1].split("\n      - name:", 1)[0])
ALWAYS = ("format-check", "changes", "lint", "android-lint", "dependency-guard")
PR_ONLY = ("secret-scan", "dependency-api")
LANES = {
    "unit": "unit-tests",
    "screenshot": "screenshot-tests",
    "instrumented": "instrumented-tests",
    "native": "native-tests",
    "web": "web-tests",
}


def fixture(event="pull_request", selected=()):
    pr_result = "success" if event == "pull_request" else "skipped"
    needs = {job: {"result": "success"} for job in ALWAYS}
    needs.update({job: {"result": pr_result} for job in PR_ONLY})
    needs["changes"]["outputs"] = {lane: str(lane in selected).lower() for lane in LANES}
    needs.update({
        job: {"result": "success" if lane in selected else "skipped"}
        for lane, job in LANES.items()
    })
    return needs


def run_gate(needs, event="pull_request"):
    env = os.environ | {"EVENT_NAME": event, "NEEDS_JSON": json.dumps(needs)}
    return subprocess.run(["bash", "-c", GATE], env=env, capture_output=True, text=True)


class CiGateTests(unittest.TestCase):
    def test_docs_pr_and_non_pr_validation_are_valid(self):
        docs = run_gate(fixture())
        self.assertEqual(0, docs.returncode, docs.stdout + docs.stderr)
        dispatch = run_gate(fixture("workflow_dispatch", selected=LANES), "workflow_dispatch")
        self.assertEqual(0, dispatch.returncode, dispatch.stdout + dispatch.stderr)

    def test_selected_lane_cannot_skip(self):
        needs = fixture(selected=("web",))
        needs["web-tests"]["result"] = "skipped"
        result = run_gate(needs)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("web-tests expected success, got skipped", result.stdout)

    def test_missing_lane_selection_fails(self):
        needs = fixture()
        del needs["changes"]["outputs"]["native"]
        result = run_gate(needs)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid or missing selection for native", result.stdout)

    def test_required_jobs_cannot_skip(self):
        for job in (*ALWAYS, *PR_ONLY):
            with self.subTest(job=job):
                needs = fixture()
                needs[job]["result"] = "skipped"
                result = run_gate(needs)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"{job} expected success, got skipped", result.stdout)

    def test_new_jobs_cannot_skip_without_an_explicit_policy(self):
        needs = fixture()
        needs["new-job"] = {"result": "skipped"}
        result = run_gate(needs)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("new-job has no skip policy and reported skipped", result.stdout)


if __name__ == "__main__":
    unittest.main()
