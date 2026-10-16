package com.simtop.navigation

import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.contract.PortableRoute

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

/** Returns Android split metadata when this navigation key belongs to an on-demand feature. */
fun NavKey.dynamicFeatureOrNull(): DynamicFeature? =
  when (this) {
    PortableRoute.BeerBrowse -> DynamicFeature.BeerBrowse
    is PortableRoute.BeerDetail -> DynamicFeature.BeerDetail
    else -> null
  }
