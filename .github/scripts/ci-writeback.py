#!/usr/bin/env python3
"""Publish bounded outputs from credential-free CI runs using trusted default-branch code."""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
import sys
import urllib.request
import hashlib
import zipfile
from pathlib import Path, PurePosixPath
from typing import Optional

WORKFLOWS = {
    ".github/workflows/format_fix.yml": "format",
    ".github/workflows/detekt_baseline.yml": "detekt",
    ".github/workflows/record_screenshots.yml": "screenshots",
}
MAX_PATCH_BYTES = 128 * 1024 * 1024
MAX_PATCH_FILES = 20_000
MAX_ARCHIVE_ENTRIES = MAX_PATCH_FILES + 1
MAX_MANIFEST_BYTES = 2 * 1024 * 1024
MAX_SCREENSHOT_BYTES = 32 * 1024 * 1024


class WritebackError(Exception):
    pass


def validate_event(run: dict, repository: str, default_branch: str) -> dict:
    """Accept only a successful same-repository manual run on a non-default branch."""
    kind = WORKFLOWS.get(run.get("path"))
    source_repository = run.get("head_repository") or {}
    run_repository = run.get("repository") or {}
    branch = run.get("head_branch")
    sha = run.get("head_sha")
    attempt = run.get("run_attempt")
    run_id = run.get("id")
    if (kind is None or run.get("event") != "workflow_dispatch"
            or run.get("conclusion") != "success"):
        raise WritebackError("workflow run is not a successful generated-output dispatch")
    if (run_repository.get("full_name") != repository
            or source_repository.get("full_name") != repository
            or run_repository.get("id") != source_repository.get("id")):
        raise WritebackError("workflow run did not originate from this repository")
    if not isinstance(branch, str) or branch == default_branch or branch in {"main", "master"}:
        raise WritebackError("generated output cannot target the default branch")
    if (branch.startswith("-") or ".." in branch or "\\" in branch
            or any(ord(char) < 32 or ord(char) == 127 for char in branch)):
        raise WritebackError("workflow branch name is invalid")
    check_ref = subprocess.run(
        ["git", "check-ref-format", "--branch", branch], capture_output=True, check=False, text=True,
    )
    if check_ref.returncode != 0:
        raise WritebackError("workflow branch name is not a valid git branch")
    if not isinstance(sha, str) or re.fullmatch(r"[0-9a-f]{40}", sha) is None:
        raise WritebackError("workflow source SHA is malformed")
    if type(run_id) is not int or run_id <= 0 or type(attempt) is not int or attempt <= 0:
        raise WritebackError("workflow run identity is malformed")
    return {"kind": kind, "branch": branch, "sha": sha, "run_id": run_id, "attempt": attempt}


def validate_artifact(artifacts: list[dict], run_id: int, attempt: int) -> str:
    expected_name = f"generated-output-{attempt}"
    matching = [artifact for artifact in artifacts if artifact.get("name") == expected_name]
    if len(matching) != 1:
        raise WritebackError("source run must have exactly one generated-output artifact for this attempt")
    artifact = matching[0]
    size = artifact.get("size_in_bytes")
    if (artifact.get("expired") or type(size) is not int or not 1 <= size <= MAX_PATCH_BYTES):
        raise WritebackError("generated-output artifact is expired or exceeds the size limit")
    identity = artifact.get("workflow_run") or {}
    if identity and (identity.get("id") != run_id or identity.get("run_attempt") != attempt):
        raise WritebackError("generated-output artifact belongs to a different workflow attempt")
    return expected_name


def artifacts_for_run(token: str, repository: str, run_id: int) -> list[dict]:
    url = f"https://api.github.com/repos/{repository}/actions/runs/{run_id}/artifacts?per_page=100"
    request = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = response.read(1024 * 1024 + 1)
    if len(payload) > 1024 * 1024:
        raise WritebackError("workflow artifact metadata exceeds the size limit")
    body = json.loads(payload.decode("utf-8"))
    artifacts = body.get("artifacts")
    if not isinstance(artifacts, list):
        raise WritebackError("workflow artifact metadata is malformed")
    return artifacts


