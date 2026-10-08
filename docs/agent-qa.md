# Screenshot-led agent QA

Attach a screenshot and describe the problem or expected behavior, for example:

> Use agent-qa to reproduce and fix the clipped button. This is Android, after opening
> Saved filters with increased text size.

The [portable skill](../skills/agent-qa/SKILL.md) directs the agent to inspect the image,
identify candidate screens, inspect their source/navigation/state recipes, reproduce in the
running host, and verify the fix with before/after evidence plus native regression checks.
A screenshot alone may not identify its preceding actions or dynamic data.

## Search current source

```bash
make qa-index
make qa-find QUERY="Saved filters" QA_HOST=android
make qa-find QUERY="Cerveza no disponible" QA_HOST=web
make qa-screen SCREEN=beer-unavailable QA_HOST=web
make qa-screen SCREEN=saved-filters QA_HOST=android
```

`qa-index` writes `build/qa/project.json`. Searches and screen lookups rebuild in memory from
current source, returning JSON without launching an app or executing recipe commands.
The index includes nine screen recipes, source locations/hashes, discovered components,
labels/translations, UI tags and Android route registrations. Undeclared source components
remain searchable without an invented navigation recipe. Search requires every term to match
and includes matching resource evidence. Candidates still need confirmation in the live UI.

[qa/project.config.json](../qa/project.config.json) supplies project-specific source globs,
extraction patterns, hosts, owners, recognition clues, entry actions and state recipes.
The generator checks anchors and extracts lexical source facts; it is not a compiler/call graph.
Recipes retain declared scope: `live-app`, `test`, `preview` or `none`. It does not implement a
state-forcing console. For example, Web cold detail links recreate an unavailable-beer state,
Android rename failure uses an instrumented fake, and live mutation-error injection is unavailable.

Read `host_setup` and recipe limits before acting. Android full-app installation must use
`make install` to stage on-demand splits; clearing app data removes staging. On Web,
`#saved-filters` identifies both presets and applied results: apply a preset through the UI
rather than opening that hash directly. Reload intentionally loses the session result token.
A direct detail link may not recreate the original list/back stack or its scroll position.

Keep incident notes, logs and before/after captures under an ignored `build/qa/` directory.
Record actual host/build, state, ordered actions and observed behavior. Use existing native
Make targets for regression checks; `ui-test` supports `UI_TEST_CLASS=class#method` selection.

## Maintain and reuse

`make qa-validate` checks anchors, IDs, host bindings and state scope. `make qa-test` verifies
source refresh, searches, another-language configuration and project integration. Both run
in CI's existing always-run format/reporting checks; index-only changes do not force app lanes.

Copy the complete skill bundle into another project's skill location and adapt
`qa/project.config.json` using the [index contract](../skills/agent-qa/references/project-index.md).
Python 3.10+ standard library is sufficient; Make/Gradle/Kotlin are not prerequisites.
Existing project documentation can replace the index when the project has not adopted it.

This checkout exposes the tracked bundle through an ignored `.claude/skills/agent-qa` symlink,
also visible via `.agents/skills`. The reusable source is `skills/agent-qa`; local discovery
links and generated JSON are not committed.
