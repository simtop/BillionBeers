#!/usr/bin/env python3
"""Validate platform preparation evidence and safely union its Gradle ledgers."""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_LANES = {"linux", "web", "managed", "apple"}
CHECKER_SPEC = importlib.util.spec_from_file_location(
    "metadata_checker", Path(__file__).with_name("check-verification-metadata-update.py")
)
CHECKER = importlib.util.module_from_spec(CHECKER_SPEC)
CHECKER_SPEC.loader.exec_module(CHECKER)


def component_key(element: ET.Element) -> tuple[str, str, str]:
    return tuple(element.attrib.get(key, "") for key in ("group", "name", "version"))


def validate_evidence(
    directory: Path, head: str, run_id: str, attempt: str, source_ref: str, mode: str
) -> list[Path]:
    if not directory.is_dir():
        raise ValueError(f"platform evidence directory is missing: {directory}")
    lane_dirs = {path.name: path for path in directory.iterdir() if path.is_dir()}
    if set(lane_dirs) != EXPECTED_LANES:
        raise ValueError(f"expected lane evidence {sorted(EXPECTED_LANES)}, got {sorted(lane_dirs)}")

    common: dict[str, str] | None = None
    prepared_bytes: bytes | None = None
    ledgers = []
    for lane in sorted(EXPECTED_LANES):
        manifest_path = lane_dirs[lane] / "manifest.json"
        ledger_path = lane_dirs[lane] / "verification-metadata.xml"
        try:
            manifest = json.loads(manifest_path.read_text())
        except (OSError, json.JSONDecodeError) as error:
            raise ValueError(f"invalid {lane} manifest: {error}") from error
        required_fields = {
            "lane": str, "head_sha": str, "run_id": str, "run_attempt": str,
            "source_ref": str, "conclusion": str, "mode": str, "skipped": bool,
            "prepared_sha256": str, "formatting_files_sha256": str,
        }
        if not isinstance(manifest, dict) or any(
            key not in manifest or not isinstance(manifest[key], value_type)
            for key, value_type in required_fields.items()
        ):
            raise ValueError(f"invalid {lane} manifest: required fields are missing or malformed")
        expected = {"lane": lane, "head_sha": head, "run_id": run_id,
                    "run_attempt": attempt, "source_ref": source_ref,
                    "conclusion": "success", "mode": mode}
        if any(str(manifest.get(key, "")) != value for key, value in expected.items()):
            raise ValueError(f"{lane} evidence has a stale or wrong run/head/source identity")
        identity = {key: str(manifest.get(key, "")) for key in
                    ("mode", "prepared_sha256", "formatting_files_sha256", "skipped")}
        prepared = lane_dirs[lane] / "prepared.patch"
        if not prepared.is_file():
            raise ValueError(f"{lane} prepared inputs are missing")
        patch = prepared.read_bytes()
        if hashlib.sha256(patch).hexdigest() != identity["prepared_sha256"]:
            raise ValueError(f"{lane} prepared inputs do not match their recorded digest")
        if prepared_bytes is None:
            prepared_bytes = patch
        elif patch != prepared_bytes:
            raise ValueError("platform lanes do not share the same prepared baseline/formatting patch")
        formatting = lane_dirs[lane] / "formatting-files"
        if not formatting.is_file() or hashlib.sha256(formatting.read_bytes()).hexdigest() != identity["formatting_files_sha256"]:
            raise ValueError(f"{lane} formatter file list does not match its recorded digest")
        paths = [path.decode() for path in formatting.read_bytes().split(b"\0") if path]
        if any(not path.endswith((".kt", ".gradle.kts")) for path in paths):
            raise ValueError(f"{lane} formatter file list contains an unexpected path")
        if common is None:
            common = identity
        elif identity != common:
            raise ValueError("platform lanes disagree on mode, runtime baseline, or formatted output")
        if not ledger_path.is_file():
            raise ValueError(f"{lane} ledger is missing")
        ledgers.append(ledger_path)
    return ledgers


