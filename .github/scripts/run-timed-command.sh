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

started_at="$(date +%s)"
"$@"
command_status=$?
elapsed_seconds=$(( $(date +%s) - started_at ))

printf 'elapsed_seconds=%s\n' "$elapsed_seconds" >> "$GITHUB_OUTPUT"
exit "$command_status"
