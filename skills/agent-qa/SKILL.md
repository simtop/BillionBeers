---
name: agent-qa
description: Investigate a reported UI problem from a screenshot or description, identify its screen and navigation path, reproduce it in the running app, and verify a fix. Use for screenshot-led bug reports and exploratory app QA; existing tests are optional supporting tools.
---

# Agent QA

Start from the reported symptom. The useful outcome is an identified screen/source owner,
an observed reproduction path, and a fix verified against that symptom when a fix is requested.
A pre-existing test or scenario is not required.

## Identify the screen and its state

Inspect the actual supplied image. Extract visible titles, selected tabs, labels, dialogs,
data, clipping/overlap and error text. Distinguish those observations from hypotheses about
the platform, route, network, font or cause. Screenshot pixels do not establish logical
window size, device density or text scale.

Read project instructions. If `qa/project.config.json` exists, use the
[project index](references/project-index.md): `project_index.py search` for distinctive image
clues, then `show` for candidate screen owners, navigation entry steps and state recipes.
These queries regenerate source facts in memory; a generated JSON file can be stale.
Confirm candidates in the live app. Read `qa/project.md`, if present, for launch commands, navigation
owners, screen entry paths and state prerequisites. Otherwise derive these from the current
source: search visible copy/resources, screen composables/views, route declarations, host
registration and callbacks. Follow the event from the visible control to the destination,
then to the ViewModel/state and repository boundary as needed. Check host-specific slots
when a shared screen renders differently on one platform.

Identify a likely screen, how to reach it and what data/state it needs. A selected tab or URL
may represent several internal screens. Confirm the registered destination and preceding
actions; do not infer a complete back stack from one image. Ask only for missing information
that materially distinguishes reproductions, such as the platform or action immediately
before a transient error, while continuing independent source investigation.

## Reproduce through the running app

Use the available platform/device/browser tools and the project's supported build/install/
launch commands. Select the reported host and observe the current UI before interacting.
Use current labels, roles or inspected bounds; reread the UI after transitions. Canvas-based
UI may require screenshots plus exposed accessibility semantics rather than DOM text.

Navigate along the suspected user path, preserving the preceding actions when they matter.
A direct route/deep link is a shortcut only when it recreates the relevant state. Match the
reported viewport, text scale, locale, theme, keyboard/insets, scroll position and data as
needed. Check loading/error/recovery, warm/cold cache or lifecycle only where the symptom
supports them; do not turn every report into an exhaustive matrix.

Use existing fixtures or documented debug controls when useful and label that reproduction
as simulated. Preserve user/developer data; project installation and reset rules apply.
If tooling or state is unavailable, state the blocked boundary. If the symptom does not
appear, report the paths tried and remaining hypotheses rather than claiming reproduction.

Keep a small local incident note when investigation spans steps: observed symptom and image,
host/build, relevant state, ordered actions, expected versus actual outcome, source owners,
before evidence and logs. Use the project's ignored evidence area; screenshots/logs can
contain user data and should not automatically enter a commit.

## Fix and verify the reported symptom

Trace the reproduced behavior to its owner and make a scoped fix when authorized. Replay
the same actions and state on the same host, capture the after state, and inspect it beside
the before image. For visual reports, a green interaction test cannot establish that clipping,
icons, spacing or readability are corrected. Verify the relevant interaction/recovery as well.

Use the project's applicable checks and add a focused native regression where it can express
the failure meaningfully. Prefer extending existing tests over introducing another framework.
Report the reproduction steps, cause, fix, before/after evidence, checks and remaining limits.
Keep screenshot inspection, live reproduction and automated assertions as separate claims.

## Adapt to another project

Copy this skill directory. The screenshot investigation workflow has no Python, Make or
index prerequisite; the index helper needs Python 3.10+ and only the standard library.
For cheap adaptation, supply `qa/project.config.json` using the
[index contract](references/project-index.md): source globs/extractors, hosts, screen owners,
entry and state recipes. Generation/search use the same standard-library Python requirement.
A short `qa/project.md` or existing documentation also works without an index. Link current
source rather than duplicating a complete graph. Keep project names/routes/platform commands
outside this skill; recipe entries may be added incrementally as screenshots require them.
