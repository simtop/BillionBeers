#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

SCRIPT = Path(__file__).with_name("summarize-test-failures.py")
SPEC = importlib.util.spec_from_file_location("test_failure_summary", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write(root: Path, relative: str, content: str = "data") -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    return path


class TestFailureSummaryTest(unittest.TestCase):
    def test_collects_exact_paparazzi_identifiers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write(
                root,
                "feature/beerslist/build/test-results/verifyPaparazziDebug/TEST-screenshots.xml",
                """<testsuite>
  <testcase classname="com.simtop.billionbeers.screenshot.BeersListScreenshotTest" name="snapshot[BeerList_dark_fr_rtl]"><failure/></testcase>
  <testcase classname="com.simtop.billionbeers.screenshot.BeersListScreenshotTest" name="snapshot[BeerList_light_en]"><error/></testcase>
  <testcase classname="com.simtop.billionbeers.screenshot.BeersListScreenshotTest" name="passing"/>
  <testcase classname="com.example.OrdinaryTest" name="ordinary"><failure/></testcase>
</testsuite>""",
            )

            failures, unreadable, fallback = MODULE.collect(root, "paparazzi")

            self.assertEqual([], unreadable)
            self.assertEqual([], fallback)
            self.assertEqual(
                [
                    MODULE.Failure(
                        ":feature:beerslist",
                        "com.simtop.billionbeers.screenshot.BeersListScreenshotTest",
                        "snapshot[BeerList_dark_fr_rtl]",
                        "failure",
                    ),
                    MODULE.Failure(
                        ":feature:beerslist",
                        "com.simtop.billionbeers.screenshot.BeersListScreenshotTest",
                        "snapshot[BeerList_light_en]",
                        "error",
                    ),
                ],
                failures,
            )

    def test_managed_device_prefers_project_property_and_falls_back_to_path(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write(
                root,
                "feature/beersearch/build/outputs/androidTest-results/managedDevice/debug/atdApi35/TEST-search.xml",
                """<testsuites xmlns="urn:junit">
  <testsuite>
    <properties><property name="project" value=":feature:beersearch"/></properties>
    <testcase classname="com.simtop.feature.beersearch.SearchTest" name="typingWorks"><failure/></testcase>
    <testcase classname="com.simtop.feature.beersearch.SearchTest" name="passing"/>
  </testsuite>
</testsuites>""",
            )
            write(
                root,
                "app-release-smoke/build/outputs/androidTest-results/managedDevice/releasesmoke/atdApi35/TEST-smoke.xml",
                """<testsuite>
  <testcase classname="com.simtop.billionbeers.releasesmoke.ReleaseLaunchSmokeTest" name="minifiedAppLaunches"><error/></testcase>
  <testcase classname="com.example.Skipped" name="ignored"><skipped/></testcase>
</testsuite>""",
            )

            failures, unreadable, fallback = MODULE.collect(root, "managed-device")

            self.assertEqual([], unreadable)
            self.assertEqual([], fallback)
            self.assertEqual(
                [
                    MODULE.Failure(
                        ":app-release-smoke",
                        "com.simtop.billionbeers.releasesmoke.ReleaseLaunchSmokeTest",
                        "minifiedAppLaunches",
                        "error",
                    ),
                    MODULE.Failure(
                        ":feature:beersearch",
                        "com.simtop.feature.beersearch.SearchTest",
                        "typingWorks",
                        "failure",
                    ),
                ],
                failures,
            )

    def test_malformed_xml_and_paparazzi_file_fallback_do_not_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            malformed = write(
                root,
                "catalog/build/test-results/verifyPaparazziDebug/TEST-broken.xml",
                "<testsuite>",
            )
            write(root, "catalog/build/paparazzi/failures/delta-example.png")
            (root / "feature/beersearch/build/paparazzi/failures").mkdir(parents=True)

            failures, unreadable, fallback = MODULE.collect(root, "paparazzi")
            report = MODULE.generate(root, "paparazzi", "probe/branch")

            self.assertEqual([], failures)
            self.assertEqual([malformed.as_posix()], unreadable)
            self.assertEqual([":catalog"], fallback)
            self.assertIn("`:catalog`", report)
            self.assertIn("modules=:catalog", report)
            self.assertIn("Could not parse 1 JUnit report", report)

    def test_rendering_is_sorted_deduplicated_and_safe(self) -> None:
        failures = [
            MODULE.Failure(":z", "Class", "method|two", "failure"),
            MODULE.Failure(":a", "Class", "method\n`one`", "error"),
            MODULE.Failure(":z", "Class", "method|two", "failure"),
        ]

        report = MODULE.render_managed_device(sorted(set(failures)), [])

        self.assertLess(report.index("`:a`"), report.index("`:z`"))
        self.assertNotIn("method\n", report)
        self.assertIn("method `one`", report)
        self.assertIn("method|two", report)
        self.assertEqual(1, report.count("method|two"))

    def test_main_prints_and_appends_identical_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            summary = root / "summary.md"
            output = io.StringIO()

            with redirect_stdout(output):
                result = MODULE.main(
                    [
                        "managed-device",
                        "--root",
                        str(root),
                        "--summary",
                        str(summary),
                    ]
                )

            self.assertEqual(0, result)
            self.assertEqual(output.getvalue(), summary.read_text())
            self.assertIn("No failed JUnit testcase was emitted", output.getvalue())


if __name__ == "__main__":
    unittest.main()
