#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("render-ci-pr-comment.py")
SPEC = importlib.util.spec_from_file_location("render_ci_pr_comment", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class RenderCiPrCommentTest(unittest.TestCase):
    def test_cancelled_job_is_distinguished_from_test_failure(self) -> None:
        body = MODULE.render(
            {
                "jobs": [
                    {"name": "Instrumented Tests (Gradle Managed Device)", "conclusion": "cancelled"}
                ],
                "artifacts": [],
            },
            "https://github.com/simtop/BillionBeers/actions/runs/123",
            "simtop/BillionBeers",
            "123",
        )

        self.assertIn("Instrumented Tests (Gradle Managed Device)", body)
        self.assertIn("cancelled", body)
        self.assertIn("Cancellation note", body)
        self.assertIn("not evidence of a test assertion failure", body)
        self.assertIn("rerun the workflow", body)
        self.assertIn("https://github.com/simtop/BillionBeers/actions/runs/123", body)

    def test_unavailable_artifacts_do_not_hide_cancelled_job(self) -> None:
        body = MODULE.render(
            {
                "jobs": [{"name": "Unit Tests", "conclusion": "failure"}],
                "artifacts": [{"name": "expired", "id": 1, "expired": True}],
            },
            "https://github.com/example/run",
            "owner/repo",
            "9",
        )

        self.assertIn("Unit Tests", body)
        self.assertNotIn("expired", body)
        self.assertIn("Open the CI run", body)


if __name__ == "__main__":
    unittest.main()
