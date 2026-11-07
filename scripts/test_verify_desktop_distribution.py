#!/usr/bin/env python3
"""Deterministic tests for the Desktop distribution verifier."""

from __future__ import annotations

import plistlib
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from smoke_desktop_distribution import SmokeError
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

    def fake_hdiutil(self, image_app: Path):
        def run(command, **_kwargs):
            if command[1] == "attach":
                self.assertEqual(["-readonly", "-nobrowse", "-noautoopen", "-mountpoint"], command[2:6])
                shutil.copytree(image_app, Path(command[6]) / image_app.name, symlinks=True)
            if command[1] == "detach":
                shutil.rmtree(Path(command[2]) / image_app.name)
            return subprocess.CompletedProcess(command, 0)

        return run

    def test_valid_bundle_and_dmg(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            dmg = root / "BillionBeers-1.0.0.dmg"
            dmg.write_bytes(b"dmg")
            with patch("verify_desktop_distribution.subprocess.run", side_effect=self.fake_hdiutil(app)) as hdiutil:
                checked = verify_distribution(app, dmg, require_arm64=False)
            self.assertIn(f"{dmg.name}/{app.name}/Contents/MacOS/BillionBeers", checked)
            self.assertEqual(["verify", "attach", "detach"], [call.args[0][1] for call in hdiutil.call_args_list])
            self.assertFalse(Path(hdiutil.call_args.args[0][2]).exists())

    def test_smoke_launches_the_mounted_app_and_detaches_after_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            dmg = root / "test.dmg"
            dmg.write_bytes(b"dmg")

            def smoke(mounted_app):
                self.assertNotEqual(app, mounted_app)
                self.assertTrue((mounted_app / "Contents/MacOS/BillionBeers").is_file())
                raise SmokeError("mounted app failed")

            with (
                patch("verify_desktop_distribution.subprocess.run", side_effect=self.fake_hdiutil(app)) as hdiutil,
                patch("verify_desktop_distribution.smoke_distribution", side_effect=smoke) as run_smoke,
            ):
                with self.assertRaisesRegex(SmokeError, "mounted app failed"):
                    verify_distribution(app, dmg, require_arm64=False, smoke=True)
            run_smoke.assert_called_once()
            self.assertEqual("detach", hdiutil.call_args.args[0][1])
            self.assertFalse(run_smoke.call_args.args[0].exists())

    def test_corrupt_dmg_is_rejected_before_mount(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            dmg = root / "corrupt.dmg"
            dmg.write_bytes(b"not a disk image")
            failure = subprocess.CalledProcessError(1, ["hdiutil", "verify"], stderr="invalid image")
            with patch("verify_desktop_distribution.subprocess.run", side_effect=failure) as hdiutil:
                with self.assertRaisesRegex(VerificationError, "DMG verify failed: invalid image"):
                    verify_distribution(app, dmg, require_arm64=False)
            hdiutil.assert_called_once_with(
                ["hdiutil", "verify", str(dmg.resolve())], check=True, capture_output=True, text=True,
            )

    def test_wrong_app_in_dmg_is_rejected_and_volume_is_detached(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = self.make_app(root)
            image_app = self.make_app(root / "image")
            info_path = image_app / "Contents/Info.plist"
            info = plistlib.loads(info_path.read_bytes())
            info["CFBundleIdentifier"] = "wrong.bundle"
            info_path.write_bytes(plistlib.dumps(info))
            dmg = root / "wrong.dmg"
            dmg.write_bytes(b"dmg")
            with patch("verify_desktop_distribution.subprocess.run", side_effect=self.fake_hdiutil(image_app)) as hdiutil:
                with self.assertRaisesRegex(VerificationError, "bundle identifier"):
                    verify_distribution(app, dmg, require_arm64=False)
            self.assertEqual("detach", hdiutil.call_args.args[0][1])
            self.assertFalse(Path(hdiutil.call_args.args[0][2]).exists())

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
