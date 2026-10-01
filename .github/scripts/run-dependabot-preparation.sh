#!/usr/bin/env bash
# Observe the reference/candidate graph without hiding its failure status.
set -euo pipefail
case "${1:-}" in reference|candidate) ;; *) exit 1 ;; esac
metrics="$RUNNER_TEMP/dependency-preparation-resources.log"
sample() {
  {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
    free -m
    df -h .
    ps -eo pid,rss,comm --sort=-rss | head -12 || true
  } >> "$metrics" 2>&1
}
sample
(
  while sleep 30; do sample; done
) &
sampler=$!
trap 'kill "$sampler" 2>/dev/null || true; wait "$sampler" 2>/dev/null || true; sample' EXIT
start=$SECONDS
set +e
make "verification-metadata-$1"
status=$?
set -e
echo "Preparation graph: $1; elapsed seconds: $((SECONDS - start)); exit status: $status" >> "$metrics"
exit "$status"
