package com.simtop.navigation

import android.net.Uri

sealed interface DeepLinkDestination {
  data object BeersList : DeepLinkDestination

  data object Favorites : DeepLinkDestination

  data class BeerDetail(val beerId: String) : DeepLinkDestination
}

// Takes decomposed Uri parts (not android.net.Uri itself) so this stays a pure function testable
// on the plain JVM - android.net.Uri's methods throw when unmocked outside instrumented tests.
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

fun Uri.toDeepLinkDestination(): DeepLinkDestination? =
  DeepLinkParser.parse(scheme, host, pathSegments)
