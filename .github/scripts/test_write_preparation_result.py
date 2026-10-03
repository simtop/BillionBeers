#!/usr/bin/env python3
"""Bounded result artifact and four-lane producer contract tests."""

from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


WRITER = load("write_preparation_result", "write-preparation-result.py")
RECONCILE = WRITER.RECONCILE
SHA = "a" * 40
BRANCH = "dependabot/gradle/example"
REPOSITORY = "owner/repo"
RUN_ID = 123
ATTEMPT = 2


def api_pr():
    return {"number": 7, "state": "open", "user": {"login": "dependabot[bot]"},
            "head": {"sha": SHA, "ref": BRANCH, "repo": {"full_name": REPOSITORY}},
            "base": {"repo": {"id": 42, "full_name": REPOSITORY}}}


def full_result(run_id=RUN_ID, attempt=ATTEMPT, *, output_sha=SHA, source_sha=SHA):
    graph_digest = "b" * 64
    return {"schema": 1, "repository": REPOSITORY, "repository_id": 42, "pr_number": 7,
            "branch": BRANCH, "source_sha": source_sha, "output_sha": output_sha,
            "mode": "write", "run_id": run_id, "run_attempt": attempt, "kind": "full",
            "graph_mode": "reference", "graph_digest": graph_digest,
            "lanes": {lane: {"lane": lane, "run_id": run_id, "run_attempt": attempt,
                "head_sha": source_sha, "mode": "write", "graph_mode": "reference",
                "graph_digest": graph_digest, "conclusion": "success", "manifest_sha256": "c" * 64}
                for lane in WRITER.LANES}}


def archive(content):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as result:
        result.writestr("authoritative-preparation.json", json.dumps(content))
    return stream.getvalue()


class FakeEvidenceApi:
    repository = REPOSITORY

    def __init__(self, run, result):
        self.run = run
        self.result = result
        self.run_list = [run]
        self.path_list = [{"filename": "gradle/libs.versions.toml"}]
        self.runs = {run["id"]: run}
        self.results = {run["id"]: result} if result is not None else {}
        self.expired = set()

    def pages(self, path, key=None, **kwargs):
        if path == "pulls/7/files":
            return self.path_list
        if path.startswith("pulls?"):
            return [api_pr()]
        if path.startswith("actions/workflows/"):
            return self.run_list
        if path.startswith("actions/runs/") and path.endswith("/artifacts"):
            run_id = int(path.split("/")[2])
            if run_id not in self.results:
                return []
            return [{"id": run_id, "name": f"authoritative-preparation-result-{run_id}-{self.runs[run_id]['run_attempt']}",
                     "size_in_bytes": len(archive(self.results[run_id])), "expired": run_id in self.expired}]
        return [{"id": 9, "name": f"authoritative-preparation-result-{self.run['id']}-{self.run['run_attempt']}",
                 "size_in_bytes": len(archive(self.result)), "expired": False}]

    def get(self, path):
        if path.startswith("actions/runs/"):
            return self.runs[int(path.rsplit("/", 1)[-1])]
        if path.startswith("commits/"):
            sha = path.split("/", 1)[1]
            return {"parents": [{"sha": "d" * 40 if sha == SHA else "e" * 40}]}
        raise AssertionError(path)

    def download(self, path, limit):
        artifact_id = int(path.split("/")[2])
        return archive(self.results[artifact_id])


