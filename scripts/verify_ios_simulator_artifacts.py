#!/usr/bin/env python3
"""Create or verify a self-contained unsigned iOS simulator evidence packet."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import plistlib
import shutil
import subprocess
import sys
from pathlib import Path, PurePosixPath


class VerificationError(Exception):
    pass


SCHEMA = 1
TARGET = "ios-simulator"
APP_RELATIVE_PATH = "simulator-app/BillionBeers.app"
REPORT_RELATIVE_PATH = "native-test-reports"
APP_BUNDLE_IDENTIFIER = "com.simtop.billionbeers.ios"
REQUIRED_MODULES = ("core-common", "beer_network", "beer_database", "ios-shared")
REPORT_RELATIVE_DIR = "build/test-results/iosSimulatorArm64Test"
PROVENANCE_KEYS = (
    "GITHUB_REPOSITORY",
    "GITHUB_RUN_ID",
    "GITHUB_RUN_ATTEMPT",
    "GITHUB_JOB",
    "GITHUB_SHA",
)
CLAIMS = [
    "apple_shared_targets_compiled",
    "simulator_framework_linked",
    "unsigned_simulator_host_built",
    "native_simulator_tests_executed",
    "exact_simulator_artifact_identified",
]
BLOCKERS = [
    "physical_device_execution_or_signing",
    "voiceover_and_manual_accessibility",
    "network_image_loading",
    "deployment_or_app_store_delivery",
    "upgrade_or_rollback_behavior",
    "android_desktop_ios_aggregate_release_readiness",
]


def _load_report_validator():
    path = Path(__file__).parents[1] / ".github" / "scripts" / "validate-native-test-reports.py"
    spec = importlib.util.spec_from_file_location("validate_native_test_reports", path)
    if spec is None or spec.loader is None:
        raise VerificationError(f"could not load native report validator: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _validate_reports(root: Path) -> None:
    errors = _load_report_validator().validate(root)
    if errors:
        raise VerificationError("native simulator reports are invalid: " + "; ".join(errors))


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
        raise VerificationError(f"evidence directory does not exist: {root}")
    paths: list[Path] = []
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_symlink():
            raise VerificationError(f"evidence contains a symlink: {path.relative_to(root)}")
        if path.is_file():
            paths.append(path)
        elif not path.is_dir():
            raise VerificationError(f"evidence contains a non-file entry: {path.relative_to(root)}")
    if not paths:
        raise VerificationError(f"evidence directory is empty: {root}")
    return paths


def _copy_tree(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=False)
    for source_path in _files(source):
        target = destination / source_path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_path, target)


def _records(root: Path, prefix: str = "") -> list[dict[str, int | str]]:
    records: list[dict[str, int | str]] = []
    for path in _files(root):
        relative = path.relative_to(root).as_posix()
        records.append(
            {
                "path": f"{prefix}/{relative}" if prefix else relative,
                "bytes": path.stat().st_size,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
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


def _provenance(source_revision: str) -> dict[str, str]:
    values = {key: os.environ[key] for key in PROVENANCE_KEYS if os.environ.get(key)}
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        missing = [key for key in PROVENANCE_KEYS if not os.environ.get(key)]
        if missing:
            raise VerificationError("GitHub Actions provenance is incomplete: " + ", ".join(missing))
        if values["GITHUB_SHA"] != source_revision:
            raise VerificationError(
                f"GITHUB_SHA {values['GITHUB_SHA']} does not match checked-out revision {source_revision}"
            )
    return values


def _validate_app(app: Path) -> list[str]:
    app = app.resolve()
    if not app.is_dir() or app.suffix != ".app":
        raise VerificationError(f"simulator application bundle does not exist: {app}")
    info_path = app / "Info.plist"
    if not info_path.is_file():
        raise VerificationError(f"Info.plist does not exist: {info_path}")
    try:
        info = plistlib.loads(info_path.read_bytes())
    except (OSError, plistlib.InvalidFileException) as error:
        raise VerificationError(f"could not read Info.plist: {error}") from error
    if info.get("CFBundlePackageType") != "APPL":
        raise VerificationError("Info.plist is not an application bundle")
    if info.get("CFBundleIdentifier") != APP_BUNDLE_IDENTIFIER:
        raise VerificationError("Info.plist has an unexpected bundle identifier")
    executable_name = info.get("CFBundleExecutable")
    if not isinstance(executable_name, str) or not executable_name:
        raise VerificationError("Info.plist has no executable name")
    executable = app / executable_name
    if not executable.is_file() or executable.stat().st_size == 0:
        raise VerificationError(f"simulator executable is missing or empty: {executable}")
    framework = app / "Frameworks" / "BillionBeersData.framework"
    if not framework.is_dir() or not _files(framework):
        raise VerificationError(f"embedded simulator framework is missing or empty: {framework}")
    return [
        info_path.relative_to(app).as_posix(),
        executable.relative_to(app).as_posix(),
        framework.relative_to(app).as_posix(),
    ]


def _assert_separate(app: Path, reports: Path, output: Path) -> None:
    output = output.resolve()
    source_roots = [app.resolve()]
    source_roots.extend((reports / module).resolve() for module in REQUIRED_MODULES)
    for source in source_roots:
        if output == source or output in source.parents or source in output.parents:
            raise VerificationError("packet output must be separate from source evidence")


def create_packet(
    app: Path,
    reports: Path,
    output: Path,
    *,
    repository: Path,
    destination: str,
    source_revision: str | None = None,
    working_tree: str | None = None,
    provenance: dict[str, str] | None = None,
) -> dict[str, object]:
    app = app.resolve()
    reports = reports.resolve()
    output = output.resolve()
    if not destination.strip():
        raise VerificationError("simulator destination is required")
    _assert_separate(app, reports, output)
    if output.exists() and any(output.iterdir()):
        raise VerificationError(f"packet output is not empty: {output}")
    _validate_app(app)
    _validate_reports(reports)
    source_revision, working_tree = (
        (source_revision, working_tree)
        if source_revision is not None and working_tree is not None
        else _source_provenance(repository)
    )
    provenance = provenance if provenance is not None else _provenance(source_revision)

    output.mkdir(parents=True, exist_ok=True)
    app_snapshot = output / APP_RELATIVE_PATH
    report_snapshot = output / REPORT_RELATIVE_PATH
    _copy_tree(app, app_snapshot)
    for module in REQUIRED_MODULES:
        source_reports = reports / module / REPORT_RELATIVE_DIR
        target_reports = report_snapshot / module / REPORT_RELATIVE_DIR
        _copy_tree(source_reports, target_reports)

    _validate_app(app_snapshot)
    _validate_reports(report_snapshot)
    records = sorted(
        _records(app_snapshot, APP_RELATIVE_PATH) + _records(report_snapshot, REPORT_RELATIVE_PATH),
        key=lambda record: str(record["path"]),
    )
    manifest: dict[str, object] = {
        "schema": SCHEMA,
        "target": TARGET,
        "configuration": "Debug",
        "sdk": "iphonesimulator",
        "architecture": "arm64",
        "destination": destination,
        "source_revision": source_revision,
        "working_tree": working_tree,
        "provenance": provenance,
        "producer": {
            "compile": "make ios-compile",
            "framework": "make ios-framework",
            "host": "make ios-host-build",
            "tests": "make ios-test",
            "report_validation": ".github/scripts/validate-native-test-reports.py",
        },
        "checks": {
            "simulator_app": "passed",
            "native_test_reports": "passed",
        },
        "claims": CLAIMS,
        "blockers": BLOCKERS,
        "file_count": len(records),
        "total_bytes": sum(int(record["bytes"]) for record in records),
        "tree_sha256": tree_digest(records),
        "files": records,
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return manifest


def _packet_files(packet: Path) -> set[str]:
    actual: set[str] = set()
    for path in sorted(packet.rglob("*"), key=lambda item: item.relative_to(packet).as_posix()):
        if path.is_symlink():
            raise VerificationError(f"packet contains a symlink: {path.relative_to(packet)}")
        if not path.is_file():
            continue
        relative = path.relative_to(packet).as_posix()
        if relative != "manifest.json" and not (
            relative.startswith(f"{APP_RELATIVE_PATH}/")
            or relative.startswith(f"{REPORT_RELATIVE_PATH}/")
        ):
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
    if manifest.get("configuration") != "Debug" or manifest.get("sdk") != "iphonesimulator":
        raise VerificationError("packet manifest has unsupported simulator build metadata")
    if manifest.get("architecture") != "arm64" or not manifest.get("destination"):
        raise VerificationError("packet manifest has incomplete simulator metadata")
    source_revision = manifest.get("source_revision")
    if not isinstance(source_revision, str) or not source_revision:
        raise VerificationError("packet manifest has incomplete source provenance")
    provenance = manifest.get("provenance", {})
    if not isinstance(provenance, dict):
        raise VerificationError("packet manifest has invalid provenance")
    github_sha = provenance.get("GITHUB_SHA")
    if github_sha is not None and github_sha != source_revision:
        raise VerificationError("packet provenance GITHUB_SHA does not match source revision")
    manifest_files = manifest.get("files")
    if not isinstance(manifest_files, list) or not manifest_files:
        raise VerificationError("packet manifest has no files")
    expected: list[dict[str, int | str]] = []
    for record in manifest_files:
        if not isinstance(record, dict):
            raise VerificationError("packet manifest contains an invalid file record")
        path = _relative_path(str(record.get("path", "")))
        if not (path.startswith(f"{APP_RELATIVE_PATH}/") or path.startswith(f"{REPORT_RELATIVE_PATH}/")):
            raise VerificationError(f"packet manifest contains an invalid file path: {path}")
        if not isinstance(record.get("bytes"), int) or not isinstance(record.get("sha256"), str):
            raise VerificationError(f"packet manifest contains an incomplete file record: {path}")
        expected.append({"path": path, "bytes": record["bytes"], "sha256": record["sha256"]})
    if expected != sorted(expected, key=lambda record: str(record["path"])):
        raise VerificationError("packet manifest file records are not sorted")

    app = packet / APP_RELATIVE_PATH
    reports = packet / REPORT_RELATIVE_PATH
    _validate_app(app)
    _validate_reports(reports)
    records = sorted(
        _records(app, APP_RELATIVE_PATH) + _records(reports, REPORT_RELATIVE_PATH),
        key=lambda record: str(record["path"]),
    )
    if records != expected:
        raise VerificationError("packet files do not match the manifest")
    if manifest.get("file_count") != len(records):
        raise VerificationError("packet file count does not match the manifest")
    if manifest.get("total_bytes") != sum(int(record["bytes"]) for record in records):
        raise VerificationError("packet byte total does not match the manifest")
    if manifest.get("tree_sha256") != tree_digest(records):
        raise VerificationError("packet tree digest does not match the manifest")
    expected_packet_files = {"manifest.json"} | {str(record["path"]) for record in records}
    if _packet_files(packet) != expected_packet_files:
        raise VerificationError("packet contains missing or extra files")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path, nargs="?")
    parser.add_argument("reports", type=Path, nargs="?")
    parser.add_argument("output", type=Path, nargs="?")
    parser.add_argument("--verify-packet", type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--destination", default=os.environ.get("IOS_HOST_DESTINATION", "unknown"))
    args = parser.parse_args()
    try:
        if args.verify_packet is not None:
            if any(value is not None for value in (args.app, args.reports, args.output)):
                parser.error("--verify-packet cannot be combined with app/reports/output")
            manifest = verify_packet(args.verify_packet)
            print(f"iOS simulator evidence packet verified: {args.verify_packet}")
            print(f"Tree SHA-256: {manifest['tree_sha256']}")
            return 0
        if args.app is None or args.reports is None or args.output is None:
            parser.error("app, reports, and output are required unless --verify-packet is used")
        manifest = create_packet(
            args.app,
            args.reports,
            args.output,
            repository=args.repo_root.resolve(),
            destination=args.destination,
        )
    except (OSError, VerificationError) as error:
        print(f"iOS simulator evidence packet failed: {error}", file=sys.stderr)
        return 1
    print(f"iOS simulator evidence packet created: {args.output}")
    print(f"Tree SHA-256: {manifest['tree_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
