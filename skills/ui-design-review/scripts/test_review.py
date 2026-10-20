#!/usr/bin/env python3
"""Exercise evidence integrity and report behavior with independent PNG fixtures."""

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zlib

sys.dont_write_bytecode = True

SPEC = importlib.util.spec_from_file_location("review", Path(__file__).with_name("review.py"))
review = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(review)


def png(width=2, height=3):
    def chunk(kind, payload):
        data = kind + payload
        return struct.pack(">I", len(payload)) + data + struct.pack(">I", zlib.crc32(data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress((b"\0" + b"\xff\xff\xff" * width) * height)) + chunk(b"IEND", b""))


class ReviewTest(unittest.TestCase):
    def test_project_adapter_selects_unique_golden_names_and_existing_fixtures(self):
        root = Path(__file__).resolve().parents[3]
        config = root / "qa" / "design-review.json"
        if not config.is_file():
            self.skipTest("No BillionBeers adapter in this skill installation")
        for name, screen in review.read_json(config)["screens"].items():
            with self.subTest(screen=name):
                selected = []
                # The CI format lane can see LFS pointers, so inspect names without decoding images.
                images = list((root / screen["image_directory"]).glob("*.png"))
                for case in screen["cases"]:
                    matches = [path for path in images if case["match"] in path.name]
                    self.assertEqual(len(matches), 1, case["id"])
                    selected.extend(matches)
                self.assertEqual(len(selected), len(set(selected)))
                self.assertTrue(review.source_hashes(root, screen["fixtures"]))

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "qa").mkdir()
        (self.root / "screenshots").mkdir()
        (self.root / "screen.kt").write_text("screen v1")
        (self.root / "fixtures.kt").write_text("fixed six beers")
        for key in ("light", "dark"):
            (self.root / "screenshots" / (key + ".png")).write_bytes(png())
        self.spec = {"platform": "android", "renderer": "test-renderer", "sources": ["screen.kt", "fixtures.kt"],
                     "fixtures": ["fixtures.kt"], "image_directory": "screenshots",
                     "cases": [{"id": key, "match": key, "conditions": {"theme": key}} for key in ("light", "dark")]}
        self.config = self.root / "qa" / "design-review.json"
        self.save_config()

    def save_config(self):
        self.config.write_text(json.dumps({"schema": 1, "screens": {"catalog": self.spec}}))

    def capture(self, name, mode="existing"):
        return review.capture(self.config, "catalog", self.root / name, mode)

    def test_freezes_images_and_records_unverified_import(self):
        capture = self.capture("before")
        data, images = review.load_capture(capture)
        self.assertEqual(data["freshness"], "existing-unverified")
        self.assertEqual(list(images), ["light", "dark"])
        (self.root / "screenshots" / "light.png").write_bytes(png(4, 5))
        self.assertEqual(review.png_size(images["light"]), [2, 3])
        with self.assertRaisesRegex(review.ReviewError, "already exists"):
            self.capture("before")

    def test_runs_renderer_without_shell_and_records_its_log(self):
        self.spec["record"] = [sys.executable, "-c", "print('rendered')"]
        self.save_config()
        capture = self.capture("rendered", "record")
        self.assertEqual(review.read_json(capture)["freshness"], "record")
        self.assertIn("rendered", (capture.parent / "render.log").read_text())

    def test_failed_render_does_not_create_a_success_manifest(self):
        self.spec["record"] = [sys.executable, "-c", "raise SystemExit(7)"]
        self.save_config()
        with self.assertRaisesRegex(review.ReviewError, "Renderer failed"):
            self.capture("failed", "record")
        self.assertFalse((self.root / "failed" / "capture.json").exists())

    def test_source_mutation_during_recording_is_rejected(self):
        self.spec["record"] = [sys.executable, "-c", "from pathlib import Path; Path('screen.kt').write_text('changed')"]
        self.save_config()
        with self.assertRaisesRegex(review.ReviewError, "changed during rendering"):
            self.capture("raced", "record")

    def test_ambiguous_case_is_rejected(self):
        (self.root / "screenshots" / "other-light.png").write_bytes(png())
        with self.assertRaisesRegex(review.ReviewError, "matched 2 images"):
            self.capture("ambiguous")

    def test_changed_conditions_dimensions_fixtures_and_platform_are_rejected(self):
        baseline = review.read_json(self.capture("baseline"))
        changes = [
            lambda value: value.update(platform="ios"),
            lambda value: value.update(fixtures={"fixtures.kt": "different"}),
            lambda value: value["cases"][0].update(pixels=[3, 4]),
            lambda value: value["cases"][0].update(conditions={"theme": "dark"}),
            lambda value: value["cases"].pop(),
        ]
        for change in changes:
            candidate = copy.deepcopy(baseline)
            change(candidate)
            with self.subTest(candidate=candidate), self.assertRaises(review.ReviewError):
                review.assert_comparable(baseline, candidate)

    def test_tampered_evidence_and_escaping_image_paths_are_rejected(self):
        path = self.capture("tamper")
        (path.parent / "light.png").write_bytes(png(4, 5))
        with self.assertRaisesRegex(review.ReviewError, "changed after capture"):
            review.load_capture(path)
        data = review.read_json(path)
        data["cases"][0]["image"] = "../screenshots/light.png"
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(review.ReviewError, "escapes"):
            review.load_capture(path)

    def test_report_includes_all_states_escapes_text_and_allows_baseline_to_win(self):
        before = self.capture("baseline")
        (self.root / "screen.kt").write_text("screen v2")
        after = self.capture("candidate")
        patch = self.root / "candidate.patch"
        patch.write_text("-screen v1\n+screen v2\n")
        proposal = {"title": "Catalog <script>alert(1)</script>", "scope": "two real fixture images",
                    "strengths": ["Readable"], "findings": ["Secondary action dominates"], "limitations": ["Not runtime tested"],
                    "recommendation": "baseline", "conclusion": "Keep the baseline.",
                    "prior_evidence": ["Earlier larger artwork reduced list density"],
                    "references": [{"title": "Reference <app>", "url": "https://example.com/design?a=1&b=2",
                                    "contribution": "Use imagery deliberately"}],
                    "options": [{"id": "a", "title": "UI design review: quiet cards", "capture": str(after),
                                 "patch": str(patch),
                                 "critique": "Shadow emphasis", "changes": ["Reduced elevation"],
                                 "assessment": "Too little benefit", "tradeoffs": ["Less separation"]}]}
        review_path = self.root / "review.json"
        review_path.write_text(json.dumps(proposal))
        output = review.report(before, review_path, self.root / "report")
        document = output.read_text()
        self.assertIn("&lt;script&gt;", document)
        self.assertNotIn("<script>alert(1)</script>", document)
        self.assertEqual(document.count("data:image/png;base64,"), 4)
        self.assertIn("Existing images imported", document)
        self.assertEqual(len(list((output.parent / "images").glob("*.png"))), 4)
        self.assertEqual((output.parent / "option-0.patch").read_text(), patch.read_text())
        self.assertIn("Keep the baseline.", (output.parent / "report.md").read_text())
        with mock.patch.object(review.Path, "cwd", return_value=self.root):
            default = review.report(before, review_path)
        self.assertTrue(default.is_relative_to((self.root / "design" / "reviews").resolve()))
        self.assertTrue(default.is_file())
        self.assertIn('href="https://example.com/design?a=1&amp;b=2"', document)
        self.assertIn("Reference &lt;app&gt;", document)
        self.assertIn("Earlier larger artwork reduced list density", document)
        for unsafe in ("javascript:alert(1)", "file:///private/example", "https:///missing-host"):
            proposal["references"][0]["url"] = unsafe
            review_path.write_text(json.dumps(proposal))
            with self.assertRaisesRegex(review.ReviewError, "Reference URL"):
                review.report(before, review_path, self.root / "unsafe-report")


class ExperimentStashTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        self.output = Path(self.temp.name) / "evidence" / "stash.json"
        self.git("init", "-q")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        (self.root / "screen.kt").write_text("original")
        (self.root / "other.kt").write_text("unrelated")
        self.git("add", "screen.kt", "other.kt")
        self.git("commit", "-qm", "fixture")

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.STDOUT).decode().strip()

    def test_preserves_stable_stash_and_untracked_sources_without_losing_older_stashes(self):
        (self.root / "other.kt").write_text("older experiment")
        self.git("stash", "push", "-m", "older")
        older = self.git("rev-parse", "refs/stash")
        (self.root / "screen.kt").write_text("proposed")
        (self.root / "preview.kt").write_text("new preview")
        output = review.stash_experiment(self.root, "catalog-fusion", ["screen.kt", "preview.kt"], self.output)
        saved = review.read_json(output)
        self.assertEqual(saved["base_revision"], self.git("rev-parse", "HEAD"))
        self.assertEqual(self.git("status", "--porcelain"), "")
        self.assertIn(older, self.git("stash", "list", "--format=%H"))
        self.assertEqual(saved["sources"]["screen.kt"], hashlib.sha256(b"proposed").hexdigest())
        self.git("stash", "apply", saved["stash_oid"])
        self.assertEqual((self.root / "screen.kt").read_text(), "proposed")
        self.assertEqual((self.root / "preview.kt").read_text(), "new preview")
        self.assertIn(saved["stash_oid"], self.git("stash", "list", "--format=%H"))
        with self.assertRaisesRegex(review.ReviewError, "already exists"):
            review.stash_experiment(self.root, "another", ["screen.kt", "preview.kt"], self.output)
        with self.assertRaisesRegex(review.ReviewError, "already has a stash"):
            review.stash_experiment(self.root, "catalog-fusion", ["screen.kt", "preview.kt"], self.output.with_name("another.json"))

    def test_rejects_unrelated_changes_and_broad_or_escaping_pathspecs(self):
        (self.root / "screen.kt").write_text("proposed")
        (self.root / "other.kt").write_text("unrelated change")
        for paths in (["screen.kt"], ["."], ["../outside"], [".git"]):
            with self.subTest(paths=paths), self.assertRaises(review.ReviewError):
                review.stash_experiment(self.root, "catalog", paths, self.output)
        self.assertEqual((self.root / "other.kt").read_text(), "unrelated change")
        self.assertEqual(self.git("stash", "list"), "")


if __name__ == "__main__":
    unittest.main()
