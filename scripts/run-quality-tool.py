#!/usr/bin/env python3
"""Install and run a pinned CLI from a public GitHub release."""

import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
RELEASES = {
    "kotlin-mutation-forge": (
        "0.1.0",
        "877a72a1d8368acb21f2145095e8ae941739d7c62ccead7ad45064dddf65d684",
    ),
    "kotlin-quality-compass": (
        "0.1.0",
        "c726b9e080e146e18cf1a9b50441bb39ced4b22c904cb6ce32e2fa35b06b8b29",
    ),
}


def install(tool):
    version, expected = RELEASES[tool]
    name = f"{tool}-{version}"
    cache = ROOT / "build/quality-tools"
    destination = cache / f"{name}-{expected[:12]}"
    launcher = destination / "bin" / tool
    if launcher.is_file():
        return launcher

    cache.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=cache) as temporary:
        staging = Path(temporary)
        archive = staging / f"{name}.zip"
        url = f"https://github.com/simtop/{tool}/releases/download/v{version}/{archive.name}"
        print(f"Downloading {tool} {version}", file=sys.stderr)
        subprocess.run(
            ["curl", "--disable", "--fail", "--silent", "--show-error", "--location",
             "--proto", "=https", "--proto-redir", "=https", "--retry", "2",
             "--output", str(archive), url],
            check=True,
        )
        digest = hashlib.sha256()
        with archive.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            raise ValueError(f"SHA-256 mismatch for {archive.name}")

        with zipfile.ZipFile(archive) as release:
            release.extractall(staging)
        extracted = staging / name
        (extracted / "bin" / tool).chmod(0o755)
        try:
            extracted.rename(destination)
        except OSError:
            if not launcher.is_file():
                raise
    return launcher


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in RELEASES:
        sys.exit(f"Usage: {sys.argv[0]} {{{'|'.join(RELEASES)}}} [arguments]")
    tool, *arguments = sys.argv[1:]
    try:
        launcher = install(tool)
        os.execv(launcher, [str(launcher), *arguments])
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        sys.exit(f"{tool}: {error}")


if __name__ == "__main__":
    main()
