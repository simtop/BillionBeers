#!/usr/bin/env python3
"""Exercise every KMP tier reported by test-tier-inventory.sh."""

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/test-tier-inventory.sh"

with tempfile.TemporaryDirectory() as directory:
    fixture = Path(directory)
    module = fixture / "fixture"
    (module / "src/test").mkdir(parents=True)
    (module / "src/test/resources").mkdir(parents=True)
    (module / "src/commonTest/kotlin").mkdir(parents=True)
    (module / "src/jvmTest/kotlin").mkdir(parents=True)
    (module / "src/androidHostTest/kotlin").mkdir(parents=True)
    (module / "src/wasmJsTest/kotlin").mkdir(parents=True)
    (module / "src/iosArm64Test/kotlin").mkdir(parents=True)
    (module / "build.gradle.kts").write_text("plugins { id(\"org.jetbrains.kotlin.multiplatform\") }\n")

    empty = fixture / "empty"
    (empty / "src/test/resources").mkdir(parents=True)
    (empty / "src/test/resources/fixture.txt").write_text("not a test source\n")
    (empty / "src/commonTest").mkdir(parents=True)
    (empty / "src/androidTest").mkdir(parents=True)
    (empty / "build.gradle.kts").write_text("plugins { id(\"org.jetbrains.kotlin.multiplatform\") }\n")

    for source in (
        "src/test/Test.kt",
        "src/commonTest/kotlin/CommonTest.kt",
        "src/jvmTest/kotlin/JvmTest.kt",
        "src/androidHostTest/kotlin/HostTest.kt",
        "src/wasmJsTest/kotlin/BrowserTest.kt",
        "src/iosArm64Test/kotlin/NativeTest.kt",
    ):
        path = module / source
        path.write_text("class FixtureTest\n")

    result = subprocess.run(
        ["bash", str(SCRIPT), "--root", str(fixture)],
        check=True,
        capture_output=True,
        text=True,
    )
    report = result.stdout
    expected = {
        "| :fixture | yes | — | — | — | yes | yes | yes | yes | yes |",
    }
    missing = expected - set(report.splitlines())
    if missing:
        raise SystemExit(f"KMP inventory fixture lost classifications: {sorted(missing)}\n{report}")
    if any(line.startswith("| :empty |") for line in report.splitlines()):
        raise SystemExit(f"KMP inventory reported a module with no test source files:\n{report}")
