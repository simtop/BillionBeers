#!/usr/bin/env python3
"""Validate an exact read-only graph review and dispatch its approved writer."""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import stat
import sys
import time
import urllib.parse
import zipfile
from pathlib import Path

import importlib.util

RECONCILE_SPEC = importlib.util.spec_from_file_location(
    "reconcile_ci_report", Path(__file__).with_name("reconcile-ci-report.py")
)
RECONCILE = importlib.util.module_from_spec(RECONCILE_SPEC)
RECONCILE_SPEC.loader.exec_module(RECONCILE)
PREPARATION = RECONCILE.PREPARATION

REVIEW_WORKFLOW = "dependency-graph-review.yml"
APPROVAL_WORKFLOW = "approve-dependency-graph.yml"
WRITER_WORKFLOW = PREPARATION.WORKFLOW
REVIEW_PREFIX = "dependency-graph-review-"
RECEIPT_PREFIX = "graph-approval-receipt-"
MAX_REVIEW_ARCHIVE = 128 * 1024
MAX_REVIEW_JSON = 64 * 1024
MAX_REVIEW_MARKDOWN = 32 * 1024
MAX_RECEIPT_ARCHIVE = 32 * 1024
MAX_RECEIPT_JSON = 16 * 1024
MAX_HISTORY_ITEMS = 100
MAX_WAIT_SECONDS = 5 * 60 * 60 + 30 * 60


