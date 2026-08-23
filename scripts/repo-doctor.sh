#!/usr/bin/env bash
set -uo pipefail

# This script only calls GET endpoints through gh. It checks names and metadata, never secret
# values. The defaults describe BillionBeers; REPO_DOCTOR_* overrides let an adopting repository
# keep the same doctor while it changes its branch/check/secret names.
repo="${REPO:-${1:-}}"
requested_branch="${BRANCH:-${2:-}}"
expected_default_branch="${REPO_DOCTOR_DEFAULT_BRANCH:-master}"
required_check="${REPO_DOCTOR_REQUIRED_CHECK:-CI Gate}"
required_secret="${REPO_DOCTOR_REQUIRED_SECRET:-VERIFICATION_METADATA_DEPLOY_KEY}"
failures=0

pass() {
  printf 'PASS  %s\n' "$1"
}

fail() {
  printf 'FAIL  %s\n' "$1"
  failures=$((failures + 1))
}

note() {
  printf 'INFO  %s\n' "$1"
}

if ! command -v gh >/dev/null 2>&1; then
  printf 'ERROR gh is required and must be authenticated.\n' >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  printf 'ERROR jq is required.\n' >&2
  exit 1
fi

if [[ -z "$repo" ]]; then
  repo="$(gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null || true)"
fi

if [[ -z "$repo" ]]; then
  printf 'ERROR could not determine the repository. Set REPO=owner/name.\n' >&2
  exit 1
fi

repo_json="$(gh api "repos/$repo" 2>/dev/null || true)"
if [[ -z "$repo_json" ]]; then
  printf 'ERROR could not read repository settings for %s.\n' "$repo" >&2
  exit 1
fi

default_branch="$(jq -r '.default_branch // empty' <<<"$repo_json")"
branch="${requested_branch:-$default_branch}"

if [[ "$default_branch" == "$expected_default_branch" ]]; then
  pass "default branch is $default_branch"
else
  fail "default branch is $default_branch; expected $expected_default_branch"
fi

if jq -e '.allow_auto_merge == true' >/dev/null <<<"$repo_json"; then
  pass 'pull-request auto-merge is enabled'
else
  fail 'pull-request auto-merge is disabled'
fi

if jq -e '.allow_squash_merge == true' >/dev/null <<<"$repo_json"; then
  pass 'squash merge is enabled'
else
  fail 'squash merge is disabled'
fi

protection_json="$(gh api "repos/$repo/branches/$branch/protection" 2>/dev/null || true)"
if [[ -z "$protection_json" ]]; then
  fail "branch protection could not be read for $branch"