class WritePreparationResultTest(unittest.TestCase):
    def test_graph_identity_requires_all_four_successful_manifests_and_matching_reviews(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            review = WRITER.PREPARATION.graph_review("a:lib:1\n", "a:lib:1\n", SHA)
            for lane in WRITER.LANES:
                folder = root / lane
                folder.mkdir()
                patch = b""
                formatting = b""
                (folder / "prepared.patch").write_bytes(patch)
                (folder / "formatting-files").write_bytes(formatting)
                (folder / "verification-metadata.xml").write_text("<verification-metadata/>")
                (folder / "graph-review.json").write_text(json.dumps(review))
                manifest = {"lane": lane, "head_sha": SHA, "run_id": str(RUN_ID),
                    "run_attempt": str(ATTEMPT), "source_ref": BRANCH, "conclusion": "success",
                    "mode": "write", "skipped": False,
                    "prepared_sha256": hashlib.sha256(patch).hexdigest(),
                    "formatting_files_sha256": hashlib.sha256(formatting).hexdigest(),
                    "graph_mode": "reference", "graph_digest": review["digest"]}
                (folder / "manifest.json").write_text(json.dumps(manifest))
            digest, identities = WRITER.graph_identity(root, SHA, RUN_ID, ATTEMPT, BRANCH)
            self.assertEqual(review["digest"], digest)
            self.assertEqual(WRITER.LANES, set(identities))
            self.assertTrue(all(item["conclusion"] == "success" for item in identities.values()))
            broken = root / "apple" / "manifest.json"
            payload = json.loads(broken.read_text())
            payload["graph_digest"] = "d" * 64
            broken.write_text(json.dumps(payload))
            with self.assertRaises(ValueError):
                WRITER.graph_identity(root, SHA, RUN_ID, ATTEMPT, BRANCH)

    def test_result_schema_binds_every_lane_and_exact_pr_run_identity(self):
        run = {"id": RUN_ID, "run_attempt": ATTEMPT, "head_sha": SHA}
        result = full_result()
        RECONCILE.validate_preparation_result(Mock(repository=REPOSITORY), result, run, api_pr())
        altered = full_result()
        altered["lanes"].pop("apple")
        with self.assertRaises(ValueError):
            RECONCILE.validate_preparation_result(Mock(repository=REPOSITORY), altered, run, api_pr())
        mismatches = {
            "repository": "other/repo", "pr_number": 8, "branch": "other-branch",
            "source_sha": "d" * 40, "mode": "candidate", "run_id": RUN_ID + 1,
            "run_attempt": ATTEMPT + 1,
        }
        for key, value in mismatches.items():
            with self.subTest(identity=key):
                altered = full_result()
                altered[key] = value
                with self.assertRaises(ValueError):
                    RECONCILE.validate_preparation_result(Mock(repository=REPOSITORY), altered, run, api_pr())
        altered = full_result()
        altered["lanes"]["web"]["run_attempt"] = 1
        with self.assertRaises(ValueError):
            RECONCILE.validate_preparation_result(Mock(repository=REPOSITORY), altered, run, api_pr())

    def test_result_archive_accepts_only_bounded_named_json_with_matching_run_identity(self):
        run = {"id": RUN_ID, "run_attempt": ATTEMPT, "head_sha": SHA, "run_number": 1}
        result = full_result()
        api = FakeEvidenceApi(run, result)
        parsed, artifact = RECONCILE.result_archive(api, run, api_pr())
        self.assertEqual("full", parsed["kind"])
        self.assertEqual(RUN_ID, artifact["id"])
        api.pages = lambda path, key=None: [{"id": 9, "name": "wrong-name", "size_in_bytes": 10, "expired": False}]
        with self.assertRaises(ValueError):
            RECONCILE.result_archive(api, run, api_pr())
        api = FakeEvidenceApi(run, result)
        api.expired.add(RUN_ID)
        with self.assertRaises(ValueError):
            RECONCILE.result_archive(api, run, api_pr())
        api = FakeEvidenceApi(run, result)
        api.results.clear()
        with self.assertRaises(ValueError):
            RECONCILE.result_archive(api, run, api_pr())
        api = FakeEvidenceApi(run, result)
        api.download = lambda path, limit: b"malformed zip"
        with self.assertRaises(zipfile.BadZipFile):
            RECONCILE.result_archive(api, run, api_pr())
        api = FakeEvidenceApi(run, result)
        api.pages = lambda path, key=None, **kwargs: ([{"id": RUN_ID, "name": f"authoritative-preparation-result-{RUN_ID}-{ATTEMPT}",
            "size_in_bytes": RECONCILE.MAX_PREPARATION_RESULT_ARCHIVE + 1, "expired": False},
            {"id": RUN_ID + 1, "name": f"authoritative-preparation-result-{RUN_ID}-{ATTEMPT}",
            "size_in_bytes": 10, "expired": False}])
        with self.assertRaises(ValueError):
            RECONCILE.result_archive(api, run, api_pr())

    def test_reuse_references_a_full_origin_and_rejects_nested_reuse(self):
        source = "d" * 40
        origin = {"id": RUN_ID, "run_number": 1, "run_attempt": 1, "head_sha": source,
                  "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
                  "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
                  "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
                  "status": "completed", "conclusion": "success"}
        current = {"id": RUN_ID + 1, "run_number": 2, "run_attempt": 1, "head_sha": SHA}
        result = full_result(run_id=RUN_ID, attempt=1, source_sha=source, output_sha=SHA)
        result["kind"] = "reuse"
        result.pop("lanes")
        result.update({"source_sha": SHA, "run_id": current["id"], "run_attempt": current["run_attempt"],
                       "origin_run_id": RUN_ID, "origin_run_attempt": 1})
        api = FakeEvidenceApi(origin, full_result(run_id=RUN_ID, attempt=1, source_sha=source, output_sha=SHA))
        api.runs[origin["id"]] = origin
        api.results[origin["id"]] = full_result(run_id=RUN_ID, attempt=1, source_sha=source, output_sha=SHA)
        RECONCILE.validate_preparation_result(api, result, current, api_pr())
        self.assertEqual("full", result["origin_result"]["kind"])
        nested = dict(result, origin_run_id=RUN_ID)
        nested_origin = api.results[RUN_ID]
        nested_origin["kind"] = "reuse"
        nested_origin.pop("lanes")
        nested_origin.update({"source_sha": SHA, "origin_run_id": RUN_ID, "origin_run_attempt": 1})
        with self.assertRaises(ValueError):
            RECONCILE.validate_preparation_result(api, nested, current, api_pr())

    def test_live_selector_requires_valid_result_for_current_pr_output(self):
        run = {"id": RUN_ID, "run_number": 1, "run_attempt": ATTEMPT, "head_sha": SHA,
               "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
               "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}",
               "event": "workflow_dispatch", "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
               "status": "completed", "conclusion": "success", "html_url": "https://github.test/run/123"}
        api = FakeEvidenceApi(run, full_result())
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertTrue(selected["authoritative_valid"])
        self.assertEqual(SHA, selected["result"]["output_sha"])
        pending = dict(run, id=RUN_ID + 1, run_number=2, run_attempt=1,
                       status="in_progress", conclusion=None)
        api.runs[pending["id"]] = pending
        api.run_list = [run, pending]
        api.get = lambda path: pending if path.endswith(f"/{RUN_ID + 1}") else run
        blocked = RECONCILE.preparation_current(api, api_pr())
        self.assertEqual("pending", blocked["category"])
        self.assertFalse(blocked["authoritative_valid"])
        experiment = dict(run, id=RUN_ID + 2, run_number=3, display_title=f"Dependency preparation | mode=candidate | ref={BRANCH}",
                          status="completed", conclusion="failure")
        api.runs[experiment["id"]] = experiment
        api.run_list = [run, pending, experiment]
        api.get = lambda path: {RUN_ID: run, RUN_ID + 1: pending, RUN_ID + 2: experiment}[int(path.rsplit("/", 1)[-1])]
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertEqual(RUN_ID + 1, selected["id"])

    def test_selector_orders_new_run_before_late_rerun_of_older_run(self):
        older = {"id": RUN_ID, "run_number": 5, "run_attempt": 4, "head_sha": SHA,
                 "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
                 "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
                 "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
                 "status": "completed", "conclusion": "success"}
        newer = dict(older, id=RUN_ID + 1, run_number=6, run_attempt=1)
        api = FakeEvidenceApi(older, full_result(run_id=RUN_ID, attempt=4))
        api.runs[newer["id"]] = newer
        api.results[newer["id"]] = full_result(run_id=RUN_ID + 1, attempt=1)
        api.run_list = [older, newer]
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertEqual(RUN_ID + 1, selected["id"])

    def test_writer_rejects_duplicate_open_dispatch_branch_even_with_requested_number(self):
        api = FakeEvidenceApi({"id": RUN_ID, "run_attempt": ATTEMPT}, full_result())
        duplicate = api_pr()
        duplicate["number"] = 8
        api.pages = lambda path, key=None, **kwargs: [api_pr(), duplicate]
        self.assertIsNone(WRITER.find_pr(api, REPOSITORY, BRANCH, SHA, 7))

    def test_experiment_does_not_replace_latest_authoritative_write(self):
        write = {"id": RUN_ID, "run_number": 1, "run_attempt": 1, "head_sha": SHA,
                 "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
                 "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
                 "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
                 "status": "completed", "conclusion": "success"}
        experiment = dict(write, id=RUN_ID + 1, run_number=2,
            display_title=f"Dependency preparation | mode=candidate | ref={BRANCH}", conclusion="failure")
        api = FakeEvidenceApi(write, full_result(run_id=RUN_ID, attempt=1))
        api.runs[experiment["id"]] = experiment
        api.run_list = [write, experiment]
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertTrue(selected["authoritative_valid"])
        self.assertEqual(RUN_ID, selected["id"])

    def test_writer_requires_unique_dispatch_branch_association(self):
        api = FakeEvidenceApi({"id": RUN_ID, "run_attempt": ATTEMPT}, full_result())
        duplicate = api_pr()
        duplicate["number"] = 8
        api.pages = lambda path, key=None, **kwargs: [api_pr(), duplicate]
        self.assertIsNone(WRITER.find_pr(api, REPOSITORY, BRANCH, SHA, 7))

    def test_newer_unrelated_failed_source_is_stale_and_does_not_hide_current_result(self):
        successful = {"id": RUN_ID, "run_number": 1, "run_attempt": 1, "head_sha": SHA,
               "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
               "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
               "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
               "status": "completed", "conclusion": "success", "html_url": "https://github.test/run/123"}
        newer = dict(successful, id=RUN_ID + 1, run_number=2, head_sha="d" * 40,
                     status="completed", conclusion="failure")
        api = FakeEvidenceApi(successful, full_result(attempt=1))
        api.runs[newer["id"]] = newer
        api.run_list = [successful, newer]
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertTrue(selected["authoritative_valid"])
        self.assertEqual(RUN_ID, selected["id"])
        self.assertIn(newer["id"], [item["id"] for item in selected["stale_runs"]])

    def test_failed_writer_with_valid_pre_push_result_for_live_output_blocks(self):
        source = "d" * 40
        failed = {"id": RUN_ID, "run_number": 2, "run_attempt": 1, "head_sha": source,
                  "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
                  "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
                  "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
                  "status": "completed", "conclusion": "failure", "html_url": "https://github.test/run/123"}
        result = full_result(attempt=1, source_sha=source, output_sha=SHA)
        api = FakeEvidenceApi(failed, result)
        selected = RECONCILE.preparation_current(api, api_pr())
        self.assertFalse(selected["authoritative_valid"])
        self.assertEqual("unresolved_write_attempt", selected["category"])
        self.assertEqual(SHA, selected["result"]["output_sha"])

    def test_pending_writer_blocks_live_output_only_when_its_result_names_that_output(self):
        source = "d" * 40
        pending = {"id": RUN_ID, "run_number": 2, "run_attempt": 1, "head_sha": source,
                   "head_branch": BRANCH, "head_repository": {"full_name": REPOSITORY},
                   "path": f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}", "event": "workflow_dispatch",
                   "display_title": f"Dependency preparation | mode=write | ref={BRANCH}",
                   "status": "in_progress", "conclusion": None, "html_url": "https://github.test/run/123"}
        api = FakeEvidenceApi(pending, full_result(run_id=RUN_ID, attempt=1, source_sha=source, output_sha=SHA))
        blocked = RECONCILE.preparation_current(api, api_pr())
        self.assertEqual("pending", blocked["category"])
        api.results.clear()
        stale = RECONCILE.preparation_current(api, api_pr())
        self.assertNotEqual(RUN_ID, stale.get("id"))


if __name__ == "__main__":
    unittest.main()
