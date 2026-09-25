package com.simtop.navigation

import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.contract.PortableRoute
import kotlinx.serialization.Serializable

@Serializable object BeersList : NavKey

@Serializable object Favorites : NavKey

@Serializable object BeersSearch : NavKey

// `get()` rather than a stored property: an initialised `override val` would have a backing field,
// which kotlinx.serialization would then try to serialise into the saved back stack.
@Serializable
object BeerBrowse : DynamicFeatureKey {
  override val feature: DynamicFeature
    get() = DynamicFeature.BeerBrowse
}

@Serializable
data class BeerDetail(val beer: Beer) : DynamicFeatureKey {
  override val feature: DynamicFeature
    get() = DynamicFeature.BeerDetail
}

fun NavKey.toPortableRoute(): PortableRoute? =
  when (this) {
    BeersList -> PortableRoute.BeersList
    Favorites -> PortableRoute.Favorites
    BeersSearch -> PortableRoute.BeersSearch
    BeerBrowse -> PortableRoute.BeerBrowse
    is BeerDetail -> PortableRoute.BeerDetail(beer)
    else -> null
  }

fun PortableRoute.toNavKey(): NavKey =
  when (this) {
    PortableRoute.BeersList -> BeersList
    PortableRoute.Favorites -> Favorites
    PortableRoute.BeersSearch -> BeersSearch
    PortableRoute.BeerBrowse,
    is PortableRoute.BeerBrowseSelection -> BeerBrowse
    is PortableRoute.BeerDetail -> BeerDetail(beer)
  }
