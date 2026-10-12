#!/usr/bin/env python3
"""Exercise branch-update eligibility, conflicts and races using real isolated Git remotes."""

from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("update-ready-prs.py")
spec = importlib.util.spec_from_file_location("update_ready_prs", SCRIPT)
MODULE = importlib.util.module_from_spec(spec)
spec.loader.exec_module(MODULE)
REPO = "example/repository"
ROOT = SCRIPT.parents[2]


def git(directory: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", "-c", "user.name=Contributor", "-c", "user.email=contributor@example.invalid",
         *args], cwd=directory, text=True, stderr=subprocess.DEVNULL,
    ).strip()


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.remote = root / "remote.git"
        self.author = root / "author"
        self.worker = root / "worker"
        git(root, "init", "--bare", "--initial-branch=master", str(self.remote))
        git(self.remote, "config", "receive.denyNonFastForwards", "true")
        git(root, "clone", str(self.remote), str(self.author))
        (self.author / "shared.txt").write_text("original\n")
        self.initial = self.commit("initial")
        git(self.author, "push", "origin", "master")
        git(self.author, "switch", "-c", "feature")
        (self.author / "feature.txt").write_text("feature\n")
        self.head = self.commit("feature")
        git(self.author, "push", "origin", "feature")
        git(self.author, "switch", "master")
        (self.author / "base.txt").write_text("new base\n")
        self.base = self.commit("base advanced")
        git(self.author, "push", "origin", "master")
        git(root, "clone", str(self.remote), str(self.worker))
        self.pr = {
            "number": 7, "state": "open", "draft": False, "auto_merge": {"merge_method": "squash"},
            "base": {"ref": "master", "repo": {"full_name": REPO}},
            "head": {"ref": "feature", "sha": self.head, "repo": {"full_name": REPO}},
        }
        self.active = False
        self.reads = []
        self.updater = MODULE.Updater(REPO, "master", self.worker)
        self.api_patch = patch.object(MODULE, "api", side_effect=self.api)
        self.api_patch.start()
        self.addCleanup(self.api_patch.stop)

    def commit(self, message: str) -> str:
        git(self.author, "add", ".")
        git(self.author, "commit", "-m", message)
        return git(self.author, "rev-parse", "HEAD")

    def api(self, endpoint: str, *, paginate: bool = False):
        self.reads.append(endpoint)
        if "/workflows/ci.yml/runs?" in endpoint:
            return [{"workflow_runs": [{
                "head_sha": self.base, "head_branch": "master", "event": "push",
                "status": "completed", "conclusion": "success",
            }]}]
        if "/actions/runs?" in endpoint:
            return [{"workflow_runs": [{"status": "queued" if self.active else "completed"}]}]
        if endpoint.endswith("/pulls/7"):
            return copy.deepcopy(self.pr)
        if "/pulls?" in endpoint:
            return [[copy.deepcopy(self.pr)]]
        raise AssertionError(f"unexpected API request: {endpoint}")

    def remote_head(self) -> str:
        return git(self.remote, "rev-parse", "refs/heads/feature")

    def test_merge_preserves_head_and_base_without_checking_out_pr(self):
        original_checkout = git(self.worker, "rev-parse", "HEAD")
        self.updater.update(copy.deepcopy(self.pr))
        merged = self.remote_head()
        self.assertEqual([self.head, self.base], git(self.remote, "show", "-s", "--format=%P", merged).split())
        self.assertEqual("feature", git(self.remote, "show", f"{merged}:feature.txt"))
        self.assertEqual("new base", git(self.remote, "show", f"{merged}:base.txt"))
        self.assertEqual(original_checkout, git(self.worker, "rev-parse", "HEAD"))
        self.assertFalse((self.worker / "feature.txt").exists())
        self.assertEqual("github-actions[bot]", git(self.remote, "show", "-s", "--format=%an", merged))
        self.pr["head"]["sha"] = merged
        self.updater.update(copy.deepcopy(self.pr))
        self.assertEqual(merged, self.remote_head())
        self.assertIn("already up to date", self.updater.messages[-1])

    def test_noneligible_prs_never_fetch_or_write(self):
        for change in (
            {"auto_merge": None}, {"draft": True}, {"state": "closed"},
            {"head": {**self.pr["head"], "repo": {"full_name": "fork/repository"}}},
            {"head": {**self.pr["head"], "repo": None}},
            {"head": {**self.pr["head"], "ref": "master"}},
            {"base": {**self.pr["base"], "ref": "other"}},
        ):
            with self.subTest(change=change), patch.object(self.updater, "git") as command:
                self.updater.update(self.pr | change)
                command.assert_not_called()
        self.assertEqual(self.head, self.remote_head())

    def test_active_preparation_defers_update(self):
        self.active = True
        self.updater.update(self.pr)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("still active", self.updater.messages[-1])

    def test_dry_run_does_not_push(self):
        self.updater.dry_run = True
        self.updater.update(self.pr)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("dry run", self.updater.messages[-1])

    def test_auto_merge_disabled_during_calculation_stops_update(self):
        snapshot = copy.deepcopy(self.pr)
        self.pr["auto_merge"] = None
        self.updater.update(snapshot)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("eligibility or head changed", self.updater.messages[-1])

    def test_head_changed_before_fetch_stops_update(self):
        git(self.author, "switch", "feature")
        (self.author / "concurrent.txt").write_text("contributor change\n")
        concurrent = self.commit("concurrent change")
        git(self.author, "push", "origin", "feature")
        self.updater.update(self.pr)
        self.assertEqual(concurrent, self.remote_head())
        self.assertIn("head changed", self.updater.messages[-1])

    def test_head_changed_during_calculation_stops_update(self):
        snapshot = copy.deepcopy(self.pr)
        self.pr["head"]["sha"] = self.base
        self.updater.update(snapshot)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("eligibility or head changed", self.updater.messages[-1])

    def test_workflow_started_during_calculation_stops_update(self):
        with patch.object(self.updater, "busy", side_effect=[False, True]):
            self.updater.update(self.pr)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("a workflow started", self.updater.messages[-1])

    def test_concurrent_push_is_not_overwritten(self):
        real_git = self.updater.git
        concurrent = None

        def racing_git(*args, **kwargs):
            nonlocal concurrent
            if args[0] == "push":
                git(self.author, "switch", "feature")
                (self.author / "concurrent.txt").write_text("contributor change\n")
                concurrent = self.commit("concurrent change")
                git(self.author, "push", "origin", "feature")
            return real_git(*args, **kwargs)

        with patch.object(self.updater, "git", side_effect=racing_git):
            with self.assertRaisesRegex(RuntimeError, "push rejected"):
                self.updater.update(self.pr)
        self.assertEqual(concurrent, self.remote_head())

    def test_conflicts_leave_remote_untouched(self):
        git(self.author, "switch", "feature")
        (self.author / "shared.txt").write_text("feature edit\n")
        self.head = self.commit("feature conflict")
        git(self.author, "push", "origin", "feature")
        self.pr["head"]["sha"] = self.head
        git(self.author, "switch", "master")
        (self.author / "shared.txt").write_text("base edit\n")
        self.commit("base conflict")
        git(self.author, "push", "origin", "master")
        self.updater.update(self.pr)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("merge conflicts", self.updater.messages[-1])

    def test_concurrent_rewind_is_not_resurrected(self):
        real_git = self.updater.git

        def racing_git(*args, **kwargs):
            if args[0] == "push":
                # Simulate an intentional history edit on a branch that permits it.
                git(self.remote, "config", "receive.denyNonFastForwards", "false")
                git(self.author, "push", "--force", "origin", f"{self.initial}:refs/heads/feature")
            return real_git(*args, **kwargs)

        with patch.object(self.updater, "git", side_effect=racing_git):
            with self.assertRaisesRegex(RuntimeError, "push rejected"):
                self.updater.update(self.pr)
        self.assertEqual(self.initial, self.remote_head())

    def test_concurrent_deletion_is_not_recreated(self):
        real_git = self.updater.git

        def racing_git(*args, **kwargs):
            if args[0] == "push":
                git(self.author, "push", "origin", ":refs/heads/feature")
            return real_git(*args, **kwargs)

        with patch.object(self.updater, "git", side_effect=racing_git):
            with self.assertRaisesRegex(RuntimeError, "push rejected"):
                self.updater.update(self.pr)
        self.assertEqual("", git(self.author, "ls-remote", "origin", "refs/heads/feature"))

    def test_unreadable_actions_fails_without_writing(self):
        with patch.object(MODULE, "api", side_effect=RuntimeError("unavailable")):
            with self.assertRaisesRegex(RuntimeError, "unavailable"):
                self.updater.update(self.pr)
        self.assertEqual(self.head, self.remote_head())

    def test_paginated_pr_inventory_and_error_status(self):
        with patch.object(self.updater, "ensure_master_ci"), patch.object(
            self.updater, "update", side_effect=RuntimeError("test failure")
        ):
            self.assertEqual(1, self.updater.run())
        self.assertIn("state=open", self.reads[0])
        self.assertEqual(self.head, self.remote_head())

    def test_master_ci_is_not_dispatched_when_exact_sha_has_a_run(self):
        with patch.object(MODULE, "dispatch_ci") as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_not_called()
        self.assertTrue(any("/workflows/ci.yml/runs?head_sha=" in item for item in self.reads))

    def test_ci_completion_for_a_dispatched_master_run_does_not_recurse(self):
        with patch.object(MODULE, "api", return_value=[{"workflow_runs": [
            {"head_sha": self.base, "head_branch": "master",
             "event": "workflow_dispatch", "status": "completed"}
        ]}]), patch.object(MODULE, "dispatch_ci") as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_not_called()

    def test_master_ci_dispatches_when_no_run_exists_for_exact_sha(self):
        with patch.object(MODULE, "api", return_value=[{"workflow_runs": [
            {"head_sha": "older-master-sha", "head_branch": "master", "event": "push"}
        ]}]), patch.object(MODULE, "dispatch_ci") as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_called_once_with(REPO)

    def test_master_ci_dispatches_when_no_runs_exist(self):
        with patch.object(MODULE, "api", return_value=[{"workflow_runs": []}]), patch.object(
            MODULE, "dispatch_ci"
        ) as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_called_once_with(REPO)

    def test_master_ci_dry_run_never_dispatches_or_queries_actions(self):
        self.updater.dry_run = True
        with patch.object(MODULE, "api") as api_call, patch.object(
            MODULE, "dispatch_ci"
        ) as dispatch:
            self.updater.ensure_master_ci()
        api_call.assert_not_called()
        dispatch.assert_not_called()

    def test_master_ci_ignores_exact_sha_run_from_a_pr(self):
        with patch.object(MODULE, "api", return_value=[{"workflow_runs": [
            {"head_sha": self.base, "head_branch": "feature", "event": "pull_request"}
        ]}]), patch.object(MODULE, "dispatch_ci") as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_called_once_with(REPO)

    def test_queued_running_and_failed_master_runs_do_not_retry(self):
        for status, conclusion in (("queued", None), ("in_progress", None),
                                  ("completed", "failure")):
            with self.subTest(status=status, conclusion=conclusion):
                with patch.object(MODULE, "api", return_value=[{"workflow_runs": [{
                    "head_sha": self.base, "head_branch": "master",
                    "event": "push", "status": status, "conclusion": conclusion,
                }]}]), patch.object(MODULE, "dispatch_ci") as dispatch:
                    self.updater.ensure_master_ci()
                dispatch.assert_not_called()

    def test_master_tip_change_skips_dispatch(self):
        original_git = self.updater.git

        def advance_before_dispatch(*args, **kwargs):
            if args == ("fetch", "--no-tags", "origin", "refs/heads/master"):
                return original_git(*args, **kwargs)
            if args == ("rev-parse", "FETCH_HEAD"):
                return subprocess.CompletedProcess(args, 0, "new-master-tip\n", "")
            return original_git(*args, **kwargs)

        with patch.object(MODULE, "api", return_value=[{"workflow_runs": []}]), patch.object(
            self.updater, "git", side_effect=advance_before_dispatch
        ), patch.object(MODULE, "dispatch_ci") as dispatch:
            self.updater.ensure_master_ci()
        dispatch.assert_not_called()

    def test_non_master_base_skips_master_ci_fallback(self):
        self.updater.base = "release"
        with patch.object(MODULE, "api") as api_call, patch.object(
            MODULE, "dispatch_ci"
        ) as dispatch:
            self.updater.ensure_master_ci()
        api_call.assert_not_called()
        dispatch.assert_not_called()

    def test_second_invocation_after_dispatch_does_not_recurse(self):
        calls = 0

        def runs(*args, **kwargs):
            nonlocal calls
            calls += 1
            if calls == 1:
                return [{"workflow_runs": []}]
            return [{"workflow_runs": [{
                "head_sha": self.base, "head_branch": "master",
                "event": "workflow_dispatch", "status": "queued",
            }]}]

        with patch.object(MODULE, "api", side_effect=runs), patch.object(
            MODULE, "dispatch_ci"
        ) as dispatch:
            self.updater.ensure_master_ci()
            self.updater.ensure_master_ci()
        dispatch.assert_called_once_with(REPO)

    def test_dispatch_uses_workflow_dispatch_on_master(self):
        with patch.object(MODULE.subprocess, "run") as run:
            MODULE.dispatch_ci(REPO)
        command = run.call_args.args[0]
        self.assertIn("repos/example/repository/actions/workflows/ci.yml/dispatches", command)
        self.assertEqual(["--method", "POST", "-f", "ref=master"], command[-4:])

    def test_every_pr_inventory_page_is_processed(self):
        pages = [[self.pr], [self.pr | {"number": 8}]]
        with patch.object(MODULE, "api", return_value=pages) as inventory:
            with patch.object(self.updater, "ensure_master_ci"), patch.object(
                self.updater, "update"
            ) as update:
                self.assertEqual(0, self.updater.run())
        inventory.assert_called_once()
        self.assertTrue(inventory.call_args.kwargs["paginate"])
        self.assertEqual([7, 8], [call.args[0]["number"] for call in update.call_args_list])


