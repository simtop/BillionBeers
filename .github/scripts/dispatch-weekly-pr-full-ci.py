#!/usr/bin/env python3
"""Dispatch a full CI run against the merge ref of every open PR."""

from __future__ import annotations

import json
import os
import sys
import urllib.request

MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_PAGES = 20


class GitHub:
    def __init__(self, repository: str, token: str):
        owner, separator, name = repository.partition("/")
        if not separator or not owner or not name or "/" in name:
            raise ValueError("REPOSITORY must be owner/repository")
        self.base = f"https://api.github.com/repos/{repository}"
        self.repository = repository
        self.token = token

    def request(self, path: str, payload: dict | None = None):
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        request = urllib.request.Request(
            f"{self.base}/{path}",
            data=data,
            method="POST" if data is not None else "GET",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                **({"Content-Type": "application/json"} if data is not None else {}),
            },
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            if data is not None:
                return response.status
            content = response.read(MAX_RESPONSE_BYTES + 1)
        if len(content) > MAX_RESPONSE_BYTES:
            raise ValueError("GitHub API response exceeds 2 MiB")
        return json.loads(content.decode("utf-8"))

    def open_pull_requests(self) -> list[dict]:
        result = []
        for page in range(1, MAX_PAGES + 1):
            response = self.request(f"pulls?state=open&per_page=100&page={page}")
            if not isinstance(response, list):
                raise ValueError("GitHub API returned malformed pull-request data")
            result.extend(response)
            if len(response) < 100:
                return result
        raise ValueError("Open pull-request pagination exceeded safety bound")

    def dispatch_full_ci(self, pull_request_number: int) -> None:
        self.request(
            "actions/workflows/ci.yml/dispatches",
            {
                "ref": "master",
                "inputs": {
                    "force_full": "true",
                    "pull_request_number": str(pull_request_number),
                },
            },
        )


def eligible_pull_requests(pull_requests: list[dict], repository: str) -> list[dict]:
    return [
        pr for pr in pull_requests
        if pr.get("state") == "open"
        and pr.get("base", {}).get("ref") in {"main", "master"}
        and pr.get("base", {}).get("repo", {}).get("full_name") == repository
        and type(pr.get("number")) is int
    ]


def main() -> int:
    try:
        repository = os.environ["REPOSITORY"]
        api = GitHub(repository, os.environ["GH_TOKEN"])
        pull_requests = api.open_pull_requests()
    except Exception as error:
        print(f"::error::Could not list open pull requests ({type(error).__name__}).", file=sys.stderr)
        return 1

    eligible = eligible_pull_requests(pull_requests, repository)
    print(
        f"Open PRs: {len(pull_requests)}; PRs scheduled for full CI: {len(eligible)}."
    )
    failures = []
    for pr in eligible:
        number = pr.get("number", "unknown")
        try:
            api.dispatch_full_ci(number)
            print(f"PR #{number}: dispatched full CI for its merge ref.", flush=True)
        except Exception as error:
            failures.append(number)
            print(
                f"::error::PR #{number}: full CI dispatch failed ({type(error).__name__}).",
                file=sys.stderr,
                flush=True,
            )

    if failures:
        print(f"::error::Failed to dispatch full CI for PRs: {', '.join(map(str, failures))}.", file=sys.stderr)
        return 1
    print(f"Full CI dispatched for {len(eligible)} PR(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
