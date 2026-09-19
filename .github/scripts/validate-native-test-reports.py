#!/usr/bin/env python3
"""Validate that every required Apple simulator test module produced real JUnit evidence."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

REQUIRED_MODULES = ("core-common", "beer_network", "beer_database", "ios-shared")
REPORT_GLOB = "build/test-results/iosSimulatorArm64Test/*.xml"


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    for module in REQUIRED_MODULES:
        reports = sorted((root / module).glob(REPORT_GLOB))
        if not reports:
            errors.append(f"{module}: no JUnit reports at {module}/{REPORT_GLOB}")
            continue

        tests = failures = errors_count = skipped = 0
        for report in reports:
            try:
                suite_root = ET.parse(report).getroot()
            except ET.ParseError as error:
                errors.append(f"{module}: malformed XML {report}: {error}")
                continue
            suites = [suite_root] if suite_root.tag == "testsuite" else list(suite_root)
            for suite in suites:
                if suite.tag != "testsuite":
                    continue
                tests += int(suite.attrib.get("tests", "0"))
                failures += int(suite.attrib.get("failures", "0"))
                errors_count += int(suite.attrib.get("errors", "0"))
                skipped += int(suite.attrib.get("skipped", "0"))

        if tests == 0:
            errors.append(f"{module}: reports contain zero executed tests")
        if failures or errors_count:
            errors.append(f"{module}: reports contain failures={failures}, errors={errors_count}")
        if skipped == tests and tests > 0:
            errors.append(f"{module}: all reported tests are skipped")
        elif skipped:
            errors.append(f"{module}: reports contain skipped tests={skipped}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validate(args.root)
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        return 1
    print("Native simulator test reports validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
