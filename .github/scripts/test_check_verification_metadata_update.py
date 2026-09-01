#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-verification-metadata-update.py")
SPEC = importlib.util.spec_from_file_location("verification_update", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def metadata(components: str, configuration: str = '<verify-metadata>true</verify-metadata>') -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
  <configuration>{configuration}</configuration>
  <components>{components}</components>
</verification-metadata>
"""


def component(version: str, artifact: str, checksum: str) -> str:
    return f"""
    <component group="example" name="library" version="{version}">
      <artifact name="{artifact}"><sha256 value="{checksum}"/></artifact>
    </component>
"""


class VerificationMetadataUpdateTest(unittest.TestCase):
    def verify(self, before: str, after: str) -> int:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before_path = root / "before.xml"
            after_path = root / "after.xml"
            before_path.write_text(before)
            after_path.write_text(after)
            return MODULE.verify(before_path, after_path)

    def test_allows_new_component_version(self) -> None:
        before = metadata(component("1.0", "library-1.0.jar", "old"))
        after = metadata(
            component("1.0", "library-1.0.jar", "old")
            + component("1.1", "library-1.1.jar", "new")
        )
        self.assertEqual(0, self.verify(before, after))

    def test_rejects_alternative_checksum_for_existing_artifact(self) -> None:
        before = metadata(component("1.0", "library-1.0.jar", "expected"))
        after = before.replace(
            '<sha256 value="expected"/>',
            '<sha256 value="expected"><also-trust value="unexpected"/></sha256>',
        )
        self.assertEqual(MODULE.CHECKSUM_MISMATCH, self.verify(before, after))

    def test_rejects_removed_existing_artifact(self) -> None:
        before = metadata(component("1.0", "library-1.0.jar", "expected"))
        after = metadata("")
        self.assertEqual(MODULE.CHECKSUM_MISMATCH, self.verify(before, after))

    def test_rejects_configuration_change(self) -> None:
        before = metadata(component("1.0", "library-1.0.jar", "expected"))
        after = metadata(
            component("1.0", "library-1.0.jar", "expected"),
            '<verify-metadata>false</verify-metadata>',
        )
        self.assertEqual(MODULE.CONFIGURATION_CHANGED, self.verify(before, after))

    def test_equivalence_accepts_identical_graphs(self) -> None:
        ledger = metadata(component("1.0", "library-1.0.jar", "expected"))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ledger.xml"
            path.write_text(ledger)
            self.assertEqual(0, MODULE.verify(path, path, require_equivalent=True))

    def test_equivalence_rejects_new_artifact(self) -> None:
        before = metadata(component("1.0", "library-1.0.jar", "expected"))
        after = metadata(
            component("1.0", "library-1.0.jar", "expected")
            + component("1.1", "library-1.1.jar", "new")
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before_path = root / "reference.xml"
            after_path = root / "candidate.xml"
            before_path.write_text(before)
            after_path.write_text(after)
            self.assertEqual(
                MODULE.NOT_EQUIVALENT,
                MODULE.verify(before_path, after_path, require_equivalent=True),
            )


if __name__ == "__main__":
    unittest.main()
