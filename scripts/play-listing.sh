#!/bin/bash
#
# play-listing.sh - prepare Google Play store listing assets for BillionBeers.
#
# This script does the MECHANICAL half of a listing refresh: it puts the device into a
# deterministic state, captures Play-compliant screenshots, and validates every asset
# against Play's published limits. Deciding *which* screens to shoot and *what the copy
# says* needs judgement - that lives in the `play-listing` skill, which drives this.
#
# It deliberately NEVER talks to a Play publishing API. It produces files; you upload
# them in the Play Console yourself.
#
# Usage:
#   scripts/play-listing.sh init                 Scaffold the metadata tree with templates
#   scripts/play-listing.sh prepare              Put the device in capture state (9:16 + clean status bar)
#   scripts/play-listing.sh capture <name>       Capture the current screen as <name>
#   scripts/play-listing.sh reset                Undo `prepare` - ALWAYS run when finished
#   scripts/play-listing.sh check                Validate all listing assets against Play limits
#   scripts/play-listing.sh deeplink <uri>       Navigate the app via deep link (repeatable nav)
#
set -euo pipefail

# ---------------------------------------------------------------------------------------
# Play Store limits. Verified 2026-08-01 against Google's published requirements:
#   Preview assets:  https://support.google.com/googleplay/android-developer/answer/9866151
#   Store listing:   https://support.google.com/googleplay/android-developer/answer/13393723
# If Play changes these, edit HERE - nothing else in this script hardcodes a limit.
# ---------------------------------------------------------------------------------------
readonly MAX_TITLE_CHARS=30
readonly MAX_SHORT_DESC_CHARS=80
readonly MAX_FULL_DESC_CHARS=4000
readonly MIN_SCREENSHOTS=2          # Play's hard minimum
readonly REC_SCREENSHOTS=4          # recommended minimum to be eligible for featuring
readonly MAX_SCREENSHOTS=8
readonly MIN_SCREENSHOT_SIDE=320    # px, shortest side
readonly MAX_SCREENSHOT_SIDE=3840   # px, longest side
readonly MAX_SCREENSHOT_BYTES=$((8 * 1024 * 1024))
readonly ICON_SIZE=512              # 512x512, 32-bit PNG (alpha allowed)
readonly FEATURE_GRAPHIC_W=1024     # 1024x500, JPEG or 24-bit PNG, NO alpha
readonly FEATURE_GRAPHIC_H=500
# Screenshots must be 16:9 or 9:16. A stock AVD is 1080x2400 (9:20), which does NOT
# qualify - so `prepare` resizes the display to a true 9:16 instead of letterboxing
# afterwards. That keeps captures pixel-native with no bars and no rescaling.
readonly CAPTURE_W=1080
readonly CAPTURE_H=1920

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly METADATA_DIR="$REPO_ROOT/fastlane/metadata/android/en-US"
readonly SHOTS_DIR="$METADATA_DIR/images/phoneScreenshots"
readonly RAW_DIR="$REPO_ROOT/build/play-listing-raw"
readonly APP_ID="com.simtop.billionbeers"
readonly LAUNCHER="$APP_ID/.presentation.MainActivity"
readonly DEEPLINK_SCHEME="billionbeers"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"

