# Capture and report commands

The Python helper uses the standard library. It does not require an emulator, image library, browser, or API key. The project's renderer still needs its own build dependencies.

## Capture a screen

From the checkout that owns the screenshots, run:

```bash
python3 skills/ui-design-review/scripts/review.py capture \
  --config qa/design-review.json --screen catalog \
  --output design/reviews/catalog-trial/baseline --run record
```

`--run record` executes the configured record command. Use that mode for new previews or inside an isolated candidate checkout. `--run verify` executes the configured verification command without accepting new goldens. `--run existing` imports existing images and labels their freshness as unverified. Explain that limitation when using imported captures.

Each capture directory contains `capture.json`, named PNGs, and the renderer log when a command ran. The manifest records conditions, pixel sizes, image hashes, source hashes, fixture hashes, and Git state. Capture directories cannot be overwritten. A failed render produces no successful manifest. Source files cannot change during the render.

The project config has `schema: 1` and a `screens` object. Each screen declares `platform`, `renderer`, `record`, `verify`, `image_directory`, `sources`, `fixtures`, and `cases`. Commands are argument arrays, executed without a shell. Each case has `id`, a unique literal filename `match`, and `conditions`. Keep platform-appropriate conditions such as theme, font scale, locale, viewport, and image state explicit.

The helper can import PNGs from Android, iOS, Web, or Desktop using that same format. A config entry does not implement a renderer. Add a host only after checking its actual capture command and output.

For a flow spanning several screen IDs, capture and report each screen with the same option/revision IDs. Keep one stash/patch for the option's complete changes and use a local index under `design/` to summarize the recommendation across screens. Each report still needs matching before/after cases for its own screen.

## Describe the review

Create a JSON file next to the capture directories:

```json
{
  "title": "Catalog design review",
  "scope": "Android compact home, deterministic fixtures",
  "strengths": ["Names and availability are already easy to distinguish."],
  "findings": ["The permanent naming form takes space from browsing."],
  "recommendation": "disclosure",
  "conclusion": "Use the disclosure option because it recovers space while preserving the rows.",
  "limitations": ["Synthetic bottle artwork; interaction behavior was not runtime-tested."],
  "options": [
    {
      "id": "disclosure",
      "title": "UI design review: disclose the save form",
      "capture": "disclosure/capture.json",
      "patch": "disclosure.patch",
      "critique": "Naming a saved filter is secondary to browsing.",
      "changes": ["Show the naming form after the Save filter action."],
      "assessment": "The list has more room and retains the original visual hierarchy.",
      "tradeoffs": ["Saving now requires one additional action."]
    }
  ]
}
```

Optional `prior_evidence` is a list of lessons from previous critiques and rendered experiments. Optional `references` records outside design sources as objects with `title`, `url` (HTTP or HTTPS), and `contribution` describing the pattern adapted. These appear in both reports. Describe each option's visual hypothesis and synthesis lineage in its critique and changes.

Set `recommendation` to an option ID or `baseline`. Paths resolve relative to this JSON file. The optional `patch` path embeds the source diff in HTML and copies it beside the Markdown report. Preserve source patches alongside the option captures. Include verification results in `limitations` or the option assessment where they affect the conclusion.

## Generate the report

```bash
python3 skills/ui-design-review/scripts/review.py report \
  --baseline design/reviews/catalog-trial/baseline/capture.json \
  --review design/reviews/catalog-trial/review.json --output design/reviews/catalog-trial/report
```

The helper creates `report.html` and `report.md`. HTML embeds all images and works offline. It rejects altered images, different case sets, different dimensions or conditions, and changed fixture files. A matching manifest establishes comparable render inputs, not design quality. Open the report, inspect its layout, and show the user the observed result.


## Preserve and recover every experiment

Use a clean isolated checkout at the source baseline for each option. If the baseline includes copied local changes, record a local checkpoint in that checkout first so those changes are part of the baseline, not the experiment. Do not commit or stash the user's unrelated working changes.

After rendering and saving the patch, run this helper with all changed experiment files, including added previews and any intended golden updates. Alternatively, freeze the captures and restore only the isolated checkout’s recorded goldens before stashing source changes:

```bash
python3 skills/ui-design-review/scripts/review.py stash \
  --root /tmp/catalog-option --id catalog-trial-fusion \
  --paths path/to/Screen.kt path/to/NewPreview.kt \
  --output design/reviews/catalog-trial/fusion/stash.json
```

The output path resolves from the command's working directory. It defaults beneath `design/reviews/` in the experiment checkout; specify a persistent project path when that checkout is temporary. The helper refuses dirty files outside the declared source paths, duplicate IDs and an existing manifest. It saves tracked and new files to a named Git stash and records its stable OID, source hashes and baseline revision. Existing stashes remain intact. Keep the manifest with the report and add the OID to that option's assessment or limitations.

Run `git stash apply <OID>` on an isolated adoption branch to recover an entire option without dropping its backup. For a mix, use `git restore --source=<OID> -- path/to/Tracked.kt`. Added files are in the stash's third parent: `git show '<OID>^3:path/to/NewPreview.kt' > path/to/NewPreview.kt`. Inspect the diff and resolve conflicts against the current source before testing. For an older baseline, adapt selected files/hunks against current interfaces and check that accepted interaction fixes survive the mix. Never use a moving `stash@{n}` as the saved identity or automatically drop experiment stashes after adoption.

The report command defaults to the current project's `design/reviews/<UTC run>/` when `--output` is omitted. Keep that directory ignored because capture provenance can contain local paths and working-tree metadata. The selected report can be moved to another directory under `design/` when requested.
