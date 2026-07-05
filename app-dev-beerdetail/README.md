# app-dev-beerdetail

Standalone dev-app for `:feature:beerdetail`, stamped out by `scripts/new-dev-app.sh`. See
`app-dev-beerslist/README.md` for the pattern this replicates in full.

## Still TODO after generation

1. **di/DevFakesModule.kt**: bind whatever repositories BeerDetail depends on to fakes (uncomment
   and adapt the template, or write a new fake if one doesn't exist in a `:fakes` module yet).
2. **MainActivity.kt**: replace the TODO block with the real call to BeerDetail's screen composable.
3. If `:feature:beerdetail` is a dynamic feature (like `:feature:beerdetail`), its screen is normally
   loaded reflectively via `DynamicFeatureContentProvider` - for this dev-app you can most
   likely call the underlying `@Composable` screen function directly instead, since there's no
   split-install to gate here (check what the feature's provider implementation wraps).

## Regenerating

Delete `app-dev-beerdetail/` and its `include(":app-dev-beerdetail")` line in `settings.gradle.kts`, then
rerun: `scripts/new-dev-app.sh beerdetail BeerDetail :feature:beerdetail`
