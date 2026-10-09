# Searchable project index

Use this when the project supplies `qa/project.config.json`, or when adding a reusable screen
index for screenshot investigation. The helper uses Python 3.10+ standard library only.

```bash
python3 <skill>/scripts/project_index.py --root <project> generate
python3 <skill>/scripts/project_index.py --root <project> search 'visible text' --host android
python3 <skill>/scripts/project_index.py --root <project> show saved-filters --host android
```

`generate` writes `build/qa/project.json`. `search`, `show` and `validate` rebuild their index
in memory from current source; they do not use an old generated file, launch an app or execute
recipe commands. All paths are project-relative. Use `--config` for a different input and
`generate --output` for a different artifact path. Generated output cannot overwrite inputs.

## Input contract

The config has `schema: 1`, a `hosts` object, a nonempty `screens` array and optional `scan`.

- A host maps its ID to nonempty `launch` and `preconditions` string arrays and `evidence`.
  Launch strings describe setup actions, not executable argument arrays.
- A screen needs a lowercase hyphenated `id`, `name`, `hosts`, `recognition` strings, `owners`,
  `entry`, nonempty `limits`, and optional `states`. Recognition hints are declared clues;
  they can include dynamic title patterns or visual descriptions without asserting identity.
- `entry` contains nonempty `steps`, a `preconditions` string array and `evidence`.
- Each state needs `id`, `hosts`, `mechanism`, `scope`, nonempty `steps`, `observe`, `limits`
  and `evidence`. Mechanisms: `ui`, `environment`, `fixture`, `unavailable`. Scopes:
  `live-app`, `test`, `preview`, `none`. Unavailable states must have scope `none`.
- Every `evidence` / `owners` array contains `{ "path": "src/file", "needle": "exact anchor" }`.
  Owners can additionally declare `hosts` to separate platform bindings. The generator checks
  that the file and anchor exist and adds the first line and match count. Prefer a distinctive
  declaration/callback anchor. These checks do not prove the recipe's semantics.

The generator validates host IDs and duplicate screen/state IDs. It records config and source
byte hashes. Source changes update locations, extracted values and hashes; deleted anchors fail
validation. Entry actions and state semantics still need maintenance and live verification.

## Automatic source discovery

`scan.include` contains project-relative globs. Generated `build/`, IDE `bin/`, `.git/` files
and scanned symlink files are excluded. Choose actual UI/resource roots rather than scanning
private configuration or unrelated files.

Each `scan.extractors` entry has `kind`, `files` (a filename/path glob) and `pattern` (Python
regex). A pattern needs one capture group for its value, or two named groups `key` and `value`.
For example, a web project might extract labels and selectors from its source templates:

```json
{
  "include": ["src/**/*.tsx"],
  "extractors": [
    {"kind": "selector", "files": "*.tsx", "pattern": "data-testid=\"([^\"]+)\""}
  ]
}
```

The index includes source matches even when they have no declared screen recipe. Discovery is
lexical: comments, previews, shared-file siblings or unregistered components can be candidates.
It does not infer a complete navigation graph, interpret Kotlin/JavaScript, or identify a screen
from image pixels. This keeps it inexpensive and independent of compiler/toolchain setup.

`resource-reference` values can be linked to extracted resource values with a matching `key`.
This makes translated labels searchable. Key association does not resolve namespace imports,
locale fallback, XML markup/escaping or runtime formatting; verify the actual rendered text.

## Agent use

Extract clues from the image, search a few distinctive labels/selectors and inspect candidate
records with `show`. Search requires every query term to match within one recognition clue,
recipe field or source match; it does not assemble a title from unrelated labels/translations.
Full phrases and declared recognition hints take priority over contextual text. Matching resource
values include their source locations. Scores are ordering hints, not probabilities. Confirm screen, host and state
in the live UI before using a recipe. Host filtering selects relevant owners and state recipes.

State recipes describe the setup mechanism and its boundary. A fixture marked `test` or
`preview` is not an ordinary-app control. `unavailable` records make missing state injection
explicit. The helper never executes steps or creates those controls.

Successful CLI exit means generation/query/validation succeeded; it does not mean any app
behavior was tested. No search result is a valid outcome for an unknown screenshot clue.
