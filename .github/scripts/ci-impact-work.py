#!/usr/bin/env python3
"""Compare shadow candidate owners with observed test work; never select tests."""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import re

LANES = {"unit": "unit-tests", "screenshot": "screenshot-tests", "instrumented": "instrumented-tests"}
ARTIFACTS = {"unit": "unit-gradle-profiles", "screenshot": "screenshot-gradle-profiles",
             "instrumented": "managed-device-gradle-profiles"}
REPORT_MODES = {"unit": "unit", "screenshot": "paparazzi", "instrumented": "managed-device"}
BUCKETS = ("candidate", "outside", "unmapped")


def owner(task):
    if not isinstance(task, str) or not re.fullmatch(r"(?::[A-Za-z0-9_-]+)+", task):
        raise ValueError("invalid task name")
    return task.rpartition(":")[0]


def validate_impact(impact, identity):
    if (type(impact.get("schema")) is not int or impact.get("schema") != 1 or impact.get("mode") != "shadow" or
            impact.get("execution_changed") is not False or impact.get("status") not in ("candidates", "full-fallback")):
        raise ValueError("impact report unavailable or unsupported")
    if impact["provenance"]["checkout_sha"] != identity["GITHUB_SHA"]:
        raise ValueError("impact checkout does not match measured checkout")
    if (type(impact["provenance"].get("explicit_scenario")) is not bool or
            not isinstance(impact["changed_files"], list) or
            any(not isinstance(path, str) for path in impact["changed_files"])):
        raise ValueError("invalid scenario or changed-file identity")
    for tier in LANES:
        selection = impact["selections"][tier]
        sets = {}
        for key in ("configured_tasks", "candidate_tasks", "outside_candidate_set"):
            values = selection[key]
            if not isinstance(values, list) or any(not isinstance(task, str) for task in values):
                raise ValueError("invalid candidate inventory")
            if len(values) != len(set(values)):
                raise ValueError("duplicate candidate inventory")
            for task in values:
                if not owner(task):
                    raise ValueError("candidate task has no module owner")
            sets[key] = set(values)
        if (sets["candidate_tasks"] & sets["outside_candidate_set"] or
                sets["candidate_tasks"] | sets["outside_candidate_set"] != sets["configured_tasks"]):
            raise ValueError("candidate inventory is not a complete partition")
        if impact["status"] == "full-fallback" and sets["candidate_tasks"] != sets["configured_tasks"]:
            raise ValueError("full fallback omits configured tasks")


def measure_lane(tier, selection, evidence, identity):
    if (type(evidence.get("schema")) is not int or evidence.get("schema") != 1 or evidence.get("available") is not True or
            evidence.get("errors") != [] or not evidence.get("profiles")):
        raise ValueError("task profiles missing, incomplete, or unsupported")
    expected = identity | {"GITHUB_JOB": LANES[tier]}
    if any(evidence.get("provenance", {}).get(key) != value for key, value in expected.items()):
        raise ValueError("profile run, attempt, checkout, or producer does not match")
    if evidence.get("lane_outcome") != "success":
        raise ValueError("lane did not complete successfully")
    configured = {owner(task) for task in selection["configured_tasks"]}
    candidates = {owner(task) for task in selection["candidate_tasks"]}
    totals = {bucket: 0.0 for bucket in BUCKETS}
    nonexecuted = {bucket: 0.0 for bucket in BUCKETS}
    rows = []
    observed = set()
    other = 0.0
    files = set()
    for profile in evidence["profiles"]:
        name = profile["file"]
        if not isinstance(name, str) or name in files:
            raise ValueError("duplicate or invalid profile identity")
        files.add(name)
        tasks = set()
        for task in profile["tasks"]:
            path = task["task"]
            module = owner(path)
            duration = task["seconds"]
            if (path in tasks or type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0 or
                    not isinstance(task["outcome"], str) or not isinstance(task["category"], str)):
                raise ValueError("invalid task work")
            tasks.add(path)
            if task["category"] != "Test tasks":
                other += duration
                continue
            observed.add(module)
            bucket = "candidate" if module in candidates else "outside" if module in configured else "unmapped"
            destination = totals if task["outcome"] == "EXECUTED" else nonexecuted
            destination[bucket] += duration
            rows.append({"profile": name, "task": path, "owner": module, "bucket": bucket,
                         "seconds": duration, "outcome": task["outcome"]})
    if not rows:
        raise ValueError("no test-task observations")
    return {"status": "measured", "lane_outcome": evidence["lane_outcome"],
            "executed_test_task_seconds": {key: round(value, 3) for key, value in totals.items()},
            "nonexecuted_test_task_seconds": {key: round(value, 3) for key, value in nonexecuted.items()},
            "other_task_seconds": round(other, 3), "test_task_observations": rows,
            "unobserved_configured_owners": sorted(configured - observed), "profiles": sorted(files)}


