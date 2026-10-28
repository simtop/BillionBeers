#!/usr/bin/env python3
"""Report conservative test-task candidates without controlling CI execution."""

from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import os
import platform
from pathlib import Path, PurePosixPath
import re
import subprocess

TIERS = ("unit", "screenshot", "instrumented", "native", "web")
GLOBAL_CHECKS = (
    "Full coverage floor", "Konsist and resolved architecture checks", "Build-logic tests",
    "Test/report inventories", "Android tablet journey and minified release smoke",
)


def validate_model(model):
    if not isinstance(model, dict) or type(model.get("schemaVersion")) is not int or model["schemaVersion"] != 1:
        raise ValueError("unsupported graph schema")
    nodes = model["nodes"]
    modules = {node["path"]: node["relativeDirectory"] for node in nodes}
    if not modules or len(modules) != len(nodes) or len(set(modules.values())) != len(modules):
        raise ValueError("empty or duplicate module inventory")
    for module, directory in modules.items():
        if not re.fullmatch(r"(?::[A-Za-z0-9_-]+)+", module):
            raise ValueError("invalid module path")
        parts = PurePosixPath(directory).parts
        if not parts or directory.startswith("/") or any(part in (".", "..") for part in parts) or "\\" in directory:
            raise ValueError("invalid module directory")
    consumers = defaultdict(set)
    for edge in model["edges"]:
        if edge["source"] not in modules or edge["target"] not in modules:
            raise ValueError("graph edge references an unknown module")
        # Include every configuration, including runtime, KSP, and test-helper dependencies.
        consumers[edge["target"]].add(edge["source"])
    inventory = model["verificationTasks"]
    if set(inventory) != set(TIERS):
        raise ValueError("incomplete test-task inventory")
    for tasks in inventory.values():
        if not isinstance(tasks, list) or len(tasks) != len(set(tasks)):
            raise ValueError("invalid test-task inventory")
        for task in tasks:
            if not isinstance(task, str):
                raise ValueError("invalid test-task name")
            module, separator, name = task.rpartition(":")
            if not separator or module not in modules or not re.fullmatch(r"[A-Za-z0-9_]+", name):
                raise ValueError("test task has no graph owner")
    return modules, consumers, inventory


def source_impact(relative):
    """Return affected tiers and whether production consumers must be included."""
    parts = PurePosixPath(relative).parts
    if len(parts) < 3 or parts[0] != "src":
        return None
    source_set = parts[1]
    if source_set == "test":
        if parts[2] == "snapshots" or "screenshot" in parts[2:]:
            return {"screenshot"}, False
        # Shared test helpers can affect handwritten screenshot tests in the same module.
        return {"unit", "screenshot"}, False
    if source_set in ("androidTest", "androidDeviceTest"):
        return {"instrumented"}, False
    if source_set == "commonTest":
        return {"unit", "native", "web"}, False
    if source_set in ("jvmTest", "androidHostTest"):
        return {"unit"}, False
    if source_set == "wasmJsTest":
        return {"unit", "web"}, False
    if re.fullmatch(r"(?:ios.*|apple|native)Test", source_set):
        return {"native"}, False
    if re.fullmatch(r"(?:ios.*|apple|native)Main", source_set):
        return {"native"}, True
    if source_set in ("androidMain", "jvmMain"):
        return {"unit", "screenshot", "instrumented"}, True
    if source_set == "wasmJsMain":
        return {"unit", "web"}, True
    if source_set in ("commonMain", "main", "debug", "release"):
        return set(TIERS), True
    return None


