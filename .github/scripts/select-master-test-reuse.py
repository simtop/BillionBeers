#!/usr/bin/env python3
"""Select a fully verified PR test result for an exact post-merge master tree.

This is an optimization only: every lookup or validation failure selects the full suite.
"""

from __future__ import annotations

import io
import json
import os
import re
import stat
import sys
import urllib.parse
import urllib.request
import urllib.error
import zipfile

MAX_ARCHIVE = 64 * 1024
MAX_JSON = 16 * 1024
ARTIFACT_PREFIX = "verified-master-test-result-"
REQUIRED_JOBS = {
    "Code Style Formatting Check", "Detect change scope", "Secret Scan (gitleaks)",
    "Static Analysis (Detekt)", "Android Lint", "Dependency Guard", "Unit Tests",
    "Web Tests (Wasm/static)", "Screenshot Tests (Paparazzi)",
    "Instrumented Tests (Gradle Managed Device)", "Native Tests (Apple)", "CI Gate",
}
# These jobs belong to every workflow run but have conditional PR outcomes.
CONDITIONAL_JOB_CONCLUSIONS = {
    "Dependency API Compatibility": {"success", "skipped"},
    "Track failed master CI run": {"skipped"},
}


class ReuseRejected(ValueError):
    """A fixed validation message that is safe to include in CI logs."""


class GitHub:
    def __init__(self, token: str, repository: str):
        self.base = f"https://api.github.com/repos/{repository}"
        self.token = token

    def get(self, path: str):
        url = f"{self.base}/{path}" if path else self.base
        request = urllib.request.Request(
            url,
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28"},
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read(2 * 1024 * 1024 + 1)
        if len(data) > 2 * 1024 * 1024:
            raise ReuseRejected("GitHub API response exceeds 2 MiB")
        return json.loads(data.decode("utf-8"))

    def pages(self, path: str, key: str | None):
        result = []
        separator = "&" if "?" in path else "?"
        for page in range(1, 11):
            response = self.get(f"{path}{separator}per_page=100&page={page}")
            items = response if key is None else response.get(key)
            if not isinstance(items, list):
                raise ReuseRejected("GitHub API returned malformed pagination data")
            result.extend(items)
            if len(items) < 100:
                return result
        raise ReuseRejected("GitHub API pagination exceeded safety bound")

    def download(self, path: str):
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
                raise ReuseRejected("non-HTTPS artifact redirect") from error
            # Signed storage URL: never forward the repository token to a different host.
            response = urllib.request.urlopen(location, timeout=20)
        with response:
            data = response.read(MAX_ARCHIVE + 1)
        if len(data) > MAX_ARCHIVE:
            raise ReuseRejected("artifact exceeds size limit")
        return data


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def valid_sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ReuseRejected("proof JSON contains a duplicate key")
        result[key] = value
    return result


def load_proof(api, run, pr, repository):
    name = f"{ARTIFACT_PREFIX}{run['id']}-{run['run_attempt']}"
    artifacts = [a for a in api.pages(f"actions/runs/{run['id']}/artifacts", "artifacts")
                 if a.get("name") == name]
    if len(artifacts) != 1:
        raise ReuseRejected("proof artifact is missing or duplicated")
    artifact = artifacts[0]
    size = artifact.get("size_in_bytes")
    if artifact.get("expired") or type(size) is not int or not 0 <= size <= MAX_ARCHIVE:
        raise ReuseRejected("proof artifact is expired or too large")
    artifact_run = artifact.get("workflow_run")
    if isinstance(artifact_run, dict):
        artifact_identity = {
            "id": run.get("id"),
            "repository_id": pr.get("base", {}).get("repo", {}).get("id"),
            "head_repository_id": run.get("head_repository", {}).get("id"),
            "head_sha": run.get("head_sha"),
        }
        if any(artifact_run.get(key) != value for key, value in artifact_identity.items()):
            raise ReuseRejected("proof artifact belongs to another workflow run or repository")
    content = api.download(f"actions/artifacts/{artifact['id']}/zip")
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        entries = archive.infolist()
        if len(entries) != 1 or entries[0].filename != "verified-master-test-result.json":
            raise ReuseRejected("proof archive has unexpected contents")
        entry = entries[0]
        if entry.is_dir() or entry.file_size > MAX_JSON or stat.S_ISLNK(entry.external_attr >> 16):
            raise ReuseRejected("proof archive member is unsafe")
        raw = archive.read(entry)
    if len(raw) != entry.file_size:
        raise ReuseRejected("proof archive length mismatch")
    proof = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_json_object)
    if not isinstance(proof, dict) or proof.get("schema") != 1 or type(proof.get("schema")) is not int:
        raise ReuseRejected("unsupported proof schema")
    expected = {
        "repository": repository,
        "repository_id": pr["base"]["repo"]["id"],
        "pr_number": pr["number"],
        "pr_head": pr["head"]["sha"],
        "run_id": run["id"],
        "run_attempt": 1,
        "event": "pull_request",
        "workflow": ".github/workflows/ci.yml",
    }
    if any(proof.get(k) != v for k, v in expected.items()):
        raise ReuseRejected("proof identity does not match source run")
    for key in ("checkout_sha", "tree_sha", "workflow_blob"):
        if not valid_sha(proof.get(key)):
            raise ReuseRejected("proof contains malformed git identity")
    if not valid_sha(proof.get("base_sha")):
        raise ReuseRejected("proof has a malformed PR base SHA")
    # The pull_request workflow checks out GitHub's synthetic merge commit. Verify that the
    # claimed checkout really joins this run's recorded base and PR head; then derive the tree and
    # workflow blob from GitHub's commit/content APIs instead of trusting artifact JSON claims.
    checkout = api.get(f"commits/{proof['checkout_sha']}")
    parents = [parent.get("sha") for parent in checkout.get("parents", [])]
    expected_parents = [proof["base_sha"], proof["pr_head"]]
    if parents != expected_parents:
        raise ReuseRejected("proof checkout is not the source run's synthetic PR merge commit")
    if checkout.get("commit", {}).get("tree", {}).get("sha") != proof["tree_sha"]:
        raise ReuseRejected("proof tree SHA does not match the checkout commit")
    checkout_workflow = api.get(
        f"contents/.github/workflows/ci.yml?ref={urllib.parse.quote(proof['checkout_sha'])}"
    )
    if checkout_workflow.get("sha") != proof["workflow_blob"]:
        raise ReuseRejected("proof workflow blob does not match the checkout commit")
    base_workflow = api.get(
        f"contents/.github/workflows/ci.yml?ref={urllib.parse.quote(proof['base_sha'])}"
    )
    if base_workflow.get("sha") != proof["workflow_blob"]:
        raise ReuseRejected("source workflow differs from the trusted PR base workflow")
    return proof


