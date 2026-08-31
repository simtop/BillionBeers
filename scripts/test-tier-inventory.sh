#!/usr/bin/env bash
# Report which test tiers each module owns. Architecture rules, not this inventory, enforce that
# Android test source trees are scheduled and attached to real modules.

set -uo pipefail
cd "$(dirname "$0")/.."

OUTPUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      [[ $# -ge 2 ]] || { echo "error: --output needs a path" >&2; exit 2; }
      OUTPUT="$2"
      shift 2
      ;;
    -h|--help)
      printf '%s\n' \
        "Usage: scripts/test-tier-inventory.sh [--output PATH]" \
        "  --output PATH write Markdown instead of stdout"
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      exit 2
      ;;
  esac
done

python3 - "$OUTPUT" <<'PY'
from pathlib import Path
import sys

output_path = sys.argv[1]
root = Path.cwd()


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
    if not (unit or screenshot or instrumented or standalone):
        continue
    rows.append((
        module_name(module_dir),
        "yes" if unit else "—",
        "yes" if screenshot else "—",
        "yes" if instrumented else "—",
        "yes" if standalone else "—",
    ))

lines = [
    "# Test-tier inventory",
    "",
    "| Module | Local unit | Screenshot | Instrumented | Standalone test APK |",
    "|---|---:|---:|---:|---:|",
]
for row in rows:
    lines.append("| " + " | ".join(row) + " |")
lines.extend([
    "",
    f"Modules reported: {len(rows)}",
    "",
    "> This report is informational. `InstrumentedTestOptInBoundaryTest` and "
    "`OrphanedSourceTreeTest` remain the authoritative scheduling and source-tree gates.",
])

markdown = "\n".join(lines) + "\n"
if output_path:
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(markdown, encoding="utf-8")
else:
    sys.stdout.write(markdown)
PY
