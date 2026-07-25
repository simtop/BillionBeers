# 0007: Adopt Gradle dependency verification, with auto-regeneration on Dependabot branches

## Status

Accepted. Supersedes the deferral in ADR 0006 §3 — its revisit trigger ("a CI job to
regenerate + commit `verification-metadata.xml` on Dependabot branches is built first") is now met.

## Context

ADR 0006 deferred `gradle/verification-metadata.xml` for one reason: enforcement taxes every
Dependabot bump. A bumped artifact's hash is not in the ledger, CI fails, and a human has to
regenerate and commit — which defeats the grouped monthly auto-merge flow ADR 0005 built. The
control itself (a checked-in sha256 ledger of every resolved artifact, failing the build on
mismatch) was never rejected on the merits: it is the only measure that covers the *library*
supply-chain surface — artifact substitution on a mirror, a typosquatted coordinate, a tampered
JAR under a trusted version.

This ADR records the automation that removes the recurring cost, and two design decisions in it
that are easy to get wrong.

## Decision

1. **Enforce dependency verification.** `gradle/verification-metadata.xml` is committed;
   every Gradle invocation (local and CI) verifies sha256 checksums automatically from then on.
   No CI change is needed for enforcement — the file's existence is the switch.
2. **Regenerate via `make verification-metadata`.** The target runs
   `--write-verification-metadata sha256` **attached to the full CI task graph** (build, unit,
   paparazzi, lint, dependency-guard, jacoco, and the GMD instrumented lane). A bare
   `--write-verification-metadata` invocation only runs `help` and resolves almost nothing —
   the ledger only covers configurations a build actually resolves, so the regen must execute
   what CI executes. `--no-configuration-cache` because the flag is incompatible with it.
3. **A regen workflow keeps Dependabot auto-merge alive**
   (`.github/workflows/regen-verification-metadata.yml`). On Dependabot PRs it regenerates the
   ledger and pushes the diff back to the branch; CI re-runs on the new head and the existing
   `--auto` merge gate resolves normally.

## The two load-bearing choices in the workflow

### Push with a deploy key, not `GITHUB_TOKEN`

Events created with the default `GITHUB_TOKEN` do not trigger workflow runs (GitHub's recursion
guard). A regen commit pushed with it would move the PR head to a SHA that CI never runs on, the
required "CI Gate" check would stay pending forever, and `gh pr merge --auto` would hang — every
Dependabot PR stalling *silently*, which is strictly worse than failing red. A push authenticated
by a write **deploy key** triggers workflows normally. The private key lives in two secret stores
under one name (`VERIFICATION_METADATA_DEPLOY_KEY`): the **Dependabot** store, because
Dependabot-triggered runs only see Dependabot secrets, and the **Actions** store, for
`workflow_dispatch` runs.

### `pull_request`, not `pull_request_target`

The obvious pattern for pushing to a PR branch is `pull_request_target` (base-branch context,
write token). We don't need it: the deploy key provides the push, so the workflow runs in the
ordinary least-privilege `pull_request` context with a read-only token, and never mixes
base-context privileges with PR-head code execution.

### Loop termination

The bot's own push re-triggers the workflow. A guard step exits early when HEAD is already the
regen commit; even without it, the second run would produce no diff and commit nothing.

## Operational notes

- **Warm caches under-record.** A ledger generated locally misses metadata files (POMs, BOMs,
  `.module`) that a warm `~/.gradle` cache never re-downloads but a cold CI runner does —
  observed live on PR #116 (`kotlinx-coroutines-bom-1.8.0.pom`). Symptom: green locally,
  verification failure within seconds on CI, during project configuration. Fix: dispatch the
  regen workflow against the branch; it regenerates cold on Linux and pushes the completed
  ledger. These entries accumulate, so this fades after the first few regens.
- **Reading a verification failure:** "artifact is not listed" means the ledger is behind —
  regenerate. A checksum **mismatch** on an artifact already in the ledger is the alarm this
  control exists for — do not regenerate over it; verify the artifact independently first.
- **Platform-specific artifacts:** a ledger generated on macOS lacks Linux-only artifacts (e.g.
  `aapt2 …:linux`). The `workflow_dispatch` trigger exists exactly for this — run the workflow
  against a branch and it appends what Linux resolves. Symmetrically, after a bump lands, the
  first local macOS build may need a local `make verification-metadata` for the osx twins.
- **Escape hatch:** `./gradlew --dependency-verification off …` (or `lenient`) bypasses
  enforcement for one invocation when debugging.
- **Rebases:** a manual `@dependabot rebase` drops the bot's ledger commit; the resulting
  synchronize event re-fires the regen workflow, so it self-heals.
- **Ledger hygiene:** `--write-verification-metadata` appends; entries for dropped versions are
  not pruned automatically. Occasionally regenerate from an empty file to compact it.
- **Tasks outside the CI graph** (e.g. `make benchmark-check`) may resolve artifacts the ledger
  misses. Fix is always the same: `make verification-metadata` (extend the target's task list if
  a lane becomes permanent).

## Cost accepted

- The regen workflow runs a full CI-sized task graph on each Dependabot PR head (monthly,
  grouped) — roughly doubling CI cost for those PRs. That is the price of a complete ledger;
  resolution cannot be faked without executing the resolving tasks.
- A ~1000+ line generated XML file lives in the repo and churns with every bump. Reviewers should
  treat its diffs as machine output: sanity-check *which* coordinates changed, not the hashes.
- Verification trusts the ledger's first write (trust-on-first-resolution). It defends against
  the bytes changing *after* adoption, not against a compromise that predates the ledger.
