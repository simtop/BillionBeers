#!/usr/bin/env python3
"""Compare changed release-runtime dependency APIs with a pinned japicmp CLI."""

from __future__ import annotations

import argparse
import io
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
    return [(key, old.get(key), new.get(key)) for key in sorted(old.keys() | new.keys())
            if old.get(key) != new.get(key)]


def extract_class_jar(path: Path, destination: Path) -> Path | None:
    if path.suffix == ".jar":
        return path
    if path.suffix != ".aar":
        return None
    with zipfile.ZipFile(path) as archive:
        jars = [name for name in archive.namelist()
                if name == "classes.jar" or (name.startswith("libs/") and name.endswith(".jar"))]
        if not jars:
            return None
        destination.parent.mkdir(parents=True, exist_ok=True)
        entries = {}
        with zipfile.ZipFile(destination, "w") as output:
            for jar in sorted(jars):
                with zipfile.ZipFile(io.BytesIO(archive.read(jar))) as embedded:
                    for name in embedded.namelist():
                        if not name.endswith(".class") or name.startswith("META-INF/"):
                            continue
                        data = embedded.read(name)
                        if name in entries:
                            if entries[name] != data:
                                raise ValueError(f"Conflicting embedded class {name} in {path}")
                        else:
                            entries[name] = data
                            output.writestr(name, data)
    return destination


def read_artifacts(manifest: Path, output_dir: Path) -> dict[tuple[str, str, str, str], Path]:
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


def japicmp_options() -> list[str]:
    # Full ABI report, including internal/experimental APIs. Consumer linkage decides
    # whether a change blocks the app; no library or missing-class allowlists apply.
    return ["-a", "protected", "--only-incompatible", "--markdown"]


def read_consumers(path: Path) -> list[tuple[str, str, Path]]:
    rows = []
    for raw in path.read_text().splitlines():
        side, label, value = raw.split("\t")
        source = Path(value)
        if side not in {"consumer", "project", "platform"} or not source.exists():
            raise ValueError(f"Invalid or missing consumer input: {raw}")
        rows.append((side, label, source))
    if not any(side == "project" for side, _, _ in rows):
        raise ValueError("No compiled release consumer inputs")
    if not any(side == "platform" for side, _, _ in rows):
        raise ValueError("Android platform classpath is missing")
    return list(dict.fromkeys(rows))


def check_context(args: argparse.Namespace) -> int:
    old_graph = read_graph(args.old_graph)
    new_graph = read_graph(args.new_graph)
    bumps = changed_modules(old_graph, new_graph)
    artifacts = read_artifacts(args.artifacts / "artifacts.tsv", args.artifacts)
    consumers = read_consumers(args.artifacts / "consumers.tsv")
    reports = args.artifacts / "reports"
    reports.mkdir(exist_ok=True)
    inputs = []
    changed_keys = {key for key, _, _ in bumps}
    for (side, group, artifact, version), path in sorted(artifacts.items()):
        inputs.append((side, f"{group}:{artifact}:{version}", path))
        if (group, artifact) in changed_keys:
            inputs.append(("changed", f"{group}:{artifact}:{version}", path))
    inputs.extend(consumers)
    manifest = reports / "linkage-inputs.tsv"
    manifest.write_text("".join(f"{side}\t{label}\t{path}\n" for side, label, path in inputs))
    classpaths = {}
    for side in ("old", "new"):
        classpaths[side] = os.pathsep.join(dict.fromkeys(
            str(path) for kind, _, path in inputs if kind in {side, "platform"}
        ))
    empty = reports / "empty.jar"
    with zipfile.ZipFile(empty, "w"):
        pass
    report_errors = False
    for (group, artifact), old_version, new_version in bumps:
        old_jar = artifacts.get(("old", group, artifact, old_version))
        new_jar = artifacts.get(("new", group, artifact, new_version))
        if old_jar is None and new_jar is None:
            print(f"{group}:{artifact}: no JVM classes; platform tests cover other targets.")
            continue
        stem = f"{group}-{artifact}"
        result = subprocess.run([
            "java", "-jar", str(args.japicmp), "--old", str(old_jar or empty),
            "--new", str(new_jar or empty), "--old-classpath", classpaths["old"],
            "--new-classpath", classpaths["new"], "--xml-file", str(reports / f"{stem}.xml"),
            *japicmp_options(),
        ], check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (reports / f"{stem}.md").write_text(result.stdout)
        if result.returncode:
            print(result.stdout)
            report_errors = True
        print(f"Full API diff: {reports / f'{stem}.md'}")
    result = subprocess.run([
        "java", "-Xmx2g", "--class-path", str(args.japicmp),
        str(Path(__file__).with_name("CheckDependencyLinkage.java")),
        str(manifest), str(reports / "linkage-risks.tsv"),
    ], check=False)
    if report_errors:
        print("Japicmp could not complete the full API comparison; inspect the archived report.")
        return 2
    return result.returncode


def check(args: argparse.Namespace) -> int:
    contexts = args.artifacts / "contexts.tsv"
    if not contexts.exists():
        return check_context(args)
    rows = contexts.read_text().splitlines()
    if not rows:
        raise ValueError("No release module classpaths were exported")
    results = []
    for row in rows:
        module, raw_path = row.split("\t")
        directory = Path(raw_path)
        print(f"\n## Release runtime context {module}", flush=True)
        results.append(check_context(argparse.Namespace(
            old_graph=directory / "before.txt", new_graph=directory / "after.txt",
            artifacts=directory, japicmp=args.japicmp,
        )))
    return max(results)


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
