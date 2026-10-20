# Review BillionBeers screens

The recording adapter is Android. `qa/design-review.json` maps `catalog` to the debug-only full-home previews in `app/src/debug/java/com/simtop/billionbeers/presentation/CatalogDesignReviewPreview.kt`.

The preview calls the real `BeersListContent` and `AppBottomNavigation`. It covers loaded light and dark themes, 150% text, long content, loading, empty, and error states. Images are authored synthetic bottle fixtures supplied through a preview-only Coil handler. They exercise loaded-image geometry without network access. They cannot establish the quality of real catalog photography.

Use the existing KSP and Paparazzi convention. Do not introduce a second screenshot framework or a preview-only copy of the screen. Use Makefile commands and respect the scoped preview inventory check:

```bash
make screenshot-record MODULE=:app
make screenshot-verify MODULE=:app
```

Scoped inventory commands share `build/reports/paparazzi/inventory`; serialize them within a checkout or choose distinct `SCREENSHOT_INVENTORY_DIR` values.

When adding or removing previews, inspect the new PNGs and update the reviewed inventory through `make screenshot-inventory-record MODULE=:app`. Do not update that inventory merely to hide a missing case.

For a baseline on unchanged code, prefer `capture --run verify`. Inside the isolated option checkout, use `capture --run record`. Keep the fixture preview file unchanged across options. Save each option's production-source diff before restoring that checkout to its source baseline.

`detail` maps to debug-only `DetailDesignReviewPreview` in `:feature:beerdetail`. Five cases cover light, dark, 150% text, long content and sparse records, using the real `ComposeBeerDetail` and an offline loaded-image handler. Capture it with `make design-review-capture DESIGN_REVIEW_SCREEN=detail`; use `MODULE=:feature:beerdetail` for record, verify and inventory commands. Detail labels remain in `:presentation_utils`.

Other screens already have feature previews. Add a screen config using those previews when they contain representative content. Add a full-screen preview only when the existing component or loading-only captures cannot support the intended critique.

iOS and Web have runtime evidence commands, and Desktop has Compose test captures. Those are useful starting points, but this adapter does not automate their recording. Import actual host PNGs when available. Never relabel the Android shared-Compose render as proof for another host.
