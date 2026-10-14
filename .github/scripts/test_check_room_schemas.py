#!/usr/bin/env python3
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-room-schemas.py")
SPEC = importlib.util.spec_from_file_location("check_room_schemas", SCRIPT)
CHECKER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CHECKER
SPEC.loader.exec_module(CHECKER)


class RoomSchemaCheckTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.schema_dir = self.root / CHECKER.SCHEMA_DIR
        self.schema_dir.mkdir(parents=True)
        for version in (1, 2):
            (self.schema_dir / f"{version}.json").write_text(f'{{"version": {version}}}\n')
        self.git("init", "-q")
        self.git("config", "user.name", "Schema Test")
        self.git("config", "user.email", "schema-test@example.invalid")
        self.git("add", ".")
        self.git("commit", "-qm", "initial schemas")
        self.base = self.git("rev-parse", "HEAD")

    def tearDown(self):
        self.temporary.cleanup()

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.root, check=True, capture_output=True, text=True
        ).stdout.strip()

    def test_unchanged_history_and_generated_files_pass(self):
        self.assertEqual(CHECKER.check_history(self.root, self.base), [])
        self.assertEqual(CHECKER.check_generated_files(self.root), [])

    def test_rewriting_shipped_schema_fails(self):
        (self.schema_dir / "2.json").write_text('{"version": 2, "changed": true}\n')
        self.git("add", ".")
        self.git("commit", "-qm", "rewrite old schema")

        violations = CHECKER.check_history(self.root, self.base)

        self.assertTrue(
            any("shipped Room schema files are immutable" in item for item in violations)
        )

    def test_adding_new_version_is_allowed_and_existing_versions_are_preserved(self):
        (self.schema_dir / "3.json").write_text('{"version": 3}\n')
        self.git("add", ".")
        self.git("commit", "-qm", "add schema version")

        self.assertEqual(CHECKER.check_history(self.root, self.base), [])
        self.assertEqual(CHECKER.check_generated_files(self.root), [])

    def test_added_schema_at_or_below_shipped_maximum_fails(self):
        self.git("rm", f"{CHECKER.SCHEMA_DIR}/1.json")
        self.git("commit", "-qm", "remove older fixture schema")
        self.base = self.git("rev-parse", "HEAD")
        (self.schema_dir / "1.json").write_text('{"version": 1}\n')
        self.git("add", ".")
        self.git("commit", "-qm", "add lower schema")

        violations = CHECKER.check_history(self.root, self.base)

        self.assertTrue(
            any("must be greater than the shipped maximum" in item for item in violations)
        )

    def test_generated_schema_drift_and_untracked_schema_fail(self):
        (self.schema_dir / "2.json").write_text('{"version": 2, "generated": true}\n')
        self.assertTrue(CHECKER.check_generated_files(self.root))
        (self.schema_dir / "2.json").write_text('{"version": 2}\n')
        (self.schema_dir / "3.json").write_text('{"version": 3}\n')

        self.assertTrue(any("3.json" in item for item in CHECKER.check_generated_files(self.root)))


if __name__ == "__main__":
    unittest.main()
