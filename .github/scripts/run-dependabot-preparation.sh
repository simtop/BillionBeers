#!/usr/bin/env bash
# Observe the reference/candidate graph without hiding its failure status.
set -euo pipefail
case "${1:-}" in linux|web|managed|apple) ;; *) exit 1 ;; esac
lane="$1"
mode="${2:-reference}"
case "$mode" in reference|candidate) ;; *) exit 1 ;; esac
metrics="$RUNNER_TEMP/dependency-preparation-resources.log"
echo "Evidence identity: lane=$lane mode=${PREPARATION_MODE:-$mode} graph=$mode run=${GITHUB_RUN_ID:-local} attempt=${GITHUB_RUN_ATTEMPT:-local} head=${PREPARATION_HEAD:-unknown} source=${PREPARATION_SOURCE_REF:-unknown}" >> "$metrics"
sample() {
  {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
    if command -v free >/dev/null 2>&1; then
      free -m
    elif command -v vm_stat >/dev/null 2>&1; then
      vm_stat
      sysctl hw.memsize
    fi
    df -h .
    if ps -eo pid,rss,comm --sort=-rss >/dev/null 2>&1; then
      ps -eo pid,rss,comm --sort=-rss | head -12 || true
    else
      ps -axo pid,rss,comm | sort -nr -k 2 | head -12 || true
    fi
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
make verification-metadata-lane "LANE=$lane" "MODE=$mode"
status=$?
set -e
echo "Preparation lane: $lane; mode: $mode; elapsed seconds: $((SECONDS - start)); exit status: $status" >> "$metrics"
exit "$status"
