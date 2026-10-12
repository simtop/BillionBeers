# Automatic PR branch updates

Enable GitHub auto-merge on a reviewed PR when it is ready to land. The
`Update ready PR branches` workflow keeps open, non-draft PR branches in this repository up to date
with `master` while auto-merge is enabled. Disable auto-merge to opt out of future updates.
Fork branches require manual updates because the repository write key cannot push to a fork.

The updater runs on pushes to `master`, after `BillionBeers CI` completes, and approximately every
ten minutes. GitHub can delay scheduled runs. For an immediate retry, dispatch it from `master`:

```bash
gh workflow run update-ready-prs.yml --ref master
```

The deploy-key push that updates a PR branch starts PR CI. GitHub suppresses workflow runs caused
by `GITHUB_TOKEN` pushes, including the bot's later merge into `master`. The updater checks for
a `BillionBeers CI` run on the exact current `master` revision and dispatches one on `master`
when none exists. A run for an older revision does not count, and the updater checks that master
has not advanced before dispatching. Existing queued, running, successful, or failed runs all count,
so a failed CI run is reported by CI and is not automatically retried. Branch updates continue
independently of this fallback. Manual CI dispatches on other refs fail the required `CI Gate`,
because dependency preparation is only available for pull request events.

Updates use merge commits and preserve existing history. Existing commit IDs remain stable for collaborators;
pull the remote branch before continuing local work. An explicit expected-head lease rejects a concurrent
head change, including a rewind or branch deletion. Every accepted push is a fast-forward,
and merge conflicts require manual resolution. The workflow run summary reports updates, conflicts
and deferred branches. Running or queued CI and dependency preparation on the current head defer
an update until a later run, rather than interrupting that work.

Only trusted default-branch updater code executes with the existing
`VERIFICATION_METADATA_DEPLOY_KEY`. Git calculates a merge tree and creates a commit without
checking out or running PR code. The deploy-key push triggers fresh PR CI, unlike an ordinary
`GITHUB_TOKEN` push. Conflicting merges require manual resolution. The updater preserves branch history;
GitHub retains responsibility for enabling auto-merge, its selected merge method, and landing.

Strict branch protection still requires an up-to-date branch and successful `CI Gate`. GitHub
auto-merge lands the PR after requirements pass. Select squash when enabling auto-merge to land
one commit per PR without its branch-update commits in `master` history. Library/build changes
usually require all test lanes again; merge updates only allow earlier successful results to be
reused where the existing CI filter proves a lane is unaffected.

This is a branch updater, not a merge queue or custom merge sequencer. It does not guarantee a
single CI run or serialize dependency preparations. Preparation retains its existing concurrency
and exact-head approval rules; a changed head requires fresh evidence and any coordinate approval
must be reviewed again. See [monthly dependency updates](dependabot-updates.md).
