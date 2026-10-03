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
    "Run Web browser and static distribution verification": (
        "make web-verify",
        ("web-static-distribution", "web-smoke-evidence", "web-release-confidence"),
        None,
    ),
    "Compile Apple targets": ("make ios-compile", ("native-test-reports",), None),
    "Link Apple frameworks": ("make ios-framework", ("native-test-reports",), None),
    "Run Apple simulator tests": ("make ios-test", ("native-test-reports",), None),
    "Verify native test reports": ("make ios-test", ("native-test-reports",), None),
    "Create and verify iOS simulator evidence": (
        "make ios-simulator-evidence",
        ("ios-simulator-confidence", "ios-host-build", "native-test-reports"),
        None,
    ),
    "Run Spotless Check": ("make format", (), None),
    "Run Detekt": ("make lint", (), None),
    "Run Android Lint": ("make android-lint", ("android-lint-reports",), None),
    "Run Dependency Guard": ("make dependency-guard", (), None),
    "Test CI reporting": ("make ci-report-test", (), None),
    "Check repository Markdown links and generated indexes": ("make docs-check", (), None),
    "Verify the README version table matches the catalog": ("make update-docs", (), None),
}
EMPTY_RULE = (None, (), None)
PREPARATION_ACTIONS = {
    "missing_authoritative_result": "No current validated write result is available. Dispatch write mode for this exact Dependabot head and wait for its result before relying on CI.",
    "operational_termination": "A verified runner shutdown interrupted preparation. The trusted collector only retries an eligible first-attempt in-repository Dependabot pull request after its strict current-head checks; manual writes and bare exit codes are not retry evidence.",
    "manual_investigation_required": "A verified runner shutdown interrupted preparation, but strict automatic recovery conditions were not met. Review the completed attempt logs and resource evidence manually before proceeding; automatic recovery is limited to one eligible first attempt.",
    "coordinate_change": "Review the added/removed coordinates in the preparation artifact. Accept only its exact head SHA and graph digest through the manual write-mode dispatch.",
    "formatting": "Preparation could not apply/check the formatter output; inspect the preparation log before dispatching Format Fix.",
    "resolution_conflict": "Diagnose production/test classpaths with dependencyInsight and fix the demonstrated version constraints.",
    "checksum_mismatch": "ALARM: verify the recorded artifact independently. Do not accept or regenerate over changed bytes.",
    "verification_policy_change": "Review verification-policy changes by hand; preparation must not weaken the ledger policy.",
    "verification_failure": "Inspect the dependency-verification report; distinguish an unlisted artifact from a changed checksum before recovery.",
    "evidence_unavailable": "The authoritative result artifact or run evidence is missing, expired, malformed, or temporarily unavailable. Redispatch write mode if it does not appear after a short wait; this evidence never establishes success by itself.",
}


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
    preparation = data.get("preparation") or {}
    preparation_bad = preparation.get("conclusion") in BAD
    preparation_pending = bool(preparation) and (preparation.get("status") != "completed" or preparation.get("category") == "pending")
    preparation_category = preparation.get("category")
    status = data.get("status", "completed")
    conclusion = data.get("conclusion")
    if preparation_category == "manual_investigation_required":
        title = "⚠️ Preparation shutdown needs manual investigation"
    elif preparation_category == "operational_termination":
        title = "⚠️ Runner shutdown interrupted preparation"
    elif preparation_bad:
        title = "❌ Dependency preparation needs attention"
    elif conclusion == "success" and preparation_pending:
        title = "⏳ CI passed; dependency preparation pending"
    elif conclusion == "success":
        title = "✅ CI passed"
    elif conclusion == "skipped":
        title = "⏭️ CI skipped"
    elif jobs or conclusion in BAD:
        title = "❌ CI needs attention"
    else:
        title = "⏳ CI running" if run_id else "⏳ Awaiting CI"
    generation = {"sha": data.get("head_sha", ""), "run_id": run_id, "attempt": data.get("run_attempt", 1),
                  "preparation_id": preparation.get("id"), "preparation_attempt": preparation.get("run_attempt")}
    lines = [MARKER, f"<!-- billionbeers-ci:state {json.dumps(generation, sort_keys=True)} -->", f"## {title}", ""]
    # Keep the fallback link above bounded detail, so truncation never removes the way to full logs.
    lines += [f"[Open the CI run]({run_url})" if run_id else f"[Open PR checks]({run_url})", ""]
    if data.get("head_sha"):
        lines += [f"Commit {SUMMARY.inline_code(data['head_sha'][:7])} · attempt {data.get('run_attempt', 1)}", ""]
    if preparation:
        lines += ["### Dependency preparation", ""]
        if preparation.get("id"):
            lines += [f"[Open preparation run]({preparation['html_url']})", ""]
        else:
            lines += ["No authoritative write run is associated with this exact head.", ""]
        result = preparation.get("result") or {}
        if preparation.get("authoritative_valid") and result:
            lines += [f"Validated write · source `{result['source_sha'][:7]}` → output `"
                      f"{result['output_sha'][:7]}` · graph `{result['graph_digest'][:12]}`", ""]
        if preparation_bad:
            category = preparation.get("category", "unknown")
            lines += [f"**Result:** {text(category)}", "",
                      PREPARATION_ACTIONS.get(category, "Inspect the first preparation failure. No generated files were pushed."), ""]
            if preparation.get("error"):
                lines += [SUMMARY.inline_code(preparation["error"]), ""]
            for artifact in preparation.get("artifacts", []):
                lines += [f"[Download preparation evidence](https://github.com/{repository}/actions/runs/{preparation['id']}/artifacts/{artifact['id']})", ""]
            lines += ["CI verification errors can follow from the unprepared ledger; skipped lanes are not additional test defects.", ""]
        elif preparation.get("status") != "completed":
            lines += ["Preparation is still running. Its result has not been established.", ""]
        else:
            lines += ["Preparation completed; strict CI still determines merge eligibility.", ""]
        stale_runs = preparation.get("stale_runs", [])
        if stale_runs:
            lines += ["**Stale or superseded preparation runs:**", ""]
            lines += [f"- [{item.get('run_number', item.get('id', 'run'))} · "
                      f"{text(item.get('stale_reason', 'stale'))}]({item.get('html_url', '')})"
                      for item in stale_runs[:5]]
            lines += [""]
    if data.get("mergeable_state") == "behind":
        lines += ["**Branch is behind master.** Strict protection requires an update and fresh CI even if the previous run is green. Rebase the Dependabot branch; preparation will regenerate its derived files.", ""]
    if conclusion == "success" and preparation_bad:
        lines += ["CI passed, but dependency preparation remains unresolved.", ""]
    elif conclusion in {"success", "skipped"}:
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
            verification_error = "Dependency verification failed" in job.get("diagnostics", {}).get(name, {}).get("error", "")
            if name == "Run Spotless Check" and verification_error:
                lines += ["Gradle failed verification before formatting could run. Inspect dependency preparation; Format Fix hits the same blocker.", ""]
            elif command:
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
