#!/usr/bin/env python3
"""Render one current, step-aware CI diagnosis. Inputs are data, never commands to execute."""

from __future__ import annotations

import argparse
import html
import importlib.util
import json
import re
import sys
from pathlib import Path


def load_script(name: str):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


SUMMARY = load_script("summarize-test-failures")
MARKER = "<!-- billionbeers-ci:diagnosis -->"
BAD = {"failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale"}
# Step names, not lane names: the unit lane also owns architecture and coverage checks.
# command, relevant artifacts, optional JUnit mode
STEP_RULES = {
    "Run Unit Tests": ("make test", ("unit-test-reports",), "unit"),
    "Coverage floor check": ("make coverage-check", ("unit-coverage-reports",), None),
    "Generate test-tier ownership inventory": ("make test-tier-inventory", ("unit-test-reports",), None),
    "Run Konsist Architecture Rules": ("make konsist", ("unit-test-reports",), "unit"),
    "Check data-layer classpath boundary": ("make check-data-layer-boundary", (), None),
    "Check resolved architecture graph policy": ("make architecture-policy", (), None),
    "Verify Screenshot Tests": ("make screenshot-verify", ("screenshot-test-results", "screenshot-failures"), "paparazzi"),
    # Compatibility with runs predating ordinary failure propagation in the screenshot step.
    "Signal Failure": ("make screenshot-verify", ("screenshot-test-results", "screenshot-failures"), "paparazzi"),
    "Run instrumented tests on the managed devices": ("make ui-test-managed-ci", ("instrumented-test-reports", "managed-device-gradle-profiles"), "managed-device"),
    "Run minified release confidence smoke tests": ("make release-smoke", ("instrumented-test-reports", "release-confidence-artifacts", "managed-device-gradle-profiles"), "managed-device"),
    "Compile Apple targets": ("make ios-compile", ("native-test-reports",), None),
    "Link Apple frameworks": ("make ios-framework", ("native-test-reports",), None),
    "Run Apple simulator tests": ("make ios-test", ("native-test-reports",), None),
    "Verify native test reports": ("make ios-test", ("native-test-reports",), None),
    "Run Spotless Check": ("make format", (), None),
    "Run Detekt": ("make lint", (), None),
    "Run Android Lint": ("make android-lint", ("android-lint-reports",), None),
    "Run Dependency Guard": ("make dependency-guard", (), None),
    "Test CI reporting": ("make ci-report-test", (), None),
    "Check repository Markdown links and generated indexes": ("make docs-check", (), None),
    "Verify the README version table matches the catalog": ("make update-docs", (), None),
}
EMPTY_RULE = (None, (), None)


def failed_steps(job: dict) -> list[dict]:
    steps = [step for step in job.get("steps", []) if step.get("conclusion") in BAD]
    if any(step.get("name") == "Verify Screenshot Tests" for step in steps):
        steps = [step for step in steps if step.get("name") != "Signal Failure"]
    return steps


def actionable_jobs(data: dict) -> list[dict]:
    if data.get("conclusion") in {"success", "skipped"}:
        return []
    jobs = [job for job in data.get("jobs", []) if job.get("conclusion") in BAD]
    if any(job.get("name") != "CI Gate" for job in jobs):
        jobs = [job for job in jobs if job.get("name") != "CI Gate"]
    return jobs


def error_excerpt(log: str) -> str:
    """Prefer actionable error lines over boilerplate 'process exited' annotations."""
    lines = [re.sub(r"^\d{4}-\d\d-\d\dT\S+\s*", "", line).strip() for line in log.splitlines()]
    for line in lines:
        match = re.search(r"Line coverage [\d.]+% is below the floor [\d.]+%\.", line)
        if match:
            return match.group(0)
    for index, line in enumerate(lines):
        if line == "* What went wrong:":
            detail = []
            for following in lines[index + 1:index + 9]:
                if following.startswith("* "):
                    break
                if following:
                    detail.append(following)
            if detail:
                return " ".join(detail)[:700]
    for line in lines:
        if line.startswith(("e: ", "##[error]", "Error:")) and "Process completed with exit code" not in line:
            return line.removeprefix("##[error]")[:700]
    return ""


def text(value: str) -> str:
    return html.escape(" ".join(value.split())).replace("@", "&#64;")


