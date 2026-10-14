#!/usr/bin/env python3
"""Adversarial tests for verified post-merge CI result selection."""

import importlib.util
import io
import json
import os
import re
import subprocess
import tempfile
import textwrap
import urllib.error
import unittest
import zipfile
from email.message import Message
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("select_master_test_reuse", Path(__file__).with_name("select-master-test-reuse.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

REPOSITORY = "simtop/BillionBeers"
SHA = {name: char * 40 for name, char in {
    "master": "a", "tree": "b", "workflow": "c", "head": "d", "base": "e", "checkout": "f",
}.items()}


def zip_proof(proof):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("verified-master-test-result.json", json.dumps(proof).encode())
    return stream.getvalue()


class FakeGitHub:
    def __init__(self):
        self.pr = {
            "number": 17,
            "merged": True,
            "merge_commit_sha": SHA["master"],
            "base": {"ref": "master", "repo": {"full_name": REPOSITORY, "id": 7}},
            "head": {"sha": SHA["head"], "repo": {"full_name": REPOSITORY, "id": 7}},
        }
        self.run = {
            "id": 99, "run_number": 44, "run_attempt": 1, "status": "completed", "conclusion": "success",
            "event": "pull_request", "path": ".github/workflows/ci.yml", "head_sha": SHA["head"],
            "head_repository": {"id": 7},
            # GitHub commonly omits the PR array after merge. The proof's base is instead
            # independently checked against the actual merge commit's first parent.
            "pull_requests": [],
        }
        self.run_list = [self.run]
        self.jobs = [{"name": name, "status": "completed", "conclusion": "success", "steps": []}
                     for name in MODULE.REQUIRED_JOBS]
        gate = next(job for job in self.jobs if job["name"] == "CI Gate")
        gate["steps"] = [{"name": "Checkout tested source for the verified result proof", "conclusion": "success"},
                         {"name": "Record verified test result", "conclusion": "success"},
                         {"name": "Upload verified test result", "conclusion": "success"}]
        self.proof = {
            "schema": 1, "repository": REPOSITORY, "repository_id": 7, "pr_number": 17,
            "pr_head": SHA["head"], "base_sha": SHA["base"],
            "run_id": 99, "run_attempt": 1, "event": "pull_request",
            "checkout_sha": SHA["checkout"], "tree_sha": SHA["tree"],
            "workflow": ".github/workflows/ci.yml", "workflow_blob": SHA["workflow"],
        }
        self.artifacts = [{"id": 100, "name": f"{MODULE.ARTIFACT_PREFIX}99-1", "size_in_bytes": 100}]
        self.archive = None
        self.fail = None

    def get(self, path):
        if self.fail:
            raise OSError("API unavailable")
        if path == "":
            return {"id": 7}
        if path == f"commits/{SHA['master']}":
            return {"parents": [{"sha": SHA["base"]}], "commit": {"tree": {"sha": SHA["tree"]}}}
        if path == f"commits/{SHA['head']}":
            return {"commit": {"tree": {"sha": SHA["tree"]}}}
        if path.startswith("contents/.github/workflows/ci.yml?ref="):
            ref = path.split("?ref=", 1)[1]
            return {"sha": SHA["workflow"] if ref in {SHA["master"], SHA["checkout"], SHA["base"]} else "0" * 40}
        if path == f"commits/{SHA['checkout']}":
            return {"parents": [{"sha": self.proof["base_sha"]}, {"sha": SHA["head"]}],
                    "commit": {"tree": {"sha": self.proof["tree_sha"]}}}
        if path == "pulls/17":
            return self.pr
        if path == f"actions/runs/99/artifacts":
            return {"artifacts": self.artifacts}
        if path == f"actions/runs/99/jobs":
            return {"jobs": self.jobs}
        raise AssertionError(path)

    def pages(self, path, key):
        if path.startswith("commits/") and path.endswith("/pulls"):
            # GitHub's commits/{sha}/pulls response contains abbreviated PR resources.
            return [{"number": 17, "state": "closed", "merged_at": "2026-10-11T12:00:00Z"}]
        if path.startswith("actions/workflows/ci.yml/runs?"):
            return list(self.runs)
        if path == "actions/runs/99/jobs":
            return self.jobs
        if path == "actions/runs/99/artifacts":
            return self.artifacts
        raise AssertionError(path)

    def download(self, path):
        return self.archive if self.archive is not None else zip_proof(self.proof)

    @property
    def runs(self):
        return self.run_list


class SelectMasterReuseTest(unittest.TestCase):
    def setUp(self):
        self.api = FakeGitHub()

    def select(self):
        return MODULE.select(self.api, REPOSITORY, SHA["master"], "push", "refs/heads/master")

    def test_reuses_only_exact_verified_successful_pr_tree(self):
        self.assertEqual((True, 99, 17), self.select())

    def test_force_full_dispatch_bypasses_reuse_without_constructing_api_client(self):
        with tempfile.NamedTemporaryFile(mode="w+", encoding="utf-8") as output:
            environment = {
                "GITHUB_OUTPUT": output.name,
                "FORCE_FULL": "true",
                "EVENT_NAME": "workflow_dispatch",
                "GITHUB_REF": "refs/heads/master",
            }
            with patch.dict(os.environ, environment, clear=True), \
                    patch.object(MODULE, "GitHub") as github:
                MODULE.main()
                github.assert_not_called()
            output.seek(0)
            self.assertIn("eligible=false", output.read())

    def test_only_literal_true_activates_force_full_override(self):
        with tempfile.NamedTemporaryFile(mode="w+", encoding="utf-8") as output:
            for value in ("false", "TRUE", "1", "yes", ""):
                with self.subTest(value=value):
                    output.seek(0)
                    output.truncate()
                    environment = {
                        "GITHUB_OUTPUT": output.name,
                        "FORCE_FULL": value,
                        "EVENT_NAME": "workflow_dispatch",
                        "GITHUB_REF": "refs/heads/master",
                        "GITHUB_SHA": SHA["master"],
                        "REPO": REPOSITORY,
                        "GH_TOKEN": "test-token",
                    }
                    with patch.dict(os.environ, environment, clear=True), \
                            patch.object(MODULE, "GitHub", return_value=self.api) as github, \
                            patch.object(MODULE, "select", return_value=False) as select:
                        MODULE.main()
                        github.assert_called_once_with("test-token", REPOSITORY)
                        select.assert_called_once_with(
                            self.api, REPOSITORY, SHA["master"], "workflow_dispatch", "refs/heads/master"
                        )

    def test_manual_workflow_exposes_boolean_force_full_input(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows" / "ci.yml").read_text()
        self.assertIn("force_full:", workflow)
        self.assertIn("description: Run all five test lanes even if a verified test result exists.", workflow)
        self.assertRegex(workflow, r"force_full:\n\s+description:.*?\n\s+type: boolean\n\s+required: false\n\s+default: false")
        self.assertIn("FORCE_FULL: ${{ inputs.force_full }}", workflow)

    def test_reuse_selector_runs_in_isolated_python_mode(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows" / "ci.yml").read_text()
        self.assertIn("run: python3 -I .github/scripts/select-master-test-reuse.py", workflow)

    def test_inline_proof_ignores_hostile_checkout_stdlib_shadows(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows" / "ci.yml").read_text()
        producer_step = workflow.split("- name: Record verified test result", 1)[1]
        producer_step = producer_step.split("- name: Upload verified test result", 1)[0]
        match = re.search(r"(?ms)^\s+python3 -I - <<'PY'\s*$\n(?P<code>.*?)^\s+PY\s*$", producer_step)
        self.assertIsNotNone(match)
        producer = textwrap.dedent(match.group("code"))

        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory)
            (checkout / ".github/workflows").mkdir(parents=True)
            workflow_file = checkout / ".github/workflows/ci.yml"
            workflow_file.write_text(workflow)
            (checkout / "json.py").write_text("raise RuntimeError('shadow json imported')\n")
            (checkout / "subprocess.py").write_text("raise RuntimeError('shadow subprocess imported')\n")
            subprocess.run(["git", "init", "-q"], cwd=checkout, check=True)
            subprocess.run(["git", "config", "user.name", "Test"], cwd=checkout, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=checkout, check=True)
            subprocess.run(["git", "add", "."], cwd=checkout, check=True)
            subprocess.run(["git", "commit", "-qm", "fixture"], cwd=checkout, check=True)
            actual = {
                "checkout_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=checkout, text=True).strip(),
                "tree_sha": subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=checkout, text=True).strip(),
                "workflow_blob": subprocess.check_output(
                    ["git", "rev-parse", "HEAD:.github/workflows/ci.yml"], cwd=checkout, text=True
                ).strip(),
            }
            environment = dict(os.environ, REPOSITORY=REPOSITORY, REPOSITORY_ID="7", PR_NUMBER="17",
                               PR_HEAD=SHA["head"], PR_BASE=SHA["base"], RUN_ID="99",
                               RUN_ATTEMPT="1", EVENT_NAME="pull_request")
            completed = subprocess.run(
                ["python3", "-I", "-"], input=producer, cwd=checkout, env=environment,
                text=True, capture_output=True, check=True,
            )
            proof = json.loads((checkout / "build/verified-master-test-result/verified-master-test-result.json").read_text())
            self.assertEqual(actual, {key: proof[key] for key in actual})

    def test_non_master_or_non_push_event_keeps_full_suite(self):
        self.assertFalse(MODULE.select(self.api, REPOSITORY, SHA["master"], "pull_request", "refs/heads/master"))
        self.assertFalse(MODULE.select(self.api, REPOSITORY, SHA["master"], "merge_group", "refs/heads/master"))
        self.assertFalse(MODULE.select(self.api, REPOSITORY, SHA["master"], "push", "refs/heads/feature"))
        self.assertEqual((True, 99, 17), MODULE.select(
            self.api, REPOSITORY, SHA["master"], "workflow_dispatch", "refs/heads/master"
        ))

    def test_tree_and_workflow_must_match_current_master_and_trusted_base(self):
        self.api.proof["tree_sha"] = "9" * 40
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.proof["workflow_blob"] = "9" * 40
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.get = lambda path: ({"sha": "9" * 40} if path.endswith(SHA["base"]) and path.startswith("contents/")
                                      else FakeGitHub.get(self.api, path))
        self.assertRaises(ValueError, self.select)

    def test_synthetic_checkout_tree_must_match_master_even_if_pr_head_tree_matches(self):
        self.api.proof["tree_sha"] = "9" * 40
        # Both trees are confirmed by the fake GitHub commit API; only the synthetic merge differs.
        self.assertEqual(SHA["tree"], self.api.get(f"commits/{SHA['head']}")["commit"]["tree"]["sha"])
        self.assertRaises(ValueError, self.select)

    def test_tested_base_must_match_merged_commit_first_parent(self):
        self.api.proof["base_sha"] = "9" * 40
        # Point the source base ref at a valid workflow blob while the merged commit retains its
        # original first parent, so this reaches the independent first-parent comparison.
        self.api.get = lambda path: ({"sha": SHA["workflow"]} if path.startswith("contents/.github/workflows/ci.yml?ref=")
                                      else FakeGitHub.get(self.api, path))
        # Checkout parents and source-base workflow are valid; only master first-parent differs.
        self.assertRaises(ValueError, self.select)

    def test_newer_pending_or_rerun_attempt_disqualifies_older_success(self):
        newer = dict(self.api.run, id=100, run_number=45, status="in_progress", conclusion=None)
        self.api.runs.append(newer)
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.run["run_attempt"] = 2
        self.assertRaises(ValueError, self.select)

    def test_newer_exact_head_run_with_malformed_identity_blocks_older_green_run(self):
        newer = dict(self.api.run, id=100, run_number=45, path=".github/workflows/other.yml")
        self.api.runs.append(newer)
        self.assertRaises(ValueError, self.select)
        self.setUp()
        newer = dict(self.api.run, id=100, run_number=45)
        newer.pop("head_repository")
        self.api.runs.append(newer)
        self.assertRaises(ValueError, self.select)

    def test_skipped_missing_or_duplicate_lane_disqualifies_reuse(self):
        unit = next(job for job in self.api.jobs if job["name"] == "Unit Tests")
        unit["conclusion"] = "skipped"
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.jobs = self.api.jobs[:-1]
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.jobs.append(dict(self.api.jobs[0]))
        self.assertRaises(ValueError, self.select)

    def test_missing_proof_step_disqualifies_reuse(self):
        gate = next(job for job in self.api.jobs if job["name"] == "CI Gate")
        gate["steps"][-1]["conclusion"] = "skipped"
        self.assertRaises(ValueError, self.select)

    def test_missing_expired_duplicated_or_malformed_artifact_disqualifies_reuse(self):
        self.api.artifacts = []
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.artifacts.append(dict(self.api.artifacts[0]))
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.artifacts[0]["expired"] = True
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.archive = b"not a zip"
        self.assertRaises(zipfile.BadZipFile, self.select)

    def test_unsafe_archive_member_and_oversized_json_disqualify_reuse(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("../verified-master-test-result.json", json.dumps(self.api.proof))
        self.api.archive = stream.getvalue()
        self.assertRaises(ValueError, self.select)
        self.setUp()
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("verified-master-test-result.json", b"x" * (MODULE.MAX_JSON + 1))
        self.api.archive = stream.getvalue()
        self.api.artifacts[0]["size_in_bytes"] = len(self.api.archive)
        self.assertRaises(ValueError, self.select)

    def test_duplicate_json_keys_are_rejected(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("verified-master-test-result.json", b'{"schema":1,"schema":1}')
        self.api.archive = stream.getvalue()
        self.api.artifacts[0]["size_in_bytes"] = len(self.api.archive)
        self.assertRaises(ValueError, self.select)

    def test_artifact_workflow_run_identity_is_checked_when_api_returns_it(self):
        self.api.artifacts[0]["workflow_run"] = {
            "id": 99, "repository_id": 7, "head_repository_id": 7, "head_sha": SHA["head"],
        }
        self.assertEqual((True, 99, 17), self.select())
        self.api.artifacts[0]["workflow_run"]["head_sha"] = "9" * 40
        self.assertRaises(ValueError, self.select)

    def test_wrong_repo_event_checkout_or_api_error_disqualifies_reuse(self):
        self.api.proof["repository"] = "someone-else/repo"
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.run["event"] = "workflow_dispatch"
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.proof["checkout_sha"] = "9" * 40
        self.assertRaises(AssertionError, self.select)
        self.setUp()
        self.api.fail = True
        self.assertRaises(OSError, self.select)

    def test_only_exact_same_repository_master_merge_is_eligible(self):
        self.api.pr["head"]["repo"]["full_name"] = "fork/BillionBeers"
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.pr["merge_commit_sha"] = "9" * 40
        self.assertRaises(ValueError, self.select)
        self.setUp()
        self.api.pr["head"]["repo"]["full_name"] = "simtop/BillionBeers"
        self.api.proof["base_sha"] = "9" * 40
        self.assertRaises(ValueError, self.select)

    def test_artifact_redirect_never_forwards_repository_token(self):
        message = Message()
        message["Location"] = "https://objects.example.invalid/signed-artifact"
        redirect = urllib.error.HTTPError("https://api.github.com/artifact", 302, "Found", message, None)

        class Opener:
            def open(self, request, timeout):
                self.request = request
                raise redirect

        opener = Opener()
        body = io.BytesIO(b"bounded-artifact")
        with patch.object(MODULE.urllib.request, "build_opener", return_value=opener), \
                patch.object(MODULE.urllib.request, "urlopen", return_value=body) as signed_fetch:
            downloaded = MODULE.GitHub("secret-token", REPOSITORY).download("actions/artifacts/100/zip")
        self.assertEqual(b"bounded-artifact", downloaded)
        self.assertEqual("Bearer secret-token", opener.request.get_header("Authorization"))
        self.assertEqual("https://objects.example.invalid/signed-artifact", signed_fetch.call_args.args[0])
        self.assertNotIsInstance(signed_fetch.call_args.args[0], MODULE.urllib.request.Request)

    def test_repository_identity_request_uses_exact_api_url(self):
        requests = []

        def response(request, timeout):
            requests.append(request)
            return io.BytesIO(b'{"id":7}')

        with patch.object(MODULE.urllib.request, "urlopen", side_effect=response):
            self.assertEqual({"id": 7}, MODULE.GitHub("token", REPOSITORY).get(""))
        self.assertEqual("https://api.github.com/repos/simtop/BillionBeers", requests[0].full_url)

    def test_main_writes_full_suite_output_on_api_error(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "github-output"
            environment = {
                "GH_TOKEN": "token", "REPO": REPOSITORY, "GITHUB_SHA": SHA["master"],
                "EVENT_NAME": "push", "GITHUB_REF": "refs/heads/master", "GITHUB_OUTPUT": str(output),
            }
            with patch.dict(os.environ, environment, clear=True), \
                    patch.object(MODULE, "select", side_effect=OSError("API unavailable")), \
                    redirect_stdout(io.StringIO()):
                MODULE.main()
            self.assertIn("eligible=false", output.read_text())


if __name__ == "__main__":
    unittest.main()
