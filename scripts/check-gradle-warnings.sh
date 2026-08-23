#!/usr/bin/env bash
set -euo pipefail

log_file="${1:?usage: check-gradle-warnings.sh <gradle-output-log>}"

if [[ ! -f "$log_file" ]]; then
  echo "::error::Gradle warning log does not exist: $log_file"
  exit 1
fi

# These are the two remaining warnings recorded in docs/gradle-compatibility.md. They are
# intentionally allow-listed by their stable message prefix, not by line number or Gradle version.
# When either upstream contract disappears, the informational notice below prompts a cleanup of
# the inventory instead of silently letting the documentation drift.
known_patterns=(
  'The ReportingExtension.file\(String\) method has been deprecated\.'
  'Using a Project object as a dependency notation has been deprecated\.'
)

# The stacktrace hint also contains the word "deprecation"; omit that secondary line so the
# detector reports warning headers rather than implementation details of the warning machinery.
warning_lines="$(rg -n -i 'deprecated' "$log_file" | rg -v 'full stack trace of this deprecation warning' || true)"
if [[ -z "$warning_lines" ]]; then
  echo "No Gradle deprecation warnings emitted. Remove any obsolete entries from docs/gradle-compatibility.md."
  exit 0
fi

unexpected_lines="$warning_lines"
for pattern in "${known_patterns[@]}"; do
  unexpected_lines="$(printf '%s\n' "$unexpected_lines" | rg -v "$pattern" || true)"
done

if [[ -n "$unexpected_lines" ]]; then
  echo "::error::Unexpected Gradle deprecation output; update the owning code or explicitly document an upstream contract."
  printf '%s\n' "$unexpected_lines"
  exit 1
fi

echo "Only the documented upstream Gradle warnings were emitted:"
printf '%s\n' "$warning_lines"
