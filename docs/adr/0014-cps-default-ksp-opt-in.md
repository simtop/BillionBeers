# 0014: Screenshot preview discovery defaults to CPS, with KSP as an opt-in backend

## Status

Accepted.

## Context

Screenshot tests need to discover Compose previews across the six preview-bearing modules and expand
multipreview annotations, preview parameters, and the accessibility matrix into deterministic
Paparazzi cases. The original KSP implementation had a correctness gap: it recognized a function as
a preview but did not enumerate the nested `@Preview` declarations in multipreview annotations.
That caused the old implementation to omit 28 ordinary dark cases.

The migration to ComposablePreviewScanner (CPS) repaired that gap and became the application's
working default. Before treating the migration as settled, the KSP backend was repaired and measured
against CPS using the same six modules and existing goldens. The comparison excludes the catalog's
handwritten Paparazzi case: there are 272 discovered preview cases, plus that one manual case, for
273 tracked screenshot PNGs in the full suite.

The repaired KSP implementation recursively expands nested multipreviews, preserves preview
metadata, honors `PreviewParameter.limit`, uses provider display names, expands the 36-case
accessibility matrix, and generates deterministic module-local inventories. Both backends produced
the same normalized inventory:

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
existing golden images. After repairing clean-build processor wiring, a repeated equal-case
end-to-end measurement used one discarded warm-up and five measured runs per backend, alternating
backend order. Each run disabled the build cache, configuration cache, and daemon, reran every task,
and verified the same 273 goldens. Repaired KSP's median was 83.48 seconds (range 76.27-125.00)
versus 99.95 seconds for CPS (range 92.07-130.52), a 16.5% lower median. The wide ranges make this
useful directional evidence, not a universal performance claim; the result remains machine- and
workload-specific.

CPS and KSP have different failure and maintenance surfaces:

- CPS keeps discovery in the screenshot test process and follows compiled Compose classes. It avoids
  maintaining a custom processor, generated invocation code, and KSP/Gradle integration.
- KSP moves discovery to compilation and can reduce runtime discovery cost, but it adds a processor,
  generated source, KotlinPoet/KSP compatibility, aggregating invalidation, and explicit build wiring.
  Its current implementation also assumes the repository's top-level preview-function shape and
  needs broader compatibility coverage before it is a general-purpose library.

## Decision

**Use CPS as the application's default screenshot-discovery backend.** The default remains the
`cps` value of `billionbeers.screenshot.discovery`.

**Keep repaired KSP as an explicit opt-in backend** using
`-Pbillionbeers.screenshot.discovery=ksp`. It remains available for experiments, fallback use, and
continued evaluation as a possible standalone compile-time screenshot-discovery library. It is not
the application's default despite its measured speed advantage because correctness is equal and the
application benefits more from CPS's smaller maintenance and compatibility surface.

The two approaches are not presented as equivalent product choices: CPS is the supported app path;
KSP is a deliberately retained candidate whose library value can be evaluated independently of the
app's default.

## Consequences

- Normal screenshot builds do not apply KSP or carry the processor dependency; they use CPS.
- The application retains the 272-case discovered-preview coverage, including the 28 ordinary dark
  cases that exposed the old KSP defect.
- The repaired KSP implementation and its regression fixtures remain useful as a benchmark and as
  evidence for a future reusable library, but changes to it must preserve opt-in isolation.
- A runtime discovery step remains in the CPS path. The measured KSP speed advantage is accepted as
  the cost of choosing the lower-maintenance default for this application.
- The comparison counts must be read by scope: 272 discovered preview cases plus one handwritten
  catalog case equals 273 tracked screenshot files.

## When to revisit

Reconsider the default if any of the following becomes true:

- CPS develops a correctness limitation that the processor can solve without comparable complexity.
- Repeated measurements on representative clean and incremental builds show a material performance
  or memory regression from CPS at the repository's scale.
- The screenshot surface grows enough that runtime discovery becomes a meaningful bottleneck.
- KSP gains the required coverage for member-contained previews, annotation/version compatibility,
  Gradle/Kotlin upgrades, and clean/incremental/CI execution, while keeping its maintenance burden
  demonstrably lower than the cost it removes.
- A standalone library effort establishes a stable public compatibility contract for the processor.

Any change of default requires another apples-to-apples inventory, golden, and build-cost comparison;
performance measurements alone are not sufficient.

## Related

- `docs/adr/0009-feature-module-ui-test-tier.md` — Paparazzi and device-test boundaries.
- `docs/adr/0011-build-time-budget.md` — local measurement methodology and why machine-specific
  timings are not CI gates.
- PR #179 — CPS migration: https://github.com/simtop/BillionBeers/pull/179
- PR #180 — repaired KSP comparison: https://github.com/simtop/BillionBeers/pull/180
