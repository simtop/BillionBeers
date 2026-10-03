#!/usr/bin/env python3
"""Reconcile the current PR's CI, never the triggering event's stale snapshot.

Run only from trusted default-branch code, under the PR-scoped workflow concurrency lock.
"""

from __future__ import annotations

import argparse
import copy
import importlib.util
import io
import json
import os
import re
import stat
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path, PurePosixPath

SPEC = importlib.util.spec_from_file_location("render_ci_report", Path(__file__).with_name("render-ci-pr-comment.py"))
RENDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RENDER)
PUBLISH = RENDER.load_script("publish-ci-comment")
API_ERRORS = (OSError, ValueError, KeyError, zipfile.BadZipFile)
MAX_ARCHIVE = 32 * 1024 * 1024
MAX_XML = 2 * 1024 * 1024
MAX_LOG = 16 * 1024 * 1024
MAX_PREPARATION_RESULT_ARCHIVE = 64 * 1024
MAX_PREPARATION_RESULT_JSON = 32 * 1024
PREPARATION = RENDER.load_script("dependabot-preparation")
PREPARATION_LANES = {"linux", "web", "managed", "apple"}
PREPARATION_RESULT_PREFIX = "authoritative-preparation-result-"
PREPARATION_RUN_NAME = re.compile(
    r"Dependency preparation \| mode=(write|reference|candidate) \| ref=(.+)"
)
CI_REQUIRED_JOBS = {
    "Code Style Formatting Check", "Detect change scope", "Secret Scan (gitleaks)",
    "Static Analysis (Detekt)", "Android Lint", "Dependency Guard", "Unit Tests",
    "Web Tests (Wasm/static)", "Screenshot Tests (Paparazzi)",
    "Instrumented Tests (Gradle Managed Device)", "Native Tests (Apple)", "CI Gate",
}


def warning(message: str) -> None:
    print(f"::warning::{message}", file=sys.stderr)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


class GitHub:
    def __init__(self, token: str, repository: str):
        self.token = token
        self.repository = repository
        self.base = f"https://api.github.com/repos/{repository}"

    def get(self, path: str):
        return PUBLISH.api_request(self.token, f"{self.base}/{path}")

    def post(self, path: str) -> None:
        request = urllib.request.Request(f"{self.base}/{path}", data=b"{}", method="POST", headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28",
        })
        with urllib.request.urlopen(request, timeout=20):
            pass  # The rerun endpoint returns 201/202 with no JSON body.

    def post_json(self, path: str, body: dict) -> dict:
        request = urllib.request.Request(f"{self.base}/{path}", data=json.dumps(body).encode(), method="POST", headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28",
        })
        with urllib.request.urlopen(request, timeout=20) as response:
            content = response.read(1024 * 1024 + 1)
        if len(content) > 1024 * 1024:
            raise ValueError("GitHub API response exceeds 1 MiB")
        return json.loads(content.decode("utf-8"))

    def pages(self, path: str, key: str | None = None, *, max_items: int | None = None) -> list:
        result = []
        page = 1
        separator = "&" if "?" in path else "?"
        while True:
            page_size = min(100, max_items - len(result)) if max_items is not None else 100
            if page_size <= 0:
                return result
            response = self.get(f"{path}{separator}per_page={page_size}&page={page}")
            items = response[key] if key else response
            result.extend(items)
            if len(items) < page_size or (max_items is not None and len(result) >= max_items):
                return result
            page += 1

    def download(self, path: str, limit: int) -> bytes:
        request = urllib.request.Request(f"{self.base}/{path}", headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
        })
        opener = urllib.request.build_opener(NoRedirect)
        try:
            response = opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            if error.code not in {301, 302, 303, 307, 308}:
                raise
            location = error.headers["Location"]
            if urllib.parse.urlsplit(location).scheme != "https":
                raise ValueError("Non-HTTPS evidence redirect") from error
            # Signed storage URL: do not forward the repository token to another host.
            response = urllib.request.urlopen(location, timeout=20)
        with response:
            content = response.read(limit + 1)
        if len(content) > limit:
            raise ValueError("Evidence exceeds the download limit")
        return content


