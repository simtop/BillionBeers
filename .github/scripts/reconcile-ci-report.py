#!/usr/bin/env python3
"""Reconcile the current PR's CI, never the triggering event's stale snapshot.

Run only from trusted default-branch code, under the PR-scoped workflow concurrency lock.
"""

from __future__ import annotations

import argparse
import copy
import importlib.util
import io
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

    def pages(self, path: str, key: str | None = None) -> list:
        result = []
        page = 1
        separator = "&" if "?" in path else "?"
        while True:
            response = self.get(f"{path}{separator}per_page=100&page={page}")
            items = response[key] if key else response
            result.extend(items)
            if len(items) < 100:
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
    if run.get("event") != "pull_request" or run.get("path") != ".github/workflows/ci.yml":
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
        if same_source(pr, run, api.repository):
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


def watch(api: GitHub, pr_number: int, *, budget: float = 3300, interval: float = 30,
          clock=time.monotonic, sleep=time.sleep, publisher=PUBLISH.publish) -> None:
    deadline = clock() + budget
    adopted = None
    last_data = None
    last_pr = None
    evidence = Evidence(api)
    completed_observations = 0
    last_body = None

    def publish(data, pr, run, *, create, reset=False):
        nonlocal last_body
        expected = generation(pr, run)

        def is_current():
            live_pr, live_run = current(api, pr_number)
            return live_pr.get("state") == "open" and generation(live_pr, live_run) == expected

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
            identity = generation(pr, run)
            if identity != adopted:
                completed_observations = 0
                last_data, last_pr = None, None
                evidence = Evidence(api)
                # Clear obsolete failures before downloading evidence for the replacement attempt.
                if not run or run["status"] != "completed":
                    pending = {"status": "in_progress", "head_sha": pr["head"]["sha"], "run_attempt": run["run_attempt"] if run else 1}
                    if not publish(pending, pr, run, create=False, reset=True):
                        sleep(min(interval, max(0, deadline - clock())))
                        continue
                adopted = identity
            data = snapshot(api, pr, run, evidence)
            create = bool(RENDER.actionable_jobs(data) or data.get("conclusion") in RENDER.BAD)
            if not publish(data, pr, run, create=create):
                sleep(min(interval, max(0, deadline - clock())))
                continue
            last_data, last_pr = data, pr
            if run and run["status"] == "completed":
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
            if pr.get("state") == "open" and generation(pr, run) == adopted:
                last_data["incomplete"] = True
                publish(last_data, last_pr, run, create=False)
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
