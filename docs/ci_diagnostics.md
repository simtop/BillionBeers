# CI diagnostics

The `CI PR Diagnosis` workflow listens for completed `Android CI` runs and upserts one
`<!-- billionbeers-ci:diagnosis -->` comment on the associated pull request when the run is not
green. It runs only trusted reporting code from the default branch; it never checks out or executes
the pull request's code. This keeps reporting available for fork pull requests without granting
untrusted workflow code write permissions.

The comment contains failed job names, copy-paste local commands, the completed run link, and links
to non-expired artifacts. Test artifacts retain the exact JUnit class/test identifier and bounded
failure detail when the report contains it. Artifact retention is currently seven days, so the run
link remains the fallback after an artifact expires.

Screenshot failures also retain the existing Step Summary command:

```bash
gh workflow run record_screenshots.yml --ref <branch> -f modules='<affected modules>'
```

The report publisher is intentionally best-effort. A GitHub API or artifact-download failure emits a
warning in the reporting run and must not change the original `CI Gate` result. The detailed Step
Summary and raw job logs remain authoritative when a report is incomplete.
