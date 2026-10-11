#!/usr/bin/env python3
"""Fail a successful test lane when it produced no executed JUnit tests."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

REPORT_PATTERNS = {
    "unit": "**/build/test-results/**/*.xml",
    "managed-device": "**/build/outputs/androidTest-results/managedDevice/**/*.xml",
}


def validate(root: Path, lane: str, require_test: str | None = None) -> list[str]:
    reports = sorted(root.glob(REPORT_PATTERNS[lane]))
    if not reports:
        return [f"{lane}: no JUnit reports were produced"]

    tests = skipped = 0
    required_test_executed = False
    errors: list[str] = []
    for report in reports:
        try:
            report_root = ET.parse(report).getroot()
        except ET.ParseError as error:
            errors.append(f"{lane}: malformed XML {report}: {error}")
            continue
        suites = [report_root] if report_root.tag == "testsuite" else list(report_root)
        for suite in suites:
            if suite.tag != "testsuite":
                continue
            tests += int(suite.attrib.get("tests", "0"))
            skipped += int(suite.attrib.get("skipped", "0"))
            for case in suite.findall("testcase"):
                test_id = f"{case.attrib.get('classname', '')}.{case.attrib.get('name', '')}"
                if test_id == require_test and case.find("skipped") is None:
                    required_test_executed = True

    executed = tests - skipped
    if executed <= 0:
        errors.append(f"{lane}: reports contain no executed tests (tests={tests}, skipped={skipped})")
    if require_test and not required_test_executed:
        errors.append(f"{lane}: required test did not execute: {require_test}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("lane", choices=REPORT_PATTERNS)
    parser.add_argument("root", nargs="?", type=Path, default=Path.cwd())
    parser.add_argument("--require-test", help="Require this JUnit classname.method to execute")
    args = parser.parse_args()

    errors = validate(args.root, args.lane, args.require_test)
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        return 1
    print(f"{args.lane} JUnit reports contain executed tests")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
