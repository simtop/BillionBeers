from __future__ import annotations

import unittest
from pathlib import Path

from check_dependabot_catalog_coverage import (
    CatalogCoverageError,
    check_coverage,
    find_group_matches,
    parse_catalog_entries,
    parse_gradle_groups,
)


ROOT = Path(__file__).resolve().parents[1]


class DependabotCatalogCoverageTest(unittest.TestCase):
    def test_repository_catalog_entries_each_match_one_group(self) -> None:
        catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        dependabot = (ROOT / ".github/dependabot.yml").read_text(encoding="utf-8")

        total, failures = check_coverage(catalog, dependabot)

        self.assertEqual(118, total)
        self.assertEqual([], failures)

    def test_catalog_resolves_library_notations_and_plugin_ids(self) -> None:
        catalog = """
[libraries]
plain = "org.example:plain:1.0"
module = { module = "org.example:module", version.ref = "example" }
group-name = { group = "org.example", name = "group-name", version = "1.0" }

[plugins]
plugin = { id = "org.example.plugin", version = "1.0" }
"""

        entries = parse_catalog_entries(catalog)

        self.assertEqual(
            [
                ("library", "plain", "org.example:plain"),
                ("library", "module", "org.example:module"),
                ("library", "group-name", "org.example:group-name"),
                ("plugin", "plugin", "org.example.plugin"),
            ],
            entries,
        )

    def test_uncovered_or_overlapping_coordinates_fail(self) -> None:
        catalog = '[libraries]\nitem = "org.example:item:1.0"\n'
        dependabot = """
updates:
  - package-ecosystem: "gradle"
    groups:
      first:
        patterns:
          - "org.example*"
      second:
        patterns:
          - "org.*"
"""

        total, failures = check_coverage(catalog, dependabot)

        self.assertEqual(1, total)
        self.assertIn("matches first, second", failures[0])

    def test_exclude_patterns_remove_a_broad_group_match(self) -> None:
        dependabot = """
updates:
  - package-ecosystem: "gradle"
    groups:
      kotlin:
        patterns:
          - "org.jetbrains.kotlin*"
        exclude-patterns:
          - "org.jetbrains.kotlinx*"
"""

        groups = parse_gradle_groups(dependabot)

        self.assertEqual([], find_group_matches("org.jetbrains.kotlinx:core", groups))
        self.assertEqual(["kotlin"], find_group_matches("org.jetbrains.kotlin:kotlin-gradle-plugin", groups))

    def test_unsupported_catalog_entries_fail_closed(self) -> None:
        with self.assertRaisesRegex(CatalogCoverageError, "module or group and name"):
            parse_catalog_entries("[libraries]\ninvalid = { version = \"1.0\" }\n")


if __name__ == "__main__":
    unittest.main()
