#!/usr/bin/env bash
#
# Create and operate the local BillionBeers resizable emulator.
#
# The settings below are intentionally explicit. Edit them here for the normal local setup, or
# override them through the matching EMULATOR_* variables on a make command, for example:
#
#   make emulator-recreate CONFIRM=1 EMULATOR_API=37.0 EMULATOR_IMAGE_TAG=google_apis_ps16k
#   make emulator-recreate CONFIRM=1 EMULATOR_RAM_MB=4096 EMULATOR_DATA_DISK=10G
#
# `recreate` and `delete` are destructive: they require CONFIRM=1 because recreating an AVD wipes
# its user data. The default API 35 Google APIs image is deliberately lighter than a full Play
# Store image and matches this project's fast managed-device lane.
#
# Usage: scripts/emulator.sh {create|recreate|start|stop|status|delete}

set -euo pipefail

# -----------------------------------------------------------------------------------------------
# Local emulator configuration. Keep these values easy to find and change.
# -----------------------------------------------------------------------------------------------
readonly AVD_NAME="${AVD_NAME:-BillionBeers_Resizable}"
readonly ANDROID_API="${ANDROID_API:-35}"
readonly SYSTEM_IMAGE_TAG="${SYSTEM_IMAGE_TAG:-google_apis}"
readonly ABI="${ABI:-arm64-v8a}"
readonly DEVICE_PROFILE="${DEVICE_PROFILE:-resizable}"
readonly RAM_MB="${RAM_MB:-3072}"
readonly DATA_DISK="${DATA_DISK:-8G}"
readonly CPU_CORES="${CPU_CORES:-4}"
readonly LCD_WIDTH="${LCD_WIDTH:-1080}"
readonly LCD_HEIGHT="${LCD_HEIGHT:-2400}"
readonly LCD_DENSITY="${LCD_DENSITY:-420}"
readonly GPU_MODE="${GPU_MODE:-auto}"
readonly CONFIRM="${CONFIRM:-0}"
readonly ACCEPT_ANDROID_LICENSES="${ACCEPT_ANDROID_LICENSES:-0}"

readonly ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
readonly ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
readonly AVD_ROOT="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
readonly SYSTEM_IMAGE_PACKAGE="system-images;android-${ANDROID_API};${SYSTEM_IMAGE_TAG};${ABI}"
readonly SYSTEM_IMAGE_DIR="$ANDROID_HOME/system-images/android-${ANDROID_API}/${SYSTEM_IMAGE_TAG}/${ABI}"
readonly AVD_DIR="$AVD_ROOT/${AVD_NAME}.avd"
readonly AVD_CONFIG="$AVD_DIR/config.ini"
readonly LOG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/emulator"
readonly ADB="${ANDROID_HOME}/platform-tools/adb"

EMULATOR_BIN="${EMULATOR_BIN:-${ANDROID_HOME}/emulator/emulator}"
SDKMANAGER_BIN="${SDKMANAGER_BIN:-$(command -v sdkmanager || true)}"
AVDMANAGER_BIN="${AVDMANAGER_BIN:-$(command -v avdmanager || true)}"

red() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
grn() { printf '\033[32m%s\033[0m\n' "$*"; }
ylw() { printf '\033[33m%s\033[0m\n' "$*"; }
die() { red "error: $*"; exit 1; }

usage() {
  cat <<'EOF'
Create and operate the local BillionBeers resizable emulator.

Commands:
  create       Install the system image if needed and create the AVD (idempotent)
  recreate     Recreate the AVD with current settings (wipes user data; CONFIRM=1 required)
  start        Start or reuse the AVD and wait until Android has finished booting
  stop         Stop only the configured AVD
  status       Print configuration, AVD existence, and boot state
  delete       Delete the AVD (wipes user data; CONFIRM=1 required)

Examples:
  make emulator-create
  make emulator-start
  make ui-test-local
  make emulator-recreate CONFIRM=1 EMULATOR_API=37.0 EMULATOR_IMAGE_TAG=google_apis_ps16k
  make emulator-recreate CONFIRM=1 EMULATOR_RAM_MB=4096 EMULATOR_DATA_DISK=10G
  make emulator-create ACCEPT_ANDROID_LICENSES=1
EOF
}

require_adb() {
  [ -x "$ADB" ] || die "adb not found at $ADB (set ANDROID_HOME or ANDROID_SDK_ROOT)"
}

require_emulator() {
  [ -x "$EMULATOR_BIN" ] || {
    local fallback
    fallback="$(command -v emulator || true)"
    [ -n "$fallback" ] && [ -x "$fallback" ] && EMULATOR_BIN="$fallback"
  }
  [ -x "$EMULATOR_BIN" ] || die "emulator binary not found (set EMULATOR_BIN or install the Android Emulator)"
}

