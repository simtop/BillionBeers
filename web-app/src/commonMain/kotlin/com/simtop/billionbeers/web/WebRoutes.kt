package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute

internal sealed interface WebRouteResolution {
  data class Ready(val route: PortableRoute) : WebRouteResolution

  data class MissingBeer(val id: String) : WebRouteResolution
}

/** External detail links only resolve locally; an absent record is a visible host outcome. */
internal suspend fun resolveWebHash(
  hash: String,
  findLocalBeer: suspend (String) -> Beer?,
): WebRouteResolution =
  when (val destination = parseWebHash(hash)) {
    WebRouteDestination.Catalog,
    null -> WebRouteResolution.Ready(PortableRoute.BeersList)
    WebRouteDestination.Favorites -> WebRouteResolution.Ready(PortableRoute.Favorites)
    WebRouteDestination.Search -> WebRouteResolution.Ready(PortableRoute.BeersSearch)
    WebRouteDestination.SavedFilters -> WebRouteResolution.Ready(PortableRoute.SavedFilterPresets)
    WebRouteDestination.Browse -> WebRouteResolution.Ready(PortableRoute.BeerBrowse)
    is WebRouteDestination.BrowseSelection ->
      WebRouteResolution.Ready(
        PortableRoute.BeerBrowseSelection(
          category =
            when (destination.kind) {
              WebRouteDestination.BrowseSelection.Kind.Style ->
                BrowseCategory.Style(destination.id, destination.id)
              WebRouteDestination.BrowseSelection.Kind.Brewery ->
                BrowseCategory.Brewery(destination.id, destination.id)
            }
        )
      )
    is WebRouteDestination.BeerDetail ->
      findLocalBeer(destination.id)?.let { WebRouteResolution.Ready(PortableRoute.BeerDetail(it)) }
        ?: WebRouteResolution.MissingBeer(destination.id)
  }

internal sealed interface WebRouteDestination {
  data object Catalog : WebRouteDestination

  data object Favorites : WebRouteDestination

  data object Search : WebRouteDestination

  data object SavedFilters : WebRouteDestination

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
    hash == "#saved-filters" -> WebRouteDestination.SavedFilters
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
    PortableRoute.SavedFilterPresets -> "#saved-filters"
    PortableRoute.BeerBrowse -> "#browse"
    is PortableRoute.BeerBrowseSelection ->
      when (val category = category) {
        is com.simtop.navigation.contract.BrowseCategory.Style -> "#browse/style/${category.id}"
        is com.simtop.navigation.contract.BrowseCategory.Brewery -> "#browse/brewery/${category.id}"
      }
    is PortableRoute.BeerDetail -> "#beer/${beer.id}"
  }
