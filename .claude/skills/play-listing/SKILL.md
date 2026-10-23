---
name: play-listing
description: >-
  Prepare or refresh BillionBeers Google Play listing screenshots and copy. Capture real
  phone, tablet and unfolded foldable UI, render phone marketing frames, and validate
  separate upload folders. Produces files for manual Play Console upload; never publishes.
metadata:
  keywords:
    - play store
    - store listing
    - screenshots
    - app description
    - google play
    - billionbeers
---

# Preparing the Play Store listing

Use real captures of the current app. `scripts/play-listing.sh` prepares device state,
captures JPEGs, and validates assets. `scripts/store-frames.sh` renders phone captions.
Do not generate or paint app UI. This workflow never publishes; uploads remain manual.

## Output and upload mapping

All paths below are relative to `fastlane/metadata/android/<locale>/` (default `en-US`).

| Folder | Purpose / Play Console destination |
|---|---|
| `images/phoneScreenshots/` | Full phone UI, also suitable for documentation |
| `images/phoneScreenshotsFramed/` | Captioned phone set; preferred phone upload alternative |
| `images/sevenInchScreenshots/` | Raw 7-inch tablet upload set |
| `images/tenInchScreenshots/` | Raw 10-inch tablet upload set |
| `images/foldableScreenshots/` | Native unfolded UI; local manual selection set |

The first, third and fourth names follow fastlane supply. The framed and foldable folders
are local conventions, not automatic publishing targets. Google lists phone and tablet
slots, not a dedicated foldable slot: select unfolded shots for the relevant Console slot
and keep the final selection at eight or fewer. Never upload both phone alternatives as
one set. Keep native foldable captures separate rather than stretching them into 16:9.
For this Play listing, render marketing frames only for phones. Keep tablet and foldable
sets as raw app captures. This follows Google's large-screen guidance to exclude added
marketing text. It is a listing choice, not a blanket prohibition on framing other sizes.
The local foldable folder does not target Fold devices automatically. Google does not
document a guaranteed screenshot switch when a device unfolds. Describe the upload
category and keep that targeting limitation explicit in the handoff.

Read the locale's `screenshot-manifest.tsv` before refreshing an existing set. The
[current en-US manifest](../../../fastlane/metadata/android/en-US/screenshot-manifest.tsv)
records each file's benefit, visible content as alt text, dimensions, size, and SHA-256.
Use filenames for gallery order. After replacing captures or frames, update the manifest
and verify its hashes against the final files. Keep this as the asset handoff rather than
creating a separate upload guide that repeats these instructions.

`title.txt`, `short_description.txt`, `full_description.txt`, and
`changelogs/<versionCode>.txt` hold listing copy. Raw PNGs and device restoration state
live under ignored `build/play-listing-*` directories.

## Capture workflow

Use the `android-cli` skill for emulator lifecycle, UI inspection and screenshots. Check
installed command help. Set `ANDROID_SERIAL` when multiple devices are attached; use a
dedicated emulator to avoid changing a personal device. Install current code with
`make install GRADLE_RUNNER=./gradlew` so both on-demand features are staged. An ordinary
APK install is insufficient; clearing app data removes staged splits and requires reinstall.

Use `LOCALE` and `PROFILE` consistently on capture/check commands. Profiles:

| Profile | Pixels / density | Intended window |
|---|---|---|
| `phone` | 1080×1920 / 420 | Compact portrait |
| `seven-inch` | 1920×1080 / 320 | Smaller expanded landscape |
| `ten-inch` | 2560×1440 / 320 | Larger expanded landscape |
| `foldable` | 2208×1840 / 420 | Expanded unfolded |

These are capture window presets, not hardware emulation. On a resizable AVD, select its
actual phone/tablet/unfolded mode first (inspect `adb emu help resize-display`); use the
unfolded mode for foldable claims. Record the original mode and restore it separately.
On other hardware, preserve actual posture and inspect the resulting layout.

```bash
export ANDROID_SERIAL=emulator-5554
export PROFILE=phone
bash scripts/play-listing.sh prepare
# Install cleanup before navigating, including when capture fails.
trap 'bash scripts/play-listing.sh reset' EXIT
bash scripts/play-listing.sh deeplink billionbeers://beers
# Inspect android layout --pretty; tap current element bounds, not old coordinates.
bash scripts/play-listing.sh capture 01-catalog
# ...navigate, inspect, capture...
bash scripts/play-listing.sh reset
trap - EXIT
```

