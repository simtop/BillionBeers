#!/usr/bin/env python3
"""Behavioral project-index checks in an independent, non-Kotlin fixture project."""

import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import project_index as indexer
from project_index import QaError


class ProjectIndexTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name).resolve()
        (self.root / "screen.txt").write_text('SCREEN Search\nLABEL No matches\nTAG search-empty\n')
        self.anchor = {"path": "screen.txt", "needle": "SCREEN Search"}
        self.profile = {
            "schema": 1,
            "hosts": {"browser": {"launch": ["Never executed by the index"],
                                  "preconditions": ["Isolated fixture"], "evidence": [self.anchor]}},
            "screens": [{"id": "search", "name": "Search", "hosts": ["browser"],
                         "recognition": ["Search"], "owners": [self.anchor],
                         "entry": {"steps": ["Open Search"], "preconditions": [], "evidence": [self.anchor]},
                         "states": [{"id": "empty", "hosts": ["browser"], "mechanism": "ui", "scope": "live-app",
                                     "steps": ["Submit an unmatched query"], "observe": "No matches",
                                     "limits": ["Fixture only"], "evidence": [self.anchor]}],
                         "limits": ["Fixture only"]}],
            "scan": {"include": ["**/*.txt"], "extractors": [
                {"kind": "component", "files": "*.txt", "pattern": r"SCREEN (.+)"},
                {"kind": "literal-text", "files": "*.txt", "pattern": r"LABEL (.+)"},
                {"kind": "selector", "files": "*.txt", "pattern": r"TAG (.+)"}]},
        }
        self.config = self.root / "config.json"

    def generate(self):
        self.config.write_text(json.dumps(self.profile))
        return indexer.generate(self.root, self.config)

    def cli(self, *args):
        self.config.write_text(json.dumps(self.profile))
        return subprocess.run([sys.executable, str(Path(indexer.__file__)), "--root", str(self.root),
                               "--config", "config.json", *args], text=True, capture_output=True, timeout=10)

    def test_other_language_project_works_via_public_cli(self):
        result = self.cli("generate")
        self.assertEqual(0, result.returncode, result.stderr)
        artifact = json.loads((self.root / "build/qa/project.json").read_text())
        self.assertEqual("Search", artifact["discovery"][0]["value"])
        self.assertEqual(artifact, self.generate())
        result = self.cli("search", "No matches", "--host", "browser")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("search", json.loads(result.stdout)["screens"][0]["screen"])
        result = self.cli("show", "search", "--host", "browser")
        shown = json.loads(result.stdout)
        self.assertEqual(["Open Search"], shown["entry"]["steps"])
        self.assertEqual("live-app", shown["states"][0]["scope"])

    def test_index_refreshes_labels_lines_hashes_and_new_files(self):
        before = self.generate()
        (self.root / "screen.txt").write_text('\nSCREEN Search\nLABEL Nothing found\nTAG search-empty\n')
        (self.root / "new.txt").write_text('SCREEN Detail\n')
        after = self.generate()
        self.assertNotEqual(before["source_files"]["screen.txt"], after["source_files"]["screen.txt"])
        self.assertEqual(2, after["screens"][0]["owners"][0]["line"])
        self.assertTrue(indexer.search(after, "Nothing found", "browser", 5)["screens"])
        result = indexer.search(after, "Detail", None, 5)
        self.assertEqual([], result["screens"])
        self.assertEqual("new.txt", result["source_matches"][0]["path"])

    def test_deleted_anchor_and_invalid_contracts_fail(self):
        original = copy.deepcopy(self.profile)
        for mutate in (
            lambda p: p["screens"][0].update(owners=[{"path": "screen.txt", "needle": "Absent"}]),
            lambda p: p["screens"][0].update(hosts=["unknown"]),
            lambda p: p["screens"][0]["states"][0].update(mechanism="unavailable"),
            lambda p: p["scan"]["extractors"][0].update(pattern="("),
            lambda p: p["scan"].update(include=["../outside/*.txt"]),
            lambda p: p["screens"].append(copy.deepcopy(p["screens"][0])),
        ):
            self.profile = copy.deepcopy(original)
            mutate(self.profile)
            with self.assertRaises(QaError):
                self.generate()

    def test_output_cannot_overwrite_input(self):
        for output in ("config.json", "screen.txt", "../outside.json"):
            result = self.cli("generate", "--output", output)
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertTrue((self.root / "screen.txt").read_text().startswith("SCREEN"))

    def test_generated_build_and_bin_sources_are_excluded(self):
        for directory in ("build", "bin"):
            path = self.root / directory
            path.mkdir()
            (path / "generated.txt").write_text('SCREEN Stale\n')
        index = self.generate()
        self.assertEqual([], indexer.search(index, "Stale", None, 5)["source_matches"])

    def test_host_filter_does_not_offer_another_hosts_fixture(self):
        self.profile["hosts"]["desktop"] = copy.deepcopy(self.profile["hosts"]["browser"])
        self.profile["screens"][0]["hosts"].append("desktop")
        self.profile["screens"][0]["states"][0]["hosts"] = ["browser"]
        index = self.generate()
        result = indexer.search(index, "Search", "desktop", 5)
        self.assertEqual([], result["screens"][0]["states"])
        with self.assertRaises(QaError):
            indexer.search(index, "Search", "missing", 5)

    def test_named_resource_keys_connect_translated_labels_to_owner(self):
        (self.root / "screen.txt").write_text('SCREEN Search\nRESOURCE empty\n')
        (self.root / "strings.txt").write_text('empty=Sin resultados\n')
        self.profile["scan"]["extractors"].extend([
            {"kind": "resource-reference", "files": "screen.txt", "pattern": r"RESOURCE (.+)"},
            {"kind": "resource-text", "files": "strings.txt", "pattern": r"(?P<key>\w+)=(?P<value>.+)"},
        ])
        index = self.generate()
        result = indexer.search(index, "Sin resultados", "browser", 5)
        self.assertEqual("search", result["screens"][0]["screen"])
        self.assertEqual("strings.txt", result["source_matches"][0]["path"])

    def test_unknown_title_does_not_match_a_common_word_or_json_metadata(self):
        (self.root / "screen.txt").write_text(
            'SCREEN Search\nLABEL No matches\nLABEL Cerveza especial\nLABEL Disponible\n'
        )
        index = self.generate()
        for query in ("Cerveza no disponible", "Search unknown-gibberish", "scope", "source_files"):
            self.assertEqual([], indexer.search(index, query, "browser", 5)["screens"], query)


if __name__ == "__main__":
    unittest.main()
