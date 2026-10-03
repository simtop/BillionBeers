#!/usr/bin/env python3
"""Create/reuse the bounded authoritative result consumed by CI Gate and reporting."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECONCILE_SPEC = importlib.util.spec_from_file_location(
    "reconcile_ci_report", Path(__file__).with_name("reconcile-ci-report.py")
)
RECONCILE = importlib.util.module_from_spec(RECONCILE_SPEC)
RECONCILE_SPEC.loader.exec_module(RECONCILE)
MERGE_SPEC = importlib.util.spec_from_file_location(
    "merge_platforms", Path(__file__).with_name("merge-verification-metadata-platforms.py")
)
MERGE = importlib.util.module_from_spec(MERGE_SPEC)
MERGE_SPEC.loader.exec_module(MERGE)
PREPARATION = RECONCILE.PREPARATION
LANES = {"linux", "web", "managed", "apple"}


def same_bot_pr(pr: dict, repository: str, branch: str, source_sha: str) -> bool:
    return (
        pr.get("state") == "open"
        and pr.get("user", {}).get("login") == "dependabot[bot]"
        and pr.get("head", {}).get("sha") == source_sha
        and pr.get("head", {}).get("ref") == branch
        and (pr.get("head", {}).get("repo") or {}).get("full_name") == repository
        and pr.get("base", {}).get("repo", {}).get("full_name") == repository
    )


def find_pr(api, repository: str, branch: str, source_sha: str, number: int = 0) -> dict | None:
    owner = repository.split("/", 1)[0]
    query = RECONCILE.urllib.parse.urlencode({"state": "open", "head": f"{owner}:{branch}"})
    candidates = api.pages(f"pulls?{query}")
    matches = [pr for pr in candidates if same_bot_pr(pr, repository, branch, source_sha)]
    if len(matches) != 1 or (number and matches[0].get("number") != number):
        return None
    return matches[0]


def output(values: dict[str, object]) -> None:
    target = os.environ.get("GITHUB_OUTPUT")
    if not target:
        return
    with open(target, "a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"{key}={value}\n")


def find_reuse(api, pr: dict, current_run_id: int, source_sha: str, wait_seconds: int) -> dict | None:
    deadline = time.monotonic() + wait_seconds
    while True:
        selection = RECONCILE.preparation_current(api, pr, exclude_run_id=current_run_id)
        result = selection.get("result") if selection else None
        if selection and selection.get("authoritative_valid") and result and result.get("output_sha") == source_sha:
            if result.get("kind") == "full":
                return selection
            if result.get("kind") == "reuse" and result.get("origin_result"):
                # Flatten the reference so every reuse points directly at retained full lane evidence.
                selection = dict(selection)
                selection["reuse_origin"] = result["origin_run"]
                selection["reuse_result"] = result["origin_result"]
                return selection
        # The push that launched this run can precede completion of the original run by a few
        # seconds. Wait only while such an earlier write is actually still running; if evidence
        # remains unavailable, this caller falls through to the full cold lanes.
        parent_finalizing = bool(selection and any(
            run.get("status") != "completed" and run.get("head_sha") != source_sha
            for run in selection.get("stale_runs", [])
        ))
        remaining = deadline - time.monotonic()
        if not parent_finalizing or remaining <= 0:
            return None
        time.sleep(min(10, remaining))


def find_reuse_main(args) -> int:
    api = RECONCILE.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
    result = {"reuse": "false", "origin_run_id": "", "origin_attempt": "", "pr_number": "0"}
    try:
        pr = find_pr(api, api.repository, args.branch, args.source_sha, args.pr_number)
        result["pr_number"] = str(pr["number"]) if pr else "0"
        if pr:
            selection = find_reuse(api, pr, args.current_run_id, args.source_sha, args.wait_seconds)
            if selection:
                origin = selection.get("reuse_origin", selection["run"])
                result.update({"reuse": "true", "origin_run_id": str(origin["id"]),
                               "origin_attempt": str(origin["run_attempt"])})
    except RECONCILE.API_ERRORS + (UnicodeDecodeError, json.JSONDecodeError, TypeError) as error:
        print(f"::notice::Could not validate reusable preparation ({type(error).__name__}); executing all cold lanes.")
    output(result)
    print("Validated prior preparation found; skipping cold lanes." if result["reuse"] == "true"
          else "No reusable authoritative result; this run must execute the cold lanes.")
    return 0


def graph_identity(evidence: Path, source_sha: str, run_id: int, attempt: int, branch: str) -> tuple[str, dict]:
    ledgers = MERGE.validate_evidence(evidence, source_sha, str(run_id), str(attempt), branch, "write")
    del ledgers
    lane_results = {}
    graph_digest = None
    for lane in sorted(LANES):
        folder = evidence / lane
        manifest_path = folder / "manifest.json"
        manifest_bytes = manifest_path.read_bytes()
        manifest = json.loads(manifest_bytes)
        review_path = folder / "graph-review.json"
        review = json.loads(review_path.read_text(encoding="utf-8"))
        normalized = PREPARATION.graph_review(
            "\n".join(review["before"]), "\n".join(review["after"]), source_sha
        )
        if normalized != review:
            raise ValueError(f"{lane} graph-review digest or contents do not validate")
        if manifest.get("mode") != "write" or manifest.get("graph_mode") != "reference":
            raise ValueError(f"{lane} manifest is not a write/reference lane")
        if manifest.get("graph_digest") != review["digest"]:
            raise ValueError(f"{lane} graph identity disagrees with its review file")
        if graph_digest is None:
            graph_digest = review["digest"]
        elif graph_digest != review["digest"]:
            raise ValueError("platform lanes disagree on the reviewed graph")
        lane_results[lane] = {
            "lane": lane,
            "run_id": run_id,
            "run_attempt": attempt,
            "head_sha": source_sha,
            "mode": "write",
            "graph_mode": "reference",
            "graph_digest": graph_digest,
            "conclusion": "success",
            "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        }
    if graph_digest is None:
        raise ValueError("no platform graph identity was recorded")
    return graph_digest, lane_results


def parent_shas(output_sha: str) -> list[str]:
    raw = subprocess.check_output(["git", "show", "-s", "--format=%P", output_sha], cwd=ROOT, text=True)
    return raw.split()


def build_result(api, args) -> tuple[dict, dict]:
    repository = api.repository
    run = api.get(f"actions/runs/{args.current_run_id}")
    if (run.get("path") != f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}"
            or run.get("head_sha") != args.source_sha or run.get("head_branch") != args.branch
            or RECONCILE.preparation_mode(run) != "write"):
        raise ValueError("current run does not match the immutable write intent")
    pr = find_pr(api, repository, args.branch, args.source_sha, args.pr_number)
    if not pr:
        raise LookupError("no unique open same-repository Dependabot PR matches this write source")
    if args.output_sha != args.source_sha and parent_shas(args.output_sha) != [args.source_sha]:
        raise ValueError("generated output is not a direct child of the preparation source")
    identity = {
        "schema": 1,
        "repository": repository,
        "repository_id": pr["base"]["repo"]["id"],
        "pr_number": pr["number"],
        "branch": args.branch,
        "source_sha": args.source_sha,
        "output_sha": args.output_sha,
        "mode": "write",
        "run_id": run["id"],
        "run_attempt": run["run_attempt"],
    }
    if args.origin_run_id:
        origin = api.get(f"actions/runs/{args.origin_run_id}")
        if origin.get("run_attempt") != args.origin_attempt:
            raise ValueError("reuse origin attempt changed")
        if (origin.get("status") != "completed" or origin.get("conclusion") != "success"
                or origin.get("path") != f".github/workflows/{RECONCILE.PREPARATION.WORKFLOW}"
                or RECONCILE.preparation_mode(origin) != "write"):
            raise ValueError("reuse origin did not complete as a successful write")
        origin_result, _ = RECONCILE.result_archive(api, origin, pr, allow_reuse=False)
        if (origin_result.get("kind") != "full" or origin_result.get("output_sha") != args.source_sha
                or origin.get("run_number", 0) >= run.get("run_number", 0)):
            raise ValueError("reuse origin does not validate this exact output head")
        identity.update({"kind": "reuse", "graph_mode": origin_result["graph_mode"],
                         "graph_digest": origin_result["graph_digest"],
                         "origin_run_id": origin["id"], "origin_run_attempt": origin["run_attempt"]})
    else:
        graph_digest, lanes = graph_identity(args.evidence, args.source_sha, run["id"],
                                             run["run_attempt"], args.branch)
        identity.update({"kind": "full", "graph_mode": "reference", "graph_digest": graph_digest,
                         "lanes": lanes})
    return identity, pr


def write_result_main(args) -> int:
    api = RECONCILE.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
    try:
        result, pr = build_result(api, args)
    except LookupError as error:
        output({"pr_number": "0"})
        print(f"::notice::{error}. The write may complete, but it is not PR-authoritative.")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    output({"pr_number": str(pr["number"])})
    print(f"Prepared authoritative {result['kind']} identity for PR #{pr['number']}.")
    return 0


def verify_pr_head_main(args) -> int:
    if not args.pr_number:
        return 0
    api = RECONCILE.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
    pr = api.get(f"pulls/{args.pr_number}")
    repo = api.repository
    if (pr.get("state") != "open" or pr.get("user", {}).get("login") != "dependabot[bot]"
            or pr.get("head", {}).get("ref") != args.branch
            or pr.get("head", {}).get("sha") != args.expected_sha
            or (pr.get("head", {}).get("repo") or {}).get("full_name") != repo
            or pr.get("base", {}).get("repo", {}).get("full_name") != repo):
        raise ValueError("PR identity or head changed during preparation; refusing to push/accept the result")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    reuse = commands.add_parser("find-reuse")
    reuse.add_argument("--branch", required=True)
    reuse.add_argument("--source-sha", required=True)
    reuse.add_argument("--pr-number", type=int, default=0)
    reuse.add_argument("--current-run-id", type=int, required=True)
    reuse.add_argument("--wait-seconds", type=int, default=120)
    write = commands.add_parser("write-result")
    write.add_argument("--branch", required=True)
    write.add_argument("--source-sha", required=True)
    write.add_argument("--output-sha", required=True)
    write.add_argument("--pr-number", type=int, default=0)
    write.add_argument("--current-run-id", type=int, required=True)
    write.add_argument("--evidence", type=Path, default=Path(".platform-evidence"))
    write.add_argument("--output", type=Path, required=True)
    write.add_argument("--origin-run-id", type=int, default=0)
    write.add_argument("--origin-attempt", type=int, default=0)
    verify = commands.add_parser("verify-pr-head")
    verify.add_argument("--pr-number", type=int, required=True)
    verify.add_argument("--branch", required=True)
    verify.add_argument("--expected-sha", required=True)
    args = parser.parse_args()
    try:
        if args.command == "find-reuse":
            return find_reuse_main(args)
        if args.command == "write-result":
            return write_result_main(args)
        return verify_pr_head_main(args)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
