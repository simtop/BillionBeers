package com.simtop.billionbeers.web

import com.simtop.navigation.contract.PortableRoute

internal sealed interface WebRouteDestination {
  data object Catalog : WebRouteDestination

  data object Favorites : WebRouteDestination

  data object Search : WebRouteDestination

  data object Browse : WebRouteDestination

  data class BeerDetail(val id: String) : WebRouteDestination
}

internal fun parseWebHash(hash: String): WebRouteDestination? =
  when {
    hash.isBlank() || hash == "#catalog" -> WebRouteDestination.Catalog
    hash == "#favorites" -> WebRouteDestination.Favorites
    hash == "#search" -> WebRouteDestination.Search
    hash == "#browse" -> WebRouteDestination.Browse
    hash.startsWith("#beer/") && hash.removePrefix("#beer/").isNotBlank() ->
      WebRouteDestination.BeerDetail(hash.removePrefix("#beer/"))
    else -> null
  }

internal fun PortableRoute.toWebHash(): String =
  when (this) {
    PortableRoute.BeersList -> "#catalog"
    PortableRoute.Favorites -> "#favorites"
    PortableRoute.BeersSearch -> "#search"
    PortableRoute.BeerBrowse -> "#browse"
    is PortableRoute.BeerDetail -> "#beer/${beer.id}"
  }
