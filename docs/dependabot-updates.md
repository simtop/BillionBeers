# Monthly dependency updates

Dependabot checks Gradle and Actions dependencies once a month. Minor/patch updates are grouped
by stack; major upgrades stay separately reviewed. The ten-PR limit is retained so one monthly
batch is not restricted to three groups. Actions updates do not need Gradle preparation.

## Preparation and merge eligibility

The preparation workflow checks out an immutable PR head, snapshots the verification ledger,
resolves the proposed runtime baseline, checks its coordinate delta, and applies the explicitly
versioned ktfmt formatter. Four cold lanes then run serially: Linux/JVM/Android-host, Web Wasm,
managed-device debug and release smoke, and Apple native/framework/iOS simulator closure. Each lane
uploads its own ledger, resource log, and immutable head/run/source identity. The final job verifies
all lane evidence and prepared-input digests, checks every lane against the original checksums and
policy, and unions platform-only artifacts. Shared artifacts must have matching accepted
checksums. Only then can the final job push one commit containing tracked formatting, the union
ledger, the permitted baseline, and README changes. Platform jobs have read access only. A head
change during the final write rejects it.

Before merging each Gradle update:

1. Rebase the PR onto the current `master` and review the exact dependency graph and any approved
   coordinate delta.
2. Verify all four preparation lanes and confirm preparation succeeded for the exact current PR
   head.
3. Wait for fresh green required CI on that same head, then merge the PR.
4. Rebase/update the next Dependabot PR before repeating this checklist.

The existing CI diagnosis includes preparation results and links to its evidence. A Gradle
verification failure before Spotless starts is not diagnosed as a formatting defect. CI Gate also
requires successful preparation on the current Dependabot head when the PR changes Gradle inputs.
It waits for pending preparation and its single eligible shutdown retry on the exact head tested
by CI, with a 350-minute wait inside GitHub's six-hour job maximum. The four platform lanes have
individual 35–90 minute limits and the merge job has a 15-minute limit. Queue time and a full retry
can exceed the CI Gate's observer bound. Other failures stop the gate. Ordinary strict CI remains
required after the preparation commit.

Write runs identify their intent in the immutable Actions run title and publish a small result
artifact before pushing. The result binds the open same-repository Dependabot PR, source and output
SHA, run and attempt, graph digest, and all four successful lane identities. CI Gate accepts only
the newest validated write result for the live PR head; reference/candidate experiments do not
count. A later queued, failed, or evidence-missing write blocks a previously green Gate. The trusted
completion collector reruns only CI Gate when its recorded preparation generation is outdated, or
when its only failure is the exact missing-preparation check. Other failed CI jobs are left untouched.
A successful write that pushes a generated commit gets normal fresh CI on that new head.

Legacy write runs without the immutable write title or authoritative result artifact cannot establish
freshness. Dispatch **Regenerate Verification Metadata** again in `write` mode on the current PR
branch; if CI Gate had already terminated, the collector refreshes only that Gate after validating
the new write intent. Dispatch again as well when repository concurrency superseded a pending run.
Hosted preparation still uses cold Linux, Web, managed-device, and Apple runners; queue time can
exceed the Gate's wait bound, and hosted lane execution remains the evidence for platform closure.

A trusted default-branch completion collector can retry a preparation run once, only when both
the actual runner-shutdown error and exit 143 are present, the failed step is a platform resolver, no
build/policy rejection is present, the open same-repository PR is still authored by Dependabot,
and neither a newer run nor a changed head supersedes it. It never executes PR code or accepts a
dependency graph. Manual dispatches, cancellations and second attempts are not automatically retried.
Preparation records elapsed time and samples memory, disk and process usage every 30 seconds using
Linux/macOS-compatible probes; runner shutdown can still prevent the evidence upload, so the
completed job log is authoritative. A repository-wide concurrency group serializes preparations,
but GitHub keeps only one pending run and a newer request may supersede it. Dispatch a
superseded ref again after the current preparation completes.

