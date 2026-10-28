from __future__ import annotations

import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("impact", Path(__file__).with_name("ci-impact-shadow.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def model():
    modules = (":domain", ":shared:detail", ":feature:detail", ":app", ":other", ":helper", ":konsist")
    inventory = {tier: [] for tier in MODULE.TIERS}
    for module in modules:
        for tier, task in (("unit", "jvmTest"), ("screenshot", "verifyPaparazziDebug"),
                           ("instrumented", "ciGroupDebugAndroidTest"), ("native", "iosSimulatorArm64Test"),
                           ("web", "wasmJsBrowserTest")):
            inventory[tier].append(module + ":" + task)
    inventory["unit"].append(":konsist:test")
    # App dynamic-feature delivery intentionally has no compile dependency on :feature:detail.
    return {"schemaVersion": 1,
            "nodes": [{"path": module, "relativeDirectory": module[1:].replace(":", "/")} for module in modules],
            "edges": [{"source": source, "target": target} for source, target in (
                (":shared:detail", ":domain"), (":feature:detail", ":shared:detail"),
                (":feature:detail", ":helper"), (":app", ":domain"))],
            "verificationTasks": inventory}


def candidates(record, tier):
    return set(record["selections"][tier]["candidate_tasks"])


class ImpactShadowTest(unittest.TestCase):
    def setUp(self):
        self.model = model()

    def test_common_production_selects_transitive_consumers_and_global_architecture_rules(self):
        record = MODULE.select(self.model, ["domain/src/commonMain/kotlin/Beer.kt"])
        self.assertEqual("candidates", record["status"])
        self.assertIn(":feature:detail:jvmTest", candidates(record, "unit"))
        self.assertIn(":app:jvmTest", candidates(record, "unit"))
        self.assertIn(":konsist:test", candidates(record, "unit"))
        self.assertNotIn(":other:jvmTest", candidates(record, "unit"))
        self.assertIn(":shared:detail:iosSimulatorArm64Test", candidates(record, "native"))

    def test_platform_changes_preserve_source_set_boundaries(self):
        for source, tiers in (("iosMain", {"native"}), ("wasmJsMain", {"unit", "web"}),
                              ("androidMain", {"unit", "screenshot", "instrumented"})):
            with self.subTest(source=source):
                record = MODULE.select(self.model, [f"domain/src/{source}/kotlin/Adapter.kt"])
                actual = {tier for tier in MODULE.TIERS if candidates(record, tier) - {":konsist:test"}}
                self.assertEqual(tiers, actual)

    def test_local_unit_helpers_invalidate_screenshots_without_propagating_to_consumers(self):
        record = MODULE.select(self.model, ["domain/src/test/kotlin/Helper.kt"])
        self.assertIn(":domain:verifyPaparazziDebug", candidates(record, "screenshot"))
        self.assertNotIn(":shared:detail:jvmTest", candidates(record, "unit"))
        self.assertFalse(candidates(record, "instrumented"))

    def test_goldens_and_device_tests_only_select_their_owners(self):
        for source, tier, task in (("test/snapshots/images/old.png", "screenshot", "verifyPaparazziDebug"),
                                   ("androidTest/kotlin/Journey.kt", "instrumented", "ciGroupDebugAndroidTest"),
                                   ("androidDeviceTest/kotlin/Journey.kt", "instrumented", "ciGroupDebugAndroidTest")):
            with self.subTest(source=source):
                record = MODULE.select(self.model, [f"feature/detail/src/{source}"])
                self.assertEqual({f":feature:detail:{task}"}, candidates(record, tier))

    def test_common_tests_execute_for_all_supported_test_targets_but_not_consumer_modules(self):
        record = MODULE.select(self.model, ["domain/src/commonTest/kotlin/BeerTest.kt"])
        self.assertEqual({":domain:iosSimulatorArm64Test"}, candidates(record, "native"))
        self.assertEqual({":domain:wasmJsBrowserTest"}, candidates(record, "web"))
        self.assertFalse(candidates(record, "screenshot"))

    def test_test_helpers_and_ksp_edges_are_included_regardless_of_scope(self):
        self.model["edges"][-2]["scopes"] = ["test"]
        self.model["edges"][-2]["configurations"] = ["ksp"]
        record = MODULE.select(self.model, ["helper/src/main/kotlin/Fake.kt"])
        self.assertIn(":feature:detail:verifyPaparazziDebug", candidates(record, "screenshot"))

    def test_app_integration_remains_selected_for_dynamic_feature_production(self):
        record = MODULE.select(self.model, ["feature/detail/src/main/kotlin/Screen.kt"])
        self.assertIn(":app:ciGroupDebugAndroidTest", candidates(record, "instrumented"))

    def test_cycles_terminate_and_never_lose_a_consumer(self):
        self.model["edges"].append({"source": ":domain", "target": ":feature:detail"})
        record = MODULE.select(self.model, ["shared/detail/src/commonMain/kotlin/Screen.kt"])
        self.assertIn(":app:jvmTest", candidates(record, "unit"))

    def test_build_changes_unknown_sources_and_unmapped_files_fall_back_to_every_task(self):
        for path in ("build.gradle.kts", "feature/detail/build.gradle.kts", "scripts/runner.sh",
                     ".github/scripts/ci-impact-shadow.py", "domain/src/newPlatformMain/Adapter.kt", "new-module/src/main/New.kt"):
            with self.subTest(path=path):
                record = MODULE.select(self.model, [path])
                self.assertEqual("full-fallback", record["status"])
                for tier in MODULE.TIERS:
                    self.assertEqual(set(self.model["verificationTasks"][tier]), candidates(record, tier))

    def test_docs_are_inert_but_markdown_runtime_resources_are_production_inputs(self):
        record = MODULE.select(self.model, ["docs/guide.md", "README.md"])
        self.assertFalse(any(candidates(record, tier) for tier in MODULE.TIERS))
        record = MODULE.select(self.model, ["domain/src/commonMain/resources/help.md"])
        self.assertTrue(candidates(record, "native"))

    def test_renames_deletions_and_unions_invalidate_both_old_and_new_owners(self):
        record = MODULE.select(self.model, ["domain/src/commonMain/Old.kt", "other/src/commonMain/New.kt"])
        self.assertIn(":domain:jvmTest", candidates(record, "unit"))
        self.assertIn(":other:jvmTest", candidates(record, "unit"))
        self.assertEqual(record, MODULE.select(self.model, list(reversed(record["changed_files"]))))

    def test_malformed_graphs_are_rejected_instead_of_omitting_tests(self):
        cases = [None, [], {"schemaVersion": True}]
        for mutate in (lambda m: m.update(schemaVersion=2), lambda m: m["nodes"].append(m["nodes"][0]),
                       lambda m: m["nodes"][0].update(relativeDirectory="../outside"),
                       lambda m: m["edges"].append({"source": ":absent", "target": ":domain"}),
                       lambda m: m["verificationTasks"].pop("native"),
                       lambda m: m["verificationTasks"]["unit"].append(":absent:test")):
            value = copy.deepcopy(self.model)
            mutate(value)
            cases.append(value)
        for value in cases:
            with self.subTest(model=value), self.assertRaises(ValueError):
                MODULE.select(value, [])

    def test_unreachable_base_is_unavailable_and_never_writes_ci_control_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.dict(os.environ, {"GITHUB_OUTPUT": str(root / "outputs")}), \
                    patch.object(MODULE, "git", side_effect=OSError("unreachable")):
                self.assertEqual(0, MODULE.main(["--root", str(root)]))
            record = json.loads((root / "build/reports/ci-impact-shadow/report.json").read_text())
            self.assertEqual("unavailable", record["status"])
            self.assertFalse(record["execution_changed"])
            self.assertFalse((root / "outputs").exists())

    def test_workflow_keeps_shadow_outputs_out_of_execution_and_reuse_gates(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows/ci.yml").read_text()
        step = workflow.split("- name: Report test impact candidates in shadow mode", 1)[1].split("- name:", 1)[0]
        self.assertIn("continue-on-error: true", step)
        self.assertIn("run: make ci-impact-shadow", step)
        self.assertNotIn("impact-shadow.outputs", workflow)
        self.assertNotIn("ci-impact-shadow", workflow.split("  changes:", 1)[1].split("  secret-scan:", 1)[0])
        self.assertIn("--rerun-tasks --continue", (Path(__file__).resolve().parents[2] / "Makefile").read_text())


if __name__ == "__main__":
    unittest.main()
