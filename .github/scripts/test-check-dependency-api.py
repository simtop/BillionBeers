#!/usr/bin/env python3
"""Tests for check-dependency-api.py."""

from __future__ import annotations

import importlib.util
import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("check-dependency-api.py")
SPEC = importlib.util.spec_from_file_location("check_dependency_api", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class DependencyApiTest(unittest.TestCase):
    def test_full_api_report_has_no_library_or_missing_class_exclusions(self) -> None:
        options = MODULE.japicmp_options()
        self.assertNotIn("--ignore-missing-classes", options)
        self.assertNotIn("--exclude", options)
        self.assertNotIn("--error-on-binary-incompatibility", options)

    def test_graph_reads_coordinates_and_rejects_duplicates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            graph = Path(directory) / "graph.txt"
            graph.write_text("g:a:1\ng:b:2\n")
            self.assertEqual({("g", "a"): "1", ("g", "b"): "2"}, MODULE.read_graph(graph))
            graph.write_text("g:a:1\ng:a:2\n")
            with self.assertRaisesRegex(ValueError, "duplicate module"):
                MODULE.read_graph(graph)

    def test_changed_modules_includes_additions_and_removals(self) -> None:
        old = {("g", "same"): "1", ("g", "gone"): "2", ("g", "bump"): "3"}
        new = {("g", "same"): "1", ("g", "added"): "4", ("g", "bump"): "5"}
        self.assertEqual([(("g", "added"), None, "4"), (("g", "bump"), "3", "5"),
                          (("g", "gone"), "2", None)], MODULE.changed_modules(old, new))

    def test_extracts_aar_classes_and_skips_resource_only_aar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            aar = root / "library.aar"
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as jar:
                jar.writestr("example/Api.class", b"bytecode")
            classes = buffer.getvalue()
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", classes)
            extracted = MODULE.extract_class_jar(aar, root / "out/classes.jar")
            with zipfile.ZipFile(extracted) as jar:
                self.assertEqual(b"bytecode", jar.read("example/Api.class"))

            resources = root / "resources.aar"
            with zipfile.ZipFile(resources, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
            self.assertIsNone(MODULE.extract_class_jar(resources, root / "out/resources.jar"))

    def test_rejects_an_empty_resolved_artifact_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "artifacts.tsv"
            manifest.write_text("")
            with self.assertRaisesRegex(ValueError, "no JVM artifacts"):
                MODULE.read_artifacts(manifest, root)

    def test_aar_includes_embedded_runtime_jars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as jar:
                jar.writestr("embedded/Consumer.class", b"embedded-bytecode")
            aar = root / "library.aar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("libs/embedded.jar", buffer.getvalue())
            output = MODULE.extract_class_jar(aar, root / "classes.jar")
            with zipfile.ZipFile(output) as jar:
                self.assertEqual(b"embedded-bytecode", jar.read("embedded/Consumer.class"))

    def test_consumer_manifest_requires_platform_and_compiled_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "consumers.tsv"
            manifest.write_text(f"project\t:app\t{root}\n")
            with self.assertRaisesRegex(ValueError, "platform"):
                MODULE.read_consumers(manifest)
            manifest.write_text(f"platform\tandroid\t{root}\n")
            with self.assertRaisesRegex(ValueError, "consumer"):
                MODULE.read_consumers(manifest)

    def test_consumer_linkage_result_controls_the_gate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = root / "old.txt"
            new = root / "new.txt"
            old.write_text("g:a:1\n")
            new.write_text("g:a:2\n")
            artifact_dir = root / "artifacts"
            artifact_dir.mkdir()
            old_jar = artifact_dir / "old.jar"
            new_jar = artifact_dir / "new.jar"
            old_jar.write_bytes(b"old")
            new_jar.write_bytes(b"new")
            (artifact_dir / "artifacts.tsv").write_text(
                f"old\tg:a:1\t{old_jar}\nnew\tg:a:2\t{new_jar}\n"
            )
            (artifact_dir / "consumers.tsv").write_text(
                f"project\t:app\t{artifact_dir}\nplatform\tandroid\t{artifact_dir}\n"
            )
            japicmp = root / "japicmp.jar"
            japicmp.write_bytes(b"pinned-test-fixture")
            args = MODULE.argparse.Namespace(
                old_graph=old,
                new_graph=new,
                artifacts=artifact_dir,
                japicmp=japicmp,
            )
            with (
                patch.object(
                    MODULE.subprocess,
                    "run",
                    side_effect=[MODULE.subprocess.CompletedProcess([], 0, "removed method", ""),
                             MODULE.subprocess.CompletedProcess([], 1)],
                ) as run,
            ):
                self.assertEqual(1, MODULE.check(args))
                self.assertTrue(any("CheckDependencyLinkage.java" in part for part in run.call_args.args[0]))
                report_command = run.call_args_list[0].args[0]
                self.assertNotIn("--error-on-binary-incompatibility", report_command)
                self.assertIn("protected", report_command)


if __name__ == "__main__":
    unittest.main()
