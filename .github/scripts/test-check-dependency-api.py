#!/usr/bin/env python3
"""Tests for check-dependency-api.py."""

from __future__ import annotations

import importlib.util
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
    def test_graph_reads_coordinates_and_rejects_duplicates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            graph = Path(directory) / "graph.txt"
            graph.write_text("g:a:1\ng:b:2\n")
            self.assertEqual({("g", "a"): "1", ("g", "b"): "2"}, MODULE.read_graph(graph))
            graph.write_text("g:a:1\ng:a:2\n")
            with self.assertRaisesRegex(ValueError, "duplicate module"):
                MODULE.read_graph(graph)

    def test_changed_modules_ignores_additions_and_removals(self) -> None:
        old = {("g", "same"): "1", ("g", "gone"): "2", ("g", "bump"): "3"}
        new = {("g", "same"): "1", ("g", "added"): "4", ("g", "bump"): "5"}
        self.assertEqual([(("g", "bump"), "3", "5")], MODULE.changed_modules(old, new))

    def test_extracts_aar_classes_and_skips_resource_only_aar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            aar = root / "library.aar"
            classes = b"PK\x03\x04synthetic-jar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", classes)
            extracted = MODULE.extract_class_jar(aar, root / "out/classes.jar")
            self.assertEqual(classes, extracted.read_bytes())

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

    def test_no_bumps_skips_tool_invocation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = root / "old.txt"
            new = root / "new.txt"
            old.write_text("g:a:1\n")
            new.write_text("g:a:1\n")
            args = MODULE.argparse.Namespace(
                old_graph=old,
                new_graph=new,
                artifacts=root / "artifacts",
                japicmp=root / "japicmp.jar",
            )
            with patch.object(MODULE.subprocess, "run") as run:
                self.assertEqual(0, MODULE.check(args))
                run.assert_not_called()

    def test_binary_incompatibility_fails_the_check(self) -> None:
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
                    return_value=MODULE.subprocess.CompletedProcess([], 1, "removed method", ""),
                ) as run,
            ):
                self.assertEqual(1, MODULE.check(args))
                self.assertIn("--error-on-binary-incompatibility", run.call_args.args[0])
                self.assertIn("protected", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