def patch_paths(repo: Path, patch: bytes) -> list[str]:
    if len(patch) > MAX_PATCH_BYTES:
        raise WritebackError("generated patch exceeds the size limit")
    if not patch:
        return []
    result = subprocess.run(
        ["git", "apply", "--numstat", "-z"], cwd=repo, input=patch,
        capture_output=True, check=False,
    )
    if result.returncode != 0:
        raise WritebackError("generated patch is not a valid git patch")
    records = result.stdout.split(b"\0")
    paths = []
    for record in records:
        if not record:
            continue
        fields = record.split(b"\t", 2)
        if len(fields) != 3 or not fields[2]:
            raise WritebackError("generated patch contains an unsupported rename or path")
        try:
            path = fields[2].decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise WritebackError("generated patch contains a non-UTF-8 path") from error
        pure = PurePosixPath(path)
        if (pure.is_absolute() or any(part in {"", ".", ".."} for part in path.split("/"))
                or "\\" in path or any(ord(char) < 32 or ord(char) == 127 for char in path)):
            raise WritebackError("generated patch contains an unsafe path")
        paths.append(path)
        if len(paths) > MAX_PATCH_FILES:
            raise WritebackError("generated patch contains too many files")
    if len(set(paths)) != len(paths):
        raise WritebackError("generated patch contains duplicate paths")
    return paths


def path_allowed(kind: str, path: str) -> bool:
    pure = PurePosixPath(path)
    if (pure.is_absolute() or any(part in {"", ".", ".."} for part in path.split("/"))
            or "\\" in path or any(ord(char) < 32 or ord(char) == 127 for char in path)):
        return False
    if kind == "format":
        return path.endswith((".kt", ".kts"))
    if kind == "detekt":
        return PurePosixPath(path).name == "detekt-baseline.xml"
    if kind == "screenshots":
        return "/src/test/snapshots/images/" in f"/{path}" and path.endswith(".png")
    return False


def tracked(repo: Path, path: str) -> bool:
    return subprocess.run(
        ["git", "ls-files", "--error-unmatch", "--", path], cwd=repo,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
    ).returncode == 0


def apply_generated_patch(repo: Path, patch_path: Path, kind: str) -> list[str]:
    """Validate and apply only output paths belonging to the requested generator."""
    if patch_path.is_symlink() or not patch_path.is_file():
        raise WritebackError("generated patch is missing or is not a regular file")
    patch = patch_path.read_bytes()
    paths = patch_paths(repo, patch)
    for path in paths:
        if not path_allowed(kind, path):
            raise WritebackError(f"generated patch path is outside the {kind} allowlist")
        if kind == "format" and not tracked(repo, path):
            raise WritebackError("format patch may only modify tracked source files")
        if kind == "detekt" and not tracked(repo, path) and (repo / path).exists():
            raise WritebackError("new Detekt baseline path already exists outside git")
    if not paths:
        return []
    check = subprocess.run(
        ["git", "apply", "--check", "--binary"], cwd=repo, input=patch,
        capture_output=True, check=False,
    )
    if check.returncode != 0:
        raise WritebackError("generated patch does not apply cleanly to its source revision")
    apply = subprocess.run(
        ["git", "apply", "--binary"], cwd=repo, input=patch,
        capture_output=True, check=False,
    )
    if apply.returncode != 0:
        raise WritebackError("could not apply generated patch")
    for path in paths:
        target = repo / path
        if target.exists() or target.is_symlink():
            metadata = target.lstat()
            if not stat.S_ISREG(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o644:
                raise WritebackError("generated patch contains a non-regular file or changes its mode")
        elif kind not in {"screenshots", "detekt"}:
            raise WritebackError("this generator may not delete tracked files")
    subprocess.run(["git", "add", "--", *paths], cwd=repo, check=True)
    return paths


def screenshot_changes(repo: Path) -> list[tuple[str, str]]:
    result = subprocess.run(
        ["git", "diff", "--name-status", "-z", "--no-renames", "HEAD", "--",
         "**/src/test/snapshots/images/*.png"], cwd=repo, capture_output=True, check=False,
    )
    if result.returncode != 0:
        raise WritebackError("could not inspect recorded screenshot changes")
    fields = result.stdout.split(b"\0")
    changes = []
    for index in range(0, len(fields) - 1, 2):
        if not fields[index]:
            continue
        try:
            status = fields[index].decode("ascii")
            path = fields[index + 1].decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise WritebackError("screenshot output contains a non-text path") from error
        if status not in {"A", "M", "D"} or not path_allowed("screenshots", path):
            raise WritebackError("screenshot output contains an unsupported change")
        changes.append((status, path))
        if len(changes) > MAX_PATCH_FILES:
            raise WritebackError("screenshot output contains too many files")
    untracked = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard", "-z", "--",
         "**/src/test/snapshots/images/*.png"], cwd=repo, capture_output=True, check=False,
    )
    if untracked.returncode != 0:
        raise WritebackError("could not inspect new screenshot files")
    for raw_path in untracked.stdout.split(b"\0"):
        if not raw_path:
            continue
        try:
            path = raw_path.decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise WritebackError("screenshot output contains a non-text path") from error
        if not path_allowed("screenshots", path) or tracked(repo, path):
            raise WritebackError("new screenshot output contains an unsupported path")
        changes.append(("A", path))
        if len(changes) > MAX_PATCH_FILES:
            raise WritebackError("screenshot output contains too many files")
    if len({path for _, path in changes}) != len(changes):
        raise WritebackError("screenshot output contains duplicate paths")
    return changes