The platform matrix stops queued lanes after a lane fails. Failed runs can therefore have less
all-platform diagnostic coverage because companions that had not started are cancelled. Recovery
uses the completed run's job logs and matrix state; there is no live shutdown observer or promise
that other runners will be cancelled immediately. The one retry remains limited to a single
verified shutdown, with only unstarted cancelled companions accepted as fail-fast fallout.
The lane supervisor records TERM/INT, forwards it to its isolated process group, and uses bounded
grace periods before force-killing remaining lane or probe processes. A verified shutdown on a
later attempt or one with ambiguous matrix failures requires manual investigation; force-kill can
still prevent evidence artifacts from uploading, so the completed job log remains authoritative.

## Accepting an intentional coordinate change

Version-only runtime graph changes are automatically re-baselined. Added or removed coordinates
stop preparation for review, even on minor updates.

1. Open the successful **Dependency Graph Review** run for the current PR head. Its summary shows
   added and removed coordinates. The run artifact contains the complete old and proposed
   versioned baselines, exact source SHA, graph digest and review attempt. Review why each new
   dependency is needed and whether removals are intended.
2. Dispatch **Approve Dependency Graph** from the default branch, using the source run ID and the
   exact attempt you inspected. Leave `approve_graph` false to inspect the evidence without
   dispatching a writer. To approve, set it to true; the triggering actor must currently have
   repository admin or maintain permission. For example:

   ```bash
   gh workflow run approve-dependency-graph.yml --ref master \
     -f source_run_id='<review-run-id>' -f source_run_attempt='<review-attempt>' \
     -f approve_graph=true
   ```

3. The handoff validates the source workflow, attempt, artifact, repository, open PR, branch, head
   and digest, then records a one-time receipt before dispatch. The existing writer recomputes the
   reviewed head and digest before running its four platform lanes. Any changed head or graph
   rejects the approval. The handoff follows that exact writer run and waits for required CI on its
   validated output SHA; it reports both run links and never merges.
4. If the source review run is rerun, use its new attempt explicitly and review its graph again.
   An approval receipt consumes one source run attempt, so it cannot be dispatched twice. The
   writer's manual SHA/digest inputs remain available for deliberate fallback use.

The local fallback remains `make dependency-guard-baseline-unverified` after deliberate review;
the exact run handoff normally removes the need to copy SHA/digest values or edit and push a
baseline manually.

## Green but behind master

Strict branch protection requires the latest base, not merely green checks against an older one.
When one update merges, another previously green PR may need a rebase and fresh validation.
The diagnosis reports `behind` explicitly. Request a Dependabot rebase using its PR controls,
then let preparation and CI finish; re-review any coordinate approval because it binds the old SHA.

Keep strict protection. Reusing an old green verdict across a changed base would miss interactions
between updates. The repository is personal-account-owned, so GitHub's native merge queue is not
available. [ADR 0005](adr/0005-dependabot-over-renovate.md) keeps queue activation deferred until
organization ownership makes it available; there is no custom merge sequencer. Cohesive groups
reduce independent rebase cycles without coupling every update into one all-or-nothing PR.

## Comparing smaller metadata writers

Dispatch **Compare Verification Metadata Writers** on a chosen immutable branch revision. It runs
the reference and candidate sequentially through the same cold Linux, Web, managed-device, and
Apple lanes, downloads their merged ledgers, and compares verification policy, every artifact, and
all accepted checksums. Candidate mode changes only managed-device execution to explicit APK/app
assembly; it does not approve a new dependency graph. The comparison workflow has no deploy key and
never pushes.

For a workflow not yet available on the default branch, dispatch the existing regeneration workflow
twice on the same branch revision, once with `mode=reference` and once with `mode=candidate`, then
download its two merged ledger artifacts and run:

```bash
python3 .github/scripts/check-verification-metadata-update.py --require-equivalent \
  reference/gradle/verification-metadata.xml candidate/gradle/verification-metadata.xml
```

A green comparison proves equivalence for that immutable SHA, not a performance gain or coverage of
every future dependency. [ADR 0007](adr/0007-gradle-dependency-verification.md) owns the task
coverage and union rules.
