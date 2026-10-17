#!/usr/bin/env python3
"""Select the actual Android test task for a module's supported plugin convention."""

from __future__ import annotations

import argparse
from pathlib import Path
import re


KMP_PLUGINS = (
    "billionbeers.kmp.library",
    "billionbeers.kmp.feature",
    "com.android.kotlin.multiplatform.library",
)


def task_for_module(root: Path, module: str, prefix: str) -> str:
    if not re.fullmatch(r"(?::[A-Za-z0-9_-]+)+", module):
        raise ValueError(f"Invalid Gradle module path: {module}")
    script = root.joinpath(*module.strip(":").split(":"), "build.gradle.kts")
    text = script.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("//"))
    kmp = any(
        re.search(r'\bid\s*\(\s*"' + re.escape(plugin) + r'"\s*\)', text)
        for plugin in KMP_PLUGINS
    )
    suffix = "AndroidDeviceTest" if kmp else "DebugAndroidTest"
    return f"{module}:{prefix}{suffix}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("module")
    parser.add_argument("prefix", choices=("connected", "atdApi35", "pixel9Api37"))
    args = parser.parse_args()
    try:
        print(task_for_module(Path(__file__).resolve().parents[1], args.module, args.prefix))
    except (OSError, ValueError) as error:
        parser.exit(2, f"{error}\n")


if __name__ == "__main__":
    main()
