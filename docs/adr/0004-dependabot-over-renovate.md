# 0004: Dependabot over Renovate for the version catalog

## Status

Accepted

## Context

`gradle/libs.versions.toml` has ~130 version/library entries (MASTER_PLAN.md Phase 3 calls for
automated dependency updates here). Two real options exist: GitHub's native Dependabot, or the
Mend Renovate GitHub App. Both handle Gradle version catalogs adequately as of this writing.

## Decision

Use Dependabot (`.github/dependabot.yml`), not Renovate.

## Why

- **No third-party trust surface.** Dependabot is native to GitHub - no external app to grant repo
  access to. Renovate requires installing the Mend-operated GitHub App, a third party with commit-
  adjacent access to the repo. For a solo project this is a real, if modest, cost with no
  corresponding benefit strong enough to justify it.
- **Native integration with GitHub's Security tab** - the same pipeline that surfaces vulnerability
  advisories also drives routine version bumps, one less moving part.
- **Zero setup friction** - a single YAML file, no external account, no app installation flow.

## Cost accepted

Renovate's real advantages are given up deliberately:

- **Weaker grouping/scheduling model.** Renovate's package-rule engine and regex managers are more
  powerful than Dependabot's `groups:` key. Mitigated here by grouping `dependabot.yml` along the
  same categories `libs.versions.toml` already comments its `[versions]` block with (Core, AndroidX,
  Compose, Testing, KotlinX, tooling), keeping monthly PR volume sane without Renovate's extra
  power.
- **No Dependency Dashboard.** Renovate keeps one tracking issue with every pending/rate-limited/
  pinned update visible at a glance; Dependabot has no equivalent - each update is its own PR, full
  stop.

## Consequences

- Auto-merge for patch/minor updates (`.github/workflows/dependabot-auto-merge.yml`) required two
  repo-level changes beyond just Dependabot config: branch protection on `master` requiring CI
  status checks, and enabling the repo's `allow_auto_merge` setting. Both are now in place. Major
  version bumps are never auto-merged (checked independently of `dependabot.yml`'s own
  `update-types` group filter, as a second gate in the auto-merge workflow itself).
- Revisit if the ~130-entry catalog's monthly PR volume becomes unmanageable even with grouping,
  or if this ever grows into a multi-repo/team setup where Renovate's dashboard earns its third-
  party-trust cost.
