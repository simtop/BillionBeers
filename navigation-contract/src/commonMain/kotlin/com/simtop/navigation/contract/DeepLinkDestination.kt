package com.simtop.navigation.contract

sealed interface DeepLinkDestination {
  data object BeersList : DeepLinkDestination

  data object Favorites : DeepLinkDestination

  data class BeerDetail(val beerId: String) : DeepLinkDestination
}

object DeepLinkParser {
  const val SCHEME = "billionbeers"
  const val HOST_BEERS = "beers"
  const val HOST_FAVORITES = "favorites"

  fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLinkDestination? {
    if (scheme != SCHEME) return null
    return when (host) {
      HOST_FAVORITES ->
        if (pathSegments.isEmpty() || pathSegments.all(String::isBlank)) {
          DeepLinkDestination.Favorites
        } else {
          null
        }
      HOST_BEERS -> {
        val beerId = pathSegments.firstOrNull()
        if (beerId.isNullOrBlank()) {
          DeepLinkDestination.BeersList
        } else {
          DeepLinkDestination.BeerDetail(beerId)
        }
      }
      else -> null
    }
  }
}
