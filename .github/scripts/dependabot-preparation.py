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
LANE_JOB_NAMES = {
    f"Regenerate verification-metadata.xml ({lane})"
    for lane in ("linux", "web", "managed", "apple")
}
LEGACY_LANE_JOB_NAME = "Regenerate verification-metadata.xml"
CI_GATE_NAME = "CI Gate"
PREPARATION_GATE_FAILURE = "Dependency preparation has not succeeded on this PR head."


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
    if shutdown and terminated and step in {"Regenerate verification metadata", "Resolve and record platform lane"}:
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
    escape_code = lambda value: (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                               .replace("`", "&#96;").replace("\r", "").replace("\n", "&#10;"))
    lines = ["### Dependency graph review", "", f"Head: `{review['head_sha']}`",
             f"Graph digest: `{review['digest']}`", "", "Added coordinates:", ""]
    lines += [f"- `{escape_code(item)}`" for item in review["added"]] or ["None."]
    lines += ["", "Removed coordinates:", ""]
    lines += [f"- `{escape_code(item)}`" for item in review["removed"]] or ["None."]
    # Describe workflow inputs without constructing shell commands from branch names.
    lines += ["", "Review graph-review.json from Dependency Graph Review on this exact PR head.",
              "Dispatch Approve Dependency Graph on the default branch with that review's source run ID",
              "and exact attempt; set approve_graph=true after reviewing the graph.",
              "Manual fallback: dispatch Regenerate Verification Metadata in write mode on this branch",
              "with approved_head_sha and approved_graph_digest set to the values above.",
              "Approval expires when either the PR head or proposed graph changes.", ""]
    (directory / "graph-review.md").write_text("\n".join(lines))


def eligible_pr(pr: dict, run: dict, repository: str) -> bool:
    return (pr.get("state") == "open" and pr.get("user", {}).get("login") == "dependabot[bot]"
            and (pr.get("head", {}).get("repo") or {}).get("full_name") == repository
            and (run.get("head_repository") or {}).get("full_name") == repository
            and pr.get("base", {}).get("repo", {}).get("full_name") == repository
            and pr.get("head", {}).get("sha") == run.get("head_sha")
            and pr.get("head", {}).get("ref") == run.get("head_branch"))


def unstarted_cancelled_companion(job: dict) -> bool:
    """Accept fail-fast matrix rows only when they never started executing."""
    if (job.get("name") not in LANE_JOB_NAMES or job.get("conclusion") != "cancelled"
            or "started_at" not in job or job.get("started_at") or not isinstance(job.get("steps"), list)):
        return False
    return all("started_at" in step and not step.get("started_at")
               and step.get("conclusion") in {None, "skipped"} for step in job["steps"])


def single_retryable_lane_failure(jobs: list[dict]) -> dict | None:
    """Return the sole failed lane only when every companion is success/skip or unstarted cancellation."""
    failed_lanes = [job for job in jobs if job.get("conclusion") == "failure"]
    cancelled = [job for job in jobs if job.get("conclusion") == "cancelled"]
    unexpected = [job for job in jobs if job.get("conclusion") not in
                  {"success", "skipped", "failure", "cancelled"}]
    if (len(failed_lanes) != 1 or unexpected
            or failed_lanes[0].get("name") not in LANE_JOB_NAMES | {LEGACY_LANE_JOB_NAME}
            or any(not unstarted_cancelled_companion(job) for job in cancelled)):
        return None
    return failed_lanes[0]


def paths_require_preparation(paths: list[str]) -> bool:
    return any(path in {"gradle/libs.versions.toml", "gradle.properties", "settings.gradle.kts", "build.gradle.kts"}
               or path.startswith(("gradle/wrapper/", "build-logic/"))
               or path.endswith("/build.gradle.kts") for path in paths)


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
    lane_failure = single_retryable_lane_failure(jobs)
    if not lane_failure:
        return False
    failed_steps = reconcile.RENDER.failed_steps(lane_failure)
    if len(failed_steps) != 1 or failed_steps[0]["name"] not in {
        "Regenerate verification metadata", "Resolve and record platform lane"
    }:
        return False
    log = api.download(f"actions/jobs/{lane_failure['id']}/logs", reconcile.MAX_LOG).decode("utf-8", errors="replace")
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


