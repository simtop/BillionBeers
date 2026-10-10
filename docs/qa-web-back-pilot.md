# Web Back screenshot investigation — 10 October 2026

## Observed defect and repair

A compact Search screenshot showed a missing-glyph box in the Back action. In an isolated
headless Brave session (Chrome 149.0.7827.155), at 320×844 CSS pixels, navigating Catalog → Search
with an empty query reproduced the box in the first capture. One second later the arrow appeared.
Two subsequent navigations and reload samples already showed the arrow. Waiting before taking
a screenshot can therefore hide this defect; passing Back/history assertions did not catch it.

The owner was Web's `Text("←")` adapter in
[WebMain](../web-app/src/wasmJsMain/kotlin/com/simtop/billionbeers/web/WebMain.kt), supplied to the
shared shell's Back slot. `document.fonts.status` reported `loaded` even in the broken capture;
that browser signal does not establish readiness of the Compose canvas font renderer. The exact
Compose/Skia fallback trigger was not isolated. The observed font-backed transient is sufficient
reason to use a vector for this control.

[SharedBackIcon](../shared/presentation/src/commonMain/kotlin/com/simtop/billionbeers/shared/presentation/SharedBackIcon.kt)
draws a 24dp arrow without font lookup, keeps the host content color and accessible description,
and mirrors in RTL. No icon library or font dependency was added. Replaying the same early,
settled and reload captures against a freshly built production distribution showed the arrow.
The production smoke retained real Back/history checks;
[BackIconRenderTest](../shared/presentation/src/jvmTest/kotlin/com/simtop/billionbeers/shared/presentation/BackIconRenderTest.kt)
checks rendered arrow shape, fixed size, description and absence of text in both directions at
normal/2× font scale. This does not establish native screen-reader acceptance or full RTL Web layout.

## Measured reproduction effort

This was a prepared operational replay, after investigating the incident, on the repaired app.
It measures reaching and capturing the reported state, rather than a blind agent diagnosing an
unfamiliar screenshot. Each browser run used a fresh profile, current production bundle,
intercepted API fixtures, accessibility bounds and real mouse input. The existing smoke's
server/DevTools helpers were reused in an ignored, shortened probe.

| Workflow | Lookup operations | Lookup command time | Browser capture, Back and cleanup | Total |
|---|---|---:|---:|---:|
| Indexed | `qa-find`, `qa-screen`, one host-source check | 0.270s | 3.621s | 3.892s |
| Direct source | Seven `rg` searches, including one wrong-path lookup and its recovery | 0.054s | 2.992s | 3.049s |

The indexed queries were `make qa-find QUERY="Search beers" QA_HOST=web` and
`make qa-screen SCREEN=search QA_HOST=web`. Direct searches traced visible copy → screen →
shared navigation → Web route → host slot, then found the launch/smoke commands. Both workflows
used the same prepared browser probe: Catalog → Search, immediate capture, Back → Catalog.
The totals include fresh browser launch and cleanup; first Search captures occurred 1.58s and
1.45s after page navigation respectively. Indexed ran first, direct source second.

These figures exclude image interpretation, reading/thinking time, probe preparation, building,
dependency downloads and diagnosis/fix work. Commands and routes were already known. An earlier
pair overlapped the screenshot verification build and took 25.07s/29.35s; those timings were
retained separately and excluded from the idle replay above. One ordered pair cannot establish
an agent speedup, and the indexed run was slower here. The index supplied entry/setup information
with fewer lookup operations; browser preparation remained outside it.

The observed host-slot discovery gap is now recorded as the `web-back-first-frame` UI recipe in
[the project config](../qa/project.config.json), including checked host/shell/vector anchors and
the first-frame capture requirement. The helper still describes actions rather than executing them.
Before expanding tooling, time several unfamiliar incidents from image inspection through confirmed
reproduction, recording preparation/build time separately and alternating lookup order.

Local raw commands, timings, browser JSON, before/after images and verification logs are retained
under the ignored `build/qa/web-back-glyph-20261010/` evidence directory. The initial probe reused
the existing bundle from the preceding work; post-fix verification rebuilt the production bundle.