class WorkflowTests(unittest.TestCase):
    def test_trusted_checkout_and_required_test_wiring(self):
        workflow = (ROOT / ".github/workflows/update-ready-prs.yml").read_text()
        self.assertIn("if: github.ref == 'refs/heads/master'", workflow)
        self.assertIn("ref: master", workflow)
        self.assertIn("ssh-key: ${{ secrets.VERIFICATION_METADATA_DEPLOY_KEY }}", workflow)
        self.assertNotIn("pull_request_target:", workflow)
        self.assertNotIn("github.event.workflow_run.head", workflow)
        self.assertNotIn("contents: write", workflow)
        update_job = workflow.split("  update:\n", 1)[1]
        self.assertIn("actions: write", update_job)
        self.assertIn("actions: read", workflow.split("jobs:", 1)[0])
        self.assertIn("test_update_ready_prs.py", (ROOT / "Makefile").read_text())

    def test_ci_dispatch_is_master_only_and_fails_off_master(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("workflow_dispatch:", workflow)
        format_job = workflow.split("  format-check:", 1)[1].split("\n  changes:", 1)[0]
        self.assertIn("github.event_name == 'workflow_dispatch'", format_job)
        self.assertIn("github.ref != 'refs/heads/master'", format_job)
        self.assertIn("exit 1", format_job)


if __name__ == "__main__":
    unittest.main()
