#!/usr/bin/env python3
"""Regression coverage for conventional and KMP Android test command routing."""

from pathlib import Path
import subprocess
import tempfile
import unittest

from android_test_task import KMP_PLUGINS, task_for_module

REPO_ROOT = Path(__file__).resolve().parents[1]


class AndroidTestTaskTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def script(self, text: str) -> None:
        module = self.root / "nested" / "sample"
        module.mkdir(parents=True, exist_ok=True)
        (module / "build.gradle.kts").write_text(text, encoding="utf-8")

    def test_standard_android_keeps_debug_tasks(self) -> None:
        self.script('plugins { id("billionbeers.android.library") }')
        for prefix in ("connected", "atdApi35", "pixel9Api37"):
            with self.subTest(prefix=prefix):
                self.assertEqual(
                    f":nested:sample:{prefix}DebugAndroidTest",
                    task_for_module(self.root, ":nested:sample", prefix),
                )

    def test_supported_kmp_plugins_select_real_device_tasks(self) -> None:
        for plugin in KMP_PLUGINS:
            self.script(f'plugins {{ id("{plugin}") }}')
            for prefix in ("connected", "atdApi35", "pixel9Api37"):
                with self.subTest(plugin=plugin, prefix=prefix):
                    self.assertEqual(
                        f":nested:sample:{prefix}AndroidDeviceTest",
                        task_for_module(self.root, ":nested:sample", prefix),
                    )

    def test_commented_kmp_plugin_does_not_change_android_routing(self) -> None:
        self.script(
            '// id("billionbeers.kmp.library")\n'
            '/* id("com.android.kotlin.multiplatform.library") */\n'
            'plugins { id("billionbeers.android.library") }'
        )
        self.assertEqual(
            ":nested:sample:atdApi35DebugAndroidTest",
            task_for_module(self.root, ":nested:sample", "atdApi35"),
        )

    def test_invalid_or_missing_module_fails_instead_of_selecting_another_task(self) -> None:
        for module in ("sample", ":../sample", ":nested:sample:"):
            with self.subTest(module=module), self.assertRaises(ValueError):
                task_for_module(self.root, module, "atdApi35")
        with self.assertRaises(FileNotFoundError):
            task_for_module(self.root, ":missing", "atdApi35")

    def test_aggregate_wrappers_include_both_android_task_families(self) -> None:
        for target, expected in (
            ("ui-test-managed-ci", ("ciGroupDebugAndroidTest", "ciGroupAndroidDeviceTest")),
            ("ui-test-managed-all", ("allDevicesDebugAndroidTest", "allDevicesAndroidDeviceTest")),
        ):
            with self.subTest(target=target):
                result = subprocess.run(
                    ["make", "--no-print-directory", target, "GRADLE_RUNNER=echo"],
                    cwd=REPO_ROOT,
                    text=True,
                    capture_output=True,
                    check=True,
                    timeout=10,
                )
                self.assertEqual(list(expected), result.stdout.splitlines()[-1].split())

    def test_hosted_lanes_and_metadata_include_kmp_device_tests(self) -> None:
        for filename, command in (
            ("ci.yml", "ciGroupDebugAndroidTest ciGroupAndroidDeviceTest"),
            ("weekly-compat.yml", "compatGroupDebugAndroidTest compatGroupAndroidDeviceTest"),
        ):
            with self.subTest(workflow=filename):
                text = (REPO_ROOT / ".github/workflows" / filename).read_text(encoding="utf-8")
                self.assertIn(f"./gradlew {command}", text)
        makefile = (REPO_ROOT / "Makefile").read_text(encoding="utf-8")
        self.assertIn(
            "VERIFICATION_METADATA_REFERENCE_DEBUG_DEVICE_TASKS := "
            "ciGroupDebugAndroidTest ciGroupAndroidDeviceTest",
            makefile,
        )
        self.assertIn(":beer_database:assembleAndroidDeviceTest", makefile)


if __name__ == "__main__":
    unittest.main()
