#!/bin/bash
#
# store-frames.sh - turn raw app screenshots into marketable store frames.
#
# A raw screencap sells the UI; a store frame sells the app. This wraps each capture in
# the layout every polished listing uses: a headline on a brand background, with the
# screen shown in a device mockup below it.
#
# It renders HTML with headless Chrome, because that is the one capable renderer already
# on this machine (Puppeteer's Chromium, cached by mermaid-cli) and because HTML/CSS
# gives full control over gradients, type and shadows with no image library to install.
#
# Input:  fastlane/metadata/android/<locale>/images/phoneScreenshots/*.jpg (from play-listing.sh)
#         fastlane/metadata/android/<locale>/framing-captions.tsv          (filename<TAB>headline)
# Output: fastlane/metadata/android/<locale>/images/phoneScreenshotsFramed/*.png (1080x1920, 9:16)
#
# Captions live beside the rest of that locale's listing copy, so adding a language is the
# same move as for any other store text: copy the directory and translate. The layout is
# shared and never edited per locale.
#
# Usage:
#   scripts/store-frames.sh [locale]      # default: en-US
#
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly LOCALE="${1:-en-US}"
readonly SHOTS_DIR="$REPO_ROOT/fastlane/metadata/android/$LOCALE/images/phoneScreenshots"
readonly CAPTIONS="$REPO_ROOT/fastlane/metadata/android/$LOCALE/framing-captions.tsv"
readonly OUT_DIR="$REPO_ROOT/fastlane/metadata/android/$LOCALE/images/phoneScreenshotsFramed"
readonly WORK_DIR="$REPO_ROOT/build/store-frames/$LOCALE"

# Play-compliant canvas: 9:16, inside the 320-3840 per-side bounds. See play-listing.sh
# for the full set of Play limits - this file only needs the canvas.
readonly W=1080
readonly H=1920

# Brand palette, lifted from core/designsystem/.../theme/Color.kt so the frames read as
# the same product as the app.
readonly BRAND_DARK="#001945"
readonly BRAND_MID="#0047AB"
readonly BRAND_LIGHT="#D8E2FF"

die() { printf '\033[31merror: %s\033[0m\n' "$*" >&2; exit 1; }

find_chrome() {
  local c
  for c in \
    "$HOME"/.cache/puppeteer/chrome-headless-shell/*/chrome-headless-shell-*/chrome-headless-shell \
    "$HOME"/.cache/puppeteer/chrome/*/chrome-mac*/Google\ Chrome\ for\ Testing.app/Contents/MacOS/Google\ Chrome\ for\ Testing \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "/Applications/Chromium.app/Contents/MacOS/Chromium"; do
    [ -x "$c" ] && { printf '%s' "$c"; return 0; }
  done
  return 1
}

CHROME="$(find_chrome || true)"
[ -n "$CHROME" ] || die "no Chrome/Chromium found. Run 'npx -y puppeteer browsers install chrome-headless-shell' once."
[ -f "$CAPTIONS" ] || die "no captions file at $CAPTIONS"
[ -d "$SHOTS_DIR" ] || die "no screenshots at $SHOTS_DIR - run scripts/play-listing.sh capture first"

mkdir -p "$OUT_DIR" "$WORK_DIR"

# One HTML page per frame. The screenshot is embedded as a data URI so the page has no
# external requests and renders identically regardless of working directory.
emit_html() {
  local img_b64="$1" headline="$2" out="$3"
  cat > "$out" <<HTML
<!doctype html><html><head><meta charset="utf-8"><style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width:${W}px; height:${H}px; overflow:hidden;
    background:
      radial-gradient(120% 70% at 50% 0%, ${BRAND_MID} 0%, ${BRAND_DARK} 62%),
      ${BRAND_DARK};
    font-family:-apple-system,"SF Pro Display","Helvetica Neue",Arial,sans-serif;
    display:flex; flex-direction:column; align-items:center;
  }
  .headline {
    margin:132px 88px 0; text-align:center;
    font-size:78px; line-height:1.12; font-weight:700; letter-spacing:-1.6px;
    color:#fff; text-wrap:balance;
  }
  .rule { width:104px; height:8px; border-radius:4px; background:${BRAND_LIGHT}; margin-top:44px; opacity:.85; }
  /* The device bleeds off the bottom edge - it reads as "there is more app below". */
  .device {
    position:relative; margin-top:84px; width:760px; height:1352px;
    border-radius:60px; padding:13px; background:#0d1017;
    box-shadow:0 46px 90px rgba(0,0,0,.55), 0 0 0 2px rgba(255,255,255,.09) inset;
  }
  .device img { width:100%; height:100%; object-fit:cover; object-position:top center; border-radius:48px; display:block; }
</style></head><body>
  <div class="headline">${headline}</div>
  <div class="rule"></div>
  <div class="device"><img src="data:image/jpeg;base64,${img_b64}"></div>
</body></html>
HTML
}

count=0
while IFS=$'\t' read -r name headline; do
  # Skip blanks and comments.
  [ -z "${name:-}" ] && continue
  case "$name" in \#*) continue ;; esac
  [ -n "${headline:-}" ] || die "no headline for '$name' in $(basename "$CAPTIONS")"

  src="$SHOTS_DIR/$name"
  [ -f "$src" ] || die "captions reference '$name', which is not in $SHOTS_DIR"

  base="${name%.*}"
  html="$WORK_DIR/$base.html"
  emit_html "$(base64 -i "$src" | tr -d '\n')" "$headline" "$html"

  "$CHROME" --headless --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --window-size="$W,$H" \
    --screenshot="$OUT_DIR/$base.png" "file://$html" >/dev/null 2>&1

  [ -s "$OUT_DIR/$base.png" ] || die "Chrome produced no output for $base"
  printf '\033[32m  framed  %s\033[0m  (%s)\n' "$base.png" "$headline"
  count=$((count + 1))
done < "$CAPTIONS"

rm -rf "$WORK_DIR"
printf '\n\033[32m%d frames in %s\033[0m\n' "$count" "$OUT_DIR"
echo "Upload EITHER these or the raw phoneScreenshots/ - Play takes one set, not both."