`prepare` saves original size/density overrides, rotation, theme, demo-mode allowance and
hardware-keyboard IME setting per serial before changing them. Repeated prepares preserve
that original state. `reset` restores it. It does not clear app data or disable IMEs.
Dismiss onboarding if present. Use English app content for `en-US`. Clean the status bar
with demo mode, dismiss the keyboard, and wait for image loading to finish. Open every
accepted capture: reject spinners, placeholders, errors, debug drawers, snackbars, keyboard
coach marks and accidental system UI. Android hardware-keyboard mode can still show an
IME toolbar: use its visible collapse control, then verify search remains open. Back
can exit the app if the toolbar has already disappeared.
Do not use Escape as a generic hide-keyboard action; it can navigate back. A successful
command does not prove a good shot. Capture refuses to overwrite output when the app
is not foreground, dimensions differ, or PROFILE differs from the prepared session.

Inspect the current deep-link parser and layout before navigation. Routes may change;
links can preserve existing navigation state. Use Back or force-stop/relaunch as needed.
Avoid hard-coded historical tap coordinates. Theme can be set with `adb shell cmd uimode
night yes/no`; keep it inside the saved capture session so reset restores the original.

## Choose a useful sequence

Before capture, write a short benefit-to-evidence map: one defensible user promise, one
benefit per image, and the visible behavior that proves it. Use any accessible listing
performance evidence, but state when it is unavailable. Attractive frames do not prove
higher conversion; that requires a listing experiment. Ground design choices in the
current shared design tokens rather than the previous listing's palette.

Phone: catalog → search with a real query → beer detail → populated style/brewery list →
favorites in dark theme. Replace a weak shot with favorites or filters when it tells a clearer story.
Aim for five, with actual app UI prominent in the first three.

For each tablet and foldable set, aim for four strong shots. Show catalog/detail together
in the adaptive two-pane layout, another selected beer, a useful search or browse state,
and dark mode or favorites. Confirm both panes and expanded navigation are visible rather
than just enlarging a phone screen. Do not claim split-pane support for a route that
actually remains single-pane. Inspect the assembled navigation and each feature’s scene
metadata: a navigation rail alone does not prove list-detail support. Preserve artwork and readable text; do not crop or stretch
screenshots to manufacture the layout.

## Frames and copy

For a visual refresh, the free [Frontend Design skill](https://github.com/anthropics/skills/tree/main/skills/frontend-design)
(Apache-2.0; instructions only) is an optional companion. Review its current source and
license before using it. Apply its subject-specific typography, coherent palette and
visual critique to our existing HTML renderer; no external image API or new editor is
required. Keep app artwork and shipped brand colors as the focal point. Use one concrete
benefit per image and evaluate the sequence at thumbnail size. Screenshot-test fixtures
can check layout regressions, but use installed-app captures for final marketing assets
unless fixtures faithfully include real artwork, system UI and current navigation.

```bash
make store-frames LOCALE=en-US
make play-listing-check
make play-listing-test
```

`framing-captions.tsv` uses `filename<TAB>headline`, with `<br>` for line breaks. Keep
headlines concise and benefit-led. The renderer uses the shipped Plus Jakarta Sans font
and preserves the full phone UI without a synthetic device bezel. It checks font readiness
and layout bounds. Keep caption text under 20% of the canvas. Inspect first and last
frames and the longest headline; ensure every input has a corresponding current output.
Do not apply phone framing to large-screen shots.

Write consumer copy from shipped behavior and strings, not architecture or backlog.
Verify claims against current source and the running app. Mention adaptive tablet and
foldable layouts only after capturing them. Keep cached content claims distinct from
first-use offline availability, and local stock tracking distinct from shop inventory.
Do not invent a release/changelog just because screenshots changed.

## Validation and handoff

Check [Google's current requirements and recommendations](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
when changing capture rules. `scripts/check-play-screenshots.py` owns screenshot limits;
`play-listing.sh` owns text and graphic dimensions. The validator covers raw and framed
sets, image format/alpha, dimensions, count and size. General uploads allow ratios up to
2:1; 1080px+ 16:9/9:16 is promotion guidance. Native unfolded shots can be uploadable
without meeting that promotion ratio. Distinguish warnings from upload failures. Use the
conservative general 3840px maximum despite the separate larger tablet allowance.

After rendering, run `scripts/play-listing.sh check` and visually inspect every final
image (contact sheets are useful). Verify malformed images or excess screenshots fail
validation when changing the validator. Include short alt text (up to 140 characters per
shot) and a folder-to-Console mapping in the handoff. State which assets were refreshed,
what was verified and what remains manual. Preserve unrelated user files, including
existing README captures; update documentation images only when requested.
