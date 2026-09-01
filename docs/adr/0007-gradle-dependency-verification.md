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
   `--write-verification-metadata sha256` **attached to the full root-build CI task graph**: build,
   Android and pure-JVM unit tests, Konsist and resolved architecture checks, Paparazzi, lint,
   dependency-guard, Jacoco, the GMD debug lane, and the standalone minified-app smoke test. A bare
   `--write-verification-metadata` invocation only runs `help` and resolves almost nothing —
   the ledger only covers configurations a build actually resolves, so the regen must execute
   what CI executes. `--no-configuration-cache` because the flag is incompatible with it.
3. **A narrowly triggered regen workflow keeps Dependabot auto-merge alive**
   (`.github/workflows/regen-verification-metadata.yml`). On Dependabot PRs that change a Gradle
   dependency input, it regenerates the ledger and pushes the diff back to the branch; CI re-runs
   on the new head and the existing `--auto` merge gate resolves normally. GitHub Actions-only
   bumps do not resolve Gradle artifacts and therefore do not pay for this workflow. A successful
   run also executes `make update-docs` and includes the generated README version table in the same
   commit, so a catalog bump does not need a second formatting-fix commit.
4. **The workflow re-baselines dependency-guard first, and only for version-only drift.** See
   below — this is what makes (3) actually complete on a real bump.

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

`format_fix.yml` shares this key for the same reason — it commits directly to the branch it runs
on, so a `GITHUB_TOKEN` push there would strand an open PR's head on a SHA CI never runs. The name
is now narrower than its use; treat it as "the bot's write key", not one workflow's.

### `pull_request`, not `pull_request_target`

The obvious pattern for pushing to a PR branch is `pull_request_target` (base-branch context,
write token). We don't need it: the deploy key provides the push, so the workflow runs in the
ordinary least-privilege `pull_request` context with a read-only token, and never mixes
base-context privileges with PR-head code execution.

### Re-baseline dependency-guard before regenerating, and only when drift is version-only

The ledger is not the only file a bump invalidates. `:app`'s release runtime classpath usually
shifts too, so dependency-guard's committed baseline (`app/dependencies/releaseRuntimeClasspath.txt`)
goes stale on the same commit. Because `make verification-metadata` runs `:app:dependencyGuard` —
which *fails* on baseline drift — the regen step exited non-zero, its commit-and-push step was
skipped, and **neither** file was ever written. The workflow built to unblock Dependabot could not
complete on any bump that moved the shipped graph. Observed as a hard deadlock on PRs #123, #124
and #127 (Kotlin 2.4.0→2.4.10, the Metro group, Material 1.13.0→1.14.0), each stuck red with no
path forward: every manual fix a human could reach for — `spotlessApply`, a re-dispatched regen —
was itself a Gradle invocation, and so failed on the same unlisted checksums.

So the workflow runs `:app:dependencyGuardBaseline` **before** the regen, under
`--write-verification-metadata` so the bump's unlisted artifacts don't block the resolution needed
to list them. `make verification-metadata` afterwards remains the authority on ledger content —
it still runs the full CI task graph, with `:app:dependencyGuard` now passing.

Doing that unconditionally would quietly disable dependency-guard for exactly the PRs it guards,
since patch/minor Dependabot PRs auto-merge unread. The workflow therefore compares the
**version-stripped coordinate sets** either side of the re-baseline:

- **version-only drift** (same coordinates, new versions) is what a bump legitimately is, and is
  already under review as the bump itself → re-baseline and continue;
- **a coordinate added or removed** is a new or dropped transitive dependency — the runtime-binary-
  incompatibility signal the guard exists for → fail red, auto-merge never resolves, a human
  reviews the delta and re-baselines by hand.

  That hand re-baseline needs the escape hatch, not the Makefile target: on an un-regenerated
  branch the bump's artifacts aren't in the ledger, so `make dependency-guard-baseline` fails on
  verification like everything else. Use
  `./gradlew --dependency-verification off :app:dependencyGuardBaseline`, commit, push — the
  coordinate sets then match and the workflow finishes the ledger itself.

### Complete task coverage is explicit

