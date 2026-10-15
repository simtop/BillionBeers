#!/usr/bin/env python3
"""Compare changed release-runtime dependency APIs with a pinned japicmp CLI."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import zipfile
from pathlib import Path


def read_graph(path: Path) -> dict[tuple[str, str], str]:
    result: dict[tuple[str, str], str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), start=1):
        line = raw.strip()
        if not line:
            continue
        parts = line.split(":")
        if len(parts) != 3 or not all(parts):
            raise ValueError(f"{path}:{number}: expected group:artifact:version, found {line!r}")
        group, artifact, version = parts
        key = (group, artifact)
        if key in result:
            raise ValueError(f"{path}:{number}: duplicate module {group}:{artifact}")
        result[key] = version
    return result


def changed_modules(old: dict[tuple[str, str], str], new: dict[tuple[str, str], str]):
    return [(key, old[key], new[key]) for key in sorted(old.keys() & new.keys()) if old[key] != new[key]]


def extract_class_jar(path: Path, destination: Path) -> Path | None:
    if path.suffix == ".jar":
        return path
    if path.suffix != ".aar":
        return None
    with zipfile.ZipFile(path) as archive:
        try:
            data = archive.read("classes.jar")
        except KeyError:
            return None
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    return destination


def read_artifacts(manifest: Path, output_dir: Path) -> dict[tuple[str, str, str], Path]:
    result: dict[tuple[str, str, str], Path] = {}
    with manifest.open() as stream:
        for number, raw in enumerate(stream, start=1):
            side, coordinate, raw_path = raw.rstrip("\n").split("\t")
            group, artifact, version = coordinate.split(":")
            source = Path(raw_path)
            if not source.is_file():
                raise ValueError(f"{manifest}:{number}: resolved artifact is missing: {source}")
            extracted = extract_class_jar(
                source,
                output_dir / "classes" / side / f"{group}-{artifact}-{version}.jar",
            )
            if extracted is not None:
                result[(side, group, artifact, version)] = extracted
    if not result:
        raise ValueError(f"{manifest}: Gradle resolver exported no JVM artifacts")
    return result


def check(args: argparse.Namespace) -> int:
    old_graph = read_graph(args.old_graph)
    new_graph = read_graph(args.new_graph)
    bumps = changed_modules(old_graph, new_graph)
    if not bumps:
        print("No existing release-runtime modules changed versions; japicmp check is not applicable.")
        return 0

    artifacts = read_artifacts(args.artifacts / "artifacts.tsv", args.artifacts)
    classpaths: dict[str, str] = {}
    for side in ("old", "new"):
        jars = sorted({str(path) for (artifact_side, *_), path in artifacts.items() if artifact_side == side})
        classpaths[side] = os.pathsep.join(jars)

    failures = []
    compared = 0
    skipped = []
    for (group, artifact), old_version, new_version in bumps:
        old_jar = artifacts.get(("old", group, artifact, old_version))
        new_jar = artifacts.get(("new", group, artifact, new_version))
        coordinate = f"{group}:{artifact}:{old_version} -> {new_version}"
        if old_jar is None or new_jar is None:
            skipped.append(coordinate + " (no JVM classes in the selected artifact)")
            continue
        compared += 1
        print(f"\n### {coordinate}")
        result = subprocess.run(
            [
                "java", "-jar", str(args.japicmp),
                "--old", str(old_jar), "--new", str(new_jar),
                "--old-classpath", classpaths["old"],
                "--new-classpath", classpaths["new"],
                "-a", "protected", "--only-incompatible",
                "--error-on-binary-incompatibility", "--markdown",
            ],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        print(result.stdout, end="" if result.stdout.endswith("\n") else "\n")
        if result.returncode:
            failures.append(coordinate)

    if skipped:
        print("\nArtifacts without JVM classes (not compared):")
        for item in skipped:
            print(f"- {item}")
    if compared == 0:
        print("No changed runtime artifacts contained JVM classes to compare.")
    if failures:
        print("\nBinary-incompatible dependency API changes detected:")
        for item in failures:
            print(f"- {item}")
        return 1
    print(f"\nCompared {compared} changed runtime artifact(s); no binary-incompatible API changes found.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old-graph", required=True, type=Path)
    parser.add_argument("--new-graph", required=True, type=Path)
    parser.add_argument("--artifacts", required=True, type=Path)
    parser.add_argument("--japicmp", required=True, type=Path)
    try:
        return check(parser.parse_args())
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Dependency API check could not complete: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
