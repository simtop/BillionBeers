#!/usr/bin/env python3
import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check_markdown_links.py")
SPEC = importlib.util.spec_from_file_location("check_markdown_links", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class MarkdownCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        (self.root / "README.md").write_text("# README\n", encoding="utf-8")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_relative_file_and_fragment_links(self):
        source = self.root / "docs" / "guide.md"
        source.parent.mkdir()
        target = source.parent / "target.md"
        target.write_text("# Installation\n", encoding="utf-8")
        source.write_text("[target](target.md#installation)\n", encoding="utf-8")
        self.assertEqual([], MODULE.check_links(self.root, [source, target]))

    def test_missing_target_and_fragment_are_reported(self):
        source = self.root / "guide.md"
        source.write_text("[missing](missing.md#nowhere)\n", encoding="utf-8")
        errors = MODULE.check_links(self.root, [source])
        self.assertEqual(1, len(errors))
        self.assertIn("missing link target", errors[0])

        target = self.root / "target.md"
        target.write_text("# Present\n", encoding="utf-8")
        source.write_text("[target](target.md#absent)\n", encoding="utf-8")
        errors = MODULE.check_links(self.root, [source, target])
        self.assertIn("missing link fragment", errors[0])

    def test_external_links_are_ignored(self):
        source = self.root / "guide.md"
        source.write_text(
            "[web](https://example.com/missing) ![image](https://example.com/image.png)\n",
            encoding="utf-8",
        )
        self.assertEqual([], MODULE.check_links(self.root, [source]))

    def test_generated_markers_must_be_unique(self):
        self.assertEqual(1, len(MODULE.check_generated_indexes(self.root)))

    def test_generated_block_matches_expected_values(self):
        (self.root / "gradle").mkdir()
        (self.root / "gradle/wrapper").mkdir()
        (self.root / "gradle/libs.versions.toml").write_text(
            'org-jetbrains-kotlin = "2.2.20"\n'
            'composeBom = "2026.01.00"\n'
            'dev-zacsweers-metro = "0.11.0"\n'
            'androidx-room = "2.8.0"\n',
            encoding="utf-8",
        )
        (self.root / "gradle/wrapper/gradle-wrapper.properties").write_text(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.1-bin.zip\n",
            encoding="utf-8",
        )
        block = MODULE.expected_version_block(self.root)
        (self.root / "README.md").write_text(
            f"# README\n{MODULE.START_MARKER}\n{block}\n{MODULE.END_MARKER}\n",
            encoding="utf-8",
        )
        self.assertEqual([], MODULE.check_generated_indexes(self.root))
        (self.root / "README.md").write_text(
            f"# README\n{MODULE.START_MARKER}\n{block.replace('2.2.20', 'old')}\n{MODULE.END_MARKER}\n",
            encoding="utf-8",
        )
        self.assertIn("generated version block is stale", MODULE.check_generated_indexes(self.root)[0])


if __name__ == "__main__":
    unittest.main()
