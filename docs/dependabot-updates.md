# Monthly dependency updates

Dependabot checks Gradle and Actions dependencies once a month. Minor/patch updates are grouped
by stack; major upgrades stay separately reviewed. The ten-PR limit is retained so one monthly
batch is not restricted to three groups. Actions updates do not need Gradle preparation.

## Preparation and merge eligibility

The preparation workflow checks out an immutable PR head, snapshots the verification ledger,
resolves the proposed runtime baseline, checks its coordinate delta, and applies the explicitly
versioned ktfmt formatter. It then executes the reference metadata graph and rejects changed
checksums or verification policy before pushing one commit containing the tracked formatting,
ledger, permitted baseline and README changes. A head change during the run rejects the push.

The existing CI diagnosis includes preparation results and links to its evidence. A Gradle
verification failure before Spotless starts is not diagnosed as a formatting defect. CI Gate also
requires successful preparation on the current Dependabot head when the PR changes Gradle inputs.
It waits for pending preparation and its single eligible shutdown retry on the exact head tested
by CI, with a 180-minute bound covering two 90-minute attempts. This avoids a manual CI rerun when
successful preparation makes no commit but finishes after ordinary CI. Other failures stop the gate.
Ordinary strict CI remains required after the preparation commit.

A trusted default-branch completion collector can retry a preparation run once, only when both
the actual runner-shutdown error and exit 143 are present, the failed step is regeneration, no
build/policy rejection is present, the open same-repository PR is still authored by Dependabot,
and neither a newer run nor a changed head supersedes it. It never executes PR code or accepts a
dependency graph. Manual dispatches, cancellations and second attempts are not automatically retried.
Preparation records elapsed time and samples memory, disk and process usage every 30 seconds;
runner shutdown can still prevent the evidence upload, so the completed job log is authoritative.

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
the reference and candidate on separate cold Linux runners, downloads their ledgers, and compares
verification policy, every artifact and all accepted checksums. It has no deploy key and never pushes.
The comparison must pass before changing the production writer alias.

For a workflow not yet available on the default branch, dispatch the existing regeneration workflow
twice on the same branch revision, once with `mode=reference` and once with `mode=candidate`, then
download its two ledger artifacts and run:

```bash
python3 .github/scripts/check-verification-metadata-update.py --require-equivalent \
  reference/gradle/verification-metadata.xml candidate/gradle/verification-metadata.xml
```

Linux equivalence is necessary but not sufficient for platform coverage. The hosted writer does not
execute Darwin-only native tasks. Before promotion, verify prepared dependency heads in the required
Apple and Web CI lanes and explicitly resolve any missing platform artifacts. Measure elapsed time
and runner work; a single green comparison proves coverage for its SHA, not a performance gain or
coverage of every future dependency. The production writer stays the reference until that evidence
exists. [ADR 0007](adr/0007-gradle-dependency-verification.md) owns this requirement.
