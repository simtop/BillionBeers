#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


STATIC_SPEC = importlib.util.spec_from_file_location(
    "verify_web_static", Path(__file__).with_name("verify_web_static.py")
)
assert STATIC_SPEC.loader is not None
static = importlib.util.module_from_spec(STATIC_SPEC)
import sys

sys.modules[STATIC_SPEC.name] = static
STATIC_SPEC.loader.exec_module(static)

RELEASE_SPEC = importlib.util.spec_from_file_location(
    "verify_web_release_artifacts", Path(__file__).with_name("verify_web_release_artifacts.py")
)
assert RELEASE_SPEC.loader is not None
release = importlib.util.module_from_spec(RELEASE_SPEC)
sys.modules[RELEASE_SPEC.name] = release
RELEASE_SPEC.loader.exec_module(release)


class VerifyWebReleaseArtifactsTest(unittest.TestCase):
    def make_distribution(self, root: Path, *, extra: bool = False) -> Path:
        distribution = root / "distribution"
        distribution.mkdir()
        (distribution / "index.html").write_text(
            '<!doctype html><script src="web-app.js"></script>', encoding="utf-8"
        )
        (distribution / "web-app.js").write_text(
            'console.log(r.p+"app.wasm");', encoding="utf-8"
        )
        (distribution / "app.wasm").write_bytes(b"wasm")
        if extra:
            (distribution / "data.txt").write_text("fixture", encoding="utf-8")
        return distribution

    def create_packet(self, root: Path, *, distribution: Path | None = None) -> Path:
        distribution = distribution or self.make_distribution(root)
        packet = root / "packet"
        release.create_packet(
            distribution,
            packet,
            repository=root,
            browser_smoke_passed=True,
            source_revision="revision",
            working_tree="clean",
            provenance={"GITHUB_REPOSITORY": "owner/repo"},
        )
        return packet

    def test_create_and_verify_packet_records_exact_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)

            manifest = release.verify_packet(packet)

            self.assertEqual("web-wasm", manifest["target"])
            self.assertEqual("revision", manifest["source_revision"])
            self.assertEqual("clean", manifest["working_tree"])
            self.assertEqual(3, manifest["file_count"])
            self.assertEqual(
                ["app.wasm", "index.html", "web-app.js"],
                [record["path"] for record in manifest["files"]],
            )
            self.assertTrue((packet / "distribution" / "app.wasm").is_file())

    def test_tree_digest_is_stable_for_creation_order(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            first_distribution = self.make_distribution(Path(first), extra=True)
            second_distribution = Path(second) / "distribution"
            second_distribution.mkdir()
            for name, content in (
                ("web-app.js", 'console.log(r.p+"app.wasm");'),
                ("data.txt", "fixture"),
                ("app.wasm", "wasm"),
                ("index.html", '<!doctype html><script src="web-app.js"></script>'),
            ):
                target = second_distribution / name
                target.write_bytes(content.encode() if isinstance(content, str) else content)
            first_packet = self.create_packet(Path(first), distribution=first_distribution)
            second_packet = self.create_packet(Path(second), distribution=second_distribution)

            first_manifest = release.verify_packet(first_packet)
            second_manifest = release.verify_packet(second_packet)

            self.assertEqual(first_manifest["tree_sha256"], second_manifest["tree_sha256"])
            self.assertEqual(first_manifest["files"], second_manifest["files"])

    def test_changed_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)
            (packet / "distribution" / "app.wasm").write_bytes(b"changed")

            with self.assertRaisesRegex(release.VerificationError, "match the manifest"):
                release.verify_packet(packet)

    def test_missing_and_extra_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)
            (packet / "distribution" / "app.wasm").unlink()
            with self.assertRaises(release.VerificationError):
                release.verify_packet(packet)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)
            (packet / "distribution" / "extra.bin").write_bytes(b"extra")
            with self.assertRaisesRegex(release.VerificationError, "match the manifest"):
                release.verify_packet(packet)

    def test_packet_rejects_unexpected_top_level_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)
            (packet / "notes.txt").write_text("unexpected", encoding="utf-8")

            with self.assertRaisesRegex(release.VerificationError, "unexpected file"):
                release.verify_packet(packet)

    def test_empty_missing_and_symlinked_distributions_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaises(release.VerificationError):
                release.create_packet(
                    root / "missing",
                    root / "packet",
                    repository=root,
                    browser_smoke_passed=True,
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )
            empty = root / "empty"
            empty.mkdir()
            with self.assertRaises(release.VerificationError):
                release.create_packet(
                    empty,
                    root / "empty-packet",
                    repository=root,
                    browser_smoke_passed=True,
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )
            distribution = self.make_distribution(root)
            (distribution / "link").symlink_to(distribution / "app.wasm")
            with self.assertRaisesRegex(release.VerificationError, "symlink"):
                release.create_packet(
                    distribution,
                    root / "symlink-packet",
                    repository=root,
                    browser_smoke_passed=True,
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_packet_output_cannot_be_inside_distribution(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            distribution = self.make_distribution(root)
            with self.assertRaisesRegex(release.VerificationError, "separate"):
                release.create_packet(
                    distribution,
                    distribution / "packet",
                    repository=root,
                    browser_smoke_passed=True,
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_browser_smoke_is_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(release.VerificationError, "browser smoke"):
                release.create_packet(
                    self.make_distribution(root),
                    root / "packet",
                    repository=root,
                    browser_smoke_passed=False,
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_github_sha_must_match_checked_out_revision(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            environment = {
                "GITHUB_ACTIONS": "true",
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_RUN_ID": "12",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_JOB": "web-tests",
                "GITHUB_SHA": "wrong",
            }
            with patch.dict(os.environ, environment, clear=False):
                with self.assertRaisesRegex(release.VerificationError, "GITHUB_SHA"):
                    release.create_packet(
                        self.make_distribution(root),
                        root / "packet",
                        repository=root,
                        browser_smoke_passed=True,
                        source_revision="revision",
                        working_tree="clean",
                    )

    def test_manifest_rejects_unsafe_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packet = self.create_packet(root)
            manifest_path = packet / "manifest.json"
            manifest = release.verify_packet(packet)
            manifest["files"][0]["path"] = "../outside"
            manifest_path.write_text(__import__("json").dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(release.VerificationError, "unsafe packet path"):
                release.verify_packet(packet)


if __name__ == "__main__":
    unittest.main()
