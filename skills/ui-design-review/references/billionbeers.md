# Review BillionBeers screens

`qa/design-review.json` configures Android KSP/Paparazzi captures of production compositions. Review fixtures live in debug source sets. Run `make design-review-capture DESIGN_REVIEW_SCREEN=<id>` with the desired output/run variables, or use the portable helper directly.

| Screen ID | Cases and boundary | Module |
|---|---|---|
| `catalog` | Home with actual bottom navigation; loaded light/dark/large text, long content, loading, empty, error, inline search and Save form | `:app` |
| `detail` | Expanded, partial, compact and body-at-end states; light/dark/large text, long and sparse records | `:feature:beerdetail` |
| `advanced-search` | Settled text + style query, Save form closed/open, light/dark and large form text | `:feature:beersearch` |
| `browse-style` | Style results, Save form closed/open, light/dark | `:feature:beerbrowse` |
| `browse-brewery` | Brewery results, Save form closed/open, light/dark and large form text | `:feature:beerbrowse` |

Search/Browse captures are feature surfaces with their own toolbar; they omit the host's bottom navigation. Capture the running app when judging complete navigation flows. Catalog/detail use synthetic loaded bottle art. Search/Browse use fixed data with loading placeholders. These fixtures establish geometry, not real photography quality. Callbacks in previews are inert; use runtime checks for behavior.

## Record and verify

Use the existing Makefile wrappers with the module from the table:

```bash
make screenshot-record MODULE=:feature:beersearch
make screenshot-verify MODULE=:feature:beersearch
```

Prefer `capture --run verify` for an unchanged baseline and `capture --run record` inside an isolated option checkout. `--run existing` explicitly imports unverified images. Keep fixture files unchanged across options. New fixture/case coverage requires a fresh shared baseline.

Inspect new PNGs, then update the reviewed inventory with `make screenshot-inventory-record MODULE=:module`. Scoped inventory commands share `build/reports/paparazzi/inventory`; serialize them or use distinct `SCREENSHOT_INVENTORY_DIR` values. Run `make design-review-test` locally to validate adapter selection and helper behavior. Model critique, experiments and reports stay local under `design/`; do not add review execution or report generation to CI.

## Verify behavior before adoption

Inventory Home, Advanced Search, Browse style results and Browse brewery results when changing Save/search UI. Preserve actual query criteria and save eligibility, draft retention on failure/restoration, delayed-completion focus and Back dismissal. Existing checks live in `FilterPresetFormLayoutTest`, `SavedFiltersMutationUiTest`, `BeersSearchInputUiTest` and `BeerBrowseUiTest`; confirm current names and assertions in source before choosing a Makefile device target.

Android detail uses exit-until-collapsed behavior. `BeerDetailUiTest` checks actual swipes, released body space, controls, restoration and re-expansion, plus the scrolled reference. Compact previews seed only the header with the body at top; `DetailScrolledDesignReviewPreview` follows the final scroll range as layout settles; native checks assert the body is at its end at normal and large text in a compact test host. Preserve that distinction in reports. Other hosts retain their explicit policies and title overrides.

## Brand and renderer boundaries

`BillionBeersTheme.brand` exposes shared catalog/beer/detail title roles, hero colors and artwork geometry from `shared/designsystem/.../theme/BillionBeersBrand.kt`. General palette, typography and spacing live beside it. Product copy remains in translated resources; dynamic-feature labels remain in `:presentation_utils`. Keep interaction policies with their screen owners.

The generated Paparazzi runner chooses Pixel 5 for compact and Pixel Tablet for expanded cases. Explicit `@Preview` dimensions/device names currently select a bucket; their exact dimensions are not applied. The preview named landscape renders the tablet configuration, not its requested 844×390 viewport. The runner sets layout-direction metadata but does not explicitly pass `supportsRtl`; confirm mirroring before claiming RTL evidence. The review adapter uses LTR cases and known device buckets. Use a measured native capture when another viewport/direction matters.

This adapter automates Android screenshots. iOS/Web runtime evidence commands and Desktop Compose tests can support a later host adapter, but compilation or Android shared-Compose renders do not establish screenshots for those hosts. Import actual host PNGs only with verified capture commands and declared conditions.
