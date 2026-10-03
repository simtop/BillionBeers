#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in linux|web|managed|apple) ;; *) exit 1 ;; esac
case "${2:-reference}" in reference|candidate) ;; *) exit 1 ;; esac
if [ "$#" -ne 3 ]; then exit 1; fi
lane="$1"
mode="$2"
log="$3"
worker_pid=""
tee_pid=""
termination_signal=""
fifo_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/dependency-preparation-fifo.XXXXXX")
fifo="$fifo_dir/output"
mkfifo "$fifo"

forward_signal() {
  if [ -z "$termination_signal" ]; then
    termination_signal="$1"
  fi
  if [ -n "$worker_pid" ]; then
    kill -s "$1" "$worker_pid" 2>/dev/null || true
  fi
}
trap 'forward_signal INT' INT
trap 'forward_signal TERM' TERM

tee "$log" < "$fifo" &
tee_pid=$!
bash "$(dirname "${BASH_SOURCE[0]}")/run-dependabot-preparation.sh" "$lane" "$mode" \
  > "$fifo" 2>&1 &
worker_pid=$!
if [ -n "$termination_signal" ]; then
  kill -s "$termination_signal" "$worker_pid" 2>/dev/null || true
fi

set +e
while :; do
  wait "$worker_pid"
  status=$?
  # A trapped signal can interrupt wait before the worker exits. Reap it fully,
  # even if another TERM/INT interrupts a later wait as well.
  if ! kill -0 "$worker_pid" 2>/dev/null; then
    break
  fi
done
# Bash can report an interrupted wait just as the child exits. Re-wait to collect
# its cached, actual exit status rather than returning the signal's wait status.
wait "$worker_pid"
status=$?
set -e
while :; do
  wait "$tee_pid" || true
  if ! kill -0 "$tee_pid" 2>/dev/null; then
    break
  fi
done
rm "$fifo"
rmdir "$fifo_dir"

classification=success
if [ "$status" -ne 0 ]; then
  if grep -Fq "is not verified because it is not listed in the verification metadata" "$log"; then
    classification=unlisted_artifact
  elif grep -Eiq "checksum.*(failed|mismatch|expected)|expected.*checksum|has been compromised" "$log"; then
    classification=checksum_mismatch
  elif grep -Eq "^> Task .* FAILED([[:space:]]|$)|BUILD FAILED" "$log"; then
    classification=build_test_or_policy_failure
  elif [ "$status" -eq 143 ] || [ "$status" -eq 130 ] || grep -Eq "(^|[[:space:]])Terminated$" "$log"; then
    # Process exit alone is diagnostic. Recovery separately requires the actual
    # runner shutdown marker and exit 143 from the completed Actions job log.
    classification=process_termination
  else
    classification=build_test_or_policy_failure
  fi
fi
echo "classification=$classification" >> "$GITHUB_OUTPUT"
exit "$status"
