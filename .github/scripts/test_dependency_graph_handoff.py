from __future__ import annotations

import copy
import importlib.util
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "dependency_graph_handoff", Path(__file__).with_name("dependency-graph-handoff.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

SHA = "a" * 40
OUTPUT_SHA = "b" * 40
BRANCH = "dependabot/gradle/example"
REPOSITORY = "owner/repo"


def archive(files: dict[str, bytes]) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zipped:
        for name, content in files.items():
            zipped.writestr(name, content)
    return buffer.getvalue()


class FakeApi:
    def __init__(self):
        self.repository = REPOSITORY
        self.review = MODULE.PREPARATION.graph_review("g:a:1\n", "g:a:2\ng:b:1\n", SHA)
        self.review_run = {
            "id": 41, "run_number": 41, "run_attempt": 1, "path": ".github/workflows/dependency-graph-review.yml",
            "event": "pull_request", "status": "completed", "conclusion": "success", "head_sha": SHA,
            "head_branch": BRANCH, "head_repository": {"id": 1, "full_name": REPOSITORY},
            "pull_requests": [{"number": 7}], "html_url": "https://github.com/owner/repo/actions/runs/41",
            "created_at": "2026-10-03T12:00:00Z",
        }
        self.pr = {
            "number": 7, "state": "open", "user": {"login": "dependabot[bot]"},
            "base": {"repo": {"id": 1, "full_name": REPOSITORY}},
            "head": {"sha": SHA, "ref": BRANCH, "repo": {"id": 1, "full_name": REPOSITORY}},
            "html_url": "https://github.com/owner/repo/pull/7",
        }
        self.repo = {"id": 1, "default_branch": "master"}
        self.handoff = {
            "id": 90, "path": ".github/workflows/approve-dependency-graph.yml", "event": "workflow_dispatch",
            "run_attempt": 1, "head_branch": "master", "head_sha": "c" * 40,
            "triggering_actor": {"login": "maintainer"},
            "display_title": "Dependency graph approval | source_run=41 | source_attempt=1 | approved=true",
        }
        self.permission = {"permission": "write", "role_name": "maintain"}
        self.review_manifest = {
            "schema": 1, "repository": REPOSITORY, "repository_id": 1,
            "pr_number": 7, "branch": BRANCH, "source_sha": SHA,
            "run_id": 41, "run_attempt": 1,
            "workflow": ".github/workflows/dependency-graph-review.yml",
            "graph_digest": self.review["digest"],
        }
        self.review_zip = archive({
            "graph-review.json": json.dumps(self.review).encode(),
            "graph-review.md": b"review artifact",
            "manifest.json": json.dumps(self.review_manifest).encode(),
        })
        self.review_artifact = {"id": 70, "name": "dependency-graph-review-41-1",
                                "size_in_bytes": len(self.review_zip), "expired": False}
        self.runs = {41: self.review_run, 90: self.handoff}
        self.ci_jobs = []
        self.current_receipt_zip = None
        self.prior_runs = []
        self.prior_artifacts = {}
        self.downloads = {"actions/artifacts/70/zip": self.review_zip}
        self.posts = []
        self.writer = {
            "id": 100, "path": ".github/workflows/regen-verification-metadata.yml",
            "event": "workflow_dispatch", "head_branch": BRANCH, "head_sha": SHA,
            "head_repository": {"full_name": REPOSITORY}, "run_attempt": 1,
            "display_title": "Dependency preparation | mode=write | ref=" + BRANCH,
            "html_url": "https://github.com/owner/repo/actions/runs/100",
        }
        self.runs[100] = self.writer

    def get(self, path):
        if path == "":
            return copy.deepcopy(self.repo)
        if path.startswith("actions/runs/"):
            run_id = int(path.split("/")[2])
            return copy.deepcopy(self.runs[run_id])
        if path == "pulls/7":
            return copy.deepcopy(self.pr)
        if path.endswith("collaborators/maintainer/permission"):
            return copy.deepcopy(self.permission)
        raise AssertionError(f"Unexpected GET {path}")

    def pages(self, path, key=None, *, max_items=None):
        if path == "actions/runs/41/artifacts":
            result = [copy.deepcopy(self.review_artifact)]
        elif path == "actions/runs/90/artifacts":
            result = [copy.deepcopy(self.current_receipt_artifact)] if self.current_receipt_zip else []
        elif path.startswith("actions/workflows/approve-dependency-graph.yml/runs?"):
            result = copy.deepcopy(self.prior_runs)
        elif any(path == f"actions/runs/{run_id}/artifacts" for run_id in self.prior_artifacts):
            run_id = int(path.split("/")[2])
            result = copy.deepcopy(self.prior_artifacts[run_id])
        elif path.startswith("actions/workflows/ci.yml/runs?"):
            result = [copy.deepcopy(self.ci_run)] if hasattr(self, "ci_run") else []
        elif path == "pulls/7/files":
            result = [{"filename": "gradle/libs.versions.toml"}]
        elif path.startswith("actions/workflows/regen-verification-metadata.yml/runs?"):
            result = copy.deepcopy(getattr(self, "preparation_runs", [self.writer]))
        elif path.startswith("pulls?"):
            result = [copy.deepcopy(self.pr)]
        elif path.startswith("actions/runs/") and "/attempts/" in path and path.endswith("/jobs"):
            result = copy.deepcopy(getattr(self, "ci_jobs", []))
        elif path.startswith("actions/runs/") and path.endswith("/jobs?filter=all"):
            result = copy.deepcopy(getattr(self, "ci_jobs", []))
        else:
            raise AssertionError(f"Unexpected pages {path}")
        return result

    def download(self, path, limit):
        content = self.downloads[path]
        if len(content) > limit:
            raise ValueError("fake download too large")
        return content

    def post_json(self, path, body):
        self.posts.append((path, body))
        return {"workflow_run_id": 100, "run_url": "https://api.github.com/repos/owner/repo/actions/runs/100",
                "html_url": self.writer["html_url"]}

    def add_receipt_artifact(self, receipt):
        self.current_receipt_zip = archive({"approval-receipt.json": json.dumps(receipt).encode()})
        self.current_receipt_artifact = {
            "id": 71, "name": f"graph-approval-receipt-{receipt['source_run_id']}-{receipt['source_run_attempt']}",
            "size_in_bytes": len(self.current_receipt_zip), "expired": False,
        }
        self.downloads["actions/artifacts/71/zip"] = self.current_receipt_zip


class DependencyGraphHandoffTest(unittest.TestCase):
    def setUp(self):
        github_env = patch.dict(os.environ, {"GITHUB_SHA": "c" * 40}, clear=True)
        github_env.start()
        self.addCleanup(github_env.stop)
        self.api = FakeApi()

    def test_approval_rejects_mismatched_environment_sha(self):
        with patch.dict(os.environ, {"GITHUB_SHA": "d" * 40}):
            with self.assertRaisesRegex(ValueError, "trusted default-branch"):
                MODULE.current_handoff(self.api, 90)

    def test_review_binds_run_attempt_pr_repository_head_and_digest(self):
        run, pr, review, _ = MODULE.read_review(self.api, 41, 1)
        self.assertEqual(7, pr["number"])
        self.assertEqual(SHA, run["head_sha"])
        self.assertEqual(["g:b"], review["added"])
        self.api.runs[41]["run_attempt"] = 2
        with self.assertRaisesRegex(ValueError, "selected successful graph-review attempt"):
            MODULE.read_review(self.api, 41, 1)

    def test_review_rejects_wrong_head_repository_pr_and_digest(self):
        variations = ("head", "repository", "pr", "digest", "manifest")
        for variation in variations:
            api = FakeApi()
            if variation == "head":
                api.pr["head"]["sha"] = "d" * 40
            elif variation == "repository":
                api.review_run["head_repository"]["full_name"] = "attacker/repo"
            elif variation == "pr":
                api.pr["number"] = 8
            elif variation == "digest":
                api.review["added"] = ["g:evil"]
                api.review_zip = archive({
                    "graph-review.json": json.dumps(api.review).encode(),
                    "graph-review.md": b"review artifact",
                    "manifest.json": json.dumps(api.review_manifest).encode(),
                })
                api.review_artifact["size_in_bytes"] = len(api.review_zip)
                api.downloads["actions/artifacts/70/zip"] = api.review_zip
            elif variation == "manifest":
                api.review_manifest["repository_id"] = 999
                api.review_zip = archive({
                    "graph-review.json": json.dumps(api.review).encode(),
                    "graph-review.md": b"review artifact",
                    "manifest.json": json.dumps(api.review_manifest).encode(),
                })
                api.review_artifact["size_in_bytes"] = len(api.review_zip)
                api.downloads["actions/artifacts/70/zip"] = api.review_zip
            with self.subTest(variation=variation), self.assertRaises(ValueError):
                MODULE.read_review(api, 41, 1)

    def test_source_attempt_and_pr_head_races_during_artifact_download_are_rejected(self):
        api = FakeApi()
        original = api.download
        def rerun_source(path, limit):
            content = original(path, limit)
            api.review_run["run_attempt"] = 2
            return content
        api.download = rerun_source
        with self.assertRaisesRegex(ValueError, "attempt or PR head changed"):
            MODULE.read_review(api, 41, 1)
        api = FakeApi()
        original = api.download
        def move_head(path, limit):
            content = original(path, limit)
            api.pr["head"]["sha"] = OUTPUT_SHA
            return content
        api.download = move_head
        with self.assertRaisesRegex(ValueError, "attempt or PR head changed"):
            MODULE.read_review(api, 41, 1)

    def test_review_rejects_expired_duplicate_oversized_and_extra_artifact_files(self):
        api = FakeApi()
        api.review_artifact["expired"] = True
        with self.assertRaisesRegex(ValueError, "expired"):
            MODULE.read_review(api, 41, 1)
        api = FakeApi()
        api.pages = lambda path, key=None, **kwargs: ([api.review_artifact, dict(api.review_artifact)]
            if path == "actions/runs/41/artifacts" else [])
        with self.assertRaisesRegex(ValueError, "missing or duplicate"):
            MODULE.read_review(api, 41, 1)
        api = FakeApi()
        api.review_artifact["size_in_bytes"] = MODULE.MAX_REVIEW_ARCHIVE + 1
        with self.assertRaisesRegex(ValueError, "size limit"):
            MODULE.read_review(api, 41, 1)
        api = FakeApi()
        api.review_zip = archive({"graph-review.json": b"{}", "graph-review.md": b"", "manifest.json": b"{}", "extra": b"x"})
        api.review_artifact["size_in_bytes"] = len(api.review_zip)
        api.downloads["actions/artifacts/70/zip"] = api.review_zip
        with self.assertRaisesRegex(ValueError, "unexpected or duplicate"):
            MODULE.read_review(api, 41, 1)
        api = FakeApi()
        api.review_zip = archive({
            "graph-review.json": b"x" * (MODULE.MAX_REVIEW_JSON + 1),
            "graph-review.md": b"review artifact", "manifest.json": json.dumps(api.review_manifest).encode(),
        })
        api.review_artifact["size_in_bytes"] = len(api.review_zip)
        api.downloads["actions/artifacts/70/zip"] = api.review_zip
        with self.assertRaisesRegex(ValueError, "unsafe or oversized"):
            MODULE.read_review(api, 41, 1)

    def test_untrusted_actor_and_missing_default_branch_context_cannot_approve(self):
        self.api.permission = {"permission": "write", "role_name": "write"}
        with self.assertRaisesRegex(ValueError, "admin or maintain"):
            MODULE.prepare_approval(self.api, 41, 1, 90, Path("/tmp/receipt.json"))
        self.api.permission = {"permission": "write", "role_name": "maintain"}
        self.api.handoff["head_branch"] = BRANCH
        with self.assertRaisesRegex(ValueError, "trusted default-branch"):
            MODULE.prepare_approval(self.api, 41, 1, 90, Path("/tmp/receipt.json"))

    def test_approval_workflow_rerun_cannot_create_a_receipt(self):
        self.api.handoff["run_attempt"] = 2
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "trusted default-branch"):
                MODULE.prepare_approval(self.api, 41, 1, 90, Path(directory) / "receipt.json")
            self.assertFalse(Path(directory, "receipt.json").exists())

    def test_consumed_receipt_is_uploaded_before_exact_writer_dispatch(self):
        with tempfile.TemporaryDirectory() as directory:
            receipt = MODULE.prepare_approval(self.api, 41, 1, 90, Path(directory) / "approval-receipt.json")
        self.api.add_receipt_artifact(receipt)
        writer, pr, digest = MODULE.dispatch_write(self.api, 41, 1, 90)
        self.assertEqual(100, writer["id"])
        self.assertEqual(7, pr["number"])
        self.assertEqual(self.api.review["digest"], digest)
        path, body = self.api.posts[0]
        self.assertEqual("actions/workflows/regen-verification-metadata.yml/dispatches", path)
        self.assertTrue(body["return_run_details"])
        self.assertEqual(BRANCH, body["ref"])
        self.assertEqual(SHA, body["inputs"]["approved_head_sha"])
        self.assertEqual(self.api.review["digest"], body["inputs"]["approved_graph_digest"])
        self.assertEqual(3, len(body["inputs"]))

    def test_exact_writer_run_identity_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            receipt = MODULE.prepare_approval(self.api, 41, 1, 90, Path(directory) / "approval-receipt.json")
        self.api.add_receipt_artifact(receipt)
        self.api.writer["head_sha"] = OUTPUT_SHA
        with self.assertRaisesRegex(ValueError, "expected exact-head write run"):
            MODULE.dispatch_write(self.api, 41, 1, 90)

    def test_source_rerun_after_receipt_upload_prevents_dispatch(self):
        api = FakeApi()
        with tempfile.TemporaryDirectory() as directory:
            receipt = MODULE.prepare_approval(api, 41, 1, 90, Path(directory) / "approval-receipt.json")
        api.add_receipt_artifact(receipt)
        original = api.download
        def rerun_after_receipt(path, limit):
            content = original(path, limit)
            if path == "actions/artifacts/71/zip":
                api.review_run["run_attempt"] = 2
            return content
        api.download = rerun_after_receipt
        with self.assertRaisesRegex(ValueError, "selected successful graph-review attempt"):
            MODULE.dispatch_write(api, 41, 1, 90)
        self.assertEqual([], api.posts)

    def test_prior_receipt_blocks_replay_and_ambiguous_history_fails_closed(self):
        api = FakeApi()
        api.prior_runs = [{
            "id": 80, "path": ".github/workflows/approve-dependency-graph.yml", "head_branch": "master",
            "run_attempt": 1,
            "event": "workflow_dispatch", "triggering_actor": {"login": "previous-maintainer"},
            "display_title": "Dependency graph approval | source_run=41 | source_attempt=1 | approved=true",
        }]
        receipt = MODULE.receipt_for(api.prior_runs[0], "previous-maintainer", 41, 1,
                                     api.pr, api.review, REPOSITORY)
        zip_bytes = archive({"approval-receipt.json": json.dumps(receipt).encode()})
        api.prior_artifacts[80] = [{"id": 88, "name": "graph-approval-receipt-41-1",
                                    "size_in_bytes": len(zip_bytes), "expired": False}]
        api.downloads["actions/artifacts/88/zip"] = zip_bytes
        with self.assertRaisesRegex(ValueError, "already has an approval receipt"):
            with tempfile.TemporaryDirectory() as directory:
                MODULE.prepare_approval(api, 41, 1, 90, Path(directory) / "receipt.json")
        api = FakeApi()
        api.prior_runs = [{
            "id": 80, "path": ".github/workflows/approve-dependency-graph.yml", "head_branch": "master",
            "run_attempt": 1, "event": "workflow_dispatch",
            "triggering_actor": {"login": "previous-maintainer"},
            "display_title": "Dependency graph approval | source_run=41 | source_attempt=1 | approved=true",
        }]
        api.prior_artifacts[80] = []
        with self.assertRaisesRegex(ValueError, "no unique consumed receipt"):
            with tempfile.TemporaryDirectory() as directory:
                MODULE.prepare_approval(api, 41, 1, 90, Path(directory) / "receipt.json")

    def test_later_queued_approval_does_not_block_current_handoff(self):
        api = FakeApi()
        api.prior_runs = [{
            "id": 91, "path": ".github/workflows/approve-dependency-graph.yml", "head_branch": "master",
            "run_attempt": 1, "event": "workflow_dispatch",
            "display_title": "Dependency graph approval | source_run=41 | source_attempt=1 | approved=true",
        }]
        self.assertFalse(MODULE.prior_receipt(api, api.review_run, 41, 1, 90, api.pr, api.review))

    def test_bounded_truncated_history_fails_closed(self):
        api = FakeApi()
        api.pages = lambda path, key=None, **kwargs: [dict(id=index) for index in range(MODULE.MAX_HISTORY_ITEMS + 1)]
        with self.assertRaisesRegex(ValueError, "exceeds its safe scan limit"):
            MODULE.prior_receipt(api, api.review_run, 41, 1, 90, api.pr, api.review)

    def test_review_and_approval_workflow_contracts_keep_permissions_separate(self):
        root = Path(__file__).resolve().parents[2]
        review_workflow = (root / ".github/workflows/dependency-graph-review.yml").read_text()
        approval_workflow = (root / ".github/workflows/approve-dependency-graph.yml").read_text()
        writer_workflow = (root / ".github/workflows/regen-verification-metadata.yml").read_text()
        handoff_script = (root / ".github/scripts/dependency-graph-handoff.py").read_text()
        self.assertIn("pull_request:", review_workflow)
        self.assertIn("--review-only", review_workflow)
        self.assertIn("cache-disabled: true", review_workflow)
        self.assertIn("dependency-verification-preparation-${{ github.repository_id }}", review_workflow)
        self.assertIn('cp app/dependencies/releaseRuntimeClasspath.txt "$RUNNER_TEMP/guard-before.txt"', review_workflow)
        self.assertNotIn("BASE_SHA:", review_workflow)
        self.assertNotIn("$HEAD_SHA:app/dependencies/releaseRuntimeClasspath.txt", review_workflow)
        self.assertNotIn("contents: write", review_workflow)
        self.assertNotIn("VERIFICATION_METADATA_DEPLOY_KEY", review_workflow)
        self.assertIn("default: false", approval_workflow)
        self.assertIn("source_run_attempt:", approval_workflow)
        self.assertIn("actions: write", approval_workflow)
        self.assertNotIn("contents: write", approval_workflow)
        self.assertIn("Checkout trusted default-branch helpers", approval_workflow)
        self.assertLess(approval_workflow.index("Consume this exact review attempt before dispatch"),
                       approval_workflow.index("Dispatch approved writer"))
        self.assertIn('"return_run_details": True', handoff_script)
        self.assertNotIn("approved_review_run_id:", writer_workflow)
        self.assertNotIn("approved_review_attempt:", writer_workflow)
        self.assertIn("!inputs.approved_head_sha && !inputs.approved_graph_digest", writer_workflow)
        self.assertIn("source_run_id={int(os.environ['GITHUB_RUN_ID'])}", review_workflow)
        self.assertIn("source_run_attempt={int(os.environ['GITHUB_RUN_ATTEMPT'])}", review_workflow)
        self.assertIn("Approve Dependency Graph** on the default branch", handoff_script)
        self.assertIn("Fallback: manually dispatch the writer", handoff_script)
        self.assertIn("dispatch a new run of this workflow", approval_workflow)
        self.assertNotIn("rerun this workflow", approval_workflow)

    def test_writer_run_title_keeps_legacy_identity(self):
        for title, expected in (
            (f"Dependency preparation | mode=write | ref={BRANCH}", "write"),
            (f"Dependency preparation | mode=candidate | ref={BRANCH}", "candidate"),
        ):
            with self.subTest(title=title):
                self.assertEqual(expected, MODULE.RECONCILE.preparation_mode({
                    "display_title": title, "head_branch": BRANCH,
                }))

    def test_dispatch_waits_for_exact_output_head_ci_and_rejects_wrong_head(self):
        api = FakeApi()
        api.writer.update({"status": "completed", "conclusion": "success"})
        api.runs[100] = api.writer
        output_pr = copy.deepcopy(api.pr)
        output_pr["head"]["sha"] = OUTPUT_SHA
        api.pr = output_pr
        api.ci_run = {
            "id": 200, "run_number": 200, "run_attempt": 1,
            "path": ".github/workflows/ci.yml", "event": "pull_request",
            "head_sha": OUTPUT_SHA, "head_branch": BRANCH,
            "head_repository": {"full_name": REPOSITORY},
            "pull_requests": [{"number": 7}], "status": "completed", "conclusion": "success",
            "html_url": "https://github.com/owner/repo/actions/runs/200",
        }
        api.runs[200] = api.ci_run
        with patch.object(MODULE, "successful_ci_inventory", return_value=True), \
             patch.object(MODULE, "ci_matches_preparation", return_value=True), \
             patch.object(MODULE.RECONCILE, "result_archive", return_value=({
                 "output_sha": OUTPUT_SHA, "graph_digest": api.review["digest"],
             }, {})):
            with patch.object(MODULE, "dispatch_write", return_value=(api.writer, {
                **output_pr, "head": {**output_pr["head"], "sha": SHA}
            }, api.review["digest"])):
                self.assertEqual(0, MODULE.dispatch_and_follow(api, 41, 1, 90))
        api.pr["head"]["sha"] = "d" * 40
        with patch.object(MODULE, "successful_ci_inventory", return_value=True), \
             patch.object(MODULE, "ci_matches_preparation", return_value=True), \
             patch.object(MODULE, "dispatch_write", return_value=(api.writer, {
            **output_pr, "head": {**output_pr["head"], "sha": SHA}
        }, api.review["digest"])), \
             patch.object(MODULE.RECONCILE, "result_archive", return_value=({
                 "output_sha": OUTPUT_SHA, "graph_digest": api.review["digest"],
             }, {})):
            with self.assertRaisesRegex(ValueError, "live PR head is not its validated output"):
                MODULE.dispatch_and_follow(api, 41, 1, 90)

    def test_only_exact_output_ci_conclusion_can_complete_handoff(self):
        api = FakeApi()
        pr = copy.deepcopy(api.pr)
        pr["head"]["sha"] = OUTPUT_SHA
        api.pr = pr
        api.ci_run = {"id": 200, "path": ".github/workflows/ci.yml", "event": "pull_request",
                      "head_sha": SHA, "head_branch": BRANCH,
                      "head_repository": {"full_name": REPOSITORY}, "status": "completed", "conclusion": "success"}
        api.runs[200] = api.ci_run
        self.assertIsNone(MODULE.output_ci(api, pr, OUTPUT_SHA))
        api.ci_run["head_sha"] = OUTPUT_SHA
        api.ci_run["conclusion"] = "failure"
        api.writer.update(status="completed", conclusion="success")
        writer = api.writer
        with patch.object(MODULE, "dispatch_write", return_value=(writer, {**pr, "head": {**pr["head"], "sha": SHA}}, api.review["digest"])), \
             patch.object(MODULE.RECONCILE, "result_archive", return_value=({
                 "output_sha": OUTPUT_SHA, "graph_digest": api.review["digest"],
             }, {})), \
             patch.object(MODULE.PREPARATION, "refresh_failed_ci_gate", return_value=False):
            with self.assertRaisesRegex(ValueError, "required CI for output head failed"):
                MODULE.dispatch_and_follow(api, 41, 1, 90)

    def test_failed_write_is_reported_without_waiting_for_ci(self):
        api = FakeApi()
        api.writer.update(status="completed", conclusion="failure")
        with patch.object(MODULE, "dispatch_write", return_value=(api.writer, api.pr, api.review["digest"])):
            with self.assertRaisesRegex(ValueError, "writer run .* completed failure"):
                MODULE.dispatch_and_follow(api, 41, 1, 90)

    def test_no_diff_output_is_accepted_when_digest_and_exact_ci_match(self):
        api = FakeApi()
        api.writer.update(status="completed", conclusion="success")
        api.runs[100] = api.writer
        api.ci_run = {
            "id": 200, "run_number": 200, "run_attempt": 1,
            "path": ".github/workflows/ci.yml", "event": "pull_request",
            "head_sha": SHA, "head_branch": BRANCH,
            "head_repository": {"full_name": REPOSITORY},
            "pull_requests": [{"number": 7}], "status": "completed", "conclusion": "success",
            "html_url": "https://github.com/owner/repo/actions/runs/200",
        }
        api.runs[200] = api.ci_run
        with patch.object(MODULE, "dispatch_write", return_value=(api.writer, api.pr, api.review["digest"])), \
             patch.object(MODULE.RECONCILE, "result_archive", return_value=({
                 "output_sha": SHA, "graph_digest": api.review["digest"],
             }, {})), \
             patch.object(MODULE, "successful_ci_inventory", return_value=True), \
             patch.object(MODULE, "ci_matches_preparation", return_value=True):
            self.assertEqual(0, MODULE.dispatch_and_follow(api, 41, 1, 90))

    def test_writer_graph_digest_must_match_approved_review(self):
        api = FakeApi()
        api.writer.update(status="completed", conclusion="success")
        api.runs[100] = api.writer
        with patch.object(MODULE, "dispatch_write", return_value=(api.writer, api.pr, api.review["digest"])), \
             patch.object(MODULE.RECONCILE, "result_archive", return_value=({
                 "output_sha": SHA, "graph_digest": "0" * 64,
             }, {})):
            with self.assertRaisesRegex(ValueError, "does not match the approved review"):
                MODULE.dispatch_and_follow(api, 41, 1, 90)

    def test_skipped_ci_gate_cannot_pass_inventory(self):
        api = FakeApi()
        api.ci_run = {"id": 200, "run_attempt": 1}
        api.ci_jobs = [
            {"id": index, "name": name, "conclusion": "skipped" if name == "CI Gate" else "success"}
            for index, name in enumerate(MODULE.RECONCILE.CI_REQUIRED_JOBS, start=1)
        ]
        self.assertFalse(MODULE.successful_ci_inventory(api, api.ci_run))

    def test_output_head_ci_uses_current_authoritative_preparation(self):
        api = FakeApi()
        output_pr = copy.deepcopy(api.pr)
        output_pr["head"]["sha"] = OUTPUT_SHA
        api.pr = output_pr
        api.writer.update({"run_number": 100, "head_sha": SHA, "status": "completed",
                           "conclusion": "success", "head_repository": {"full_name": REPOSITORY}})
        api.runs[100] = api.writer
        latest_preparation = {
            "id": 101, "run_number": 101, "run_attempt": 1,
            "path": ".github/workflows/regen-verification-metadata.yml", "event": "workflow_dispatch",
            "head_branch": BRANCH, "head_sha": OUTPUT_SHA,
            "head_repository": {"full_name": REPOSITORY},
            "display_title": "Dependency preparation | mode=write | ref=" + BRANCH,
            "status": "completed", "conclusion": "success",
        }
        api.runs[101] = latest_preparation
        api.preparation_runs = [latest_preparation]
        api.ci_run = {
            "id": 200, "run_number": 200, "run_attempt": 1,
            "path": ".github/workflows/ci.yml", "event": "pull_request",
            "head_sha": OUTPUT_SHA, "head_branch": BRANCH,
            "head_repository": {"full_name": REPOSITORY},
            "pull_requests": [{"number": 7}], "status": "completed", "conclusion": "success",
        }
        api.runs[200] = api.ci_run
        api.ci_jobs = [
            {"id": index, "name": name, "conclusion": "success"}
            for index, name in enumerate(MODULE.RECONCILE.CI_REQUIRED_JOBS, start=1)
        ]
        gate = next(job for job in api.ci_jobs if job["name"] == "CI Gate")
        gate["id"] = 300
        api.downloads["actions/jobs/300/logs"] = (
            f"DEPENDENCY_PREPARATION_RESULT run_id=101 attempt=1 head={OUTPUT_SHA}\n".encode()
        )
        result = {"source_sha": SHA, "output_sha": OUTPUT_SHA,
                  "graph_digest": api.review["digest"], "kind": "full"}
        def current_result(_api, run, _pr, *, allow_reuse=True):
            self.assertIn(run["id"], {100, 101})
            return result, {}

        api.writer = api.writer
        api.runs[100] = api.writer
        with patch.object(MODULE, "dispatch_write", return_value=(api.writer, {
            **output_pr, "head": {**output_pr["head"], "sha": SHA}
        }, api.review["digest"])), \
             patch.object(MODULE.RECONCILE, "result_archive", side_effect=current_result):
            self.assertEqual(0, MODULE.dispatch_and_follow(api, 41, 1, 90))


if __name__ == "__main__":
    unittest.main()
