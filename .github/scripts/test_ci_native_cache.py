from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("native_cache", Path(__file__).with_name("native-cache.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NativeCacheTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.inputs = {
            "gradle/libs.versions.toml": 'org-jetbrains-kotlin = "2.4.21"\n',
            "gradle.properties": "org.gradle.parallel=true\n",
            "build.gradle.kts": "// fixture\n",
            "gradle/wrapper/gradle-wrapper.properties": "distributionUrl=fixture\n",
            "build-logic/convention/src/main/kotlin/Options.kt": "class Options\n",
            "Makefile": "ios-test:\n\t./gradlew kmpIosSimulatorTest\n",
            "scripts/ios-framework-build.sh": "#!/bin/bash\n",
            ".github/workflows/ci.yml": "# workflow fixture\n",
            ".github/scripts/native-cache.py": "# helper fixture\n",
        }
        for name, content in self.inputs.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        (self.root / "Source.kt").write_text("class Beer\n")
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "add", *self.inputs, "Source.kt"], cwd=self.root, check=True)
        self.environment = {"GITHUB_RUN_ID": "123", "RUNNER_OS": "macOS", "RUNNER_ARCH": "ARM64",
                            "ImageOS": "macos14", "ImageVersion": "20261026.1", "RUNNER_TEMP": str(self.root / "temp")}

    def identity(self, **environment):
        return MODULE.cache_identity(self.root, self.environment | environment, "Xcode 16.2\nBuild version 16C5032a", self.root)

    def test_runs_share_a_key_but_toolchain_changes_invalidate_it(self):
        first = self.identity(GITHUB_RUN_ATTEMPT="1")
        self.assertEqual(first, self.identity(GITHUB_RUN_ID="987", GITHUB_RUN_ATTEMPT="2"))
        for changed in ({"RUNNER_ARCH": "X64"}, {"ImageVersion": "next"}, {"ImageOS": "macos15"}):
            with self.subTest(changed=changed):
                self.assertNotEqual(first["key"], self.identity(**changed)["key"])
        xcode = MODULE.cache_identity(self.root, self.environment, "Xcode 16.3", self.root)
        self.assertNotEqual(first["key"], xcode["key"])

    def test_build_and_cache_driver_inputs_invalidate_but_tracked_source_edits_do_not(self):
        first = self.identity()["key"]
        (self.root / "Source.kt").write_text("class ChangedBeer\n")
        self.assertEqual(first, self.identity()["key"])
        for name, content in self.inputs.items():
            with self.subTest(name=name):
                path = self.root / name
                path.write_text(content + "\n# changed\n")
                self.assertNotEqual(first, self.identity()["key"])
                path.write_text(content)
        catalog = self.root / "gradle/libs.versions.toml"
        catalog.write_text(self.inputs["gradle/libs.versions.toml"].replace("2.4.21", "2.4.22"))
        self.assertIn("kotlin-native-prebuilt-macos-aarch64-2.4.22", self.identity()["components"])

    def test_round_trip_preserves_components_and_symlinks_without_copying_any_locks(self):
        record = self.identity()
        source = record["konan_root"]
        stage = record["staging_root"]
        for name in record["components"]:
            directory = source / name / "cache"
            directory.mkdir(parents=True)
            (directory / "library.a").write_bytes(b"compiled library")
            for lock in ("cache.lock", ".lock", "cache.lck"):
                (directory / lock).write_text("transient")
            (directory / "alias.a").symlink_to("library.a")
        MODULE.copy_components(source, stage, record["components"])
        restored = self.root / "restored"
        MODULE.copy_components(stage, restored, record["components"])
        for name in record["components"]:
            directory = restored / name / "cache"
            self.assertEqual(b"compiled library", (directory / "alias.a").read_bytes())
            self.assertTrue((directory / "alias.a").is_symlink())
            self.assertEqual({"library.a", "alias.a"}, {path.name for path in directory.iterdir()})
        self.assertNotIn(".gradle", record["paths"])
        self.assertNotIn("!", record["paths"])

    def test_uncertain_identity_rejects_caching(self):
        for changed in ({"RUNNER_OS": "Linux"}, {"RUNNER_ARCH": "unknown"},
                        {"ImageOS": ""}, {"ImageVersion": ""}, {"RUNNER_TEMP": "relative"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.identity(**changed)

    def native_workflow(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows/ci.yml").read_text()
        return workflow.split("  native-tests:", 1)[1].split("  ci-gate:", 1)[0]

    def test_cache_never_replaces_full_native_verification(self):
        native = self.native_workflow()
        self.assertNotIn("restore-keys:", native)
        for step in ("Compile Apple targets", "Link Apple frameworks", "Build iOS host", "Run Apple simulator tests"):
            body = native.split("- name: " + step, 1)[1].split("- name:", 1)[0]
            self.assertNotIn("continue-on-error", body)
            self.assertNotIn("if:", body)
        self.assertIn("--mode snapshot", native)
        self.assertIn("--mode restore", native)
        self.assertLess(native.index("--mode snapshot"), native.index("Save verified Native component cache"))

    def test_only_successful_default_branch_code_can_publish_components(self):
        native = self.native_workflow()
        body = native.split("- name: Stage verified Native components without locks", 1)[1].split("- name:", 1)[0]
        condition = re.search(r"if: >-\n((?:          .*\n)+)", body)[1]
        condition = " ".join(condition.split()).replace("&&", " and ").replace("||", " or ")
        # Evaluate the actual workflow condition over events, including master dispatches
        # that deliberately check out a PR merge ref instead of trusted master source.
        expression = re.sub(r"\b(?:github|steps)\.[A-Za-z0-9_.-]+", lambda match: f"values[{match[0]!r}]", condition)
        defaults = {"github.ref": "refs/heads/master", "github.event_name": "push",
                    "github.event.inputs.pull_request_number": "",
                    "steps.native-cache-restore.outputs.cache-hit": "false",
                    "steps.native-cache-key.outputs.key": "valid-key"}
        cases = [({}, True), ({"github.event_name": "workflow_dispatch"}, True),
                 ({"github.event_name": "workflow_dispatch", "github.event.inputs.pull_request_number": "42"}, False),
                 ({"github.event_name": "pull_request"}, False), ({"github.event_name": "merge_group"}, False),
                 ({"github.ref": "refs/heads/feature"}, False),
                 ({"steps.native-cache-restore.outputs.cache-hit": "true"}, False),
                 ({"steps.native-cache-key.outputs.key": ""}, False)]
        for changes, expected in cases:
            with self.subTest(changes=changes):
                self.assertEqual(expected, eval(expression, {"__builtins__": {}}, {"values": defaults | changes, "success": lambda: True}))
        self.assertFalse(eval(expression, {"__builtins__": {}}, {"values": defaults, "success": lambda: False}))
        save = native.split("- name: Save verified Native component cache", 1)[1].split("- name:", 1)[0]
        self.assertIn("if: success() && steps.native-cache-snapshot.outcome == 'success'", save)


if __name__ == "__main__":
    unittest.main()
