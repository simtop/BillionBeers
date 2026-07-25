# 0006: CI supply-chain hardening — pin Actions to SHAs, scan for secrets, defer dependency verification

## Status

Accepted

## Context

CI is an execution surface, not just a checker. Every workflow job runs third-party code — the
GitHub Actions in `uses:` clauses — with a repo-scoped `GITHUB_TOKEN` and access to any secrets the
job can read. Separately, the build resolves ~130 catalog entries (`gradle/libs.versions.toml`)
plus their transitive graph from remote repositories. Both are places where code we did not write
runs against our repo, so both are supply-chain surfaces worth hardening.

Three well-known hardening measures were on the table. They are not equal in cost, and the deciding
lens for this repo is: **can the maintenance pain be paid once or automated away, or is it inherent
and recurring?** A solo project cannot afford a control that taxes every routine change.

## Decision

1. **Pin every GitHub Action to a full commit SHA**, with a trailing `# vX` version comment.
2. **Add a `gitleaks` secret-scanning job** to `ci.yml`, scoped to the pull request's own commit
   range, run from a version-pinned, checksum-verified release binary.
3. **Defer Gradle dependency verification** (`gradle/verification-metadata.xml`). Not rejected on
   the merits — deferred because its cost is recurring and manual for this repo. This ADR is the
   record of that reasoning; revisit criteria are below.

---

## 1. SHA-pinning Actions

### What it actually does

`uses: actions/checkout@v7` resolves `v7` at run time. A Git tag is a **mutable pointer**: whoever
controls the action's repository can move `v7` to a different commit at any moment, and the next CI
run silently executes the new code. Pinning to the commit — `actions/checkout@3d3c42e5… # v7` —
resolves to an **immutable** object: a commit SHA is a hash of its own content and history, so it
cannot be repointed. Move the `v7` tag all you like; a run pinned to the SHA keeps executing the
exact tree that SHA names, which is the tree we reviewed.

The `# v7` comment is not decoration. Dependabot reads it to know the human-facing version the SHA
corresponds to, so it can bump the pin and the comment together and report the semver update-type.

### Why it's worth it — with a concrete example

The threat is not abstract. **`tj-actions/changed-files` (March 2025):** an attacker gained write
access and **retagged existing version tags** to a commit that dumped the runner's memory —
including secrets — into the build log. Every workflow using a *mutable tag* of that action
executed the malicious code on its next run; roughly tens of thousands of repositories were
affected, and any that printed logs publicly leaked their secrets. **Repos that pinned a SHA were
unaffected** — their pinned commit was never the retagged one. This is precisely the class of
attack SHA-pinning defeats, and it is why GitHub's own hardening guide and the OpenSSF Scorecard
`Pinned-Dependencies` check both rank it the first thing to do.

### Why the pain is avoidable here (this is the crux)

The usual objection is that SHAs are opaque and that pinning creates a churn treadmill. Neither
survives contact with this repo's existing setup:

- **Churn is already automated.** `.github/dependabot.yml` has the `github-actions` ecosystem
  enabled. Dependabot bumps a pinned SHA *and* its `# vX` comment on its monthly schedule, exactly
  as it bumps a tag. There is no new manual step.
- **It does not break auto-merge.** The concern was that `dependabot-auto-merge.yml` might decide
  major-vs-minor by parsing `@v6` out of the workflow text. It does not: it reads
  `dependabot/fetch-metadata`'s `update-type` output — Dependabot's own semantic classification of
  the PR — which is agnostic to whether the ref is a tag or a SHA. Verified by inspection before
  landing.
- **Readability is preserved** by the `# vX` comment.

Residual cost: a one-time resolution of five action SHAs. That's the whole bill.

---

## 2. `gitleaks` secret scanning

### What it does

`gitleaks` inspects Git content for committed secrets using two complementary strategies: a library
of **regex rules** for known credential shapes (AWS keys, GitHub PATs, private-key PEM blocks,
Slack tokens, …) and **Shannon-entropy** heuristics for high-randomness strings that look like keys
even without a matching rule. Our job runs it in `git` mode over `origin/<base>..HEAD`, so it scans
the **commits the PR introduces**, and fails the check if it finds anything.

### Two deliberate scoping choices

- **PR commit range, not full history.** Branch protection means every change reaches `master`
  through a PR, so scanning each PR's own commits guards every change going forward. A full-history
  scan on every PR would re-flag any long-removed commit forever and grow slower as history grows.
  Cleaning past history is a *one-off* audit-and-rotate task, not a job that belongs on the
  per-PR hot path. And note the real remediation for a secret that was ever committed: it is
  compromised the moment it lands, so **rotation** is the fix — removing it later, or catching it
  in CI, does not un-leak it. This gate exists to stop the *next* one.
