package com.simtop.billionbeers.web

import com.simtop.navigation.contract.PortableRoute

internal sealed interface WebRouteDestination {
  data object Catalog : WebRouteDestination

  data object Favorites : WebRouteDestination

  data object Search : WebRouteDestination

  data object Browse : WebRouteDestination

  data class BrowseSelection(val kind: Kind, val id: String) : WebRouteDestination {
    enum class Kind {
      Style,
      Brewery,
    }
  }

  data class BeerDetail(val id: String) : WebRouteDestination
}

internal fun parseWebHash(hash: String): WebRouteDestination? =
  when {
    hash.isBlank() || hash == "#catalog" -> WebRouteDestination.Catalog
    hash == "#favorites" -> WebRouteDestination.Favorites
    hash == "#search" -> WebRouteDestination.Search
    hash == "#browse" -> WebRouteDestination.Browse
    hash.startsWith("#browse/style/") &&
      hash.removePrefix("#browse/style/").isNotBlank() &&
      !hash.removePrefix("#browse/style/").contains("/") ->
      WebRouteDestination.BrowseSelection(
        WebRouteDestination.BrowseSelection.Kind.Style,
        hash.removePrefix("#browse/style/"),
      )
    hash.startsWith("#browse/brewery/") &&
      hash.removePrefix("#browse/brewery/").isNotBlank() &&
      !hash.removePrefix("#browse/brewery/").contains("/") ->
      WebRouteDestination.BrowseSelection(
        WebRouteDestination.BrowseSelection.Kind.Brewery,
        hash.removePrefix("#browse/brewery/"),
      )
    hash.startsWith("#beer/") &&
      hash.removePrefix("#beer/").isNotBlank() &&
      !hash.removePrefix("#beer/").contains("/") ->
      WebRouteDestination.BeerDetail(hash.removePrefix("#beer/"))
    else -> null
  }

internal fun PortableRoute.toWebHash(): String =
  when (this) {
    PortableRoute.BeersList -> "#catalog"
    PortableRoute.Favorites -> "#favorites"
    PortableRoute.BeersSearch -> "#search"
    PortableRoute.BeerBrowse -> "#browse"
    is PortableRoute.BeerBrowseSelection ->
      when (val category = category) {
        is com.simtop.navigation.contract.BrowseCategory.Style -> "#browse/style/${category.id}"
        is com.simtop.navigation.contract.BrowseCategory.Brewery -> "#browse/brewery/${category.id}"
      }
    is PortableRoute.BeerDetail -> "#beer/${beer.id}"
  }
