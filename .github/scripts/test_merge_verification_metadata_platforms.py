#!/usr/bin/env python3
"""Regression tests for immutable platform evidence and ledger union safety."""

from __future__ import annotations

import importlib.util
import hashlib
import json
import difflib
import contextlib
import io
import tempfile
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "merge_platforms", Path(__file__).with_name("merge-verification-metadata-platforms.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
HEAD = "a" * 40
RUN_ID = "1234"
ATTEMPT = "1"
REF = "dependabot/gradle/example"
POLICY = """<verification-metadata><configuration><verify-metadata>true</verify-metadata></configuration><components>{}</components></verification-metadata>"""


class PlatformMetadataMergeTest(unittest.TestCase):
    def setUp(self):
        capture = contextlib.ExitStack()
        capture.enter_context(contextlib.redirect_stdout(io.StringIO()))
        capture.enter_context(contextlib.redirect_stderr(io.StringIO()))
        self.addCleanup(capture.close)

    def prepare(self, root: Path, *, mutate=None):
        incoming = root / "incoming"
        incoming.mkdir(exist_ok=True)
        for lane in sorted(MODULE.EXPECTED_LANES):
            folder = incoming / lane
            folder.mkdir(exist_ok=True)
            manifest = {"lane": lane, "head_sha": HEAD, "run_id": RUN_ID, "run_attempt": ATTEMPT,
                        "source_ref": REF, "conclusion": "success", "mode": "write", "skipped": False}
            (folder / "prepared.patch").write_bytes(b"prepared inputs")
            manifest["prepared_sha256"] = hashlib.sha256((folder / "prepared.patch").read_bytes()).hexdigest()
            (folder / "formatting-files").write_bytes(b"src/Foo.kt\0")
            manifest["formatting_files_sha256"] = hashlib.sha256((folder / "formatting-files").read_bytes()).hexdigest()
            ledger_artifact = lane
            if mutate:
                manifest, ledger_artifact = mutate(lane, manifest, ledger_artifact)
            (folder / "manifest.json").write_text(json.dumps(manifest))
            (folder / "verification-metadata.xml").write_text(
                POLICY.format(f'<component group="test" name="{ledger_artifact}" version="1">'
                              f'<artifact name="{ledger_artifact}.jar"><sha256 value="hash-{ledger_artifact}"/>'
                              '</artifact></component>')
            )
        before = root / "before.xml"
        before.write_text(POLICY.format(""))
        return incoming, before

    def test_unions_disjoint_platform_artifacts_with_matching_immutable_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, before = self.prepare(root)
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            output = root / "merged.xml"
            MODULE.merge(before, ledgers, output)
            checksums = MODULE.CHECKER.artifact_checksums(MODULE.CHECKER.load(output))
            self.assertEqual(4, len(checksums))
            rendered = output.read_text().splitlines()
            self.assertEqual('<?xml version="1.0" encoding="UTF-8"?>', rendered[0])
            self.assertEqual("   <configuration>", rendered[2])
            self.assertNotIn(" />", output.read_text())

    def test_coalesces_shared_artifact_when_accepted_checksums_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def duplicate(lane, manifest, artifact):
                return manifest, "shared" if lane in {"linux", "web"} else artifact
            incoming, before = self.prepare(root, mutate=duplicate)
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            output = root / "merged.xml"
            MODULE.merge(before, ledgers, output)
            self.assertEqual(3, len(MODULE.CHECKER.artifact_checksums(MODULE.CHECKER.load(output))))

    def test_real_repository_ledger_is_byte_identical_when_union_adds_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, before = self.prepare(root)
            original = Path(__file__).resolve().parents[2] / "gradle/verification-metadata.xml"
            before.write_bytes(original.read_bytes())
            for lane in MODULE.EXPECTED_LANES:
                (incoming / lane / "verification-metadata.xml").write_bytes(original.read_bytes())
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            output = root / "merged.xml"
            MODULE.merge(before, ledgers, output)
            self.assertEqual(original.read_bytes(), output.read_bytes())

    def test_additive_union_keeps_gradle_self_closing_style(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, before = self.prepare(root)
            original = Path(__file__).resolve().parents[2] / "gradle/verification-metadata.xml"
            baseline = original.read_bytes()
            before.write_bytes(baseline)
            addition = (b'      <component group="ztest" name="fresh" version="1">\n'
                        b'         <artifact name="fresh.jar">\n'
                        b'            <sha256 value="fresh-hash"/>\n'
                        b'         </artifact>\n'
                        b'      </component>\n')
            for lane in MODULE.EXPECTED_LANES:
                path = incoming / lane / "verification-metadata.xml"
                path.write_bytes(baseline.replace(b"</components>", addition + b"</components>"))
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            output = root / "merged.xml"
            MODULE.merge(before, ledgers, output)
            rendered = output.read_text()
            self.assertIn('<sha256 value="fresh-hash"/>', rendered)
            self.assertNotIn(" />", rendered)
            delta = list(difflib.unified_diff(
                baseline.decode().splitlines(), rendered.splitlines(), lineterm=""
            ))
            self.assertLess(len(delta), 20)

    def test_rejects_missing_lane_stale_head_and_wrong_source(self):
        for kind in ("missing", "head", "source"):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                incoming, _ = self.prepare(root)
                if kind == "missing":
                    (incoming / "apple").rename(root / "apple")
                else:
                    path = incoming / "linux" / "manifest.json"
                    manifest = json.loads(path.read_text())
                    manifest["head_sha" if kind == "head" else "source_ref"] = "b" * 40 if kind == "head" else "other"
                    path.write_text(json.dumps(manifest))
                with self.subTest(kind=kind), self.assertRaises(ValueError):
                    MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")

    def test_rejects_shared_artifact_checksum_conflict_and_baseline_checksum_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def duplicate(lane, manifest, artifact):
                return manifest, "shared" if lane in {"linux", "web"} else artifact
            incoming, before = self.prepare(root, mutate=duplicate)
            web_ledger = incoming / "web" / "verification-metadata.xml"
            web_ledger.write_text(web_ledger.read_text().replace("hash-shared", "changed-hash"))
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            with self.assertRaisesRegex(ValueError, "conflicting shared artifact"):
                MODULE.merge(before, ledgers, root / "merged.xml")

            before.write_text(POLICY.format(
                '<component group="test" name="linux" version="1"><artifact name="linux.jar">'
                '<sha256 value="old-hash"/></artifact></component>'
            ))
            incoming, before = self.prepare(root, mutate=duplicate)
            before.write_text(POLICY.format(
                '<component group="test" name="linux" version="1"><artifact name="linux.jar">'
                '<sha256 value="old-hash"/></artifact></component>'
            ))
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            with self.assertRaisesRegex(ValueError, "changes a recorded checksum"):
                MODULE.merge(before, ledgers, root / "merged.xml")

    def test_rejects_changed_policy_and_lane_format_or_baseline_disagreement(self):
        for identity_key in ("formatting_sha256", "baseline_sha256"):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                incoming, before = self.prepare(root)
                path = incoming / "web" / "prepared.patch"
                path.write_bytes(b"different prepared inputs")
                manifest_path = incoming / "web" / "manifest.json"
                manifest = json.loads(manifest_path.read_text())
                manifest["prepared_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                manifest_path.write_text(json.dumps(manifest))
                with self.subTest(identity_key=identity_key), self.assertRaisesRegex(ValueError, "prepared"):
                    MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, _ = self.prepare(root)
            manifest_path = incoming / "apple" / "manifest.json"
            manifest = json.loads(manifest_path.read_text())
            manifest["conclusion"] = "failure"
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "identity"):
                MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, before = self.prepare(root)
            path = incoming / "apple" / "verification-metadata.xml"
            path.write_text(path.read_text().replace("verify-metadata>true", "verify-metadata>false"))
            ledgers = MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")
            with self.assertRaisesRegex(ValueError, "verification policy"):
                MODULE.merge(before, ledgers, root / "merged.xml")

    def test_rejects_unanimous_wrong_mode_and_malformed_manifest_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            incoming, _ = self.prepare(root)
            with self.assertRaisesRegex(ValueError, "identity"):
                MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "candidate")

        for mutation in ("missing", "wrong_shape", "wrong_skipped_type"):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                incoming, _ = self.prepare(root)
                manifest_path = incoming / "linux" / "manifest.json"
                if mutation == "wrong_shape":
                    manifest_path.write_text("[]")
                else:
                    manifest = json.loads(manifest_path.read_text())
                    if mutation == "missing":
                        del manifest["mode"]
                    else:
                        manifest["skipped"] = "false"
                    manifest_path.write_text(json.dumps(manifest))
                with self.subTest(mutation=mutation), self.assertRaisesRegex(ValueError, "manifest"):
                    MODULE.validate_evidence(incoming, HEAD, RUN_ID, ATTEMPT, REF, "write")


if __name__ == "__main__":
    unittest.main()
