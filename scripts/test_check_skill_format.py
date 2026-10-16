from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

from check_skill_format import check_repository, validate_skill_text


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "scripts/fixtures/skill-format"


class SkillFormatTest(unittest.TestCase):
    def test_repository_tracked_skills_pass(self) -> None:
        count, failures = check_repository(ROOT)

        self.assertGreater(count, 0)
        self.assertEqual([], failures)

    def test_evaluation_cases_match_the_documented_fixture_shape(self) -> None:
        cases_file = FIXTURES / "cases.json"
        suite = json.loads(cases_file.read_text(encoding="utf-8"))
        self.assertEqual({"cases"}, set(suite))
        seen_ids: set[str] = set()
        for case in suite["cases"]:
            self.assertEqual(
                {"id", "fixture", "expected_name", "expected_errors"},
                set(case),
            )
            self.assertRegex(case["id"], re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z"))
            self.assertNotIn(case["id"], seen_ids)
            seen_ids.add(case["id"])
            fixture_name = case["fixture"]
            self.assertEqual(Path(fixture_name).name, fixture_name)
            fixture = (FIXTURES / fixture_name).read_text(encoding="utf-8")
            self.assertEqual(
                case["expected_errors"],
                validate_skill_text(fixture, case["expected_name"]),
                case["id"],
            )

    def test_empty_skill_body_fails(self) -> None:
        text = "---\nname: sample-skill\ndescription: A sample.\n---\n  \n"

        self.assertEqual(
            ["skill body must not be empty"],
            validate_skill_text(text, "sample-skill"),
        )


if __name__ == "__main__":
    unittest.main()
