#!/usr/bin/env python3
"""Tests for repeat-new-instrumented-tests.py."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("repeat-new-instrumented-tests.py")
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
        self.assertEqual("./gradlew --rerun-tasks", run.call_args_list[0].kwargs["env"]["GRADLE_RUNNER"])


if __name__ == "__main__":
    unittest.main()
