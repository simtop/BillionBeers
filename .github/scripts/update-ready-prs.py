#!/usr/bin/env python3
"""Merge the latest base into same-repository PRs that opted into GitHub auto-merge."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import urlencode


def api(endpoint: str, *, paginate: bool = False):
    command = ["gh", "api", endpoint]
    if paginate:
        command += ["--paginate", "--slurp"]
    return json.loads(subprocess.check_output(command, text=True))


def dispatch_ci(repo: str) -> None:
    subprocess.run(
        ["gh", "api", f"repos/{repo}/actions/workflows/ci.yml/dispatches",
         "--method", "POST", "-f", "ref=master"],
        check=True, capture_output=True, text=True,
    )


def eligible(pr: dict, repo: str, base: str) -> bool:
    return (
        pr.get("state") == "open"
        and not pr.get("draft", True)
        and pr.get("auto_merge") is not None
        and pr.get("base", {}).get("ref") == base
        and (pr.get("base", {}).get("repo") or {}).get("full_name") == repo
        and (pr.get("head", {}).get("repo") or {}).get("full_name") == repo
        and pr.get("head", {}).get("ref") != base
    )


class Updater:
    def __init__(self, repo: str, base: str, directory: Path, *, dry_run: bool = False):
        self.repo = repo
        self.base = base
        self.directory = directory
        self.dry_run = dry_run
        self.messages: list[str] = []

    def git(self, *args: str, check: bool = True) -> subprocess.CompletedProcess:
        # Commit-tree does not check out PR files or execute their hooks/build scripts.
        return subprocess.run(
            ["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false",
             "-c", "user.name=github-actions[bot]",
             "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com", *args],
            cwd=self.directory, check=check, capture_output=True, text=True,
        )

    def report(self, number: int, message: str) -> None:
        line = f"PR #{number}: {message}"
        self.messages.append(line)
        print(line, flush=True)

    def busy(self, sha: str) -> bool:
        query = urlencode({"head_sha": sha, "per_page": 100})
        pages = api(f"repos/{self.repo}/actions/runs?{query}", paginate=True)
        # Include preparation and queued runs, not just the aggregate CI check.
        return any(run.get("status") != "completed"
                   for page in pages for run in page["workflow_runs"])

    def ensure_master_ci(self) -> None:
        if self.base != "master":
            print(f"Skipping master CI fallback for non-master base {self.base}.", flush=True)
            return
        if self.dry_run:
            print("Dry run: skipping master CI fallback.", flush=True)
            return

        # GITHUB_TOKEN pushes do not start workflows. Check the exact checked-out master SHA so
        # the updater can restore the post-merge CI run that those pushes may have suppressed.
        master_sha = self.git("rev-parse", "HEAD").stdout.strip()
        query = urlencode({"head_sha": master_sha, "per_page": 100})
        pages = api(
            f"repos/{self.repo}/actions/workflows/ci.yml/runs?{query}", paginate=True
        )
        if any(run.get("head_sha") == master_sha
               and run.get("head_branch") == "master"
               and run.get("event") in {"push", "workflow_dispatch"}
               for page in pages for run in page.get("workflow_runs", [])):
            print(f"CI already exists for master {master_sha}.", flush=True)
            return

        self.git("fetch", "--no-tags", "origin", "refs/heads/master")
        current_master = self.git("rev-parse", "FETCH_HEAD").stdout.strip()
        if current_master != master_sha:
            print(
                f"Skipping CI dispatch: master advanced from {master_sha} to {current_master}.",
                flush=True,
            )
            return
        dispatch_ci(self.repo)
        print(f"Dispatched CI for master {master_sha}.", flush=True)

    def update(self, pr: dict) -> None:
        number = pr["number"]
        if not eligible(pr, self.repo, self.base):
            return
        branch = pr["head"]["ref"]
        head = pr["head"]["sha"]
        for ref in (branch, self.base):
            self.git("check-ref-format", f"refs/heads/{ref}")

        # Fetch exact branch tips without checking out or executing any PR content.
        self.git("fetch", "--no-tags", "origin", f"refs/heads/{self.base}")
        base_sha = self.git("rev-parse", "FETCH_HEAD").stdout.strip()
        self.git("fetch", "--no-tags", "origin", f"refs/heads/{branch}")
        fetched_head = self.git("rev-parse", "FETCH_HEAD").stdout.strip()
        if fetched_head != head:
            self.report(number, "head changed; retry on a later run")
            return
        ancestor = self.git("merge-base", "--is-ancestor", base_sha, head, check=False)
        if ancestor.returncode == 0:
            self.report(number, "already up to date")
            return
        if ancestor.returncode != 1:
            raise RuntimeError("could not determine whether the branch is up to date")
        if self.busy(head):
            self.report(number, "CI or preparation is still active; retry after it finishes")
            return

        merged = self.git("merge-tree", "--write-tree", head, base_sha, check=False)
        if merged.returncode == 1:
            self.report(number, "merge conflicts; resolve the update manually")
            return
        if merged.returncode != 0:
            raise RuntimeError("could not calculate the merge tree")
        tree = merged.stdout.splitlines()[0]

        # Auto-merge can be disabled or the branch closed/edited while we calculate the merge.
        current = api(f"repos/{self.repo}/pulls/{number}")
        if (not eligible(current, self.repo, self.base)
                or current["head"]["sha"] != head or current["head"]["ref"] != branch):
            self.report(number, "eligibility or head changed; leaving the branch untouched")
            return
        if self.busy(head):
            self.report(number, "a workflow started; retry after it finishes")
            return
        if self.dry_run:
            self.report(number, "would merge the latest base; dry run did not push")
            return

        commit = self.git("commit-tree", tree, "-p", head, "-p", base_sha,
                          "-m", f"Merge {self.base} into PR #{number}").stdout.strip()
        # The commit retains head as its first parent, so an accepted push is fast-forward.
        # An explicit lease also rejects concurrent rewinds/deletions, not just new commits.
        pushed = self.git("push", f"--force-with-lease=refs/heads/{branch}:{head}",
                          "origin", f"{commit}:refs/heads/{branch}", check=False)
        if pushed.returncode != 0:
            raise RuntimeError("push rejected; the branch may have changed or disallowed writes")
        self.report(number, "base merged; waiting for fresh required CI and GitHub auto-merge")

    def run(self) -> int:
        self.ensure_master_ci()
        query = urlencode({"state": "open", "base": self.base, "per_page": 100})
        pages = api(f"repos/{self.repo}/pulls?{query}", paginate=True)
        failures = 0
        for page in pages:
            for pr in page:
                try:
                    self.update(pr)
                except (subprocess.CalledProcessError, RuntimeError, KeyError) as error:
                    failures += 1
                    # Do not expose command output from credential-bearing Git operations.
                    message = str(error) if isinstance(error, RuntimeError) else "Git/API operation failed"
                    self.report(pr.get("number", 0), f"{message}; inspect and retry")
        if not self.messages:
            print("No eligible same-repository PRs with auto-merge enabled.")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as output:
                output.write("### Automatic PR branch updates\n\n")
                output.write("\n".join(f"- {message}" for message in self.messages)
                             or "No eligible same-repository PRs with auto-merge enabled.")
                output.write("\n")
        return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--base", default="master")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    return Updater(args.repo, args.base, Path.cwd(), dry_run=args.dry_run).run()


if __name__ == "__main__":
    raise SystemExit(main())
