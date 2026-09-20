package com.simtop.navigation.contract

import com.simtop.beerdomain.domain.models.Beer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PortableRoute {
  @Serializable
  @SerialName("beers_list")
  data object BeersList : PortableRoute

  @Serializable
  @SerialName("favorites")
  data object Favorites : PortableRoute

  @Serializable
  @SerialName("beers_search")
  data object BeersSearch : PortableRoute

  @Serializable
  @SerialName("beer_browse")
  data object BeerBrowse : PortableRoute

  @Serializable
  @SerialName("beer_detail")
  data class BeerDetail(val beer: Beer) : PortableRoute
}
