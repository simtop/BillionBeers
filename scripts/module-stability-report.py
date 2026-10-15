#!/usr/bin/env python3
"""Report production-graph stability and 90-day changed-line churn per Gradle module."""

from __future__ import annotations

import argparse
import json
import subprocess
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


class GitHistoryUnavailable(RuntimeError):
    """Raised when the checkout cannot support a full 90-day churn measurement."""


def parse_numstat(
    output: str, module_directories: list[str]
) -> dict[str, int]:
    directories = sorted(
        (directory.rstrip("/") for directory in module_directories),
        key=len,
        reverse=True,
    )
    churn = {directory: 0 for directory in directories}
    for line in output.splitlines():
        fields = line.split("\t", 2)
        if len(fields) != 3 or fields[0] == "-" or fields[1] == "-":
            continue
        try:
            changed_lines = int(fields[0]) + int(fields[1])
        except ValueError:
            continue
        path = fields[2]
        module_directory = next(
            (
                directory
                for directory in directories
                if path == directory or path.startswith(directory + "/")
            ),
            None,
        )
        if module_directory is not None:
            churn[module_directory] += changed_lines
    return churn


def collect_churn(
    root: Path,
    module_directories: list[str],
    days: int = 90,
    now: datetime | None = None,
) -> dict[str, int]:
    shallow = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--is-shallow-repository"],
        check=False,
        capture_output=True,
        text=True,
    )
    if shallow.returncode != 0:
        raise GitHistoryUnavailable("Git history could not be inspected")
    if shallow.stdout.strip() == "true":
        raise GitHistoryUnavailable("the checkout has shallow Git history")

    as_of = now or datetime.now(timezone.utc)
    as_of = as_of.astimezone(timezone.utc)
    cutoff = (as_of - timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    result = subprocess.run(
        [
            "git",
            "-C",
            str(root),
            "log",
            f"--since={cutoff}",
            "--no-merges",
            "--no-renames",
            "--numstat",
            "--format=",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise GitHistoryUnavailable("Git could not read the last 90 days of changes")
    return parse_numstat(result.stdout, module_directories)


def module_metrics(graph: dict[str, Any], churn: dict[str, int]) -> list[dict[str, Any]]:
    nodes = graph.get("nodes")
    edges = graph.get("edges")
    if not isinstance(nodes, list) or not isinstance(edges, list):
        raise ValueError("module graph must contain node and edge arrays")

    module_by_path = {
        node["path"]: node
        for node in nodes
        if isinstance(node, dict)
        and isinstance(node.get("path"), str)
        and isinstance(node.get("relativeDirectory"), str)
    }
    fan_in: dict[str, set[str]] = defaultdict(set)
    fan_out: dict[str, set[str]] = defaultdict(set)
    for edge in edges:
        if not isinstance(edge, dict) or "main" not in edge.get("scopes", []):
            continue
        source = edge.get("source")
        target = edge.get("target")
        if source in module_by_path and target in module_by_path and source != target:
            fan_out[source].add(target)
            fan_in[target].add(source)

    metrics = []
    for module_path, node in module_by_path.items():
        directory = node["relativeDirectory"].replace("\\", "/").rstrip("/")
        changed_lines = churn.get(directory, 0)
        incoming = len(fan_in[module_path])
        outgoing = len(fan_out[module_path])
        total_edges = incoming + outgoing
        stability = incoming / total_edges if total_edges else None
        stability_text = f"{stability:.0%}" if stability is not None else "n/a"
        metrics.append(
            {
                "id": f"module_stability_churn_90d:{module_path}",
                "name": f"Module {module_path} stability and churn",
                "displayValue": (
                    f"{changed_lines:,} changed lines / 90d · stability {stability_text} "
                    f"(fan-in {incoming}, fan-out {outgoing})"
                ),
                "value": changed_lines,
                "unit": "lines/90d",
                "status": "info",
                "statusLabel": "ℹ️ tracked",
                "action": (
                    "stability = production dependents / (dependents + dependencies); "
                    "churn counts added and deleted text lines in non-merge commits"
                ),
            }
        )
    if not metrics:
        raise ValueError("module graph contains no modules")
    return sorted(metrics, key=lambda metric: (-metric["value"], metric["name"]))


def unavailable_metric(reason: str) -> dict[str, Any]:
    return {
        "id": "module_stability_churn_90d_unavailable",
        "name": "Module stability and 90-day churn",
        "displayValue": "n/a",
        "value": None,
        "unit": "lines/90d",
        "status": "unavailable",
        "statusLabel": "⚪ not measured",
        "action": reason,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--graph", type=Path, required=True, help="module graph JSON")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Git repository root")
    parser.add_argument("--as-of", help="UTC timestamp for deterministic cutoff calculation")
    args = parser.parse_args(argv)

    try:
        graph = json.loads(args.graph.read_text(encoding="utf-8"))
        if not isinstance(graph, dict):
            raise ValueError("module graph must be a JSON object")
        modules = graph.get("nodes", [])
        directories = [
            node["relativeDirectory"]
            for node in modules
            if isinstance(node, dict) and isinstance(node.get("relativeDirectory"), str)
        ]
        as_of = (
            datetime.fromisoformat(args.as_of.replace("Z", "+00:00"))
            if args.as_of
            else None
        )
        if as_of is not None and as_of.tzinfo is None:
            raise ValueError("--as-of must include a UTC offset")
        churn = collect_churn(args.root, directories, now=as_of)
        metrics = module_metrics(graph, churn)
    except GitHistoryUnavailable as error:
        metrics = [
            unavailable_metric(
                f"{error}; fetch full repository history to measure the 90-day window"
            )
        ]
    except (OSError, json.JSONDecodeError, TypeError, ValueError, KeyError, AttributeError) as error:
        metrics = [unavailable_metric(f"module analysis could not be completed: {error}")]

    for metric in metrics:
        print(json.dumps(metric, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