def validate_jobs(api, run):
    jobs = api.pages(f"actions/runs/{run['id']}/jobs", "jobs")
    by_name = {}
    for job in jobs:
        by_name.setdefault(job.get("name"), []).append(job)
    expected_jobs = REQUIRED_JOBS | CONDITIONAL_JOB_CONCLUSIONS.keys()
    if expected_jobs - by_name.keys():
        raise ReuseRejected("source run has missing expected jobs")
    if by_name.keys() - expected_jobs:
        raise ReuseRejected("source run has unexpected jobs")
    if any(len(items) != 1 for items in by_name.values()):
        raise ReuseRejected("source run has duplicate jobs")
    for name, items in by_name.items():
        job = items[0]
        allowed_conclusions = CONDITIONAL_JOB_CONCLUSIONS.get(name, {"success"})
        if job.get("status") != "completed" or job.get("conclusion") not in allowed_conclusions:
            raise ReuseRejected(f"source job has an ineligible status or conclusion: {name}")
    gate = by_name["CI Gate"][0]
    steps = {step.get("name"): step for step in gate.get("steps", [])}
    for name in ("Checkout tested source for the verified result proof",
                 "Record verified test result", "Upload verified test result"):
        if steps.get(name, {}).get("conclusion") != "success":
            raise ReuseRejected(f"proof step did not succeed: {name}")