def output(values: dict[str, object]) -> None:
    target = os.environ.get("GITHUB_OUTPUT")
    if not target:
        return
    with open(target, "a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"{key}={value}\n")


def valid_sha(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def positive_int(value: object, label: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{label} must be a positive integer")
    try:
        result = int(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"{label} must be a positive integer") from error
    if result <= 0 or str(result) != str(value):
        raise ValueError(f"{label} must be a positive integer")
    return result


def find_review_pr(api, run: dict) -> dict:
    candidates = run.get("pull_requests", [])
    if not candidates:
        try:
            candidates = api.pages(f"commits/{run['head_sha']}/pulls", max_items=101)
        except RECONCILE.API_ERRORS:
            candidates = []
    if not candidates:
        owner = api.repository.split("/", 1)[0]
        query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{run.get('head_branch', '')}"})
        candidates = api.pages(f"pulls?{query}", max_items=101)
    if len(candidates) > MAX_HISTORY_ITEMS:
        raise ValueError("PR lookup exceeds its safe ambiguity limit")
    matches = []
    for candidate in candidates:
        number = candidate.get("number")
        if not isinstance(number, int):
            continue
        pr = api.get(f"pulls/{number}")
        head = pr.get("head", {})
        if (pr.get("state") == "open" and pr.get("user", {}).get("login") == "dependabot[bot]"
                and pr.get("base", {}).get("repo", {}).get("full_name") == api.repository
                and (head.get("repo") or {}).get("full_name") == api.repository
                and head.get("ref") == run.get("head_branch")
                and head.get("sha") == run.get("head_sha")
                and RECONCILE.same_source(pr, run, api.repository)):
            matches.append(pr)
    unique = {pr["number"]: pr for pr in matches}
    if len(unique) != 1:
        raise ValueError("review run does not identify one open same-repository Dependabot PR")
    return next(iter(unique.values()))


def exact_zip_member(archive: zipfile.ZipFile, expected: str, maximum: int) -> bytes:
    entries = archive.infolist()
    if len(entries) != 1 or entries[0].filename != expected:
        raise ValueError(f"artifact archive must contain only {expected}")
    entry = entries[0]
    if (entry.is_dir() or entry.file_size > maximum or stat.S_ISLNK(entry.external_attr >> 16)):
        raise ValueError(f"artifact member {expected} is oversized or unsafe")
    raw = archive.read(entry)
    if len(raw) != entry.file_size:
        raise ValueError(f"artifact member {expected} has inconsistent length")
    return raw


def read_review(api, source_run_id: int, source_attempt: int) -> tuple[dict, dict, dict, dict]:
    run = api.get(f"actions/runs/{source_run_id}")
    if (run.get("path") != f".github/workflows/{REVIEW_WORKFLOW}"
            or run.get("event") != "pull_request"
            or run.get("run_attempt") != source_attempt
            or run.get("status") != "completed" or run.get("conclusion") != "success"
            or not valid_sha(run.get("head_sha"))
            or (run.get("head_repository") or {}).get("full_name") != api.repository):
        raise ValueError("source run is not the selected successful graph-review attempt")
    pr = find_review_pr(api, run)
    artifact_name = f"{REVIEW_PREFIX}{source_run_id}-{source_attempt}"
    artifact_inventory = api.pages(f"actions/runs/{source_run_id}/artifacts", "artifacts",
                                   max_items=MAX_HISTORY_ITEMS + 1)
    if len(artifact_inventory) > MAX_HISTORY_ITEMS:
        raise ValueError("source run artifact inventory exceeds its safe lookup limit")
    artifacts = [item for item in artifact_inventory if item.get("name") == artifact_name]
    if len(artifacts) != 1:
        raise ValueError("source attempt has a missing or duplicate graph-review artifact")
    artifact = artifacts[0]
    size = artifact.get("size_in_bytes")
    if (artifact.get("expired") or type(size) is not int
            or not 0 <= size <= MAX_REVIEW_ARCHIVE):
        raise ValueError("graph-review artifact is expired or exceeds its size limit")
    content = api.download(f"actions/artifacts/{artifact['id']}/zip", MAX_REVIEW_ARCHIVE)
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != 3 or set(names) != {"graph-review.json", "graph-review.md", "manifest.json"}:
            raise ValueError("graph-review artifact contains unexpected or duplicate files")
        maximums = {"graph-review.json": MAX_REVIEW_JSON,
                    "graph-review.md": MAX_REVIEW_MARKDOWN,
                    "manifest.json": MAX_REVIEW_JSON}
        for entry in entries:
            if (entry.file_size > maximums[entry.filename]
                    or stat.S_ISLNK(entry.external_attr >> 16)):
                raise ValueError(f"graph-review artifact member {entry.filename} is unsafe or oversized")
        raw_review = archive.read("graph-review.json")
        raw_markdown = archive.read("graph-review.md")
        raw_manifest = archive.read("manifest.json")
        for name, raw, maximum in (("graph-review.json", raw_review, MAX_REVIEW_JSON),
                                   ("graph-review.md", raw_markdown, MAX_REVIEW_MARKDOWN),
                                   ("manifest.json", raw_manifest, MAX_RECEIPT_JSON)):
            info = archive.getinfo(name)
            if (len(raw) != info.file_size or info.file_size > maximum
                    or stat.S_ISLNK(info.external_attr >> 16)):
                raise ValueError(f"graph-review artifact member {name} is unsafe or oversized")
    review = json.loads(raw_review.decode("utf-8"))
    manifest = json.loads(raw_manifest.decode("utf-8"))
    if not isinstance(review, dict) or not isinstance(review.get("before"), list) or not isinstance(review.get("after"), list):
        raise ValueError("graph-review JSON has malformed baseline lists")
    if any(not isinstance(line, str) for line in review["before"] + review["after"]):
        raise ValueError("graph-review JSON has non-string dependency entries")
    expected_review = PREPARATION.graph_review("\n".join(review["before"]), "\n".join(review["after"]), run["head_sha"])
    if review != expected_review:
        raise ValueError("graph-review digest or coordinate delta does not validate")
    expected_manifest = {
        "schema": 1,
        "repository": api.repository,
        "repository_id": pr.get("base", {}).get("repo", {}).get("id"),
        "pr_number": pr["number"],
        "branch": pr["head"]["ref"],
        "source_sha": run["head_sha"],
        "run_id": source_run_id,
        "run_attempt": source_attempt,
        "workflow": f".github/workflows/{REVIEW_WORKFLOW}",
        "graph_digest": review["digest"],
    }
    if manifest != expected_manifest:
        raise ValueError("graph-review manifest has a wrong repository, PR, head, run, or digest")
    latest = api.get(f"actions/runs/{source_run_id}")
    live_pr = api.get(f"pulls/{pr['number']}")
    if (latest.get("run_attempt") != source_attempt or latest.get("status") != "completed"
            or latest.get("conclusion") != "success"
            or live_pr.get("state") != "open"
            or live_pr.get("head", {}).get("sha") != run["head_sha"]
            or live_pr.get("head", {}).get("ref") != run["head_branch"]):
        raise ValueError("review attempt or PR head changed while validating evidence")
    return run, live_pr, review, artifact


def escaped_markdown(value: str) -> str:
    return (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("`", "&#96;").replace("\r", "").replace("\n", "&#10;"))


def present_review(api, source_run_id: int, source_attempt: int, output_path: Path) -> dict:
    run, pr, review, artifact = read_review(api, source_run_id, source_attempt)
    rendered = {
        "source_run_id": source_run_id,
        "source_attempt": source_attempt,
        "source_url": run.get("html_url", ""),
        "pr_number": pr["number"],
        "branch": pr["head"]["ref"],
        "source_sha": run["head_sha"],
        "graph_digest": review["digest"],
        "added": review["added"],
        "removed": review["removed"],
        "artifact_id": artifact["id"],
    }
    output_path.write_text(json.dumps(rendered, indent=2) + "\n", encoding="utf-8")
    output({"pr_number": pr["number"], "source_sha": run["head_sha"],
            "graph_digest": review["digest"], "source_attempt": source_attempt})
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        lines = ["### Dependency graph review", "", f"Source PR: #{pr['number']}",
                 f"Source run: [{source_run_id}, attempt {source_attempt}]({run.get('html_url', '')})",
                 f"Head: `{run['head_sha']}`", f"Graph digest: `{review['digest']}`", "",
                 "Added coordinates:", ""]
        lines += [f"- `{escaped_markdown(item)}`" for item in review["added"]] or ["None."]
        lines += ["", "Removed coordinates:", ""]
        lines += [f"- `{escaped_markdown(item)}`" for item in review["removed"]] or ["None."]
        lines += ["", "The source run artifact is evidence, not approval.",
                  "To approve this graph, use **Approve Dependency Graph** on the default branch, "
                  f"enter source run ID `{source_run_id}` and attempt `{source_attempt}`, review the coordinates, "
                  "and set `approve_graph=true`.",
                  "Fallback: manually dispatch the writer with `approved_head_sha` and `approved_graph_digest`.", ""]
        with open(target, "a", encoding="utf-8") as stream:
            stream.write("\n".join(lines))
    print(f"Validated graph review for PR #{pr['number']} at {run['head_sha']} ({review['digest']}).")
    return {"run": run, "pr": pr, "review": review, "artifact": artifact}


def current_handoff(api, current_run_id: int) -> tuple[dict, str]:
    run = api.get(f"actions/runs/{current_run_id}")
    repository = api.get("")
    default_branch = repository.get("default_branch")
    if (run.get("path") != f".github/workflows/{APPROVAL_WORKFLOW}"
            or run.get("event") != "workflow_dispatch"
            or run.get("head_branch") != default_branch
            or (os.environ.get("GITHUB_SHA") and run.get("head_sha") != os.environ["GITHUB_SHA"])
            or run.get("run_attempt") != 1):
        raise ValueError("approval must run once from trusted default-branch workflow code")
    triggering_actor = run.get("triggering_actor", {}).get("login")
    if not triggering_actor:
        raise ValueError("approval run has no triggering actor identity")
    return run, triggering_actor


def require_maintainer(api, login: str) -> None:
    if not isinstance(login, str) or not re.fullmatch(r"[A-Za-z0-9-]+", login):
        raise ValueError("triggering actor identity is malformed")
    collaborator = api.get(f"collaborators/{urllib.parse.quote(login, safe='')}/permission")
    permission = collaborator.get("permission")
    role_name = collaborator.get("role_name")
    if permission != "admin" and role_name not in {"admin", "maintain"}:
        raise ValueError("graph approval requires a live admin or maintain repository permission")


def approval_run_key(run: dict) -> tuple[int, int, bool] | None:
    match = re.fullmatch(
        r"Dependency graph approval \| source_run=(\d+) \| source_attempt=(\d+) \| approved=(true|false)",
        run.get("display_title", ""),
    )
    return (int(match.group(1)), int(match.group(2)), match.group(3) == "true") if match else None


def prior_receipt(api, source_run: dict, source_run_id: int, source_attempt: int,
                  current_run_id: int, pr: dict, review: dict) -> bool:
    created_at = source_run.get("created_at")
    if not isinstance(created_at, str) or not re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", created_at):
        raise ValueError("source run creation time is unavailable; cannot bound replay history")
    repository = api.get("")
    branch = repository.get("default_branch")
    query = urllib.parse.urlencode({"event": "workflow_dispatch", "branch": branch, "created": f">={created_at}"})
    runs = api.pages(f"actions/workflows/{APPROVAL_WORKFLOW}/runs?{query}", "workflow_runs", max_items=MAX_HISTORY_ITEMS + 1)
    if len(runs) > MAX_HISTORY_ITEMS:
        raise ValueError("approval history exceeds its safe scan limit; refusing possible replay")
    receipt_name = f"{RECEIPT_PREFIX}{source_run_id}-{source_attempt}"
    matching = []
    for run in runs:
        if (run.get("id") == current_run_id
                or run.get("id", 0) > current_run_id
                or run.get("path") != f".github/workflows/{APPROVAL_WORKFLOW}"
                or run.get("head_branch") != branch or run.get("event") != "workflow_dispatch"):
            continue
        key = approval_run_key(run)
        if key == (source_run_id, source_attempt, True):
            matching.append(run)
    for previous in matching:
        artifact_inventory = api.pages(f"actions/runs/{previous['id']}/artifacts", "artifacts",
                                       max_items=MAX_HISTORY_ITEMS + 1)
        if len(artifact_inventory) > MAX_HISTORY_ITEMS:
            raise ValueError("prior receipt artifact inventory exceeds its safe lookup limit")
        artifacts = [item for item in artifact_inventory if item.get("name") == receipt_name]
        if len(artifacts) != 1:
            raise ValueError("matching approved run has no unique consumed receipt; refusing ambiguous replay")
        receipt_artifact = artifacts[0]
        size = receipt_artifact.get("size_in_bytes")
        if (receipt_artifact.get("expired") or type(size) is not int
                or not 0 <= size <= MAX_RECEIPT_ARCHIVE):
            raise ValueError("prior approval receipt is unavailable or oversized; refusing replay")
        content = api.download(f"actions/artifacts/{receipt_artifact['id']}/zip", MAX_RECEIPT_ARCHIVE)
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            raw = exact_zip_member(archive, "approval-receipt.json", MAX_RECEIPT_JSON)
        receipt = json.loads(raw.decode("utf-8"))
        if (receipt.get("repository") != api.repository
                or receipt.get("source_run_id") != source_run_id
                or receipt.get("source_run_attempt") != source_attempt
                or receipt.get("handoff_run_id") != previous.get("id")
                or receipt.get("handoff_run_attempt") != previous.get("run_attempt")
                or receipt.get("handoff_run_attempt") != 1
                or not isinstance(receipt.get("actor"), str) or not receipt["actor"]
                or receipt.get("pr_number") != pr.get("number")
                or receipt.get("branch") != pr.get("head", {}).get("ref")
                or receipt.get("source_sha") != source_run.get("head_sha")
                or receipt.get("graph_digest") != review.get("digest")):
            raise ValueError("prior approval receipt identity is invalid; refusing replay")
        if receipt.get("actor") != previous.get("triggering_actor", {}).get("login"):
            raise ValueError("prior approval receipt actor does not match its trusted run")
        return True
    return False


def receipt_for(run: dict, actor: str, source_run_id: int, source_attempt: int,
                pr: dict, review: dict, repository: str) -> dict:
    return {
        "schema": 1,
        "repository": repository,
        "handoff_run_id": run["id"],
        "handoff_run_attempt": run["run_attempt"],
        "actor": actor,
        "source_run_id": source_run_id,
        "source_run_attempt": source_attempt,
        "pr_number": pr["number"],
        "branch": pr["head"]["ref"],
        "source_sha": pr["head"]["sha"],
        "graph_digest": review["digest"],
    }


def prepare_approval(api, source_run_id: int, source_attempt: int, current_run_id: int, receipt_path: Path) -> dict:
    handoff, actor = current_handoff(api, current_run_id)
    if approval_run_key(handoff) != (source_run_id, source_attempt, True):
        raise ValueError("workflow dispatch did not explicitly approve this exact source run attempt")
    require_maintainer(api, actor)
    source_run, pr, review, _ = read_review(api, source_run_id, source_attempt)
    if prior_receipt(api, source_run, source_run_id, source_attempt, current_run_id, pr, review):
        raise ValueError("this exact graph-review attempt already has an approval receipt")
    current_handoff_again, actor_again = current_handoff(api, current_run_id)
    require_maintainer(api, actor_again)
    if (actor_again != actor or current_handoff_again.get("run_attempt") != handoff.get("run_attempt")
            or current_handoff_again.get("head_sha") != handoff.get("head_sha")):
        raise ValueError("approval handoff identity changed while validating")
    receipt = receipt_for(handoff, actor, source_run_id, source_attempt, pr, review, api.repository)
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    receipt_path.write_text(json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared one-time approval receipt for source run {source_run_id} attempt {source_attempt} by {actor}.")
    return receipt


def current_receipt(api, current_run_id: int, source_run_id: int, source_attempt: int) -> dict:
    receipt_name = f"{RECEIPT_PREFIX}{source_run_id}-{source_attempt}"
    artifact_inventory = api.pages(f"actions/runs/{current_run_id}/artifacts", "artifacts",
                                   max_items=MAX_HISTORY_ITEMS + 1)
    if len(artifact_inventory) > MAX_HISTORY_ITEMS:
        raise ValueError("current receipt artifact inventory exceeds its safe lookup limit")
    artifacts = [item for item in artifact_inventory if item.get("name") == receipt_name]
    if len(artifacts) != 1:
        raise ValueError("approval receipt was not uploaded exactly once before dispatch")
    artifact = artifacts[0]
    size = artifact.get("size_in_bytes")
    if artifact.get("expired") or type(size) is not int or not 0 <= size <= MAX_RECEIPT_ARCHIVE:
        raise ValueError("approval receipt is unavailable or oversized")
    content = api.download(f"actions/artifacts/{artifact['id']}/zip", MAX_RECEIPT_ARCHIVE)
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        raw = exact_zip_member(archive, "approval-receipt.json", MAX_RECEIPT_JSON)
    return json.loads(raw.decode("utf-8"))


def dispatch_write(api, source_run_id: int, source_attempt: int, current_run_id: int) -> tuple[dict, dict, str]:
    handoff, actor = current_handoff(api, current_run_id)
    if approval_run_key(handoff) != (source_run_id, source_attempt, True):
        raise ValueError("workflow dispatch did not explicitly approve this exact source run attempt")
    require_maintainer(api, actor)
    source_run, pr, review, _ = read_review(api, source_run_id, source_attempt)
    if prior_receipt(api, source_run, source_run_id, source_attempt, current_run_id, pr, review):
        raise ValueError("this exact graph-review attempt already has an approval receipt")
    receipt = current_receipt(api, current_run_id, source_run_id, source_attempt)
    expected_receipt = receipt_for(handoff, actor, source_run_id, source_attempt, pr, review, api.repository)
    if receipt != expected_receipt:
        raise ValueError("uploaded approval receipt does not match the live maintainer, PR, source attempt, or graph")
    # Re-fetch after receipt validation. A rerun or push during approval invalidates the dispatch.
    source_run, pr, review, _ = read_review(api, source_run_id, source_attempt)
    latest_handoff, latest_actor = current_handoff(api, current_run_id)
    if latest_actor != actor or latest_handoff.get("head_sha") != handoff.get("head_sha"):
        raise ValueError("approval actor or handoff source changed before dispatch")
    require_maintainer(api, latest_actor)
    payload = {
        "ref": pr["head"]["ref"],
        "inputs": {
            "mode": "write",
            "approved_head_sha": source_run["head_sha"],
            "approved_graph_digest": review["digest"],
        },
        "return_run_details": True,
    }
    response = api.post_json(f"actions/workflows/{WRITER_WORKFLOW}/dispatches", payload)
    writer_id = response.get("workflow_run_id")
    if type(writer_id) is not int or writer_id <= 0:
        raise ValueError("workflow dispatch did not return an exact writer run ID")
    writer = None
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            writer = api.get(f"actions/runs/{writer_id}")
            break
        except RECONCILE.API_ERRORS:
            time.sleep(2)
    if writer is None:
        raise ValueError("dispatched writer run ID is not yet available from GitHub")
    if (writer.get("id") != writer_id or writer.get("path") != f".github/workflows/{WRITER_WORKFLOW}"
            or writer.get("event") != "workflow_dispatch"
            or writer.get("head_branch") != pr["head"]["ref"]
            or writer.get("head_sha") != source_run["head_sha"]
            or (writer.get("head_repository") or {}).get("full_name") != api.repository
            or RECONCILE.preparation_mode(writer) != "write"):
        raise ValueError("dispatch response does not identify the expected exact-head write run")
    latest_source = api.get(f"actions/runs/{source_run_id}")
    if latest_source.get("run_attempt") != source_attempt:
        raise ValueError("review source attempt changed during writer dispatch")
    output({"writer_run_id": writer_id, "writer_run_url": writer.get("html_url", "")})
    return writer, pr, review["digest"]


def output_ci(api, pr: dict, output_sha: str) -> dict | None:
    live_pr = api.get(f"pulls/{pr['number']}")
    if (live_pr.get("state") != "open"
            or live_pr.get("head", {}).get("sha") != output_sha
            or live_pr.get("head", {}).get("ref") != pr.get("head", {}).get("ref")
            or (live_pr.get("head", {}).get("repo") or {}).get("full_name") != api.repository):
        raise ValueError("PR head changed or does not match the validated writer output")
    query = urllib.parse.urlencode({"event": "pull_request", "head_sha": output_sha})
    runs = api.pages(f"actions/workflows/ci.yml/runs?{query}", "workflow_runs", max_items=100)
    matches = [run for run in runs if (
        run.get("event") == "pull_request"
        and run.get("path") == ".github/workflows/ci.yml"
        and run.get("head_sha") == output_sha
        and run.get("head_branch") == pr["head"]["ref"]
        and (run.get("head_repository") or {}).get("full_name") == api.repository
        and (not run.get("pull_requests") or any(item.get("number") == pr["number"]
                                                   for item in run["pull_requests"]))
    )]
    if not matches:
        return None
    selected = max(matches, key=lambda item: (item.get("run_number", 0), item.get("run_attempt", 0)))
    current = api.get(f"actions/runs/{selected['id']}")
    if (current.get("id") != selected.get("id")
            or current.get("path") != f".github/workflows/ci.yml"
            or current.get("event") != "pull_request"
            or current.get("head_sha") != output_sha
            or current.get("head_branch") != pr["head"]["ref"]
            or (current.get("head_repository") or {}).get("full_name") != api.repository):
        raise ValueError("selected required CI run changed identity while reading its status")
    return current


def successful_ci_inventory(api, run: dict) -> bool:
    attempt = run.get("run_attempt", 1)
    jobs = api.pages(f"actions/runs/{run['id']}/attempts/{attempt}/jobs", "jobs")
    if attempt > 1:
        history = api.pages(f"actions/runs/{run['id']}/jobs?filter=all", "jobs")
        jobs = RECONCILE.effective_jobs(jobs, history, attempt)
    names = [job.get("name") for job in jobs]
    if set(names) != RECONCILE.CI_REQUIRED_JOBS or len(names) != len(RECONCILE.CI_REQUIRED_JOBS):
        return False
    gate = [job for job in jobs if job.get("name") == "CI Gate"]
    return (len(gate) == 1 and gate[0].get("conclusion") == "success"
            and all(job.get("conclusion") in {"success", "skipped"} for job in jobs))


def ci_matches_preparation(api, run: dict, pr: dict, output_sha: str, graph_digest: str) -> bool:
    preparation = RECONCILE.preparation_current(api, pr)
    result = (preparation or {}).get("result") or {}
    if (not preparation or not preparation.get("authoritative_valid")
            or result.get("output_sha") != output_sha
            or result.get("graph_digest") != graph_digest):
        return False
    attempt = run.get("run_attempt", 1)
    jobs = api.pages(f"actions/runs/{run['id']}/attempts/{attempt}/jobs", "jobs")
    if attempt > 1:
        history = api.pages(f"actions/runs/{run['id']}/jobs?filter=all", "jobs")
        jobs = RECONCILE.effective_jobs(jobs, history, attempt)
    gates = [job for job in jobs if job.get("name") == "CI Gate"]
    if len(gates) != 1 or gates[0].get("conclusion") != "success":
        return False
    log = api.download(f"actions/jobs/{gates[0]['id']}/logs", RECONCILE.MAX_LOG).decode("utf-8", errors="replace")
    markers = [re.fullmatch(r"DEPENDENCY_PREPARATION_RESULT run_id=(\d+) attempt=(\d+) head=([0-9a-f]{40})", line)
               for line in PREPARATION.output_lines(log)]
    markers = [marker for marker in markers if marker]
    if not (len(markers) == 1 and (int(markers[0].group(1)), int(markers[0].group(2)), markers[0].group(3)) == (
        preparation["id"], preparation.get("run_attempt", 1), output_sha,
    )):
        return False
    live_pr = api.get(f"pulls/{pr['number']}")
    live_ci = api.get(f"actions/runs/{run['id']}")
    latest_preparation = RECONCILE.preparation_current(api, live_pr)
    latest_result = (latest_preparation or {}).get("result") or {}
    return bool(
        live_pr.get("state") == "open" and live_pr.get("head", {}).get("sha") == output_sha
        and live_ci.get("id") == run.get("id")
        and live_ci.get("run_attempt") == run.get("run_attempt", 1)
        and live_ci.get("status") == "completed" and live_ci.get("conclusion") == "success"
        and latest_preparation and latest_preparation.get("authoritative_valid")
        and (latest_preparation.get("id"), latest_preparation.get("run_attempt", 1)) == (
            preparation.get("id"), preparation.get("run_attempt", 1),
        )
        and latest_result.get("output_sha") == output_sha
        and latest_result.get("graph_digest") == graph_digest
    )


def dispatch_and_follow(api, source_run_id: int, source_attempt: int, current_run_id: int) -> int:
    writer, pr, approved_digest = dispatch_write(api, source_run_id, source_attempt, current_run_id)
    writer_id = writer["id"]
    deadline = time.monotonic() + MAX_WAIT_SECONDS
    output_sha = None
    result = None
    while time.monotonic() < deadline:
        current = api.get(f"actions/runs/{writer_id}")
        latest_source = api.get(f"actions/runs/{source_run_id}")
        if latest_source.get("run_attempt") != source_attempt:
            raise ValueError("review source attempt changed while following the dispatched writer")
        if (current.get("id") != writer_id or current.get("path") != f".github/workflows/{WRITER_WORKFLOW}"
                or current.get("event") != "workflow_dispatch"
                or current.get("head_branch") != pr["head"]["ref"]
                or current.get("head_sha") != pr["head"]["sha"]
                or RECONCILE.preparation_mode(current) != "write"):
            raise ValueError("exact dispatched writer identity changed while waiting")
        if current.get("status") == "completed":
            if current.get("conclusion") != "success":
                raise ValueError(f"writer run {current.get('html_url', writer_id)} completed {current.get('conclusion')}")
            result, _ = RECONCILE.result_archive(api, current, pr, allow_reuse=True)
            output_sha = result.get("output_sha")
            if not valid_sha(output_sha):
                raise ValueError("validated writer result has a malformed output SHA")
            if result.get("graph_digest") != approved_digest:
                raise ValueError("writer result graph digest does not match the approved review")
            break
        print(f"Waiting for exact writer run {writer_id} ({current.get('status', 'unknown')}).", flush=True)
        time.sleep(30)
    if result is None:
        raise TimeoutError(f"writer run {writer.get('html_url', writer_id)} did not finish within the bounded wait")
    live_pr = api.get(f"pulls/{pr['number']}")
    if (live_pr.get("state") != "open" or live_pr.get("head", {}).get("sha") != output_sha
            or live_pr.get("head", {}).get("ref") != pr["head"]["ref"]):
        raise ValueError("writer completed but the live PR head is not its validated output SHA")
    while time.monotonic() < deadline:
        ci = output_ci(api, pr, output_sha)
        if ci:
            if ci.get("status") == "completed":
                if ci.get("conclusion") != "success":
                    if PREPARATION.refresh_failed_ci_gate(api, writer_id, RECONCILE):
                        print("Requested the safe CI Gate refresh for the prepared output head.", flush=True)
                        time.sleep(30)
                        continue
                    raise ValueError(f"required CI for output head failed: {ci.get('html_url', '')}")
                if not successful_ci_inventory(api, ci):
                    if PREPARATION.refresh_failed_ci_gate(api, writer_id, RECONCILE):
                        print("Requested the safe CI Gate refresh for the prepared output head.", flush=True)
                        time.sleep(30)
                        continue
                    raise ValueError("required CI did not complete its exact required-job inventory and successful Gate")
                live_output_pr = api.get(f"pulls/{pr['number']}")
                if not ci_matches_preparation(api, ci, live_output_pr, output_sha, approved_digest):
                    if PREPARATION.refresh_failed_ci_gate(api, writer_id, RECONCILE):
                        print("Requested the safe CI Gate refresh for the approved prepared output head.", flush=True)
                        time.sleep(30)
                        continue
                    raise ValueError("required CI Gate does not attest the approved preparation generation")
                latest_source = api.get(f"actions/runs/{source_run_id}")
                if latest_source.get("run_attempt") != source_attempt:
                    raise ValueError("review source attempt changed before CI handoff completion")
                print(f"Writer run: {writer.get('html_url', writer_id)}")
                print(f"Output head: {output_sha}")
                print(f"Required CI: {ci.get('html_url', '')}")
                summary = os.environ.get("GITHUB_STEP_SUMMARY")
                if summary:
                    with open(summary, "a", encoding="utf-8") as stream:
                        stream.write(f"\nWriter run: [{writer_id}]({writer.get('html_url', '')})\n\n"
                                     f"Prepared output: `{output_sha}`\n\n"
                                     f"Required CI: [{ci.get('run_number')}]({ci.get('html_url', '')})\n")
                return 0
            print(f"Waiting for required CI on {output_sha}.", flush=True)
        else:
            print(f"Waiting for required CI creation on {output_sha}.", flush=True)
        time.sleep(30)
    raise TimeoutError(f"writer run {writer.get('html_url', writer_id)} finished, but exact-output CI did not finish in time")


def make_api() -> object:
    return RECONCILE.GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])


def main() -> int:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    inspect = commands.add_parser("inspect")
    inspect.add_argument("--source-run-id", type=lambda value: positive_int(value, "source run ID"), required=True)
    inspect.add_argument("--source-run-attempt", type=lambda value: positive_int(value, "source run attempt"), required=True)
    inspect.add_argument("--output", type=Path, required=True)
    prepare = commands.add_parser("prepare-approval")
    prepare.add_argument("--source-run-id", type=lambda value: positive_int(value, "source run ID"), required=True)
    prepare.add_argument("--source-run-attempt", type=lambda value: positive_int(value, "source run attempt"), required=True)
    prepare.add_argument("--current-run-id", type=lambda value: positive_int(value, "current run ID"), required=True)
    prepare.add_argument("--receipt", type=Path, required=True)
    dispatch = commands.add_parser("dispatch-and-follow")
    dispatch.add_argument("--source-run-id", type=lambda value: positive_int(value, "source run ID"), required=True)
    dispatch.add_argument("--source-run-attempt", type=lambda value: positive_int(value, "source run attempt"), required=True)
    dispatch.add_argument("--current-run-id", type=lambda value: positive_int(value, "current run ID"), required=True)
    args = parser.parse_args()
    try:
        api = make_api()
        if args.command == "inspect":
            present_review(api, args.source_run_id, args.source_run_attempt, args.output)
            return 0
        if args.command == "prepare-approval":
            prepare_approval(api, args.source_run_id, args.source_run_attempt,
                             args.current_run_id, args.receipt)
            return 0
        return dispatch_and_follow(api, args.source_run_id, args.source_run_attempt, args.current_run_id)
    except (OSError, ValueError, KeyError, TypeError, UnicodeDecodeError,
            json.JSONDecodeError, zipfile.BadZipFile, TimeoutError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
