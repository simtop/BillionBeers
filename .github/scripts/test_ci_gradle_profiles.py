from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("profiles", Path(__file__).with_name("summarize-gradle-profiles.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def profile(rows):
    return '<table><tr><td>Total Build Time</td><td>1m10.5s</td></tr>' + "".join(
        f'<tr><td class="{style}">{task}</td><td>{duration}</td><td>{outcome}</td></tr>'
        for style, task, duration, outcome in rows
    ) + '</table>'


class GradleProfilesTest(unittest.TestCase):
    def test_parallel_work_is_separate_from_build_time_and_totals_are_not_double_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile-1.html"
            path.write_text(profile([
                ("", ":ios-shared", "2m", "(total)"),
                ("indentPath", ":ios-shared:linkDebugFrameworkIosArm64", "1m0s", ""),
                ("indentPath", ":ios-shared:linkDebugTestIosSimulatorArm64", "30s", ""),
                ("indentPath", ":app:compileDebugKotlin", "20s", "FROM-CACHE"),
                ("indentPath", ":app:testDebugUnitTest", "10s", ""),
            ]))
            record = MODULE.parse_profile(path)
            self.assertEqual(70.5, record["build_wall_seconds"])
            self.assertEqual(120, record["summed_task_seconds"])
            self.assertEqual(60, record["categories"]["Apple framework linking"])
            self.assertEqual(30, record["categories"]["Apple test linking"])
            self.assertEqual(20, record["categories"]["Compilation"])
            self.assertEqual("FROM-CACHE", record["tasks"][2]["outcome"])

    def test_duration_parser_handles_hours_and_rejects_ambiguous_values(self):
        self.assertEqual(3723.25, MODULE.seconds("1h2m3.25s"))
        self.assertEqual(0, MODULE.seconds("0s"))
        self.assertEqual(60, MODULE.seconds("1m"))
        self.assertEqual(3600, MODULE.seconds("1h"))
        for value in ("", "3", "-1s", "unknown", "1s2m"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                MODULE.seconds(value)

    def test_missing_and_malformed_profiles_remain_explicit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertFalse(MODULE.summarize(root)["available"])
            (root / "profile-bad.html").write_text("<html>incomplete</html>")
            (root / "unrelated.html").write_text("ignored")
            record = MODULE.summarize(root)
            self.assertFalse(record["available"])
            self.assertEqual(["Cannot parse profile-bad.html"], record["errors"])

    def test_json_and_summary_preserve_a_failed_lane_without_claiming_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "profile-1.html").write_text(profile([
                ("indentPath", ":app:testDebugUnitTest", "3s", ""),
            ]))
            output = root / "timings.json"
            summary = root / "summary.md"
            self.assertEqual(0, MODULE.main([
                str(root), "--json", str(output), "--summary", str(summary), "--outcome", "failure",
            ]))
            self.assertEqual("failure", json.loads(output.read_text())["lane_outcome"])
            self.assertIn("not wall-clock savings", summary.read_text())
            self.assertIn("not proof that it passed", summary.read_text())

    def test_failed_and_skipped_task_outcomes_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile-1.html"
            path.write_text(profile([
                ("indentPath", ":app:testDebugUnitTest", "0s", "SKIPPED"),
                ("indentPath", ":app:compileDebugKotlin", "1s", "FAILED"),
            ]))
            record = MODULE.parse_profile(path)
            self.assertEqual(["FAILED", "SKIPPED"], [task["outcome"] for task in record["tasks"]])


if __name__ == "__main__":
    unittest.main()
