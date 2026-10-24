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
#   scripts/play-listing.sh prepare [profile]    Prepare phone, seven-inch, ten-inch, or foldable capture
#   scripts/play-listing.sh capture <name>       Capture the current screen as <name>
#   scripts/play-listing.sh reset                Undo `prepare` - ALWAYS run when finished
#   scripts/play-listing.sh check                Validate all listing assets against Play limits
#   scripts/play-listing.sh deeplink <uri>       Navigate the app via deep link (repeatable nav)
#
set -euo pipefail

# ---------------------------------------------------------------------------------------
# Play Store limits. Verified 2026-10-24 against Google's published requirements:
#   Preview assets:  https://support.google.com/googleplay/android-developer/answer/9866151
#   Store listing:   https://support.google.com/googleplay/android-developer/answer/13393723
# Text/graphic limits live here; screenshot rules live in check-play-screenshots.py.
# ---------------------------------------------------------------------------------------
readonly MAX_TITLE_CHARS=30
readonly MAX_SHORT_DESC_CHARS=80
readonly MAX_FULL_DESC_CHARS=4000
readonly ICON_SIZE=512              # 512x512, 32-bit PNG (alpha allowed)
readonly FEATURE_GRAPHIC_W=1024     # 1024x500, JPEG or 24-bit PNG, NO alpha
readonly FEATURE_GRAPHIC_H=500
# 16:9 / 9:16 is a promotion recommendation, not the general upload requirement.
# Native unfolded captures may use another ratio, up to 2:1.
readonly PROFILE="${PROFILE:-phone}"
readonly LOCALE="${LOCALE:-en-US}"
case "$PROFILE" in
  phone)      SHOT_FOLDER=phoneScreenshots; CAPTURE_W=1080; CAPTURE_H=1920; CAPTURE_DENSITY=420 ;;
  seven-inch) SHOT_FOLDER=sevenInchScreenshots; CAPTURE_W=1920; CAPTURE_H=1080; CAPTURE_DENSITY=320 ;;
  ten-inch)   SHOT_FOLDER=tenInchScreenshots; CAPTURE_W=2560; CAPTURE_H=1440; CAPTURE_DENSITY=320 ;;
  foldable)   SHOT_FOLDER=foldableScreenshots; CAPTURE_W=2208; CAPTURE_H=1840; CAPTURE_DENSITY=420 ;;
  *) echo "Unknown PROFILE: $PROFILE" >&2; exit 1 ;;
esac
[[ "$LOCALE" =~ ^[a-z]{2}-[A-Z]{2}$ ]] || { echo "Invalid LOCALE" >&2; exit 1; }
readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly METADATA_DIR="$REPO_ROOT/fastlane/metadata/android/$LOCALE"
readonly SHOTS_DIR="$METADATA_DIR/images/$SHOT_FOLDER"
readonly RAW_DIR="$REPO_ROOT/build/play-listing-raw/$LOCALE/$PROFILE"
readonly APP_ID="com.simtop.billionbeers"
readonly DEEPLINK_SCHEME="billionbeers"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"

