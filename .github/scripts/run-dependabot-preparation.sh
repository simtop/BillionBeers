#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in linux|web|managed|apple) ;; *) exit 1 ;; esac
mode="${2:-reference}"
case "$mode" in reference|candidate) ;; *) exit 1 ;; esac
exec python3 "$(dirname "${BASH_SOURCE[0]}")/run-dependabot-preparation.py" "$1" "$mode"
