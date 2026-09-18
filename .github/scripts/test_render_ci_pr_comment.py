#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("render-ci-pr-comment.py")
SPEC = importlib.util.spec_from_file_location("render_ci_pr_comment", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def job(name="Unit Tests", step="Run Unit Tests", conclusion="failure", **kwargs):
    return dict(name=name, conclusion=conclusion, html_url="https://github.test/job/1",
                steps=[{"name": step, "conclusion": conclusion}], **kwargs)


def render(jobs=(), **kwargs):
    return MODULE.render(dict(jobs=list(jobs), **kwargs), "https://github.test/run/1", "owner/repo", "1")


class RenderCiPrCommentTest(unittest.TestCase):
    def test_coverage_only_failure_has_no_unrelated_evidence_or_test_diagnoses(self):
        failed = job(step="Coverage floor check", diagnostics={
            "Coverage floor check": {"error": "Line coverage 48.5% is below the floor 52.5%."},
        }, artifacts=[{"name": "unit-coverage-reports", "id": 1}, {"name": "instrumented-test-reports", "id": 2}])
        body = render([failed, job("CI Gate"), job("Screenshot Tests (Paparazzi)", conclusion="success"),
                       job("Instrumented Tests (Gradle Managed Device)", conclusion="success")])
        self.assertIn("Coverage floor check", body)
        self.assertIn("48.5%", body)
        self.assertIn("make coverage-check", body)
        self.assertIn("unit-coverage-reports", body)
        self.assertIn("CI Gate also failed", body)
        for unrelated in ("make test", "Instrumented", "Screenshot", "instrumented-test-reports", "No failed testcase", "### CI Gate"):
            self.assertNotIn(unrelated, body)

    def test_multiple_actual_failures_are_shown_once(self):
        body = render([job(), job("Screenshot Tests (Paparazzi)", "Verify Screenshot Tests"), job("CI Gate")], status="in_progress")
        self.assertEqual(1, body.count("### Unit Tests"))
        self.assertEqual(1, body.count("### Screenshot Tests"))
        self.assertIn("make screenshot-verify", body)
        self.assertIn("CI is still running", body)
        self.assertNotIn("Instrumented", body)
        self.assertNotIn("record_screenshots", body)

    def test_success_and_skipped_clear_all_old_failure_content(self):
        for conclusion, heading in (("success", "✅ CI passed"), ("skipped", "⏭️ CI skipped")):
            with self.subTest(conclusion=conclusion):
                body = render([job()], conclusion=conclusion)
                self.assertIn(heading, body)
                self.assertIn("failure details have been cleared", body)
                self.assertNotIn("### Unit Tests", body)
                self.assertNotIn("make test", body)

    def test_cancelled_and_timed_out_are_not_assertions(self):
        for conclusion in ("cancelled", "timed_out"):
            body = render([job(conclusion=conclusion)])
            self.assertIn(conclusion, body)
            self.assertIn("not evidence of a test assertion failure", body)
            self.assertNotIn("No failed testcase", body)

    def test_standalone_gate_failure_is_not_hidden(self):
        body = render([job("CI Gate", "Set up job")])
        self.assertIn("### CI Gate", body)
        self.assertNotIn("also failed", body)

    def test_unknown_setup_failure_does_not_invent_a_test_command(self):
        body = render([job(step="Set up JDK 23")])
        self.assertIn("Set up JDK 23", body)
        self.assertNotIn("make test", body)
        self.assertNotIn("No failed testcase", body)
        self.assertIn("https://github.test/job/1", body)

    def test_no_xml_is_not_a_claim_that_compilation_failed(self):
        body = render([job()])
        self.assertIn("No failed testcase was found", body)
        self.assertIn("missing XML alone does not establish the cause", body)
        self.assertNotIn("build likely failed", body)

    def test_expired_artifact_is_not_linked(self):
        body = render([job(artifacts=[{"name": "unit-test-reports", "id": 1, "expired": True}])])
        self.assertNotIn("unit-test-reports", body)
        self.assertIn("Open failing job", body)

    def test_carried_forward_failure_has_provenance_not_fresh_xml_claims(self):
        body = render([job(carried_forward=True, run_attempt=1)], run_attempt=2)
        self.assertIn("Unresolved result from attempt 1", body)
        self.assertNotIn("No failed testcase", body)

    def test_exact_test_identifiers_are_bounded(self):
        failures = [dict(module=":example", classname="ExampleTest", name=f"fails{i}", kind="failure", detail="expected 1", location="Example.kt:2") for i in range(10)]
        body = render([job(diagnostics={"Run Unit Tests": {"failures": failures}})])
        self.assertIn("ExampleTest#fails0", body)
        self.assertIn("Example.kt:2", body)
        self.assertIn("2 more failures", body)
        self.assertNotIn("ExampleTest#fails9", body)

    def test_late_evidence_failure_is_honest(self):
        body = render([job(diagnostics={"Run Unit Tests": {"unavailable": True, "unreadable": True}})])
        self.assertIn("could not be parsed", body)
        self.assertIn("diagnostic evidence is unavailable", body)
        self.assertNotIn("complete logs", body)

    def test_log_excerpt_prefers_coverage_or_gradle_reason(self):
        self.assertEqual("Line coverage 48.5% is below the floor 52.5%.", MODULE.error_excerpt(
            "2026-09-17T19:32:37Z ❌ Line coverage 48.5% is below the floor 52.5%.\n##[error]Process completed with exit code 2."))
        excerpt = MODULE.error_excerpt("* What went wrong:\nExecution failed for task ':example:compile'.\n> Unresolved reference: foo\n\n* Try:\n--stacktrace")
        self.assertIn("Unresolved reference", excerpt)
        self.assertNotIn("--stacktrace", excerpt)

    def test_pending_and_incomplete_are_not_success(self):
        for extra in ({}, {"incomplete": True}):
            body = render(status="in_progress", **extra)
            self.assertIn("CI running", body)
            self.assertNotIn("CI passed", body)

    def test_job_names_cannot_inject_markdown_html_or_mentions(self):
        body = render([job(name="<img>\n@someone")])
        self.assertIn("&lt;img&gt;", body)
        self.assertNotIn("@someone", body)


if __name__ == "__main__":
    unittest.main()
