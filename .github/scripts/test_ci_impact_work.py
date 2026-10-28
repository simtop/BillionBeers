from __future__ import annotations

import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("work", Path(__file__).with_name("ci-impact-work.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
IDENTITY = {"GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "2", "GITHUB_SHA": "a" * 40}


def selection():
    return {"configured_tasks": [":selected:aggregateTest", ":outside:aggregateTest"],
            "candidate_tasks": [":selected:aggregateTest"], "outside_candidate_set": [":outside:aggregateTest"]}


def task(path, duration, outcome="EXECUTED", category="Test tasks"):
    return {"task": path, "seconds": duration, "outcome": outcome, "category": category}


def profile(tier):
    return {"schema": 1, "available": True, "errors": [], "lane_outcome": "success",
            "provenance": IDENTITY | {"GITHUB_JOB": MODULE.LANES[tier]},
            "profiles": [{"file": "profile-1.html", "build_wall_seconds": 5,
                          "tasks": [task(":selected:atdApi35DebugAndroidTest", 10),
                                    task(":outside:testDebugUnitTest", 3),
                                    task(":outside:aggregateTest", 0),
                                    task(":outside:wasmJsBrowserTest", 2, "FROM-CACHE"),
                                    task(":smoke:releaseTest", 4),
                                    task(":outside:compileDebugKotlin", 20, category="Compilation")]}]}


class ImpactWorkTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.impact = {"schema": 1, "mode": "shadow", "execution_changed": False, "status": "candidates",
                       "provenance": {"checkout_sha": IDENTITY["GITHUB_SHA"], "explicit_scenario": False},
                       "changed_files": ["selected/src/main/Screen.kt"],
                       "selections": {tier: selection() for tier in MODULE.LANES}}
        self.write("ci-impact-shadow", "report.json", self.impact)
        for tier in MODULE.LANES:
            self.write(MODULE.ARTIFACTS[tier], "gradle-profiles.json", profile(tier))

    def write(self, artifact, filename, value):
        path = self.root / artifact / "nested" / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value))
        return path

    def test_concrete_tasks_use_owner_scope_without_losing_unmapped_or_cached_work(self):
        report = MODULE.report(self.root, IDENTITY)
        self.assertEqual("complete", report["status"])
        lane = report["lanes"]["instrumented"]
        self.assertEqual({"candidate": 10, "outside": 3, "unmapped": 4}, lane["executed_test_task_seconds"])
        self.assertEqual({"candidate": 0, "outside": 2, "unmapped": 0}, lane["nonexecuted_test_task_seconds"])
        self.assertEqual(20, lane["other_task_seconds"])
        self.assertEqual([], lane["unobserved_configured_owners"])
        rendered = MODULE.render(report)
        self.assertIn("not wall-clock savings", rendered)
        self.assertIn("may still be required", rendered)
        self.assertNotIn("savings_seconds", json.dumps(report))

    def test_repeated_execution_in_different_commands_counts_but_duplicate_profiles_do_not(self):
        evidence = profile("unit")
        repeat = copy.deepcopy(evidence["profiles"][0])
        repeat["file"] = "profile-2.html"
        evidence["profiles"].append(repeat)
        measured = MODULE.measure_lane("unit", selection(), evidence, IDENTITY)
        self.assertEqual(6, measured["executed_test_task_seconds"]["outside"])
        evidence["profiles"].append(repeat)
        with self.assertRaises(ValueError):
            MODULE.measure_lane("unit", selection(), evidence, IDENTITY)

    def test_failed_missing_or_stale_lanes_are_unavailable_not_zero_cost(self):
        for mutate in (lambda p: p.update(lane_outcome="failure"), lambda p: p.update(available=False),
                       lambda p: p.update(errors=["cannot parse profile"]), lambda p: p.update(profiles=[]),
                       lambda p: p["provenance"].update(GITHUB_RUN_ID="other"),
                       lambda p: p["provenance"].update(GITHUB_RUN_ATTEMPT="1"),
                       lambda p: p["provenance"].update(GITHUB_SHA="b" * 40),
                       lambda p: p["provenance"].update(GITHUB_JOB="screenshot-tests")):
            with self.subTest(mutate=mutate):
                evidence = profile("unit")
                mutate(evidence)
                self.write(MODULE.ARTIFACTS["unit"], "gradle-profiles.json", evidence)
                report = MODULE.report(self.root, IDENTITY)
                self.assertEqual("partial", report["status"])
                self.assertEqual("unavailable", report["lanes"]["unit"]["status"])
                self.assertNotIn("executed_test_task_seconds", report["lanes"]["unit"])

    def test_bad_durations_duplicate_tasks_and_empty_test_observations_are_rejected(self):
        for value in (-1, float("nan"), float("inf"), True, "12"):
            evidence = profile("unit")
            evidence["profiles"][0]["tasks"][0]["seconds"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                MODULE.measure_lane("unit", selection(), evidence, IDENTITY)
        evidence = profile("unit")
        evidence["profiles"][0]["tasks"].append(evidence["profiles"][0]["tasks"][0])
        with self.assertRaises(ValueError):
            MODULE.measure_lane("unit", selection(), evidence, IDENTITY)
        evidence["profiles"][0]["tasks"] = [task(":selected:compileKotlin", 1, category="Compilation")]
        with self.assertRaises(ValueError):
            MODULE.measure_lane("unit", selection(), evidence, IDENTITY)

    def test_incomplete_or_mismatched_candidate_inventory_cannot_produce_a_cost_comparison(self):
        for mutate in (lambda i: i["provenance"].update(checkout_sha="b" * 40),
                       lambda i: i.update(mode="execute"), lambda i: i.update(execution_changed=True),
                       lambda i: i.update(status="unavailable"), lambda i: i.update(schema=True),
                       lambda i: i["provenance"].update(explicit_scenario="false"),
                       lambda i: i.update(changed_files="not a path list"),
                       lambda i: i["selections"]["unit"]["outside_candidate_set"].clear(),
                       lambda i: i.update(status="full-fallback")):
            impact = copy.deepcopy(self.impact)
            mutate(impact)
            self.write("ci-impact-shadow", "report.json", impact)
            with self.subTest(mutate=mutate):
                self.assertEqual("unavailable", MODULE.report(self.root, IDENTITY)["status"])

    def test_unobserved_owners_and_offline_scenarios_are_explicit(self):
        evidence = profile("unit")
        evidence["profiles"][0]["tasks"] = [task(":selected:test", 1)]
        lane = MODULE.measure_lane("unit", selection(), evidence, IDENTITY)
        self.assertEqual([":outside"], lane["unobserved_configured_owners"])
        self.impact["provenance"]["explicit_scenario"] = True
        self.write("ci-impact-shadow", "report.json", self.impact)
        self.assertIn("Explicit offline scenario", MODULE.render(MODULE.report(self.root, IDENTITY)))

    def test_missing_ambiguous_and_malformed_evidence_remains_explicit(self):
        path = self.write(MODULE.ARTIFACTS["unit"], "gradle-profiles.json", profile("unit"))
        path.unlink()
        self.assertEqual("partial", MODULE.report(self.root, IDENTITY)["status"])
        path.write_text("incomplete JSON")
        self.assertEqual("partial", MODULE.report(self.root, IDENTITY)["status"])
        path.write_text(json.dumps(profile("unit")))
        (path.parent.parent / "gradle-profiles.json").write_text(path.read_text())
        self.assertEqual("unavailable", MODULE.report(self.root, IDENTITY)["lanes"]["unit"]["status"])

    def test_cli_writes_only_diagnostics_and_preserves_identity(self):
        output = self.root / "out/report.json"
        control = self.root / "outputs"
        with patch.dict(os.environ, IDENTITY | {"GITHUB_OUTPUT": str(control)}):
            self.assertEqual(0, MODULE.main([str(self.root), "--json", str(output)]))
        report = json.loads(output.read_text())
        self.assertEqual(IDENTITY, report["provenance"])
        self.assertFalse(report["execution_changed"])
        self.assertFalse(control.exists())

    def test_workflow_reporting_cannot_block_or_control_tests_and_keeps_full_verification(self):
        workflow = (Path(__file__).resolve().parents[1] / "workflows/ci.yml").read_text()
        for name in ("Download test impact candidates", "Download measured task profiles",
                     "Compare impact candidates with observed test work", "Archive measured test impact report"):
            body = workflow.split("- name: " + name, 1)[1].split("- name:", 1)[0]
            self.assertIn("continue-on-error: true", body)
            self.assertIn("if: always()", body)
        self.assertIn("merge-multiple: false", workflow)
        self.assertNotIn("impact-work.outputs", workflow)
        self.assertIn("./gradlew --profile --max-workers=2", workflow)
        self.assertIn("make test\n", workflow)
        self.assertIn("make screenshot-verify\n", workflow)
        self.assertIn("./gradlew ciGroupDebugAndroidTest ciGroupAndroidDeviceTest --profile", workflow)
        proof = workflow.split("- name: Record verified test result", 1)[1].split("- name: Upload verified test result", 1)[0]
        self.assertNotIn("ci-impact", proof)
        self.assertNotIn("ci-impact", workflow.split("  changes:", 1)[1].split("  secret-scan:", 1)[0])


if __name__ == "__main__":
    unittest.main()
