package com.simtop.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable object BeersList : NavKey

@Serializable object Favorites : NavKey

@Serializable object BeersSearch : NavKey

@Serializable object SavedFilterPresets : NavKey

@Serializable
data class SavedFilterResults(
  val id: String,
  val name: String,
  val search: String? = null,
  val styleId: String? = null,
  val breweryId: String? = null,
) : NavKey {
  fun toQuery() = com.simtop.beerdomain.domain.models.BeersQuery(search, styleId, breweryId)
}

// `get()` rather than a stored property: an initialised `override val` would have a backing field,
// which kotlinx.serialization would then try to serialise into the saved back stack.
@Serializable
object BeerBrowse : DynamicFeatureKey {
  override val feature: DynamicFeature
    get() = DynamicFeature.BeerBrowse
}

@Serializable
data class BeerDetail(val beerId: String) : DynamicFeatureKey {
  override val feature: DynamicFeature
    get() = DynamicFeature.BeerDetail
}
