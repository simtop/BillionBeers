#!/usr/bin/env python3
"""Review dependency graph changes and recover one interrupted preparation run.

GitHub evidence is data only. Recovery executes trusted default-branch code and
never checks out a PR or accepts a graph/checksum change.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import time
from pathlib import Path

WORKFLOW = "regen-verification-metadata.yml"


def output_lines(log: str) -> list[str]:
    """Remove timestamps/ANSI and the echoed 'Run' script, not its actual output."""
    lines = []
    echoed = False
    for raw in log.splitlines():
        line = re.sub(r"\x1b\[[0-9;]*m", "", raw)
        line = re.sub(r"^.*?\d{4}-\d\d-\d\dT\S+\s*", "", line).strip()
        if line.startswith("##[group]Run "):
            echoed = True
        elif line.startswith("##[endgroup]"):
            echoed = False
        elif not echoed:
            lines.append(line)
    return lines


def classify(log: str, step: str = "") -> str:
    lines = output_lines(log)
    actual = "\n".join(lines)
    if "Regeneration changed checksums" in actual or re.search(
        r"checksum[^\n]*(?:mismatch|failed|expected)|has been compromised", actual, re.I
    ):
        return "checksum_mismatch"
    if "Dependency-verification configuration changed" in actual:
        return "verification_policy_change"
    if any(line.startswith("##[error]This bump adds or removes dependency coordinates") for line in lines):
        return "coordinate_change"
    if "following files had format violations" in actual:
        return "formatting"
    if "conflicting requirements" in actual or "satisfies the version constraints" in actual:
        return "resolution_conflict"
    if "Dependency verification failed" in actual or "not listed in the verification metadata" in actual:
        return "verification_failure"
    # --continue can reach a shutdown after a genuine task failure: that must not
    # be retried automatically. Exit 143 echoed in the script is not evidence.
    if any(re.match(r"> Task .* FAILED(?:\s|$)", line) for line in lines) or "BUILD FAILED" in actual:
        return "build_test_or_policy_failure"
    shutdown = any(line.startswith("##[error]The runner has received a shutdown signal") for line in lines)
    terminated = any(re.fullmatch(r"##\[error\]Process completed with exit code 143\.", line) for line in lines)
    if shutdown and terminated and step == "Regenerate verification metadata":
        return "operational_termination"
    return "build_test_or_policy_failure"


def graph_review(before: str, after: str, head_sha: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", head_sha):
        raise ValueError("Review requires a full immutable head SHA")
    previous = sorted(set(before.splitlines()))
    proposed = sorted(set(after.splitlines()))
    previous = [line for line in previous if line.strip()]
    proposed = [line for line in proposed if line.strip()]
    coords = lambda entries: {entry.rsplit(":", 1)[0] for entry in entries}
    review = {"head_sha": head_sha, "before": previous, "after": proposed,
              "added": sorted(coords(proposed) - coords(previous)),
              "removed": sorted(coords(previous) - coords(proposed))}
    review["digest"] = hashlib.sha256(json.dumps(review, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return review


def check_graph(review: dict, approved_head: str, approved_digest: str) -> str:
    if approved_head or approved_digest:
        if approved_head != review["head_sha"] or approved_digest != review["digest"]:
            raise ValueError("Approval does not match this head and exact proposed graph")
        return "reviewed_coordinate_change"
    if review["added"] or review["removed"]:
        return "coordinate_change"
    return "version_only"


def write_review(review: dict, directory: Path, branch: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "graph-review.json").write_text(json.dumps(review, indent=2) + "\n")
    lines = ["### Dependency graph review", "", f"Head: `{review['head_sha']}`",
             f"Graph digest: `{review['digest']}`", "", "Added coordinates:", ""]
    lines += [f"- `{item}`" for item in review["added"]] or ["None."]
    lines += ["", "Removed coordinates:", ""]
    lines += [f"- `{item}`" for item in review["removed"]] or ["None."]
    # The workflow UI takes branch/head/digest as inputs. Avoid constructing shell
    # commands from branch names; they are not trusted executable text.
    lines += ["", "Download the dependency-preparation artifact and review graph-review.json.",
              "To accept this exact graph, dispatch Regenerate Verification Metadata in write mode",
              "on this branch with approved_head_sha and approved_graph_digest set to the values above.",
              "Approval expires when either the PR head or proposed graph changes.", ""]
    (directory / "graph-review.md").write_text("\n".join(lines))


def eligible_pr(pr: dict, run: dict, repository: str) -> bool:
    return (pr.get("state") == "open" and pr.get("user", {}).get("login") == "dependabot[bot]"
            and (pr.get("head", {}).get("repo") or {}).get("full_name") == repository
            and (run.get("head_repository") or {}).get("full_name") == repository
            and pr.get("base", {}).get("repo", {}).get("full_name") == repository
            and pr.get("head", {}).get("sha") == run.get("head_sha")
            and pr.get("head", {}).get("ref") == run.get("head_branch"))


def recover(api, run_id: int, reconcile) -> bool:
    run = api.get(f"actions/runs/{run_id}")
    if (run.get("path") != f".github/workflows/{WORKFLOW}" or run.get("event") != "pull_request"
            or run.get("status") != "completed" or run.get("conclusion") != "failure"
            or run.get("run_attempt") != 1):
        return False
    number = reconcile.resolve_pr(api, run)
    if not number:
        return False
    pr = api.get(f"pulls/{number}")
    if not eligible_pr(pr, run, api.repository):
        return False
    jobs = api.pages(f"actions/runs/{run_id}/attempts/1/jobs", "jobs")
    failures = [job for job in jobs if job.get("conclusion") not in {"success", "skipped"}]
    if len(failures) != 1 or failures[0].get("name") != "Regenerate verification-metadata.xml":
        return False
    failed_steps = reconcile.RENDER.failed_steps(failures[0])
    if len(failed_steps) != 1 or failed_steps[0]["name"] != "Regenerate verification metadata":
        return False
    log = api.download(f"actions/jobs/{failures[0]['id']}/logs", reconcile.MAX_LOG).decode("utf-8", errors="replace")
    if classify(log, failed_steps[0]["name"]) != "operational_termination":
        return False
    # Reconcile again after evidence downloads. A newer run (including dispatch),
    # rebased head or previous rerun makes this completion event obsolete.
    live_pr = api.get(f"pulls/{number}")
    live = api.get(f"actions/runs/{run_id}")
    runs = api.pages(f"actions/workflows/{WORKFLOW}/runs?head_sha={run['head_sha']}", "workflow_runs")
    if (not eligible_pr(live_pr, run, api.repository) or live.get("run_attempt") != 1
            or live.get("status") != "completed" or live.get("conclusion") != "failure"
            or any(item["run_number"] > run["run_number"] for item in runs)):
        return False
    api.post(f"actions/runs/{run_id}/rerun")
    print(f"Requested the single operational retry for run {run_id}, head {run['head_sha']}")
    return True


def require_prepared_head(api, number: int, reconcile, wait_seconds: int = 0, expected_head: str = "") -> bool:
    pr = api.get(f"pulls/{number}")
    expected_head = expected_head or pr["head"]["sha"]
    if pr.get("user", {}).get("login") != "dependabot[bot]":
        return True
    paths = [item["filename"] for item in api.pages(f"pulls/{number}/files")]
    required = any(path in {"gradle/libs.versions.toml", "gradle.properties", "settings.gradle.kts", "build.gradle.kts"}
                   or path.startswith(("gradle/wrapper/", "build-logic/")) or path.endswith("/build.gradle.kts")
                   for path in paths)
    if not required:
        return True  # Actions-only updates never resolve Gradle artifacts.
    deadline = time.monotonic() + wait_seconds
    classified_attempt = None
    retry_pending = False
    while True:
        pr = api.get(f"pulls/{number}")
        if pr.get("state") != "open" or pr["head"]["sha"] != expected_head:
            print("::error::PR head changed or closed while awaiting dependency preparation.")
            return False
        run = reconcile.preparation_current(api, pr)
        if run is not None and run.get("status") == "completed":
            identity = (run.get("id"), run.get("run_attempt"))
            if identity != classified_attempt:
                retry_pending = (wait_seconds > 0 and run.get("run_attempt") == 1
                                 and run.get("conclusion") == "failure"
                                 and reconcile.preparation_snapshot(api, run).get("category") == "operational_termination")
                classified_attempt = identity
            if not retry_pending:
                break
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        print("Waiting for dependency preparation on this immutable head…", flush=True)
        time.sleep(min(30, remaining))
    if run is None or run.get("status") != "completed" or run.get("conclusion") != "success":
        print("::error::Dependency preparation has not succeeded on this PR head. Open its preparation run.")
        return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--recover-run", type=int)
    parser.add_argument("--require-prepared-head", type=int)
    parser.add_argument("--expected-head", default="")
    parser.add_argument("--wait-seconds", type=int, default=0)
    parser.add_argument("--before", type=Path)
    parser.add_argument("--after", type=Path)
    parser.add_argument("--head-sha")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--approved-head", default="")
    parser.add_argument("--approved-digest", default="")
    args = parser.parse_args()
    if args.recover_run or args.require_prepared_head:
        spec = importlib.util.spec_from_file_location("reconcile", Path(__file__).with_name("reconcile-ci-report.py"))
        reconcile = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(reconcile)
        api = reconcile.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
        if args.recover_run:
            recover(api, args.recover_run, reconcile)
            return 0
        return 0 if require_prepared_head(api, args.require_prepared_head, reconcile,
                                         args.wait_seconds, args.expected_head) else 1
    if not all((args.before, args.after, args.head_sha, args.output)):
        parser.error("graph review requires --before, --after, --head-sha and --output")
    review = graph_review(args.before.read_text(), args.after.read_text(), args.head_sha)
    write_review(review, args.output, os.environ.get("GITHUB_REF_NAME", ""))
    try:
        classification = check_graph(review, args.approved_head, args.approved_digest)
    except ValueError as error:
        print(f"::error::{error}")
        return 1
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"classification={classification}\n")
    print((args.output / "graph-review.md").read_text())
    if classification == "coordinate_change":
        print("::error::This bump adds or removes dependency coordinates, not just versions.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
