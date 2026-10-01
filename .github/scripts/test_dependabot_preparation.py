#!/usr/bin/env python3
"""Exercise approvals, stale evidence, retry limits and preparation gate failures."""

from __future__ import annotations

import copy
import importlib.util
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock
from unittest.mock import patch


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


MODULE = load("dependabot-preparation")
STAGE = load("stage-dependabot-preparation")
SHA = "a" * 40
SHUTDOWN = "##[error]The runner has received a shutdown signal.\n##[error]Process completed with exit code 143.\n"


class DependabotPreparationTest(unittest.TestCase):
    def setUp(self):
        # Expected rejection tests must not emit real workflow error annotations.
        capture = contextlib.ExitStack()
        capture.enter_context(contextlib.redirect_stdout(io.StringIO()))
        capture.enter_context(contextlib.redirect_stderr(io.StringIO()))
        self.addCleanup(capture.close)

    def fixture(self):
        api = Mock(repository="owner/repo")
        upstream = dict(id=12, run_number=12, run_attempt=1, event="pull_request", status="completed",
                        conclusion="failure", path=f".github/workflows/{MODULE.WORKFLOW}", head_sha=SHA,
                        head_branch="dependabot/gradle/example", head_repository={"full_name": "owner/repo"})
        pr = dict(number=7, state="open", user={"login": "dependabot[bot]"},
                  head={"sha": SHA, "ref": upstream["head_branch"], "repo": {"full_name": "owner/repo"}},
                  base={"repo": {"full_name": "owner/repo"}})
        failed = dict(id=99, name="Regenerate verification-metadata.xml", conclusion="failure",
                      steps=[{"name": "Regenerate verification metadata", "conclusion": "failure"}])
        states = {"actions/runs/12": upstream, "pulls/7": pr}
        api.get.side_effect = lambda path: copy.deepcopy(states[path])
        api.pages.side_effect = lambda path, key=None: [copy.deepcopy(failed)] if path.endswith("/jobs") else [copy.deepcopy(upstream)]
        api.download.return_value = SHUTDOWN.encode()
        reconcile = Mock(MAX_LOG=10000)
        reconcile.resolve_pr.return_value = 7
        reconcile.RENDER.failed_steps.side_effect = lambda job: [step for step in job["steps"] if step["conclusion"] == "failure"]
        return api, reconcile, upstream, pr, failed

    def test_current_shutdown_retries_once_and_never_checks_out_source(self):
        api, reconcile, _, _, _ = self.fixture()
        self.assertTrue(MODULE.recover(api, 12, reconcile))
        api.post.assert_called_once_with("actions/runs/12/rerun")

    def test_second_attempt_closed_pr_fork_or_human_never_retries(self):
        for variation in ("attempt", "closed", "fork", "run_fork", "human", "dispatch", "cancelled"):
            api, reconcile, run, pr, _ = self.fixture()
            if variation == "attempt": run["run_attempt"] = 2
            if variation == "closed": pr["state"] = "closed"
            if variation == "fork": pr["head"]["repo"]["full_name"] = "fork/repo"
            if variation == "run_fork": run["head_repository"]["full_name"] = "fork/repo"
            if variation == "human": pr["user"]["login"] = "human"
            if variation == "dispatch": run["event"] = "workflow_dispatch"
            if variation == "cancelled": run["conclusion"] = "cancelled"
            with self.subTest(variation=variation):
                self.assertFalse(MODULE.recover(api, 12, reconcile))
                api.post.assert_not_called()

    def test_rebase_during_log_download_invalidates_recovery(self):
        api, reconcile, _, pr, _ = self.fixture()
        def download(*args):
            pr["head"]["sha"] = "b" * 40
            return SHUTDOWN.encode()
        api.download.side_effect = download
        self.assertFalse(MODULE.recover(api, 12, reconcile))
        api.post.assert_not_called()

    def test_newer_run_or_retry_during_download_invalidates_recovery(self):
        for variation in ("newer", "retry"):
            api, reconcile, run, _, _ = self.fixture()
            if variation == "newer":
                original = api.pages.side_effect
                api.pages.side_effect = lambda path, key=None: original(path, key) if path.endswith("/jobs") else [dict(run, run_number=13)]
            else:
                def download(*args):
                    run["run_attempt"] = 2
                    return SHUTDOWN.encode()
                api.download.side_effect = download
            with self.subTest(variation=variation):
                self.assertFalse(MODULE.recover(api, 12, reconcile))
                api.post.assert_not_called()

    def test_policy_formatting_and_build_errors_do_not_retry_even_with_shutdown(self):
        for reason in ("Regeneration changed checksums", "Dependency-verification configuration changed",
                       "##[error]This bump adds or removes dependency coordinates", "> Task :module:compile FAILED",
                       "The following files had format violations", "Component has conflicting requirements",
                       "Dependency verification failed"):
            api, reconcile, _, _, _ = self.fixture()
            api.download.return_value = (reason + "\n" + SHUTDOWN).encode()
            with self.subTest(reason=reason):
                self.assertFalse(MODULE.recover(api, 12, reconcile))
                api.post.assert_not_called()

    def test_echoed_retry_classification_is_not_shutdown_evidence(self):
        log = "2026-10-01T03:00:00Z ##[group]Run status=143\n" + SHUTDOWN + "##[endgroup]\n* What went wrong:\nBuild error"
        self.assertNotEqual("operational_termination", MODULE.classify(log, "Regenerate verification metadata"))
        self.assertNotEqual("operational_termination", MODULE.classify("##[error]Process completed with exit code 143.", "Regenerate verification metadata"))

    def test_coordinate_step_shutdown_is_not_a_regeneration_retry(self):
        api, reconcile, _, _, failed = self.fixture()
        failed["steps"][0]["name"] = "Review dependency coordinate changes"
        self.assertFalse(MODULE.recover(api, 12, reconcile))

    def test_approval_binds_head_baseline_versions_and_exact_delta(self):
        review = MODULE.graph_review("a:lib:1\n", "a:lib:2\nb:lib:1\n", SHA)
        self.assertEqual("coordinate_change", MODULE.check_graph(review, "", ""))
        self.assertEqual("reviewed_coordinate_change", MODULE.check_graph(review, SHA, review["digest"]))
        for altered in (MODULE.graph_review("a:lib:1\n", "a:lib:2\nb:lib:2\n", SHA),
                        MODULE.graph_review("a:lib:0\n", "a:lib:2\nb:lib:1\n", SHA),
                        MODULE.graph_review("a:lib:1\n", "a:lib:2\nb:lib:1\n", "b" * 40)):
            with self.assertRaises(ValueError):
                MODULE.check_graph(altered, SHA, review["digest"])
        with self.assertRaises(ValueError):
            MODULE.check_graph(review, SHA, "")

    def test_version_only_drift_is_automatic_but_additions_and_removals_require_review(self):
        review = MODULE.graph_review("a:lib:1\n", "a:lib:2\n", SHA)
        self.assertEqual("version_only", MODULE.check_graph(review, "", ""))
        review = MODULE.graph_review("a:lib:1\nb:lib:1\n", "a:lib:2\n", SHA)
        self.assertEqual(["b:lib"], review["removed"])
        self.assertEqual("coordinate_change", MODULE.check_graph(review, "", ""))

    def test_review_contains_exact_full_graph_and_is_order_independent(self):
        a = MODULE.graph_review("a:l:1\nb:l:1\n", "a:l:2\nb:l:1\n", SHA)
        b = MODULE.graph_review("b:l:1\na:l:1\n", "b:l:1\na:l:2\n", SHA)
        self.assertEqual(a["digest"], b["digest"])
        with tempfile.TemporaryDirectory() as directory:
            MODULE.write_review(a, Path(directory), "branch")
            self.assertIn('"a:l:2"', (Path(directory) / "graph-review.json").read_text())

    def test_stage_rejects_unrelated_tracked_changes(self):
        self.assertEqual(["module/Foo.kt", "README.md"], STAGE.allowed_files(b"module/Foo.kt\0", b"module/Foo.kt\0README.md\0"))
        for recorded, changed in ((b"", b".github/workflows/ci.yml\0"), (b"secrets.txt\0", b"secrets.txt\0")):
            with self.assertRaises(ValueError):
                STAGE.allowed_files(recorded, changed)

    def test_ci_gate_requires_preparation_for_gradle_but_not_actions_only(self):
        api, reconcile, _, _, _ = self.fixture()
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        api.pages.side_effect = None
        for result in (None, {"status": "in_progress", "conclusion": None}, {"status": "completed", "conclusion": "failure"}):
            reconcile.preparation_current.return_value = result
            self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile))
        reconcile.preparation_current.return_value = {"status": "completed", "conclusion": "success"}
        self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile))
        api.pages.return_value = [{"filename": ".github/workflows/ci.yml"}]
        reconcile.preparation_current.return_value = None
        self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile))

    def test_gate_waits_for_same_head_and_rejects_rebased_head(self):
        api, reconcile, _, pr, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        reconcile.preparation_current.side_effect = [
            {"status": "in_progress", "conclusion": None},
            {"status": "completed", "conclusion": "success"},
        ]
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_called_once()
        pr["head"]["sha"] = "b" * 40
        self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))

    def test_gate_waits_for_single_operational_retry_but_not_build_failure(self):
        api, reconcile, run, _, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        reconcile.preparation_snapshot.return_value = {"category": "operational_termination"}
        reconcile.preparation_current.side_effect = [run, dict(run, run_attempt=2, conclusion="success")]
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_called_once()
        reconcile.preparation_current.side_effect = None
        reconcile.preparation_current.return_value = dict(run, run_attempt=2)
        self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
        reconcile.preparation_current.return_value = run
        reconcile.preparation_snapshot.return_value = {"category": "build_test_or_policy_failure"}
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_not_called()

    def test_workflow_stages_formatting_before_writer_and_retains_safety_checks(self):
        root = Path(__file__).resolve().parents[2]
        flow = (root / ".github/workflows/regen-verification-metadata.yml").read_text()
        self.assertLess(flow.index("verification-metadata-before.xml"), flow.index("make verification-metadata-format"))
        self.assertLess(flow.index("make verification-metadata-format"), flow.index("- name: Regenerate verification metadata"))
        self.assertLess(flow.index("Reject changed checksums"), flow.index("Commit and push"))
        self.assertIn('"$(git rev-parse FETCH_HEAD)" != "$(git rev-parse HEAD)"', flow)
        self.assertIn("inputs.approved_graph_digest", flow)
        self.assertIn("cache-disabled:", flow)
        self.assertNotIn("git add -A", flow)
        self.assertNotIn("pull_request_target", flow.split("on:", 1)[1])
        comparison = (root / ".github/workflows/verification-metadata-compare.yml").read_text()
        self.assertIn("mode: [reference, candidate]", comparison)
        self.assertIn("--require-equivalent", comparison)
        self.assertNotIn("secrets: inherit", comparison)


if __name__ == "__main__":
    unittest.main()
