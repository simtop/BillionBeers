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

## 2. Install the app

`make install` is the normal install path for the app — use it first:

```bash
make install
```

If a very old branch predates PR #30 and bundletool still complains, fall back
to installing the split APKs directly:

```bash
make build            # or: ./gradlew assembleDebug  (bb/rtk wrappers via Makefile)
"$ADB" install-multiple -r \
  app/build/outputs/apk/debug/app-debug.apk \
  feature/beerdetail/build/outputs/apk/debug/beerdetail-debug.apk
```

Uninstall: `"$ADB" uninstall "$APP"`.

Other installable app modules from this repo, if the task needs them instead of
the main app (no split-APK dance needed — just `installDebug`):
- `:catalog` → `com.simtop.billionbeers.catalog` (component gallery)
- `:app-dev-<feature>` (e.g. `:app-dev-beerslist` → `com.simtop.billionbeers.devbeerslist`)
  — standalone single-feature sandboxes; `scripts/new-dev-app.sh` stamps out more of
  these (see the `new-dev-app` skill/`docs/beerdetail_dev_app.md` for what does and
  doesn't work with this pattern).

## 3. Launch / drive / observe the app

```bash
"$ADB" shell am start -n "$APP/.presentation.MainActivity"   # launch
"$ADB" shell pm clear "$APP"                                  # reset app data — SEE WARNING
"$ADB" logcat --pid=$("$ADB" shell pidof "$APP")             # app logs only
```

> ⚠️ **`pm clear` breaks on-demand dynamic-feature installs.** It wipes bundletool
> local-testing's staged split APKs, so installing `beerdetail` or `beerbrowse` afterwards
> fails with SplitInstall **error −5**. That is not an app bug and not worth debugging —
> re-run `scripts/install-local-testing.sh` to re-stage the splits. If you only need to reset
> app state, prefer relaunching or clearing via the app rather than `pm clear`.

### Screenshots — prefer the `android` CLI

The `android` CLI (see the `android-cli` skill) captures a clean PNG in one
command — verified valid (`PNG image data, 1080x2400`) on this emulator:

```bash
android screen capture -o ./screen.png     # add --device=<serial> if >1 device
```

Do **not** use `"$ADB" exec-out screencap -p > file.png` here — on this emulator
config the `[Warning] Multiple displays were found...` text gets merged into the
piped binary stream, producing a corrupted (non-PNG) file. If the `android` CLI
is unavailable, use the two-step adb form instead:

```bash
"$ADB" shell screencap -p /sdcard/screen.png
"$ADB" pull /sdcard/screen.png ./screen.png
"$ADB" shell rm /sdcard/screen.png   # optional cleanup
```

(`file ./screen.png` should report `PNG image data`.)

### Finding tap coordinates for UI automation

Don't eyeball coordinates from a screenshot — a displayed/preview image is
usually scaled down from the real device resolution, and floating elements
(FABs, drawers) are easy to misjudge. Read exact element bounds/centers from the
layout tree. Fastest is the `android` CLI, which emits structured JSON with a
`center` for each node:

```bash
android layout --pretty > ./layout.json     # each node has "center":"[x,y]"
```

Fallback via adb (`bounds="[x1,y1][x2,y2]"`, tap its center):

```bash
"$ADB" shell uiautomator dump /sdcard/window_dump.xml
"$ADB" pull /sdcard/window_dump.xml ./window_dump.xml
grep -o 'content-desc="Open debug drawer"[^/]*' ./window_dump.xml   # find your target
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