def same_source(pr: dict, run: dict, repository: str) -> bool:
    head = pr.get("head", {})
    source = run.get("head_repository") or {}
    return (
        pr.get("state") == "open"
        and pr.get("base", {}).get("repo", {}).get("full_name") == repository
        and source.get("id") is not None
        and (head.get("repo") or {}).get("id") == source["id"]
        and head.get("ref") == run.get("head_branch")
    )


def resolve_pr(api: GitHub, run: dict) -> int | None:
    is_ci = run.get("event") == "pull_request" and run.get("path") == ".github/workflows/ci.yml"
    is_preparation = (run.get("path") == f".github/workflows/{PREPARATION.WORKFLOW}"
                      and run.get("event") in {"pull_request", "workflow_dispatch"}
                      and preparation_mode(run) == "write")
    if not (is_ci or is_preparation):
        return None
    candidates = run.get("pull_requests", [])
    if not candidates:
        try:
            candidates = api.pages(f"commits/{run['head_sha']}/pulls")
        except API_ERRORS:
            # Fork commit metadata can be unavailable in the base repository.
            candidates = []
    if not candidates:
        owner = run["head_repository"]["owner"]["login"]
        query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{run['head_branch']}"})
        candidates = api.pages(f"pulls?{query}")
    matches = set()
    for candidate in candidates:
        pr = api.get(f"pulls/{candidate['number']}")
        eligible_bot_dispatch = (run.get("event") != "workflow_dispatch"
            or pr.get("user", {}).get("login") == "dependabot[bot]"
            and (pr.get("head", {}).get("repo") or {}).get("full_name") == api.repository
            and (pr.get("base", {}).get("repo") or {}).get("full_name") == api.repository)
        if eligible_bot_dispatch and same_source(pr, run, api.repository):
            matches.add(pr["number"])
    if run.get("event") == "workflow_dispatch" and not candidates:
        owner = api.repository.split("/", 1)[0]
        query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{run.get('head_branch', '')}"})
        candidates = api.pages(f"pulls?{query}")
        for pr in candidates:
            if (pr.get("user", {}).get("login") == "dependabot[bot]"
                    and pr.get("head", {}).get("sha") == run.get("head_sha")
                    and same_source(pr, run, api.repository)):
                matches.add(pr["number"])
    if len(matches) != 1:
        warning("CI comment skipped: no unambiguous open PR association")
        return None
    return matches.pop()


def current(api: GitHub, pr_number: int) -> tuple[dict, dict | None]:
    pr = api.get(f"pulls/{pr_number}")
    if pr.get("state") != "open":
        return pr, None
    query = urllib.parse.urlencode({"event": "pull_request", "head_sha": pr["head"]["sha"]})
    runs = api.pages(f"actions/workflows/ci.yml/runs?{query}", "workflow_runs")
    matches = [run for run in runs if (
        same_source(pr, run, api.repository)
        and run.get("head_sha") == pr["head"]["sha"]
        and (not run.get("pull_requests") or any(item["number"] == pr_number for item in run["pull_requests"]))
    )]
    if not matches:
        return pr, None
    # A rerun of an older original run must not supersede a newer run on the same SHA.
    latest = max(matches, key=lambda run: run["run_number"])
    return pr, api.get(f"actions/runs/{latest['id']}")


def preparation_mode(run: dict) -> str | None:
    match = PREPARATION_RUN_NAME.fullmatch(run.get("display_title", ""))
    return match.group(1) if match and match.group(2) == run.get("head_branch") else None


def result_archive(api: GitHub, run: dict, pr: dict, *, allow_reuse: bool = True) -> tuple[dict, dict]:
    """Download one bounded result artifact and validate every identity before using it."""
    artifact_name = f"{PREPARATION_RESULT_PREFIX}{run['id']}-{run['run_attempt']}"
    artifacts = [artifact for artifact in api.pages(f"actions/runs/{run['id']}/artifacts", "artifacts")
                 if artifact.get("name") == artifact_name]
    if len(artifacts) != 1:
        raise ValueError("authoritative preparation result is missing or duplicated")
    artifact = artifacts[0]
    size = artifact.get("size_in_bytes")
    if artifact.get("expired") or type(size) is not int or not 0 <= size <= MAX_PREPARATION_RESULT_ARCHIVE:
        raise ValueError("authoritative preparation result is expired or exceeds its size limit")
    content = api.download(f"actions/artifacts/{artifact['id']}/zip", MAX_PREPARATION_RESULT_ARCHIVE)
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        entries = archive.infolist()
        if len(entries) != 1 or entries[0].filename != "authoritative-preparation.json":
            raise ValueError("authoritative result archive must contain exactly one expected JSON file")
        entry = entries[0]
        if (entry.is_dir() or entry.file_size > MAX_PREPARATION_RESULT_JSON
                or stat.S_ISLNK(entry.external_attr >> 16)):
            raise ValueError("authoritative result archive contains an unsafe member")
        raw = archive.read(entry)
    if len(raw) != entry.file_size:
        raise ValueError("authoritative result archive member length is inconsistent")
    result = json.loads(raw.decode("utf-8"))
    validate_preparation_result(api, result, run, pr, allow_reuse=allow_reuse)
    return result, artifact


