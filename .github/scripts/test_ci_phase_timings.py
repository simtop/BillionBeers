#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("render-phase-timings.py")
TIMED_COMMAND = Path(__file__).with_name("run-timed-command.sh")
SPEC = importlib.util.spec_from_file_location("render_phase_timings", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class RenderPhaseTimingsTest(unittest.TestCase):
    def test_writes_markdown_and_provenance_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            summary = root / "summary.md"
            output = root / "timings.json"
            environment = {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_RUN_ID": "123",
                "GITHUB_RUN_ATTEMPT": "2",
                "GITHUB_JOB": "web-tests",
                "GITHUB_SHA": "abc123",
            }
            with patch.dict(os.environ, environment, clear=False):
                result = MODULE.main(
                    [
                        "--title",
                        "Web lane phase timings",
                        "--phase",
                        "Browser and static verification|success|209",
                        "--phase",
                        "Evidence upload|success|2",
                        "--summary",
                        str(summary),
                        "--json",
                        str(output),
                    ]
                )

            self.assertEqual(0, result)
            record = json.loads(output.read_text())
            self.assertEqual(1, record["schema"])
            self.assertEqual(211, record["measured_phase_total_seconds"])
            self.assertEqual("web-tests", record["provenance"]["GITHUB_JOB"])
            self.assertEqual(209, record["phases"][0]["elapsed_seconds"])
            self.assertIn("Measured phase total: **3m 31s**", summary.read_text())

    def test_invalid_or_missing_elapsed_values_are_not_counted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "timings.json"
            summary = root / "summary.md"
            MODULE.main(
                [
                    "--title",
                    "Incomplete timings",
                    "--phase",
                    "Skipped phase|skipped|",
                    "--summary",
                    str(summary),
                    "--json",
                    str(output),
                ]
            )
            record = json.loads(output.read_text())
            self.assertIsNone(record["phases"][0]["elapsed_seconds"])
            self.assertEqual(0, record["measured_phase_total_seconds"])

    def test_malformed_phase_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SystemExit):
                MODULE.main(
                    [
                        "--title",
                        "Invalid",
                        "--phase",
                        "not-a-phase",
                        "--summary",
                        str(Path(directory) / "summary.md"),
                    ]
                )

    def test_timed_command_preserves_failure_and_writes_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "github-output"
            environment = {**os.environ, "GITHUB_OUTPUT": str(output)}
            result = subprocess.run(
                ["bash", str(TIMED_COMMAND), "bash", "-c", "exit 7"],
                env=environment,
                check=False,
            )

            self.assertEqual(7, result.returncode)
            metadata = dict(
                line.split("=", 1) for line in output.read_text().splitlines()
            )
            self.assertEqual("7", metadata["exit_code"])
            self.assertTrue(metadata["started_at"])
            self.assertTrue(metadata["finished_at"])
            self.assertGreaterEqual(int(metadata["elapsed_seconds"]), 0)


if __name__ == "__main__":
    unittest.main()
