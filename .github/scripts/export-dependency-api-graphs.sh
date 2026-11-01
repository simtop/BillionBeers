#!/usr/bin/env bash
set -euo pipefail
base_sha="$1"
output="$2"
shift 2
runner=("$@")
repository="$PWD"
mkdir -p "$output"
output="$(cd "$output" && pwd)"
base_tree="$(mktemp -d "${TMPDIR:-/tmp}/dependency-api-base.XXXXXX")"
trap 'rm -rf "$base_tree"' EXIT
git archive "$base_sha" | tar -x -C "$base_tree"
if [[ -f local.properties ]]; then cp local.properties "$base_tree/local.properties"; fi
init="$repository/.github/scripts/resolve-dependency-api-artifacts.init.gradle"
(
  cd "$base_tree"
  "${runner[@]}" --no-configuration-cache -I "$init" \
    "-PdependencyApiExportGraph=$output/before.txt" :app:exportDependencyApiGraph
)
"${runner[@]}" --no-configuration-cache -I "$init" \
  "-PdependencyApiExportGraph=$output/after.txt" :app:exportDependencyApiGraph
