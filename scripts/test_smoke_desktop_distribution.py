#!/usr/bin/env python3
"""Deterministic tests for the packaged Desktop CLI smoke helper."""

from __future__ import annotations

import plistlib
import tempfile
import unittest
from pathlib import Path

from smoke_desktop_distribution import SmokeError, smoke_distribution


class DesktopDistributionSmokeTest(unittest.TestCase):
    def make_app(self, root: Path, script: str) -> Path:
        app = root / "BillionBeers.app"
        contents = app / "Contents"
        executable = contents / "MacOS" / "BillionBeers"
        executable.parent.mkdir(parents=True)
        executable.write_text(script)
        executable.chmod(0o755)
        (contents / "Info.plist").write_bytes(
            plistlib.dumps(
                {
                    "CFBundleExecutable": "BillionBeers",
                    "CFBundleIdentifier": "com.simtop.billionbeers.desktop",
                    "CFBundlePackageType": "APPL",
                }
            )
        )
        return app

    def test_cli_smoke_checks_output_and_database_creation(self) -> None:
        script = """#!/bin/sh
set -eu
data_dir=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--data-dir" ]; then data_dir="$2"; shift 2; else shift; fi
done
mkdir -p "$data_dir"
database="$data_dir/beers_database.db"
printf 'sqlite\\n' > "$database"
printf 'Reading the local catalog at %s\\n' "$database"
printf 'Loaded 0 beers from local data\\n'
"""
        with tempfile.TemporaryDirectory() as directory:
            database_path, output = smoke_distribution(self.make_app(Path(directory), script))
            self.assertTrue(database_path.name == "beers_database.db")
            self.assertIn("Loaded 0 beers from local data", output)

    def test_nonzero_exit_is_rejected(self) -> None:
        script = "#!/bin/sh\nprintf 'failure\\n' >&2\nexit 3\n"
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(SmokeError, "exited with 3"):
                smoke_distribution(self.make_app(Path(directory), script))

    def test_missing_database_is_rejected(self) -> None:
        script = """#!/bin/sh
set -eu
data_dir=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--data-dir" ]; then data_dir="$2"; shift 2; else shift; fi
done
database="$data_dir/beers_database.db"
printf 'Reading the local catalog at %s\\n' "$database"
printf 'Loaded 0 beers from local data\\n'
"""
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(SmokeError, "did not create a database"):
                smoke_distribution(self.make_app(Path(directory), script))

    def test_non_executable_launcher_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            app = self.make_app(Path(directory), "#!/bin/sh\nexit 0\n")
            (app / "Contents/MacOS/BillionBeers").chmod(0o644)
            with self.assertRaisesRegex(SmokeError, "not executable"):
                smoke_distribution(app)


if __name__ == "__main__":
    unittest.main()
