#!/usr/bin/env bash
# Report which test tiers each module owns. Architecture rules, not this inventory, enforce that
# Android test source trees are scheduled and attached to real modules.

set -uo pipefail
SCRIPT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$SCRIPT_ROOT"
OUTPUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      [[ $# -ge 2 ]] || { echo "error: --output needs a path" >&2; exit 2; }
      OUTPUT="$2"
      shift 2
      ;;
    --root)
      [[ $# -ge 2 ]] || { echo "error: --root needs a path" >&2; exit 2; }
      ROOT="$2"
      shift 2
      ;;
    -h|--help)
      printf '%s\n' \
        "Usage: scripts/test-tier-inventory.sh [--output PATH] [--root PATH]" \
        "  --output PATH write Markdown instead of stdout" \
        "  --root PATH scan a fixture or alternate repository root"
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      exit 2
      ;;
  esac
done

python3 - "$OUTPUT" "$ROOT" <<'PY'
from pathlib import Path
import sys

output_path = sys.argv[1]
root = Path(sys.argv[2]).resolve()


def ignored(path: Path) -> bool:
    return "build" in path.parts or ".gradle" in path.parts or ".git" in path.parts


def files_below(path: Path) -> bool:
    return any(p.is_file() for p in path.rglob("*") if not ignored(p))


def source_files_below(path: Path) -> bool:
    return any(
        p.is_file() and p.suffix in {".java", ".kt"}
        for p in path.rglob("*")
        if not ignored(p)
    )


def module_name(module_dir: Path) -> str:
    relative = module_dir.relative_to(root)
    return ":" + ":".join(relative.parts) if relative.parts else ":"


def module_dirs():
    for build_file in sorted(root.rglob("build.gradle.kts")):
        if ignored(build_file):
            continue
        yield build_file.parent, build_file


rows = []
for module_dir, build_file in module_dirs():
    text = build_file.read_text(encoding="utf-8", errors="ignore")
    unit = (module_dir / "src/test").is_dir() and files_below(module_dir / "src/test")
    screenshot = "billionbeers.android.screenshot" in text
    instrumented = (
        (module_dir / "src/androidTest").is_dir()
        and files_below(module_dir / "src/androidTest")
    )
    standalone = "com.android.test" in text and source_files_below(module_dir / "src/main")
    kmp_common = source_files_below(module_dir / "src/commonTest")
    kmp_jvm = source_files_below(module_dir / "src/jvmTest")
    kmp_host = source_files_below(module_dir / "src/androidHostTest")
    kmp_browser = source_files_below(module_dir / "src/wasmJsTest")
    kmp_native = any(
        source_files_below(path)
        for path in module_dir.glob("src/ios*Test")
        if path.is_dir()
    )
    if not (unit or screenshot or instrumented or standalone or kmp_common or kmp_jvm or kmp_host or kmp_browser or kmp_native):
        continue
    rows.append((
        module_name(module_dir),
        "yes" if unit else "—",
        "yes" if screenshot else "—",
        "yes" if instrumented else "—",
        "yes" if standalone else "—",
        "yes" if kmp_common else "—",
        "yes" if kmp_jvm else "—",
        "yes" if kmp_host else "—",
        "yes" if kmp_browser else "—",
        "yes" if kmp_native else "—",
    ))

lines = [
    "# Test-tier inventory",
    "",
    "| Module | Local unit | Screenshot | Instrumented | Standalone test APK | KMP common | KMP JVM | Android host | Browser | Native test execution |",
    "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
]
for row in rows:
    lines.append("| " + " | ".join(row) + " |")
lines.extend([
    "",
    f"Modules reported: {len(rows)}",
    "",
    "> This report is informational. Native test execution is CI-gated; native percentage coverage is "
    "not measured. `InstrumentedTestOptInBoundaryTest` and `OrphanedSourceTreeTest` remain the "
    "authoritative scheduling and source-tree gates.",
])

markdown = "\n".join(lines) + "\n"
if output_path:
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(markdown, encoding="utf-8")
else:
    sys.stdout.write(markdown)
PY
