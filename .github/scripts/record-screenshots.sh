#!/usr/bin/env bash
set -euo pipefail

record_all="${RECORD_ALL:-false}"
use_clean="${USE_CLEAN:-false}"
modules="${MODULES:-}"

if [[ "$record_all" == "true" ]]; then
  if [[ "$use_clean" == "true" ]]; then
    exec ./gradlew cleanRecordPaparazziDebug
  fi
  exec ./gradlew recordPaparazziDebug
fi

if [[ -z "$modules" ]]; then
  printf 'ERROR: set RECORD_ALL=true or provide MODULES.\n' >&2
  exit 2
fi

# Build an argv array so module input cannot become shell syntax or be split unexpectedly.
gradle_args=()
for module in $modules; do
  if [[ ! "$module" =~ ^:[A-Za-z0-9_-]+(:[A-Za-z0-9_-]+)*$ ]]; then
    printf 'ERROR: invalid Gradle module path: %s\n' "$module" >&2
    exit 2
  fi
  if [[ "$use_clean" == "true" ]]; then
    gradle_args+=("${module}:clean")
  fi
  gradle_args+=("${module}:recordPaparazziDebug")
done

printf 'Running command: ./gradlew'
printf ' %q' "${gradle_args[@]}"
printf '\n'
exec ./gradlew "${gradle_args[@]}"
