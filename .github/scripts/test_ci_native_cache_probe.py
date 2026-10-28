from __future__ import annotations

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("native_cache", Path(__file__).with_name("native-cache-probe.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NativeCacheProbeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "gradle").mkdir()
        (self.root / "gradle/libs.versions.toml").write_text('org-jetbrains-kotlin = "2.4.21"\n')
        (self.root / "gradle.properties").write_text("org.gradle.parallel=true\n")
        (self.root / "build.gradle.kts").write_text("// fixture\n")
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "gradle/libs.versions.toml", "gradle.properties", "build.gradle.kts"], cwd=self.root, check=True)
        self.environment = {"GITHUB_RUN_ID": "123", "RUNNER_OS": "macOS", "RUNNER_ARCH": "ARM64",
                            "ImageOS": "macos14", "ImageVersion": "20261026.1"}

    def identity(self, **environment):
        return MODULE.cache_identity(self.root, self.environment | environment, "Xcode 16.2\nBuild version 16C5032a", self.root)

    def test_retry_hits_same_key_but_another_run_and_toolchain_cannot(self):
        first = self.identity(GITHUB_RUN_ATTEMPT="1")
        self.assertEqual(first, self.identity(GITHUB_RUN_ATTEMPT="2"))
        for changed in ({"GITHUB_RUN_ID": "124"}, {"RUNNER_ARCH": "X64"},
                        {"ImageVersion": "next"}, {"ImageOS": "macos15"}):
            with self.subTest(changed=changed):
                self.assertNotEqual(first["key"], self.identity(**changed)["key"])
        xcode = MODULE.cache_identity(self.root, self.environment, "Xcode 16.3", self.root)
        self.assertNotEqual(first["key"], xcode["key"])

    def test_build_inputs_change_key_but_source_edits_do_not(self):
        first = self.identity()["key"]
        (self.root / "Source.kt").write_text("class Beer\n")
        self.assertEqual(first, self.identity()["key"])
        (self.root / "gradle.properties").write_text("org.gradle.parallel=false\n")
        self.assertNotEqual(first, self.identity()["key"])

    def test_only_current_compiler_and_dependencies_are_cached_and_locks_are_excluded(self):
        paths = self.identity()["paths"].splitlines()
        self.assertEqual(str(self.root / ".konan/dependencies"), paths[0])
        self.assertEqual(str(self.root / ".konan/kotlin-native-prebuilt-macos-aarch64-2.4.21"), paths[1])
        self.assertEqual(["!" + str(self.root / ".konan/**/*.lock"), "!" + str(self.root / ".konan/**/*.lck")], paths[2:])
        self.assertNotIn(".gradle", "\n".join(paths))

    def test_uncertain_identity_rejects_caching(self):
        for changed in ({"GITHUB_RUN_ID": ""}, {"RUNNER_OS": "Linux"}, {"RUNNER_ARCH": "unknown"},
                        {"ImageOS": ""}, {"ImageVersion": ""}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.identity(**changed)

    def test_workflow_is_probe_only_and_preserves_full_native_verification(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows/ci.yml").read_text()
        native = workflow.split("  native-tests:", 1)[1].split("  ci-gate:", 1)[0]
        self.assertIn("github.head_ref == 'codex/native-cache-probe'", native)
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository", native)
        self.assertNotIn("restore-keys:", native)
        for step in ("Compile Apple targets", "Link Apple frameworks", "Build iOS host", "Run Apple simulator tests"):
            body = native.split("- name: " + step, 1)[1].split("- name:", 1)[0]
            self.assertNotIn("continue-on-error", body)
            self.assertNotIn("if:", body)
        self.assertIn("success() && steps.native-cache-restore.outputs.cache-hit != 'true'", native)
        self.assertIn("cache-hit", native)


if __name__ == "__main__":
    unittest.main()
