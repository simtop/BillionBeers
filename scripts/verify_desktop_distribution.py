#!/usr/bin/env python3
"""Verify the structure of an unsigned macOS Desktop distribution."""

from __future__ import annotations

import argparse
import plistlib
import subprocess
import sys
import tempfile
from pathlib import Path

from smoke_desktop_distribution import SmokeError, smoke_distribution


class VerificationError(Exception):
    pass


def _check_architecture(executable: Path) -> None:
    try:
        result = subprocess.run(
            ["file", str(executable)],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise VerificationError(f"could not inspect executable architecture: {error}") from error
    description = result.stdout.lower()
    if "mach-o" not in description or "arm64" not in description:
        raise VerificationError(f"executable is not a macOS arm64 binary: {result.stdout.strip()}")


def _verify_app(app: Path, require_arm64: bool) -> list[str]:
    if not app.is_dir() or app.suffix != ".app":
        raise VerificationError(f"application bundle does not exist: {app}")

    contents = app / "Contents"
    macos = contents / "MacOS"
    resources = contents / "Resources"
    info_path = contents / "Info.plist"
    for path in (info_path, macos, resources):
        if not path.resolve().is_relative_to(app):
            raise VerificationError(f"application entry points outside the bundle: {path}")
    if not info_path.is_file():
        raise VerificationError(f"Info.plist does not exist: {info_path}")
    if not macos.is_dir():
        raise VerificationError(f"application executable directory does not exist: {macos}")
    if not resources.is_dir():
        raise VerificationError(f"application resources directory does not exist: {resources}")

    try:
        info = plistlib.loads(info_path.read_bytes())
    except (OSError, plistlib.InvalidFileException) as error:
        raise VerificationError(f"could not read Info.plist: {error}") from error

    if info.get("CFBundlePackageType") != "APPL":
        raise VerificationError("Info.plist is not an application bundle")
    if info.get("CFBundleIdentifier") != "com.simtop.billionbeers.desktop":
        raise VerificationError("Info.plist has an unexpected bundle identifier")
    executable_name = info.get("CFBundleExecutable")
    if (
        not isinstance(executable_name, str)
        or not executable_name
        or Path(executable_name).name != executable_name
    ):
        raise VerificationError("Info.plist has an invalid executable name")

    executable = macos / executable_name
    if not executable.resolve().is_relative_to(app):
        raise VerificationError(f"application executable points outside the bundle: {executable}")
    if not executable.is_file() or executable.stat().st_size == 0:
        raise VerificationError(f"application executable is missing or empty: {executable}")
    if not any(resources.iterdir()):
        raise VerificationError(f"application Resources directory is empty: {resources}")
    if require_arm64:
        _check_architecture(executable)

    return [str(info_path.relative_to(app)), str(executable.relative_to(app))]


def _hdiutil(operation: str, *args: str) -> None:
    try:
        subprocess.run(
            ["hdiutil", operation, *args], check=True, capture_output=True, text=True,
        )
    except subprocess.CalledProcessError as error:
        raise VerificationError(f"DMG {operation} failed: {error.stderr.strip() or error}") from error
    except OSError as error:
        raise VerificationError(f"could not run hdiutil {operation}: {error}") from error


def _verify_dmg(dmg: Path, app_name: str, require_arm64: bool, smoke: bool) -> list[str]:
    if not dmg.is_file() or dmg.stat().st_size == 0 or dmg.suffix != ".dmg":
        raise VerificationError(f"DMG does not exist or is empty: {dmg}")
    _hdiutil("verify", str(dmg))
    mount = Path(tempfile.mkdtemp(prefix="billionbeers-dmg-")).resolve()
    try:
        _hdiutil("attach", "-readonly", "-nobrowse", "-noautoopen", "-mountpoint", str(mount), str(dmg))
        try:
            mounted_app = mount / app_name
            if mounted_app.is_symlink():
                raise VerificationError(f"DMG app bundle is a symlink: {mounted_app}")
            checked = _verify_app(mounted_app, require_arm64)
            if smoke:
                smoke_distribution(mounted_app)
            return [str(dmg), *(f"{dmg.name}/{app_name}/{entry}" for entry in checked)]
        finally:
            _hdiutil("detach", str(mount))
    finally:
        # Do not recursively remove a volume if the detach itself failed.
        if mount.exists() and not mount.is_mount():
            mount.rmdir()


def verify_distribution(
    app: Path, dmg: Path | None = None, require_arm64: bool = True, *, smoke: bool = False,
) -> list[str]:
    if smoke and dmg is None:
        raise VerificationError("Desktop smoke requires a DMG")
    app = app.resolve()
    checked = _verify_app(app, require_arm64)
    if dmg is not None:
        checked.extend(_verify_dmg(dmg.resolve(), app.name, require_arm64, smoke))
    return checked


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("application", type=Path)
    parser.add_argument("--dmg", type=Path)
    parser.add_argument("--skip-architecture", action="store_true")
    parser.add_argument("--smoke", action="store_true", help="Launch the app from the mounted DMG.")
    args = parser.parse_args()
    try:
        checked = verify_distribution(args.application, args.dmg, not args.skip_architecture, smoke=args.smoke)
    except (OSError, VerificationError, SmokeError) as error:
        print(f"Desktop distribution verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Desktop distribution verification passed: {args.application}")
    print(f"Verified entries: {len(checked)}")
    if args.smoke:
        print(f"Desktop package smoke passed from mounted image: {args.dmg}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
