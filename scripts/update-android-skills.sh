#!/usr/bin/env bash
#
# Sync the official Android skills (https://github.com/android/skills) into
# .claude/skills/ so Claude Code agents can use them. Re-run any time to pull the
# latest set — newly added upstream skills are installed automatically and removed
# upstream skills are pruned. Locally-authored skills (anything NOT present in the
# upstream repo, e.g. `billionbeers-android`) are always left untouched.
#
# Three ways to control a vendored skill:
#   • .android-skills-ignore   — never install these names (opt out entirely).
#   • .android-skill-pinned    — a file inside a vendored skill dir; the sync will
#     NOT overwrite or prune that skill, so local edits to its SKILL.md/description
#     survive. Trade-off: a pinned skill no longer tracks upstream updates. Delete
#     the marker to resume tracking. Use this whenever you customize a vendored skill.
#
# Usage: scripts/update-android-skills.sh   (or: make update-android-skills)

set -euo pipefail

REPO="https://github.com/android/skills.git"
# Resolve repo root from this script's location so it works from any cwd.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKILLS_DIR="$ROOT/.claude/skills"
# Marker file dropped into each vendored skill so we know which dirs we own.
MARKER=".android-skill-source"
# Presence of this file in a skill dir means "locally customized — do not touch".
PIN_MARKER=".android-skill-pinned"
# Opt-out list: skill names (one per line, # comments) we never install.
IGNORE_FILE="$SKILLS_DIR/.android-skills-ignore"

mkdir -p "$SKILLS_DIR"

# True if $1 is listed in the ignore file.
is_ignored() {
  [ -f "$IGNORE_FILE" ] || return 1
  grep -vE '^[[:space:]]*(#|$)' "$IGNORE_FILE" | sed 's/[[:space:]]*$//' | grep -qxF "$1"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "→ Cloning $REPO ..."
git clone --depth 1 -q "$REPO" "$TMP/skills"

# 1. Prune vendored skills that no longer exist upstream (only ones we own).
#    (bash 3.2 compatible — no associative arrays: use a newline-delimited list.)
UPSTREAM_NAMES="$(find "$TMP/skills" -name SKILL.md -exec sed -n 's/^name:[[:space:]]*//p' {} \; | head -n1000)"

for dir in "$SKILLS_DIR"/*/; do
  [ -e "$dir" ] || continue
  name="$(basename "$dir")"
  # Never prune a pinned (locally customized) skill, even if dropped upstream.
  [ -f "$dir$PIN_MARKER" ] && continue
  if [ -f "$dir$MARKER" ] && ! printf '%s\n' "$UPSTREAM_NAMES" | grep -qxF "$name"; then
    echo "  ✗ pruning removed-upstream skill: $name"
    rm -rf "$dir"
  fi
done

# 2. Install / refresh every upstream skill, keyed by its frontmatter `name`.
count=0
while IFS= read -r skillmd; do
  src="$(dirname "$skillmd")"
  name="$(sed -n 's/^name:[[:space:]]*//p' "$skillmd" | head -n1)"
  if [ -z "$name" ]; then
    echo "  ! skipping (no name): ${skillmd#$TMP/skills/}" >&2
    continue
  fi
  dest="$SKILLS_DIR/$name"
  # Honour the opt-out list: skip install and remove any stale copy.
  if is_ignored "$name"; then
    [ -e "$dest" ] && rm -rf "$dest"
    echo "  ⊘ ignored (in .android-skills-ignore): $name"
    continue
  fi
  # Pinned: keep the local (customized) copy, don't overwrite with upstream.
  if [ -f "$dest/$PIN_MARKER" ]; then
    echo "  ⟳ pinned — kept local edits, not tracking upstream: $name"
    continue
  fi
  # Guard: never clobber a local (non-vendored) skill that shares the name.
  if [ -d "$dest" ] && [ ! -f "$dest/$MARKER" ]; then
    echo "  ! name clash with LOCAL skill '$name' — skipping upstream copy" >&2
    continue
  fi
  rm -rf "$dest"
  cp -R "$src" "$dest"
  printf 'Vendored from %s\nUpstream path: %s\nUpdated: %s\nDo not edit by hand; run scripts/update-android-skills.sh\n' \
    "android/skills" "${src#$TMP/skills/}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$dest/$MARKER"
  count=$((count + 1))
  echo "  ✓ $name"
done < <(find "$TMP/skills" -name SKILL.md | sort)

echo "→ Done. $count Android skill(s) installed in .claude/skills/"
