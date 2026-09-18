#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import io
import stat
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).with_name("reconcile-ci-report.py")
SPEC = importlib.util.spec_from_file_location("reconcile_ci_report", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def pr(sha="abc", repository_id=1):
    return {"number": 7, "state": "open", "html_url": "https://github.test/pr/7",
            "head": {"sha": sha, "ref": "feature/test", "repo": {"id": repository_id}},
            "base": {"repo": {"full_name": "owner/repo"}}}


def run(attempt=1, status="in_progress", conclusion=None, sha="abc", number=1):
    return {"id": number, "run_number": number, "run_attempt": attempt, "status": status,
            "conclusion": conclusion, "head_sha": sha, "head_branch": "feature/test",
            "head_repository": {"id": 1, "owner": {"login": "owner"}}, "event": "pull_request",
            "path": ".github/workflows/ci.yml", "pull_requests": [{"number": 7}],
            "html_url": f"https://github.test/run/{number}"}


def job(name="Unit Tests", step="Run Unit Tests", attempt=1, identifier=10, conclusion="failure"):
    return {"id": identifier, "name": name, "run_attempt": attempt, "status": "completed",
            "conclusion": conclusion, "html_url": f"https://github.test/job/{identifier}",
            "started_at": f"2026-09-17T12:0{attempt}:00Z", "completed_at": f"2026-09-17T12:0{attempt}:59Z",
            "steps": [{"name": step, "conclusion": conclusion, "started_at": f"2026-09-17T12:0{attempt}:00Z",
                       "completed_at": f"2026-09-17T12:0{attempt}:58Z"}]}


def artifact(name="unit-test-reports", attempt=1, identifier=100):
    return {"id": identifier, "name": name, "created_at": f"2026-09-17T12:0{attempt}:59Z", "expired": False}


def archive(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as result:
        for name, content in entries:
            result.writestr(name, content)
    return output.getvalue()


class FakeApi:
    token = "token"
    repository = "owner/repo"

    def __init__(self, states):
        self.states = states
        self.index = 0
        self.time = 0
        self.downloads = []
        self.on_download = None

    @property
    def state(self):
        return self.states[self.index]

    def get(self, path):
        if path == "pulls/7":
            return copy.deepcopy(self.state.get("pr", pr()))
        if path.startswith("actions/runs/"):
            return copy.deepcopy(self.state["run"])
        raise AssertionError(path)

    def pages(self, path, key=None):
        if path.startswith("actions/workflows/"):
            return copy.deepcopy(self.state.get("runs", [self.state["run"]] if self.state.get("run") else []))
        if "/attempts/" in path:
            if self.state.get("jobs_error"):
                raise OSError("metadata unavailable")
            return copy.deepcopy(self.state.get("jobs", []))
        if "filter=all" in path:
            return copy.deepcopy(self.state.get("history", []))
        if path.endswith("/artifacts"):
            if self.state.get("artifacts_error"):
                raise OSError("artifact API unavailable")
            return copy.deepcopy(self.state.get("artifacts", []))
        raise AssertionError(path)

    def download(self, path, limit):
        self.downloads.append(path)
        content = self.state.get("downloads", {}).get(path, b"")
        if self.on_download:
            callback, self.on_download = self.on_download, None
            callback()
        if isinstance(content, Exception):
            raise content
        return content

    def clock(self):
        return self.time

    def sleep(self, seconds):
        self.time += seconds
        self.index = min(self.index + 1, len(self.states) - 1)


class FakePublisher:
    def __init__(self, initial=None):
        self.body = initial
        self.writes = []
        self.creations = 0

    def __call__(self, token, repository, pr_number, marker, body, *, create, is_current, reset=False):
        if reset and self.body:
            state = next((line for line in body.splitlines() if line.startswith("<!-- billionbeers-ci:state ")), None)
            if state and state in self.body.splitlines():
                return "https://github.test/comment/1"
        if not is_current() or (not create and self.body is None):
            return None
        if self.body is None:
            self.creations += 1
        if self.body != body:
            self.writes.append(body)
        self.body = body
        return "https://github.test/comment/1"


def watch(states, initial=None, budget=20):
    api = FakeApi(states)
    publisher = FakePublisher(initial)
    MODULE.watch(api, 7, budget=budget, interval=1, clock=api.clock, sleep=api.sleep, publisher=publisher)
    return api, publisher


class ReconcileCiReportTest(unittest.TestCase):
    def setUp(self):
        # Simulated outages must not create real GitHub warning annotations in the test job.
        warnings = patch.object(MODULE, "warning")
        self.warnings = warnings.start()
        self.addCleanup(warnings.stop)

    def test_two_failures_then_rerun_then_success_reuse_one_comment(self):
        unit = job()
        screenshot = job("Screenshot Tests (Paparazzi)", "Verify Screenshot Tests", identifier=20)
        second_screenshot = job("Screenshot Tests (Paparazzi)", "Verify Screenshot Tests", attempt=2, identifier=21)
        _, publisher = watch([
            {"run": run(), "jobs": [unit]},
            {"run": run(), "jobs": [unit, screenshot]},
            {"run": run(attempt=2)},
            {"run": run(attempt=2), "jobs": [second_screenshot]},
            {"run": run(attempt=2, status="completed", conclusion="success")},
        ])
        self.assertEqual(1, publisher.creations)
        self.assertIn("### Unit Tests", publisher.writes[0])
        self.assertNotIn("### Screenshot", publisher.writes[0])
        both = next(body for body in publisher.writes if "### Unit Tests" in body and "### Screenshot" in body)
        self.assertEqual(1, both.count("### Unit Tests"))
        second = [body for body in publisher.writes if '"attempt": 2' in body]
        self.assertTrue(second)
        self.assertTrue(all("### Unit Tests" not in body for body in second))
        self.assertTrue(any("### Screenshot" in body for body in second))
        self.assertIn("CI passed", publisher.body)
        self.assertNotIn("### Screenshot", publisher.body)

    def test_clean_run_never_creates_a_success_or_pending_comment(self):
        _, publisher = watch([{"run": run()}, {"run": run(status="completed", conclusion="success")}])
        self.assertEqual([], publisher.writes)
        self.assertEqual(0, publisher.creations)

    def test_new_commit_clears_old_failures_before_its_run_exists(self):
        _, publisher = watch([
            {"run": run(), "jobs": [job()]},
            {"pr": pr("def"), "run": None},
            {"pr": pr("def"), "run": run(sha="def", number=2, status="completed", conclusion="success")},
        ])
        pending = next(body for body in publisher.writes if "Awaiting CI" in body)
        self.assertNotIn("### Unit Tests", pending)
        self.assertIn('"sha": "def"', pending)
        self.assertIn("CI passed", publisher.body)

    def test_old_same_sha_rerun_cannot_supersede_newer_original_run(self):
        older = run(attempt=9, number=1)
        newer = run(number=2)
        api = FakeApi([{"run": newer, "runs": [older, newer]}])
        _, actual = MODULE.current(api, 7)
        self.assertEqual(2, actual["id"])

    def test_delayed_completion_reconciles_current_run_not_event_snapshot(self):
        api = FakeApi([{"run": run(number=2, status="completed", conclusion="success")}])
        number = MODULE.resolve_pr(api, run(number=1))
        self.assertEqual(7, number)
        publisher = FakePublisher("old failure")
        MODULE.watch(api, number, budget=5, interval=1, clock=api.clock, sleep=api.sleep, publisher=publisher)
        self.assertIn("run/2", publisher.body)
        self.assertIn("CI passed", publisher.body)

    def test_generation_change_during_download_discards_old_failure(self):
        api = FakeApi([
            {"run": run(), "jobs": [job()]},
            {"pr": pr("def"), "run": run(sha="def", number=2, status="completed", conclusion="success")},
        ])
        api.on_download = lambda: setattr(api, "index", 1)
        publisher = FakePublisher("old failure")
        MODULE.watch(api, 7, budget=5, interval=1, clock=api.clock, sleep=api.sleep, publisher=publisher)
        self.assertTrue(all("### Unit Tests" not in body for body in publisher.writes))
        self.assertIn("CI passed", publisher.body)

    def test_partial_rerun_keeps_only_genuinely_unresolved_older_jobs(self):
        old_unit = job()
        old_screen = job("Screenshot Tests (Paparazzi)", "Verify Screenshot Tests", identifier=20)
        new_unit = job(attempt=2, identifier=11, conclusion="success")
        jobs = MODULE.effective_jobs([new_unit], [old_unit, old_screen, new_unit], 2)
        self.assertEqual(2, len(jobs))
        unit = next(item for item in jobs if item["name"] == "Unit Tests")
        self.assertEqual("success", unit["conclusion"])
        self.assertFalse(unit["carried_forward"])
        _, publisher = watch([{"run": run(attempt=2, status="completed", conclusion="failure"),
                              "jobs": [new_unit], "history": [old_unit, old_screen]}])
        self.assertNotIn("### Unit Tests", publisher.body)
        self.assertIn("Unresolved result from attempt 1", publisher.body)
        self.assertNotIn("No failed testcase", publisher.body)

    def test_artifacts_must_belong_to_the_producer_execution(self):
        producer = job(attempt=2)
        self.assertFalse(MODULE.artifact_for_job(artifact(attempt=1), producer))
        self.assertTrue(MODULE.artifact_for_job(artifact(attempt=2), producer))
        self.assertFalse(MODULE.artifact_for_job(dict(artifact(attempt=2), expired=True), producer))
        self.assertFalse(MODULE.artifact_for_job(artifact(attempt=2), dict(producer, carried_forward=True)))
        self.assertFalse(MODULE.artifact_for_job(dict(artifact(), created_at="invalid"), producer))
        self.assertFalse(MODULE.artifact_for_job(artifact(attempt=2), dict(producer, completed_at=None)))

    def test_missing_or_delayed_report_does_not_hide_failure_and_downloads_are_cached(self):
        xml = '<testsuite><testcase classname="Example" name="failed"><failure>expected 1</failure></testcase></testsuite>'
        content = archive([("core/build/test-results/test/TEST-core.xml", xml)])
        states = [
            {"run": run(), "jobs": [job()]},
            {"run": run(), "jobs": [job()], "artifacts": [artifact()],
             "downloads": {"actions/artifacts/100/zip": content}},
            {"run": run(status="completed", conclusion="failure"), "jobs": [job()], "artifacts": [artifact()],
             "downloads": {"actions/artifacts/100/zip": content}},
        ]
        api, publisher = watch(states)
        self.assertIn("### Unit Tests", publisher.writes[0])
        self.assertNotIn("Example#failed", publisher.writes[0])
        self.assertIn("Example#failed", publisher.body)
        self.assertEqual(1, api.downloads.count("actions/artifacts/100/zip"))
        self.assertEqual(1, api.downloads.count("actions/jobs/10/logs"))

    def test_coverage_only_does_not_download_unrelated_reports(self):
        coverage = job(step="Coverage floor check")
        api, publisher = watch([{"run": run(status="completed", conclusion="failure"), "jobs": [coverage],
                                 "artifacts": [artifact(), artifact("unit-coverage-reports", identifier=101)],
                                 "downloads": {"actions/jobs/10/logs": b"2026-09-17T12:01:30Z Line coverage 48.5% is below the floor 52.5%."}}])
        self.assertEqual(["actions/jobs/10/logs"], api.downloads)
        self.assertIn("48.5%", publisher.body)
        self.assertIn("unit-coverage-reports", publisher.body)
        self.assertNotIn("unit-test-reports", publisher.body)

    def test_api_outage_never_fabricates_success_or_old_generation_results(self):
        _, publisher = watch([
            {"run": run(), "jobs": [job()]},
            {"run": run(attempt=2), "jobs_error": True},
        ], budget=3)
        self.assertIn('"attempt": 2', publisher.body)
        self.assertNotIn("### Unit Tests", publisher.body)
        self.assertNotIn("CI passed", publisher.body)

    def test_artifact_api_failure_preserves_known_job_failure(self):
        _, publisher = watch([{"run": run(status="completed", conclusion="failure"), "jobs": [job()], "artifacts_error": True}])
        self.assertIn("### Unit Tests", publisher.body)
        self.assertIn("evidence is incomplete", publisher.body)

    def test_deadline_is_bounded_and_completion_event_can_finish(self):
        api, publisher = watch([{"run": run(), "jobs": [job()]}], budget=2)
        self.assertEqual(2, api.time)
        self.assertIn("Live reporting paused", publisher.body)
        api = FakeApi([{"run": run(status="completed", conclusion="success")}])
        MODULE.watch(api, 7, budget=5, interval=1, clock=api.clock, sleep=api.sleep, publisher=publisher)
        self.assertIn("CI passed", publisher.body)
        self.assertNotIn("Live reporting paused", publisher.body)
        self.assertEqual(1, publisher.creations)

    def test_unchanged_snapshot_skips_comment_lookup_and_publication(self):
        api = FakeApi([{"run": run(), "jobs": [job()]}])
        actual = FakePublisher()
        publisher = Mock(side_effect=actual)
        MODULE.watch(api, 7, budget=4, interval=1, clock=api.clock, sleep=api.sleep, publisher=publisher)
        # Initial update-only reset, first failure, and deadline note; not every poll.
        self.assertEqual(3, publisher.call_count)
        self.assertEqual(1, actual.creations)

    def test_closed_pr_does_not_publish(self):
        _, publisher = watch([{"pr": dict(pr(), state="closed"), "run": run()}], initial="old")
        self.assertEqual([], publisher.writes)

    def test_missing_fork_association_uses_api_and_verifies_source_repository(self):
        api = Mock(repository="owner/repo")
        upstream = run()
        upstream["pull_requests"] = []
        api.pages.return_value = [{"number": 7}, {"number": 8}]
        api.get.side_effect = [pr(), dict(pr(repository_id=999), number=8)]
        self.assertEqual(7, MODULE.resolve_pr(api, upstream))
        api.pages.assert_called_once_with("commits/abc/pulls")

    def test_fork_commit_lookup_failure_falls_back_to_verified_open_pr(self):
        api = Mock(repository="owner/repo")
        upstream = dict(run(), pull_requests=[])
        api.pages.side_effect = [OSError("fork commit unavailable"), [{"number": 7}]]
        api.get.return_value = pr()
        self.assertEqual(7, MODULE.resolve_pr(api, upstream))
        self.assertIn("head=owner%3Afeature%2Ftest", api.pages.call_args.args[0])

    def test_ambiguous_pr_association_or_non_pr_workflow_is_skipped(self):
        api = Mock(repository="owner/repo")
        upstream = run()
        upstream["pull_requests"] = [{"number": 7}, {"number": 8}]
        api.get.side_effect = [pr(), dict(pr(), number=8)]
        self.assertIsNone(MODULE.resolve_pr(api, upstream))
        self.assertIsNone(MODULE.resolve_pr(api, dict(run(), event="push")))

    def test_pagination_reads_all_jobs(self):
        api = MODULE.GitHub("token", "owner/repo")
        with patch.object(api, "get", side_effect=[{"jobs": [{"id": 1}] * 100}, {"jobs": [{"id": 2}]}]) as get:
            self.assertEqual(101, len(api.pages("actions/runs/1/jobs?filter=all", "jobs")))
            self.assertIn("&per_page=100&page=2", get.call_args.args[0])

    def test_step_log_does_not_attribute_another_steps_error(self):
        logs = "2026-09-17T12:00:01Z ##[error]wrong step\n2026-09-17T12:01:30Z ##[error]right step\n"
        self.assertEqual("right step", MODULE.RENDER.error_excerpt(MODULE.step_log(logs, job()["steps"][0])))

    def test_fractional_log_timestamp_in_last_step_second_is_included(self):
        log = "2026-09-17T12:01:58.0848565Z ❌ Line coverage 48.5% is below the floor 52.5%."
        self.assertEqual("Line coverage 48.5% is below the floor 52.5%.",
                         MODULE.RENDER.error_excerpt(MODULE.step_log(log, job()["steps"][0])))

    def test_rerun_never_downloads_same_name_old_artifact(self):
        api = FakeApi([{"run": run(attempt=2), "jobs": [job(attempt=2)]}])
        evidence = MODULE.Evidence(api)
        jobs = evidence.enrich(run(attempt=2), [job(attempt=2)], [artifact(attempt=1)])
        self.assertEqual([], jobs[0]["artifacts"])
        self.assertFalse(any("/artifacts/" in path for path in api.downloads))

    def test_evidence_redirect_drops_authorization_and_enforces_size_limit(self):
        api = MODULE.GitHub("secret-token", "owner/repo")
        redirect = MODULE.urllib.error.HTTPError("https://api.github.com", 302, "Found",
                                                {"Location": "https://storage.test/signed"}, None)
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b"ok"
        with patch.object(MODULE.urllib.request, "build_opener") as opener, patch.object(MODULE.urllib.request, "urlopen", return_value=response) as fetch:
            opener.return_value.open.side_effect = redirect
            self.assertEqual(b"ok", api.download("actions/jobs/1/logs", 10))
            fetch.assert_called_once_with("https://storage.test/signed", timeout=20)
            response.read.assert_called_once_with(11)
            response.read.return_value = b"x" * 11
            with self.assertRaises(ValueError):
                api.download("actions/jobs/1/logs", 10)

    def test_archive_rejects_traversal_symlinks_and_oversize(self):
        for name in ("../TEST-escape.xml", "/TEST-escape.xml", "..\\TEST-escape.xml"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                MODULE.parse_archive(archive([(name, "data")]), "unit")
        link = zipfile.ZipInfo("TEST-link.xml")
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaises(ValueError):
            MODULE.parse_archive(archive([(link, "target")]), "unit")
        with patch.object(MODULE, "MAX_XML", 1), self.assertRaises(ValueError):
            MODULE.parse_archive(archive([("core/build/test-results/test/TEST-a.xml", "123")]), "unit")

    def test_malformed_xml_and_non_report_files_are_not_executed(self):
        report = MODULE.parse_archive(archive([
            ("core/build/test-results/test/TEST-a.xml", "<broken>"),
            ("malicious.py", "raise RuntimeError('must never execute')"),
        ]), "unit")
        self.assertEqual([], report["failures"])
        self.assertEqual(1, len(report["unreadable"]))

    def test_workflow_preserves_trusted_checkout_and_serialized_writer(self):
        root = SCRIPT.parents[2]
        workflow = (root / ".github/workflows/ci-report.yml").read_text()
        self.assertIn("types: [in_progress, completed]", workflow)
        self.assertNotIn("workflow_run.conclusion != 'success'", workflow)
        refs = MODULE.re.findall(r"^\s+ref: (.+)$", workflow, MODULE.re.MULTILINE)
        self.assertEqual(["${{ github.event.repository.default_branch }}"] * 2, refs)
        self.assertEqual(2, workflow.count("persist-credentials: false"))
        self.assertIn("group: ci-diagnosis-${{ github.repository_id }}-${{ needs.resolve.outputs.pr_number }}", workflow)
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertNotIn("issues: write", workflow.split("  comment:")[0])
        ci = (root / ".github/workflows/ci.yml").read_text()
        self.assertIn("run: make ci-report-test", ci)
        screenshots = ci.split("  screenshot-tests:")[1].split("  instrumented-tests:")[0]
        self.assertNotIn("continue-on-error", screenshots)
        self.assertNotIn("Signal Failure", screenshots)
        # Keep step-aware commands from silently drifting when CI steps are renamed.
        step_names = set(MODULE.re.findall(r"- name: (.+)", ci))
        self.assertEqual(set(), set(MODULE.RENDER.STEP_RULES) - step_names - {"Signal Failure"})

    def test_transient_download_error_can_recover(self):
        api = FakeApi([{"run": run(), "downloads": {"actions/jobs/10/logs": OSError("not ready")}},
                       {"run": run(), "downloads": {"actions/jobs/10/logs": b"2026-09-17T12:01:30Z ##[error]real failure"}}])
        evidence = MODULE.Evidence(api)
        first = evidence.enrich(run(), [job()], [])
        self.assertTrue(first[0]["diagnostics"]["Run Unit Tests"]["unavailable"])
        api.index = 1
        second = evidence.enrich(run(), [job()], [])
        self.assertEqual("real failure", second[0]["diagnostics"]["Run Unit Tests"]["error"])


if __name__ == "__main__":
    unittest.main()
