#!/usr/bin/env python3
"""Verify the Xcode Gradle bridge is strict by default and has a narrow writer opt-in."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ios-framework-build.sh"
TASK = ":ios-shared:embedAndSignAppleFrameworkForXcode"
METADATA_ARGS = [
    "--write-verification-metadata",
    "sha256",
    "--no-configuration-cache",
    "--continue",
    "-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8",
    TASK,
]


with tempfile.TemporaryDirectory() as temporary:
    work = Path(temporary)
    gradlew = work / "gradlew"
    gradlew.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$CAPTURE_ARGS"\n')
    gradlew.chmod(0o755)
    capture = work / "args"
    env = os.environ | {"CAPTURE_ARGS": str(capture)}

    subprocess.run(["sh", str(SCRIPT)], cwd=work, env=env, check=True)
    assert capture.read_text().splitlines() == [TASK]

    env["IOS_HOST_WRITE_VERIFICATION_METADATA"] = "1"
    subprocess.run(["sh", str(SCRIPT)], cwd=work, env=env, check=True)
    assert capture.read_text().splitlines() == METADATA_ARGS

    marker = work / "unexpected-command"
    env["IOS_HOST_WRITE_VERIFICATION_METADATA"] = f"1; touch {marker}"
    rejected = subprocess.run(["sh", str(SCRIPT)], cwd=work, env=env, capture_output=True, text=True)
    assert rejected.returncode == 2
    assert not marker.exists()

project = (ROOT / "iosApp/BillionBeers.xcodeproj/project.pbxproj").read_text()
makefile = (ROOT / "Makefile").read_text()
workflow_tests = makefile.split("ci-report-test:", 1)[1].split("\n\n", 1)[0]
assert "scripts/ios-framework-build.sh" in project
assert "./gradlew :ios-shared:embedAndSignAppleFrameworkForXcode" not in project
assert "IOS_HOST_WRITE_VERIFICATION_METADATA=1 $(MAKE)" in makefile
assert "test_ios_framework_build.py" in workflow_tests

print("iOS framework build bridge scenarios passed")
