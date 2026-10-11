#!/usr/bin/env python3
"""Tests for the non-vacuous test-lane report guard."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "validate_test_lane_reports", ROOT / ".github/scripts/validate-test-lane-reports.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class ValidateTestLaneReportsTest(unittest.TestCase):
    def test_requires_executed_unit_tests(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "app/build/test-results/testDebugUnitTest/TEST-app.xml"
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite tests="1" skipped="0"/>')
            self.assertEqual([], MODULE.validate(root, "unit"))

            report.write_text('<testsuite tests="1" skipped="1"/>')
            self.assertTrue(
                any("no executed tests" in error for error in MODULE.validate(root, "unit"))
            )

    def test_requires_managed_device_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = (
                root
                / "app/build/outputs/androidTest-results/managedDevice/debug/atdApi35/TEST-app.xml"
            )
            report.parent.mkdir(parents=True)
            test_id = "com.simtop.billionbeers.MainActivityComposeTest.expandedListDetailKeepsTheListVisibleAfterSelection"
            report.write_text(
                '<testsuite tests="2" skipped="0">'
                '<testcase classname="com.simtop.billionbeers.MainActivityComposeTest" '
                'name="expandedListDetailKeepsTheListVisibleAfterSelection"/>'
                "</testsuite>"
            )
            self.assertEqual([], MODULE.validate(root, "managed-device", test_id))
            report.write_text(
                '<testsuite tests="2" skipped="1">'
                '<testcase classname="com.simtop.billionbeers.MainActivityComposeTest" '
                'name="expandedListDetailKeepsTheListVisibleAfterSelection"><skipped/></testcase>'
                "</testsuite>"
            )
            self.assertTrue(
                any(
                    "required test did not execute" in error
                    for error in MODULE.validate(root, "managed-device", test_id)
                )
            )

    def test_rejects_missing_and_malformed_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertTrue(MODULE.validate(root, "unit"))
            report = root / "app/build/test-results/testDebugUnitTest/TEST-app.xml"
            report.parent.mkdir(parents=True)
            report.write_text("<testsuite>")
            self.assertTrue(
                any("malformed XML" in error for error in MODULE.validate(root, "unit"))
            )


if __name__ == "__main__":
    unittest.main()
