#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import os
import plistlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "verify_ios_simulator_artifacts", Path(__file__).with_name("verify_ios_simulator_artifacts.py")
)
assert SPEC.loader is not None
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class VerifyIosSimulatorArtifactsTest(unittest.TestCase):
    def make_app(self, root: Path) -> Path:
        app = root / "BillionBeers.app"
        (app / "Frameworks" / "BillionBeersData.framework").mkdir(parents=True)
        (app / "Resources").mkdir()
        (app / "BillionBeers").write_bytes(b"simulator executable")
        (app / "Frameworks" / "BillionBeersData.framework" / "BillionBeersData").write_bytes(
            b"framework"
        )
        (app / "Resources" / "fixture.dat").write_bytes(b"resource")
        (app / "Info.plist").write_bytes(
            plistlib.dumps(
                {
                    "CFBundleExecutable": "BillionBeers",
                    "CFBundleIdentifier": "com.simtop.billionbeers.ios",
                    "CFBundlePackageType": "APPL",
                }
            )
        )
        return app

    def make_reports(self, root: Path, *, skipped: int = 0, failures: int = 0) -> Path:
        reports = root / "reports"
        for module_name in module.REQUIRED_MODULES:
            report_dir = reports / module_name / module.REPORT_RELATIVE_DIR
            report_dir.mkdir(parents=True)
            (report_dir / "TEST-native.xml").write_text(
                '<testsuite tests="1" failures="%d" errors="0" skipped="%d" />'
                % (failures, skipped),
                encoding="utf-8",
            )
        return reports

    def create_packet(self, root: Path, *, app: Path | None = None, reports: Path | None = None) -> Path:
        app = app or self.make_app(root)
        reports = reports or self.make_reports(root)
        packet = root / "packet"
        module.create_packet(
            app,
            reports,
            packet,
            repository=root,
            destination="platform=iOS Simulator,name=Fixture,OS=18.2",
            source_revision="revision",
            working_tree="clean",
            provenance={"GITHUB_REPOSITORY": "owner/repo"},
        )
        return packet

    def test_create_and_verify_records_app_and_four_report_modules(self):
        with tempfile.TemporaryDirectory() as directory:
            packet = self.create_packet(Path(directory))
            manifest = module.verify_packet(packet)

            self.assertEqual("ios-simulator", manifest["target"])
            self.assertEqual(5, len(manifest["claims"]))
            self.assertEqual("revision", manifest["source_revision"])
            paths = [record["path"] for record in manifest["files"]]
            self.assertEqual(paths, sorted(paths))
            self.assertIn("simulator-app/BillionBeers.app/Info.plist", paths)
            for module_name in module.REQUIRED_MODULES:
                self.assertTrue(any(path.startswith(f"native-test-reports/{module_name}/") for path in paths))

    def test_packet_contains_only_required_report_trees(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = self.make_reports(root)
            unrelated = reports / "core-common" / "build" / "bin" / "unrelated.kexe"
            unrelated.parent.mkdir(parents=True)
            unrelated.write_bytes(b"not a report")
            packet = self.create_packet(root, reports=reports)
            self.assertFalse(
                (packet / "native-test-reports/core-common/build/bin/unrelated.kexe").exists()
            )
            self.assertTrue(
                (
                    packet
                    / "native-test-reports/core-common/"
                    / module.REPORT_RELATIVE_DIR
                    / "TEST-native.xml"
                ).is_file()
            )

    def test_packet_output_can_live_under_repository_report_root(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            reports = self.make_reports(root)
            packet = root / "iosApp" / "build" / "ios-simulator-confidence"
            module.create_packet(
                app,
                reports,
                packet,
                repository=root,
                destination="fixture",
                source_revision="revision",
                working_tree="clean",
                provenance={},
            )
            self.assertEqual("ios-simulator", module.verify_packet(packet)["target"])

    def test_tree_digest_is_stable_for_same_content(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            first_packet = self.create_packet(Path(first))
            second_packet = self.create_packet(Path(second))
            first_manifest = module.verify_packet(first_packet)
            second_manifest = module.verify_packet(second_packet)
            self.assertEqual(first_manifest["tree_sha256"], second_manifest["tree_sha256"])
            self.assertEqual(first_manifest["files"], second_manifest["files"])

    def test_changed_missing_and_extra_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(root)
            (packet / "simulator-app/BillionBeers.app/Resources/fixture.dat").write_bytes(b"changed")
            with self.assertRaisesRegex(module.VerificationError, "match the manifest"):
                module.verify_packet(packet)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(root)
            (packet / "native-test-reports/core-common/build/test-results/iosSimulatorArm64Test/TEST-native.xml").unlink()
            with self.assertRaises(module.VerificationError):
                module.verify_packet(packet)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(root)
            (packet / "simulator-app/BillionBeers.app/Resources/extra.dat").write_bytes(b"extra")
            with self.assertRaisesRegex(module.VerificationError, "match the manifest"):
                module.verify_packet(packet)

    def test_symlink_and_unsafe_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            (app / "Resources/link").symlink_to(app / "Resources/fixture.dat")
            with self.assertRaisesRegex(module.VerificationError, "symlink"):
                module.create_packet(
                    app,
                    self.make_reports(root),
                    root / "packet",
                    repository=root,
                    destination="fixture",
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(root)
            manifest_path = packet / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"][0]["path"] = "../outside"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(module.VerificationError, "unsafe packet path"):
                module.verify_packet(packet)

    def test_invalid_bundle_metadata_and_missing_framework_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            info_path = app / "Info.plist"
            info = plistlib.loads(info_path.read_bytes())
            info["CFBundleIdentifier"] = "wrong.bundle"
            info_path.write_bytes(plistlib.dumps(info))
            with self.assertRaisesRegex(module.VerificationError, "bundle identifier"):
                module.create_packet(
                    app,
                    self.make_reports(root),
                    root / "packet",
                    repository=root,
                    destination="fixture",
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            (app / "Frameworks/BillionBeersData.framework/BillionBeersData").unlink()
            with self.assertRaisesRegex(module.VerificationError, "framework"):
                module.create_packet(
                    app,
                    self.make_reports(root),
                    root / "packet",
                    repository=root,
                    destination="fixture",
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_empty_destination_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(module.VerificationError, "destination"):
                module.create_packet(
                    self.make_app(root),
                    self.make_reports(root),
                    root / "packet",
                    repository=root,
                    destination="",
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_invalid_reports_are_rejected(self):
        for kwargs, expected in (
            ({"skipped": 1}, "skipped"),
            ({"failures": 1}, "failures"),
        ):
            with self.subTest(kwargs=kwargs), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with self.assertRaisesRegex(module.VerificationError, expected):
                    module.create_packet(
                        self.make_app(root),
                        self.make_reports(root, **kwargs),
                        root / "packet",
                        repository=root,
                        destination="fixture",
                        source_revision="revision",
                        working_tree="clean",
                        provenance={},
                    )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = self.make_reports(root)
            report = reports / "core-common" / module.REPORT_RELATIVE_DIR / "TEST-native.xml"
            report.write_text("not xml", encoding="utf-8")
            with self.assertRaisesRegex(module.VerificationError, "malformed XML"):
                module.create_packet(
                    self.make_app(root),
                    reports,
                    root / "packet",
                    repository=root,
                    destination="fixture",
                    source_revision="revision",
                    working_tree="clean",
                    provenance={},
                )

    def test_github_sha_must_match_source_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            environment = {
                "GITHUB_ACTIONS": "true",
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_RUN_ID": "1",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_JOB": "native-tests",
                "GITHUB_SHA": "wrong",
            }
            with patch.dict(os.environ, environment, clear=False):
                with self.assertRaisesRegex(module.VerificationError, "GITHUB_SHA"):
                    module.create_packet(
                        self.make_app(root),
                        self.make_reports(root),
                        root / "packet",
                        repository=root,
                        destination="fixture",
                        source_revision="revision",
                        working_tree="clean",
                    )

    def test_packet_rejects_tampered_github_sha(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(
                root,
                reports=self.make_reports(root),
            )
            manifest_path = packet / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["provenance"] = {"GITHUB_SHA": "different-revision"}
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(module.VerificationError, "GITHUB_SHA"):
                module.verify_packet(packet)

    def test_packet_rejects_unexpected_top_level_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            packet = self.create_packet(root)
            (packet / "notes.txt").write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(module.VerificationError, "unexpected file"):
                module.verify_packet(packet)


if __name__ == "__main__":
    unittest.main()