def refresh_failed_ci_gate(api, preparation_run_id: int, reconcile) -> bool:
    """Rerun only CI Gate when the selected write authority differs from its verdict."""
    source = api.get(f"actions/runs/{preparation_run_id}")
    if (source.get("path") != f".github/workflows/{WORKFLOW}"
            or source.get("event") not in {"pull_request", "workflow_dispatch"}
            or reconcile.preparation_mode(source) != "write"
            or (source.get("head_repository") or {}).get("full_name") != api.repository):
        return False
    owner = api.repository.split("/", 1)[0]
    query = reconcile.urllib.parse.urlencode({"state": "open", "head": f"{owner}:{source['head_branch']}"})
    candidates = api.pages(f"pulls?{query}")
    matches = [pr for pr in candidates if (
        pr.get("state") == "open" and pr.get("user", {}).get("login") == "dependabot[bot]"
        and pr.get("head", {}).get("ref") == source.get("head_branch")
        and (pr.get("head", {}).get("repo") or {}).get("full_name") == api.repository
        and pr.get("base", {}).get("repo", {}).get("full_name") == api.repository)]
    if len(matches) != 1:
        return False
    number = matches[0]["number"]

    def current_preparation():
        pr = api.get(f"pulls/{number}")
        preparation = reconcile.preparation_current(api, pr)
        result = (preparation or {}).get("result") or {}
        if (pr.get("state") != "open" or not preparation or not preparation.get("id")):
            return None
        if preparation.get("authoritative_valid") and result.get("output_sha") != pr.get("head", {}).get("sha"):
            return None
        identity = (preparation["id"], preparation.get("run_attempt", 1),
                    result.get("output_sha", pr.get("head", {}).get("sha")))
        return pr, preparation, identity

    selected = current_preparation()
    if not selected:
        return False
    pr, preparation, selected_identity = selected
    live_pr, ci = reconcile.current(api, number)
    if (live_pr.get("head", {}).get("sha") != pr["head"]["sha"] or not ci
            or ci.get("status") != "completed" or ci.get("conclusion") not in {"success", "failure"}
            or ci.get("head_sha") != pr["head"]["sha"]):
        return False

    def gate_state(run, *, read_log):
        jobs = api.pages(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", "jobs")
        if run.get("run_attempt", 1) > 1:
            history = api.pages(f"actions/runs/{run['id']}/jobs?filter=all", "jobs")
            jobs = reconcile.effective_jobs(jobs, history, run["run_attempt"])
        if {job.get("name") for job in jobs} != reconcile.CI_REQUIRED_JOBS:
            return None
        failures = [job for job in jobs if job.get("conclusion") not in {"success", "skipped"}]
        gates = [job for job in jobs if job.get("name") == CI_GATE_NAME]
        if (len(gates) != 1 or len(failures) > 1
                or any(job.get("name") != CI_GATE_NAME and job.get("conclusion") not in {"success", "skipped"}
                       for job in jobs)):
            return None
        gate = gates[0]
        if gate.get("conclusion") not in {"success", "failure"}:
            return None
        if gate.get("conclusion") == "failure":
            failed_steps = reconcile.RENDER.failed_steps(gate)
            if len(failed_steps) != 1 or failed_steps[0].get("name") != "Verify dependency preparation result":
                return None
        marker = None
        gate_log = ""
        if read_log:
            gate_log = api.download(f"actions/jobs/{gate['id']}/logs", reconcile.MAX_LOG).decode("utf-8", errors="replace")
            lines = output_lines(gate_log)
            marker = next((line for line in lines if re.fullmatch(
                r"DEPENDENCY_PREPARATION_RESULT run_id=\d+ attempt=\d+ head=[0-9a-f]{40}", line)), None)
            if gate.get("conclusion") == "failure" and PREPARATION_GATE_FAILURE not in "\n".join(lines):
                return None
        return gate, marker

    initial_gate = gate_state(ci, read_log=True)
    if not initial_gate:
        return False
    gate, consumed = initial_gate
    marker_match = re.fullmatch(r"DEPENDENCY_PREPARATION_RESULT run_id=(\d+) attempt=(\d+) head=([0-9a-f]{40})", consumed or "")
    same_generation = bool(preparation.get("authoritative_valid") and marker_match
                           and (int(marker_match.group(1)), int(marker_match.group(2))) == selected_identity[:2]
                           and marker_match.group(3) == pr["head"]["sha"])
    if ci.get("conclusion") == "success" and same_generation:
        return False
    # Only the Gate is retried; preserving every other successful job keeps real CI evidence intact.
    latest = ci
    final_gate = gate_state(latest, read_log=False)
    if not final_gate or final_gate[0].get("id") != gate.get("id"):
        return False
    selected = current_preparation()
    if not selected or selected[2] != selected_identity:
        return False
    live_pr, latest = reconcile.current(api, number)
    if (live_pr.get("head", {}).get("sha") != pr["head"]["sha"] or not latest
            or (latest.get("id"), latest.get("run_attempt")) != (ci.get("id"), ci.get("run_attempt"))
            or latest.get("status") != "completed" or latest.get("conclusion") != ci.get("conclusion")):
        return False
    api.post(f"actions/jobs/{gate['id']}/rerun")
    print(f"Requested CI Gate refresh for PR #{number}, head {pr['head']['sha']}")
    return True


def require_prepared_head(api, number: int, reconcile, wait_seconds: int = 0, expected_head: str = "") -> bool:
    pr = api.get(f"pulls/{number}")
    expected_head = expected_head or pr["head"]["sha"]
    if pr.get("user", {}).get("login") != "dependabot[bot]":
        return True
    paths = [item["filename"] for item in api.pages(f"pulls/{number}/files")]
    required = paths_require_preparation(paths)
    if not required:
        return True  # Actions-only updates never resolve Gradle artifacts.
    deadline = time.monotonic() + wait_seconds
    classified_attempt = None
    retry_pending = False
    first_observation = True
    evidence_deadlines = {}
    while True:
        if not first_observation and wait_seconds > 0 and time.monotonic() >= deadline:
            break
        first_observation = False
        pr = api.get(f"pulls/{number}")
        if pr.get("state") != "open" or pr["head"]["sha"] != expected_head:
            print("::error::PR head changed or closed while awaiting dependency preparation.")
            return False
        run = reconcile.preparation_current(api, pr)
        evidence_deadline = None
        if run is not None and run.get("status") == "completed":
            identity = (run.get("id"), run.get("run_attempt"))
            if identity != classified_attempt:
                retry_pending = (wait_seconds > 0 and run.get("run_attempt") == 1
                                 and run.get("conclusion") == "failure"
                                 and reconcile.preparation_snapshot(api, run).get("category") == "operational_termination")
                classified_attempt = identity
            if run.get("authoritative_valid"):
                retry_pending = False
            elif (wait_seconds > 0 and run.get("conclusion") == "failure"
                  and run.get("run_conclusion") == "success"
                  and run.get("category") == "evidence_unavailable"):
                evidence_deadline = evidence_deadlines.setdefault(
                    identity, min(deadline, time.monotonic() + 60))
                retry_pending = time.monotonic() < evidence_deadline
            if not retry_pending:
                break
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        print("Waiting for dependency preparation on this immutable head…", flush=True)
        sleep_for = min(30, remaining)
        if evidence_deadline is not None:
            evidence_remaining = evidence_deadline - time.monotonic()
            if evidence_remaining <= 0:
                break
            sleep_for = min(sleep_for, evidence_remaining)
        time.sleep(sleep_for)
    result = (run or {}).get("result") or {}
    if (run is None or run.get("status") != "completed" or run.get("conclusion") != "success"
            or not run.get("authoritative_valid") or result.get("output_sha") != expected_head):
        if run and run.get("status") == "completed" and run.get("conclusion") == "failure":
            diagnosis = reconcile.preparation_snapshot(api, run)
            if (diagnosis or {}).get("category") == "manual_investigation_required":
                print(f"::error::{PREPARATION_GATE_FAILURE} A verified runner shutdown did not meet the automatic recovery conditions; manual investigation is required.")
                return False
        print("::error::Dependency preparation has not succeeded on this PR head. Open its preparation run.")
        return False
    # The Gate can only pass if the exact selection and PR head still agree after evidence
    # downloads and any waiting for a runner-shutdown retry.
    live_run = reconcile.preparation_current(api, pr)
    live_result = (live_run or {}).get("result") or {}
    live_pr = api.get(f"pulls/{number}")
    if (live_pr.get("state") != "open" or live_pr.get("head", {}).get("sha") != expected_head
            or not live_run or not live_run.get("authoritative_valid")
            or live_result.get("output_sha") != expected_head
            or (live_run.get("id"), live_run.get("run_attempt")) != (run.get("id"), run.get("run_attempt"))):
        print("::error::PR head or authoritative preparation changed before CI Gate success.")
        return False
    print(f"DEPENDENCY_PREPARATION_RESULT run_id={live_run['id']} attempt={live_run['run_attempt']} head={expected_head}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--recover-run", type=int)
    parser.add_argument("--refresh-ci-gate", type=int)
    parser.add_argument("--require-prepared-head", type=int)
    parser.add_argument("--expected-head", default="")
    parser.add_argument("--wait-seconds", type=int, default=0)
    parser.add_argument("--before", type=Path)
    parser.add_argument("--after", type=Path)
    parser.add_argument("--head-sha")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--approved-head", default="")
    parser.add_argument("--approved-digest", default="")
    parser.add_argument("--review-only", action="store_true",
                        help="publish a review artifact without rejecting coordinate changes")
    args = parser.parse_args()
    if args.recover_run or args.refresh_ci_gate or args.require_prepared_head:
        spec = importlib.util.spec_from_file_location("reconcile", Path(__file__).with_name("reconcile-ci-report.py"))
        reconcile = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(reconcile)
        api = reconcile.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
        if args.recover_run:
            recover(api, args.recover_run, reconcile)
            return 0
        if args.refresh_ci_gate:
            refresh_failed_ci_gate(api, args.refresh_ci_gate, reconcile)
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
    if classification == "coordinate_change" and not args.review_only:
        print("::error::This bump adds or removes dependency coordinates, not just versions.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
