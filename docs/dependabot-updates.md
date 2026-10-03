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
can exceed the CI Gate's observer bound; if the gate times out before preparation succeeds, rerun
CI after preparation finishes. Other failures stop the gate. Ordinary strict CI remains required
after the preparation commit.

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

## Accepting an intentional coordinate change

Version-only runtime graph changes are automatically re-baselined. Added or removed coordinates
stop preparation for review, even on minor updates.

1. Open the failed preparation run and download its `dependency-preparation-<run>-<attempt>`
   artifact. Read `graph-review.json`: it contains the complete old and proposed versioned
   baselines, added/removed coordinates, immutable head SHA and graph digest.
2. Review why the new dependencies are needed and whether removals are intended. An approval
   accepts that graph; it does not override artifact checksum or build/test failures.
3. Dispatch **Regenerate Verification Metadata** on that Dependabot branch, in `write` mode,
   setting `approved_head_sha` and `approved_graph_digest` to the reviewed values. For example:

   ```bash
   gh workflow run regen-verification-metadata.yml --ref '<dependabot-branch>' \
     -f mode=write -f approved_head_sha='<full-reviewed-head-sha>' \
     -f approved_graph_digest='<reviewed-graph-digest>'
   ```

4. The workflow recomputes the proposed graph. If its SHA or full graph differs, approval is
   rejected. If it matches, the same guarded preparation runs and pushes its derived files only
   on success. CI reruns on that new head. Write mode refuses the default branch.

The local fallback remains `make dependency-guard-baseline-unverified` after deliberate review;
the SHA/digest dispatch normally removes the need to edit and push a baseline manually.

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
