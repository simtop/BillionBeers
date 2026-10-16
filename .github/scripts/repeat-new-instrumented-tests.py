#!/usr/bin/env python3
"""Repeat changed Android instrumented test classes on a managed device."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ANDROID_TEST_PATH = re.compile(r"^(?P<module>.+)/src/androidTest/.+\.kt$")
PACKAGE_DECLARATION = re.compile(r"^\s*package\s+([\w.]+)\s*$", re.MULTILINE)
TEST_ANNOTATION = re.compile(r"@(?:[\w.]+\.)?Test\b")
CLASS_DECLARATION = re.compile(
    r"^\s*(?:(?:public|internal|open|final|abstract|sealed|data|annotation|value)\s+)*"
    r"class\s+(\w+)\b",
    re.MULTILINE,
)
MANAGED_DEVICE_PLUGINS = (
    'id("billionbeers.android.managed.device")',
    'id("billionbeers.android.feature.uitest")',
)


@dataclass(frozen=True, order=True)
class TestTarget:
    module: str
    class_name: str


def changed_android_test_files(paths: list[str]) -> list[tuple[str, str]]:
    """Return changed instrumented test Kotlin paths with their Gradle module directory."""
    matches: list[tuple[str, str]] = []
    for path in paths:
        match = ANDROID_TEST_PATH.match(path)
        if match:
            matches.append((match.group("module"), path))
    return matches


def collect_targets(root: Path, changed_paths: list[str]) -> list[TestTarget]:
    targets: set[TestTarget] = set()
    for module, relative_path in changed_android_test_files(changed_paths):
        # Benchmarks use androidTest source sets too, but they run only on physical devices and
        # are deliberately outside the managed-device CI tier.
        if module.startswith("benchmark/"):
            continue

        module_dir = root / module
        build_file = module_dir / "build.gradle.kts"
        if not build_file.is_file():
            raise ValueError(f"Android test module has no sibling build script: {module}")
        build_text = build_file.read_text(encoding="utf-8")
        if not any(plugin in build_text for plugin in MANAGED_DEVICE_PLUGINS):
            raise ValueError(
                f"Android test module is not opted into managed devices: {module}"
            )

        source = (root / relative_path).read_text(encoding="utf-8")
        if not TEST_ANNOTATION.search(source):
            continue
        package = PACKAGE_DECLARATION.search(source)
        if not package:
            raise ValueError(
                f"Cannot determine Kotlin package for instrumented tests: {relative_path}"
            )

        stem = Path(relative_path).stem
        declarations = set(CLASS_DECLARATION.findall(source))
        if stem not in declarations:
            raise ValueError(
                f"Expected a test class named {stem} in instrumented test source: {relative_path}"
            )
        targets.add(TestTarget(module=module, class_name=f"{package.group(1)}.{stem}"))
    return sorted(targets)


def changed_paths(root: Path, base_sha: str, head_sha: str) -> list[str]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--no-renames",
            "--diff-filter=AM",
            "--name-only",
            "-z",
            f"{base_sha}...{head_sha}",
        ],
        cwd=root,
        check=True,
        capture_output=True,
    )
    return [path.decode("utf-8") for path in result.stdout.split(b"\0") if path]


def validate_fresh_report(
    module_dir: Path, class_name: str, started_at: float
) -> None:
    report_root = module_dir / "build/outputs/androidTest-results/managedDevice"
    matching_cases: list[str] = []
    for report in report_root.rglob("*.xml") if report_root.exists() else []:
        if report.stat().st_mtime < started_at - 2:
            continue
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError as error:
            raise RuntimeError(f"Malformed Android test report {report}: {error}") from error
        suites = [root] if root.tag == "testsuite" else list(root)
        for suite in suites:
            if suite.tag != "testsuite":
                continue
            for case in suite.findall("testcase"):
                if (
                    case.attrib.get("classname") == class_name
                    and case.find("skipped") is None
                ):
                    matching_cases.append(case.attrib.get("name", "<unnamed>"))
    if not matching_cases:
        raise RuntimeError(
            f"Repeated test class did not produce a fresh executed result: {class_name}"
        )
    print(f"Executed {class_name}: {len(matching_cases)} test(s)")


def repeat_targets(root: Path, targets: list[TestTarget], repetitions: int) -> None:
    if repetitions < 1:
        raise ValueError("repetitions must be at least 1")
    if not targets:
        print("No changed managed-device test classes; skipping repeats.")
        return

    print(
        f"Repeating {len(targets)} changed instrumented test class(es) "
        f"{repetitions} additional time(s)."
    )
    for target in targets:
        module_dir = root / target.module
        gradle_module = ":" + target.module.replace("/", ":")
        for repetition in range(1, repetitions + 1):
            print(
                f"### Repeat {repetition}/{repetitions}: "
                f"{target.module} {target.class_name}",
                flush=True,
            )
            environment = os.environ.copy()
            # Re-run the AndroidTest task even if Gradle considers its previous output current.
            environment["GRADLE_RUNNER"] = "./gradlew --rerun-tasks"
            started_at = time.time()
            subprocess.run(
                [
                    "make",
                    "ui-test-managed",
                    f"MODULE={gradle_module}",
                    f"UI_TEST_CLASS={target.class_name}",
                ],
                cwd=root,
                env=environment,
                check=True,
            )
            validate_fresh_report(module_dir, target.class_name, started_at)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="PR base commit SHA")
    parser.add_argument("--head", required=True, help="PR head commit SHA")
    parser.add_argument(
        "--repetitions",
        type=int,
        default=2,
        help="Additional runs per changed test class (default: 2; CI already ran each once)",
    )
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    try:
        paths = changed_paths(args.root, args.base, args.head)
        targets = collect_targets(args.root, paths)
        repeat_targets(args.root, targets, args.repetitions)
    except (OSError, subprocess.CalledProcessError, RuntimeError, ValueError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
