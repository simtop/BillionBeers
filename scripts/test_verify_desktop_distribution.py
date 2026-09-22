#!/usr/bin/env python3
"""Deterministic tests for the Desktop distribution verifier."""

from __future__ import annotations

import plistlib
import tempfile
import unittest
from pathlib import Path

from verify_desktop_distribution import VerificationError, verify_distribution


class DesktopDistributionVerifierTest(unittest.TestCase):
    def make_app(self, root: Path) -> Path:
        app = root / "BillionBeers.app"
        contents = app / "Contents"
        (contents / "MacOS").mkdir(parents=True)
        resources = contents / "Resources"
        resources.mkdir()
        (resources / "runtime.dat").write_bytes(b"resource")
        (contents / "MacOS" / "BillionBeers").write_bytes(b"executable")
        (contents / "Info.plist").write_bytes(
            plistlib.dumps(
                {
                    "CFBundleExecutable": "BillionBeers",
                    "CFBundleIdentifier": "com.simtop.billionbeers.desktop",
                    "CFBundlePackageType": "APPL",
                    "CFBundleShortVersionString": "1.0.0",
                }
            )
        )
        return app

    def test_valid_bundle_and_dmg(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            dmg = root / "BillionBeers-1.0.0.dmg"
            dmg.write_bytes(b"dmg")
            checked = verify_distribution(app, dmg, require_arm64=False)
            self.assertEqual(len(checked), 3)

    def test_missing_executable_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            app = self.make_app(Path(directory))
            (app / "Contents/MacOS/BillionBeers").unlink()
            with self.assertRaisesRegex(VerificationError, "executable is missing"):
                verify_distribution(app, require_arm64=False)

    def test_wrong_bundle_identifier_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            app = self.make_app(Path(directory))
            info_path = app / "Contents/Info.plist"
            info = plistlib.loads(info_path.read_bytes())
            info["CFBundleIdentifier"] = "wrong.bundle"
            info_path.write_bytes(plistlib.dumps(info))
            with self.assertRaisesRegex(VerificationError, "bundle identifier"):
                verify_distribution(app, require_arm64=False)

    def test_empty_resources_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            app = self.make_app(Path(directory))
            (app / "Contents/Resources/runtime.dat").unlink()
            with self.assertRaisesRegex(VerificationError, "Resources directory is empty"):
                verify_distribution(app, require_arm64=False)


if __name__ == "__main__":
    unittest.main()
