#!/usr/bin/env python3
import importlib.util
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

SCRIPT = Path(__file__).with_name("check-locale-parity.py")
SPEC = importlib.util.spec_from_file_location("check_locale_parity", SCRIPT)
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class LocaleParityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.resource_dir = self.root / "feature" / "src" / "main" / "res"
        self.paths = {}
        for locale in ("", "-fr", "-es"):
            path = self.resource_dir / f"values{locale}" / "strings.xml"
            path.parent.mkdir(parents=True)
            self.paths[locale or "default"] = path
        self.write(
            "default",
            '<resources><string name="title">%1$s has %2$d beers</string>'
            '<string name="internal" translatable="false">Internal</string>'
            '<plurals name="count"><item quantity="one">%1$d beer</item>'
            '<item quantity="other">%1$d beers</item></plurals></resources>',
        )
        self.write(
            "-fr",
            '<resources><string name="title">%2$d bières pour %1$s</string>'
            '<plurals name="count"><item quantity="one">%1$d bière</item>'
            '<item quantity="many">%1$d bières</item>'
            '<item quantity="other">%1$d bières</item></plurals></resources>',
        )
        self.write(
            "-es",
            '<resources><string name="title">%2$d cervezas para %1$s</string>'
            '<plurals name="count"><item quantity="one">%1$d cerveza</item>'
            '<item quantity="other">%1$d cervezas</item></plurals></resources>',
        )

    def tearDown(self):
        self.temporary.cleanup()

    def write(self, locale, content):
        self.paths[locale].write_text(content, encoding="utf-8")

    def check(self):
        return CHECKER.check_locale_files(
            [path for path in self.paths.values() if path.is_file()]
        )

    def test_default_only_android_and_compose_resources_require_both_locales(self):
        compose_default = (
            self.root
            / "web"
            / "src"
            / "commonMain"
            / "composeResources"
            / "values"
            / "strings.xml"
        )
        compose_default.parent.mkdir(parents=True)
        compose_default.write_text(
            '<resources><string name="compose_title">Catalog</string></resources>',
            encoding="utf-8",
        )

        errors = CHECKER.check_locale_files([self.paths["default"], compose_default])

        for resource_dir in (self.resource_dir, compose_default.parent.parent):
            for locale in ("fr", "es"):
                self.assertIn(
                    f"{resource_dir}: missing values-{locale}/strings.xml", errors
                )

    def test_deleting_both_locales_is_detected(self):
        self.paths["-fr"].unlink()
        self.paths["-es"].unlink()

        errors = self.check()

        for locale in ("fr", "es"):
            self.assertIn(
                f"{self.resource_dir}: missing values-{locale}/strings.xml", errors
            )

    def test_default_with_only_nontranslatable_entries_needs_no_locales(self):
        self.write(
            "default",
            '<resources><string name="internal" translatable="false">Internal</string></resources>',
        )

        self.assertEqual(CHECKER.check_locale_files([self.paths["default"]]), [])

    def test_matching_locales_allow_reordered_arguments_and_plural_categories(self):
        self.assertEqual(self.check(), [])

    def test_missing_and_extra_keys_fail(self):
        self.write("-es", '<resources><string name="extra">Extra</string></resources>')

        errors = self.check()

        self.assertTrue(any("missing <string name=\"title\">" in error for error in errors))
        self.assertTrue(any("unknown <string name=\"extra\">" in error for error in errors))

    def test_placeholder_type_or_position_changes_fail(self):
        self.write(
            "-es",
            '<resources><string name="title">%1$d cervezas para %2$s</string>'
            '<plurals name="count"><item quantity="one">%1$d cerveza</item>'
            '<item quantity="other">%1$d cervezas</item></plurals></resources>',
        )

        self.assertTrue(any("placeholder mismatch" in error for error in self.check()))

    def test_escaped_percent_is_not_an_argument(self):
        element = ElementTree.fromstring('<string name="format">Literal %%s: %1$d</string>')

        self.assertEqual(CHECKER.placeholder_patterns(element), {(('1', 'd'),)})

    def test_one_missing_locale_fails(self):
        errors = CHECKER.check_locale_files(
            [self.paths["default"], self.paths["-fr"]]
        )

        self.assertTrue(any("missing values-es/strings.xml" in error for error in errors))

    def test_orphan_locale_tables_without_default_fail(self):
        errors = CHECKER.check_locale_files(
            [self.paths["-fr"], self.paths["-es"]]
        )

        self.assertIn(f"{self.resource_dir}: missing values/strings.xml", errors)


if __name__ == "__main__":
    unittest.main()