def merge(before_path: Path, ledgers: list[Path], output: Path) -> None:
    try:
        baseline = CHECKER.load(before_path)
        baseline_configuration = CHECKER.child_named(baseline, "configuration")
        if baseline_configuration is None:
            raise ValueError("baseline has no <configuration>")
        result = copy.deepcopy(baseline)
        result_components = CHECKER.child_named(result, "components")
        if result_components is None:
            raise ValueError("baseline has no <components>")
        components = {component_key(item): item for item in result_components
                      if CHECKER.local_name(item.tag) == "component"}

        added_artifacts = False
        for ledger_path in ledgers:
            if CHECKER.verify(before_path, ledger_path):
                raise ValueError(f"{ledger_path} changes a recorded checksum or verification policy")
            ledger = CHECKER.load(ledger_path)
            configuration = CHECKER.child_named(ledger, "configuration")
            if configuration is None or CHECKER.canonical(configuration) != CHECKER.canonical(baseline_configuration):
                raise ValueError(f"{ledger_path} changed verification policy")
            ledger_components = CHECKER.child_named(ledger, "components")
            if ledger_components is None:
                raise ValueError(f"{ledger_path} has no <components>")
            for incoming_component in ledger_components:
                if CHECKER.local_name(incoming_component.tag) != "component":
                    continue
                key = component_key(incoming_component)
                destination = components.get(key)
                if destination is None:
                    destination = copy.deepcopy(incoming_component)
                    result_components.append(destination)
                    components[key] = destination
                    added_artifacts = True
                    continue
                artifacts = {item.attrib.get("name", ""): item for item in destination
                             if CHECKER.local_name(item.tag) == "artifact"}
                for incoming_artifact in incoming_component:
                    if CHECKER.local_name(incoming_artifact.tag) != "artifact":
                        continue
                    name = incoming_artifact.attrib.get("name", "")
                    previous = artifacts.get(name)
                    if previous is None:
                        destination.append(copy.deepcopy(incoming_artifact))
                        artifacts[name] = incoming_artifact
                        added_artifacts = True
                        continue
                    previous_checksums = CHECKER.artifact_checksums_for_element(previous)
                    incoming_checksums = CHECKER.artifact_checksums_for_element(incoming_artifact)
                    if previous_checksums != incoming_checksums:
                        raise ValueError(f"conflicting shared artifact {key}:{name}")

        output.parent.mkdir(parents=True, exist_ok=True)
        if not added_artifacts:
            output.write_bytes(before_path.read_bytes())
            outcome = CHECKER.verify(before_path, output)
            if outcome:
                raise ValueError("merged ledger changes a recorded checksum or policy")
            return

        def sort_tree(parent: ET.Element) -> None:
            components = [child for child in parent if CHECKER.local_name(child.tag) == "component"]
            for component in components:
                artifacts = [child for child in component if CHECKER.local_name(child.tag) == "artifact"]
                artifacts.sort(key=lambda item: item.attrib.get("name", ""))
                other = [child for child in component if CHECKER.local_name(child.tag) != "artifact"]
                component[:] = sorted(other, key=CHECKER.canonical) + artifacts
            components.sort(key=component_key)
            other = [child for child in parent if CHECKER.local_name(child.tag) != "component"]
            parent[:] = other + components

        sort_tree(result_components)
        ET.register_namespace("", "https://schema.gradle.org/dependency-verification")
        ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
        ET.indent(result, space="   ")
        with output.open("wb") as stream:
            stream.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
            ET.ElementTree(result).write(stream, encoding="UTF-8", xml_declaration=False)
            stream.write(b"\n")
        # Gradle's serializer writes self-closing tags without a space before />.
        output.write_bytes(output.read_bytes().replace(b" />", b"/>"))
        outcome = CHECKER.verify(before_path, output)
        if outcome:
            raise ValueError("merged ledger changes a recorded checksum or policy")
    except (OSError, ET.ParseError, ValueError) as error:
        raise ValueError(str(error)) from error


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--attempt", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--mode", choices=("write", "reference", "candidate"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        ledgers = validate_evidence(
            args.evidence, args.head_sha, args.run_id, args.attempt, args.source_ref, args.mode
        )
        merge(args.before, ledgers, args.output)
    except ValueError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    checksums = CHECKER.artifact_checksums(CHECKER.load(args.output))
    print(f"Safe platform union contains {len(checksums)} artifacts from {len(ledgers)} lanes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
