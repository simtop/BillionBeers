#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
hooks_dir=$(git -C "$repo_root" rev-parse --git-path hooks)
mkdir -p "$hooks_dir"
install -m 755 "$repo_root/config/git-hooks/pre-commit" "$hooks_dir/pre-commit"
