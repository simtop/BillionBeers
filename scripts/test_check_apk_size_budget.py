#!/usr/bin/env python3
"""Test fail-closed APK size budget enforcement using synthetic manifests."""

from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from check_apk_size_budget import BudgetError, check_manifest


class ApkSizeBudgetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="apk size budget ")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.manifest = self.directory / "manifest.txt"
        self.budget = self.directory / "budget.txt"
        self.budget.write_text(
            "base.apk 100\nfeature-a.apk 20\nfeature-b.apk 30\ntotal 150\n",
            encoding="utf-8",
        )

    def write_manifest(self, *records: str) -> None:
        self.manifest.write_text("variant=releaseSmoke\n" + "\n".join(records) + "\n", encoding="utf-8")

    def record(self, path: str, size: int) -> str:
        return f"artifact={path} sha256=abc bytes={size}"

    def test_all_apks_within_per_artifact_and_total_limits_pass(self) -> None:
        self.write_manifest(self.record("base.apk", 99), self.record("feature-a.apk", 20), self.record("feature-b.apk", 30))
        self.assertEqual([], check_manifest(self.manifest, self.budget))

    def test_per_artifact_and_total_overages_fail(self) -> None:
        self.write_manifest(self.record("base.apk", 101), self.record("feature-a.apk", 20), self.record("feature-b.apk", 30))
        self.assertEqual(2, len(check_manifest(self.manifest, self.budget)))

    def test_missing_budgeted_apk_fails_closed(self) -> None:
        self.write_manifest(self.record("base.apk", 50))
        with self.assertRaisesRegex(BudgetError, "missing budgeted APKs"):
            check_manifest(self.manifest, self.budget)

    def test_unbudgeted_apk_fails_closed(self) -> None:
        self.write_manifest(self.record("base.apk", 50), self.record("feature-a.apk", 5), self.record("feature-b.apk", 5), self.record("new.apk", 5))
        with self.assertRaisesRegex(BudgetError, "unbudgeted APKs"):
            check_manifest(self.manifest, self.budget)

    def test_malformed_apk_record_fails_closed(self) -> None:
        self.write_manifest("artifact=base.apk bytes=10")
        with self.assertRaisesRegex(BudgetError, "malformed artifact record"):
            check_manifest(self.manifest, self.budget)

    def test_duplicate_apk_record_fails_closed(self) -> None:
        self.write_manifest(self.record("base.apk", 1), self.record("base.apk", 1), self.record("feature-a.apk", 1), self.record("feature-b.apk", 1))
        with self.assertRaisesRegex(BudgetError, "duplicate APK artifact"):
            check_manifest(self.manifest, self.budget)


if __name__ == "__main__":
    unittest.main()