def render(data: dict, run_url: str, repository: str, run_id: str) -> str:
    jobs = actionable_jobs(data)
    status = data.get("status", "completed")
    conclusion = data.get("conclusion")
    if conclusion == "success":
        title = "✅ CI passed"
    elif conclusion == "skipped":
        title = "⏭️ CI skipped"
    elif jobs or conclusion in BAD:
        title = "❌ CI needs attention"
    else:
        title = "⏳ CI running" if run_id else "⏳ Awaiting CI"
    generation = {"sha": data.get("head_sha", ""), "run_id": run_id, "attempt": data.get("run_attempt", 1)}
    lines = [MARKER, f"<!-- billionbeers-ci:state {json.dumps(generation, sort_keys=True)} -->", f"## {title}", ""]
    # Keep the fallback link above bounded detail, so truncation never removes the way to full logs.
    lines += [f"[Open the CI run]({run_url})" if run_id else f"[Open PR checks]({run_url})", ""]
    if data.get("head_sha"):
        lines += [f"Commit {SUMMARY.inline_code(data['head_sha'][:7])} · attempt {data.get('run_attempt', 1)}", ""]
    if conclusion in {"success", "skipped"}:
        lines += ["Previous failure details have been cleared." if conclusion == "success" else "This run was skipped, not passed. Previous failure details have been cleared.", ""]
    elif not jobs:
        if status == "completed":
            lines += [f"Run conclusion: **{text(conclusion or 'unknown')}**. No actionable job details are available; inspect the run log.", ""]
        else:
            lines += ["Results pending for the current commit/attempt. Previous failure details have been cleared.", ""]

    if len(jobs) > 1:
        lines += ["**Failed jobs:** " + " · ".join(SUMMARY.inline_code(job.get("name", "Unknown job")) for job in jobs), ""]
    for job in jobs:
        steps = failed_steps(job)
        lines += [f"### {text(job.get('name', 'Unknown job'))}", ""]
        if job.get("carried_forward"):
            lines += [f"Unresolved result from attempt {job.get('run_attempt', '?')}; this job was not rerun. Open its original job log for details.", ""]
        if job.get("conclusion") in {"cancelled", "timed_out"}:
            lines += [f"**{text(job['conclusion'])}** — not evidence of a test assertion failure. Inspect the job log and rerun the workflow as appropriate.", ""]
        for step in steps:
            name = step.get("name", "Unknown step")
            lines += [f"**Failed step:** {text(name)}", ""]
            command, _, mode = STEP_RULES.get(name, EMPTY_RULE)
            if command:
                lines += [f"**Reproduce:** {SUMMARY.inline_code(command)}", ""]
            details = job.get("diagnostics", {}).get(name, {})
            if details.get("error"):
                lines += [SUMMARY.inline_code(details["error"]), ""]
            failures = [SUMMARY.Failure(**failure) for failure in details.get("failures", [])]
            if failures:
                lines += ["**Failed tests:**", "", *SUMMARY.failure_lines(failures[:8], include_detail=True), ""]
                if len(failures) > 8:
                    lines += [f"_{len(failures) - 8} more failures in the report._", ""]
            elif mode and not job.get("carried_forward") and job.get("conclusion") == "failure":
                lines += ["No failed testcase was found in the available reports. The failed step's log is authoritative; missing XML alone does not establish the cause.", ""]
            if details.get("unreadable"):
                lines += ["_Some test reports could not be parsed; the available results may be incomplete._", ""]
            if details.get("unavailable"):
                lines += ["_Some diagnostic evidence is unavailable; use the job log below._", ""]
        if not steps:
            lines += [f"Job conclusion: **{text(job.get('conclusion', 'unknown'))}**. Failed-step details are unavailable.", ""]
        url = job.get("html_url", run_url)
        lines += [f"[Open failing job and log]({url})", ""]
        names = {name for step in steps for name in STEP_RULES.get(step.get("name"), EMPTY_RULE)[1]}
        artifacts = [artifact for artifact in job.get("artifacts", []) if not artifact.get("expired") and artifact.get("name") in names]
        if artifacts:
            links = [f"[{artifact['name']}](https://github.com/{repository}/actions/runs/{run_id}/artifacts/{artifact['id']})" for artifact in artifacts]
            lines += ["**Evidence:** " + " · ".join(links), ""]
    if jobs and any(job.get("name") == "CI Gate" and job.get("conclusion") in BAD for job in data.get("jobs", [])) and not any(job.get("name") == "CI Gate" for job in jobs):
        lines += ["_CI Gate also failed because a required job did not pass._", ""]
    if data.get("incomplete"):
        lines += ["_Live reporting paused or evidence is incomplete. Open the CI run for current status; the completion event will reconcile this comment._", ""]
    elif status != "completed" and jobs:
        lines += ["_CI is still running. This comment updates as other jobs finish._", ""]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jobs", type=Path)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(render(json.loads(args.jobs.read_text()), args.run_url, args.repository, args.run_id), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
