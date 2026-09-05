# BillionBeers — agent onboarding

A multi-module Android beer catalog over the read-only `brewbuddy.dev` API: Compose,
Metro DI, Room SSOT, hand-rolled paging and two on-demand dynamic features.
The architecture is part of the product; follow enforced conventions.

Code, build scripts, ADRs and Konsist tests are authoritative. Correct this guide when
it disagrees with them. Read the relevant ADR or enforcement test for rationale.

## Where work belongs

| Area | Modules / responsibility |
|---|---|
| Assembly | `:app`: graph, navigation host, dynamic feature declarations |
| Core | `:core`: Android DI/logger; `:core-common`: pure-JVM paging, errors and shared interfaces |
| Domain | `:beerdomain:api`: immutable models/repository interfaces; `:beerdomain:fakes`: test fakes |
| Data | `:beer_network`: Retrofit/DTOs; `:beer_database`: Room; `:beer_data`: repositories/mappers/pager factory |
| Features | `:feature:beerslist`, `:feature:beersearch`; on-demand `:feature:beerdetail`, `:feature:beerbrowse` |
| Shared UI | `:presentation_utils`: paging UI, split installation and dynamic-feature strings; `:navigation`: routes; `:core:designsystem`: theme/tokens/previews |
| Catalog | `:catalog*`: component catalog/generator |
| Tests | `:testing-utils` (JVM), `:testing-utils-android` (robots), `:snapshot-testing`, `:snapshot-processor`, `:konsist`, `:app-release-smoke` |
| Build/perf | `build-logic`: composite build/conventions; `:benchmark:*`: physical-device measurements |

Modules are auto-discovered from build scripts; do not edit `settings.gradle.kts` to add one.
Search `src/`, excluding `bin/` and `build/`: ignored IDE output can contain deleted sources.

## Enforced boundaries

The exact source rules live in `konsist/src/test/`; resolved graph rules live in
`build-logic` and `config/architecture/project-dependency-policy.json`.

- Repository interfaces never import data types. Domain has no Android imports and uses
  immutable models/collections. ViewModels depend only on domain-layer types.
- Features do not depend on sibling features or declare data-layer modules; navigate via
  `:navigation`. Resolved compile-classpath checks also reject transitive data exposure.
- Dynamic-feature user-facing strings live in `:presentation_utils`, with translations;
  importing a dynamic feature's own `R` breaks instrumented tests.
- Fixtures belong in sibling `:fakes` / `:fixtures` modules, never `java-test-fixtures`.
- One-shot UI events use `Channel(BUFFERED).receiveAsFlow()`, not `MutableSharedFlow`.
- Every `src/` tree has a sibling build script. A module with `src/androidTest/` opts into
  the managed-device convention. Benchmark and standalone release-smoke tiers are deliberate exceptions.
- Test libraries never ship on production `implementation`/`api`; test infrastructure and
  benchmarks have documented exceptions.
- Production project edges and intentional `api` exposures follow
  `config/architecture/project-dependency-policy.json`; run `make architecture-policy`.
- New invariants require enforcement in the same change. `:konsist:test` must retain the
  repository `.kt`/`.kts` task inputs or its filesystem checks can silently go UP-TO-DATE.

## Settled decisions

Read the relevant [ADR](docs/adr/) before changing a settled choice.

Keep hand-rolled paging, `Either<L, R>` with typed sealed errors, injected mapper classes,
local-only availability. Add a use case only for behavior a
repository call does not provide. ViewModels use bare `viewModelScope.launch`; choose a
dispatcher where blocking/CPU work actually happens. Keep precompiled convention scripts
and the measured repository-owned KSP screenshot discovery. Do not justify new modules
by assumed build speed.

ADR 0010 defines declined infrastructure and reopen triggers; auth, remote writes, a shipped
analytics/crash SDK and other backend-dependent features are not generic completeness work.
ADR 0008 owns CI lane selection (`classify_path` in `.github/scripts/detect-change-scope.sh`).
ADRs 0008/0009 preserve rejected sharding and cache experiments, including the coherent
SDK/AVD cache: do not repeat them without changed inputs and a measurement hypothesis.

## Verification and tools

Use Makefile wrappers; `make help` lists commands. Choose checks that can falsify the change,
then complete applicable architecture/static/UI gates. Do not rerun passed checks without
new changes, failures or unresolved evidence. Do not run Android builds for prose-only edits.

- Compile the affected module, then `make test MODULE=:module`. Unscoped `make test` includes
  the named JVM modules and build-logic; check `JVM_TEST_MODULES` when adding a JVM module.
- `make konsist` checks source boundaries. `make architecture-policy` resolves project edges.
- UI changes: `make screenshot-verify`; record with `make screenshot-record` and inspect PNGs.
- `make lint` is Detekt, `make format` is Spotless, and `make android-lint` is Android Lint.
  Resources require Android Lint and translated strings. Never regenerate a baseline to hide
  a new finding.
- Use `android-cli` for device/emulator work; check `android <command> --help` for syntax.
  Install BillionBeers with `make install`: it stages on-demand splits through bundletool
  local testing, which an ordinary APK install does not replace. `pm clear` removes that
  staging; rerun `make install`. Run instrumented tests through the Makefile device targets.
  For capabilities the installed CLI lacks (such as logcat), use adb from `ANDROID_HOME`.
- Build-time measurements: `make build-budget` after material build/toolchain changes on a
  quiet local machine; 15–20 minutes, not a CI wall-clock substitute. `make benchmark-check`
  gates startup only on physical hardware.
- Use one output-compression layer. Native sessions can use RTK; the Makefile selects
  `rtk gradlew` when installed. Gateway-compressed sessions should set
  `GRADLE_RUNNER=./gradlew` and use ordinary shell commands without RTK/snip wrappers.
  The Makefile respects that environment override. Keep raw diffs and full failure evidence
  available; output-token estimates are not subscription quota or money saved.

## Skills and collaboration

Project skills live in `.claude/skills/`. A local `.agents/skills` symlink exposes the same
source to Codex; read a relevant skill directly if it is absent from the session catalog.
Use `android-cli` for Android tooling and `land` for commit/push/PR work.
`make update-android-skills` is the skill-management entry point; it respects local pins
and `.android-skills-ignore`. Do not run `android init` or `android skills add/remove` to
create a second installation path. Read skill bodies only when relevant.

Use one agent by default. Delegate only when requested or when an independent bounded task
clearly benefits and the session permits delegation. Give it a concise task and relevant
paths; account for duplicated context and verification cost. Never require model-branded
roles, multiple reviews or fable-mode for routine work. Keep model/provider preferences local.

## Documentation and memory

`docs/` contains committed, load-bearing documentation; ADRs own decisions. Planning notes
are ignored and local-only: never cite them from committed files, and never delete or
replace them without being asked. Promote load-bearing decisions into self-contained docs.
Memory is optional recall, not authority for project facts or current backlog. Verify it
against source and ADRs; do not copy resolved task histories into always-loaded instructions.
Local permissions, provider configuration and private user preferences stay untracked.
