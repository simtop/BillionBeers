package com.simtop.navigation.contract

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PortableRoute : NavKey {
  @Serializable @SerialName("com.simtop.navigation.BeersList") data object BeersList : PortableRoute

  @Serializable @SerialName("com.simtop.navigation.Favorites") data object Favorites : PortableRoute

  @Serializable
  @SerialName("com.simtop.navigation.BeersSearch")
  data object BeersSearch : PortableRoute

  @Serializable
  @SerialName("com.simtop.navigation.SavedFilterPresets")
  data object SavedFilterPresets : PortableRoute

  @Serializable
  @SerialName("com.simtop.navigation.BeerBrowse")
  data object BeerBrowse : PortableRoute

  @Serializable
  @SerialName("beer_browse_selection")
  data class BeerBrowseSelection(val category: BrowseCategory) : PortableRoute

  @Serializable(with = BeerDetailRouteSerializer::class)
  @SerialName("com.simtop.navigation.BeerDetail")
  data class BeerDetail(val beerId: String) : PortableRoute
}

@Serializable
sealed interface BrowseCategory {
  val id: String
  val name: String

  @Serializable
  @SerialName("style")
  data class Style(override val id: String, override val name: String) : BrowseCategory

  @Serializable
  @SerialName("brewery")
  data class Brewery(override val id: String, override val name: String) : BrowseCategory
}
