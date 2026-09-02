#!/usr/bin/env python3
"""Publish exact failed test identifiers from Gradle JUnit XML."""

from __future__ import annotations

import argparse
import os
import shlex
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

PAPARAZZI_PACKAGE = "com.simtop.billionbeers.screenshot"


@dataclass(frozen=True, order=True)
class Failure:
    module: str
    classname: str
    name: str
    kind: str


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def module_from_path(path: Path, root: Path) -> str | None:
    try:
        parts = path.resolve().relative_to(root.resolve()).parts
        build_index = parts.index("build")
    except (ValueError, OSError):
        return None
    module_parts = parts[:build_index]
    return ":" + ":".join(module_parts) if module_parts else None


def contains_path(path: Path, sequence: tuple[str, ...]) -> bool:
    parts = path.parts
    return any(parts[index : index + len(sequence)] == sequence for index in range(len(parts)))


def report_paths(root: Path, mode: str) -> list[Path]:
    reports = []
    for path in root.rglob("TEST-*.xml"):
        if mode == "paparazzi":
            if contains_path(path, ("build", "test-results")):
                reports.append(path)
        elif contains_path(
            path,
            ("build", "outputs", "androidTest-results", "managedDevice"),
        ):
            reports.append(path)
    return sorted(reports)


def suite_project(suite: ET.Element) -> str | None:
    for child in suite:
        if local_name(child.tag) != "properties":
            continue
        for prop in child:
            if local_name(prop.tag) == "property" and prop.attrib.get("name") == "project":
                value = prop.attrib.get("value", "").strip()
                if value:
                    return value
    return None


def failed_kind(testcase: ET.Element) -> str | None:
    for child in testcase:
        kind = local_name(child.tag)
        if kind in {"failure", "error"}:
            return kind
    return None


def parse_report(path: Path, root: Path, mode: str) -> list[Failure]:
    document = ET.parse(path).getroot()
    failures = []
    for suite in document.iter():
        if local_name(suite.tag) != "testsuite":
            continue
        module = suite_project(suite) if mode == "managed-device" else None
        module = module or module_from_path(path, root)
        for testcase in suite:
            if local_name(testcase.tag) != "testcase":
                continue
            kind = failed_kind(testcase)
            classname = testcase.attrib.get("classname", "").strip()
            name = testcase.attrib.get("name", "").strip()
            if not kind or not module or not classname or not name:
                continue
            if mode == "paparazzi" and not classname.startswith(PAPARAZZI_PACKAGE):
                continue
            failures.append(Failure(module, classname, name, kind))
    return failures


def paparazzi_failure_modules(root: Path) -> list[str]:
    modules = set()
    for directory in root.rglob("failures"):
        if not directory.is_dir() or not contains_path(
            directory,
            ("build", "paparazzi", "failures"),
        ):
            continue
        if not any(path.is_file() for path in directory.rglob("*")):
            continue
        module = module_from_path(directory, root)
        if module:
            modules.add(module)
    return sorted(modules)


def collect(root: Path, mode: str) -> tuple[list[Failure], list[str], list[str]]:
    failures = set()
    unreadable = []
    for path in report_paths(root, mode):
        try:
            failures.update(parse_report(path, root, mode))
        except (OSError, ET.ParseError):
            unreadable.append(path.as_posix())
    fallback_modules = paparazzi_failure_modules(root) if mode == "paparazzi" else []
    return sorted(failures), unreadable, fallback_modules


def inline_code(value: str) -> str:
    value = " ".join(value.replace("\r", " ").replace("\n", " ").split())
    longest = 0
    current = 0
    for char in value:
        if char == "`":
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    fence = "`" * (longest + 1)
    padding = " " if value.startswith("`") or value.endswith("`") else ""
    return f"{fence}{padding}{value}{padding}{fence}"


def failure_lines(failures: list[Failure]) -> list[str]:
    lines = []
    current_module = None
    for failure in failures:
        if failure.module != current_module:
            current_module = failure.module
            lines.append(f"- {inline_code(failure.module)}")
        identifier = f"{failure.classname}#{failure.name}"
        lines.append(f"  - {inline_code(identifier)} — {failure.kind}")
    return lines


def render_paparazzi(
    failures: list[Failure],
    unreadable: list[str],
    fallback_modules: list[str],
    branch: str | None,
) -> str:
    lines = ["### 📸 Screenshot verification failed", ""]
    modules = sorted({failure.module for failure in failures} | set(fallback_modules))
    if failures:
        lines.extend(["#### Failed snapshots/tests", "", *failure_lines(failures), ""])
    elif fallback_modules:
        lines.extend(
            [
                "JUnit XML did not expose an exact failed testcase, but failure images were emitted for:",
                "",
                *(f"- {inline_code(module)}" for module in fallback_modules),
                "",
            ]
        )
    else:
        lines.extend(
            [
                "No failed screenshot testcase or failure image was emitted. The build likely failed before comparison completed; inspect the Gradle log and `screenshot-test-results` artifact.",
                "",
            ]
        )

    if unreadable:
        lines.extend(
            [
                f"_Could not parse {len(unreadable)} JUnit report(s); remaining reports were still inspected._",
                "",
            ]
        )

    if modules and branch:
        module_arg = " ".join(modules)
        command = (
            "gh workflow run record_screenshots.yml "
            f"--ref {shlex.quote(branch)} -f modules={shlex.quote(module_arg)}"
        )
        lines.extend(
            [
                "#### 🛠️ How to fix",
                "",
                "Record updated screenshots for the affected modules:",
                "",
                "```bash",
                command,
                "```",
                "",
            ]
        )

    lines.append("Failure images and JUnit XML are available in the artifacts below. ⬇️")
    return "\n".join(lines) + "\n"


def render_managed_device(failures: list[Failure], unreadable: list[str]) -> str:
    lines = ["### 🧪 Instrumented test failures", ""]
    if failures:
        lines.extend(["#### Failed tests", "", *failure_lines(failures), ""])
    else:
        lines.extend(
            [
                "No failed JUnit testcase was emitted. Compilation, device provisioning, installation, or test-runner startup may have failed before a testcase completed.",
                "",
            ]
        )
    if unreadable:
        lines.extend(
            [
                f"_Could not parse {len(unreadable)} JUnit report(s); remaining reports were still inspected._",
                "",
            ]
        )
    lines.append("See the failing Gradle step and the `instrumented-test-reports` artifact for details.")
    return "\n".join(lines) + "\n"


def generate(root: Path, mode: str, branch: str | None = None) -> str:
    failures, unreadable, fallback_modules = collect(root, mode)
    if mode == "paparazzi":
        return render_paparazzi(failures, unreadable, fallback_modules, branch)
    return render_managed_device(failures, unreadable)


def publish(markdown: str, summary_path: Path | None) -> None:
    print(markdown, end="")
    if summary_path is None:
        return
    try:
        with summary_path.open("a", encoding="utf-8") as summary:
            summary.write(markdown)
    except OSError as error:
        print(f"::warning::Could not write GitHub job summary: {error}", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("paparazzi", "managed-device"))
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--branch")
    parser.add_argument(
        "--summary",
        type=Path,
        default=Path(os.environ["GITHUB_STEP_SUMMARY"])
        if os.environ.get("GITHUB_STEP_SUMMARY")
        else None,
    )
    args = parser.parse_args(argv)
    publish(generate(args.root, args.mode, args.branch), args.summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