red()  { printf '\033[31m%s\033[0m\n' "$*"; }
grn()  { printf '\033[32m%s\033[0m\n' "$*"; }
ylw()  { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { red "error: $*" >&2; exit 1; }

need_device() {
  [ -n "$ADB" ] && [ -x "$ADB" ] || die "adb not found. Set ANDROID_HOME, or see the billionbeers-android skill."
  local n
  n=$("$ADB" devices | grep -cw "device" || true)
  [ "$n" -ge 1 ] || die "no device/emulator attached. Boot one first (billionbeers-android skill §1)."
}

# Resizable AVDs expose more than one display; screencap needs to be told which one.
# Empty output means a single-display device, where the flag can be omitted.
display_id() {
  "$ADB" shell dumpsys SurfaceFlinger --display-id 2>/dev/null | awk '/^Display /{print $2; exit}'
}

# --- image helpers (macOS `sips` only - no ImageMagick dependency) ----------------------
img_w() { sips -g pixelWidth  "$1" 2>/dev/null | awk '/pixelWidth:/{print $2}'; }
img_h() { sips -g pixelHeight "$1" 2>/dev/null | awk '/pixelHeight:/{print $2}'; }
file_bytes() { stat -f%z "$1" 2>/dev/null || stat -c%s "$1"; }

cmd_init() {
  mkdir -p "$SHOTS_DIR" "$METADATA_DIR/changelogs"
  local version
  version=$(grep -oE '^billionbeers\.versionName=.*' \
    "$REPO_ROOT/gradle.properties" | cut -d'=' -f2)

  [ -f "$METADATA_DIR/title.txt" ] || printf 'BillionBeers\n' > "$METADATA_DIR/title.txt"
  [ -f "$METADATA_DIR/short_description.txt" ] || \
    printf 'TODO: one line, %s characters max.\n' "$MAX_SHORT_DESC_CHARS" > "$METADATA_DIR/short_description.txt"
  [ -f "$METADATA_DIR/full_description.txt" ] || \
    printf 'TODO: the full listing body, %s characters max.\n' "$MAX_FULL_DESC_CHARS" > "$METADATA_DIR/full_description.txt"
  [ -z "$version" ] || [ -f "$METADATA_DIR/changelogs/$version.txt" ] || \
    printf 'TODO: what changed in version %s.\n' "$version" > "$METADATA_DIR/changelogs/$version.txt"

  grn "Scaffolded $METADATA_DIR"
  echo "Layout follows fastlane supply, so it stays usable if publishing is ever automated."
}

cmd_prepare() {
  need_device
  echo "Resizing display to ${CAPTURE_W}x${CAPTURE_H} (9:16 - Play requires 16:9 or 9:16)..."
  "$ADB" shell wm size "${CAPTURE_W}x${CAPTURE_H}" >/dev/null

  echo "Enabling SysUI demo mode for a clean, reproducible status bar..."
  "$ADB" shell settings put global sysui_demo_allowed 1 >/dev/null
  local b="am broadcast -a com.android.systemui.demo"
  "$ADB" shell "$b -e command enter" >/dev/null
  "$ADB" shell "$b -e command clock -e hhmm 1000" >/dev/null
  "$ADB" shell "$b -e command battery -e level 100 -e plugged false" >/dev/null
  "$ADB" shell "$b -e command network -e wifi show -e level 4" >/dev/null
  "$ADB" shell "$b -e command network -e mobile show -e datatype none -e level 4" >/dev/null
  "$ADB" shell "$b -e command notifications -e visible false" >/dev/null

  # The soft keyboard (and its coach marks) covers content the moment a text field gains
  # focus, which ruins any capture of a search screen. Disable it; `reset` restores it.
  local ime
  ime=$("$ADB" shell settings get secure default_input_method 2>/dev/null | tr -d '\r')
  if [ -n "$ime" ] && [ "$ime" != "null" ]; then
    "$ADB" shell ime disable "$ime" >/dev/null 2>&1 || true
    echo "Disabled the on-screen keyboard ($ime)."
  fi

  mkdir -p "$RAW_DIR" "$SHOTS_DIR"
  grn "Device ready. Capture with: $0 capture <name>"
  ylw "Run '$0 reset' when finished - the device stays resized and in demo mode until you do."
}

cmd_reset() {
  need_device
  "$ADB" shell "am broadcast -a com.android.systemui.demo -e command exit" >/dev/null 2>&1 || true
  "$ADB" shell settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
  "$ADB" shell wm size reset >/dev/null 2>&1 || true
  "$ADB" shell wm density reset >/dev/null 2>&1 || true
  "$ADB" shell ime reset >/dev/null 2>&1 || true
  grn "Device restored (display size, density, status bar, keyboard)."
}

cmd_deeplink() {
  need_device
  local uri="${1:?usage: $0 deeplink <uri>   e.g. ${DEEPLINK_SCHEME}://beers}"
  "$ADB" shell am start -a android.intent.action.VIEW -d "'$uri'" "$APP_ID" >/dev/null
  sleep 2
  grn "Navigated to $uri"
}

cmd_capture() {
  need_device
  local name="${1:?usage: $0 capture <name>   e.g. 01-catalog}"
  mkdir -p "$RAW_DIR" "$SHOTS_DIR"

  local raw="$RAW_DIR/$name.png"
  local out="$SHOTS_DIR/$name.jpg"

  # A multi-display AVD makes bare `screencap` print a warning to STDOUT, which lands in
  # the middle of the PNG and corrupts it. Always pass an explicit display id.
  local did
  did=$(display_id)
  if [ -n "$did" ]; then
    "$ADB" exec-out screencap -d "$did" -p > "$raw"
  else
    "$ADB" exec-out screencap -p > "$raw"
  fi
  [ -s "$raw" ] || die "screencap produced an empty file"
  # Cheap integrity guard: a corrupted capture is text, not a PNG.
  if [ "$(head -c 4 "$raw" | xxd -p)" != "89504e47" ]; then
    die "screencap did not return a PNG. First bytes: $(head -c 120 "$raw")"
  fi

  local w h
  w=$(img_w "$raw"); h=$(img_h "$raw")
  if [ "$w" != "$CAPTURE_W" ] || [ "$h" != "$CAPTURE_H" ]; then
    ylw "warn: captured ${w}x${h}, expected ${CAPTURE_W}x${CAPTURE_H}. Did you run '$0 prepare'?"
  fi

  # Deliver JPEG: screencap PNGs carry an alpha channel, and Play wants 24-bit PNG with
  # no alpha. Converting sidesteps that entirely, and JPEG is an accepted format.
  sips -s format jpeg -s formatOptions 95 "$raw" --out "$out" >/dev/null
  grn "Captured $out  ($(img_w "$out")x$(img_h "$out"))"
}

check_text() {
  local file="$1" label="$2" limit="$3" fail=0
  if [ ! -f "$file" ]; then
    red "  MISSING  $label  ($file)"; return 1
  fi
  # Character count, not bytes - Play counts characters.
  local n
  n=$(python3 -c "import sys;print(len(open(sys.argv[1],encoding='utf-8').read().rstrip('\n')))" "$file")
  if [ "$n" -gt "$limit" ]; then
    red "  OVER     $label  $n/$limit chars"; fail=1
  elif grep -qi "^TODO" "$file"; then
    ylw "  TODO     $label  $n/$limit chars - still a placeholder"
  else
    grn "  ok       $label  $n/$limit chars"
  fi
  return $fail
}

cmd_check() {
  local fail=0
  echo "Listing text ($METADATA_DIR)"
  check_text "$METADATA_DIR/title.txt"             "title            " "$MAX_TITLE_CHARS"      || fail=1
  check_text "$METADATA_DIR/short_description.txt" "short_description" "$MAX_SHORT_DESC_CHARS" || fail=1
  check_text "$METADATA_DIR/full_description.txt"  "full_description " "$MAX_FULL_DESC_CHARS"  || fail=1

  echo
  echo "Phone screenshots ($SHOTS_DIR)"
  local shots=()
  while IFS= read -r f; do [ -n "$f" ] && shots+=("$f"); done < <(
    find "$SHOTS_DIR" -maxdepth 1 \( -name '*.png' -o -name '*.jpg' -o -name '*.jpeg' \) 2>/dev/null | sort
  )
  local n=${#shots[@]}
  if [ "$n" -lt "$MIN_SCREENSHOTS" ]; then
    red "  $n screenshots - Play requires at least $MIN_SCREENSHOTS"; fail=1
  elif [ "$n" -lt "$REC_SCREENSHOTS" ]; then
    ylw "  $n screenshots - meets the minimum, but $REC_SCREENSHOTS+ is needed to be eligible for featuring"
  elif [ "$n" -gt "$MAX_SCREENSHOTS" ]; then
    red "  $n screenshots - Play allows at most $MAX_SCREENSHOTS"; fail=1
  else
    grn "  $n screenshots (min $MIN_SCREENSHOTS, max $MAX_SCREENSHOTS)"
  fi

  local f w h bytes short long
  for f in "${shots[@]:-}"; do
    [ -e "$f" ] || continue
    w=$(img_w "$f"); h=$(img_h "$f"); bytes=$(file_bytes "$f")
    local problems=""
    # Aspect must be exactly 16:9 or 9:16.
    if [ $((w * 9)) -ne $((h * 16)) ] && [ $((w * 16)) -ne $((h * 9)) ]; then
      problems+=" aspect ${w}:${h} is not 16:9 or 9:16;"
    fi
    short=$(( w < h ? w : h )); long=$(( w > h ? w : h ))
    [ "$short" -ge "$MIN_SCREENSHOT_SIDE" ] || problems+=" shortest side ${short}px < ${MIN_SCREENSHOT_SIDE}px;"
    [ "$long" -le "$MAX_SCREENSHOT_SIDE" ]  || problems+=" longest side ${long}px > ${MAX_SCREENSHOT_SIDE}px;"
    [ "$bytes" -le "$MAX_SCREENSHOT_BYTES" ] || problems+=" ${bytes}B exceeds 8MB;"
    if [ -n "$problems" ]; then
      red "  BAD  $(basename "$f")  ${w}x${h} -${problems%;}"; fail=1
    else
      grn "  ok   $(basename "$f")  ${w}x${h}"
    fi
  done

  echo
  echo "Graphics"
  local icon="$METADATA_DIR/images/icon.png"
  local feat="$METADATA_DIR/images/featureGraphic.png"
  if [ -f "$icon" ]; then
    [ "$(img_w "$icon")" = "$ICON_SIZE" ] && [ "$(img_h "$icon")" = "$ICON_SIZE" ] \
      && grn "  ok       icon ${ICON_SIZE}x${ICON_SIZE}" \
      || { red "  BAD      icon must be ${ICON_SIZE}x${ICON_SIZE}"; fail=1; }
  else
    ylw "  absent   icon.png - optional here; the Console already has one"
  fi
  if [ -f "$feat" ]; then
    [ "$(img_w "$feat")" = "$FEATURE_GRAPHIC_W" ] && [ "$(img_h "$feat")" = "$FEATURE_GRAPHIC_H" ] \
      && grn "  ok       featureGraphic ${FEATURE_GRAPHIC_W}x${FEATURE_GRAPHIC_H}" \
      || { red "  BAD      featureGraphic must be ${FEATURE_GRAPHIC_W}x${FEATURE_GRAPHIC_H}"; fail=1; }
  else
    ylw "  absent   featureGraphic.png - optional here; the Console already has one"
  fi

  echo
  if [ "$fail" -eq 0 ]; then
    grn "All present assets satisfy Play's limits. Upload them from $METADATA_DIR."
  else
    red "Some assets violate Play's limits - see above."
  fi
  return $fail
}

case "${1:-}" in
  init)     shift; cmd_init "$@" ;;
  prepare)  shift; cmd_prepare "$@" ;;
  capture)  shift; cmd_capture "$@" ;;
  deeplink) shift; cmd_deeplink "$@" ;;
  reset)    shift; cmd_reset "$@" ;;
  check)    shift; cmd_check "$@" ;;
  *) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