The reference writer names CI tasks even when another task currently resolves the same artifacts.
That includes `:snapshot-processor:test`, `checkDataLayerClasspathBoundary`,
`verifyArchitectureGraph`, and `:app-release-smoke:atdApi35ReleaseSmokeAndroidTest`. The redundancy
is deliberate: if task internals diverge later, the ledger still follows the CI contract instead of
silently relying on incidental overlap.

The convention-plugin tests are the one apparent omission. CI runs them through
`./gradlew -p build-logic :convention:test`, which is a separate Gradle build with no
`build-logic/gradle/verification-metadata.xml`; the root ledger does not govern that invocation.
Executing it from this target would add runtime without adding entries to the root ledger. The
included build is still resolved while configuring the root build, so the convention plugins needed
by root tasks remain covered.

### Resolution-only writer is an experiment, not an assumption

`verification-metadata-reference` retains actual GMD and release-smoke execution and remains the
production writer behind `make verification-metadata`. `verification-metadata-candidate` replaces
those two executions with explicit assembly of the six opted-in debug test APKs, the minified
`releaseSmoke` app, and its standalone test APK. It intentionally names each supported module rather
than calling a broad root `assembleDebugAndroidTest`, which would pull unsupported benchmark or
container variants into the graph.

Manual dispatch accepts `reference` and `candidate` modes. Both regenerate on a clean Linux runner,
run the pre-write safety check, upload the ledger and dependency-guard baseline, and never commit or
push. After downloading artifacts from runs on the same SHA, compare them with:

```bash
python3 .github/scripts/check-verification-metadata-update.py --require-equivalent \
  reference/verification-metadata.xml candidate/verification-metadata.xml
```

The comparison canonicalizes the verification policy and compares every component, artifact, and
accepted checksum, including Gradle's nested `<also-trust>` alternatives. The default writer must
not switch until cold-Linux runs are equivalent or every missing candidate artifact is understood
and resolved by a narrowly scoped task. Assembly alone is only the first candidate; GMD/UTP tooling
may be resolved lazily during device tasks.

### Loop termination

The bot's own push re-triggers the workflow. A guard step exits early when HEAD is already the
regen commit; even without it, the second run would produce no diff and commit nothing.

### Write mode must not bless changed bytes

`--write-verification-metadata` is necessary to record a bumped version, but it also changes normal
mismatch behavior: Gradle can append the newly observed hash as an alternative instead of failing.
The workflow therefore snapshots the ledger before its first write-mode invocation and compares it
to the final generated XML. New components and artifacts are allowed; any checksum-set change for
an artifact already in the snapshot is rejected before commit. The same check rejects changes to
the `<configuration>` block, so regeneration cannot silently weaken trusted-artifact policy.

### Failure classification is diagnostic, not recovery

The workflow summary separates coordinate-set rejection, a still-unlisted artifact, changed bytes
for a recorded artifact, verification-policy drift, build/test/policy failure, and an operational
termination such as exit 143. It never retries automatically. In particular, a checksum mismatch
remains an alarm: classification only makes the next human action clear and never writes over the
evidence.

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
- **IDE-only artifacts are trusted by rule, not by hash.** Android Studio's Gradle sync resolves
  `-sources.jar` / `-javadoc.jar` variants that no build ever asks for. They were therefore absent
  from the ledger, and sync failed with ~137 "checksums are missing" entries while every CLI build
  and every CI lane stayed green — **no test could have caught this**: CI has no IDE, never requests
  those variants, and `make verification-metadata` runs the CI task graph, so the ledger
  *structurally* cannot contain them. Recording their hashes would be the wrong fix (unbounded
  churn, and still incomplete the moment someone opens sources for one more dependency). The fix is
  a `<trusted-artifacts>` rule in the ledger's `<configuration>` block trusting both suffixes by
  regex. Safe because sources and javadoc jars never land on a compile or runtime classpath — they
  are never executed, so the supply-chain surface this ADR protects is untouched.
  `--write-verification-metadata` preserves the block verbatim, so the regen workflow does not
  undo it (verified).

  **Gradle's own distribution sources are the same case, one coordinate further out.** Sync also
  resolves `gradle:gradle:<version>` `-src.zip` from the IDE-injected "Gradle distributions"
  repository, to give build scripts API completion. It surfaces as a *separate* one-artifact
  failure — and as the easily-dismissed `Could not resolve Gradle distribution sources` line — so
  fixing the jars alone leaves sync still red. A third `<trust>` rule covers it, scoped to that
  group/name. Pinning its hash instead would re-break sync on **every Gradle upgrade**, which is
  what the accumulated 9.5.0 / 9.6.0 / 9.6.1 copies in `~/.gradle` show happening.