else
  if jq -e --arg check "$required_check" '
      .required_status_checks != null and
      .required_status_checks.strict == true and
      ((.required_status_checks.contexts // []) | index($check) != null)
    ' >/dev/null <<<"$protection_json"; then
    pass "branch protection requires strict status check: $required_check"
  else
    fail "branch protection does not require strict status check: $required_check"
  fi

  if jq -e '.allow_force_pushes.enabled == false and .allow_deletions.enabled == false' \
    >/dev/null <<<"$protection_json"; then
    pass 'branch force-pushes and deletions are blocked'
  else
    fail 'branch force-pushes and deletions are not both blocked'
  fi
fi

if [[ -f .github/workflows/ci.yml ]] && rg -q '^  ci-gate:' .github/workflows/ci.yml \
  && rg -q '^    name: CI Gate$' .github/workflows/ci.yml; then
  pass 'ci.yml defines the CI Gate aggregate job'
else
  fail 'ci.yml does not define the expected CI Gate aggregate job'
fi

if [[ -f .github/dependabot.yml ]] \
  && rg -q "package-ecosystem: [\"']gradle[\"']" .github/dependabot.yml \
  && rg -q "package-ecosystem: [\"']github-actions[\"']" .github/dependabot.yml; then
  pass 'Dependabot configuration covers Gradle and GitHub Actions'
else
  fail 'Dependabot configuration is missing Gradle or GitHub Actions coverage'
fi

if [[ -f .github/workflows/dependabot-auto-merge.yml ]] \
  && rg -q "github\.actor == 'dependabot\[bot\]'" .github/workflows/dependabot-auto-merge.yml \
  && rg -q 'gh pr merge --auto' .github/workflows/dependabot-auto-merge.yml; then
  pass 'Dependabot auto-merge workflow is configured'
else
  fail 'Dependabot auto-merge workflow is missing or incomplete'
fi

actions_secrets="$(gh api "repos/$repo/actions/secrets" --jq '.secrets[]?.name' 2>/dev/null || true)"
if printf '%s\n' "$actions_secrets" | rg -Fxq "$required_secret"; then
  pass "Actions secret exists: $required_secret (name only)"
else
  fail "Actions secret is missing: $required_secret"
fi

dependabot_secrets="$(gh api "repos/$repo/dependabot/secrets" --jq '.secrets[]?.name' 2>/dev/null || true)"
if printf '%s\n' "$dependabot_secrets" | rg -Fxq "$required_secret"; then
  pass "Dependabot secret exists: $required_secret (name only)"
else
  fail "Dependabot secret is missing: $required_secret"
fi

if [[ ! -f .github/CODEOWNERS ]]; then
  fail 'CODEOWNERS file is missing'
else
  codeowners_error_count="$(gh api "repos/$repo/codeowners/errors" --jq '.errors | length' 2>/dev/null || true)"
  if [[ "$codeowners_error_count" == '0' ]]; then
    pass 'GitHub reports no CODEOWNERS parse errors'
  else
    fail 'GitHub reports CODEOWNERS parse errors or the endpoint is unavailable'
  fi

  # These paths are explicitly called out in CODEOWNERS because mistakes there are expensive or
  # silent. Exact-pattern checks keep this doctor deterministic without pretending to implement
  # GitHub's complete CODEOWNERS glob language.
  required_codeowner_patterns=(
    '*'
    '/build-logic/'
    '/gradle/libs.versions.toml'
    '/gradle/verification-metadata.xml'
    '/app/dependencies/'
    '/.github/'
    '/konsist/'
    '/docs/adr/'
  )
  for pattern in "${required_codeowner_patterns[@]}"; do
    if awk -v expected="$pattern" '$1 == expected && NF >= 2 { found = 1 } END { exit !found }' \
      .github/CODEOWNERS; then
      pass "CODEOWNERS covers $pattern"
    else
      fail "CODEOWNERS does not contain a valid owner for $pattern"
    fi
  done
fi

environments_json="$(gh api "repos/$repo/environments" 2>/dev/null || true)"
workflow_environments="$(rg --no-heading --no-filename -o 'environment:\s*[A-Za-z0-9_.-]+' .github/workflows 2>/dev/null \
  | sed -E 's/^environment:\s*//' | sort -u)"

if [[ -z "$environments_json" ]]; then
  fail 'GitHub environments could not be read'
else
  configured_environments="$(jq -r '.environments[]?.name' <<<"$environments_json")"
  if [[ -z "$workflow_environments" ]]; then
    if [[ -z "$configured_environments" ]]; then
      pass 'no GitHub environments are configured or referenced; release deployment remains adopter-owned'
    else
      note "configured GitHub environments are currently unused: $configured_environments"
    fi
  else
    for environment in $workflow_environments; do
      if printf '%s\n' "$configured_environments" | rg -Fxq "$environment"; then
        pass "workflow environment exists: $environment"
      else
        fail "workflow environment is not configured: $environment"
      fi
    done
  fi
fi

if ((failures == 0)); then
  printf 'Repository doctor: all checks passed for %s (%s).\n' "$repo" "$branch"
else
  printf 'Repository doctor: %d check(s) failed for %s (%s).\n' "$failures" "$repo" "$branch"
  exit 1
fi
