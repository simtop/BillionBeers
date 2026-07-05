#!/usr/bin/env bash
#
# Install the app with its on-demand dynamic feature(s) using bundletool's
# --local-testing mode.
#
# Why this exists: `installDebug` (and plain APK installs) only push the base
# module plus *install-time* splits. The `:feature:beerdetail` module is declared
# `<dist:on-demand/>`, so it is deliberately NOT installed at install time — in
# production it is fetched at runtime via Play Feature Delivery. On a bare
# emulator there is no Play backend to serve it, so navigating to beer detail
# would fail.
#
# `bundletool build-apks --local-testing` marks the APK set for local testing and
# `install-apks` then pushes the on-demand feature APKs into the app's local
# storage. The real SplitInstallManager (feature-delivery 2.1.0) picks them up
# from there, so the genuine on-demand code path is exercised without Play.
#
# Usage: scripts/install-local-testing.sh [path/to/app-debug.aab]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAB="${1:-$ROOT/app/build/outputs/bundle/debug/app-debug.aab}"
OUT_DIR="$ROOT/app/build/outputs/apks-local-testing"
APKS="$OUT_DIR/app-debug.apks"

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="$ANDROID_HOME/platform-tools/adb"

DEBUG_KEYSTORE="$HOME/.android/debug.keystore"

if [ ! -f "$AAB" ]; then
  echo "❌ App bundle not found: $AAB"
  echo "   Build it first, e.g.: ./gradlew :app:bundleDebug"
  exit 1
fi

if ! command -v bundletool >/dev/null 2>&1; then
  echo "📦 bundletool not found — installing via Homebrew..."
  if command -v brew >/dev/null 2>&1; then
    brew install bundletool
  else
    echo "❌ Homebrew not available. Install bundletool manually: https://github.com/google/bundletool/releases"
    exit 1
  fi
fi

if [ ! -x "$ADB" ]; then
  echo "❌ adb not found at $ADB (set ANDROID_HOME)."
  exit 1
fi

mkdir -p "$OUT_DIR"
rm -f "$APKS"

echo "🧱 Building local-testing APK set from $(basename "$AAB")..."
build_args=(
  build-apks
  --local-testing
  --bundle="$AAB"
  --output="$APKS"
  --overwrite
)
# Sign with the Android debug keystore so the artifact matches AGP debug builds
# and installs/upgrades cleanly. Fall back to bundletool's own debug key if the
# standard keystore is missing.
if [ -f "$DEBUG_KEYSTORE" ]; then
  build_args+=(
    --ks="$DEBUG_KEYSTORE"
    --ks-pass=pass:android
    --ks-key-alias=androiddebugkey
    --key-pass=pass:android
  )
fi
bundletool "${build_args[@]}"

# Target a specific device when exactly one is connected; otherwise let
# bundletool prompt / require --device-id.
SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
DEVICE_COUNT="$(printf '%s\n' "$SERIAL" | grep -c . || true)"
install_args=(install-apks --apks="$APKS" --adb="$ADB")
if [ "$DEVICE_COUNT" -eq 1 ]; then
  install_args+=(--device-id="$SERIAL")
elif [ "$DEVICE_COUNT" -gt 1 ]; then
  echo "⚠️  Multiple devices connected; bundletool will require a choice."
fi

echo "📲 Installing base + staging on-demand feature APKs for local testing..."
bundletool "${install_args[@]}"

echo "✅ Done. The app is installed; on-demand modules (e.g. beerdetail) are staged"
echo "   locally and will install at runtime via SplitInstallManager when requested."