- **Trusting the sources jars exposes a second, opposite gap — one that must be *recorded*, not
  trusted.** Fetching sources for a module still needs that module's `.module`/`.pom` metadata in
  the ledger. For anything CI resolves this is already there; the exception is
  `localGroovy()`, pulled in by `kotlin-dsl` in `build-logic`. Its jars come from the Gradle
  distribution, so CI never resolves them remotely and the ledger had **no `org.apache.groovy`
  entries at all** — but asking for their *sources* forces a real Maven Central resolution, which
  needs the metadata. Symptom: sync fails one artifact at a time (`groovy-4.0.32.module`, then its
  siblings). These are ordinary executable dependencies, so the fix is checksums, not a `<trust>`
  rule: resolve all eleven `org.apache.groovy:*` modules plus their sources under
  `--write-verification-metadata sha256` in one pass. Recorded hashes were cross-checked against
  Maven Central's published artifact before committing (`groovy-4.0.32.module` matched on both
  sha1 and sha256).

  A cache sweep for other coordinates missing from the ledger *at any version* turned up only
  unreferenced version-catalog entries and residue from other projects, so this class is closed —
  but the sweep is the way to check it, not one sync round-trip per artifact.
- **The `ideSyncArtifacts` task is what stops the above recurring.** Trusting suffixes by regex is
  version-agnostic, but the *recorded* Groovy checksums are not: the bundled Groovy moves with the
  Gradle version (4.0.29 → 4.0.32 already happened), so a Gradle bump would strand the ledger on
  the old coordinates and break sync again — and nothing in CI could see it. The root-project task
  resolves that graph, deriving both version and module list from the running distribution rather
  than hardcoding them. It is wired in twice, deliberately:
  - `make verification-metadata` includes it, so a regen **records** the current coordinates. This
    is the one entry in that target that does *not* mirror a CI task, for the reason above.
  - the always-run `format-check` lane in `ci.yml` runs it plainly, so a stale ledger **fails
    red** in CI instead of waiting to ambush whoever next opens the IDE.

  Verified in all three modes: passes when the ledger is complete, fails with "12 artifacts failed
  verification" when the Groovy entries are stripped, and records them under
  `--write-verification-metadata`. It is configuration-cache clean (~0.5s on a reused entry), so
  its cost on the always-run lane is negligible.
- **Escape hatch:** `./gradlew --dependency-verification off …` (or `lenient`) bypasses
  enforcement for one invocation when debugging.
- **Rebases:** a manual `@dependabot rebase` drops the bot's ledger commit; the resulting
  synchronize event re-fires the regen workflow, so it self-heals.
- **Ledger hygiene:** `--write-verification-metadata` appends; entries for dropped versions are
  not pruned automatically. Occasionally regenerate from an empty file to compact it — but the
  generator does **not** reconstruct the `<configuration>` block, so compacting drops the
  `<trusted-artifacts>` rules above and breaks IDE sync again. Preserve that block by hand.
- **Tasks outside the CI graph** (e.g. `make benchmark-check`) may resolve artifacts the ledger
  misses. Fix is always the same: `make verification-metadata` (extend the target's task list if
  a lane becomes permanent).

## Cost accepted

- The regen workflow runs a full CI-sized task graph on each Gradle-related Dependabot PR head
  (monthly, grouped) — roughly doubling CI cost for those PRs. Actions-only bumps are path-filtered
  out. The remaining cost is the price of the reference ledger until a smaller resolution graph is
  proven equivalent; resolution coverage must not be assumed.
- A ~1000+ line generated XML file lives in the repo and churns with every bump. Reviewers should
  treat its diffs as machine output: sanity-check *which* coordinates changed, not the hashes.
- Verification trusts the ledger's first write (trust-on-first-resolution). It defends against
  the bytes changing *after* adoption, not against a compromise that predates the ledger.
