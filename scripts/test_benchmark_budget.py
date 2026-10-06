#!/usr/bin/env python3
"""Exercise startup-budget validation and the real Make recipe without a device or Gradle."""

from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts/check-benchmark-budget.sh"
OUTPUT = "benchmark/macrobenchmark/build/outputs/connected_android_test_additional_output"


def report(median: float = 202.0) -> dict:
    return {
        "context": {
            "build": {
                "fingerprint": "google/shiba/shiba:17/reference/user/release-keys",
                "model": "Pixel 8",
                "device": "shiba",
                "version": {"sdk": 37},
            },
            "cpuLocked": False,
        },
        "benchmarks": [{
            "name": "startup",
            "metrics": {"timeToInitialDisplayMs": {"median": median}},
        }],
    }


class BenchmarkBudgetParserTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="benchmark budget ")
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "results with spaces.json"

    def check(self, data: object) -> subprocess.CompletedProcess:
        self.path.write_text(json.dumps(data), encoding="utf-8")
        return self.run_checker(str(self.path))

    def run_checker(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["bash", str(CHECKER), *args], capture_output=True, text=True, timeout=10,
        )

    def assert_rejected(self, data: object, diagnostic: str) -> None:
        result = self.check(data)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(diagnostic, result.stderr)
        self.assertNotIn("All benchmarks", result.stdout)

    def test_physical_measurement_below_or_at_budget_passes(self) -> None:
        for median in (0, 202.5, 500):
            with self.subTest(median=median):
                result = self.check(report(median))
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("OK   startup:timeToInitialDisplayMs", result.stdout)
                self.assertIn("Pixel 8 (API 37)", result.stdout)
                self.assertIn("All benchmarks within budget.", result.stdout)

    def test_physical_measurement_above_budget_fails(self) -> None:
        result = self.check(report(500.1))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("FAIL startup:timeToInitialDisplayMs", result.stdout)
        self.assertIn("exceeded their performance budget", result.stderr)

    def test_emulator_measurement_is_reported_without_enforcing_physical_budget(self) -> None:
        data = report(1200)
        data["context"]["build"].update(
            fingerprint="google/sdk_gphone64_arm64/emulator", model="sdk_gphone64_arm64",
            device="ranchu",
        )
        result = self.check(data)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("SKIP startup:timeToInitialDisplayMs", result.stdout)
        self.assertIn("budgets are not enforced here", result.stdout)

    def test_missing_argument_or_file_is_rejected(self) -> None:
        for args in ((), (str(self.path),)):
            with self.subTest(args=args):
                result = self.run_checker(*args)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Usage:", result.stderr)

    def test_malformed_json_is_rejected(self) -> None:
        self.path.write_text("{not json", encoding="utf-8")
        result = self.run_checker(str(self.path))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cannot read benchmark report", result.stderr)

    def test_empty_or_invalid_benchmark_shape_is_rejected(self) -> None:
        for data, diagnostic in (
            ([], "root must be an object"),
            ({}, "non-empty 'benchmarks' list"),
            ({"benchmarks": []}, "non-empty 'benchmarks' list"),
            ({"benchmarks": {}}, "non-empty 'benchmarks' list"),
            ({"benchmarks": [None]}, "entry 0 must be an object"),
        ):
            with self.subTest(data=data):
                self.assert_rejected(data, diagnostic)

    def test_missing_required_metric_is_rejected(self) -> None:
        for name, metric in (("startup", "otherMetric"), ("otherTest", "timeToInitialDisplayMs")):
            with self.subTest(name=name, metric=metric):
                data = report()
                data["benchmarks"] = [{"name": name, "metrics": {metric: {"median": 1}}}]
                self.assert_rejected(data, "missing required metric startup:timeToInitialDisplayMs")

    def test_missing_or_empty_metrics_and_missing_median_are_rejected(self) -> None:
        for metrics, diagnostic in (
            (None, "non-empty 'metrics' object"),
            ({}, "non-empty 'metrics' object"),
            ({"timeToInitialDisplayMs": {}}, "has no median"),
            ({"timeToInitialDisplayMs": 202}, "has no median"),
        ):
            with self.subTest(metrics=metrics):
                data = report()
                data["benchmarks"][0]["metrics"] = metrics
                self.assert_rejected(data, diagnostic)

    def test_invalid_medians_are_rejected(self) -> None:
        for median in (True, None, "202", -1, float("nan"), float("inf"), -float("inf"), 10 ** 400):
            with self.subTest(median=median):
                self.assert_rejected(report(median), "median must be finite and non-negative")

    def test_invalid_names_cannot_corrupt_shell_rows(self) -> None:
        for field, value, diagnostic in (
            ("name", "", "invalid name"),
            ("name", "startup\tother", "invalid name"),
            ("metric", "timeToInitialDisplayMs\nother", "invalid metric name"),
        ):
            with self.subTest(field=field, value=value):
                data = report()
                benchmark = data["benchmarks"][0]
                if field == "name":
                    benchmark["name"] = value
                else:
                    benchmark["metrics"] = {value: {"median": 202}}
                self.assert_rejected(data, diagnostic)

    def test_invalid_device_context_is_rejected(self) -> None:
        for context, diagnostic in (
            (None, "no valid device context/build object"),
            ({"build": []}, "no valid device context/build object"),
            ({"build": {"version": []}}, "device build version must be an object"),
            ({"build": {"model": []}}, "device identifiers must be scalar values"),
            ({"build": {"model": "Pixel\n8"}}, "device label contains control characters"),
        ):
            with self.subTest(context=context):
                data = report()
                data["context"] = context
                self.assert_rejected(data, diagnostic)

    def test_unknown_metrics_do_not_replace_or_disable_required_budget(self) -> None:
        for median, expected_status in ((202, 0), (501, 1)):
            with self.subTest(median=median):
                data = report(median)
                data["benchmarks"][0]["metrics"]["otherMetric"] = {"median": 9999}
                result = self.check(data)
                self.assertEqual(expected_status, result.returncode, result.stdout + result.stderr)
                self.assertIn("no budget configured for 'startup:otherMetric'", result.stderr)


class BenchmarkFreshReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="benchmark recipe ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        shutil.copy2(ROOT / "Makefile", self.root / "Makefile")
        (self.root / "scripts").mkdir()
        shutil.copy2(CHECKER, self.root / "scripts/check-benchmark-budget.sh")
        (self.root / "tmp").mkdir()
        runner = self.root / "fake-gradle.py"
        # The real Make recipe runs against controlled output, never a device or Gradle.
        # Exact nanosecond timestamps avoid sleeps and test the strict freshness boundary.
        runner.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, pathlib, sys\n"
            "assert sys.argv[1:] == [':benchmark:macrobenchmark:connectedCheck']\n"
            "markers = list(pathlib.Path(os.environ['TMPDIR']).glob('benchmark-check.*'))\n"
            "assert len(markers) == 1, markers\n"
            "timestamp = markers[0].stat().st_mtime_ns\n"
            "mode = os.environ['BENCHMARK_FIXTURE_MODE']\n"
            "if mode == 'runner-failure': sys.exit(7)\n"
            f"output = pathlib.Path({OUTPUT!r})\n"
            "if mode == 'missing-directory': sys.exit(0)\n"
            "output.mkdir(parents=True, exist_ok=True)\n"
            f"data = {report()!r}\n"
            "def write(name, offset, valid=True):\n"
            "    path = output / 'device with spaces' / name\n"
            "    path.parent.mkdir(parents=True, exist_ok=True)\n"
            "    path.write_text(json.dumps(data) if valid else '{}')\n"
            "    os.utime(path, ns=(timestamp + offset, timestamp + offset))\n"
            "if mode == 'empty-directory': sys.exit(0)\n"
            "write('stale-benchmarkData.json', -1000000000, valid=False)\n"
            "if mode == 'stale-only': sys.exit(0)\n"
            "if mode == 'equal-timestamp':\n"
            "    write('equal-benchmarkData.json', 0)\n"
            "    sys.exit(0)\n"
            "if mode == 'over-budget': data['benchmarks'][0]['metrics']['timeToInitialDisplayMs']['median'] = 501\n"
            "write('fresh-benchmarkData.json', 1000000000, mode != 'invalid-report')\n"
            "if mode == 'multiple-fresh': write('second-benchmarkData.json', 1000000000)\n",
            encoding="utf-8",
        )
        runner.chmod(0o755)

    def run_recipe(self, mode: str) -> subprocess.CompletedProcess:
        environment = os.environ | {
            "TMPDIR": str(self.root / "tmp"), "BENCHMARK_FIXTURE_MODE": mode,
        }
        environment.pop("MAKEFLAGS", None)
        result = subprocess.run(
            ["make", "--no-print-directory", "benchmark-check", "GRADLE_RUNNER=./fake-gradle.py"],
            cwd=self.root, env=environment, capture_output=True, text=True, timeout=15,
        )
        self.assertEqual([], list((self.root / "tmp").glob("benchmark-check.*")), "marker leaked")
        return result

    def test_one_fresh_report_is_selected_instead_of_stale_output(self) -> None:
        result = self.run_recipe("one-fresh")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("All benchmarks within budget.", result.stdout)

    def test_missing_output_directory_is_rejected(self) -> None:
        result = self.run_recipe("missing-directory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("benchmark output directory not found", result.stderr)

    def test_no_fresh_report_is_rejected(self) -> None:
        for mode in ("empty-directory", "stale-only", "equal-timestamp"):
            with self.subTest(mode=mode):
                result = self.run_recipe(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("expected exactly one fresh benchmarkData.json after this run; found 0", result.stderr)
                self.assertNotIn("All benchmarks", result.stdout)

    def test_multiple_fresh_reports_are_rejected_with_candidates(self) -> None:
        result = self.run_recipe("multiple-fresh")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("after this run; found 2", result.stderr)
        self.assertIn("fresh-benchmarkData.json", result.stderr)
        self.assertIn("second-benchmarkData.json", result.stderr)
        self.assertNotIn("stale-benchmarkData.json", result.stderr)

    def test_invalid_fresh_report_propagates_checker_failure(self) -> None:
        result = self.run_recipe("invalid-report")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("non-empty 'benchmarks' list", result.stderr)
        self.assertNotIn("All benchmarks", result.stdout)

    def test_over_budget_fresh_report_propagates_budget_failure(self) -> None:
        result = self.run_recipe("over-budget")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exceeded their performance budget", result.stderr)

    def test_failed_benchmark_runner_stops_selection(self) -> None:
        result = self.run_recipe("runner-failure")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("All benchmarks", result.stdout)
        self.assertFalse((self.root / OUTPUT).exists())


if __name__ == "__main__":
    unittest.main()