def one_json(directory, filename):
    files = list(directory.rglob(filename))
    if len(files) != 1:
        raise ValueError("expected exactly one evidence file")
    return json.loads(files[0].read_text())


def compare_failures(tier, impact, evidence, identity):
    provenance = impact["provenance"]
    if (provenance["explicit_scenario"] or
            any(provenance.get(key) != identity[key] for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"))):
        raise ValueError("candidate report is not from this CI attempt")
    expected = identity | {"GITHUB_JOB": LANES[tier]}
    if (type(evidence["schema"]) is not int or evidence["schema"] != 1 or
            evidence["mode"] != REPORT_MODES[tier] or evidence["provenance"] != expected):
        raise ValueError("failure report run, attempt, checkout, or producer does not match")

    selection = impact["selections"][tier]
    configured = {owner(task) for task in selection["configured_tasks"]}
    candidates = {owner(task) for task in selection["candidate_tasks"]}
    unreadable = evidence["unreadable_reports"]
    fallback = evidence["fallback_modules"]
    if (type(unreadable) is not int or unreadable < 0 or not isinstance(fallback, list) or
            not isinstance(evidence["failures"], list)):
        raise ValueError("invalid JUnit failure evidence")
    for module in fallback:
        owner(module)

    counts = dict.fromkeys(BUCKETS, 0)
    failures = []
    pilot = impact.get("locale_pilot", {})
    pilot_counts = None
    if tier in ("screenshot", "instrumented") and pilot.get("status") == "candidates":
        planned_cases = {(case["module"], case["classname"], case["name"])
                         for case in pilot["cases"] if case["tier"] == tier}
        pilot_counts = {"candidate": 0, "outside": 0}
    for failure in evidence["failures"]:
        module = failure["module"]
        owner(module)
        if (failure["kind"] not in ("failure", "error") or
                any(not isinstance(failure[key], str) or not failure[key].strip() for key in ("classname", "name"))):
            raise ValueError("invalid failed testcase")
        bucket = "candidate" if module in candidates else "outside" if module in configured else "unmapped"
        counts[bucket] += 1
        observed = failure | {"bucket": bucket}
        if pilot_counts is not None:
            planned = any((module, failure["classname"], name) in planned_cases for name in (failure["name"], ""))
            observed["locale_pilot_bucket"] = "candidate" if planned else "outside"
            pilot_counts[observed["locale_pilot_bucket"]] += 1
        failures.append(observed)

    status = "no-testcase-evidence"
    if failures:
        status = "partial" if unreadable or set(fallback) - {failure["module"] for failure in failures} else "observed"
    result = {"status": status, "counts": counts, "failures": failures,
              "unreadable_reports": unreadable, "screenshot_failure_modules": fallback}
    if failures and pilot_counts is not None:
        result["locale_pilot_counts"] = pilot_counts
    return result


def report(directory, identity):
    result = {"schema": 1, "mode": "shadow", "execution_changed": False, "provenance": identity, "lanes": {}}
    try:
        if (not re.fullmatch(r"[1-9][0-9]*", identity["GITHUB_RUN_ID"]) or
                not re.fullmatch(r"[1-9][0-9]*", identity["GITHUB_RUN_ATTEMPT"]) or
                not re.fullmatch(r"[0-9a-f]{40}", identity["GITHUB_SHA"])):
            raise ValueError("expected run identity is missing or invalid")
        impact = one_json(directory / "ci-impact-shadow", "report.json")
        validate_impact(impact, identity)
        result["impact_status"] = impact["status"]
        result["explicit_scenario"] = impact["provenance"].get("explicit_scenario", False)
        result["changed_files"] = impact["changed_files"]
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        return result | {"status": "unavailable", "reason": str(error) if isinstance(error, ValueError) else type(error).__name__}
    for tier in LANES:
        artifact = directory / ARTIFACTS[tier]
        evidence = {}
        try:
            evidence = one_json(artifact, "gradle-profiles.json")
            result["lanes"][tier] = measure_lane(tier, impact["selections"][tier], evidence, identity)
        except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
            result["lanes"][tier] = {"status": "unavailable", "reason": str(error) if isinstance(error, ValueError) else type(error).__name__}
        lane_failed = isinstance(evidence, dict) and evidence.get("lane_outcome") == "failure"
        if lane_failed or any(artifact.rglob("impact-failures.json")):
            try:
                failures = one_json(artifact, "impact-failures.json")
                comparison = compare_failures(tier, impact, failures, identity)
            except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
                comparison = {"status": "unavailable", "reason": str(error) if isinstance(error, ValueError) else type(error).__name__}
            result["lanes"][tier]["failure_comparison"] = comparison
    measured = sum(lane["status"] == "measured" for lane in result["lanes"].values())
    result["status"] = "complete" if measured == len(LANES) else "partial" if measured else "unavailable"
    return result


def render(record):
    lines = ["### Test impact: observed task work", "",
             "Advisory only. Summed task work includes parallel execution and is not wall-clock savings.",
             "Buckets use module ownership: verification aggregates can delegate to concrete test tasks.",
             "Outside-candidate work may still be required by full coverage, tablet journeys, or release smoke.",
             "Compilation, setup, shared dependencies, and all existing test decisions remain unchanged.",
             "Unit figures cover root-build profiles; standalone build-logic checks remain full.", "",
             f"Status: `{record['status']}`; impact: `{record.get('impact_status', 'unavailable')}`."]
    if record.get("explicit_scenario"):
        lines.append("Explicit offline scenario; these are not the run's changed files.")
    if record.get("reason"):
        lines.append(record["reason"])
    lines.extend(["", "| Tier | Candidate owners: executed test work (s) | Outside owners (s) | Unmapped owners (s) | Other task work (s) |",
                  "|---|---:|---:|---:|---:|"])
    for tier, lane in record["lanes"].items():
        if lane["status"] != "measured":
            lines.append(f"| {tier} | unavailable: {lane['reason']} | — | — | — |")
            continue
        work = lane["executed_test_task_seconds"]
        lines.append(f"| {tier} | {work['candidate']:.1f} | {work['outside']:.1f} | {work['unmapped']:.1f} | {lane['other_task_seconds']:.1f} |")
    lines.append("")
    for tier, lane in record["lanes"].items():
        if lane["status"] == "measured":
            executed = sum(row["outcome"] == "EXECUTED" for row in lane["test_task_observations"])
            lines.append(f"{tier}: {executed} executed test-task observations; "
                         f"{len(lane['test_task_observations']) - executed} nonexecuted; "
                         f"{len(lane['unobserved_configured_owners'])} configured owners unobserved.")
    comparisons = {tier: lane["failure_comparison"] for tier, lane in record["lanes"].items()
                   if "failure_comparison" in lane}
    if comparisons:
        lines.extend(["", "#### Observed JUnit failures", "",
                      "Outside owners expose possible omissions under module selection. "
                      "Unmapped owners and incomplete evidence need review.", "",
                      "| Tier | Evidence | Candidate | Outside | Unmapped |", "|---|---|---:|---:|---:|"])
        for tier, comparison in comparisons.items():
            counts = comparison.get("counts", {}) if comparison["status"] in ("observed", "partial") else {}
            lines.append(f"| {tier} | {comparison['status']} | {counts.get('candidate', '—')} | "
                         f"{counts.get('outside', '—')} | {counts.get('unmapped', '—')} |")
        for tier, comparison in comparisons.items():
            if "locale_pilot_counts" in comparison:
                counts = comparison["locale_pilot_counts"]
                lines.extend(["", f"Locale pilot, {tier}: {counts['candidate']} observed failures in the plan "
                              f"and {counts['outside']} outside it. Review failures outside the plan."])
    lines.extend(["", "Missing, failed, cached, empty, or stale evidence is not proof of saved test execution.", ""])
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--summary", type=Path,
                        default=Path(os.environ["GITHUB_STEP_SUMMARY"]) if os.environ.get("GITHUB_STEP_SUMMARY") else None)
    args = parser.parse_args(argv)
    identity = {key: os.environ.get(key, "") for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_SHA")}
    record = report(args.directory, identity)
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(record, indent=2) + "\n")
    if args.summary:
        with args.summary.open("a") as summary:
            summary.write(render(record))
    print(f"Impact/task work report: {record['status']}; execution unchanged.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
