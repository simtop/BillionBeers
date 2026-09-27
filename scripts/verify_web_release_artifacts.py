#!/usr/bin/env python3
"""Create or verify a self-contained Web release-confidence packet."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path, PurePosixPath

from verify_web_static import VerificationError, verify_distribution

SCHEMA = 1
TARGET = "web-wasm"
PACKAGE_KIND = "wasmJsBrowserDistribution"
PROVENANCE_KEYS = (
    "GITHUB_REPOSITORY",
    "GITHUB_RUN_ID",
    "GITHUB_RUN_ATTEMPT",
    "GITHUB_JOB",
    "GITHUB_SHA",
)
CLAIMS = [
    "wasm_browser_tests",
    "production_distribution_built",
    "relative_static_assets_verified",
    "chromium_packaged_smoke",
]
BLOCKERS = [
    "deployed_host_headers_cache_csp",
    "github_pages_behavior",
    "safari_firefox_support",
    "upstream_image_cors_or_proxy",
    "signing_or_store_delivery",
    "physical_device_evidence",
    "upgrade_or_rollback_behavior",
    "android_desktop_ios_release_readiness",
]


def _git_output(repository: Path, *arguments: str) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(repository), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise VerificationError(f"could not read Git provenance: {error}") from error
    return result.stdout.strip()


def _source_provenance(repository: Path) -> tuple[str, str]:
    revision = _git_output(repository, "rev-parse", "HEAD")
    working_tree = "dirty" if _git_output(repository, "status", "--porcelain") else "clean"
    return revision, working_tree


def _relative_path(value: str) -> str:
    path = PurePosixPath(value)
    if not value or "\\" in value or path.is_absolute() or ".." in path.parts:
        raise VerificationError(f"unsafe packet path: {value}")
    normalized = path.as_posix()
    if normalized in {"", "."}:
        raise VerificationError(f"unsafe packet path: {value}")
    return normalized


def _files(root: Path) -> list[Path]:
    if not root.is_dir():
        raise VerificationError(f"distribution directory does not exist: {root}")
    paths: list[Path] = []
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_symlink():
            raise VerificationError(f"distribution contains a symlink: {path.relative_to(root)}")
        if path.is_file():
            paths.append(path)
        elif not path.is_dir():
            raise VerificationError(f"distribution contains a non-file entry: {path.relative_to(root)}")
    if not paths:
        raise VerificationError(f"distribution is empty: {root}")
    return paths


def _file_records(root: Path) -> list[dict[str, int | str]]:
    records: list[dict[str, int | str]] = []
    for path in _files(root):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        records.append(
            {
                "path": path.relative_to(root).as_posix(),
                "bytes": path.stat().st_size,
                "sha256": digest,
            }
        )
    return records


def tree_digest(records: list[dict[str, int | str]]) -> str:
    digest = hashlib.sha256()
    for record in records:
        digest.update(
            f"{record['path']}\0{record['bytes']}\0{record['sha256']}\n".encode("utf-8")
        )
    return digest.hexdigest()


def _provenance(
    repository: Path,
    source_revision: str,
) -> dict[str, str]:
    values = {key: os.environ[key] for key in PROVENANCE_KEYS if os.environ.get(key)}
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        missing = [key for key in PROVENANCE_KEYS if not os.environ.get(key)]
        if missing:
            raise VerificationError(
                "GitHub Actions provenance is incomplete: " + ", ".join(missing)
            )
        if values["GITHUB_SHA"] != source_revision:
            raise VerificationError(
                f"GITHUB_SHA {values['GITHUB_SHA']} does not match checked-out revision {source_revision}"
            )
    return values


def _copy_distribution(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=False)
    for source_path in _files(source):
        relative = source_path.relative_to(source)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_path, target)


def _assert_separate(source: Path, output: Path) -> None:
    source = source.resolve()
    output = output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise VerificationError("packet output must be separate from the distribution")


def create_packet(
    distribution: Path,
    output: Path,
    *,
    repository: Path,
    browser_smoke_passed: bool,
    source_revision: str | None = None,
    working_tree: str | None = None,
    provenance: dict[str, str] | None = None,
) -> dict[str, object]:
    distribution = distribution.resolve()
    output = output.resolve()
    _assert_separate(distribution, output)
    if not browser_smoke_passed:
        raise VerificationError("release packet requires a successful browser smoke")
    if output.exists() and any(output.iterdir()):
        raise VerificationError(f"packet output is not empty: {output}")

    checked_assets = verify_distribution(distribution)
    source_revision, working_tree = (
        (source_revision, working_tree)
        if source_revision is not None and working_tree is not None
        else _source_provenance(repository)
    )
    provenance = provenance if provenance is not None else _provenance(repository, source_revision)
    output.mkdir(parents=True, exist_ok=True)
    snapshot = output / "distribution"
    _copy_distribution(distribution, snapshot)
    snapshot_assets = verify_distribution(snapshot)
    if snapshot_assets != checked_assets:
        raise VerificationError("distribution changed while creating the release packet")
    records = _file_records(snapshot)
    manifest: dict[str, object] = {
        "schema": SCHEMA,
        "target": TARGET,
        "package_kind": PACKAGE_KIND,
        "distribution_root": "distribution",
        "entrypoint": "index.html",
        "source_revision": source_revision,
        "working_tree": working_tree,
        "provenance": provenance,
        "producer": {
            "command": "make web-verify",
            "distribution_task": ":web-app:wasmJsBrowserDistribution",
        },
        "checks": {
            "static_distribution": "passed",
            "browser_smoke": "passed",
        },
        "claims": CLAIMS,
        "blockers": BLOCKERS,
        "verified_assets": checked_assets,
        "file_count": len(records),
        "total_bytes": sum(int(record["bytes"]) for record in records),
        "tree_sha256": tree_digest(records),
        "files": records,
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def _packet_files(packet: Path, distribution_root: str, manifest_name: str) -> set[str]:
    actual: set[str] = set()
    for path in sorted(packet.rglob("*"), key=lambda item: item.relative_to(packet).as_posix()):
        if path.is_symlink():
            raise VerificationError(f"packet contains a symlink: {path.relative_to(packet)}")
        if not path.is_file():
            continue
        relative = path.relative_to(packet).as_posix()
        if relative != manifest_name and not relative.startswith(f"{distribution_root}/"):
            raise VerificationError(f"packet contains an unexpected file: {relative}")
        actual.add(relative)
    return actual


def verify_packet(packet: Path) -> dict[str, object]:
    packet = packet.resolve()
    manifest_path = packet / "manifest.json"
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise VerificationError(f"packet manifest does not exist: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise VerificationError(f"could not read packet manifest: {error}") from error
    if manifest.get("schema") != SCHEMA or manifest.get("target") != TARGET:
        raise VerificationError("packet manifest has an unsupported schema or target")
    distribution_root = _relative_path(str(manifest.get("distribution_root", "")))
    entrypoint = _relative_path(str(manifest.get("entrypoint", "")))
    manifest_files = manifest.get("files")
    if not isinstance(manifest_files, list) or not manifest_files:
        raise VerificationError("packet manifest has no files")
    expected: list[dict[str, int | str]] = []
    for record in manifest_files:
        if not isinstance(record, dict):
            raise VerificationError("packet manifest contains an invalid file record")
        path = _relative_path(str(record.get("path", "")))
        if path in {"manifest.json", "distribution"}:
            raise VerificationError(f"packet manifest contains an invalid file path: {path}")
        if not isinstance(record.get("bytes"), int) or not isinstance(record.get("sha256"), str):
            raise VerificationError(f"packet manifest contains an incomplete file record: {path}")
        expected.append({"path": path, "bytes": record["bytes"], "sha256": record["sha256"]})
    if expected != sorted(expected, key=lambda record: str(record["path"])):
        raise VerificationError("packet manifest file records are not sorted")
    distribution = packet / distribution_root
    checked_assets = verify_distribution(distribution, entrypoint)
    records = _file_records(distribution)
    if records != expected:
        raise VerificationError("packet files do not match the manifest")
    if manifest.get("file_count") != len(records):
        raise VerificationError("packet file count does not match the manifest")
    if manifest.get("total_bytes") != sum(int(record["bytes"]) for record in records):
        raise VerificationError("packet byte total does not match the manifest")
    if manifest.get("tree_sha256") != tree_digest(records):
        raise VerificationError("packet tree digest does not match the manifest")
    if manifest.get("verified_assets") != checked_assets:
        raise VerificationError("packet verified assets do not match the distribution")
    expected_packet_files = {"manifest.json"} | {
        f"{distribution_root}/{record['path']}" for record in records
    }
    if _packet_files(packet, distribution_root, "manifest.json") != expected_packet_files:
        raise VerificationError("packet contains missing or extra files")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("distribution", type=Path, nargs="?")
    parser.add_argument("output", type=Path, nargs="?")
    parser.add_argument("--verify-packet", type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--browser-smoke-passed", action="store_true")
    args = parser.parse_args()
    try:
        if args.verify_packet is not None:
            if args.distribution is not None or args.output is not None:
                parser.error("--verify-packet cannot be combined with distribution/output")
            manifest = verify_packet(args.verify_packet)
            print(f"Web release packet verified: {args.verify_packet}")
            print(f"Tree SHA-256: {manifest['tree_sha256']}")
            return 0
        if args.distribution is None or args.output is None:
            parser.error("distribution and output are required unless --verify-packet is used")
        manifest = create_packet(
            args.distribution,
            args.output,
            repository=args.repo_root.resolve(),
            browser_smoke_passed=args.browser_smoke_passed,
        )
    except (OSError, VerificationError) as error:
        print(f"Web release packet failed: {error}", file=sys.stderr)
        return 1
    print(f"Web release packet created: {args.output}")
    print(f"Tree SHA-256: {manifest['tree_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
