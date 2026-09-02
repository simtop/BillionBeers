# Repository setup doctor

GitHub branch protection, repository secrets, Dependabot stores and environments live outside the
repository. A clone can therefore look identical while auto-merge or required checks are missing.

Run the read-only doctor with an authenticated GitHub CLI session:

```text
make repo-doctor
```

To inspect a different repository or branch:

```text
REPO=owner/name BRANCH=master make repo-doctor
```

The doctor verifies:

- `master` is the default branch and its protection requires only the strict `CI Gate` check;
- force-pushes and branch deletion are blocked;
- pull-request auto-merge and squash merge are enabled;
- `ci.yml` contains the `CI Gate` aggregate job and subscribes to `merge_group` checks;
- the native merge queue is active when the repository is organization-owned;
- Dependabot covers Gradle and GitHub Actions and its auto-merge workflow is present;
- `VERIFICATION_METADATA_DEPLOY_KEY` exists by name in both Actions and Dependabot secret stores;
- GitHub reports no CODEOWNERS parser errors and critical repository paths have owners; and
- workflow-referenced environments exist. This base intentionally has none because release
  deployment and its approval model belong to the adopting product.

The command only reads GitHub metadata. It never requests, prints or compares secret values. The
expected branch, check and deploy-key names can be overridden with `REPO_DOCTOR_DEFAULT_BRANCH`,
`REPO_DOCTOR_REQUIRED_CHECK` and `REPO_DOCTOR_REQUIRED_SECRET` when adapting the repository.

## Merge queue availability

Android CI is queue-ready: GitHub's `merge_group` event runs the complete heavy test set against the
synthetic queue commit and reports the same stable `CI Gate` required check as a pull request. The
repository itself is currently owned by a personal account, for which GitHub does not expose a
native merge queue. The doctor reports that state as informational rather than pretending the queue
is active or failing an otherwise-correct personal repository.

If the repository moves to an organization, configure a native merge queue for `master` before
relying on queued merges, then run the doctor again. In an organization-owned repository, a missing
queue is a failure. Recovery is deliberately small: disable the queue rule if queue validation
fails; keep `merge_group` support because it is harmless until GitHub creates a queue candidate.
Repository transfer itself is outside this setup procedure because it changes ownership and URLs.

The doctor does not replace branch protection or organization policy. Run it after creating a fork,
changing required checks, moving the verification workflow, changing ownership, configuring a merge
queue, or changing release ownership.
