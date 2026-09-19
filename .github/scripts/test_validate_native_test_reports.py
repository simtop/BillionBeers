#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("validate_native_test_reports", ROOT / ".github/scripts/validate-native-test-reports.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class ValidateNativeTestReportsTest(unittest.TestCase):
    def write_reports(self, root: Path, xml_by_module: dict[str, str]) -> None:
        for module, xml in xml_by_module.items():
            path = root / module / "build/test-results/iosSimulatorArm64Test/TEST-report.xml"
            path.parent.mkdir(parents=True)
            path.write_text(xml)

    def test_requires_complete_nonempty_successful_reports(self) -> None:
        xml = '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="ok"/></testsuite>'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_reports(root, {module: xml for module in MODULE.REQUIRED_MODULES})
            self.assertEqual(MODULE.validate(root), [])

    def test_rejects_missing_zero_skipped_malformed_and_failed_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_reports(root, {
                "core-common": '<testsuite tests="0" failures="0" errors="0" skipped="0"/>',
                "beer_network": '<testsuite tests="1" failures="0" errors="0" skipped="1"/>',
                "beer_database": '<testsuite>',
            })
            errors = MODULE.validate(root)
            self.assertTrue(any("core-common" in error and "zero" in error for error in errors))
            self.assertTrue(any("beer_network" in error and "skipped" in error for error in errors))
            self.assertTrue(any("beer_database" in error and "malformed" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
