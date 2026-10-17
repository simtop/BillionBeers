#!/usr/bin/env python3
"""Tests for repeat-new-instrumented-tests.py."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("repeat-new-instrumented-tests.py")
ROOT = SCRIPT.parents[2]
SPEC = importlib.util.spec_from_file_location("repeat_new_instrumented_tests", SCRIPT)
assert SPEC and SPEC.loader
repeat_tests = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = repeat_tests
SPEC.loader.exec_module(repeat_tests)


class RepeatNewInstrumentedTestsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def write_module(self, module: str, source_path: str, source: str) -> None:
        module_dir = self.root / module
        module_dir.mkdir(parents=True, exist_ok=True)
        (module_dir / "build.gradle.kts").write_text(
            'plugins { id("billionbeers.android.managed.device") }\n', encoding="utf-8"
        )
        source_file = self.root / source_path
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text(source, encoding="utf-8")

    def test_collects_changed_managed_test_class(self):
        path = "feature/beerslist/src/androidTest/java/com/example/BeersListUiTest.kt"
        self.write_module(
            "feature/beerslist",
            path,
            "package com.example\n\nimport org.junit.Test\n\nclass BeersListUiTest { @Test fun loads() {} }\n",
        )

        self.assertEqual(
            [repeat_tests.TestTarget("feature/beerslist", "com.example.BeersListUiTest")],
            repeat_tests.collect_targets(self.root, [path]),
        )

    def test_ignores_changed_helper_without_test_annotation(self):
        path = "app/src/androidTest/java/com/example/HomeRobot.kt"
        self.write_module("app", path, "package com.example\n\nclass HomeRobot\n")

        self.assertEqual([], repeat_tests.collect_targets(self.root, [path]))

    def test_ignores_benchmark_android_test_sources(self):
        path = "benchmark/microbenchmark/src/androidTest/java/com/example/MapperBenchmark.kt"
        self.write_module(
            "benchmark/microbenchmark",
            path,
            "package com.example\n\nimport org.junit.Test\nclass MapperBenchmark { @Test fun benchmark() {} }\n",
        )

        self.assertEqual([], repeat_tests.collect_targets(self.root, [path]))

    def test_rejects_test_module_without_managed_device_plugin(self):
        path = "feature/example/src/androidTest/java/com/example/ExampleTest.kt"
        module_dir = self.root / "feature/example"
        module_dir.mkdir(parents=True)
        (module_dir / "build.gradle.kts").write_text('plugins { id("com.android.library") }\n')
        source_file = self.root / path
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package com.example\nimport org.junit.Test\nclass ExampleTest { @Test fun test() {} }\n"
        )

        with self.assertRaisesRegex(ValueError, "not opted into managed devices"):
            repeat_tests.collect_targets(self.root, [path])

    def test_requires_a_fresh_executed_result_for_the_selected_class(self):
        report_root = (
            self.root / "app/build/outputs/androidTest-results/managedDevice/atdApi35"
        )
        report_root.mkdir(parents=True)
        report = report_root / "TEST-com.example.AppUiTest.xml"
        report.write_text(
            '<testsuite tests="1" skipped="0"><testcase classname="com.example.AppUiTest" name="opens"/></testsuite>',
            encoding="utf-8",
        )

        repeat_tests.validate_fresh_report(
            self.root / "app", "com.example.AppUiTest", time.time() - 1
        )

    def test_rejects_a_repeat_without_a_fresh_result(self):
        report_root = (
            self.root / "app/build/outputs/androidTest-results/managedDevice/atdApi35"
        )
        report_root.mkdir(parents=True)
        report = report_root / "TEST-com.example.AppUiTest.xml"
        report.write_text(
            '<testsuite tests="1"><testcase classname="com.example.AppUiTest" name="opens"/></testsuite>',
            encoding="utf-8",
        )

        with self.assertRaisesRegex(RuntimeError, "did not produce a fresh executed result"):
            repeat_tests.validate_fresh_report(
                self.root / "app", "com.example.AppUiTest", time.time() + 5
            )

    def test_repeats_each_target_requested_number_of_times(self):
        targets = [repeat_tests.TestTarget("app", "com.example.AppUiTest")]
        with (
            patch.object(repeat_tests.subprocess, "run") as run,
            patch.object(repeat_tests, "validate_fresh_report"),
        ):
            repeat_tests.repeat_targets(self.root, targets, repetitions=2)

        self.assertEqual(2, run.call_count)
        self.assertIn("UI_TEST_CLASS=com.example.AppUiTest", run.call_args_list[0].args[0])
        self.assertIn("UI_TEST_RERUN=true", run.call_args_list[0].args[0])
        self.assertNotIn("--rerun-tasks", run.call_args_list[0].kwargs["env"].get("GRADLE_RUNNER", ""))

    def test_groups_classes_by_module_for_each_repeat_and_checks_each_class(self):
        targets = [
            repeat_tests.TestTarget("feature/beerslist", "com.example.SecondTest"),
            repeat_tests.TestTarget("app", "com.example.ZebraTest"),
            repeat_tests.TestTarget("app", "com.example.AlphaTest"),
        ]
        with (
            patch.dict("os.environ", {"GRADLE_RUNNER": "./gradlew --profile"}),
            patch.object(repeat_tests.subprocess, "run") as run,
            patch.object(repeat_tests, "validate_fresh_report") as validate,
        ):
            repeat_tests.repeat_targets(self.root, targets, repetitions=2)

        self.assertEqual(4, run.call_count)
        expected_calls = [
            (":app", "com.example.AlphaTest,com.example.ZebraTest"),
            (":app", "com.example.AlphaTest,com.example.ZebraTest"),
            (":feature:beerslist", "com.example.SecondTest"),
            (":feature:beerslist", "com.example.SecondTest"),
        ]
        for call, (module, selector) in zip(run.call_args_list, expected_calls):
            command = call.args[0]
            self.assertIn(f"MODULE={module}", command)
            self.assertIn(f"UI_TEST_CLASS={selector}", command)
            self.assertIn("UI_TEST_RERUN=true", command)
            self.assertEqual("./gradlew --profile", call.kwargs["env"]["GRADLE_RUNNER"])
        self.assertEqual(
            [
                "com.example.AlphaTest",
                "com.example.ZebraTest",
                "com.example.AlphaTest",
                "com.example.ZebraTest",
                "com.example.SecondTest",
                "com.example.SecondTest",
            ],
            [call.args[1] for call in validate.call_args_list],
        )

    def test_grouped_repeat_rejects_a_skipped_selected_class(self):
        report_root = self.root / "app/build/outputs/androidTest-results/managedDevice/atdApi35"
        report = report_root / "TEST-example.xml"

        def run_and_write_report(*args, **kwargs):
            report_root.mkdir(parents=True, exist_ok=True)
            report.write_text(
                '<testsuites><testsuite>'
                '<testcase classname="com.example.FirstTest" name="runs"/>'
                '<testcase classname="com.example.SecondTest" name="skips"><skipped/></testcase>'
                '</testsuite></testsuites>',
                encoding="utf-8",
            )
            return subprocess.CompletedProcess(args[0], 0)

        targets = [
            repeat_tests.TestTarget("app", "com.example.FirstTest"),
            repeat_tests.TestTarget("app", "com.example.SecondTest"),
        ]
        with patch.object(repeat_tests.subprocess, "run", side_effect=run_and_write_report):
            with self.assertRaisesRegex(RuntimeError, "com.example.SecondTest"):
                repeat_tests.repeat_targets(self.root, targets, repetitions=1)

    def test_grouped_repeat_rejects_a_missing_selected_class(self):
        report_root = self.root / "app/build/outputs/androidTest-results/managedDevice/atdApi35"
        report = report_root / "TEST-example.xml"

        def run_and_write_report(*args, **kwargs):
            report_root.mkdir(parents=True, exist_ok=True)
            report.write_text(
                '<testsuites><testsuite>'
                '<testcase classname="com.example.FirstTest" name="runs"/>'
                '</testsuite></testsuites>',
                encoding="utf-8",
            )
            return subprocess.CompletedProcess(args[0], 0)

        targets = [
            repeat_tests.TestTarget("app", "com.example.FirstTest"),
            repeat_tests.TestTarget("app", "com.example.SecondTest"),
        ]
        with patch.object(repeat_tests.subprocess, "run", side_effect=run_and_write_report):
            with self.assertRaisesRegex(RuntimeError, "com.example.SecondTest"):
                repeat_tests.repeat_targets(self.root, targets, repetitions=1)

    def test_make_places_task_scoped_rerun_after_android_test_task(self):
        for module, expected_task in (
            (":app", ":app:atdApi35DebugAndroidTest"),
            (":beer_database", ":beer_database:atdApi35AndroidDeviceTest"),
        ):
            with self.subTest(module=module):
                result = subprocess.run(
                    [
                        "make",
                        "--no-print-directory",
                        "ui-test-managed",
                        f"MODULE={module}",
                        "UI_TEST_CLASS=com.example.FirstTest,com.example.SecondTest",
                        "UI_TEST_RERUN=true",
                        "GRADLE_RUNNER=echo",
                    ],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=True,
                    timeout=10,
                )
                command = result.stdout.strip()
                self.assertIn(f"{expected_task} --rerun", command)
                self.assertIn(
                    "-Pandroid.testInstrumentationRunnerArguments.class=com.example.FirstTest,com.example.SecondTest",
                    command,
                )
                self.assertNotIn("--rerun-tasks", command)

        default = subprocess.run(
            [
                "make",
                "--no-print-directory",
                "ui-test-managed",
                "MODULE=:app",
                "GRADLE_RUNNER=echo",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=True,
            timeout=10,
        )
        self.assertNotIn("--rerun", default.stdout)

    def test_propagates_managed_test_failure_without_validating_report(self):
        failure = subprocess.CalledProcessError(1, ["make", "ui-test-managed"])
        targets = [repeat_tests.TestTarget("app", "com.example.AppUiTest")]
        with (
            patch.object(repeat_tests.subprocess, "run", side_effect=failure),
            patch.object(repeat_tests, "validate_fresh_report") as validate,
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                repeat_tests.repeat_targets(self.root, targets, repetitions=1)

        validate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
