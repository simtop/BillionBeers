#!/usr/bin/env bash
#
# Removal probes for the AGP/Kotlin compatibility properties in gradle.properties.
#
# This intentionally uses a temporary copy. A successful build with the properties present only
# proves that the compatibility path still works; it says nothing about whether the properties can
# be removed. Each probe starts from a fresh copy and removes one logical exception before running
# the smallest task that exercises its owner.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_command="${GRADLE_COMMAND:-./gradlew}"
probe_root="$(mktemp -d "${TMPDIR:-/tmp}/billionbeers-gradle-compat.XXXXXX")"
trap 'rm -rf "$probe_root"' EXIT

if ! command -v rsync >/dev/null 2>&1; then
  echo "error: rsync is required to create the isolated removal probe copy" >&2
  exit 1
fi

copy_project() {
  local destination="$1"
  mkdir -p "$destination"
  rsync -a \
    --exclude '.git/' \
    --exclude '.gradle/' \
    --exclude 'gradle-user-home/' \
    --exclude 'build/' \
    --exclude 'profile-out/' \
    --exclude 'rod/' \
    --exclude '.claude/' \
    --exclude '.agents/' \
    --exclude '.idea/' \
    --exclude '.vscode/' \
    "$repo_root/" "$destination/"
}

remove_properties() {
  local project_dir="$1"
  shift
  local property
  local expression
  for property in "$@"; do
    expression="^${property//./\\.}=.*$"
    sed -i.bak "/${expression}/d" "$project_dir/gradle.properties"
  done
}

run_probe() {
  local name="$1"
  local description="$2"
  shift 2
  local project_dir="$probe_root/$name"
  copy_project "$project_dir"

  local -a properties=()
  while [[ "$1" != "--" ]]; do
    properties+=("$1")
    shift
  done
  shift
  local -a task_args=("$@")

  remove_properties "$project_dir" "${properties[@]}"
  echo "==> $name: $description"
  if (
    cd "$project_dir"
    "$gradle_command" "${task_args[@]}" \
      --no-configuration-cache \
      --warning-mode all \
      --console=plain
  ); then
    echo "PASS $name: removed ${properties[*]}"
  else
    echo "FAIL $name: at least one removed compatibility property is still required" >&2
    return 1
  fi
}

# The two Kotlin properties are one logical suppression: AGP/Kotlin coexistence is configured
# during full-graph configuration, before any Android task is selected.
run_probe \
  builtin_kotlin \
  'configure the full project without the built-in-Kotlin suppressions' \
  systemProp.kotlin.ignore.agp.builtin.kotlin \
  systemProp.kotlin.diagnostics.suppress.AgpWithBuiltInKotlinApplied \
  -- \
  help

run_probe \
  unique_package_names \
  'assemble the debug app without legacy shared-package behavior' \
  android.uniquePackageNames \
  -- \
  :app:assembleDebug

run_probe \
  dependency_constraints \
  'resolve the release runtime graph with AGP dependency constraints enabled' \
  android.dependency.useConstraints \
  -- \
  :app:dependencies \
  --configuration \
  releaseRuntimeClasspath

run_probe \
  r8_strict_full_mode \
  'assemble the minified release-smoke app with strict full-mode keep rules' \
  android.r8.strictFullModeForKeepRules \
  -- \
  :app:assembleReleaseSmoke
