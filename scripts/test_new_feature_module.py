#!/usr/bin/env python3
"""Exercise paired feature scaffolding in isolated repository fixtures."""

from __future__ import annotations

from contextlib import contextmanager
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/new-feature-module.sh"


class NewFeatureModuleTest(unittest.TestCase):
    @contextmanager
    def fixture(self):
        with tempfile.TemporaryDirectory() as root_directory, tempfile.TemporaryDirectory() as cwd_directory:
            root = Path(root_directory)
            sentinels = {
                root / "settings.gradle.kts": b'rootProject.name = "fixture"\n',
                root / "Makefile": b"help:\n\t@true\n",
                root / "config/architecture/project-dependency-policy.json": b'{"fixture": true}\n',
            }
            for path, contents in sentinels.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(contents)
            (root / "feature").mkdir()
            (root / "shared").mkdir()
            yield root, Path(cwd_directory), sentinels

    def run_scaffold(
        self,
        root: Path,
        cwd: Path,
        name: str = "sample",
        pascal: str | None = "Sample",
        *,
        fail_after_first_publish: bool = False,
    ) -> subprocess.CompletedProcess[str]:
        command = ["bash", str(SCRIPT), name]
        if pascal is not None:
            command.append(pascal)
        environment = os.environ | {"BILLIONBEERS_REPO_ROOT": str(root)}
        if fail_after_first_publish:
            environment["BILLIONBEERS_SCAFFOLD_TEST_FAIL_AFTER_FIRST_PUBLISH"] = "1"
        return subprocess.run(
            command,
            cwd=cwd,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def assert_clean_transaction_artifacts(self, root: Path) -> None:
        self.assertEqual([], sorted(path.name for path in root.glob(".new-feature-module*")))

    def assert_sentinels_unchanged(self, sentinels: dict[Path, bytes]) -> None:
        for path, contents in sentinels.items():
            self.assertEqual(contents, path.read_bytes(), path)

    def snapshot(self, root: Path) -> dict[str, bytes]:
        return {
            str(path.relative_to(root)): path.read_bytes()
            for path in sorted(root.rglob("*"))
            if path.is_file()
        }

    def test_generates_complete_android_and_shared_pair(self) -> None:
        with self.fixture() as (root, cwd, sentinels):
            result = self.run_scaffold(root, cwd)

            self.assertEqual(0, result.returncode, result.stderr)
            feature = root / "feature/sample"
            shared = root / "shared/sample"
            self.assertTrue(feature.is_dir())
            self.assertTrue(shared.is_dir())

            feature_build = (feature / "build.gradle.kts").read_text()
            shared_build = (shared / "build.gradle.kts").read_text()
            self.assertIn('id("billionbeers.android.feature")', feature_build)
            self.assertIn('id("billionbeers.android.screenshot")', feature_build)
            self.assertIn('project(":shared:sample")', feature_build)
            self.assertIn('id("billionbeers.kmp.feature")', shared_build)

            shared_view_model = next(shared.glob("src/commonMain/**/SampleViewModel.kt")).read_text()
            android_view_model = next(feature.glob("src/main/**/SampleViewModel.kt")).read_text()
            android_screen = next(feature.glob("src/main/**/SampleScreen.kt")).read_text()
            common_test = next(shared.glob("src/commonTest/**/SampleViewModelTest.kt")).read_text()
            self.assertIn("package com.simtop.billionbeers.shared.sample", shared_view_model)
            self.assertIn("class SampleViewModel : SharedSampleViewModel()", android_view_model)
            self.assertIn("binding<ViewModel>()", android_view_model)
            self.assertIn("SharedSampleContent(", android_screen)
            self.assertIn("class SampleViewModelTest", common_test)

            for directory in ("values", "values-es", "values-fr"):
                strings = feature / f"src/main/res/{directory}/strings.xml"
                self.assertIn("sample_title", strings.read_text())

            generated_text = "\n".join(
                path.read_text()
                for module in (feature, shared)
                for path in module.rglob("*")
                if path.is_file()
            )
            for forbidden in (
                "TODO",
                "billionbeers.android.dynamic.feature",
                "billionbeers.android.managed.device",
                "billionbeers.android.feature.uitest",
            ):
                self.assertNotIn(forbidden, generated_text)
            self.assertFalse(any(path.name == "androidTest" for path in feature.rglob("*")))
            self.assertIn("do not edit settings.gradle.kts", (feature / "README.md").read_text())
            self.assertIn("architecture-policy", (feature / "README.md").read_text())
            self.assertIn("Makefile KMP target lists", (shared / "README.md").read_text())
            self.assert_sentinels_unchanged(sentinels)
            self.assert_clean_transaction_artifacts(root)

    def test_derives_pascal_name_when_omitted(self) -> None:
        with self.fixture() as (root, cwd, _):
            result = self.run_scaffold(root, cwd, name="catalog", pascal=None)

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(
                next((root / "shared/catalog").glob("src/commonMain/**/CatalogViewModel.kt")).is_file()
            )

    def test_existing_android_destination_prevents_both_outputs(self) -> None:
        with self.fixture() as (root, cwd, _):
            feature = root / "feature/sample"
            feature.mkdir(parents=True)
            sentinel = feature / "keep.txt"
            sentinel.write_text("keep\n")

            result = self.run_scaffold(root, cwd)

            self.assertNotEqual(0, result.returncode)
            self.assertEqual("keep\n", sentinel.read_text())
            self.assertFalse((root / "shared/sample").exists())
            self.assert_clean_transaction_artifacts(root)

    def test_existing_shared_destination_prevents_both_outputs(self) -> None:
        with self.fixture() as (root, cwd, _):
            shared = root / "shared/sample"
            shared.mkdir(parents=True)
            sentinel = shared / "keep.txt"
            sentinel.write_text("keep\n")

            result = self.run_scaffold(root, cwd)

            self.assertNotEqual(0, result.returncode)
            self.assertEqual("keep\n", sentinel.read_text())
            self.assertFalse((root / "feature/sample").exists())
            self.assert_clean_transaction_artifacts(root)

    def test_rerun_preserves_generated_pair_byte_for_byte(self) -> None:
        with self.fixture() as (root, cwd, _):
            first = self.run_scaffold(root, cwd)
            self.assertEqual(0, first.returncode, first.stderr)
            before = self.snapshot(root)

            second = self.run_scaffold(root, cwd)

            self.assertNotEqual(0, second.returncode)
            self.assertEqual(before, self.snapshot(root))
            self.assert_clean_transaction_artifacts(root)

    def test_invalid_names_fail_without_output(self) -> None:
        for name in ("../escape", "Beer", "beer-name", "beer/name", "beer name", "1beer"):
            with self.subTest(name=name), self.fixture() as (root, cwd, sentinels):
                result = self.run_scaffold(root, cwd, name=name, pascal="Sample")

                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], list((root / "feature").iterdir()))
                self.assertEqual([], list((root / "shared").iterdir()))
                self.assert_sentinels_unchanged(sentinels)
                self.assert_clean_transaction_artifacts(root)

    def test_invalid_pascal_name_fails_without_output(self) -> None:
        with self.fixture() as (root, cwd, _):
            result = self.run_scaffold(root, cwd, pascal="Sample Name")

            self.assertNotEqual(0, result.returncode)
            self.assertEqual([], list((root / "feature").iterdir()))
            self.assertEqual([], list((root / "shared").iterdir()))
            self.assert_clean_transaction_artifacts(root)

    def test_failure_after_first_publication_rolls_back_both_modules(self) -> None:
        with self.fixture() as (root, cwd, sentinels):
            result = self.run_scaffold(root, cwd, fail_after_first_publish=True)

            self.assertNotEqual(0, result.returncode)
            self.assertFalse((root / "feature/sample").exists())
            self.assertFalse((root / "shared/sample").exists())
            self.assert_sentinels_unchanged(sentinels)
            self.assert_clean_transaction_artifacts(root)

    def test_concurrent_invocations_leave_one_complete_pair(self) -> None:
        with self.fixture() as (root, cwd, _):
            environment = os.environ | {"BILLIONBEERS_REPO_ROOT": str(root)}
            command = ["bash", str(SCRIPT), "sample", "Sample"]
            processes = [
                subprocess.Popen(
                    command,
                    cwd=cwd,
                    env=environment,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                )
                for _ in range(2)
            ]
            results = [process.communicate() + (process.returncode,) for process in processes]

            self.assertEqual([0, 1], sorted(result[2] for result in results), results)
            self.assertTrue((root / "feature/sample/build.gradle.kts").is_file())
            self.assertTrue((root / "shared/sample/build.gradle.kts").is_file())
            self.assert_clean_transaction_artifacts(root)


if __name__ == "__main__":
    unittest.main()
