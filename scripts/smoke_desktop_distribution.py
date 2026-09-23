#!/usr/bin/env python3
"""Run the packaged Desktop executable through its deterministic CLI path."""

from __future__ import annotations

import argparse
import plistlib
import subprocess
import sys
import tempfile
from pathlib import Path


class SmokeError(Exception):
    pass


def _executable_for_app(app: Path) -> Path:
    app = app.resolve()
    if not app.is_dir() or app.suffix != ".app":
        raise SmokeError(f"application bundle does not exist: {app}")

    info_path = app / "Contents" / "Info.plist"
    if not info_path.is_file():
        raise SmokeError(f"Info.plist does not exist: {info_path}")
    try:
        info = plistlib.loads(info_path.read_bytes())
    except (OSError, plistlib.InvalidFileException) as error:
        raise SmokeError(f"could not read Info.plist: {error}") from error

    executable_name = info.get("CFBundleExecutable")
    if not isinstance(executable_name, str) or not executable_name:
        raise SmokeError("Info.plist has no executable name")

    executable = app / "Contents" / "MacOS" / executable_name
    if not executable.is_file():
        raise SmokeError(f"packaged executable does not exist: {executable}")
    if not executable.stat().st_mode & 0o111:
        raise SmokeError(f"packaged executable is not executable: {executable}")
    return executable


def smoke_distribution(app: Path, timeout_seconds: float = 60.0) -> tuple[Path, str]:
    executable = _executable_for_app(app)
    with tempfile.TemporaryDirectory(prefix="billionbeers-desktop-smoke-") as directory:
        data_dir = Path(directory)
        database_path = data_dir / "beers_database.db"
        command = [
            str(executable),
            "--cli",
            "--data-dir",
            str(data_dir),
        ]
        try:
            result = subprocess.run(
                command,
                cwd=data_dir,
                capture_output=True,
                text=True,
                timeout=timeout_seconds,
                check=False,
            )
        except subprocess.TimeoutExpired as error:
            raise SmokeError(f"packaged executable timed out after {timeout_seconds:g}s") from error
        except OSError as error:
            raise SmokeError(f"could not launch packaged executable: {error}") from error

        output = result.stdout
        if result.returncode != 0:
            raise SmokeError(
                f"packaged executable exited with {result.returncode}\n"
                f"stdout:\n{result.stdout}\n"
                f"stderr:\n{result.stderr}"
            )
        expected_lines = (
            f"Reading the local catalog at {database_path}",
            "Loaded 0 beers from local data",
        )
        missing_lines = [line for line in expected_lines if line not in output]
        if missing_lines:
            raise SmokeError(
                "packaged executable output did not contain expected lines: "
                f"{missing_lines}\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )
        if not database_path.is_file() or database_path.stat().st_size == 0:
            raise SmokeError(f"packaged executable did not create a database: {database_path}")
        return database_path, output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("application", type=Path)
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()
    try:
        database_path, output = smoke_distribution(args.application, args.timeout)
    except (OSError, SmokeError) as error:
        print(f"Desktop package smoke failed: {error}", file=sys.stderr)
        return 1
    print(f"Desktop package smoke passed: {args.application}")
    print(f"Initialized temporary database: {database_path}")
    print(output, end="" if output.endswith("\n") else "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
