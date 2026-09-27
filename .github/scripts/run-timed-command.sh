#!/usr/bin/env bash
set -uo pipefail

if [[ $# -eq 0 ]]; then
  printf 'ERROR command is required.\n' >&2
  exit 2
fi

if [[ -z "${GITHUB_OUTPUT:-}" ]]; then
  printf 'ERROR GITHUB_OUTPUT is required.\n' >&2
  exit 2
fi

started_at_epoch="$(date +%s)"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
"$@"
command_status=$?
finished_at_epoch="$(date +%s)"
finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
elapsed_seconds=$(( finished_at_epoch - started_at_epoch ))

{
  printf 'started_at=%s\n' "$started_at"
  printf 'finished_at=%s\n' "$finished_at"
  printf 'elapsed_seconds=%s\n' "$elapsed_seconds"
  printf 'exit_code=%s\n' "$command_status"
} >> "$GITHUB_OUTPUT"
exit "$command_status"
