from __future__ import annotations

import importlib.util
import subprocess
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("module-stability-report.py")
SPEC = importlib.util.spec_from_file_location("module_stability_report", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ModuleStabilityReportTest(unittest.TestCase):
    def test_numstat_assigns_nested_paths_to_the_most_specific_module(self):
        output = (
            "10\t2\tfeature/src/Main.kt\n"
            "8\t3\tfeature/detail/src/Detail.kt\n"
            "-\t-\tfeature/src/logo.png\n"
            "5\t0\tREADME.md\n"
        )

        churn = MODULE.parse_numstat(output, ["feature", "feature/detail"])

        self.assertEqual(12, churn["feature"])
        self.assertEqual(11, churn["feature/detail"])

    def test_metrics_use_production_edges_and_show_isolated_modules(self):
        graph = {
            "nodes": [
                {"path": ":a", "relativeDirectory": "module-a"},
                {"path": ":b", "relativeDirectory": "module-b"},
                {"path": ":c", "relativeDirectory": "module-c"},
            ],
            "edges": [
                {"source": ":a", "target": ":b", "scopes": ["main"]},
                {"source": ":c", "target": ":a", "scopes": ["test"]},
            ],
        }

        metrics = MODULE.module_metrics(
            graph,
            {"module-a": 12, "module-b": 4, "module-c": 0},
        )
        by_module = {metric["name"].split()[1]: metric for metric in metrics}

        self.assertIn("stability 0% (fan-in 0, fan-out 1)", by_module[":a"]["displayValue"])
        self.assertIn("stability 100% (fan-in 1, fan-out 0)", by_module[":b"]["displayValue"])
        self.assertIn("stability n/a (fan-in 0, fan-out 0)", by_module[":c"]["displayValue"])
        self.assertEqual(12, by_module[":a"]["value"])
        self.assertEqual("lines/90d", by_module[":a"]["unit"])

    def test_churn_uses_a_90_day_utc_cutoff_and_non_merge_numstat(self):
        now = datetime(2026, 4, 1, tzinfo=timezone.utc)
        results = [
            subprocess.CompletedProcess([], 0, stdout="false\n", stderr=""),
            subprocess.CompletedProcess([], 0, stdout="7\t3\tmodule/src/A.kt\n", stderr=""),
        ]
        with patch.object(MODULE.subprocess, "run", side_effect=results) as run:
            churn = MODULE.collect_churn(Path("/repo"), ["module"], now=now)

        self.assertEqual({"module": 10}, churn)
        command = run.call_args_list[1].args[0]
        self.assertIn("--since=2026-01-01T00:00:00Z", command)
        self.assertIn("--no-merges", command)
        self.assertIn("--no-renames", command)
        self.assertIn("--numstat", command)

    def test_shallow_git_history_is_reported_as_unavailable(self):
        shallow_result = subprocess.CompletedProcess([], 0, stdout="true\n", stderr="")
        with patch.object(MODULE.subprocess, "run", return_value=shallow_result):
            with self.assertRaisesRegex(MODULE.GitHistoryUnavailable, "shallow"):
                MODULE.collect_churn(Path("/repo"), ["module"])


if __name__ == "__main__":
    unittest.main()