red()  { printf '\033[31m%s\033[0m\n' "$*"; }
grn()  { printf '\033[32m%s\033[0m\n' "$*"; }
ylw()  { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { red "error: $*" >&2; exit 1; }

need_device() {
  [ -n "$ADB" ] && [ -x "$ADB" ] || die "adb not found. Set ANDROID_HOME."
  if [ -z "${ANDROID_SERIAL:-}" ]; then
    local serials
    serials=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
    [ "$(printf '%s\n' "$serials" | grep -c . || true)" -eq 1 ] || \
      die "Connect one device, or set ANDROID_SERIAL explicitly."
    export ANDROID_SERIAL="$serials"
  fi
  "$ADB" get-state >/dev/null || die "Device $ANDROID_SERIAL is unavailable."
  STATE_DIR="$REPO_ROOT/build/play-listing-state/$ANDROID_SERIAL"
}

save_setting() {
  "$ADB" shell settings get "$1" "$2" | tr -d '\r' > "$STATE_DIR/$1.$2"
}
restore_setting() {
  local value
  value=$(cat "$STATE_DIR/$1.$2")
  if [ "$value" = null ]; then
    "$ADB" shell settings delete "$1" "$2" >/dev/null
  else
    "$ADB" shell settings put "$1" "$2" "$value" >/dev/null
  fi
}

# --- image helpers (macOS `sips` only - no ImageMagick dependency) ----------------------
img_w() { sips -g pixelWidth  "$1" 2>/dev/null | awk '/pixelWidth:/{print $2}'; }
img_h() { sips -g pixelHeight "$1" 2>/dev/null | awk '/pixelHeight:/{print $2}'; }

cmd_init() {
  mkdir -p "$SHOTS_DIR" "$METADATA_DIR/changelogs"
  local version
  version=$(grep -oE '^billionbeers\.versionCode=.*' \
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
  if [ ! -d "$STATE_DIR" ]; then
    mkdir -p "$STATE_DIR"
    "$ADB" shell wm size | tr -d '\r' > "$STATE_DIR/size"
    "$ADB" shell wm density | tr -d '\r' > "$STATE_DIR/density"
    "$ADB" shell cmd uimode night | tr -d '\r' | sed 's/.*: //' > "$STATE_DIR/night"
    save_setting global sysui_demo_allowed
    save_setting secure show_ime_with_hard_keyboard
    save_setting system accelerometer_rotation
    save_setting system user_rotation
  fi
  echo "Preparing $PROFILE: ${CAPTURE_W}x${CAPTURE_H}, density $CAPTURE_DENSITY..."
  "$ADB" shell settings put system accelerometer_rotation 0 >/dev/null
  "$ADB" shell settings put system user_rotation 0 >/dev/null
  "$ADB" shell wm size "${CAPTURE_W}x${CAPTURE_H}" >/dev/null
  "$ADB" shell wm density "$CAPTURE_DENSITY" >/dev/null

  echo "Enabling SysUI demo mode for a clean, reproducible status bar..."
  "$ADB" shell settings put global sysui_demo_allowed 1 >/dev/null
  local b="am broadcast -a com.android.systemui.demo"
  "$ADB" shell "$b -e command enter" >/dev/null
  "$ADB" shell "$b -e command clock -e hhmm 1000" >/dev/null
  "$ADB" shell "$b -e command battery -e level 100 -e plugged false" >/dev/null
  "$ADB" shell "$b -e command network -e wifi show -e level 4 -e fully true" >/dev/null
  "$ADB" shell "$b -e command network -e mobile hide" >/dev/null
  "$ADB" shell "$b -e command notifications -e visible false" >/dev/null

  # Keep the user's enabled IMEs intact. Emulator hardware input suppresses the IME.
  "$ADB" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null

  printf '%s\n' "$PROFILE" > "$STATE_DIR/profile"
  mkdir -p "$RAW_DIR" "$SHOTS_DIR"
  grn "Device ready. Capture with: PROFILE=$PROFILE bash $0 capture <name>"
  ylw "Run 'bash $0 reset' when finished - the device stays resized and in demo mode until you do."
}

cmd_reset() {
  need_device
  [ -d "$STATE_DIR" ] || die "No saved capture state for $ANDROID_SERIAL."
  "$ADB" shell "am broadcast -a com.android.systemui.demo -e command exit" >/dev/null
  local kind value
  for kind in size density; do
    value=$(sed -n 's/^Override [^:]*: //p' "$STATE_DIR/$kind")
    "$ADB" shell wm "$kind" "${value:-reset}" >/dev/null
  done
  restore_setting global sysui_demo_allowed
  restore_setting secure show_ime_with_hard_keyboard
  restore_setting system accelerometer_rotation
  restore_setting system user_rotation
  "$ADB" shell cmd uimode night "$(cat "$STATE_DIR/night")" >/dev/null
  rm -r "$STATE_DIR"
  grn "Restored saved display, rotation, theme, keyboard and demo-mode settings."
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
  [[ "$name" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]] || die "Use a simple screenshot name without an extension."
  mkdir -p "$RAW_DIR" "$SHOTS_DIR"

  [ -f "$STATE_DIR/profile" ] || die "Run prepare before capturing."
  [ "$(cat "$STATE_DIR/profile")" = "$PROFILE" ] || \
    die "PROFILE differs from prepared session; export PROFILE consistently."
  "$ADB" shell dumpsys activity activities | \
    grep -E 'topResumedActivity|mResumedActivity' | grep -q "$APP_ID/" || \
    die "BillionBeers is not the foreground activity; refusing to capture another app."
  local raw="$RAW_DIR/$name.png"
  local out="$SHOTS_DIR/$name.jpg"

  # The CLI handles device screenshot capture; adb is reserved for controls it lacks.
  android screen capture --device="$ANDROID_SERIAL" -o="$raw" >/dev/null
  [ -s "$raw" ] || die "screencap produced an empty file"
  # Cheap integrity guard: a corrupted capture is text, not a PNG.
  if [ "$(head -c 4 "$raw" | xxd -p)" != "89504e47" ]; then
    die "screencap did not return a PNG. First bytes: $(head -c 120 "$raw")"
  fi

  local w h
  w=$(img_w "$raw"); h=$(img_h "$raw")
  if [ "$w" != "$CAPTURE_W" ] || [ "$h" != "$CAPTURE_H" ]; then
    die "Captured ${w}x${h}, expected ${CAPTURE_W}x${CAPTURE_H}; refusing to replace output."
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
  elif [ "$n" -eq 0 ] || grep -qi "^TODO" "$file"; then
    fail=1
    red "  TODO     $label  $n/$limit chars - still a placeholder"
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

  python3 "$REPO_ROOT/scripts/check-play-screenshots.py" "$METADATA_DIR/images" || fail=1

  echo
  echo "Graphics"
  local icon="$METADATA_DIR/images/icon.png"
  local feat="$METADATA_DIR/images/featureGraphic.png"
  if [ -f "$icon" ]; then
    [ "$(img_w "$icon")" = "$ICON_SIZE" ] && [ "$(img_h "$icon")" = "$ICON_SIZE" ] \
      && grn "  ok       icon ${ICON_SIZE}x${ICON_SIZE}" \
      || { red "  BAD      icon must be ${ICON_SIZE}x${ICON_SIZE}"; fail=1; }
  else
    ylw "  absent   icon.png - not supplied here; verify the existing Console asset"
  fi
  if [ -f "$feat" ]; then
    [ "$(img_w "$feat")" = "$FEATURE_GRAPHIC_W" ] && [ "$(img_h "$feat")" = "$FEATURE_GRAPHIC_H" ] \
      && grn "  ok       featureGraphic ${FEATURE_GRAPHIC_W}x${FEATURE_GRAPHIC_H}" \
      || { red "  BAD      featureGraphic must be ${FEATURE_GRAPHIC_W}x${FEATURE_GRAPHIC_H}"; fail=1; }
  else
    ylw "  absent   featureGraphic.png - not supplied here; verify the existing Console asset"
  fi

  echo
  if [ "$fail" -eq 0 ]; then
    grn "All present assets pass local validation. See the play-listing skill for upload mapping."
  else
    red "Some assets violate Play's limits - see above."
  fi
  return $fail
}

case "${1:-}" in
  init)     shift; cmd_init "$@" ;;
  prepare)
    if [ -n "${2:-}" ] && [ "$2" != "$PROFILE" ]; then
      exec env PROFILE="$2" bash "$0" prepare
    fi
    shift; cmd_prepare "$@" ;;
  capture)  shift; cmd_capture "$@" ;;
  deeplink) shift; cmd_deeplink "$@" ;;
  reset)    shift; cmd_reset "$@" ;;
  check)    shift; cmd_check "$@" ;;
  *) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
