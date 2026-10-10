#!/usr/bin/env python3
"""Exercise screenshot lookup and native recipe integration without launching an app."""

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]


def cli(*args):
    result = subprocess.run(
        [sys.executable, "skills/agent-qa/scripts/project_index.py", *args],
        cwd=ROOT, text=True, capture_output=True, check=True, timeout=10,
    )
    return json.loads(result.stdout)


class QaIntegrationTest(unittest.TestCase):
    def test_shared_save_form_resolves_to_actual_consumers_and_names_fixture_scope(self):
        shared = {"catalog", "search", "browse-results"}
        for host in ("android", "web", "ios", "desktop"):
            expected = shared | {"saved-filter-results"} if host == "android" else shared
            found = cli("search", "SharedFilterPresetForm", "--host", host, "--limit", "20")
            self.assertEqual(expected, {item["screen"] for item in found["screens"]})
        shown = cli("show", "search", "--host", "web")
        recipe = next(state for state in shown["states"] if state["id"] == "shared-large-text-save-form")
        self.assertEqual("test", recipe["scope"])
        self.assertEqual("fixture", recipe["mechanism"])

    def test_shared_beer_row_resolves_only_for_its_consuming_hosts(self):
        expected = {"catalog", "favorites", "search", "browse-results", "saved-filter-results"}
        for host in ("android", "web"):
            found = cli("search", "SharedBeerListItem", "--host", host, "--limit", "20")
            self.assertEqual(expected, {item["screen"] for item in found["screens"]})
        for host in ("ios", "desktop"):
            self.assertEqual([], cli("search", "SharedBeerListItem", "--host", host)["screens"])

    def test_android_save_form_resolves_to_its_consuming_screens(self):
        found = cli("search", "ComposeFilterPresetForm", "--host", "android", "--limit", "20")
        expected = {"catalog", "search", "browse-results", "saved-filter-results"}
        self.assertEqual(expected, {item["screen"] for item in found["screens"]})
        shown = cli("show", "search", "--host", "android")
        self.assertTrue(any(state["id"] == "large-text-save-form" and state["scope"] == "live-app"
                            for state in shown["states"]))

    def test_android_shared_row_clues_resolve_to_consuming_screens(self):
        expected = {"catalog", "search", "favorites", "browse-results", "saved-filter-results"}
        for query in ("ComposeBeersListItem", "Available"):
            found = cli("search", query, "--host", "android", "--limit", "20")
            self.assertTrue(expected <= {item["screen"] for item in found["screens"]})
        shown = cli("show", "search", "--host", "android")
        self.assertTrue(any(state["id"] == "large-text-row" and state["scope"] == "live-app"
                            for state in shown["states"]))

    def test_error_screenshot_titles_resolve_through_current_resource_text(self):
        for text in ("Beer unavailable", "Cerveza no disponible", "Bière indisponible"):
            found = cli("search", text, "--host", "web")
            self.assertEqual("beer-unavailable", found["screens"][0]["screen"])
            evidence = found["screens"][0]["matched_source"]
            self.assertTrue(any(item["value"] == text for item in evidence))
        self.assertEqual([], cli("search", "Cerveza no disponible", "--host", "android")["screens"])

    def test_state_recipe_scope_and_missing_live_controls_are_explicit(self):
        shown = cli("show", "saved-filters", "--host", "android")
        states = {state["id"]: state for state in shown["states"]}
        self.assertEqual("test", states["rename-failure"]["scope"])
        self.assertEqual("none", states["live-mutation-failure"]["scope"])
        self.assertEqual("unavailable", states["live-mutation-failure"]["mechanism"])
        self.assertEqual(["android"], list(shown["host_setup"]))
        web = cli("show", "beer-unavailable", "--host", "web")
        self.assertEqual("live-app", web["states"][0]["scope"])

    def test_native_test_recipes_select_the_declared_case_and_respect_runner_override(self):
        config = json.loads((ROOT / "qa/project.config.json").read_text())
        commands = [step for screen in config["screens"] for state in screen["states"]
                    for step in state["steps"] if step.startswith("Run make ui-test ")]
        self.assertTrue(commands)
        for command in commands:
            selected = re.search(r"UI_TEST_CLASS=([^ ]+)", command)[1]
            result = subprocess.run(
                ["make", "--no-print-directory", "-n", "ui-test", "MODULE=:app",
                 "UI_TEST_CLASS=" + selected, "GRADLE_RUNNER=custom-gradle"],
                cwd=ROOT, text=True, capture_output=True, check=True, timeout=10,
            )
            self.assertIn("custom-gradle :app:connectedDebugAndroidTest", result.stdout)
            self.assertIn('-Pandroid.testInstrumentationRunnerArguments.class="' + selected + '"', result.stdout)

    def test_make_search_and_screen_lookup_do_not_build(self):
        env = os.environ.copy()
        env.pop("MAKEFLAGS", None)
        for arguments, expected in (
            (["qa-find", "QUERY=Cerveza no disponible", "QA_HOST=web"], "beer-unavailable"),
            (["qa-screen", "SCREEN=saved-filters", "QA_HOST=android"], "rename-failure"),
        ):
            result = subprocess.run(
                ["make", "--no-print-directory", "GRADLE_RUNNER=false", *arguments],
                cwd=ROOT, env=env, capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn(expected, result.stdout)


if __name__ == "__main__":
    unittest.main()
