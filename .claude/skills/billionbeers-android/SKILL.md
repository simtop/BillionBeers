---
name: billionbeers-android
description: >-
  Drive the Android SDK command-line tools (adb, emulator, sdkmanager, avdmanager)
  for the BillionBeers app. Use this WHENEVER a task touches a real device or
  emulator — starting/stopping an emulator, checking connected devices or SDK
  status, installing/uninstalling the app, launching the app, taking a
  screenshot, reading logcat, clearing app data, or running instrumented
  (androidTest) tests on a device. Prefer this over ad-hoc adb invocations so
  paths and the known installDebug workaround are handled correctly. Triggers on:
  "adb", "emulator", "install the app", "launch the app", "screenshot the app",
  "logcat", "run on device", "connected devices", "AVD", "sdkmanager".
---

# Android CLI (BillionBeers)

Wraps the local Android SDK CLI so agents interact with devices/emulators
consistently. **Always prefer these recipes over improvising `adb` paths.**

## Environment (already installed)

- SDK root: `$ANDROID_HOME` = `~/Library/Android/sdk` (also `$ANDROID_SDK_ROOT`).
- `adb` is **not on PATH by default** — always invoke it as
  `"$ANDROID_HOME/platform-tools/adb"` (or plain `adb` if PATH is configured).
- `emulator` binary: `/usr/local/bin/emulator` (or `$ANDROID_HOME/emulator/emulator`).
- `sdkmanager` / `avdmanager` are on PATH (Homebrew `android-commandlinetools`).
- App id: `com.simtop.billionbeers` — launcher:
  `com.simtop.billionbeers/.presentation.MainActivity`.

Define a shorthand at the top of a shell session:

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="${ANDROID_HOME}/emulator/emulator"   # or /usr/local/bin/emulator
APP=com.simtop.billionbeers
```

## 0. Preflight — check status first

```bash
"$ADB" version                 # confirm adb works
"$ADB" devices -l              # list connected devices/emulators
"$EMU" -list-avds              # list installable AVDs
sdkmanager --list_installed    # SDK packages (optional)
```

If `adb devices` shows no `device` (only `offline`/empty), start an emulator (§1).

## 1. Emulator lifecycle

```bash
"$EMU" -list-avds                                   # pick one, e.g. Resizable_Android_17
"$EMU" -avd Resizable_Android_17 -netdelay none -netspeed full &   # boot (backgrounded)
"$ADB" wait-for-device
# block until fully booted:
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
"$ADB" shell input keyevent 82                      # dismiss keyguard
```

Stop an emulator: `"$ADB" -s emulator-5554 emu kill`.

## 2. Install the app  ⚠️ read this

`make install` / `:app:installDebug` is **broken on this branch** — the dynamic
feature install goes through the app bundle and bundletool rejects a duplicate
`META-INF/services/...PreviewProvider` entry (see the
`installdebug-bundle-broken` memory; real fix is PR #30). Until that fix is on
your branch, install the split APKs directly:

```bash
make build            # or: ./gradlew assembleDebug  (bb/rtk wrappers via Makefile)
"$ADB" install-multiple -r \
  app/build/outputs/apk/debug/app-debug.apk \
  feature/beerdetail/build/outputs/apk/debug/beerdetail-debug.apk
```

Once PR #30 is merged into the working branch, `make install` works again — try
it first and fall back to `install-multiple` only if bundletool complains.

Uninstall: `"$ADB" uninstall "$APP"`.

## 3. Launch / drive / observe the app

```bash
"$ADB" shell am start -n "$APP/.presentation.MainActivity"   # launch
"$ADB" shell pm clear "$APP"                                  # reset app data
"$ADB" logcat --pid=$("$ADB" shell pidof "$APP")             # app logs only
# screenshot to the host:
"$ADB" exec-out screencap -p > /tmp/billionbeers-$(date +%s).png
```

## 4. Instrumented tests on a device

Prefer the Gradle/Makefile path (routes through `bb`/`rtk`):

```bash
./gradlew :app:connectedDebugAndroidTest        # needs a booted device (§1)
```

## Notes

- For **unit tests, builds, lint, format, screenshots (Paparazzi)** use the
  `Makefile` targets (`make test`, `make build`, `make screenshot-verify`, …),
  not adb — those don't need a device. This skill is only for on-device work.
- Never hardcode `emulator-5554`; read the serial from `adb devices` and pass it
  with `-s <serial>` when more than one device is attached.
- This is the **project-specific** device skill (app id, install workaround). For
  general Android tasks there are also vendored **official Android skills** from
  [github.com/android/skills](https://github.com/android/skills) in
  `.claude/skills/` — e.g. `android-cli` (Google's `android` tool), `navigation-3`,
  `r8-analyzer`, `testing-setup`, `edge-to-edge`, `perfetto-trace-analysis`. Refresh
  them with `make update-android-skills`.
