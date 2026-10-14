#!/usr/bin/env python3
"""Tests for current-version Play release-note validation."""

from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/release_preflight.py"


class ReleasePreflightTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="release-preflight-")
        self.root = Path(self.temporary.name)
        (self.root / "gradle.properties").write_text(
            "billionbeers.versionCode=71\n", encoding="utf-8"
        )
        self.changelogs = self.root / "fastlane/metadata/android/en-US/changelogs"
        self.changelogs.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_preflight(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root)],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_requires_changelog_matching_current_version_code(self) -> None:
        (self.changelogs / "70.txt").write_text("Older release", encoding="utf-8")
        result = self.run_preflight()
        self.assertEqual(1, result.returncode)
        self.assertIn("versionCode 71", result.stderr)

    def test_rejects_empty_notes(self) -> None:
        (self.changelogs / "71.txt").write_text(" \n", encoding="utf-8")
        result = self.run_preflight()
        self.assertEqual(1, result.returncode)
        self.assertIn("are empty", result.stderr)

    def test_accepts_exactly_500_unicode_characters(self) -> None:
        (self.changelogs / "71.txt").write_text("🍺" * 500, encoding="utf-8")
        result = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_more_than_500_unicode_characters(self) -> None:
        (self.changelogs / "71.txt").write_text("🍺" * 501, encoding="utf-8")
        result = self.run_preflight()
        self.assertEqual(1, result.returncode)
        self.assertIn("501 characters", result.stderr)


if __name__ == "__main__":
    unittest.main()
