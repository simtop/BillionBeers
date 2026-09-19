# CI diagnostics

`CI PR Diagnosis` maintains one `<!-- billionbeers-ci:diagnosis -->` bot comment per pull request.
It starts when `BillionBeers CI` enters `in_progress` and reconciles again on `completed`, including
successful runs. It executes only reporting code from the default branch, never the PR checkout,
its build scripts, or code from an artifact. Fork PRs do not receive comment-write permissions.
Changes to this trusted reporter take effect after merging into the default branch.

## Comment lifecycle

- The first actionable failure creates the comment. As other jobs finish, the **same comment**
  updates to include their failures; it does not post a new message for each lane.
- A new commit or run attempt replaces obsolete failure details with a pending state. This happens
  when the active reporter observes the change, or when the new run starts—not necessarily at the
  instant someone pushes or requests a rerun.
- A completed partial rerun retains genuinely unresolved jobs that were not rerun, explicitly
  labelled with their earlier attempt. Retried jobs replace their previous executions.
- Success replaces the failure report with a compact success message. A skipped run is labelled
  skipped, not passed. Cancellation and timeout are not presented as failed test assertions.
- A PR that never needed a failure comment does not receive running or success-only comments.
  Unchanged bodies are not patched, and only this workflow's GitHub Actions bot marker is reused.

Writers are serialized by repository and PR, without cancelling the active reporter. Events are
wake-ups: a delayed old completion reconciles the **current** PR head and latest original CI run,
not its event payload. Rerunning an older run on the same commit does not supersede a newer run.
The current head/run/attempt is checked again immediately before writing and after publication.
GitHub does not provide an atomic comment-write/head comparison; a head change during a write is
corrected by subsequent reconciliation rather than a strict zero-staleness guarantee.

## Relevant evidence

The report groups failures by job and identifies the failed **step**. For example, a coverage-floor
failure in the Unit Tests lane recommends `make coverage-check` and links coverage reports; an
Apple native compile, framework-link or simulator-test failure recommends the corresponding `make
ios-*` target and links native test reports when available. It never creates instrumented or
screenshot failure sections. `CI Gate` is shown as a consequence
when another job failed, but a standalone gate failure remains visible.

Only artifacts relevant to failed steps are linked. Available JUnit reports supply bounded exact
class/test identifiers and failure details. Build/setup errors and missing reports link to the
failed job log without guessing a test assertion or claiming that comparison images exist.
Recognizable error excerpts come from the failed step's time window in its job log. The CI run
still provides access to all logs and artifacts; artifacts currently expire after seven days.

Artifacts have no run-attempt field. A report is accepted only when its creation timestamp falls
inside its known producer job's execution window. Earlier-attempt carried-forward failures retain
their original job link, not recycled exact diagnostics. Ambiguous, expired, oversized, malformed,
or unavailable evidence cannot hide the known job failure. Archives are bounded and only JUnit
XML is extracted; absolute paths, traversal, and symlinks are rejected.

Screenshot Step Summaries retain the recording command for reviewed visual changes:

```bash
gh workflow run record_screenshots.yml --ref <branch> -f modules='<affected modules>'
```

The PR comment recommends verification first, not blindly recording new goldens to fix any failure.

## Runtime and verification

GitHub Actions has no supported per-job completion trigger for this reporter. It polls approximately
every 30 seconds, reuses downloaded evidence, and watches for at most 55 minutes per invocation
(the reporting job has a 60-minute timeout). This occupies one extra hosted runner while watching,
including sleep time; runner-minute charges, where applicable, follow that elapsed time. If the
window expires, the comment marks live reporting as paused, and the completion event performs
final reconciliation. One additional poll allows final job/artifact metadata to settle.

Jobs, runs, artifacts, and comments are paginated. Partial-rerun reconciliation assumes the current
CI job display names are unique; a future matrix must keep its expanded names unique too.

Run `make ci-report-test` for parser, renderer, publisher, and mocked lifecycle regressions. The
always-run formatting lane runs the same target. These tests do not post real comments. Live
workflow event/permission behavior must be checked after the trusted code reaches the default
branch; the reporter remains best-effort and cannot change the original `CI Gate` result.
