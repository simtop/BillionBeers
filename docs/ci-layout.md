# CI layout

## Pull request and master checks

Start with [BillionBeers CI](../.github/workflows/ci.yml) for pull requests and pushes to `master`.
**Repository Checks** runs the tooling tests, formatting, and repository checks. **Detect change
scope** uses the [path classifier](../.github/scripts/detect-change-scope.sh) to select test lanes.
**CI Gate**, the required merge check, verifies the results of the checks and expected lanes.
[ADR 0008](adr/0008-per-lane-ci-test-selection.md) explains lane selection and when master can reuse
pull request test results.

| Work | Where to look |
| --- | --- |
| Weekly Full Protect on `master` | [BillionBeers CI](../.github/workflows/ci.yml) runs all five test lanes and the Desktop package smoke each Monday at 05:25 UTC. Each job uses the scheduled event's commit. The regular CI Gate requires every lane and the master failure issue tracks failures on the current head. Use `force_full` for a manual run. |
| Weekly full checks for open pull requests | [Weekly full PR CI](../.github/workflows/weekly-pr-full-ci.yml) calls the same CI workflow with all test lanes selected. |
| Failure comments and test reports | [CI PR Diagnosis](../.github/workflows/ci-report.yml) reads workflow results and updates the pull request comment. See [CI diagnostics](ci_diagnostics.md). |
| Dependabot graph review and preparation | The [dependency update guide](dependabot-updates.md) maps graph review, approval, metadata regeneration, and the checks required before merge. |
| Formatting fixes, baseline updates, and screenshot recording | The generator workflows upload artifacts. [CI Writeback](../.github/workflows/ci-writeback.yml) validates the source run and applies the generated files. |
| Branches waiting for auto-merge | [Update ready PR branches](../.github/workflows/update-ready-prs.yml) updates eligible branches when `master` advances. The updated head gets fresh CI. |
| Detekt debt and other health signals | [Monthly Health Report](https://github.com/simtop/BillionBeers/actions/workflows/health-report.yml) publishes the latest results in the run summary and `health-report` artifact. The [committed sample](health/SAMPLE.md) shows the format. |

## Where CI changes belong

| Change | Location |
| --- | --- |
| Event triggers, permissions, job dependencies, runner selection, and artifact handling | The [workflow](../.github/workflows/ci.yml) that owns the run. |
| A full job sequence invoked by another workflow | A workflow callable through `workflow_call`. [Compare Verification Metadata Writers](../.github/workflows/verification-metadata-compare.yml) calls the [metadata workflow](../.github/workflows/regen-verification-metadata.yml) in comparison mode. |
| A repeated step sequence on the caller's runner | A composite action. [Set up JDK](../.github/actions/setup-jdk/action.yml) shares Java setup and retry behavior. |
| Policy decisions, validation, and report parsing | A script with its tests in [`.github/scripts`](../.github/scripts). The path classifier and [CI comment renderer](../.github/scripts/render-ci-pr-comment.py) are examples. |
| Build or test commands that contributors also run locally | A [Makefile](../Makefile) target that CI can invoke with the same arguments. |

The diagnosis and writeback workflows use default branch code for operations that need write
permissions. Keep PR builds and artifact generation in their source workflows. See
[ADR 0006](adr/0006-ci-supply-chain-hardening.md) for the trust boundaries.
