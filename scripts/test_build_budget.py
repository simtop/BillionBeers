#!/usr/bin/env python3
"""Exercise the local build-time gate with synthetic Gradle Profiler output."""

from __future__ import annotations

import csv
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts/check-build-budget.sh"
SCENARIOS = ROOT / "benchmark.scenarios"


def scenario_names() -> list[str]:
    return re.findall(r"^([a-z_]+)\s*\{$", SCENARIOS.read_text(encoding="utf-8"), re.MULTILINE)


class BuildBudgetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="build budget ")
        self.addCleanup(self.temporary.cleanup)
        self.csv_path = Path(self.temporary.name) / "benchmark.csv"

    def run_checker(
        self,
        missing: str | None = None,
        omit_header: str | None = None,
    ) -> subprocess.CompletedProcess[str]:
        names = [name for name in scenario_names() if name != omit_header]
        with self.csv_path.open("w", newline="", encoding="utf-8") as output:
            writer = csv.writer(output)
            writer.writerow(["scenario", *names])
            writer.writerow(["version", *(["Gradle 9.8.0"] * len(names))])
            writer.writerow(["tasks", *(["assembleDebug"] * len(names))])
            writer.writerow(["value", *(["total execution time"] * len(names))])
            writer.writerow([
                "measured build #1",
                *("" if name == missing else "1000" for name in names),
            ])
        return subprocess.run(
            ["bash", str(CHECKER), str(self.csv_path)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=10,
        )

    def test_all_scenarios_with_measured_samples_pass(self) -> None:
        result = self.run_checker()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("All build-time budgets met.", result.stdout)

    def test_scenario_without_samples_fails_the_gate(self) -> None:
        missing = scenario_names()[0]
        result = self.run_checker(missing)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(f"FAIL {missing}: no measured builds were produced", result.stdout)
        self.assertNotIn("All build-time budgets met.", result.stdout)

    def test_required_scenario_without_a_header_column_fails_the_gate(self) -> None:
        missing = "incremental_deep"
        result = self.run_checker(omit_header=missing)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(f"FAIL missing required scenario columns: {missing}", result.stderr)
        self.assertNotIn("All build-time budgets met.", result.stdout)

    def test_scenario_mutators_reference_existing_source_files(self) -> None:
        paths = re.findall(
            r'apply-(?:abi|non-abi)-change-to\s*=\s*"([^"]+)"',
            SCENARIOS.read_text(encoding="utf-8"),
        )
        self.assertTrue(paths)
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue((ROOT / path).is_file(), f"missing Gradle Profiler mutator: {path}")


if __name__ == "__main__":
    unittest.main()
