#!/usr/bin/env bash
# Report test-tier ownership and fail when an Android test source tree is not scheduled.
# A module is scheduled when it opts into the managed-device convention; benchmark modules are the
# deliberate exception because their tests use Android Benchmark's runner and release build type.

set -uo pipefail
cd "$(dirname "$0")/.."

OUTPUT=""
CHECK=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      [[ $# -ge 2 ]] || { echo "error: --output needs a path" >&2; exit 2; }
      OUTPUT="$2"
      shift 2
      ;;
    --check)
      CHECK=1
      shift
      ;;
    -h|--help)
      printf '%s\n' \
        "Usage: scripts/test-tier-inventory.sh [--check] [--output PATH]" \
        "  --check       fail if an androidTest source tree is not scheduled" \
        "  --output PATH write Markdown instead of stdout"
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      exit 2
      ;;
  esac
done

python3 - "$OUTPUT" "$CHECK" <<'PY'
from pathlib import Path
import sys

output_path = sys.argv[1]
check = sys.argv[2] == "1"
root = Path.cwd()

def ignored(path: Path) -> bool:
    return "build" in path.parts or ".gradle" in path.parts or ".git" in path.parts

def files_below(path: Path) -> bool:
    return any(p.is_file() for p in path.rglob("*") if not ignored(p))

def module_name(module_dir: Path) -> str:
    relative = module_dir.relative_to(root)
    return ":" + ":".join(relative.parts) if relative.parts else ":"

def module_dirs():
    for build_file in sorted(root.rglob("build.gradle.kts")):
        if ignored(build_file):
            continue
        yield build_file.parent, build_file

modules = {}
for module_dir, build_file in module_dirs():
    text = build_file.read_text(encoding="utf-8", errors="ignore")
    modules[module_dir] = {
        "name": module_name(module_dir),
        "build": build_file,
        "text": text,
        "unit": (module_dir / "src/test").is_dir() and files_below(module_dir / "src/test"),
        "screenshot": "billionbeers.android.screenshot" in text,
        "device": (module_dir / "src/androidTest").is_dir() and files_below(module_dir / "src/androidTest"),
    }

orphaned = []
for android_test in sorted(root.rglob("src/androidTest")):
    if ignored(android_test) or not android_test.is_dir() or not files_below(android_test):
        continue
    module_dir = android_test.parent.parent
    if module_dir not in modules:
        orphaned.append(android_test.relative_to(root).as_posix())

rows = []
violations = []
for module_dir, module in sorted(modules.items(), key=lambda item: item[1]["name"]):
    if not (module["unit"] or module["screenshot"] or module["device"]):
        continue
    benchmark_exempt = module_dir.relative_to(root).parts[:1] == ("benchmark",)
    scheduled = False
    status = "—"
    if module["device"]:
        scheduled = (
            "billionbeers.android.managed.device" in module["text"]
            or "billionbeers.android.feature.uitest" in module["text"]
        )
        if benchmark_exempt:
            status = "exempt (benchmark)"
        elif scheduled:
            status = "scheduled"
        else:
            status = "NOT SCHEDULED"
            violations.append(f"{module['name']} has androidTest sources but no managed-device opt-in")
    rows.append((
        module["name"],
        "yes" if module["unit"] else "—",
        "yes" if module["screenshot"] else "—",
        "yes" if module["device"] else "—",
        status,
    ))

for path in orphaned:
    violations.append(f"{path} has no adjacent build.gradle.kts")

lines = [
    "# Test-tier inventory",
    "",
    "| Module | Unit | Screenshot | Device | Device scheduling |",
    "|---|---:|---:|---:|---|",
]
for row in rows:
    lines.append("| " + " | ".join(row) + " |")
lines.extend([
    "",
    f"Modules reported: {len(rows)}",
    "",
    "> Device tests must opt into `billionbeers.android.managed.device` (directly or through "
    "`billionbeers.android.feature.uitest`). Benchmark modules are exempt because they use their "
    "own runner and release measurement path.",
])
if violations:
    lines.extend(["", "## Violations", ""])
    lines.extend(f"- {violation}" for violation in violations)
else:
    lines.extend(["", "No unscheduled Android test source trees found."])

markdown = "\n".join(lines) + "\n"
if output_path:
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(markdown, encoding="utf-8")
else:
    sys.stdout.write(markdown)

if check and violations:
    sys.exit(1)
PY
