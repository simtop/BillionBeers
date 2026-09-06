#!/usr/bin/env python3
"""Create or update one bounded CI diagnosis comment on a pull request."""

from __future__ import annotations

import argparse
import os
import sys
import urllib.error
import urllib.request
import json

MAX_COMMENT_LENGTH = 8_000


def api_request(token: str, url: str, method: str = "GET", payload: object | None = None) -> object:
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def bounded_body(marker: str, body: str) -> str:
    body = body.strip()
    if marker not in body:
        body = f"{marker}\n{body}"
    if len(body) <= MAX_COMMENT_LENGTH:
        return body + "\n"
    return body[: MAX_COMMENT_LENGTH - 40].rstrip() + "\n\n_Comment truncated; open the CI run for full details._\n"


def publish(token: str, repository: str, pr_number: str, marker: str, body: str) -> str:
    base = f"https://api.github.com/repos/{repository}/issues/{pr_number}/comments"
    comments = api_request(token, f"{base}?per_page=100")
    existing = next((comment for comment in comments if marker in comment.get("body", "")), None)
    content = bounded_body(marker, body)
    if existing:
        result = api_request(token, existing["url"], "PATCH", {"body": content})
        return result["html_url"]
    result = api_request(token, base, "POST", {"body": content})
    return result["html_url"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--body-file", type=argparse.FileType("r"), default=sys.stdin)
    parser.add_argument("--marker", required=True)
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""))
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--pr-number", default=os.environ.get("PR_NUMBER", ""))
    args = parser.parse_args(argv)
    if not args.token or not args.repository or not args.pr_number:
        print("CI comment skipped: GitHub token, repository, and PR number are required.", file=sys.stderr)
        return 0
    try:
        url = publish(args.token, args.repository, args.pr_number, args.marker, args.body_file.read())
    except (OSError, urllib.error.HTTPError, urllib.error.URLError, KeyError, json.JSONDecodeError) as error:
        print(f"::warning::Could not publish CI PR comment: {error}", file=sys.stderr)
        return 0
    print(f"Published CI diagnosis comment: {url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