def select(api, repository, current_sha, event, ref):
    if event not in {"push", "workflow_dispatch"} or ref != "refs/heads/master" or not valid_sha(current_sha):
        return False
    repo = api.get("")
    repo_id = repo.get("id")
    if not isinstance(repo_id, int):
        raise ReuseRejected("repository identity unavailable")
    commit = api.get(f"commits/{current_sha}")
    tree_sha = commit.get("commit", {}).get("tree", {}).get("sha")
    workflow = api.get("contents/.github/workflows/ci.yml?ref=" + current_sha)
    workflow_blob = workflow.get("sha")
    if not valid_sha(tree_sha) or not valid_sha(workflow_blob):
        raise ReuseRejected("current master tree/workflow identity unavailable")
    candidates = api.pages(f"commits/{current_sha}/pulls", None)
    prs = []
    for candidate in candidates:
        # The commit-to-PR listing omits merged state; fetch the full resource before deciding.
        pr = api.get(f"pulls/{candidate['number']}")
        if (pr.get("merged") is True and pr.get("merge_commit_sha") == current_sha
                and pr.get("base", {}).get("ref") == "master"
                and (pr.get("base", {}).get("repo") or {}).get("full_name") == repository
                and (pr.get("base", {}).get("repo") or {}).get("id") == repo_id
                and (pr.get("head", {}).get("repo") or {}).get("full_name") == repository
                and (pr.get("head", {}).get("repo") or {}).get("id") == repo_id):
            prs.append(pr)
    if len(prs) != 1:
        raise ReuseRejected("current master commit is not one unambiguous same-repository PR merge")
    pr = api.get(f"pulls/{prs[0]['number']}")
    if (pr.get("merged") is not True or pr.get("merge_commit_sha") != current_sha
            or pr.get("head", {}).get("sha") != prs[0].get("head", {}).get("sha")):
        raise ReuseRejected("merged PR identity changed during lookup")
    merge_parents = [parent.get("sha") for parent in commit.get("parents", [])]
    if not merge_parents:
        raise ReuseRejected("merged master commit has no verifiable first parent")
    query = urllib.parse.urlencode({"event": "pull_request", "head_sha": pr["head"]["sha"]})
    runs = api.pages(f"actions/workflows/ci.yml/runs?{query}", "workflow_runs")
    if any(not isinstance(run.get("head_sha"), str) for run in runs):
        raise ReuseRejected("PR run lookup returned a run without a head SHA")
    if any(run.get("head_sha") != pr["head"]["sha"] for run in runs):
        raise ReuseRejected("PR run lookup returned a run for another head")
    if any(type(run.get("run_number")) is not int or type(run.get("id")) is not int for run in runs):
        raise ReuseRejected("PR run lookup returned an unorderable run identity")
    matching = runs
    if not matching:
        raise ReuseRejected("no eligible PR CI run for merged head")
    latest = max(matching, key=lambda r: (r.get("run_number", -1), r.get("id", -1)))
    if (latest.get("event") != "pull_request" or latest.get("path") != ".github/workflows/ci.yml"
            or (latest.get("head_repository") or {}).get("id") != repo_id
            or (latest.get("pull_requests") and not any(
                p.get("number") == pr["number"] for p in latest["pull_requests"]
            ))):
        raise ReuseRejected("newest PR CI run has an unexpected workflow, event, repository, or PR identity")
    if (latest.get("run_attempt") != 1 or latest.get("status") != "completed"
            or latest.get("conclusion") != "success"):
        raise ReuseRejected("newest PR CI run for this head is not successful")
    validate_jobs(api, latest)
    proof = load_proof(api, latest, pr, repository)
    if proof["base_sha"] != merge_parents[0]:
        raise ReuseRejected("tested PR base differs from the merged master commit's first parent")
    if proof["tree_sha"] != tree_sha or proof["workflow_blob"] != workflow_blob:
        raise ReuseRejected("tested tree or workflow differs from current master")
    return True, latest["id"], pr["number"]


def main():
    outputs = os.environ.get("GITHUB_OUTPUT")
    values = {"eligible": "false", "source_run_id": "", "source_pr": ""}
    if os.environ.get("FORCE_FULL") == "true":
        print("Forced full validation requested; skipping verified-result lookup.")
    else:
        try:
            api = GitHub(os.environ["GH_TOKEN"], os.environ["REPO"])
            selected = select(
                api, os.environ["REPO"], os.environ["GITHUB_SHA"], os.environ["EVENT_NAME"], os.environ["GITHUB_REF"]
            )
            if selected:
                values["eligible"], values["source_run_id"], values["source_pr"] = "true", selected[1], selected[2]
        except Exception as error:  # API, JSON, artifact, or proof failure must only cost CI time.
            reason = str(error) if isinstance(error, ReuseRejected) else type(error).__name__
            print(f"::warning::Post-merge test reuse unavailable; running full suite ({reason}).", file=sys.stderr)
    if outputs:
        with open(outputs, "a", encoding="utf-8") as stream:
            stream.write(f"eligible={values['eligible']}\n")
            stream.write(f"source_run_id={values['source_run_id']}\n")
            stream.write(f"source_pr={values['source_pr']}\n")
    print(f"verified-result-reuse={values['eligible']}")


if __name__ == "__main__":
    main()
