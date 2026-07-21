package com.simtop.navigation

import androidx.navigation3.runtime.NavKey

/**
 * A dynamic feature module paired with the [DynamicFeatureContentProvider] implementation it
 * exposes.
 *
 * The two are declared together so a destination cannot be wired to one module's name and another
 * module's provider class. Adding a feature means adding one entry here.
 */
enum class DynamicFeature(val moduleName: String, val providerClass: String) {
  BeerDetail("beerdetail", "com.simtop.feature.beerdetail.presentation.BeerDetailProviderImpl"),
  BeerBrowse("beerbrowse", "com.simtop.feature.beerbrowse.presentation.BeerBrowseProviderImpl"),
}

/**
 * A destination whose screen lives in a dynamic feature module, and so cannot be shown until
 * [feature] is installed.
 *
 * Declaring the requirement on the key itself is what lets the app gate every such destination in
 * one place: the navigator checks for this interface rather than knowing which features exist. A
 * new dynamic-feature destination is gated the moment it implements this - there is no second step
 * to forget.
 */
interface DynamicFeatureKey : NavKey {
  val feature: DynamicFeature
}
