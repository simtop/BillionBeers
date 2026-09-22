#!/usr/bin/env python3
"""Verify the structure of an unsigned macOS Desktop distribution."""

from __future__ import annotations

import argparse
import plistlib
import subprocess
import sys
from pathlib import Path


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


def verify_distribution(app: Path, dmg: Path | None = None, require_arm64: bool = True) -> list[str]:
    app = app.resolve()
    if not app.is_dir() or app.suffix != ".app":
        raise VerificationError(f"application bundle does not exist: {app}")

    contents = app / "Contents"
    macos = contents / "MacOS"
    resources = contents / "Resources"
    info_path = contents / "Info.plist"
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
    if not isinstance(executable_name, str) or not executable_name:
        raise VerificationError("Info.plist has no executable name")

    executable = macos / executable_name
    if not executable.is_file() or executable.stat().st_size == 0:
        raise VerificationError(f"application executable is missing or empty: {executable}")
    if not any(resources.iterdir()):
        raise VerificationError(f"application Resources directory is empty: {resources}")
    if require_arm64:
        _check_architecture(executable)

    checked = [str(info_path.relative_to(app)), str(executable.relative_to(app))]
    if dmg is not None:
        dmg = dmg.resolve()
        if not dmg.is_file() or dmg.stat().st_size == 0 or dmg.suffix != ".dmg":
            raise VerificationError(f"DMG does not exist or is empty: {dmg}")
        checked.append(str(dmg))
    return checked


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("application", type=Path)
    parser.add_argument("--dmg", type=Path)
    parser.add_argument("--skip-architecture", action="store_true")
    args = parser.parse_args()
    try:
        checked = verify_distribution(args.application, args.dmg, not args.skip_architecture)
    except (OSError, VerificationError) as error:
        print(f"Desktop distribution verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Desktop distribution verification passed: {args.application}")
    print(f"Verified entries: {len(checked)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
