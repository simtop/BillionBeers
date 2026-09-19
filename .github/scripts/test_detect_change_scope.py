#!/usr/bin/env python3
"""Exercise native-aware change-scope selection in isolated Git fixtures."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/detect-change-scope.sh"


def git(directory: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=directory, text=True).strip()


def commit(directory: Path, message: str) -> str:
    changed = [line[3:] for line in git(directory, "status", "--porcelain").splitlines() if line]
    git(directory, "add", *changed)
    subprocess.run(["git", "-c", "user.email=ci@example.invalid", "-c", "user.name=CI", "commit", "-m", message], cwd=directory, check=True, stdout=subprocess.DEVNULL)
    return git(directory, "rev-parse", "HEAD")


def run_detector(directory: Path, *, event: str, action: str, base: str, before: str = "", after: str = "", jobs: str = "") -> dict[str, str]:
    output = directory / "github-output"
    gh = directory / "gh"
    gh.write_text(
        "#!/bin/sh\n"
        "case \"$*\" in\n"
        "  *workflows/ci.yml/runs*) printf '12345\\n' ;;\n"
        f"  *jobs*) printf '%s\\n' '{jobs}' | jq -c '.[]' ;;\n"
        "  *) exit 1 ;;\n"
        "esac\n"
    )
    gh.chmod(0o755)
    environment = os.environ | {
        "EVENT_NAME": event,
        "EVENT_ACTION": action,
        "BASE_SHA": base,
        "BEFORE": before,
        "AFTER": after,
        "REPO": "example/repository",
        "GH_TOKEN": "test-token",
        "GITHUB_OUTPUT": str(output),
        "PATH": f"{directory}:{os.environ['PATH']}",
    }
    subprocess.run(["bash", str(SCRIPT)], cwd=directory, env=environment, check=True, stdout=subprocess.DEVNULL)
    return dict(line.split("=", 1) for line in output.read_text().splitlines())


with tempfile.TemporaryDirectory() as temporary:
    repository = Path(temporary)
    git(repository, "init", "-q")
    (repository / ".github/scripts").mkdir(parents=True)
    (repository / ".github/scripts/detect-change-scope.sh").write_bytes(SCRIPT.read_bytes())
    (repository / ".gitignore").write_text("gh\ngithub-output\n")
    git(repository, "add", ".")
    base = commit(repository, "base")

    assert run_detector(repository, event="push", action="", base=base) == {
        "unit": "true",
        "screenshot": "true",
        "instrumented": "true",
        "native": "true",
    }

    (repository / "README.md").write_text("docs\n")
    docs_head = commit(repository, "docs")
    jobs = '[{"name":"Detect change scope","conclusion":"success"},{"name":"Unit Tests","conclusion":"success"},{"name":"Screenshot Tests (Paparazzi)","conclusion":"success"},{"name":"Instrumented Tests (Gradle Managed Device)","conclusion":"success"},{"name":"Native Tests (Apple)","conclusion":"success"}]'
    assert run_detector(repository, event="pull_request", action="synchronize", base=base, before=base, after=docs_head, jobs=jobs) == {
        "unit": "false",
        "screenshot": "false",
        "instrumented": "false",
        "native": "false",
    }

    (repository / "feature/src/iosSimulatorArm64Test").mkdir(parents=True)
    (repository / "feature/src/iosSimulatorArm64Test/NativeTest.kt").write_text("class NativeTest\n")
    native_head = commit(repository, "native test")
    native_result = run_detector(repository, event="pull_request", action="synchronize", base=base, before=docs_head, after=native_head, jobs=jobs)
    assert native_result == {
        "unit": "false",
        "screenshot": "false",
        "instrumented": "false",
        "native": "true",
    }, native_result

    (repository / "feature/src/commonTest").mkdir(parents=True)
    (repository / "feature/src/commonTest/CommonTest.kt").write_text("class CommonTest\n")
    common_head = commit(repository, "common test")
    assert run_detector(repository, event="pull_request", action="synchronize", base=base, before=native_head, after=common_head, jobs=jobs) == {
        "unit": "true",
        "screenshot": "false",
        "instrumented": "false",
        "native": "true",
    }

workflow = (ROOT / ".github/workflows/ci.yml").read_text()
for marker in (
    "native: ${{ steps.filter.outputs.native }}",
    "name: Native Tests (Apple)",
    "needs: [format-check, changes]",
    "make ios-compile",
    "make ios-framework",
    "make ios-test",
    "- native-tests",
):
    assert marker in workflow, marker

reporting = (ROOT / ".github/scripts/render-ci-pr-comment.py").read_text()
for marker in ("Compile Apple targets", "Link Apple frameworks", "Run Apple simulator tests", "native-test-reports"):
    assert marker in reporting, marker

subprocess.run(["bash", str(SCRIPT), "--self-test"], check=True)
print("Native change-scope scenarios passed")
