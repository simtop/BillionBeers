#!/usr/bin/env python3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-screenshot-inventory.py")


class ScreenshotInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.inventory = self.root / "inventory"
        self.inventory.mkdir()
        self.baseline = self.root / "baseline.tsv"
        (self.inventory / "module.tsv").write_text(
            "module\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n"
            "module\tPreviewB\tDARK\t1.0\ten\tLTR\tcompact\tPreviewB\tgroup\tdevice\n",
            encoding="utf-8",
        )
        self.baseline.write_text(
            "module\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n"
            "module\tPreviewB\tDARK\t1.0\ten\tLTR\tcompact\tPreviewB\tgroup\tdevice\n",
            encoding="utf-8",
        )

    def tearDown(self):
        self.temp.cleanup()

    def run_checker(self, *extra):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                str(self.inventory),
                str(self.baseline),
                "--root",
                str(self.root),
                *extra,
            ],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_matching_inventory_passes(self):
        result = self.run_checker()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("matches (2", result.stdout)
        self.assertIn("no orphaned generated goldens", result.stdout)

    def test_dropped_preview_fails_and_names_the_missing_entry(self):
        inventory = self.inventory / "module.tsv"
        inventory.write_text(inventory.read_text(encoding="utf-8").splitlines()[0] + "\n", encoding="utf-8")
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Missing x1: module / PreviewB", result.stderr)

    def test_update_records_current_inventory(self):
        (self.inventory / "module.tsv").write_text(
            "module\tPreviewC\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewC\tgroup\tdevice\n",
            encoding="utf-8",
        )
        result = self.run_checker("--update")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PreviewC", self.baseline.read_text(encoding="utf-8"))

    def test_scoped_verification_ignores_other_modules(self):
        (self.inventory / "module.tsv").write_text(
            "com.simtop.billionbeers.core.designsystem\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n",
            encoding="utf-8",
        )
        self.baseline.write_text(
            "com.simtop.billionbeers.core.designsystem\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n"
            "com.simtop.billionbeers.feature.favorites\tPreviewB\tDARK\t1.0\ten\tLTR\tcompact\tPreviewB\tgroup\tdevice\n",
            encoding="utf-8",
        )
        result = self.run_checker("--scoped")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("matches (1", result.stdout)

    def test_scoped_update_preserves_other_module_baseline_rows(self):
        (self.inventory / "module.tsv").write_text(
            "com.simtop.billionbeers.core.designsystem\tPreviewC\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewC\tgroup\tdevice\n",
            encoding="utf-8",
        )
        other = "com.simtop.billionbeers.feature.favorites\tPreviewB\tDARK\t1.0\ten\tLTR\tcompact\tPreviewB\tgroup\tdevice"
        self.baseline.write_text(
            "com.simtop.billionbeers.core.designsystem\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n"
            + other + "\n",
            encoding="utf-8",
        )
        result = self.run_checker("--update", "--scoped")
        self.assertEqual(result.returncode, 0, result.stderr)
        contents = self.baseline.read_text(encoding="utf-8")
        self.assertIn("PreviewC", contents)
        self.assertIn(other, contents)
        self.assertNotIn("PreviewA", contents)

    def test_scoped_verification_detects_a_module_with_no_remaining_previews(self):
        (self.inventory / "module.tsv").write_text("", encoding="utf-8")
        self.baseline.write_text(
            "module\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n",
            encoding="utf-8",
        )
        result = self.run_checker("--scoped")
        self.assertEqual(result.returncode, 1)
        self.assertIn("Missing x1: module / PreviewA", result.stderr)

    def test_stale_golden_fails_even_after_preview_inventory_is_updated(self):
        current = "module\tPreviewA\tLIGHT\t1.0\ten\tLTR\tcompact\tPreviewA\tgroup\tdevice\n"
        (self.inventory / "module.tsv").write_text(current, encoding="utf-8")
        self.baseline.write_text(current, encoding="utf-8")
        module = self.root / "feature/example"
        (module / "build.gradle.kts").parent.mkdir(parents=True)
        (module / "build.gradle.kts").write_text(
            'android { namespace = "module" }', encoding="utf-8"
        )
        image_dir = module / "src/test/snapshots/images"
        image_dir.mkdir(parents=True)
        for preview in ("PreviewA", "PreviewB"):
            golden = image_dir / (
                "com.simtop.billionbeers.screenshot_ModuleScreenshotTest_snapshot["
                f"{preview}]_{preview.lower()}.png"
            )
            golden.touch()

        result = self.run_checker()

        self.assertEqual(result.returncode, 1)
        self.assertIn(
            "Orphan: module / feature/example/src/test/snapshots/images/",
            result.stderr,
        )
        self.assertIn("snapshot[PreviewB]", result.stderr)


if __name__ == "__main__":
    unittest.main()
