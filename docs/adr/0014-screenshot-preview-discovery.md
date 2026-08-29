# 0014: Use KSP for screenshot preview discovery

## Status

Accepted.

## Context

Screenshot tests need to discover Compose previews across the six preview-bearing modules and expand
multipreview annotations, preview parameters, and the accessibility matrix into deterministic
Paparazzi cases. The original KSP implementation recognized a function as a preview but did not
enumerate nested `@Preview` declarations in multipreview annotations, omitting 28 ordinary dark
cases.

PR #179 replaced that processor with ComposablePreviewScanner (CPS) and repaired the missing
coverage. PR #180 then repaired the KSP processor so it recursively expands nested multipreviews,
preserves preview metadata, honors `PreviewParameter.limit`, uses provider display names, expands
the 36-case accessibility matrix, removes stale aggregating output, and generates deterministic
module-local inventories.

The comparison excludes the catalog's handwritten Paparazzi case: each implementation discovers 272
preview cases, plus that one manual case, for 273 tracked screenshot PNGs in the full suite.

| Module | CPS | Repaired KSP |
|---|---:|---:|
| `core:designsystem` | 42 | 42 |
| `presentation_utils` | 10 | 10 |
| `feature:beerbrowse` | 84 | 84 |
| `feature:beerdetail` | 40 | 40 |
| `feature:beersearch` | 46 | 46 |
| `feature:beerslist` | 50 | 50 |
| **Total** | **272** | **272** |

Both produced 216 accessibility-matrix cases and 28 ordinary dark cases, and both passed the
existing golden images.

The final comparison measured the independently wired branches rather than two backends carried by
one convention plugin:

- repaired KSP at `2b056b1ae1ae12af4230eb3e43d7656ab948f849`;
- CPS at `87e3b5305761da76c9dc96e441314386dec58529`.

Each candidate had its own worktree and one discarded warm-up. Five measured rounds alternated
backend order and ran `verifyPaparazziDebug` with the build cache, configuration cache, and daemon
disabled and every task rerun. Every run verified all 273 goldens. KSP executed 388 tasks per run;
CPS executed 380.

| Candidate | Measured samples (seconds) | Median | Range |
|---|---|---:|---:|
| Repaired KSP | 77.60, 72.19, 85.05, 77.99, 66.05 | 77.60 | 66.05-85.05 |
| CPS | 88.68, 99.34, 105.40, 102.53, 91.42 | 99.34 | 88.68-105.40 |

KSP's median was 21.74 seconds, or 21.9%, lower. It won all five paired rounds, and its slowest
sample (85.05 seconds) was faster than CPS's fastest (88.68 seconds). This is repeatable,
machine-specific evidence rather than a universal performance claim.

CPS and KSP have different failure and maintenance surfaces:

- CPS discovers compiled Compose classes in the screenshot test process. It avoids a custom
  processor and generated invocation code, supports private-preview invocation through its compiled
  scanning path, but adds runtime scanning and classpath dependencies.
- KSP moves discovery to compilation and avoids runtime scanning, but the repository owns processor
  compatibility, generated source, aggregating invalidation, and explicit Gradle wiring. The current
  implementation assumes top-level, non-private preview functions and needs broader compatibility
  coverage before it can be published as a general-purpose library.

## Decision

**Use the repaired KSP implementation from PR #180.** The screenshot
convention applies KSP and `:snapshot-processor` directly; it has no CPS dependencies, runtime
scanner implementation, or backend-selection property.

The branch-isolated measurement preserves equal golden coverage and clean-build correctness with a
repeatable 21.9% KSP median advantage. That is material enough to accept the repository-owned
processor and its maintenance surface for this project.

**Keep PR #179 as the historical CPS alternative.** It preserves a working compiled-scanner
implementation for later reevaluation without carrying two backends in the application convention.

## Consequences

- Normal screenshot builds generate module-local KSP inventories.
- The catalog keeps its handwritten Paparazzi test and explicitly disables generated preview
  discovery.
- PR #180 does not resolve CPS or ClassGraph artifacts.
- The repaired processor and its functional tests are production build infrastructure, so clean and
  incremental execution remain release-blocking requirements.
- PR #179 preserves the CPS implementation for later reevaluation if its compatibility or
  performance improves.
- Case counts remain scoped as 272 discovered previews plus one handwritten catalog case.

## Reconsideration criteria

Reconsider CPS if KSP's performance advantage collapses, a required preview shape cannot reasonably
be generated or bridged, or processor compatibility creates a larger ongoing cost than runtime
discovery.

Before publishing the processor as a standalone library, add coverage for member-contained previews,
decide whether private previews are supported through a reflection/bridge path, test supported
Compose annotation versions and Kotlin/KSP combinations, and document consumer Gradle wiring. These
are implementable compatibility gaps rather than missing discovery semantics. App use does not need
to wait for that general-library surface when the repository's own preview shape is covered.

## Related

- `docs/adr/0009-feature-module-ui-test-tier.md` — Paparazzi and device-test boundaries.
- `docs/adr/0011-build-time-budget.md` — local measurement methodology and why machine-specific
  timings are not CI gates.
- PR #179 — historical CPS alternative: https://github.com/simtop/BillionBeers/pull/179
- PR #180 — selected repaired KSP implementation: https://github.com/simtop/BillionBeers/pull/180
