#!/bin/bash
#
# store-frames.sh - turn raw app screenshots into marketable store frames.
#
# A raw screencap sells the UI; a store frame sells the app. This wraps each capture in
# the layout every polished listing uses: a headline on a brand background, with the
# complete screen below it, without a synthetic device bezel.
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
[[ "$LOCALE" =~ ^[a-z]{2}-[A-Z]{2}$ ]] || { echo "Invalid locale" >&2; exit 1; }
readonly SHOTS_DIR="$REPO_ROOT/fastlane/metadata/android/$LOCALE/images/phoneScreenshots"
readonly CAPTIONS="$REPO_ROOT/fastlane/metadata/android/$LOCALE/framing-captions.tsv"
readonly OUT_DIR="$REPO_ROOT/fastlane/metadata/android/$LOCALE/images/phoneScreenshotsFramed"
readonly WORK_DIR="$REPO_ROOT/build/store-frames/$LOCALE"

# Play-compliant canvas: 9:16, inside the 320-3840 per-side bounds. See play-listing.sh
# and check-play-screenshots.py for Play limits - this file only needs the canvas.
readonly W=1080
readonly H=1920

# Brand palette, lifted from shared/designsystem/.../theme/Color.kt so the frames read as
# the same product as the app.
readonly BRAND_DARK="#321A05"
readonly BRAND_MID="#875016"
readonly BRAND_LIGHT="#F4DFBB"

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
  @font-face { font-family:"Plus Jakarta Sans"; src:url(data:font/ttf;base64,${FONT_B64}) format("truetype"); font-weight:100 900; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width:${W}px; height:${H}px; overflow:hidden;
    background:
      radial-gradient(120% 70% at 50% 0%, ${BRAND_MID} 0%, ${BRAND_DARK} 62%),
      ${BRAND_DARK};
    font-family:"Plus Jakarta Sans",sans-serif;
    display:flex; flex-direction:column; align-items:center;
  }
  .headline {
    margin:64px 72px 0; text-align:center; flex-shrink:0;
    font-size:68px; line-height:1.12; font-weight:700; letter-spacing:-1.6px;
    color:#fff; text-wrap:balance;
  }
  .rule { flex-shrink:0; width:104px; height:8px; border-radius:4px; background:${BRAND_LIGHT}; margin-top:18px; opacity:.85; }
  /* Preserve the complete UI; captions occupy less than 20% of the canvas. */
  .device {
    position:relative; margin-top:38px; width:860px; height:1529px; flex-shrink:0;
    border-radius:24px; padding:0; background:#0d1017; overflow:hidden;
    box-shadow:0 46px 90px rgba(0,0,0,.55), 0 0 0 2px rgba(255,255,255,.09) inset;
  }
  .device img { width:100%; height:100%; object-fit:contain; object-position:top center; border-radius:24px; display:block; }
</style></head><body>
  <div class="headline">${headline}</div>
  <div class="rule"></div>
  <div class="device"><img src="data:image/jpeg;base64,${img_b64}"></div>
<script>
document.fonts.ready.then(() => {
  const headline = document.querySelector('.headline').getBoundingClientRect();
  const screen = document.querySelector('.device').getBoundingClientRect();
  const image = document.querySelector('img');
  const valid = headline.height <= ${H} * .2 && screen.bottom <= ${H} &&
    screen.top >= headline.bottom && image.complete && image.naturalWidth === ${W} &&
    document.fonts.check('700 68px "Plus Jakarta Sans"');
  document.body.setAttribute('data-frame-check', valid ? 'ok' : 'failed');
});
</script>
</body></html>
HTML
}

FONT_B64=$(base64 -i "$REPO_ROOT/shared/designsystem/src/commonMain/composeResources/font/plusjakarta-variable.ttf" | tr -d '\n')
count=0
while IFS=$'\t' read -r name headline; do
  # Skip blanks and comments.
  [ -z "${name:-}" ] && continue
  case "$name" in \#*) continue ;; esac
  [ -n "${headline:-}" ] || die "no headline for '$name' in $(basename "$CAPTIONS")"

  [[ "$name" =~ ^[a-zA-Z0-9_-]+\.jpg$ ]] || die "invalid screenshot filename: $name"
  src="$SHOTS_DIR/$name"
  [ -f "$src" ] || die "captions reference '$name', which is not in $SHOTS_DIR"
  [ "$(sips -g pixelWidth "$src" 2>/dev/null | awk '/pixelWidth:/{print $2}')" = "$W" ] && \
    [ "$(sips -g pixelHeight "$src" 2>/dev/null | awk '/pixelHeight:/{print $2}')" = "$H" ] || \
    die "phone frame input must be ${W}x${H}: $name"

  base="${name%.*}"
  html="$WORK_DIR/$base.html"
  # Captions are text, with only an explicit <br> line-break marker supported.
  escaped=$(python3 -c 'import html,sys; print("<br>".join(html.escape(p) for p in sys.argv[1].split("<br>")))' "$headline")
  emit_html "$(base64 -i "$src" | tr -d '\n')" "$escaped" "$html"

  "$CHROME" --headless --disable-gpu --hide-scrollbars \
    --virtual-time-budget=1500 --force-device-scale-factor=1 --window-size="$W,$H" \
    --dump-dom --screenshot="$OUT_DIR/$base.png" "file://$html" > "$WORK_DIR/$base.dom" 2> "$WORK_DIR/$base.log"
  grep -q 'data-frame-check="ok"' "$WORK_DIR/$base.dom" || \
    die "frame overflows, font failed, or image was not ready: $base (see $WORK_DIR)"

  [ -s "$OUT_DIR/$base.png" ] || die "Chrome produced no output for $base"
  printf '\033[32m  framed  %s\033[0m  (%s)\n' "$base.png" "$headline"
  count=$((count + 1))
done < "$CAPTIONS"

rm -rf "$WORK_DIR"
printf '\n\033[32m%d frames in %s\033[0m\n' "$count" "$OUT_DIR"
echo "Upload EITHER these or the raw phoneScreenshots/ - Play takes one set, not both."
