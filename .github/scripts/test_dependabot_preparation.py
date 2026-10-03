#!/usr/bin/env python3
"""Exercise approvals, stale evidence, retry limits and preparation gate failures."""

from __future__ import annotations

import copy
import importlib.util
import contextlib
import io
import json
import os
import re
import subprocess
import sys
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

    def test_platform_lane_shutdown_remains_eligible_for_the_single_retry(self):
        api, reconcile, _, _, failed = self.fixture()
        failed["steps"][0]["name"] = "Resolve and record platform lane"
        self.assertTrue(MODULE.recover(api, 12, reconcile))
        api.post.assert_called_once_with("actions/runs/12/rerun")

    def test_recovery_accepts_each_known_matrix_name_and_the_legacy_name_only(self):
        names = sorted(MODULE.LANE_JOB_NAMES | {MODULE.LEGACY_LANE_JOB_NAME})
        for name in names:
            api, reconcile, _, _, failed = self.fixture()
            failed["name"] = name
            with self.subTest(name=name):
                self.assertTrue(MODULE.recover(api, 12, reconcile))
        api, reconcile, _, _, failed = self.fixture()
        failed["name"] = "Regenerate verification-metadata.xml (unknown)"
        self.assertFalse(MODULE.recover(api, 12, reconcile))
        api.post.assert_not_called()

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

    def test_review_only_publishes_coordinate_delta_without_treating_review_as_approval(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before, after = root / "before.txt", root / "after.txt"
            out, github_output = root / "review", root / "output"
            before.write_text("g:a:1\n")
            after.write_text("g:a:2\ng:b:1\n")
            with patch.object(sys, "argv", [
                str(Path(__file__).with_name("dependabot-preparation.py")),
                "--before", str(before), "--after", str(after), "--head-sha", SHA,
                "--output", str(out), "--review-only",
            ]), patch.dict(MODULE.os.environ, {"GITHUB_OUTPUT": str(github_output)}):
                self.assertEqual(0, MODULE.main())
            review = json.loads((out / "graph-review.json").read_text())
            self.assertEqual(["g:b"], review["added"])
            self.assertIn("classification=coordinate_change", github_output.read_text())

    def test_review_summary_escapes_untrusted_coordinate_markdown(self):
        review = MODULE.graph_review("", "group:artifact`[link](https://example.invalid):1\n", SHA)
        with tempfile.TemporaryDirectory() as directory:
            MODULE.write_review(review, Path(directory), "branch")
            summary = (Path(directory) / "graph-review.md").read_text()
            self.assertIn("&#96;[link](https://example.invalid)", summary)

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
        reconcile.preparation_current.return_value = {"id": 10, "run_attempt": 1, "status": "completed",
            "conclusion": "success", "authoritative_valid": True, "result": {"output_sha": SHA}}
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
            {"id": 12, "run_attempt": 1, "status": "completed", "conclusion": "success",
             "authoritative_valid": True, "result": {"output_sha": SHA}},
            {"id": 12, "run_attempt": 1, "status": "completed", "conclusion": "success",
             "authoritative_valid": True, "result": {"output_sha": SHA}},
        ]
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_called_once()
        pr["head"]["sha"] = "b" * 40
        self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))

    def test_gate_rechecks_pr_head_after_final_authoritative_selection(self):
        api, reconcile, _, pr, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        current = {"id": 22, "run_attempt": 1, "status": "completed", "conclusion": "success",
                   "authoritative_valid": True, "result": {"output_sha": SHA}}
        count = 0
        def select(_api, _pr):
            nonlocal count
            count += 1
            if count == 2:
                pr["head"]["sha"] = "b" * 40
            return dict(current)
        reconcile.preparation_current.side_effect = select
        self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 0, SHA))

    def test_gate_waits_briefly_for_successful_run_result_artifact(self):
        api, reconcile, _, _, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        missing = {"id": 44, "run_attempt": 1, "status": "completed", "conclusion": "failure",
                   "run_conclusion": "success", "category": "evidence_unavailable", "authoritative_valid": False}
        valid = {"id": 44, "run_attempt": 1, "status": "completed", "conclusion": "success",
                 "authoritative_valid": True, "result": {"output_sha": SHA}}
        reconcile.preparation_current.side_effect = [missing, valid, valid]
        now = [0.0]
        with patch.object(MODULE.time, "monotonic", side_effect=lambda: now[0]), \
             patch.object(MODULE.time, "sleep", side_effect=lambda _delay: now.__setitem__(0, now[0] + 1)):
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 3, SHA))
        self.assertEqual(3, reconcile.preparation_current.call_count)

    def test_gate_never_accepts_permanently_missing_or_invalid_result_after_grace(self):
        api, reconcile, _, _, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        invalid = {"id": 44, "run_attempt": 1, "status": "completed", "conclusion": "failure",
                   "run_conclusion": "success", "category": "evidence_unavailable", "authoritative_valid": False}
        reconcile.preparation_current.return_value = invalid
        now = [0.0]
        with patch.object(MODULE.time, "monotonic", side_effect=lambda: now[0]), \
             patch.object(MODULE.time, "sleep", side_effect=lambda _delay: now.__setitem__(0, now[0] + 1)):
            self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 2, SHA))
        self.assertEqual(2, reconcile.preparation_current.call_count)

    def test_gate_caps_missing_evidence_grace_at_60_seconds_with_long_wait_budget(self):
        api, reconcile, _, _, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        missing = {"id": 44, "run_attempt": 1, "status": "completed", "conclusion": "failure",
                   "run_conclusion": "success", "category": "evidence_unavailable", "authoritative_valid": False}
        now = [0.0]
        with patch.object(MODULE.time, "monotonic", side_effect=lambda: now[0]), \
             patch.object(MODULE.time, "sleep", side_effect=lambda delay: now.__setitem__(0, now[0] + delay)):
            reconcile.preparation_current.return_value = missing
            self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 21000, SHA))
            self.assertEqual(60, now[0])
            self.assertEqual(3, reconcile.preparation_current.call_count)

            reconcile.preparation_current.reset_mock()
            valid = {"id": 44, "run_attempt": 1, "status": "completed", "conclusion": "success",
                     "authoritative_valid": True, "result": {"output_sha": SHA}}
            reconcile.preparation_current.side_effect = [missing, valid, valid]
            now[0] = 0.0
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 21000, SHA))
            self.assertEqual(30, now[0])
            self.assertEqual(3, reconcile.preparation_current.call_count)

    def test_gate_waits_for_single_operational_retry_but_not_build_failure(self):
        api, reconcile, run, _, _ = self.fixture()
        api.pages.side_effect = None
        api.pages.return_value = [{"filename": "gradle/libs.versions.toml"}]
        reconcile.preparation_snapshot.return_value = {"category": "operational_termination"}
        good = dict(run, run_attempt=2, conclusion="success", authoritative_valid=True,
                    result={"output_sha": SHA})
        reconcile.preparation_current.side_effect = [run, good, good]
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertTrue(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_called_once()
        reconcile.preparation_current.side_effect = None
        reconcile.preparation_current.return_value = dict(run, run_attempt=2, authoritative_valid=True,
                                                          result={"output_sha": SHA})
        self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
        reconcile.preparation_current.return_value = run
        reconcile.preparation_snapshot.return_value = {"category": "build_test_or_policy_failure"}
        with patch.object(MODULE.time, "sleep") as sleep:
            self.assertFalse(MODULE.require_prepared_head(api, 7, reconcile, 60, SHA))
            sleep.assert_not_called()

    def gate_refresh_fixture(self):
        api = Mock(repository="owner/repo")
        source = {"id": 44, "path": f".github/workflows/{MODULE.WORKFLOW}", "event": "workflow_dispatch",
                  "status": "completed", "conclusion": "success", "head_sha": SHA,
                  "head_branch": "dependabot/gradle/example", "head_repository": {"full_name": "owner/repo"},
                  "display_title": "Dependency preparation | mode=write | ref=dependabot/gradle/example"}
        pr = {"number": 7, "state": "open", "user": {"login": "dependabot[bot]"},
              "head": {"sha": SHA, "ref": source["head_branch"], "repo": {"full_name": "owner/repo"}},
              "base": {"repo": {"full_name": "owner/repo"}}}
        ci = {"id": 99, "run_attempt": 1, "status": "completed", "conclusion": "failure", "head_sha": SHA}
        api.get.side_effect = lambda path: copy.deepcopy(source if path == "actions/runs/44" else pr)
        gate_jobs = [{"id": index + 10, "name": name, "conclusion": "success", "steps": []}
                     for index, name in enumerate(sorted(load("reconcile-ci-report").CI_REQUIRED_JOBS - {"CI Gate"}))]
        gate_jobs.append({"id": 5, "name": "CI Gate", "conclusion": "failure",
                          "steps": [{"name": "Verify dependency preparation result", "conclusion": "failure"}]})
        api.pages.side_effect = lambda path, key=None: ([copy.deepcopy(pr)] if path.startswith("pulls?") else
            copy.deepcopy(gate_jobs) if path.endswith("/jobs") else [])
        api.download.return_value = f"::error::{MODULE.PREPARATION_GATE_FAILURE}".encode()
        reconcile = Mock(MAX_LOG=1000)
        reconcile.CI_REQUIRED_JOBS = load("reconcile-ci-report").CI_REQUIRED_JOBS
        reconcile.RENDER.failed_steps.side_effect = lambda job: [step for step in job.get("steps", []) if step.get("conclusion") == "failure"]
        reconcile.preparation_mode.return_value = "write"
        reconcile.preparation_current.return_value = {"id": 44, "run_attempt": 1, "authoritative_valid": True,
            "result": {"output_sha": SHA}}
        reconcile.current.return_value = (copy.deepcopy(pr), copy.deepcopy(ci))
        return api, reconcile, pr, ci

    def test_gate_refresh_reruns_only_solo_preparation_failure(self):
        api, reconcile, _, _ = self.gate_refresh_fixture()
        self.assertTrue(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_called_once_with("actions/jobs/5/rerun")

    def test_gate_refresh_preserves_other_failures_and_stale_head(self):
        api, reconcile, _, _ = self.gate_refresh_fixture()
        api.pages.side_effect = lambda path, key=None: ([{"id": 5, "name": "CI Gate", "conclusion": "failure"},
            {"id": 6, "name": "Unit Tests", "conclusion": "failure"}] if path.endswith("/jobs") else [])
        self.assertFalse(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_not_called()

    def test_gate_refresh_invalidates_green_verdict_for_new_write_and_ignores_same_generation(self):
        api, reconcile, _, ci = self.gate_refresh_fixture()
        ci["conclusion"] = "success"
        names = sorted(load("reconcile-ci-report").CI_REQUIRED_JOBS - {"CI Gate"})
        green = [{"id": index + 10, "name": name, "conclusion": "success", "steps": []} for index, name in enumerate(names)]
        green.append({"id": 5, "name": "CI Gate", "conclusion": "success", "steps": []})
        api.pages.side_effect = lambda path, key=None: ([copy.deepcopy(_)] if path.startswith("pulls?") else
            copy.deepcopy(green) if path.endswith("/jobs") else [])
        reconcile.current.return_value = (copy.deepcopy(_), copy.deepcopy(ci))
        api.download.return_value = f"DEPENDENCY_PREPARATION_RESULT run_id=40 attempt=1 head={SHA}".encode()
        self.assertTrue(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_called_once_with("actions/jobs/5/rerun")
        api, reconcile, _, ci = self.gate_refresh_fixture()
        ci["conclusion"] = "success"
        api.pages.side_effect = lambda path, key=None: ([copy.deepcopy(_)] if path.startswith("pulls?") else
            copy.deepcopy(green) if path.endswith("/jobs") else [])
        reconcile.current.return_value = (copy.deepcopy(_), copy.deepcopy(ci))
        api.download.return_value = f"DEPENDENCY_PREPARATION_RESULT run_id=44 attempt=1 head={SHA}".encode()
        self.assertFalse(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_not_called()

    def test_gate_refresh_invalidates_same_marker_when_selected_evidence_is_no_longer_valid(self):
        api, reconcile, pr, ci = self.gate_refresh_fixture()
        ci["conclusion"] = "success"
        reconcile.current.return_value = (copy.deepcopy(pr), copy.deepcopy(ci))
        reconcile.preparation_current.return_value = {"id": 44, "run_attempt": 1,
            "authoritative_valid": False, "category": "evidence_unavailable"}
        names = sorted(load("reconcile-ci-report").CI_REQUIRED_JOBS - {"CI Gate"})
        green = [{"id": index + 10, "name": name, "conclusion": "success", "steps": []} for index, name in enumerate(names)]
        green.append({"id": 5, "name": "CI Gate", "conclusion": "success", "steps": []})
        api.pages.side_effect = lambda path, key=None: ([copy.deepcopy(pr)] if path.startswith("pulls?") else
            copy.deepcopy(green) if path.endswith("/jobs") else [])
        api.download.return_value = f"DEPENDENCY_PREPARATION_RESULT run_id=44 attempt=1 head={SHA}".encode()
        self.assertTrue(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_called_once_with("actions/jobs/5/rerun")

    def test_gate_refresh_ignores_experiments(self):
        api, reconcile, _, _ = self.gate_refresh_fixture()
        reconcile.preparation_mode.return_value = "candidate"
        self.assertFalse(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_not_called()

    def test_gate_refresh_rechecks_head_after_gate_log_download(self):
        api, reconcile, pr, _ = self.gate_refresh_fixture()
        def change_head(_path, _limit):
            pr["head"]["sha"] = "b" * 40
            return f"::error::{MODULE.PREPARATION_GATE_FAILURE}".encode()
        api.download.side_effect = change_head
        self.assertFalse(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_not_called()

    def test_gate_job_inventory_matches_ci_gate_needs(self):
        reconcile = load("reconcile-ci-report")
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/ci.yml").read_text()
        names = set(reconcile.re.findall(r"^\s+name: (.+)$", workflow, reconcile.re.MULTILINE))
        self.assertTrue(reconcile.CI_REQUIRED_JOBS <= names)
        api, reconcile, pr, _ = self.gate_refresh_fixture()
        pr["head"]["sha"] = "b" * 40
        self.assertFalse(MODULE.refresh_failed_ci_gate(api, 44, reconcile))
        api.post.assert_not_called()

    def test_workflow_stages_formatting_before_writer_and_retains_safety_checks(self):
        root = Path(__file__).resolve().parents[2]
        flow = (root / ".github/workflows/regen-verification-metadata.yml").read_text()
        self.assertLess(flow.index("verification-metadata-before.xml"), flow.index("make verification-metadata-format"))
        self.assertLess(flow.index("make verification-metadata-format"), flow.index("- name: Resolve and record platform lane"))
        self.assertLess(flow.index("Reject changed recorded hashes"), flow.index("Stage and commit complete generated output"))
        self.assertIn('"$(git rev-parse FETCH_HEAD)" != "$PREPARATION_HEAD"', flow)
        self.assertIn("inputs.approved_graph_digest", flow)
        self.assertIn("cache-disabled:", flow)
        self.assertIn("max-parallel: 1", flow)
        self.assertIn("name: Regenerate verification-metadata.xml (${{ matrix.lane }})", flow)
        self.assertIn("GRADLE_RUNNER: ${{ matrix.gradle_runner || './gradlew' }}", flow)
        self.assertEqual(2, flow.count("./gradlew --max-workers=2"))
        self.assertEqual(2, flow.count("-Xmx4g -XX:MaxMetaspaceSize=768m"))
        self.assertIn("/tmp/unit-kotlin-gc-*.log", flow)
        self.assertIn("dependency-preparation-resources-", flow)
        self.assertIn("for report in graph-review.json graph-review.md; do", flow)
        self.assertIn('"$RUNNER_TEMP/dependency-preparation/$report" "$destination/$report"', flow)
        self.assertEqual(1, flow.count("secrets.VERIFICATION_METADATA_DEPLOY_KEY"))
        for lane in ("linux", "web", "managed", "apple"):
            self.assertIn(f"lane: {lane}", flow)
        self.assertIn("needs: lanes", flow)
        self.assertIn("PREPARATION_HEAD", flow)
        self.assertIn("cmp .platform-evidence/linux/prepared.patch", flow)
        self.assertIn("stage-dependabot-preparation.py", flow)
        self.assertNotIn("git add --", flow)
        caller = (root / ".github/workflows/verification-metadata-compare.yml").read_text()
        self.assertIn("  actions: read", caller)
        self.assertIn("  pull-requests: read", caller)
        self.assertNotIn("contents: write", caller)
        self.assertIn("if [ -s .platform-evidence/linux/prepared.patch ]; then", flow)
        self.assertNotIn("VERIFICATION_METADATA_DEPLOY_KEY: ${{ secrets.", flow)
        writer_mode = "(inputs.mode || 'write') == 'write'"
        self.assertIn(f"ssh-key: ${{{{ {writer_mode} && secrets.VERIFICATION_METADATA_DEPLOY_KEY || '' }}}}", flow)
        self.assertIn(f"persist-credentials: ${{{{ {writer_mode} }}}}", flow)
        self.assertIn(f"if: {writer_mode} && steps.reuse.outputs.reuse != 'true'\n        run: make update-docs", flow)
        self.assertIn(f"if: success() && {writer_mode}", flow)
        self.assertNotIn("github.event_name == 'pull_request') && secrets.VERIFICATION_METADATA_DEPLOY_KEY", flow)
        self.assertNotIn("pull_request_target", flow.split("on:", 1)[1])
        ci = (root / ".github/workflows/ci.yml").read_text()
        timeout = int(re.search(r"ci-gate:\n    name: CI Gate\n    timeout-minutes: (\d+)", ci).group(1))
        wait = int(re.search(r"--wait-seconds (\d+)", ci).group(1))
        self.assertLess(wait, timeout * 60)
        self.assertLessEqual(timeout, 360)
        self.assertEqual(360, timeout)
        self.assertIn("-${{ steps.mode.outputs.graph }}-${{ matrix.lane }}", flow)
        self.assertIn("${{ inputs.mode == 'candidate' && 'candidate' || 'reference' }}-linux", flow)
        self.assertIn("steps.mode.outputs.value == 'write'", flow)
        comparison = (root / ".github/workflows/verification-metadata-compare.yml").read_text()
        self.assertIn("needs: reference", comparison)
        self.assertIn("mode: candidate", comparison)
        self.assertIn("--require-equivalent", comparison)
        self.assertNotIn("secrets: inherit", comparison)
        makefile = (root / "Makefile").read_text()
        self.assertIn("verification-metadata-lane", makefile)
        self.assertIn("$(kmp_test_tasks)", makefile)
        self.assertIn("$(KMP_BROWSER_TEST_MODULES)", makefile)
        self.assertIn(":web-app:wasmJsBrowserDistribution", makefile)
        self.assertIn("ios-test", makefile)
        self.assertIn("ciGroupDebugAndroidTest", makefile)

    def test_empty_prepared_patch_is_a_behavioral_no_op(self):
        root = Path(__file__).resolve().parents[2]
        flow = (root / ".github/workflows/regen-verification-metadata.yml").read_text()
        block = re.search(
            r"(?m)^          if \[ -s \.platform-evidence/linux/prepared\.patch \]; then\n"
            r"(?P<body>.*?)^          fi$",
            flow,
            re.DOTALL,
        )
        self.assertIsNotNone(block)
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            patch = folder / "prepared.patch"
            marker = folder / "git-called"
            fake_git = folder / "git"
            fake_git.write_text(f"#!/bin/sh\nprintf called > '{marker}'\n")
            fake_git.chmod(0o755)
            command = ("if [ -s \"$PATCH_FILE\" ]; then\n"
                       + block.group("body").replace(
                           ".platform-evidence/linux/prepared.patch", "$PATCH_FILE"
                       ) + "\nfi")
            environment = {**os.environ, "PATH": f"{folder}:{os.environ['PATH']}", "PATCH_FILE": str(patch)}
            patch.write_bytes(b"")
            subprocess.run(["bash", "-c", command], env=environment, check=True)
            self.assertFalse(marker.exists())
            patch.write_text("diff --git a/a b/a\n")
            subprocess.run(["bash", "-c", command], env=environment, check=True)
            self.assertTrue(marker.exists())

    def test_actual_lane_dry_runs_cover_full_test_inventory_and_platform_tasks(self):
        root = Path(__file__).resolve().parents[2]

        def make_output(*arguments: str, recursive_environment: bool = False) -> str:
            environment = {
                key: value for key, value in os.environ.items()
                if key not in {"MAKEFLAGS", "MFLAGS", "MAKELEVEL", "MAKEOVERRIDES"}
            }
            if recursive_environment:
                environment.update(MAKEFLAGS="w", MAKELEVEL="1")
            return subprocess.run(
                ["make", "--no-print-directory", *arguments],
                cwd=root, env=environment, check=True, capture_output=True, text=True,
            ).stdout

        def make_dry_run(target: str, *, recursive_environment: bool = False) -> str:
            return make_output(
                "-n", "verification-metadata-lane", f"LANE={target}",
                "MODE=reference", "GRADLE_RUNNER=./gradlew",
                recursive_environment=recursive_environment,
            )

        def gradle_commands(output: str) -> list[str]:
            commands = []
            current = []
            for line in output.splitlines():
                if not current:
                    if "./gradlew" not in line:
                        continue
                    current.append(line.rstrip().removesuffix("\\").rstrip())
                else:
                    current.append(line.rstrip().removesuffix("\\").rstrip())
                if not line.rstrip().endswith("\\"):
                    commands.append(" ".join(current))
                    current = []
            if current:
                commands.append(" ".join(current))
            return commands

        test_commands = gradle_commands(make_output("-n", "test", "GRADLE_RUNNER=./gradlew"))
        self.assertTrue(test_commands, "make -n test did not print a Gradle command")
        test_plan = test_commands[0]
        test_tasks = set(re.findall(
            r"(?<![\w-])(:[\w:-]+:(?:jvmTest|allMetadataJar|testAndroidHostTest|test|wasmJsBrowserTest)|testDebugUnitTest)",
            test_plan,
        ))
        browser_tasks = {task for task in test_tasks if task.endswith(":wasmJsBrowserTest")}
        linux_tasks = make_dry_run("linux")
        web_tasks = make_dry_run("web")
        for task in test_tasks - browser_tasks:
            self.assertIn(task, linux_tasks, f"Linux metadata lane omits {task}")
        for task in browser_tasks:
            self.assertIn(task, web_tasks, f"Web metadata lane omits {task}")
        for task in ("assembleDebug", "verifyPaparazziDebug"):
            self.assertIn(task, linux_tasks)
        self.assertIn(":web-app:wasmJsBrowserDistribution", web_tasks)

        managed_output = make_dry_run("managed", recursive_environment=True)
        self.assertNotIn("Entering directory", managed_output)
        managed_commands = gradle_commands(managed_output)
        self.assertTrue(managed_commands, "managed lane dry run did not print a Gradle command")
        debug = next(command for command in managed_commands if "ciGroupDebugAndroidTest" in command)
        self.assertNotIn("releaseSmoke", debug)
        managed_plan = "\n".join(managed_commands)
        self.assertIn("-PappTestBuildType=releaseSmoke", managed_plan)
        self.assertIn("-Pandroid.testInstrumentationRunnerArguments.class=com.simtop.billionbeers.ReleaseConfidenceSmokeTest",
                      managed_plan)

        apple = make_dry_run("apple")
        for task in (":ios-shared:compileKotlinIosArm64", ":ios-shared:compileKotlinIosSimulatorArm64",
                     ":ios-shared:linkDebugFrameworkIosSimulatorArm64", ":ios-shared:linkDebugFrameworkIosArm64",
                     ":ios-shared:iosSimulatorArm64Test"):
            self.assertIn(task, apple)


if __name__ == "__main__":
    unittest.main()