- **Pinned, checksum-verified binary, not a floating action.** The job downloads a fixed gitleaks
  release (`GITLEAKS_VERSION`) and verifies its `sha256` against a pinned digest before running it.
  Using `gitleaks-action@v2` or a `curl | sh` installer would reintroduce the exact mutable
  third-party surface this whole ADR is about — and it sidesteps that action's org-license prompt.
  The **Codecov bash-uploader compromise (2021)** — a widely-`curl`ed installer script was altered
  to exfiltrate environment variables — is the cautionary tale for why "download and run the latest
  script" is not acceptable in a job whose purpose is supply-chain hygiene. Pin the artifact, verify
  its hash, then run it.

---

## 3. Deferring Gradle dependency verification

### What it does, and why it's genuinely valuable

Gradle's `verification-metadata.xml` is a checked-in ledger of the `sha256` (and optionally
PGP signatures) of **every artifact the build resolves** — every JAR, POM, and plugin in the
transitive graph. On each build Gradle recomputes the hash of what it downloaded and **fails if it
does not match the ledger**. This defends against a threat the two measures above do not touch:
**artifact substitution.** If a repository mirror is compromised, a coordinate is typosquatted, or a
man-in-the-middle serves a tampered JAR under a version you already trust, the bytes change and the
checksum mismatch stops the build. It is the dependency-graph analogue of SHA-pinning an Action.
The broader lesson behind it is the **xz-utils backdoor (2024)**, where a trusted package's *release
artifact* was maliciously altered — "the coordinate and version look right" is not proof the bytes
are what the author published.

### Why it is deferred anyway — the cost is recurring and unavoidable here

The control only helps if the ledger stays current, and keeping it current fights this repo's own
automation:

- **It taxes every Dependabot bump.** Once checksums are enforced, a bump to any dependency
  produces an artifact whose hash is not yet in the ledger, so **CI fails until a human runs
  `./gradlew --write-verification-metadata sha256` and commits the result.** That lands directly on
  the grouped monthly auto-merge flow that ADR 0005 deliberately built — the very automation whose
  point is to *not* need a human per bump. Making it painless would mean a bespoke workflow that
  regenerates and commits metadata on Dependabot branches and interoperates with the auto-merge
  gate: real, ongoing complexity for a solo repo.
- **Generation is its own rabbit hole.** On a multi-module Android build with the configuration
  cache, `--write-verification-metadata` typically needs `--no-configuration-cache` and several
  passes to catch every variant configuration (`androidTest`, screenshot, benchmark), and yields a
  1000+ line file to maintain.
- **What it catches is partly covered.** SHA-pinned Actions and a pinned+checksummed gitleaks binary
  already close the *executable* supply-chain surface. Dependency verification adds the *library*
  surface — worth real money in a team/enterprise setting, less so where the graph changes monthly
  under one maintainer's eye.

Applying the deciding lens: SHA-pinning's pain is one-time-or-automated, so it passes; dependency
verification's pain is inherent and recurring, so for a solo repo the honest call is to defer and
document, not to ship a control that breaks every routine update.

---

## Cost accepted

- **The `actions/*` first-party residual risk is not zero.** Pinning trusts that the SHA we pin is
  itself good; a compromised commit that we then pin would be faithfully re-run. Pinning defeats
  *tag repointing after review*, not a malicious commit adopted at pin time. Dependabot's monthly
  bumps plus the tj-actions lesson make this an acceptable residual.
- **gitleaks is scoped to catch the next leak, not to clean the past.** A pre-existing secret in old
  history is out of scope here by design (see the rotation note above).
- **The library supply-chain surface stays unhardened** until dependency verification (or an
  equivalent) is adopted — accepted deliberately, tracked here.

## Consequences

- All five Actions across the five workflows (`ci`, `format_fix`, `record_screenshots`,
  `weekly-compat`, `dependabot-auto-merge`) are SHA-pinned; Dependabot keeps them fresh.
- `ci.yml` gains a PR-only `secret-scan` job gating on `format-check`, consistent with the other
  parallel jobs.
- Bumping gitleaks is a two-line change (`GITLEAKS_VERSION` + `GITLEAKS_SHA256`); the version is not
  Dependabot-managed because it is a `curl`ed release, so it is a deliberate, occasional manual bump.
  The weekly `gitleaks-version-check` workflow watches for drift and opens an issue with the
  ready-to-paste bump, so the reminder is automated even though the bump itself stays manual.

## When to revisit dependency verification

- This grows into a **multi-contributor or team** setup — the maintainer's-eye assumption breaks and
  the library surface's value rises sharply.
- A **CI job to regenerate + commit** `verification-metadata.xml` on Dependabot branches is built
  first, so enforcement no longer taxes routine bumps.
- Any of the extracted open-source tools (see the roadmap) publishes artifacts others consume — a
  producer of dependencies owes its consumers a verifiable supply chain.