def select(model, changed_files):
    modules, consumers, inventory = validate_model(model)
    affected = {tier: set() for tier in TIERS}
    explanations = []
    fallback = []
    for path in sorted(set(changed_files)):
        owner = next((module for module, directory in sorted(modules.items(), key=lambda pair: -len(pair[1]))
                      if path.startswith(directory + "/")), None)
        impact = source_impact(path[len(modules[owner]) + 1:]) if owner else None
        if impact:
            tiers, production = impact
            reached = {owner}
            pending = [owner] if production else []
            while pending:
                for consumer in consumers[pending.pop()] - reached:
                    reached.add(consumer)
                    pending.append(consumer)
            for tier in tiers:
                affected[tier].update(reached)
            explanations.append({"file": path, "owner": owner, "tiers": sorted(tiers),
                                 "consumers_included": production, "modules": sorted(reached)})
        elif path.startswith(("docs/", "imagesForReadme/", "fastlane/", "rod/", ".claude/skills/")) or path in ("README.md", "AGENTS.md", "LICENSE"):
            explanations.append({"file": path, "tiers": [], "reason": "documentation or local tooling"})
        else:
            fallback.append(path)
    if fallback:
        affected = {tier: set(modules) for tier in TIERS}
    selections = {}
    for tier in TIERS:
        tasks = sorted(inventory[tier])
        candidates = [task for task in tasks if task.rpartition(":")[0] in affected[tier]]
        # Play dynamic delivery is not a normal app-to-feature compile dependency.
        if tier == "instrumented" and affected[tier] and (fallback or any(item.get("consumers_included") for item in explanations)):
            candidates = sorted(set(candidates) | {task for task in tasks if task.rpartition(":")[0] == ":app"})
        # Konsist reads repository-wide sources rather than only its project dependencies.
        if tier == "unit" and any(affected.values()) and ":konsist:test" in tasks:
            candidates = sorted(set(candidates) | {":konsist:test"})
        selections[tier] = {"configured_tasks": tasks, "candidate_tasks": candidates,
                            "outside_candidate_set": sorted(set(tasks) - set(candidates))}
    return {"schema": 1, "mode": "shadow", "execution_changed": False,
            "status": "full-fallback" if fallback else "candidates",
            "fallback_files": fallback, "changed_files": sorted(set(changed_files)),
            "selections": selections, "explanations": explanations,
            "global_checks_remain_full": list(GLOBAL_CHECKS)}


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--graph", type=Path, default=Path("build/reports/module-graph/modules.json"))
    parser.add_argument("--base", default=os.environ.get("CI_IMPACT_BASE", "origin/master"))
    parser.add_argument("--changed-file", action="append", help="Explicit paths for an offline scenario replay")
    parser.add_argument("--output", type=Path, default=Path("build/reports/ci-impact-shadow/report.json"))
    parser.add_argument("--summary", type=Path,
                        default=Path(os.environ["GITHUB_STEP_SUMMARY"]) if os.environ.get("GITHUB_STEP_SUMMARY") else None)
    args = parser.parse_args(argv)
    root = args.root.resolve()
    provenance = {"report_host": platform.system() + "/" + platform.machine()}
    try:
        provenance["checkout_sha"] = git(root, "rev-parse", "HEAD").decode().strip()
        provenance["base_sha"] = git(root, "rev-parse", "--verify", args.base + "^{commit}").decode().strip()
        files = args.changed_file
        if files is None:
            # Compare actual checkout trees, including deletions and both sides of renames.
            files = git(root, "diff", "--no-renames", "--name-only", "-z", provenance["base_sha"], "HEAD").decode().split("\0")
        graph = (root / args.graph).read_bytes()
        provenance["graph_sha256"] = hashlib.sha256(graph).hexdigest()
        record = select(json.loads(graph), [path for path in files if path])
        provenance["explicit_scenario"] = args.changed_file is not None
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        record = {"schema": 1, "mode": "shadow", "execution_changed": False, "status": "unavailable",
                  "reason": type(error).__name__, "selections": {}}
    record["provenance"] = provenance
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    lines = ["### Test impact shadow report", "", "All existing CI decisions and full-suite proofs remain unchanged.",
             "Configured task counts include empty or cached tasks and do not estimate time saved.",
             "The inventory reflects registered tasks on the reporting host, not a cross-platform execution proof.", "",
             f"Status: `{record['status']}`.", "", "| Tier | Configured tasks | Candidates | Outside candidate set |", "|---|---:|---:|---:|"]
    for tier, selection in record["selections"].items():
        lines.append(f"| {tier} | {len(selection['configured_tasks'])} | {len(selection['candidate_tasks'])} | {len(selection['outside_candidate_set'])} |")
    lines.extend(["", "Coverage, architecture, build-logic, report checks, tablet journeys, and release smoke remain full.",
                  "This report is advisory and must not be used as a test execution command.", ""])
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as summary:
            summary.write("\n".join(lines))
    print(f"Shadow impact report: {record['status']}; execution unchanged.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