def valid_sha(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def validate_preparation_result(api: GitHub, result: dict, run: dict, pr: dict, *, allow_reuse: bool = True) -> None:
    if not isinstance(result, dict) or type(result.get("schema")) is not int or result.get("schema") != 1:
        raise ValueError("authoritative preparation result has an unsupported schema")
    expected = {
        "repository": api.repository,
        "repository_id": pr.get("base", {}).get("repo", {}).get("id"),
        "pr_number": pr.get("number"),
        "branch": pr.get("head", {}).get("ref"),
        "source_sha": run.get("head_sha"),
        "mode": "write",
        "run_id": run.get("id"),
        "run_attempt": run.get("run_attempt"),
    }
    if any(result.get(key) != value for key, value in expected.items()):
        raise ValueError("authoritative preparation result has a wrong repository, PR, branch, or run identity")
    if not valid_sha(result.get("source_sha")) or not valid_sha(result.get("output_sha")):
        raise ValueError("authoritative preparation result has a malformed source/output SHA")
    if (result.get("graph_mode") != "reference"
            or not isinstance(result.get("graph_digest"), str)
            or not re.fullmatch(r"[0-9a-f]{64}", result["graph_digest"])):
        raise ValueError("authoritative preparation result has a malformed graph identity")
    kind = result.get("kind")
    if kind == "full":
        lanes = result.get("lanes")
        if not isinstance(lanes, dict) or set(lanes) != PREPARATION_LANES:
            raise ValueError("authoritative preparation result does not contain all four platform lanes")
        for lane, identity in lanes.items():
            if not isinstance(identity, dict) or any(identity.get(key) != value for key, value in {
                "lane": lane, "run_id": run.get("id"), "run_attempt": run.get("run_attempt"),
                "head_sha": result["source_sha"], "mode": "write", "graph_mode": "reference",
                "graph_digest": result["graph_digest"], "conclusion": "success",
            }.items()) or not isinstance(identity.get("manifest_sha256"), str) or not re.fullmatch(
                r"[0-9a-f]{64}", identity["manifest_sha256"]):
                raise ValueError(f"invalid authoritative identity for platform lane {lane}")
    elif kind == "reuse":
        if not allow_reuse:
            raise ValueError("reuse references must point directly to a full preparation result")
        if (result.get("output_sha") != result.get("source_sha")
                or not isinstance(result.get("origin_run_id"), int)
                or not isinstance(result.get("origin_run_attempt"), int)):
            raise ValueError("reused preparation identity is malformed")
        origin = api.get(f"actions/runs/{result['origin_run_id']}")
        if (origin.get("run_number", 0) >= run.get("run_number", 0)
                or origin.get("status") != "completed" or origin.get("conclusion") != "success"
                or origin.get("run_attempt") != result["origin_run_attempt"]
                or origin.get("head_branch") != result.get("branch")
                or (origin.get("head_repository") or {}).get("full_name") != api.repository
                or origin.get("path") != f".github/workflows/{PREPARATION.WORKFLOW}"
                or preparation_mode(origin) != "write"):
            raise ValueError("reuse does not reference a completed earlier write run on this branch")
        origin_result, _ = result_archive(api, origin, pr, allow_reuse=False)
        if (origin_result.get("kind") != "full"
                or origin_result.get("output_sha") != result.get("source_sha")
                or origin_result.get("graph_digest") != result.get("graph_digest")):
            raise ValueError("reuse origin does not validate the current source head")
        result["origin_result"] = origin_result
        result["origin_run"] = origin
    else:
        raise ValueError("authoritative preparation result has an unknown kind")
    if result["output_sha"] != result["source_sha"]:
        commit = api.get(f"commits/{result['output_sha']}")
        parents = commit.get("parents", [])
        if len(parents) != 1 or parents[0].get("sha") != result["source_sha"]:
            raise ValueError("preparation output is not a direct child of its source SHA")


def preparation_current(api: GitHub, pr: dict, *, exclude_run_id: int | None = None) -> dict | None:
    repository = api.repository
    if (pr.get("state") != "open" or pr.get("user", {}).get("login") != "dependabot[bot]"
            or pr.get("base", {}).get("repo", {}).get("full_name") != repository
            or (pr.get("head", {}).get("repo") or {}).get("full_name") != repository):
        return None
    branch, head_sha = pr.get("head", {}).get("ref"), pr.get("head", {}).get("sha")
    try:
        changed_paths = [item["filename"] for item in api.pages(f"pulls/{pr['number']}/files")]
    except API_ERRORS as error:
        return {"status": "completed", "conclusion": "failure", "category": "evidence_unavailable",
                "authoritative_error": f"Cannot determine whether preparation is required: {type(error).__name__}",
                "head_sha": head_sha, "html_url": pr.get("html_url", ""), "stale_runs": [],
                "authoritative_valid": False}
    if not PREPARATION.paths_require_preparation(changed_paths):
        return None
    query = urllib.parse.urlencode({"branch": branch})
    # A bounded recent window is enough to identify the authoritative attempt. Older runs
    # cannot outrank a newer matching result, and scanning them makes every gate poll download
    # an unbounded number of immutable artifacts.
    runs = api.pages(f"actions/workflows/{PREPARATION.WORKFLOW}/runs?{query}", "workflow_runs", max_items=100)
    intents = []
    stale = []
    for listed in sorted(runs, key=lambda item: (item.get("run_number", 0), item.get("run_attempt", 0)), reverse=True):
        if (listed.get("path") != f".github/workflows/{PREPARATION.WORKFLOW}"
                or listed.get("event") not in {"pull_request", "workflow_dispatch"}
                or listed.get("head_branch") != branch
                or (listed.get("head_repository") or {}).get("full_name") != api.repository):
            continue
        if listed.get("id") == exclude_run_id:
            continue
        listed_mode = preparation_mode(listed)
        if listed_mode in {"reference", "candidate"}:
            continue
        if listed_mode != "write":
            if listed.get("head_sha") == head_sha:
                intents.append({"run": listed, "result": None, "artifact": None, "state": "unresolved"})
                break
            continue
        try:
            run = api.get(f"actions/runs/{listed['id']}")
        except API_ERRORS:
            if listed.get("head_sha") == head_sha:
                intents.append({"run": listed, "result": None, "artifact": None, "state": "unresolved"})
                break
            stale.append({**listed, "stale_reason": "run details unavailable"})
            continue
        if (run.get("event") not in {"pull_request", "workflow_dispatch"}
                or run.get("path") != f".github/workflows/{PREPARATION.WORKFLOW}"
                or run.get("head_branch") != branch
                or (run.get("head_repository") or {}).get("full_name") != repository
                or preparation_mode(run) not in {"write", None}):
            if run.get("head_sha") == head_sha:
                intents.append({"run": run, "result": None, "artifact": None, "state": "unresolved"})
                break
            continue
        if run.get("head_sha") == head_sha:
            if run.get("event") == "pull_request":
                numbers = {item.get("number") for item in run.get("pull_requests", [])}
                if not numbers:
                    try:
                        numbers = {item.get("number") for item in api.pages(
                            f"commits/{head_sha}/pulls"
                        )}
                    except API_ERRORS:
                        intents.append({"run": run, "result": None, "artifact": None, "state": "unresolved"})
                        break
                if numbers and pr.get("number") not in numbers:
                    continue
            elif run.get("event") == "workflow_dispatch":
                owner = api.repository.split("/", 1)[0]
                head_query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{branch}"})
                try:
                    branch_prs = api.pages(f"pulls?{head_query}")
                except API_ERRORS:
                    intents.append({"run": run, "result": None, "artifact": None, "state": "unresolved"})
                    break
                matches = [item for item in branch_prs if item.get("head", {}).get("ref") == branch
                           and item.get("user", {}).get("login") == "dependabot[bot]"
                           and (item.get("head", {}).get("repo") or {}).get("full_name") == api.repository
                           and item.get("base", {}).get("repo", {}).get("full_name") == api.repository]
                if (len(matches) != 1 or matches[0].get("number") != pr.get("number")
                        or matches[0].get("head", {}).get("sha") != head_sha):
                    continue
            intents.append({"run": run, "result": None, "artifact": None, "state": "intent"})
            # The newest current-source intent decides immediately. If it is unresolved, fail
            # closed; if it has a result, it must be validated before considering history.
            break
        if run.get("status") != "completed":
            if run.get("head_sha") == head_sha:
                intents.append({"run": run, "result": None, "artifact": None, "state": "unresolved"})
                break
            try:
                result, artifact = result_archive(api, run, pr)
            except API_ERRORS + (UnicodeDecodeError, json.JSONDecodeError, zipfile.BadZipFile):
                stale.append({**run, "stale_reason": "pending run has no validated result for the current PR head"})
                continue
            if result.get("output_sha") == head_sha:
                intents.append({"run": run, "result": result, "artifact": artifact, "state": "unresolved"})
                break
            stale.append({**run, "stale_reason": "source head differs from the current PR head"})
            continue
        if run.get("conclusion") != "success":
            # The writer deliberately uploads its identity before pushing. If the push or
            # post-push race check then fails, that identity still names the exact intended
            # output head and must block CI when that head is live.
            try:
                result, artifact = result_archive(api, run, pr)
            except API_ERRORS + (UnicodeDecodeError, json.JSONDecodeError) as error:
                stale.append({**run, "stale_reason": f"unresolved attempt: result unavailable or invalid ({type(error).__name__})"})
                continue
            if result.get("output_sha") == head_sha:
                intents.append({"run": run, "result": result, "artifact": artifact, "state": "unresolved"})
                break
            stale.append({**run, "stale_reason": "source head differs from the current PR head"})
            continue
        if len(stale) >= 5 and not intents:
            continue
        try:
            result, artifact = result_archive(api, run, pr)
        except API_ERRORS + (UnicodeDecodeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
            stale.append({**run, "stale_reason": f"result unavailable or invalid: {type(error).__name__}"})
            continue
        if result.get("output_sha") == head_sha:
            intents.append({"run": run, "result": result, "artifact": artifact, "state": "success"})
            break
        else:
            stale.append({**run, "stale_reason": "preparation output differs from the current PR head"})
    intents.sort(key=lambda item: (item["run"].get("run_number", 0), item["run"].get("run_attempt", 0)))
    stale.sort(key=lambda item: (item.get("run_number", 0), item.get("run_attempt", 0)), reverse=True)
    if not intents:
        return {
            "status": "completed", "conclusion": "failure", "category": "missing_authoritative_result",
            "head_sha": head_sha, "html_url": pr.get("html_url", ""), "stale_runs": stale[:5],
            "authoritative_valid": False,
        }
    selected = intents[-1]
    run = selected["run"]
    data = dict(run)
    if "status" not in data:
        data.update({"status": "completed", "conclusion": "failure"})
    selected_key = (run.get("run_number", 0), run.get("run_attempt", 0))
    stale_ids = {item.get("id") for item in stale}
    for older in runs:
        older_key = (older.get("run_number", 0), older.get("run_attempt", 0))
        if (older_key < selected_key and older.get("id") not in stale_ids
                and older.get("path") == f".github/workflows/{PREPARATION.WORKFLOW}"
                and preparation_mode(older) == "write"):
            stale.append({**older, "stale_reason": "superseded by a newer write attempt"})
            stale_ids.add(older.get("id"))
            if len(stale_ids) >= 5:
                break
    data["stale_runs"] = [item for item in stale[:5]]
    if selected["state"] == "success":
        data.update({"authoritative_valid": True, "result": selected["result"],
                     "result_artifact": selected["artifact"], "category": "success"})
        return data
    if selected["state"] == "unresolved":
        original_conclusion = run.get("conclusion")
        still_running = run.get("status") != "completed"
        data.update({"authoritative_valid": False, "result": selected.get("result"),
                     "result_artifact": selected.get("artifact"),
                     "category": "pending" if still_running else
                         "unresolved_write_attempt" if selected.get("result") else "evidence_unavailable",
                     "run_conclusion": original_conclusion,
                     "conclusion": None if still_running else "failure",
                     "authoritative_error": "A write intent targets the current PR head, but its identity or workflow completion could not be established."})
        return data
    data["authoritative_valid"] = False
    if run.get("status") != "completed":
        data["category"] = "pending"
        return data
    if run.get("conclusion") != "success":
        data["category"] = run.get("conclusion", "unknown")
        return data
    try:
        result, artifact = result_archive(api, run, pr)
        if result.get("output_sha") != head_sha:
            raise ValueError("authoritative output does not match the current PR head")
        data.update({"authoritative_valid": True, "result": result,
                     "result_artifact": artifact, "category": "success"})
    except API_ERRORS + (UnicodeDecodeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        data["category"] = "evidence_unavailable"
        data["authoritative_error"] = str(error)
        data["run_conclusion"] = data.get("conclusion")
        data["conclusion"] = "failure"
    return data


def preparation_snapshot(api: GitHub, run: dict | None) -> dict | None:
    if run is None:
        return None
    data = dict(run)
    if not run.get("id"):
        data.setdefault("artifacts", [])
        return data
    jobs = api.pages(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", "jobs")
    bad = [job for job in jobs if job.get("conclusion") in RENDER.BAD]
    if run.get("authoritative_valid"):
        data["category"] = "success"
    elif run.get("authoritative_error"):
        data["category"] = "evidence_unavailable"
    else:
        data.setdefault("category", "pending" if run["status"] != "completed" else run.get("conclusion", "unknown"))
    data["error"] = run.get("authoritative_error", "")
    for job in bad:
        steps = RENDER.failed_steps(job)
        if not steps:
            continue
        step = steps[0]
        data["failed_step"] = step["name"]
        try:
            log = api.download(f"actions/jobs/{job['id']}/logs", MAX_LOG).decode("utf-8", errors="replace")
            data["category"] = PREPARATION.classify(log, step["name"])
            data["error"] = RENDER.error_excerpt(step_log(log, step))
        except API_ERRORS:
            data["category"] = "evidence_unavailable"
        break
    try:
        preparation_name = f"dependency-preparation-{run['id']}-{run['run_attempt']}"
        resources_name = f"dependency-preparation-resources-{run['id']}-{run['run_attempt']}"
        data["artifacts"] = [item for item in api.pages(f"actions/runs/{run['id']}/artifacts", "artifacts")
                             if not item.get("expired") and (item.get("name") == preparation_name
                             or item.get("name", "").startswith(preparation_name + "-")
                             or item.get("name", "").startswith(resources_name + "-"))]
        if run.get("result_artifact") and not run["result_artifact"].get("expired"):
            data["artifacts"].append(run["result_artifact"])
    except API_ERRORS:
        data["artifacts"] = []
    return data


def combined_generation(pr: dict, run: dict | None, preparation: dict | None) -> tuple:
    return (*generation(pr, run), preparation.get("id") if preparation else None,
            preparation.get("run_attempt") if preparation else None)


def generation(pr: dict, run: dict | None) -> tuple:
    return (pr["head"]["sha"], run["id"] if run else None, run["run_attempt"] if run else None)


def effective_jobs(current_jobs: list[dict], history: list[dict], attempt: int) -> list[dict]:
    # Android CI has unique display names (including matrix labels if a matrix is introduced).
    by_name = {}
    for job in history + current_jobs:
        previous = by_name.get(job["name"])
        if previous is None or (job.get("run_attempt", 1), job["id"]) > (previous.get("run_attempt", 1), previous["id"]):
            by_name[job["name"]] = job
    return [dict(job, carried_forward=job.get("run_attempt", 1) < attempt) for job in by_name.values()]


def timestamp(value: str | None) -> datetime | None:
    # Runner logs have seven fractional digits; Python 3.9 accepts at most six.
    normalized = re.sub(r"(\.\d{6})\d+", r"\1", value) if value else None
    return datetime.fromisoformat(normalized.replace("Z", "+00:00")) if normalized else None


def artifact_for_job(artifact: dict, job: dict) -> bool:
    if artifact.get("expired") or job.get("carried_forward"):
        return False
    try:
        start, end, created = (timestamp(value) for value in (
            job.get("started_at"), job.get("completed_at"), artifact.get("created_at"),
        ))
        return bool(start and end and created and start <= created <= end)
    except (TypeError, ValueError):
        return False


def parse_archive(content: bytes, mode: str) -> dict:
    """Extract only bounded JUnit XML into an isolated directory; never run artifact content."""
    with zipfile.ZipFile(io.BytesIO(content)) as archive, tempfile.TemporaryDirectory() as directory:
        entries = archive.infolist()
        if len(entries) > 5000 or sum(entry.file_size for entry in entries) > MAX_ARCHIVE:
            raise ValueError("Evidence archive exceeds expansion limits")
        root = Path(directory)
        for entry in entries:
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.filename or stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError("Unsafe evidence archive path")
            if entry.is_dir() or not path.name.startswith("TEST-") or path.suffix != ".xml":
                continue
            if entry.file_size > MAX_XML:
                raise ValueError("JUnit report exceeds parsing limit")
            destination = root.joinpath(*path.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(entry))
        return RENDER.SUMMARY.failures_json(root, mode)


def step_log(log: str, step: dict) -> str:
    start, end = timestamp(step.get("started_at")), timestamp(step.get("completed_at"))
    if not start or not end:
        return ""
    result = []
    for line in log.splitlines():
        try:
            instant = timestamp(line.split(" ", 1)[0])
        except ValueError:
            continue
        # Job-step timestamps have second precision; logs retain fractional seconds.
        if instant and start <= instant.replace(microsecond=0) <= end:
            result.append(line)
    return "\n".join(result)


class Evidence:
    def __init__(self, api: GitHub):
        self.api = api
        self.logs = {}
        self.reports = {}

    def enrich(self, run: dict, jobs: list[dict], artifacts: list[dict]) -> list[dict]:
        result = copy.deepcopy(jobs)
        for job in result:
            if job not in RENDER.actionable_jobs({"jobs": result}) or job.get("carried_forward"):
                continue
            steps = RENDER.failed_steps(job)
            names = {name for step in steps for name in RENDER.STEP_RULES.get(step["name"], RENDER.EMPTY_RULE)[1]}
            job["artifacts"] = [artifact for artifact in artifacts if artifact["name"] in names and artifact_for_job(artifact, job)]
            job["diagnostics"] = {}
            log = ""
            log_unavailable = False
            if job.get("status") == "completed":
                try:
                    if job["id"] not in self.logs:
                        self.logs[job["id"]] = self.api.download(f"actions/jobs/{job['id']}/logs", MAX_LOG).decode("utf-8", errors="replace")
                    log = self.logs[job["id"]]
                except API_ERRORS:
                    log_unavailable = True
                    warning(f"Job log unavailable: {job['id']}")
            for step in steps:
                _, relevant, mode = RENDER.STEP_RULES.get(step["name"], RENDER.EMPTY_RULE)
                detail = {"error": RENDER.error_excerpt(step_log(log, step)), "failures": [], "unavailable": log_unavailable}
                if mode:
                    for artifact in job["artifacts"]:
                        if artifact["name"] not in relevant or artifact["name"] not in {"unit-test-reports", "screenshot-test-results", "instrumented-test-reports"}:
                            continue
                        try:
                            key = (artifact["id"], mode)
                            if key not in self.reports:
                                content = self.api.download(f"actions/artifacts/{artifact['id']}/zip", MAX_ARCHIVE)
                                self.reports[key] = parse_archive(content, mode)
                            report = self.reports[key]
                            detail["failures"].extend(report["failures"])
                            detail["unreadable"] = bool(detail.get("unreadable") or report["unreadable"])
                        except API_ERRORS:
                            detail["unavailable"] = True
                            warning(f"Test report unavailable: {artifact['id']}")
                job["diagnostics"][step["name"]] = detail
        return result


def snapshot(api: GitHub, pr: dict, run: dict | None, evidence: Evidence) -> dict:
    if run is None:
        return {"status": "queued", "head_sha": pr["head"]["sha"], "jobs": []}
    data = dict(run)
    attempt = run["run_attempt"]
    jobs = api.pages(f"actions/runs/{run['id']}/attempts/{attempt}/jobs", "jobs")
    for job in jobs:
        job.setdefault("run_attempt", attempt)
    if run["status"] == "completed" and attempt > 1 and run.get("conclusion") != "success":
        history = api.pages(f"actions/runs/{run['id']}/jobs?filter=all", "jobs")
        jobs = effective_jobs(jobs, history, attempt)
    data["jobs"] = jobs
    if RENDER.actionable_jobs(data):
        try:
            artifacts = api.pages(f"actions/runs/{run['id']}/artifacts", "artifacts")
        except API_ERRORS:
            artifacts = []
            data["incomplete"] = True
            warning("Artifact metadata unavailable; retaining failed job details")
        data["jobs"] = evidence.enrich(run, jobs, artifacts)
    return data


def watch(api: GitHub, pr_number: int, *, budget: float = 900, interval: float = 30,
          clock=time.monotonic, sleep=time.sleep, publisher=PUBLISH.publish) -> None:
    deadline = clock() + budget
    adopted = None
    last_data = None
    last_pr = None
    evidence = Evidence(api)
    completed_observations = 0
    last_body = None

    def publish(data, pr, run, preparation=None, *, create, reset=False):
        nonlocal last_body
        expected = combined_generation(pr, run, preparation)

        def is_current():
            live_pr, live_run = current(api, pr_number)
            return live_pr.get("state") == "open" and combined_generation(live_pr, live_run, preparation_current(api, live_pr)) == expected

        url = run["html_url"] if run else f"{pr['html_url']}/checks"
        run_id = str(run["id"]) if run else ""
        body = RENDER.render(data, url, api.repository, run_id)
        if body == last_body:
            return True
        publisher(api.token, api.repository, str(pr_number), RENDER.MARKER,
                  body, create=create, is_current=is_current, reset=reset)
        fresh = is_current()
        if fresh:
            last_body = body
        return fresh

    while clock() < deadline:
        try:
            pr, run = current(api, pr_number)
            if pr.get("state") != "open":
                return
            preparation = preparation_current(api, pr)
            identity = combined_generation(pr, run, preparation)
            if identity != adopted:
                completed_observations = 0
                last_data, last_pr = None, None
                evidence = Evidence(api)
                # Clear obsolete failures before downloading evidence for the replacement attempt.
                if not run or run["status"] != "completed":
                    pending = {"status": "in_progress", "head_sha": pr["head"]["sha"], "run_attempt": run["run_attempt"] if run else 1}
                    if not publish(pending, pr, run, preparation, create=False, reset=True):
                        sleep(min(interval, max(0, deadline - clock())))
                        continue
                adopted = identity
            data = snapshot(api, pr, run, evidence)
            data["preparation"] = preparation_snapshot(api, preparation)
            data["mergeable_state"] = pr.get("mergeable_state")
            create = bool(RENDER.actionable_jobs(data) or data.get("conclusion") in RENDER.BAD
                          or preparation and preparation.get("conclusion") in RENDER.BAD
                          or pr.get("user", {}).get("login") == "dependabot[bot]"
                          and pr.get("mergeable_state") == "behind")
            if not publish(data, pr, run, preparation, create=create):
                sleep(min(interval, max(0, deadline - clock())))
                continue
            last_data, last_pr = data, pr
            if run and run["status"] == "completed" and (not preparation or preparation["status"] == "completed"):
                # Allow one extra poll for final jobs/uploads becoming visible after completion.
                completed_observations += 1
                if completed_observations >= 2:
                    return
            else:
                completed_observations = 0
        except API_ERRORS as error:
            warning(f"CI reconciliation will retry: {type(error).__name__}")
        sleep(min(interval, max(0, deadline - clock())))
    if last_data is not None:
        try:
            pr, run = current(api, pr_number)
            preparation = preparation_current(api, pr)
            if pr.get("state") == "open" and combined_generation(pr, run, preparation) == adopted:
                last_data["incomplete"] = True
                publish(last_data, last_pr, run, preparation, create=False)
        except API_ERRORS:
            warning("Live reporting deadline reached; final reconciliation deferred to completion event")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolve-run", type=int)
    parser.add_argument("--pr-number", type=int)
    args = parser.parse_args()
    api = GitHub(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"])
    if args.resolve_run:
        run = api.get(f"actions/runs/{args.resolve_run}")
        number = resolve_pr(api, run)
        if number:
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"pr_number={number}\n")
    elif args.pr_number:
        watch(api, args.pr_number)
    else:
        parser.error("--resolve-run or --pr-number is required")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
