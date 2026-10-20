# Review a screen with rendered alternatives

Use the `ui-design-review` skill to produce a local critique with screenshots, proposed code changes, and a recommendation. The recording adapter covers the Android catalog and beer detail. The report can import other hosts' PNG captures when an actual capture command is available.

## Invoke the skill

The reusable source lives in [skills/ui-design-review](../skills/ui-design-review/SKILL.md). Link that directory into your agent's skill directory, then start a new turn. For Codex, the default directory is `~/.codex/skills`.

Example request:

> Use $ui-design-review on the Android catalog. Render distinct visual directions and a final curated synthesis and recommend whether to keep the current design or adopt one of them.

The skill keeps proposed product changes in an isolated checkout. A review does not adopt a design or replace accepted goldens in your working tree.

## Capture the catalog baseline

Run this command from the repository root:

```bash
make design-review-capture
```

The command verifies the current screenshots and freezes the PNGs under `design/reviews/catalog/<UTC timestamp>/baseline`. It also records image hashes, fixture hashes, rendering conditions, source hashes, and Git state. Use `DESIGN_REVIEW_OUTPUT` to choose another directory.

The debug-only [catalog preview](../app/src/debug/java/com/simtop/billionbeers/presentation/CatalogDesignReviewPreview.kt) composes the real screen and bottom navigation. Its cases include light and dark themes, 150% text, long content, loading, empty, and error states. Bottle images are synthetic offline fixtures. Evaluate real photography separately when it matters to a design decision.

For a new preview, record its golden with `make screenshot-record MODULE=:app`, inspect it, and update the reviewed inventory with `make screenshot-inventory-record MODULE=:app`.

Capture beer detail with `make design-review-capture DESIGN_REVIEW_SCREEN=detail`. Its debug preview covers light, dark, 150% text, long names and sparse records with the same offline image approach. Record and verify it with `MODULE=:feature:beerdetail`.

## Render and compare proposals

In an isolated checkout, make each proposed change against the same code baseline. Keep the preview data and artwork unchanged. Set `DESIGN_REVIEW_RUN=record` when capturing the proposal, and save its source patch and a named experiment stash alongside the capture.

For exploratory reviews, state a visual identity brief and compare lessons from previous proposals. A native component system supplies interaction conventions; the default component appearance is not the end goal. Explain any app reference by the specific pattern transferred. If requested, render a final synthesis and trace its choices to the earlier options. Check a direction across catalog and detail before recommending it.

Write the critique and recommendation in the [report JSON format](../skills/ui-design-review/references/capture-and-compare.md). Generate the report with:

```bash
python3 skills/ui-design-review/scripts/review.py report \
  --baseline /tmp/catalog-review/baseline/capture.json \
  --review /tmp/catalog-review/review.json --output design/reviews/catalog-trial/report
```

Open `report.html` to compare the current screen with each proposal. Expand the other states before accepting a recommendation. `report.md` and its image directory provide the same evidence in Markdown.

The helper rejects changed images and mismatched fixtures, rendering conditions, dimensions, or case sets. These checks establish comparable inputs. The agent still inspects the output and explains visual preferences and behavior that remains untested.

The report command defaults to `design/reviews/<UTC run>/` when `--output` is omitted. Generated reports and local recovery manifests remain ignored.

Preserve each option from its isolated checkout before cleanup:

```bash
python3 skills/ui-design-review/scripts/review.py stash \
  --root /tmp/catalog-option --id catalog-trial-fusion \
  --paths feature/beerslist/src/main/java/com/simtop/feature/beerslist/BeersListScreen.kt \
  --output design/reviews/catalog-trial/fusion/stash.json
```

The helper rejects unrelated changes, broad path selections and duplicate experiment IDs. It records the stable stash OID, baseline revision and source hashes, includes new source files and leaves older stashes intact. Recover with `git stash apply <OID>`; keep the stash available for further comparisons.

Run `make design-review-test` locally to test capture integrity and report generation without an Android build. Design critique, experiments, and report generation run locally; CI does not invoke the skill.