require_sdkmanager() {
  [ -n "$SDKMANAGER_BIN" ] || die "sdkmanager not found (install Android command-line tools)"
}

require_avdmanager() {
  [ -n "$AVDMANAGER_BIN" ] || die "avdmanager not found (install Android command-line tools)"
}

require_tools() {
  require_adb
  require_emulator
  require_sdkmanager
  require_avdmanager
}

validate_config() {
  case "$AVD_NAME" in
    ''|*/*) die "AVD_NAME must be non-empty and must not contain '/': $AVD_NAME" ;;
  esac
  [[ "$ANDROID_API" =~ ^[0-9]+([.][0-9]+)?$ ]] || \
    die "ANDROID_API must be a numeric API level (for example 35 or 37.0): $ANDROID_API"
  case "$RAM_MB:$CPU_CORES:$LCD_WIDTH:$LCD_HEIGHT:$LCD_DENSITY" in
    *[!0-9:]*|:*) die "RAM, CPU, display width/height, and density must be positive integers" ;;
  esac
}

avd_exists() {
  [ -f "$AVD_CONFIG" ]
}

ensure_system_image() {
  if [ -f "$SYSTEM_IMAGE_DIR/source.properties" ] && [ -f "$SYSTEM_IMAGE_DIR/package.xml" ]; then
    return
  fi

  ylw "System image is missing or incomplete: $SYSTEM_IMAGE_PACKAGE"
  echo "Installing it with sdkmanager..."
  if [ "$ACCEPT_ANDROID_LICENSES" = "1" ]; then
    yes | "$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" "$SYSTEM_IMAGE_PACKAGE"
  else
    "$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" "$SYSTEM_IMAGE_PACKAGE" || \
      die "system image install failed; accept SDK licenses explicitly with ACCEPT_ANDROID_LICENSES=1"
  fi
  [ -f "$SYSTEM_IMAGE_DIR/source.properties" ] && [ -f "$SYSTEM_IMAGE_DIR/package.xml" ] || \
    die "system image was not installed at $SYSTEM_IMAGE_DIR"
}

set_avd_property() {
  local key="$1" value="$2" tmp
  tmp="$(mktemp "${AVD_CONFIG}.XXXXXX")"
  awk -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    index($0, key "=") == 1 { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "$AVD_CONFIG" > "$tmp"
  mv "$tmp" "$AVD_CONFIG"
}

apply_avd_config() {
  [ -f "$AVD_CONFIG" ] || die "AVD config was not created: $AVD_CONFIG"

  # The device profile is the part that makes the emulator resizable. The remaining values make
  # the initial portrait size deterministic while keeping the window resize controls available.
  set_avd_property hw.device.name "$DEVICE_PROFILE"
  set_avd_property hw.ramSize "$RAM_MB"
  set_avd_property disk.dataPartition.size "$DATA_DISK"
  set_avd_property hw.cpu.ncore "$CPU_CORES"
  set_avd_property hw.gpu.enabled yes
  set_avd_property hw.gpu.mode "$GPU_MODE"
  set_avd_property hw.keyboard yes
  set_avd_property hw.keyboard.lid yes
  set_avd_property hw.lcd.width "$LCD_WIDTH"
  set_avd_property hw.lcd.height "$LCD_HEIGHT"
  set_avd_property hw.lcd.density "$LCD_DENSITY"
  set_avd_property skin.name "${LCD_WIDTH}x${LCD_HEIGHT}"
  set_avd_property showDeviceFrame no
  set_avd_property runtime.network.latency none
  set_avd_property runtime.network.speed full
  set_avd_property fastboot.forceFastBoot yes

  grn "Configured $AVD_NAME"
  printf '  image:   %s\n' "$SYSTEM_IMAGE_PACKAGE"
  printf '  profile: %s\n' "$DEVICE_PROFILE"
  printf '  memory:  %s MB\n' "$RAM_MB"
  printf '  storage: %s\n' "$DATA_DISK"
  printf '  display: %sx%s @ %s dpi\n' "$LCD_WIDTH" "$LCD_HEIGHT" "$LCD_DENSITY"
}

create_avd() {
  require_tools
  validate_config
  ensure_system_image
  mkdir -p "$AVD_ROOT"

  if avd_exists; then
    ylw "$AVD_NAME already exists; leaving its data untouched."
    echo "Run 'make emulator-recreate CONFIRM=1' to apply changed settings."
    return
  fi

  printf 'no\n' | "$AVDMANAGER_BIN" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE_PACKAGE" \
    --device "$DEVICE_PROFILE" \
    --force
  apply_avd_config
}

confirm_destructive() {
  [ "$CONFIRM" = "1" ] || die "$1 is destructive; rerun with CONFIRM=1"
}

matching_serials() {
  "$ADB" devices 2>/dev/null | awk 'NR > 1 && $1 ~ /^emulator-/ && $2 == "device" { print $1 }' | while IFS= read -r serial; do
    local name
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | awk 'NF { print; exit }')"
    [ "$name" = "$AVD_NAME" ] && printf '%s\n' "$serial"
  done
}

matching_serial() {
  matching_serials | awk 'NF { print; exit }'
}

wait_for_boot() {
  local serial="$1" timeout_seconds=180
  local deadline=$((SECONDS + timeout_seconds))
  local boot_state device_state

  while [ "$SECONDS" -lt "$deadline" ]; do
    device_state="$("$ADB" -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
    boot_state="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$device_state" = "device" ] && [ "$boot_state" = "1" ]; then
      "$ADB" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
      grn "$AVD_NAME is ready on $serial"
      return
    fi
    sleep 2
  done

  die "timed out waiting for $AVD_NAME to boot; inspect $LOG_DIR/${AVD_NAME}.log"
}

start_avd() {
  require_adb
  require_emulator
  validate_config
  avd_exists || die "$AVD_NAME does not exist; run 'make emulator-create' first"
  mkdir -p "$LOG_DIR"

  local serial pid log_file
  serial="$(matching_serial || true)"
  if [ -n "$serial" ]; then
    wait_for_boot "$serial"
    return
  fi

  log_file="$LOG_DIR/${AVD_NAME}.log"
  echo "Starting $AVD_NAME; emulator log: $log_file"
  "$EMULATOR_BIN" \
    -avd "$AVD_NAME" \
    -memory "$RAM_MB" \
    -cores "$CPU_CORES" \
    -gpu "$GPU_MODE" \
    -netdelay none \
    -netspeed full \
    -no-boot-anim \
    > "$log_file" 2>&1 < /dev/null &
  pid=$!
  disown "$pid" 2>/dev/null || true

  for _ in $(seq 1 90); do
    serial="$(matching_serial || true)"
    [ -n "$serial" ] && wait_for_boot "$serial" && return
    if ! kill -0 "$pid" 2>/dev/null; then
      die "emulator exited during startup; inspect $log_file"
    fi
    sleep 2
  done

  die "emulator did not register with adb; inspect $log_file"
}

stop_avd() {
  require_adb
  local serial stopped=0
  while IFS= read -r serial; do
    [ -n "$serial" ] || continue
    echo "Stopping $AVD_NAME on $serial..."
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
    stopped=1
  done < <(matching_serials)

  if [ "$stopped" -eq 1 ]; then
    grn "$AVD_NAME stopped"
  else
    echo "$AVD_NAME is not running"
  fi
}

status_avd() {
  validate_config
  printf 'AVD:     %s\n' "$AVD_NAME"
  printf 'Image:   %s\n' "$SYSTEM_IMAGE_PACKAGE"
  printf 'Memory:  %s MB\n' "$RAM_MB"
  printf 'Storage: %s\n' "$DATA_DISK"
  printf 'Display: %sx%s @ %s dpi\n' "$LCD_WIDTH" "$LCD_HEIGHT" "$LCD_DENSITY"

  if avd_exists; then
    printf 'Created: yes (%s)\n' "$AVD_CONFIG"
  else
    printf 'Created: no\n'
    return
  fi

  if [ ! -x "$ADB" ]; then
    printf 'Device:  adb unavailable\n'
    return
  fi

  local serial boot_state
  serial="$(matching_serial || true)"
  if [ -z "$serial" ]; then
    printf 'Device:  stopped\n'
    return
  fi
  boot_state="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  printf 'Device:  %s (%s)\n' "$serial" "$([ "$boot_state" = "1" ] && printf ready || printf booting)"
}

recreate_avd() {
  require_tools
  validate_config
  confirm_destructive recreate
  ensure_system_image
  stop_avd
  mkdir -p "$AVD_ROOT"
  printf 'no\n' | "$AVDMANAGER_BIN" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE_PACKAGE" \
    --device "$DEVICE_PROFILE" \
    --force
  apply_avd_config
}

delete_avd() {
  require_tools
  validate_config
  confirm_destructive delete
  stop_avd
  if avd_exists; then
    "$AVDMANAGER_BIN" delete avd --name "$AVD_NAME"
    grn "$AVD_NAME deleted"
  else
    echo "$AVD_NAME does not exist"
  fi
}

case "${1:-}" in
  create)   create_avd ;;
  recreate) recreate_avd ;;
  start)    start_avd ;;
  stop)     stop_avd ;;
  status)   status_avd ;;
  delete)   delete_avd ;;
  -h|--help|help) usage ;;
  *) usage >&2; exit 1 ;;
esac
