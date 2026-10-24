#!/usr/bin/env python3
"""Exercise automatic release versioning through its reusable command-line interface."""

from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("prepare_release_version.py")


class PrepareReleaseVersionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="release-version-")
        self.root = Path(self.temporary.name)
        self.properties = self.root / "gradle.properties"
        self.write_version(71, "71")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_version(self, code: int, name: str) -> None:
        self.properties.write_text(f"versionCode={code}\nversionName={name}\n", encoding="utf-8")

    def generate(self, day: str = "2026-10-24", *options: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--date", day, *options],
            cwd=self.root, capture_output=True, text=True, check=False,
        )

    def assert_version(self, code: int, name: str) -> None:
        contents = self.properties.read_text(encoding="utf-8")
        self.assertIn(f"versionCode={code}\n", contents)
        self.assertIn(f"versionName={name}\n", contents)

    def assert_rejected_without_changes(self, day: str, message: str) -> None:
        before = self.properties.read_bytes()
        result = self.generate(day)
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(message, result.stderr)
        self.assertEqual(before, self.properties.read_bytes())

    def test_first_repeat_and_next_day_need_no_manual_numbers(self) -> None:
        for day, code, name in (
            ("2026-10-24", 202610240, "20261024.0"),
            ("2026-10-24", 202610241, "20261024.1"),
            ("2026-10-25", 202610250, "20261025.0"),
        ):
            result = self.generate(day)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assert_version(code, name)

    def test_eleventh_release_rolls_to_next_date_and_stays_monotonic(self) -> None:
        self.write_version(202610248, "20261024.8")
        for code, name in (
            (202610249, "20261024.9"),
            (202610250, "20261025.0"),
            (202610251, "20261025.1"),
        ):
            result = self.generate()
            self.assertEqual(0, result.returncode, result.stderr)
            self.assert_version(code, name)

        # The real calendar date advances, but generated dates already spilled into it.
        result = self.generate("2026-10-25")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_version(202610252, "20261025.2")

    def test_old_dotted_names_retain_the_same_day_sequence(self) -> None:
        self.write_version(72, "2026.10.24.0")
        result = self.generate()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_version(202610241, "20261024.1")

    def test_code_retains_sequence_if_display_name_is_changed(self) -> None:
        self.write_version(202610244, "Preview")
        result = self.generate()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_version(202610245, "20261024.5")

    def test_clock_rollback_uses_last_generated_date_to_stay_monotonic(self) -> None:
        self.write_version(202610250, "20261025.0")
        result = self.generate("2026-10-24")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_version(202610251, "20261025.1")

    def test_existing_higher_code_cannot_be_downgraded(self) -> None:
        self.write_version(300000000, "1.0")
        self.assert_rejected_without_changes("2026-10-24", "must exceed existing versionCode")

    def test_date_boundaries_stay_monotonic_and_below_play_limit(self) -> None:
        for previous, following, expected in (
            ("20240229.9", "2024-03-01", 202403010),
            ("20261231.9", "2027-01-01", 202701010),
            ("20991231.9", "2100-01-01", 210001010),
            ("99991231.8", "9999-12-31", 999912319),
        ):
            with self.subTest(following=following):
                self.write_version(int(previous.replace(".", "")), previous)
                result = self.generate(following)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assert_version(expected, following.replace("-", "") + (".9" if following == "9999-12-31" else ".0"))
                self.assertLess(expected, 2_100_000_000)

    def test_rollover_at_end_of_supported_calendar_fails_safely(self) -> None:
        self.write_version(999912319, "99991231.9")
        self.assert_rejected_without_changes("9999-12-31", "exceeds the supported year range")

    def test_custom_file_and_keys_preserve_other_values_and_crlf(self) -> None:
        custom = self.root / "app-version.properties"
        original = b"# App version\r\nexample.versionCode=71\r\n\r\nexample.versionName=1.0\r\nother=keep\r\n"
        custom.write_bytes(original)
        custom.chmod(0o640)
        result = self.generate(
            "2026-10-24", "--properties", str(custom),
            "--version-code-key", "example.versionCode",
            "--version-name-key", "example.versionName",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            original.replace(b"Code=71", b"Code=202610240").replace(b"Name=1.0", b"Name=20261024.0"),
            custom.read_bytes(),
        )
        self.assertEqual(0o640, custom.stat().st_mode & 0o777)
        self.assert_version(71, "71")

    def test_missing_duplicate_or_invalid_properties_do_not_mutate_file(self) -> None:
        for contents in (
            "versionCode=71\n",
            "versionCode=71\nversionCode=72\nversionName=1.0\n",
            "versionCode=oops\nversionName=1.0\n",
        ):
            with self.subTest(contents=contents):
                self.properties.write_text(contents, encoding="utf-8")
                self.assert_rejected_without_changes("2026-10-24", "Release version update failed")


if __name__ == "__main__":
    unittest.main()
