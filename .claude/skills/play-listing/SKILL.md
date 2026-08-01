---
name: play-listing
description: >-
  Prepare Google Play store listing assets for BillionBeers - capture fresh, Play-compliant
  phone screenshots from a device and write/refresh the listing copy (title, short and full
  description, changelog). Use when asked to update the Play Store listing, refresh the store
  screenshots, write or rewrite the store description, prepare a release listing, or check
  whether the listing assets still match the app. Produces files for a manual Play Console
  upload - it never publishes anything.
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

Two halves. `scripts/play-listing.sh` does the mechanical work - device state, capture,
validation. **You** do the judgement: which screens tell the story, and what the copy says.

**This never publishes.** It writes files under `fastlane/metadata/android/en-US/`; Simon
uploads them in the Play Console himself. Do not look for, or add, a publishing API call.

## Where things live

```
fastlane/metadata/android/en-US/
├── title.txt                        30 chars max
├── short_description.txt            80 chars max
├── full_description.txt             4000 chars max
├── changelogs/<versionCode>.txt     "What's new" for that release
└── images/phoneScreenshots/*.jpg    2-8 shots, 16:9 or 9:16
```

That is the [fastlane supply](https://docs.fastlane.tools/actions/supply/) layout. It was
chosen so the tree stays usable if publishing is ever automated - not because fastlane is
installed. `build/play-listing-raw/` holds the unconverted PNGs and is throwaway.

## Capturing screenshots

Needs a booted device. For emulator lifecycle, use the `billionbeers-android` skill.

The app must be **installed with its dynamic features staged**, or the detail and browse
screens will not open:

```bash
./gradlew :app:bundleDebug --console=plain
bash scripts/install-local-testing.sh
```

Then:

```bash
scripts/play-listing.sh prepare              # 9:16 display, clean status bar, no keyboard
scripts/play-listing.sh deeplink billionbeers://beers
scripts/play-listing.sh capture 01-catalog
# ...navigate, capture, repeat...
scripts/play-listing.sh reset                # ALWAYS - restores the device
```

`prepare` does three things that matter:

- **Resizes the display to 1080x1920.** Play requires 16:9 or 9:16. A stock AVD is 1080x2400
  (9:20) and would be rejected. Resizing beats letterboxing afterwards - the captures stay
  pixel-native.
- **Enables SysUI demo mode** - fixed 10:00 clock, full battery, full signal, no notification
  clutter. Every capture then looks like it came from the same device at the same moment.
- **Disables the on-screen keyboard.** Otherwise the IME and its "Hold and drag to move
  toolbar" coach mark cover the search results.

`reset` undoes all three. Run it even if capture failed - the emulator stays resized otherwise.

### Navigating

Because the display size is pinned during a session, **tap coordinates are stable**. On the
catalog screen at 1080x1920:

| Target | Tap |
|---|---|
| Search icon | `input tap 1005 157` |
| Browse icon | `input tap 880 157` |
| First list item | `input tap 540 490` |

These assume the catalog screen at 1080x1920 — re-derive them if the app bar changes. The script
itself is macOS-only (it uses `sips` for image work).

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" shell input tap 1005 157        # open search
"$ADB" shell input text "ipa"          # type a query
"$ADB" shell input keyevent 4          # back
```

Two traps, both hit while building this skill:

- **Deep links do not pop the back stack.** `deeplink billionbeers://beers` while a detail
  screen is on top just re-delivers the intent to the running instance and nothing moves. Use
  `keyevent 4` to walk back, and count your presses - one too many exits to the launcher.
- **Give images time to load.** Beer artwork arrives over the network; capturing too early
  leaves grey placeholder squares in the shot. Sleep ~4s after landing on a list, and **look at
  the capture** before accepting it.

### Deterministic states

Do not stumble into error or empty states by unplugging wifi. The app ships seams for exactly
this, reachable from the **debug drawer** (tap the app-bar title):

- **Network fault injection** - drives the error and offline states.
- **Theme** - light/dark, independent of the system.
- **Feature flags** - per-flag overrides.
- **Deep link directory** - the canonical list of routes.

For dark mode specifically, driving the *system* is simpler and does not leave the drawer open:

```bash
"$ADB" shell "cmd uimode night yes"    # ...capture...
"$ADB" shell "cmd uimode night no"
```

### Which screens to shoot

Play shows the first 2-3 in search results, so lead with the strongest. Aim for 5; the
validator warns below 4 because that is Play's threshold for featuring eligibility. A set that
works: **catalog** (photo-led list, ABV/IBU, stock states) → **search** (query + result count)
→ **detail** (hero image, stats, description) → **browse by style** (shows the catalog has
structure) → **dark mode** (proves the theme is real).

Prefer a *style's beer list* over the styles index - a screen of plain text rows sells nothing.

## Writing the copy

Consumer copy, not engineering copy. Someone browsing Play wants to know what the app does for
them; they do not care that the architecture is enforced by Konsist. Keep the repo's framing out
of it — the GitHub About line and the store description are different products.

Ground every claim in a string the app actually ships (`presentation_utils/src/main/res/values/strings.xml`
is the best single source). The real feature set: paged catalog with ABV/IBU, search-as-you-type,
browse by style and brewery, a detail screen with food pairing / SRM / serving temperature /
fermentation / ingredients / glassware, and local stock tracking ("Mark as Empty" / "Refill
Barrels"). Offline-capable via the Room cache. English, French, Spanish.

Be honest about what it is: free, ad-free, open-source, no accounts, and backed by an open beer
catalog API rather than live shop inventory. Overselling a demo catalog as a shopping app is
both wrong and a policy risk.

After editing any text file, run `scripts/play-listing.sh check`.

## Making them marketable

A raw capture shows the UI; a store frame sells the app. `scripts/store-frames.sh` wraps each
capture in the layout polished listings use - a headline on the brand gradient, with the screen
in a device mockup below:

```bash
make store-frames                 # en-US
make store-frames LOCALE=fr-FR    # any locale that has captions + captures
```

Headlines live in `fastlane/metadata/android/<locale>/framing-captions.tsv`, one
`filename<TAB>headline` row per screenshot (`<br>` forces a line break). They sit beside the rest
of that locale's listing copy on purpose: adding a language is the same move as for any other
store text, and the layout is shared rather than duplicated per locale.

Output goes to `images/phoneScreenshotsFramed/`, beside the raw captures rather than replacing
them. **Which set goes where is decided (2026-08-01):**

| Surface | Set | Why |
|---|---|---|
| Play Store listing | **framed** (`phoneScreenshotsFramed/`) | A storefront competes for a thumb-scroll. The headline sells before anyone reads the description, and the first 2-3 shots appear in search results, where a bare screenshot reads as an unfinished listing. |
| README Visual Tour | **raw** (`phoneScreenshots/`) | Documentation. A reader wants the actual UI, not marketing furniture around it. |

So keep both sets. Play accepts only one, so never upload them together, and never delete the raw
set in favour of the framed one — the README renders it.

Rendering is headless Chrome over generated HTML, chosen because it needs no image library and
gives real control over gradients and type. The palette is lifted from
`core/designsystem/.../theme/Color.kt`, so reskinning the app means updating three constants at
the top of the script.

Writing headlines: say what the user gets, not what the screen is called. "Find any beer as you
type" beats "Search screen". Keep them to two short lines - the frame gives you roughly 40
characters per line before the type has to shrink.

## Validating

```bash
scripts/play-listing.sh check
```

Checks character counts, screenshot count, dimensions, aspect ratio and file size against
Play's published limits. Every limit is a named constant in one block at the top of the script,
with links to Google's pages - **if Play changes a rule, edit there and nowhere else.**

Green output means the assets are uploadable, not that the copy is good. Read it yourself.

## Handing off

Report what changed, where the files are, and what Simon still has to do by hand in the Play
Console (upload screenshots, paste copy, submit for review). If the screenshots replace visibly
stale ones, say so - and consider whether `imagesForReadme/` and the README's Visual Tour need
the same refresh, since they drift from the app for the same reason.
