#!/usr/bin/env python3
"""Rebuild instrumented-test PNGs emitted as bounded logcat chunks."""

from __future__ import annotations

import argparse
import base64
import binascii
from pathlib import Path
import re
import sys

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
LOG_LINE_PATTERNS = (
    re.compile(r"\bI FailureScreenshot: (.*)$"),
    re.compile(r"\bI/FailureScreenshot(?:\(\s*\d+\))?: (.*)$"),
)


def marker_payload(line: str) -> str | None:
    for pattern in LOG_LINE_PATTERNS:
        match = pattern.search(line)
        if match:
            return match.group(1)
    return None


def extract_logcat(logcat: Path) -> list[Path]:
    records: dict[str, dict[str, object]] = {}
    extracted: list[Path] = []
    for line in logcat.read_text(encoding="utf-8", errors="replace").splitlines():
        payload = marker_payload(line)
        if payload is None:
            continue
        parts = payload.split("|", maxsplit=3)
        if not parts:
            continue
        kind = parts[0]
        if kind == "SCREENSHOT_END" and len(parts) == 2:
            screenshot_id = parts[1]
            if screenshot_id not in records:
                continue
            record = records.pop(screenshot_id)
            chunks = record["chunks"]
            assert isinstance(chunks, dict)
            expected = record["expected_chunks"]
            assert isinstance(expected, int)
            if len(chunks) != expected or set(chunks) != set(range(expected)):
                print(f"Incomplete failure screenshot in {logcat}: {screenshot_id}", file=sys.stderr)
                continue
            try:
                png = base64.b64decode(
                    "".join(chunks[index] for index in range(expected)), validate=True
                )
            except (ValueError, binascii.Error) as error:
                print(f"Invalid failure screenshot in {logcat}: {error}", file=sys.stderr)
                continue
            if not png.startswith(PNG_SIGNATURE):
                print(f"Failure screenshot is not a PNG in {logcat}: {screenshot_id}", file=sys.stderr)
                continue

            name = re.sub(r"[^A-Za-z0-9._-]", "_", str(record["name"]))
            output_directory = logcat.parent / "failure-screenshots"
            output_directory.mkdir(parents=True, exist_ok=True)
            output = output_directory / f"{name}-{screenshot_id}.png"
            output.write_bytes(png)
            extracted.append(output)
            continue
        if len(parts) != 4:
            continue
        _, screenshot_id, value, detail = parts
        if kind == "SCREENSHOT_BEGIN":
            try:
                expected_chunks = int(detail)
            except ValueError:
                continue
            records[screenshot_id] = {
                "name": value,
                "expected_chunks": expected_chunks,
                "chunks": {},
            }
        elif kind == "SCREENSHOT_CHUNK" and screenshot_id in records:
            try:
                chunk_index = int(value)
            except ValueError:
                continue
            chunks = records[screenshot_id]["chunks"]
            assert isinstance(chunks, dict)
            chunks[chunk_index] = detail
    return extracted


def extract(root: Path) -> list[Path]:
    logcats = sorted(
        root.glob("**/build/outputs/androidTest-results/managedDevice/**/logcat-*.txt")
    )
    return [image for logcat in logcats for image in extract_logcat(logcat)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path.cwd())
    args = parser.parse_args()
    screenshots = extract(args.root)
    print(f"Extracted {len(screenshots)} failure screenshot(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
