#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$ROOT/app/build/outputs/release-smoke"
ARCHIVE_DIR="$OUTPUT_DIR/artifacts"
MANIFEST="$OUTPUT_DIR/manifest.txt"

artifacts=(
  "app/build/outputs/apk/releaseSmoke/app-releaseSmoke.apk"
  "app/build/outputs/apk/releaseSmoke/baselineProfiles/0/app-releaseSmoke.dm"
  "app/build/outputs/apk/releaseSmoke/baselineProfiles/1/app-releaseSmoke.dm"
  "app/build/outputs/mapping/releaseSmoke/mapping.txt"
  "feature/beerdetail/build/outputs/apk/releaseSmoke/beerdetail-releaseSmoke.apk"
  "feature/beerbrowse/build/outputs/apk/releaseSmoke/beerbrowse-releaseSmoke.apk"
)

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

for relative_path in "${artifacts[@]}"; do
  path="$ROOT/$relative_path"
  if [[ ! -s "$path" ]]; then
    printf 'Missing or empty release-smoke artifact: %s\n' "$relative_path" >&2
    exit 1
  fi
done

rm -rf "$ARCHIVE_DIR"
mkdir -p "$ARCHIVE_DIR"

{
  printf 'source_revision=%s\n' "$(git -C "$ROOT" rev-parse HEAD)"
  if [[ -n "$(git -C "$ROOT" status --porcelain)" ]]; then
    printf 'working_tree=dirty\n'
  else
    printf 'working_tree=clean\n'
  fi
  printf 'variant=releaseSmoke\n'
  for relative_path in "${artifacts[@]}"; do
    path="$ROOT/$relative_path"
    archive_name="$(printf '%s' "$relative_path" | tr '/' '_')"
    cp "$path" "$ARCHIVE_DIR/$archive_name"
    printf 'artifact=%s sha256=%s bytes=%s\n' \
      "$relative_path" \
      "$(sha256 "$path")" \
      "$(wc -c < "$path" | tr -d ' ')"
  done
} > "$MANIFEST"

cp "$MANIFEST" "$ARCHIVE_DIR/manifest.txt"
printf 'Verified release-smoke artifacts in %s\n' "$OUTPUT_DIR"