def snapshot_screenshots(repo: Path, output_path: Path) -> list[dict]:
    """Package changed PNG contents and deletion markers without transferring Git LFS pointers."""
    changes = screenshot_changes(repo)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = []
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for status, path in changes:
            entry = {"status": status, "path": path}
            if status != "D":
                source = repo / path
                metadata = source.lstat()
                if not stat.S_ISREG(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o644:
                    raise WritebackError("screenshot output is not a regular non-executable file")
                content = source.read_bytes()
                if len(content) > MAX_SCREENSHOT_BYTES:
                    raise WritebackError("screenshot output file exceeds the size limit")
                entry["size"] = len(content)
                entry["sha256"] = hashlib.sha256(content).hexdigest()
                archive.writestr(f"files/{path}", content)
            manifest.append(entry)
        archive.writestr("manifest.json", json.dumps(
            {"schema": 1, "files": manifest}, sort_keys=True, indent=2,
        ) + "\n")
    if output_path.stat().st_size > MAX_PATCH_BYTES:
        output_path.unlink(missing_ok=True)
        raise WritebackError("screenshot archive exceeds the size limit")
    return manifest


def apply_screenshot_archive(repo: Path, archive_path: Path) -> list[str]:
    if archive_path.is_symlink() or not archive_path.is_file():
        raise WritebackError("screenshot archive is missing or is not a regular file")
    if archive_path.stat().st_size > MAX_PATCH_BYTES:
        raise WritebackError("screenshot archive exceeds the size limit")
    try:
        with zipfile.ZipFile(archive_path) as archive:
            entries = archive.infolist()
            if len(entries) > MAX_ARCHIVE_ENTRIES:
                raise WritebackError("screenshot archive contains too many entries")
            if sum(entry.file_size for entry in entries) > MAX_PATCH_BYTES:
                raise WritebackError("screenshot archive expands beyond the size limit")
            names = [entry.filename for entry in entries]
            if len(set(names)) != len(names) or "manifest.json" not in names:
                raise WritebackError("screenshot archive has duplicate or missing entries")
            for entry in entries:
                if (entry.is_dir() or stat.S_ISLNK(entry.external_attr >> 16)
                        or entry.file_size > MAX_SCREENSHOT_BYTES + MAX_MANIFEST_BYTES):
                    raise WritebackError("screenshot archive contains an unsafe entry")
            raw_manifest = archive.read("manifest.json")
            if len(raw_manifest) > MAX_MANIFEST_BYTES:
                raise WritebackError("screenshot manifest exceeds the size limit")
            document = json.loads(raw_manifest.decode("utf-8"))
            if (not isinstance(document, dict) or document.get("schema") != 1
                    or not isinstance(document.get("files"), list)
                    or len(document["files"]) > MAX_PATCH_FILES):
                raise WritebackError("screenshot manifest is malformed")
            expected_members = {"manifest.json"}
            normalized = []
            seen_paths = set()
            for item in document["files"]:
                if not isinstance(item, dict):
                    raise WritebackError("screenshot manifest entry is malformed")
                status, path = item.get("status"), item.get("path")
                if (status not in {"A", "M", "D"} or not isinstance(path, str)
                        or not path_allowed("screenshots", path) or path in seen_paths):
                    raise WritebackError("screenshot manifest contains an invalid path or status")
                seen_paths.add(path)
                target = repo / path
                if status in {"M", "D"} and not tracked(repo, path):
                    raise WritebackError("screenshot update or deletion must target a tracked image")
                if status == "A" and (tracked(repo, path) or target.exists() or target.is_symlink()):
                    raise WritebackError("new screenshot path already exists in the source tree")
                if status == "D":
                    if f"files/{path}" in names:
                        raise WritebackError("deleted screenshot unexpectedly has file contents")
                else:
                    member = f"files/{path}"
                    expected_members.add(member)
                    content = archive.read(member)
                    if (len(content) != item.get("size") or len(content) > MAX_SCREENSHOT_BYTES
                            or hashlib.sha256(content).hexdigest() != item.get("sha256")):
                        raise WritebackError("screenshot content does not match its manifest")
                normalized.append((status, path, None if status == "D" else content))
            if set(names) != expected_members:
                raise WritebackError("screenshot archive contains unlisted files")
    except (zipfile.BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise WritebackError("screenshot archive is malformed") from error

    for status, path, content in normalized:
        target = repo / path
        parent = target.parent
        if not parent.resolve().is_relative_to(repo.resolve()):
            raise WritebackError("screenshot target escapes the trusted checkout")
        if parent.is_symlink():
            raise WritebackError("screenshot target has a symlink parent")
        if status == "D":
            if target.is_symlink() or not target.is_file():
                raise WritebackError("screenshot deletion target is not a regular file")
            target.unlink()
        else:
            parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            target.chmod(0o644)
    paths = [path for _, path, _ in normalized]
    if not paths:
        return []
    subprocess.run(["git", "lfs", "install", "--local"], cwd=repo, check=True)
    subprocess.run(["git", "add", "--", *paths], cwd=repo, check=True)
    return paths


def git(repo: Path, *args: str, capture: bool = False) -> str:
    result = subprocess.run(
        ["git", *args], cwd=repo, capture_output=capture, check=False, text=capture,
    )
    if result.returncode != 0:
        raise WritebackError(f"git {args[0]} failed")
    return result.stdout.strip() if capture else ""


def remote_sha(repo: Path, ref: str) -> str | None:
    output = git(repo, "ls-remote", "--refs", "origin", ref, capture=True)
    return output.split("\t", 1)[0] if output else None


def publish(repo: Path, kind: str, branch: str, sha: str, patch_path: Optional[Path],
            default_branch: str, repository_name: str,
            screenshot_archive: Optional[Path] = None) -> bool:
    if kind not in {"format", "detekt", "screenshots"}:
        raise WritebackError("unknown generated-output type")
    if branch == default_branch or branch in {"main", "master"} or branch.startswith("-"):
        raise WritebackError("refusing to publish generated output to a default or invalid branch")
    git(repo, "fetch", "--no-tags", "origin", f"+refs/heads/{branch}:refs/remotes/origin/ci-writeback-source")
    if git(repo, "rev-parse", "refs/remotes/origin/ci-writeback-source", capture=True) != sha:
        raise WritebackError("source branch moved after generation; dispatch the workflow again")
    if git(repo, "status", "--porcelain", capture=True):
        raise WritebackError("trusted writeback checkout is not clean")

    output_branch = f"{branch}_screenshots" if kind == "screenshots" else branch
    lease_sha = remote_sha(repo, f"refs/heads/{output_branch}") if kind == "screenshots" else None
    git(repo, "switch", "--force-create", output_branch, sha)
    if kind == "screenshots":
        if screenshot_archive is None:
            raise WritebackError("screenshot output archive is required")
        paths = apply_screenshot_archive(repo, screenshot_archive)
    else:
        if patch_path is None:
            raise WritebackError("generated patch is required")
        paths = apply_generated_patch(repo, patch_path, kind)
    if not paths or not git(repo, "diff", "--cached", "--name-only", capture=True):
        print("No generated changes to publish.")
        return False

    messages = {
        "format": "chore: apply Spotless formatting",
        "detekt": "chore: regenerate Detekt baselines",
        "screenshots": f"chore: record screenshots for {branch}",
    }
    git(repo, "config", "user.name", "github-actions[bot]")
    git(repo, "config", "user.email", "github-actions[bot]@users.noreply.github.com")
    subprocess.run(
        ["git", "-c", "core.hooksPath=/dev/null", "commit", "-m", messages[kind]],
        cwd=repo, check=True,
    )
    if remote_sha(repo, f"refs/heads/{branch}") != sha:
        raise WritebackError("source branch changed during writeback; no output was pushed")
    if kind == "screenshots":
        git(repo, "lfs", "push", "origin", "HEAD")
        lease = f"--force-with-lease=refs/heads/{output_branch}:{lease_sha or ''}"
        git(repo, "push", lease, "origin", f"HEAD:refs/heads/{output_branch}")
        create_screenshot_pr(repo, repository_name, branch, output_branch)
    else:
        git(repo, "push", "origin", f"HEAD:refs/heads/{branch}")
    print(f"Published {len(paths)} generated file(s) for {branch}.")
    return True


def create_screenshot_pr(repo: Path, repository_name: str, base: str, head: str) -> None:
    owner = repository_name.split("/", 1)[0]
    existing = subprocess.run(
        ["gh", "pr", "list", "--repo", repository_name, "--head", f"{owner}:{head}",
         "--base", base, "--json", "number", "--jq", ".[0].number // empty"],
        cwd=repo, capture_output=True, check=True, text=True,
    ).stdout.strip()
    if existing:
        print(f"Updated screenshot pull request #{existing}.")
        return
    subprocess.run(
        ["gh", "pr", "create", "--repo", repository_name,
         "--title", f"Screenshot updates for {base}",
         "--body", f"Generated Paparazzi snapshots for branch `{base}`.",
         "--base", base, "--head", head],
        cwd=repo, check=True,
    )


def write_outputs(values: dict) -> None:
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("resolve")
    snapshot_parser = commands.add_parser("snapshot-screenshots")
    snapshot_parser.add_argument("--output", required=True, type=Path)
    publish_parser = commands.add_parser("publish")
    publish_parser.add_argument("--kind", required=True)
    publish_parser.add_argument("--branch", required=True)
    publish_parser.add_argument("--sha", required=True)
    publish_parser.add_argument("--patch", type=Path)
    publish_parser.add_argument("--screenshots-archive", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "snapshot-screenshots":
            manifest = snapshot_screenshots(Path.cwd(), args.output)
            print(f"Packaged {len(manifest)} screenshot change(s).")
        elif args.command == "resolve":
            event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
            values = validate_event(
                event["workflow_run"], os.environ["GITHUB_REPOSITORY"],
                os.environ["DEFAULT_BRANCH"],
            )
            values["artifact_name"] = validate_artifact(
                artifacts_for_run(os.environ["GITHUB_TOKEN"], os.environ["GITHUB_REPOSITORY"], values["run_id"]),
                values["run_id"], values["attempt"],
            )
            write_outputs(values)
            print(f"Validated {values['kind']} output from run {values['run_id']}.")
        else:
            repo = Path.cwd()
            publish(
                repo, args.kind, args.branch, args.sha, args.patch,
                os.environ["DEFAULT_BRANCH"], os.environ["GITHUB_REPOSITORY"],
                args.screenshots_archive,
            )
    except (OSError, KeyError, ValueError, subprocess.CalledProcessError, WritebackError) as error:
        message = " ".join(str(error).splitlines())[:300]
        print(f"::error::{message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
