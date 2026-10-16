package com.simtop.navigation.contract

object DeepLinkParser {
  const val SCHEME = "billionbeers"
  const val HOST_BEERS = "beers"
  const val HOST_FAVORITES = "favorites"

  fun parse(scheme: String?, host: String?, pathSegments: List<String>): PortableRoute? {
    if (scheme != SCHEME) return null
    return when (host) {
      HOST_FAVORITES ->
        if (pathSegments.isEmpty() || pathSegments.all(String::isBlank)) {
          PortableRoute.Favorites
        } else {
          null
        }
      HOST_BEERS -> {
        val beerId = pathSegments.firstOrNull()
        if (beerId.isNullOrBlank()) {
          PortableRoute.BeersList
        } else {
          PortableRoute.BeerDetail(beerId)
        }
      }
      else -> null
    }
  }
}
