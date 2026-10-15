#!/usr/bin/env python3
"""Test baseline-profile class coverage against synthetic R8 mappings."""

from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from check_baseline_profile_mapping import ProfileMappingError, check_profile_mapping, read_threshold


class BaselineProfileMappingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="baseline profile mapping ")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.profile = self.directory / "baseline-prof.txt"
        self.mapping = self.directory / "mapping.txt"
        self.threshold = self.directory / "threshold.txt"
        self.threshold.write_text("25.0\n", encoding="utf-8")

    def write_profile(self, *rules: str) -> None:
        self.profile.write_text("# profile\n" + "\n".join(rules) + "\n", encoding="utf-8")

    def write_mapping(self, *classes: str) -> None:
        self.mapping.write_text("".join(f"{name} -> a:\n    void run() -> a\n" for name in classes), encoding="utf-8")

    def test_all_profile_classes_present_pass(self) -> None:
        self.write_profile("Lcom/example/One;", "SPLcom/example/Two;->run()V")
        self.write_mapping("com.example.One", "com.example.Two")
        self.assertEqual((2, 0, []), check_profile_mapping(self.profile, self.mapping, 25.0))

    def test_missing_percentage_at_threshold_passes(self) -> None:
        self.write_profile("Lcom/example/One;", "Lcom/example/Two;", "Lcom/example/Three;", "Lcom/example/Four;")
        self.write_mapping("com.example.One", "com.example.Two", "com.example.Three")
        self.assertEqual((4, 1, []), check_profile_mapping(self.profile, self.mapping, 25.0))

    def test_missing_percentage_over_threshold_fails(self) -> None:
        self.write_profile("Lcom/example/One;", "Lcom/example/Two;", "Lcom/example/Three;", "Lcom/example/Four;")
        self.write_mapping("com.example.One", "com.example.Two")
        total, missing, failures = check_profile_mapping(self.profile, self.mapping, 25.0)
        self.assertEqual((4, 2), (total, missing))
        self.assertIn("50.0%", failures[0])

    def test_duplicate_profile_rules_count_class_once(self) -> None:
        self.write_profile("Lcom/example/One;", "HSPLcom/example/One;->run()V")
        self.write_mapping("com.example.One")
        self.assertEqual((1, 0, []), check_profile_mapping(self.profile, self.mapping, 0.0))

    def test_malformed_profile_rule_fails_closed(self) -> None:
        self.write_profile("not a profile rule")
        self.write_mapping("com.example.One")
        with self.assertRaisesRegex(ProfileMappingError, "malformed baseline profile rule"):
            check_profile_mapping(self.profile, self.mapping, 25.0)

    def test_empty_mapping_fails_closed(self) -> None:
        self.write_profile("Lcom/example/One;")
        self.mapping.write_text("# mapping with no classes\n", encoding="utf-8")
        with self.assertRaisesRegex(ProfileMappingError, "no R8 class mappings"):
            check_profile_mapping(self.profile, self.mapping, 25.0)

    def test_invalid_threshold_fails_closed(self) -> None:
        self.threshold.write_text("101\n", encoding="utf-8")
        with self.assertRaisesRegex(ProfileMappingError, "between 0 and 100"):
            read_threshold(self.threshold)


if __name__ == "__main__":
    unittest.main()
