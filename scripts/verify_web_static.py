#!/usr/bin/env python3
"""Verify a Kotlin/Wasm browser distribution is self-contained and base-path safe."""

from __future__ import annotations

import argparse
import posixpath
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class VerificationError(Exception):
    pass


class AssetReferenceParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.references: list[tuple[str, str]] = []
        self.base_href: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "base" and attributes.get("href"):
            self.base_href = attributes["href"]
        for attribute in ("src", "href"):
            value = attributes.get(attribute)
            if value and (tag in {"script", "img", "source", "iframe"} or attribute == "href"):
                self.references.append((f"<{tag} {attribute}>", value))


def _relative_asset_path(reference: str, entrypoint: Path) -> Path:
    parsed = urlsplit(reference)
    if parsed.scheme or parsed.netloc or reference.startswith("/"):
        raise VerificationError(f"asset reference must be relative: {reference}")
    if not parsed.path or parsed.path == ".":
        raise VerificationError(f"asset reference has no file path: {reference}")
    decoded_path = unquote(parsed.path)
    normalized = posixpath.normpath(posixpath.join(entrypoint.parent.as_posix(), decoded_path))
    if normalized == ".." or normalized.startswith("../"):
        raise VerificationError(f"asset reference escapes the distribution: {reference}")
    return Path(normalized)


def verify_distribution(root: Path, entrypoint: str = "index.html") -> list[str]:
    root = root.resolve()
    if not root.is_dir():
        raise VerificationError(f"distribution directory does not exist: {root}")
    files = [path for path in root.rglob("*") if path.is_file()]
    if not files:
        raise VerificationError(f"distribution is empty: {root}")

    entrypoint_path = root / entrypoint
    if not entrypoint_path.is_file():
        raise VerificationError(f"entrypoint does not exist: {entrypoint_path}")

    parser = AssetReferenceParser()
    parser.feed(entrypoint_path.read_text(encoding="utf-8"))
    if parser.base_href:
        _relative_asset_path(parser.base_href, Path(entrypoint))

    checked: list[str] = []
    for label, reference in parser.references:
        if reference.startswith("#") or reference.startswith("data:"):
            continue
        relative_path = _relative_asset_path(reference, Path(entrypoint))
        target = (root / relative_path).resolve()
        if root not in target.parents and target != root:
            raise VerificationError(f"{label} escapes the distribution: {reference}")
        if not target.is_file():
            raise VerificationError(f"{label} is missing: {reference}")
        checked.append(relative_path.as_posix())

    wasm_files = [path for path in files if path.suffix == ".wasm"]
    if not wasm_files:
        raise VerificationError("distribution contains no Wasm module")
    if not any(path.suffix == ".js" for path in files):
        raise VerificationError("distribution contains no JavaScript entrypoint")
    return sorted(set(checked + [path.relative_to(root).as_posix() for path in wasm_files]))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("distribution", type=Path)
    parser.add_argument("--entrypoint", default="index.html")
    args = parser.parse_args()
    try:
        checked = verify_distribution(args.distribution, args.entrypoint)
    except (OSError, VerificationError) as error:
        print(f"Web static verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Web static verification passed: {args.distribution}")
    print(f"Verified assets: {len(checked)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
